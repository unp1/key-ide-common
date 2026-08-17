/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.proof.init.InitConfig;
import de.uka.ilkd.key.proof.init.Profile;
import de.uka.ilkd.key.proof.init.ProofInputException;
import de.uka.ilkd.key.proof.io.ProblemLoaderException;
import de.uka.ilkd.key.speclang.Contract;

import org.key_project.ide.config.VerificationContext;
import org.key_project.ide.key.ContextKnowledge;
import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.key.MethodResolver;
import org.key_project.ide.config.ConfigStore;
import org.key_project.ide.config.ProjectConfig;
import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.key.AppliedOptions;
import org.key_project.ide.key.AvailableOptions;
import org.key_project.ide.key.ProofBrowser;
import org.key_project.ide.key.ProofFiles;
import org.key_project.ide.key.ProofObligations;
import org.key_project.ide.key.RuleBase;
import org.key_project.ide.key.SourceProblems;
import org.key_project.ide.key.ProofRunner;
import org.key_project.ide.key.ProofsTogether;
import org.key_project.ide.key.ResolvedMethod;
import org.key_project.ide.key.SavedSettings;
import org.key_project.ide.key.StatusIcons;
import org.key_project.ide.protocol.Dtos.AvailableOptionsDto;
import org.key_project.ide.protocol.Dtos.AvailableOptionsParams;
import org.key_project.ide.protocol.Dtos.BrowseParams;
import org.key_project.ide.protocol.Dtos.CancelParams;
import org.key_project.ide.protocol.Dtos.DependenciesResult;
import org.key_project.ide.protocol.Dtos.IconsParams;
import org.key_project.ide.protocol.Dtos.IconsResult;
import org.key_project.ide.protocol.Dtos.ListObligationsParams;
import org.key_project.ide.protocol.Dtos.MarksParams;
import org.key_project.ide.protocol.Dtos.MarksResult;
import org.key_project.ide.protocol.Dtos.MethodDto;
import org.key_project.ide.protocol.Dtos.ObligationDto;
import org.key_project.ide.protocol.Dtos.ObligationsParams;
import org.key_project.ide.protocol.Dtos.OptionDifferenceDto;
import org.key_project.ide.protocol.Dtos.ObligationsChangedDto;
import org.key_project.ide.protocol.Dtos.ObligationsResult;
import org.key_project.ide.protocol.Dtos.PositionParams;
import org.key_project.ide.protocol.Dtos.PositionResult;
import org.key_project.ide.protocol.Dtos.RemovedResult;
import org.key_project.ide.protocol.Dtos.ResolveParams;
import org.key_project.ide.protocol.Dtos.ProofOutcomeDto;
import org.key_project.ide.protocol.Dtos.PreparedResult;
import org.key_project.ide.protocol.Dtos.ProveParams;
import org.key_project.ide.protocol.Dtos.ProveProgressDto;
import org.key_project.ide.protocol.Dtos.ProveResult;
import org.key_project.ide.protocol.Dtos.StaleOptionsResult;
import org.key_project.ide.protocol.Dtos.UsedContractsDto;
import org.key_project.ide.protocol.Dtos.StartParams;
import org.key_project.ide.protocol.IdeClient;
import org.key_project.ide.protocol.VerificationService;

/**
 * Serves the verification half of the protocol.
 * <p>
 * It owns the proving threads, the runs in progress, and the cached status of every
 * context, and reaches KeY through the loaded environments.
 */
public final class VerificationServiceImpl implements VerificationService {

    private static final System.Logger LOGGER =
        System.getLogger(VerificationServiceImpl.class.getName());

    /** The size a tree row wants, used when the client does not ask for one. */
    private static final int DEFAULT_ICON_SIZE = 16;

    private final BridgeSession session;
    private final EnvironmentManager environments;
    private final ProofBrowser browser;
    private final Supplier<IdeClient> clients;

    /**
     * The threads that run proofs, one for each context.
     * <p>
     * Proofs do not run on the thread that reads messages, because the bridge must still
     * be able to read a request to stop while a proof is running.
     * <p>
     * Each context has a thread of its own, so proofs of different contexts run at the
     * same time.
     * <p>
     * Proofs of one context run one after another. KeY gives every proof its own
     * {@code Services}, namespaces and caches, so proofs are otherwise independent, but
     * {@code Services.copy} hands on the specification repository by reference. That
     * repository is written during proving, and its entries are keyed by AST nodes that
     * compare structurally, so two proofs of one method can reach the same entry. Until
     * each proof has a repository of its own, they are kept apart in time.
     */
    private final Map<String, ExecutorService> proving = new ConcurrentHashMap<>();

    /** The runs that have been started and have not finished, by their id. */
    private final Map<String, Run> runs = new ConcurrentHashMap<>();

    /**
     * What KeY says about each context, one owner each.
     * <p>
     * Nothing else in the bridge caches a status: a status cached in two places goes out of
     * date in one of them. This one is notified of every change and queries KeY when it
     * must.
     */
    private final Map<String, ContextKnowledge> knowledge = new ConcurrentHashMap<>();

    /**
     * The knowledge of a context, made on first use.
     *
     * @param contextId the context
     * @return its knowledge, which queries KeY through this service and notifies the client
     */
    private ContextKnowledge knowledgeOf(String contextId) {
        return knowledge.computeIfAbsent(contextId,
            id -> new ContextKnowledge(id, contracts -> judge(id, contracts),
                this::announceChange));
    }

    /**
     * Queries KeY: reads the saved proofs back into one ProofEnvironment and asks for the
     * status of each.
     * <p>
     * Runs on the context's proving thread, since it builds proofs from the context and two
     * threads must not build from one context at the same time.
     *
     * @param contextId the context
     * @param contractNames the obligations whose saved proofs to read
     * @return the status KeY reported for each proof it could read
     */
    private List<ContextKnowledge.Verdict> judge(String contextId, List<String> contractNames) {
        Set<String> wanted = Set.copyOf(contractNames);
        List<ProofObligations.Obligation> saved = obligationsOf(contextId).list().stream()
                .filter(o -> wanted.contains(o.contract().getName()))
                .toList();
        return readSaved(contextId, saved).stream()
                .map(VerificationMapper::verdict)
                .toList();
    }

    /**
     * Reads saved proofs back and asks KeY for the status of each.
     * <p>
     * All of them are loaded into one ProofEnvironment, so that KeY relates them to each
     * other. Runs on the context's proving thread, since it builds proofs from the context
     * and two threads must not build from one context at the same time.
     *
     * @param contextId the context
     * @param obligations the obligations whose saved proofs to read
     * @return the status KeY reported for each proof it could read
     */
    private List<ProofRunner.Outcome> readSaved(String contextId,
            List<ProofObligations.Obligation> obligations) {
        if (contextId.equals(PROVING_FOR.get())) {
            return readSavedHere(contextId, obligations);
        }
        return CompletableFuture.supplyAsync(() -> readSavedHere(contextId, obligations),
            provingIn(contextId)).join();
    }

    /** Reads them on this thread, which is the one allowed to build proofs of this context. */
    private List<ProofRunner.Outcome> readSavedHere(String contextId,
            List<ProofObligations.Obligation> obligations) {
        long started = System.currentTimeMillis();
        List<ProofsTogether.Read> read = new ProofsTogether(environmentFor(contextId),
            ProjectContexts.resolved(session, contextId)).readAll(obligations);
        try {
            long took = System.currentTimeMillis() - started;
            return read.stream().map(one -> one.outcome(took)).toList();
        } finally {
            ProofsTogether.release(read);
        }
    }

    /** The status KeY reports for the proofs of a run, queried while KeY still holds them. */
    private static List<ProofRunner.Outcome> outcomesOf(List<ProofRunner.Attempt> attempts) {
        return attempts.stream().map(ProofRunner.Attempt::outcome).toList();
    }

    /**
     * @param session where the project root is recorded
     * @param environments the loaded contexts
     * @param browser shows the proof obligation browser
     */
    public VerificationServiceImpl(BridgeSession session, EnvironmentManager environments,
            ProofBrowser browser) {
        this(session, environments, browser, () -> null);
    }

    /**
     * @param session where the project root is recorded
     * @param environments the loaded contexts
     * @param browser shows the proof obligation browser
     * @param clients the connected client, for reporting progress
     */
    public VerificationServiceImpl(BridgeSession session, EnvironmentManager environments,
            ProofBrowser browser, Supplier<IdeClient> clients) {
        this.session = session;
        this.environments = environments;
        this.browser = browser;
        this.clients = clients;
        // A status holds for the ProofEnvironment it was obtained in and for no other, so a
        // context that is loaded again starts with an empty cache.
        environments.onDiscard(contextId -> knowledgeOf(contextId).reloaded());
    }

    @Override
    public CompletableFuture<ProveResult> prove(ProveParams params) {
        String runId = params.runId();
        if (runId == null || runId.isBlank()) {
            throw BridgeErrors.failure(BridgeErrors.RUN_NOT_NAMED,
                "A run has to be named, so that its progress can be told from another's.");
        }
        Run run = new Run();
        if (runs.putIfAbsent(runId, run) != null) {
            throw BridgeErrors.failure(BridgeErrors.RUN_NOT_NAMED,
                "A run named " + runId + " is already going.");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return runProofs(params, run);
            } finally {
                runs.remove(runId);
            }
        }, provingIn(params.contextId()));
    }

    /**
     * The thread that runs the proofs of one context. It is created the first time that
     * context is proved.
     *
     * @param contextId the context
     * @return its thread
     */
    private ExecutorService provingIn(String contextId) {
        return proving.computeIfAbsent(contextId, id -> Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(() -> {
                    PROVING_FOR.set(id);
                    runnable.run();
                }, "key-ide-proving-" + id);
                thread.setDaemon(true);
                return thread;
            }));
    }

    /**
     * The context whose proofs this thread builds, or null on every other thread.
     * <p>
     * Work that builds proofs is submitted to that thread and waited for. Submitting it from
     * the thread itself would wait on a queue only that thread can empty, so this records
     * which context the current thread serves.
     */
    private static final ThreadLocal<String> PROVING_FOR = new ThreadLocal<>();

    /** A single run: whether it has been asked to stop, and what it is proving. */
    private static final class Run {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile ProofRunner runner;
    }

    @Override
    public CompletableFuture<ProveResult> replay(ObligationsParams params) {
        String contextId = params.contextId();
        List<ProofObligations.Obligation> wanted = selected(params);
        List<ProofRunner.Outcome> keyOutcomes = readSaved(contextId, wanted);
        List<ProofOutcomeDto> outcomes = new ArrayList<>(judged(contextId, keyOutcomes));
        // An obligation with no saved proof is reported as such rather than left out, since
        // the caller asked about it.
        Set<String> read = keyOutcomes.stream().map(ProofRunner.Outcome::contractName)
                .collect(java.util.stream.Collectors.toSet());
        wanted.stream()
                .filter(obligation -> !read.contains(obligation.contract().getName()))
                .forEach(obligation -> outcomes.add(mapper().noSavedProof(obligation)));
        return CompletableFuture.completedFuture(new ProveResult(outcomes, false));
    }

    @Override
    public CompletableFuture<RemovedResult> removeProof(ObligationsParams params) {
        ProofRunner runner = runnerFor(params.contextId());
        int removed = 0;
        for (ProofObligations.Obligation obligation : selected(params)) {
            try {
                if (runner.removeProof(obligation)) {
                    removed++;
                }
            } catch (IOException e) {
                throw BridgeErrors.failure(BridgeErrors.CONFIG_UNREADABLE,
                    "The proof at " + mapper().relative(obligation.proofFile()) + " could not be removed: "
                        + e.getMessage());
            }
        }
        // The files are gone. The cached status of these proofs, and of the proofs that
        // used them, is invalidated on the next query.
        announceChange(params.contextId());
        return CompletableFuture.completedFuture(new RemovedResult(removed));
    }

    /** Notifies the IDE that its listing of a context is out of date. */
    private void announceChange(String contextId) {
        IdeClient client = clients.get();
        if (client != null) {
            client.obligationsChanged(new ObligationsChangedDto(contextId));
        }
    }

    /**
     * Maps what the bridge holds into wire form.
     * <p>
     * Created on use rather than kept, since it needs the project root, which is not known
     * until {@code initialize} has run.
     */
    private VerificationMapper mapper() {
        return new VerificationMapper(session.projectRoot());
    }

    /** A runner over a context, for the operations that act on its proofs. */
    private ProofRunner runnerFor(String contextId) {
        return new ProofRunner(environmentFor(contextId),
            ProjectContexts.resolved(session, contextId), proofLayout(),
            environments.loadedChoices(contextId));
    }

    /**
     * Where this project stores its proofs.
     *
     * @return the layout named by the configuration, or the default layout
     */
    private ProofFiles proofLayout() {
        return new ProofFiles(session.projectRoot(),
            ProjectContexts.read(session).proofDirectory());
    }

    /** The obligations a request names, or all obligations of the context if it names none. */
    private List<ProofObligations.Obligation> selected(ObligationsParams params) {
        return select(obligationsOf(params.contextId()), params.contractNames());
    }

    @Override
    public CompletableFuture<Void> cancel(CancelParams params) {
        // A run that has already finished needs no stopping, and is not an error.
        Run run = runs.get(params.runId());
        if (run != null) {
            run.cancelled.set(true);
            ProofRunner runner = run.runner;
            if (runner != null) {
                runner.cancel();
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Attempts each obligation in turn, reporting progress and stopping when asked.
     *
     * @param params the obligations to attempt
     * @param run the run to report progress for, and to check for a request to stop
     * @return the result of each attempt, and whether the run was stopped
     */
    private ProveResult runProofs(ProveParams params, Run run) {
        ProjectConfig config = ProjectContexts.read(session);
        // The prover is a property of this KeY rather than of a proof, so the project
        // configures it once and it is set for the run rather than per obligation.
        AppliedOptions.applyProver(config.prover());
        ProofObligations obligations = obligationsOf(params.contextId());
        List<ProofObligations.Obligation> wanted = select(obligations, params.contractNames());
        ProofRunner runner = runnerFor(params.contextId());
        run.runner = runner;

        List<ProofRunner.Attempt> attempts = new ArrayList<>();
        List<ProofRunner.Outcome> keyOutcomes = List.of();
        try {
            for (ProofObligations.Obligation obligation : wanted) {
                if (run.cancelled.get()) {
                    break;
                }
                report(params.runId(), params.contextId(), obligation.contract().getName(),
                    attempts.size(), wanted.size());
                attempts.add(runner.prove(obligation,
                    config.optionsFor(params.contextId(), obligation.contract().getName())));
            }
            // Queried while KeY still holds every proof of the run and before they are
            // disposed: whether a proof is closed or closed but for lemmas is decided by
            // KeY from the proofs it holds.
            keyOutcomes = outcomesOf(attempts);
        } finally {
            run.runner = null;
            runner.release();
        }
        List<ProofOutcomeDto> outcomes = judged(params.contextId(), keyOutcomes);
        // A run saves proofs that were not there before, so every listing of them is out of
        // date.
        announceChange(params.contextId());
        return new ProveResult(outcomes, run.cancelled.get());
    }

    /**
     * Caches what KeY reported and answers with the status that then stands.
     * <p>
     * KeY judged these proofs against the ones it held, which is every proof of a run and
     * every saved proof a replay read. Where that was not every saved proof of the context,
     * a proof it called closed but for lemmas may rest on a proof it did not hold, and the
     * knowledge asks KeY again with all of them loaded. The caller is told that answer.
     *
     * @param contextId the context the proofs belong to
     * @param keyOutcomes the status KeY reported for each proof
     * @return the outcomes as the IDE is told them
     */
    private List<ProofOutcomeDto> judged(String contextId,
            List<ProofRunner.Outcome> keyOutcomes) {
        List<ContextKnowledge.OnDisk> onDisk =
            VerificationMapper.onDisk(obligationsOf(contextId).list());
        ContextKnowledge context = knowledgeOf(contextId);
        context.keyJudged(keyOutcomes.stream().map(VerificationMapper::verdict).toList(), onDisk);
        Map<String, String> state = context.stateOf(onDisk);
        return keyOutcomes.stream()
                .map(outcome -> mapper().outcome(outcome,
                    state.getOrDefault(outcome.contractName(), outcome.status().name())))
                .toList();
    }

    /** The obligations asked for, or all obligations of the context if none were named. */
    private List<ProofObligations.Obligation> select(ProofObligations obligations,
            List<String> contractNames) {
        List<ProofObligations.Obligation> all = obligations.list();
        if (contractNames == null || contractNames.isEmpty()) {
            return all;
        }
        return all.stream().filter(o -> contractNames.contains(o.contract().getName())).toList();
    }

    private void report(String runId, String contextId, String contractName, int completed,
            int total) {
        IdeClient client = clients.get();
        if (client != null) {
            client.proveProgress(
                new ProveProgressDto(runId, contextId, contractName, completed, total));
        }
    }

    @Override
    public CompletableFuture<MethodDto> resolveAt(ResolveParams params) {
        return CompletableFuture.completedFuture(toDto(resolve(params)));
    }

    @Override
    public CompletableFuture<Void> browse(BrowseParams params) {
        KeYEnvironment<?> environment = environmentFor(params.contextId());
        ResolvedMethod method =
            new MethodResolver(environment.getJavaInfo())
                    .find(params.className(), params.name(), params.parameterTypes())
                    .orElseThrow(() -> BridgeErrors.failure(BridgeErrors.METHOD_NOT_FOUND,
                        "The loaded context holds no method " + params.className() + "#"
                            + params.name() + params.parameterTypes() + "."));
        show(params.contextId(), environment, method);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<MethodDto> verifyAt(ResolveParams params) {
        ResolvedMethod method = resolve(params);
        show(params.contextId(), environmentFor(params.contextId()), method);
        return CompletableFuture.completedFuture(toDto(method));
    }

    /**
     * Finds the method at a position, failing with a message the IDE can show.
     *
     * @param params the file and position
     * @return the method found
     */
    private ResolvedMethod resolve(ResolveParams params) {
        KeYEnvironment<?> environment = environmentFor(params.contextId());
        URI file = parse(params.uri());
        return new MethodResolver(environment.getJavaInfo())
                .resolveAt(file, params.line(), params.column())
                .orElseThrow(() -> BridgeErrors.failure(BridgeErrors.NO_METHOD_AT_POSITION,
                    "No method of context '" + params.contextId() + "' covers line "
                        + params.line() + " of " + params.uri() + "."));
    }

    @Override
    public CompletableFuture<ObligationsResult> list(ListObligationsParams params) {
        String contextId = params.contextId();
        ProjectConfig config = ProjectContexts.read(session);
        Profile profile = environmentFor(contextId).getInitConfig().getProfile();
        Map<String, String> loaded = environments.loadedChoices(contextId);
        List<ProofObligations.Obligation> listed = obligationsOf(contextId).list();
        Map<String, String> state = knowledgeOf(contextId).stateOf(VerificationMapper.onDisk(listed));
        List<ObligationDto> obligations = listed.stream()
                .map(obligation -> mapper().obligation(obligation,
                    state.getOrDefault(obligation.contract().getName(),
                        obligation.status().name()),
                    VerificationMapper.differingSettings(obligation, config.optionsFor(contextId,
                        obligation.contract().getName()), loaded, profile)))
                .toList();
        return CompletableFuture.completedFuture(new ObligationsResult(obligations));
    }

    @Override
    public CompletableFuture<Void> start(StartParams params) {
        KeYEnvironment<?> environment = environmentFor(params.contextId());
        Contract contract = obligationsOf(params.contextId())
                .contractNamed(params.contractName())
                .orElseThrow(() -> BridgeErrors.failure(BridgeErrors.METHOD_NOT_FOUND,
                    "The context '" + params.contextId() + "' declares no contract named '"
                        + params.contractName() + "'."));
        ProofObligations obligations = obligationsOf(params.contextId());
        browser.show(environment.getInitConfig(), contract.getKJT(), contract.getTarget(),
            obligations::assignProofFiles);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<DependenciesResult> dependencies(ListObligationsParams params) {
        Map<String, List<String>> reported = knowledgeOf(params.contextId()).usedContracts();
        List<UsedContractsDto> obligations = reported.entrySet().stream()
                .map(entry -> new UsedContractsDto(entry.getKey(), true, entry.getValue()))
                .toList();
        return CompletableFuture.completedFuture(new DependenciesResult(obligations));
    }

    @Override
    public CompletableFuture<StaleOptionsResult> staleOptions(ListObligationsParams params) {
        return CompletableFuture.supplyAsync(
            () -> new StaleOptionsResult(staleOptionsOf(params.contextId())));
    }

    @Override
    public CompletableFuture<StaleOptionsResult> removeStaleOptions(ListObligationsParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String contextId = params.contextId();
            List<String> stale = staleOptionsOf(contextId);
            if (!stale.isEmpty()) {
                ProjectConfig config = ProjectContexts.read(session);
                try {
                    new ConfigStore(session.projectRoot())
                            .write(config.withoutObligationOptions(contextId, stale));
                } catch (IOException e) {
                    throw BridgeErrors.failure(BridgeErrors.CONFIG_UNREADABLE, e.getMessage());
                }
            }
            return new StaleOptionsResult(stale);
        });
    }

    /**
     * The obligations of a context that have settings configured but no longer exist.
     * <p>
     * Settings are stored by contract name. Renaming or removing a method leaves its
     * settings under a name that no longer resolves. They are not removed automatically,
     * since a method may be absent only temporarily.
     *
     * @param contextId the context to look through
     * @return the names, sorted
     */
    private List<String> staleOptionsOf(String contextId) {
        Map<String, ProofOptions> stated =
            ProjectContexts.read(session).obligationOptions().getOrDefault(contextId, Map.of());
        if (stated.isEmpty()) {
            return List.of();
        }
        java.util.Set<String> existing = obligationsOf(contextId).list().stream()
                .map(o -> o.contract().getName())
                .collect(java.util.stream.Collectors.toSet());
        return stated.keySet().stream().filter(name -> !existing.contains(name)).sorted().toList();
    }

    @Override
    public CompletableFuture<PreparedResult> prepare(StartParams params) {
        String contextId = params.contextId();
        List<ProofObligations.Obligation> wanted =
            select(obligationsOf(contextId), List.of(params.contractName()));
        if (wanted.isEmpty()) {
            throw BridgeErrors.failure(BridgeErrors.METHOD_NOT_FOUND,
                "The context '" + contextId + "' declares no contract named '"
                    + params.contractName() + "'.");
        }
        ProofObligations.Obligation obligation = wanted.get(0);
        ProofOptions options =
            ProjectContexts.read(session).optionsFor(contextId, obligation.contract().getName());
        // Prepared on the context's proving thread, since two threads must not build proofs
        // of one context at the same time.
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path saved = runnerFor(contextId).prepare(obligation, options);
                announceChange(contextId);
                return new PreparedResult(mapper().relative(saved));
            } catch (ProofInputException | IOException e) {
                throw BridgeErrors.failure(BridgeErrors.ENVIRONMENT_LOAD_FAILED,
                    "The proof could not be prepared: " + e.getMessage());
            }
        }, provingIn(contextId));
    }

    @Override
    public CompletableFuture<PositionResult> at(PositionParams params) {
        Path file = fileOf(params.uri());
        String contextId = contextHolding(file);
        if (contextId == null) {
            return CompletableFuture.completedFuture(new PositionResult(null, List.of(), ""));
        }
        List<ProofObligations.Obligation> ofFile = obligationsOf(contextId).list().stream()
                .filter(obligation -> session.projectRoot().resolve(obligation.sourceFile())
                        .normalize().equals(file))
                .toList();

        // A caret inside a method means that method. A caret anywhere else in the file means
        // the whole file, and so does a method the file declares no contract about.
        Optional<ResolvedMethod> method =
            new MethodResolver(environmentFor(contextId).getJavaInfo())
                    .resolveAt(java.net.URI.create(file.toUri().toString()), params.line(),
                        params.column());
        if (method.isPresent()) {
            List<ProofObligations.Obligation> about =
                ProofObligations.about(ofFile, method.get().method());
            if (!about.isEmpty()) {
                return CompletableFuture.completedFuture(new PositionResult(contextId,
                    names(about), signatureOf(method.get())));
            }
        }
        return CompletableFuture.completedFuture(new PositionResult(contextId, names(ofFile),
            file.getFileName().toString()));
    }

    /** The contract names of obligations, which is how a request names what to act on. */
    private static List<String> names(List<ProofObligations.Obligation> obligations) {
        return obligations.stream().map(obligation -> obligation.contract().getName()).toList();
    }

    /** How a method reads to a user, which is what an action names in its text. */
    private static String signatureOf(ResolvedMethod method) {
        return method.className() + "." + method.name() + "("
            + String.join(", ", method.parameterTypes()) + ")";
    }

    @Override
    public CompletableFuture<MarksResult> marks(MarksParams params) {
        Path file = fileOf(params.uri());
        String contextId = contextHolding(file);
        if (contextId == null) {
            return CompletableFuture.completedFuture(new MarksResult(null, List.of()));
        }
        List<ObligationDto> obligations = list(new ListObligationsParams(contextId)).join()
                .obligations();
        return CompletableFuture.completedFuture(new MarksResult(contextId,
            SourceMarks.of(file, session.projectRoot(), obligations)));
    }

    /** The context whose sources hold a file, preferring the most specific one. */
    private String contextHolding(Path file) {
        Path root = session.projectRoot();
        String best = null;
        int bestDepth = -1;
        for (VerificationContext context : ProjectContexts.read(session).contexts()) {
            Path source = root.resolve(context.javaSource()).toAbsolutePath().normalize();
            if (file.startsWith(source) && source.getNameCount() > bestDepth) {
                best = context.id();
                bestDepth = source.getNameCount();
            }
        }
        return best;
    }

    /** A file named either as a {@code file:} URI or as a path. */
    private static Path fileOf(String uri) {
        try {
            return Path.of(java.net.URI.create(uri)).toAbsolutePath().normalize();
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            return Path.of(uri).toAbsolutePath().normalize();
        }
    }

    @Override
    public CompletableFuture<IconsResult> icons(IconsParams params) {
        int size = params == null || params.size() <= 0 ? DEFAULT_ICON_SIZE : params.size();
        return CompletableFuture.completedFuture(new IconsResult(StatusIcons.asDataUris(size),
            StatusIcons.asDarkDataUris(size)));
    }

    /**
     * The obligations of a context, loading it if needed.
     *
     * @param contextId the context to list
     * @return a view over its contracts
     */
    private ProofObligations obligationsOf(String contextId) {
        VerificationContext context = ProjectContexts.resolved(session, contextId);
        return new ProofObligations(environmentFor(contextId), context, proofLayout());
    }

    /**
     * Opens the browser and, once it closes, saves any proof the user started to the file
     * the proof layout expects.
     *
     * @param contextId the context the method belongs to
     * @param environment the loaded context
     * @param method the method to select
     */
    private void show(String contextId, KeYEnvironment<?> environment, ResolvedMethod method) {
        ProofObligations obligations = obligationsOf(contextId);
        browser.show(environment.getInitConfig(), method.type(), method.method(),
            obligations::assignProofFiles);
    }

    @Override
    public CompletableFuture<AvailableOptionsDto> availableOptions(AvailableOptionsParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String contextId = params == null ? null : params.contextId();
            // Which options exist is KeY's answer rather than the project's: the taclet
            // options come from the rule files and the strategy options from the profile.
            // A context that is already loaded is used, since it knows what it was loaded
            // with; one that is not is left alone rather than loaded, because a settings
            // page would then wait for the whole project to be parsed, and a project that
            // declares no context yet would have no options to show at all.
            Optional<InitConfig> loaded = environments.configOf(contextId);
            if (loaded.isPresent()) {
                return AvailableOptions.of(loaded.get(), environments.loadedChoices(contextId));
            }
            InitConfig rules = rulesOnly();
            return AvailableOptions.of(rules, AvailableOptions.chosen(rules));
        });
    }

    /**
     * KeY's rules, read without a project.
     *
     * @return the configuration the rule files were read into
     */
    private static InitConfig rulesOnly() {
        try {
            return RuleBase.initConfig();
        } catch (ProblemLoaderException e) {
            throw BridgeErrors.failure(BridgeErrors.ENVIRONMENT_LOAD_FAILED,
                "KeY could not read its own rules: " + rootMessage(e));
        }
    }

    private KeYEnvironment<?> environmentFor(String contextId) {
        VerificationContext context = ProjectContexts.resolved(session, contextId);
        try {
            return environments.environmentFor(context);
        } catch (ProblemLoaderException e) {
            // KeY says where the Java or the JML went wrong. That goes to the editor as
            // places to mark, and the message names the first of them.
            throw BridgeErrors.loadFailed(contextId, SourceProblems.of(e));
        }
    }

    /**
     * The message to show from a chain of exceptions.
     * <p>
     * KeY reports a failed load as "Load failed" and puts what actually happened in the
     * cause, which is the part a user can act on.
     *
     * @param failure the exception to describe
     * @return the deepest message in the chain
     */
    private static String rootMessage(Throwable failure) {
        Throwable deepest = failure;
        while (deepest.getCause() != null) {
            deepest = deepest.getCause();
        }
        String message = deepest.getMessage();
        return message == null || message.isBlank() ? deepest.toString() : message;
    }

    private static URI parse(String uri) {
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException e) {
            throw BridgeErrors.failure(BridgeErrors.NO_METHOD_AT_POSITION,
                "The location '" + uri + "' is not a URI.");
        }
    }

    private static MethodDto toDto(ResolvedMethod method) {
        return new MethodDto(method.className(), method.name(), method.parameterTypes(),
            method.isConstructor(), method.startLine(), method.endLine());
    }
}
