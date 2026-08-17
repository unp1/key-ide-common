/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The contents of a project's {@code .key/settings.json}.
 *
 * @param version the schema version, so later readers can migrate older files
 * @param contexts the verification contexts, in the order they appear in the file
 * @param proofDirectory the directory where the project stores its proofs, relative to
 *        its root
 * @param options the settings proofs are attempted with, unless a context or an obligation
 *        says otherwise
 * @param prover which prover runs the proofs, which is a property of the bridge and so of
 *        the project alone
 * @param obligationOptions what individual obligations say, keyed by context and contract
 */
public record ProjectConfig(int version, List<VerificationContext> contexts,
        String proofDirectory, ProofOptions options, ProverOptions prover,
        Map<String, Map<String, ProofOptions>> obligationOptions) {

    /** The schema version this implementation writes. */
    public static final int CURRENT_VERSION = 1;

    /**
     * The directory used when a configuration names none.
     * <p>
     * Declared here rather than next to the proof layout: the configuration part of the
     * bridge runs without KeY and must not depend on any class that requires it.
     */
    public static final String DEFAULT_PROOF_DIRECTORY = "proofs";

    public ProjectConfig {
        contexts = List.copyOf(contexts);
        proofDirectory = proofDirectory == null || proofDirectory.isBlank()
                ? DEFAULT_PROOF_DIRECTORY
                : proofDirectory;
        options = options == null ? ProofOptions.NONE : options;
        prover = prover == null ? ProverOptions.DEFAULT : prover;
        obligationOptions = obligationOptions == null ? Map.of() : deepCopy(obligationOptions);
    }

    private static Map<String, Map<String, ProofOptions>> deepCopy(
            Map<String, Map<String, ProofOptions>> source) {
        Map<String, Map<String, ProofOptions>> copy = new LinkedHashMap<>();
        source.forEach((contextId, byContract) -> copy.put(contextId, Map.copyOf(byContract)));
        return Map.copyOf(copy);
    }

    /**
     * A configuration with settings but none configured per proof obligation.
     *
     * @param version the schema version
     * @param contexts the verification contexts
     * @param proofDirectory where proofs are stored
     */
    public ProjectConfig(int version, List<VerificationContext> contexts,
            String proofDirectory) {
        this(version, contexts, proofDirectory, ProofOptions.NONE, ProverOptions.DEFAULT,
            Map.of());
    }

    /**
     * The settings a proof obligation is attempted with.
     * <p>
     * What the project says, with what its context says laid over it, and then what the
     * obligation itself says. Each level states only its differences.
     *
     * @param contextId the context the obligation belongs to
     * @param contractName the contract, or null for the settings of the context itself
     * @return the settings to attempt it with
     */
    public ProofOptions optionsFor(String contextId, String contractName) {
        ProofOptions effective = options.mergedWith(
            context(contextId).map(VerificationContext::options).orElse(null));
        if (contractName == null) {
            return effective;
        }
        Map<String, ProofOptions> ofContext = obligationOptions.get(contextId);
        return effective.mergedWith(ofContext == null ? null : ofContext.get(contractName));
    }

    /**
     * A configuration that uses the default proof directory.
     *
     * @param version the schema version
     * @param contexts the verification contexts
     */
    public ProjectConfig(int version, List<VerificationContext> contexts) {
        this(version, contexts, DEFAULT_PROOF_DIRECTORY);
    }

    /** A configuration with no contexts, used for a project that has none yet. */
    public static ProjectConfig empty() {
        return new ProjectConfig(CURRENT_VERSION, List.of());
    }

    /**
     * Looks up a context by its identifier.
     *
     * @param id the identifier to search for
     * @return the context, or empty if the project declares no context with that id
     */
    public Optional<VerificationContext> context(String id) {
        return contexts.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /**
     * The configuration with an edit applied at one level.
     * <p>
     * Which level is edited follows from what the caller names: the project when it names
     * no context, that context when it names one but no obligation, and each named
     * obligation otherwise. Naming several obligations edits each of them, which is what
     * makes editing a selection differ from editing one of them and copying the result.
     *
     * @param contextId the context to edit, or null for the project
     * @param contractNames the obligations to edit, empty to edit the level itself
     * @param change what the form touched
     * @return the edited configuration
     */
    public ProjectConfig withOptions(String contextId, List<String> contractNames,
            OptionChange change) {
        if (contextId == null) {
            return new ProjectConfig(version, contexts, proofDirectory, change.applyTo(options),
                prover, obligationOptions);
        }
        if (contractNames == null || contractNames.isEmpty()) {
            return withContextOptions(contextId, change);
        }
        return withObligationOptions(contextId, contractNames, change);
    }

    private ProjectConfig withContextOptions(String contextId, OptionChange change) {
        List<VerificationContext> edited = contexts.stream()
                .map(c -> c.id().equals(contextId)
                        ? new VerificationContext(c.id(), c.javaSource(), c.classpath(),
                            c.bootclasspath(), c.includes(), change.applyTo(c.options()))
                        : c)
                .toList();
        return new ProjectConfig(version, edited, proofDirectory, options, prover,
            obligationOptions);
    }

    private ProjectConfig withObligationOptions(String contextId, List<String> contractNames,
            OptionChange change) {
        Map<String, Map<String, ProofOptions>> byContext = new LinkedHashMap<>(obligationOptions);
        Map<String, ProofOptions> byContract =
            new LinkedHashMap<>(byContext.getOrDefault(contextId, Map.of()));
        for (String contractName : contractNames) {
            ProofOptions stated =
                change.applyTo(byContract.getOrDefault(contractName, ProofOptions.NONE));
            // An obligation that states nothing is left out, so that clearing every field
            // leaves the file as it was before anything was stated.
            if (stated.isEmpty()) {
                byContract.remove(contractName);
            } else {
                byContract.put(contractName, stated);
            }
        }
        if (byContract.isEmpty()) {
            byContext.remove(contextId);
        } else {
            byContext.put(contextId, byContract);
        }
        return new ProjectConfig(version, contexts, proofDirectory, options, prover, byContext);
    }

    /**
     * The configuration without what some obligations state.
     *
     * @param contextId the context the obligations belong to
     * @param contractNames the obligations whose settings to drop
     * @return the edited configuration
     */
    public ProjectConfig withoutObligationOptions(String contextId,
            java.util.Collection<String> contractNames) {
        Map<String, Map<String, ProofOptions>> byContext = new LinkedHashMap<>(obligationOptions);
        Map<String, ProofOptions> byContract =
            new LinkedHashMap<>(byContext.getOrDefault(contextId, Map.of()));
        contractNames.forEach(byContract::remove);
        if (byContract.isEmpty()) {
            byContext.remove(contextId);
        } else {
            byContext.put(contextId, byContract);
        }
        return new ProjectConfig(version, contexts, proofDirectory, options, prover, byContext);
    }

    /**
     * The configuration with a different prover.
     *
     * @param prover which prover to run proofs with
     * @return the edited configuration
     */
    public ProjectConfig withProver(ProverOptions prover) {
        return new ProjectConfig(version, contexts, proofDirectory, options, prover,
            obligationOptions);
    }
}
