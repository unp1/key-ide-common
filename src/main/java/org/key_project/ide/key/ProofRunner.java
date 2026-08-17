/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.init.ProofInputException;
import de.uka.ilkd.key.proof.io.ProofSaver;
import de.uka.ilkd.key.speclang.Contract;
import de.uka.ilkd.key.util.KeYConstants;

import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.config.VerificationContext;

/**
 * Runs KeY's automatic strategy on proof obligations of one context, headless.
 * <p>
 * This is the ordinary way a proof is attempted; the result is saved to the proof file.
 * KeY's window is for inspecting a proof afterwards, not for creating one.
 */
public final class ProofRunner {

    private static final Logger LOGGER = System.getLogger(ProofRunner.class.getName());

    /**
     * Serialises the creation of proofs across all contexts.
     * <p>
     * Creating a proof applies the taclet options to the context's InitConfig and writes
     * KeY's global settings, so two contexts creating proofs at the same time would
     * overwrite each other's choices. Creation is short; the proof search that follows is
     * not, and runs in parallel.
     */
    private static final Object BUILDING = new Object();

    /**
     * The result of one proof attempt.
     *
     * @param contractName the contract that was attempted
     * @param status the resulting status, see {@link ProofObligations.Status}
     * @param nodes the number of proof nodes
     * @param branches the number of proof branches
     * @param milliseconds the duration of the attempt
     * @param proofFile the file the proof was written to
     * @param message a message for the user, empty if there is none
     */
    public record Outcome(String contractName, ProofObligations.Status status, int nodes,
            int branches, long milliseconds, Path proofFile, String message,
            List<String> usedContracts) {

        /**
         * An outcome for an attempt that used no other contract, or where the used
         * contracts are unknown.
         */
        public Outcome(String contractName, ProofObligations.Status status, int nodes,
                int branches, long milliseconds, Path proofFile, String message) {
            this(contractName, status, nodes, branches, milliseconds, proofFile, message,
                List.of());
        }

        public Outcome {
            usedContracts = usedContracts == null ? List.of() : List.copyOf(usedContracts);
        }
    }

    private final KeYEnvironment<?> environment;
    private final VerificationContext context;
    private final ProofFiles proofs;

    /** The taclet options KeY chose when it read the context, before any proof was built. */
    private final Map<String, String> loadedChoices;

    /** The proofs of the run in progress, held so that KeY can be asked about them. */
    private final List<Proof> held = new ArrayList<>();

    /**
     * @param environment the loaded context
     * @param context the context, with absolute paths
     * @param proofs where proofs are stored
     * @param loadedChoices the taclet options KeY selected while loading the context
     */
    public ProofRunner(KeYEnvironment<?> environment, VerificationContext context,
            ProofFiles proofs, Map<String, String> loadedChoices) {
        this.environment = environment;
        this.context = context;
        this.proofs = proofs;
        this.loadedChoices = Map.copyOf(loadedChoices);
    }

    /**
     * Attempts a contract, saves the resulting proof, and keeps it loaded.
     * <p>
     * The proof stays in the context's specification repository until the run ends, because
     * KeY can be queried only about loaded proofs, and whether a proof is closed or closed
     * only but for lemmas follows from the other proofs in that repository. Disposing it
     * immediately would leave that question unanswerable. {@link #release()} ends the run
     * and disposes the proofs.
     *
     * @param obligation the proof obligation to attempt
     * @param options the settings to attempt it with
     * @return the attempt, to be queried once the run has ended
     */
    public Attempt prove(ProofObligations.Obligation obligation, ProofOptions options) {
        Contract contract = obligation.contract();
        long started = System.currentTimeMillis();
        Proof proof;
        try {
            proof = build(contract, options);
        } catch (ProofInputException e) {
            return new Attempt(obligation, null, 0,
                "The proof obligation could not be built: " + e.getMessage());
        }
        held.add(proof);

        AppliedOptions.applyStrategyOptions(proof, options);
        environment.getProofControl().startAndWaitForAutoMode(proof);
        long took = System.currentTimeMillis() - started;

        return new Attempt(obligation, proof, took, save(proof, obligation.proofFile()));
    }

    /**
     * One attempt, still loaded and ready to be queried.
     *
     * @param obligation the proof obligation that was attempted
     * @param proof the proof KeY created, or null if it could not be created
     * @param milliseconds the duration of the attempt
     * @param message a message for the user, empty if there is none
     */
    public record Attempt(ProofObligations.Obligation obligation, Proof proof, long milliseconds,
            String message) {

        /** The status KeY reports for this attempt, against the proofs KeY holds now. */
        public Outcome outcome() {
            String contractName = obligation.contract().getName();
            if (proof == null) {
                return new Outcome(contractName, ProofObligations.Status.UNKNOWN, 0, 0,
                    milliseconds, obligation.proofFile(), message);
            }
            return new Outcome(contractName, ProofVerdict.statusOf(proof), proof.countNodes(),
                proof.countBranches(), milliseconds, obligation.proofFile(), message,
                ProofVerdict.usedContracts(proof));
        }
    }

    /**
     * Disposes the proofs this runner keeps loaded.
     * <p>
     * A proof holds its entire proof tree, so they are kept only as long as KeY has to be
     * queryable about them: until the run has ended and its outcomes have been read.
     */
    public void release() {
        held.forEach(proof -> {
            if (!proof.isDisposed()) {
                proof.dispose();
            }
        });
        held.clear();
    }

    /**
     * Creates a proof and saves it without running the strategy, so that KeY's window can
     * open it.
     * <p>
     * A window opening a saved proof reads the settings from the file, so this is how a
     * proof reaches the window with the settings configured for its proof obligation. The
     * file contains the settings and the first open goal, nothing else. A previously saved
     * proof is moved to the trash, as when a proof is attempted again.
     *
     * @param obligation the proof obligation to prepare
     * @param options the settings to configure the proof with
     * @return the file the proof was saved to
     * @throws ProofInputException if the proof obligation cannot be created
     * @throws IOException if the proof cannot be saved
     */
    public Path prepare(ProofObligations.Obligation obligation, ProofOptions options)
            throws ProofInputException, IOException {
        Proof proof = build(obligation.contract(), options);
        try {
            AppliedOptions.applyStrategyOptions(proof, options);
            String failure = save(proof, obligation.proofFile());
            if (!failure.isEmpty()) {
                throw new IOException(failure);
            }
            return obligation.proofFile();
        } finally {
            proof.dispose();
        }
    }

    /**
     * Creates the proof with the taclet options it is to be attempted with.
     * <p>
     * The available taclets follow from the InitConfig the proof is created from, so the
     * options are applied first and the proof is created immediately afterwards. Creation
     * is serialised across the bridge, see {@link #BUILDING}.
     *
     * @param contract the contract to create a proof obligation for
     * @param options the settings to configure the proof with
     * @return the proof, at its first open goal
     * @throws ProofInputException if the proof obligation cannot be created
     */
    private Proof build(Contract contract, ProofOptions options) throws ProofInputException {
        synchronized (BUILDING) {
            AppliedOptions.applyTacletOptions(environment.getInitConfig(), options,
                loadedChoices);
            return environment.createProof(contract.createProofObl(environment.getInitConfig()));
        }
    }

    /**
     * Deletes the saved proof of a proof obligation.
     *
     * @param obligation the proof obligation whose proof file to delete
     * @return whether a file existed and was deleted
     * @throws IOException if the file exists but cannot be deleted
     */
    public boolean removeProof(ProofObligations.Obligation obligation) throws IOException {
        return Files.deleteIfExists(obligation.proofFile());
    }

    /** Stops a run in progress, so a user who asked for one can take it back. */
    public void cancel() {
        environment.getProofControl().stopAutoMode();
    }

    /**
     * Writes the proof, moving a previously saved one to the trash.
     *
     * @param proof the proof to write
     * @param target the file to write it to
     * @return a message for the user, empty if saving succeeded
     */
    private String save(Proof proof, Path target) {
        try {
            ProofTrash.moveToTrash(proofs.root(), target).ifPresent(
                archived -> LOGGER.log(Level.INFO, "Kept the earlier proof at " + archived));
            Files.createDirectories(target.getParent());
            proof.setProofFile(target);
            String failure = new ProofSaver(proof, target, KeYConstants.INTERNAL_VERSION).save();
            return failure == null ? "" : "The proof could not be saved: " + failure;
        } catch (IOException e) {
            return "The proof could not be saved: " + e.getMessage();
        }
    }

    /** The context these runs belong to. */
    public VerificationContext context() {
        return context;
    }
}
