/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.init.KeYUserProblemFile;
import de.uka.ilkd.key.proof.init.ProblemInitializer;
import de.uka.ilkd.key.proof.io.IntermediatePresentationProofFileParser;
import de.uka.ilkd.key.proof.io.IntermediateProofReplayer;
import de.uka.ilkd.key.proof.io.SingleThreadProblemLoader;
import de.uka.ilkd.key.speclang.Contract;
import de.uka.ilkd.key.strategy.Strategy;
import de.uka.ilkd.key.strategy.StrategyProperties;
import de.uka.ilkd.key.rule.OneStepSimplifier;

import org.key_project.ide.config.VerificationContext;

/**
 * Loads saved proofs into one ProofEnvironment, so that KeY can relate them to each other.
 * <p>
 * Whether a proof is closed or closed only but for lemmas follows from the proofs in the
 * same specification repository. Loading each proof file separately creates one environment
 * per file, so a proof that used another contract is always reported as depending on a
 * proof KeY cannot see. Loaded into the environment of the context, all of them are
 * related as they are in KeY's own window.
 * <p>
 * Each proof is created from the context, as it is for a fresh attempt, and the proof steps
 * stored in the file are replayed into it. This is what KeY's loader does once it has an
 * environment; only the environment differs.
 */
public final class ProofsTogether {

    private final KeYEnvironment<?> environment;
    private final VerificationContext context;

    /**
     * @param environment the loaded context, into whose repository the proofs are loaded
     * @param context the context, with absolute paths
     */
    public ProofsTogether(KeYEnvironment<?> environment, VerificationContext context) {
        this.environment = environment;
        this.context = context;
    }

    /**
     * The result of loading one saved proof.
     *
     * @param obligation the proof obligation it belongs to
     * @param proof the proof in the context's environment, or null if the file could not be
     *        loaded
     * @param message a message for the user, empty if there is none
     */
    public record Read(ProofObligations.Obligation obligation, Proof proof, String message) {

        /** The status of this proof as KeY reports it, relative to the loaded proofs. */
        public ProofRunner.Outcome outcome(long milliseconds) {
            String contractName = obligation.contract().getName();
            if (proof == null) {
                return new ProofRunner.Outcome(contractName, ProofObligations.Status.UNKNOWN, 0,
                    0, milliseconds, obligation.proofFile(), message);
            }
            return new ProofRunner.Outcome(contractName, ProofVerdict.statusOf(proof),
                proof.countNodes(), proof.countBranches(), milliseconds, obligation.proofFile(),
                message, ProofVerdict.usedContracts(proof));
        }
    }

    /** Disposes the loaded proofs, once KeY has been queried about them. */
    public static void release(List<Read> read) {
        read.forEach(one -> {
            if (one.proof() != null && !one.proof().isDisposed()) {
                one.proof().dispose();
            }
        });
    }

    /**
     * Loads the saved proofs of these proof obligations into the context's environment.
     * <p>
     * They remain loaded until {@link #release(List)} is called, since KeY can be queried
     * only about loaded proofs.
     *
     * @param obligations the proof obligations whose proofs to load
     * @return one entry per proof obligation that has a saved proof
     */
    public List<Read> readAll(List<ProofObligations.Obligation> obligations) {
        List<Read> read = new ArrayList<>();
        for (ProofObligations.Obligation obligation : obligations) {
            if (Files.isRegularFile(obligation.proofFile())) {
                read.add(read(obligation));
            }
        }
        return read;
    }

    /** Loads one saved proof into the context's environment. */
    private Read read(ProofObligations.Obligation obligation) {
        Contract contract = obligation.contract();
        Proof proof;
        try {
            proof = environment.createProof(contract.createProofObl(environment.getInitConfig()));
        } catch (Exception e) {
            return new Read(obligation, null,
                "The proof obligation could not be built: " + e.getMessage());
        }
        try {
            return new Read(obligation, proof, replay(proof, obligation.proofFile()));
        } catch (Exception e) {
            proof.dispose();
            return new Read(obligation, null, "The proof could not be read: " + e.getMessage());
        }
    }

    /**
     * Replays the proof steps stored in a file into a proof of this environment.
     *
     * @param proof the proof to replay into, at its first open goal
     * @param proofFile the file holding the proof steps
     * @return the replay status reported by KeY, empty if it reports none
     * @throws Exception if the file cannot be read
     */
    private String replay(Proof proof, Path proofFile) throws Exception {
        KeYUserProblemFile file = new KeYUserProblemFile(proofFile.toString(), proofFile, null,
            environment.getInitConfig().getProfile());
        ProblemInitializer initializer =
            new ProblemInitializer(environment.getInitConfig().getProfile());

        var parser = new IntermediatePresentationProofFileParser(proof);
        initializer.tryReadProof(parser, file);

        // KeY enables one step simplification for a replay, so that a proof recorded with
        // it can be replayed by a user who has since disabled it.
        StrategyProperties properties =
            proof.getSettings().getStrategySettings().getActiveStrategyProperties();
        properties.setProperty(StrategyProperties.OSS_OPTIONS_KEY, StrategyProperties.OSS_ON);
        Strategy.updateStrategySettings(proof, properties);
        OneStepSimplifier.refreshOSS(proof);

        var replayer = new IntermediateProofReplayer(loaderFor(proofFile), proof,
            parser.getResult());
        var result = replayer.replay(null, null);
        return result.getStatus() == null ? "" : result.getStatus();
    }

    /**
     * A loader for a file. The replayer uses it only to attribute errors to that file.
     *
     * @param proofFile the file being replayed
     * @return a loader that is never used to load anything
     */
    private SingleThreadProblemLoader loaderFor(Path proofFile) {
        return new SingleThreadProblemLoader(proofFile,
            context.classpath().isEmpty() ? null : context.classpath(), context.bootclasspath(),
            context.includes().isEmpty() ? null : context.includes(),
            environment.getInitConfig().getProfile(), false, null, false, null);
    }
}
