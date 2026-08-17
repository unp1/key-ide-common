/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.protocol;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object (DTO) for sending data via network
 * <p>
 * Representation of KeY specific data consisting only of simple data types. No KeY defined
 * type ever appears here: that is what keeps the IDE plugins independent works rather
 * than derivatives of KeY, and it is a licensing constraint as much as a design one.
 */
public final class Dtos {

    private Dtos() {
    }

    /**
     * Sent by the IDE as its first request.
     *
     * @param clientName the plugin identifying itself, for logs and bug reports
     * @param clientVersion the plugin version
     * @param protocolVersion the protocol version the client speaks
     * @param projectRoot the absolute path relative paths are written against
     */
    public record InitializeParams(String clientName, String clientVersion, int protocolVersion,
            String projectRoot) {
    }

    /**
     * Returned once the bridge has checked that it can drive the KeY it loaded.
     *
     * @param keyVersion the version KeY reports, for diagnostics only
     * @param keyJarSha256 identifies the jar, since every build off main reports the same
     *        version
     * @param bridgeVersion the version of this bridge
     * @param protocolVersion the protocol version the bridge speaks
     * @param capabilities the operations this bridge supports
     */
    public record InitializeResult(String keyVersion, String keyJarSha256, String bridgeVersion,
            int protocolVersion, List<String> capabilities) {
    }

    /**
     * One verification context, with paths as written in the configuration file.
     *
     * @param id identifies the context within the project
     * @param javaSource the directory holding the sources to verify
     * @param classpath entries holding Java sources KeY reads as library classes
     * @param bootclasspath a directory replacing KeY's internal JavaRedux, or {@code null}
     * @param includes additional {@code .key} files
     */
    public record ContextDto(String id, String javaSource, List<String> classpath,
            String bootclasspath, List<String> includes, ProofOptionsDto options) {

        /**
         * A context whose proofs are attempted with the project's settings.
         *
         * @param id identifies the context within the project
         * @param javaSource the directory holding the sources to verify
         * @param classpath entries holding Java sources KeY reads as library classes
         * @param bootclasspath a directory replacing KeY's internal JavaRedux, or null
         * @param includes additional {@code .key} files
         */
        public ContextDto(String id, String javaSource, List<String> classpath,
                String bootclasspath, List<String> includes) {
            this(id, javaSource, classpath, bootclasspath, includes, null);
        }
    }

    /**
     * The settings a proof is attempted with, as configured at one level.
     *
     * @param taclet the taclet options, by category
     * @param strategy the strategy options, by their key in KeY's strategy properties
     * @param maxSteps the limit on rule applications, 0 for KeY's own limit
     * @param timeout how long one attempt may take, in milliseconds; -1 is no timeout at
     *        all, and 0 leaves the level above to say
     */
    public record ProofOptionsDto(Map<String, String> taclet, Map<String, String> strategy,
            int maxSteps, long timeout) {

        /**
         * Settings without a timeout of their own.
         *
         * @param taclet the taclet options
         * @param strategy the strategy options
         * @param maxSteps the limit on rule applications
         */
        public ProofOptionsDto(Map<String, String> taclet, Map<String, String> strategy,
                int maxSteps) {
            this(taclet, strategy, maxSteps, 0);
        }
    }

    /**
     * Which prover the bridge runs proofs with.
     *
     * @param parallel whether to use the multi-core prover
     * @param threads how many workers it may use, 0 to let KeY choose
     */
    public record ProverOptionsDto(boolean parallel, int threads) {
    }

    /**
     * One value an option accepts.
     *
     * @param value the value stored when it is chosen
     * @param label the label to show
     * @param description what the value means, as KeY explains it
     */
    public record OptionValueDto(String value, String label, String description) {
    }

    /**
     * One option and the values it accepts.
     *
     * @param key the key the choice is stored under
     * @param label the label to show
     * @param description what the option decides, as KeY explains it
     * @param values the accepted values, in the order to offer them
     */
    public record OptionCategoryDto(String key, String label, String description,
            List<OptionValueDto> values) {
    }

    /**
     * The options a context offers, so that a form can offer them.
     *
     * @param taclet the taclet options the context read from its rule files
     * @param strategy the strategy options KeY declares
     * @param defaults the values used where no level configures one
     */
    public record AvailableOptionsDto(List<OptionCategoryDto> taclet,
            List<OptionCategoryDto> strategy, ProofOptionsDto defaults) {
    }

    /**
     * Requests the options a context offers, and the values configured for it.
     *
     * @param contextId the context to ask about
     */
    public record AvailableOptionsParams(String contextId) {
    }

    /**
     * A change to the settings configured at one level.
     * <p>
     * The form sends only the fields the user changed, so that editing several proof
     * obligations at once keeps the settings of each that were not changed. Clearing a
     * field is separate from setting one: falling back to the level above is not a value.
     *
     * @param taclet the taclet options to set, by category
     * @param tacletCleared the taclet categories to leave to the level above
     * @param strategy the strategy options to set, by key
     * @param strategyCleared the strategy keys to leave to the level above
     * @param maxSteps how many rule applications to allow, 0 to leave it to the level
     *        above, or null if the form did not change it
     * @param timeout how long one attempt may take in milliseconds, -1 for no timeout, 0 to
     *        leave it to the level above, or null if the form did not change it
     */
    public record OptionChangeDto(Map<String, String> taclet, List<String> tacletCleared,
            Map<String, String> strategy, List<String> strategyCleared, Integer maxSteps,
            Long timeout) {
    }

    /**
     * Changes the settings configured at one level.
     *
     * @param contextId the context, or null for the project
     * @param contractNames the obligations, empty for the context or project itself
     * @param change the fields the form changed
     */
    public record SetOptionsParams(String contextId, List<String> contractNames,
            OptionChangeDto change) {
    }

    /**
     * How the trash of replaced proofs is kept.
     *
     * @param mode {@code NEVER} to keep everything, {@code EMPTY} to throw everything away,
     *        {@code BELOW_SIZE} to throw the oldest away until the trash is below
     *        {@code megabytes}, {@code OLDER_THAN} to throw away what is older than
     *        {@code days}
     * @param megabytes the size to stay below, for {@code BELOW_SIZE}
     * @param days the age at which a proof is thrown away, for {@code OLDER_THAN}
     */
    public record TrashPolicyDto(String mode, int megabytes, int days) {
    }

    /**
     * The result of pruning the trash.
     *
     * @param files how many files were deleted
     * @param bytes how many bytes they occupied
     */
    public record PrunedResult(int files, long bytes) {
    }

    /**
     * Sets which prover runs the proofs; the project configures this once for all of them.
     *
     * @param prover the prover to use
     */
    public record SetProverParams(ProverOptionsDto prover) {
    }

    /**
     * A whole configuration file.
     *
     * @param version the schema version
     * @param contexts the contexts, in file order
     * @param proofDirectory the directory where the project stores its proofs, relative
     *        to its root, or null for the default one
     * @param options the settings proofs are attempted with unless a context or an
     *        obligation overrides them
     * @param prover which prover runs the proofs
     * @param obligationOptions the settings configured per obligation, by context and contract
     */
    public record ProjectConfigDto(int version, List<ContextDto> contexts,
            String proofDirectory, ProofOptionsDto options, ProverOptionsDto prover,
            Map<String, Map<String, ProofOptionsDto>> obligationOptions) {

        /**
         * A configuration naming no proof directory, so the default one is used.
         *
         * @param version the schema version
         * @param contexts the contexts, in file order
         */
        public ProjectConfigDto(int version, List<ContextDto> contexts) {
            this(version, contexts, null, null, null, null);
        }

        /**
         * A configuration with contexts and a proof directory, and no settings.
         *
         * @param version the schema version
         * @param contexts the contexts, in file order
         * @param proofDirectory where proofs are stored
         */
        public ProjectConfigDto(int version, List<ContextDto> contexts, String proofDirectory) {
            this(version, contexts, proofDirectory, null, null, null);
        }
    }

    /**
     * One problem found in a configuration.
     *
     * @param severity {@code ERROR} or {@code WARNING}
     * @param contextId the context it belongs to
     * @param field the field it belongs to, such as {@code classpath[0]}
     * @param message a sentence naming what is wrong
     */
    public record ProblemDto(String severity, String contextId, String field, String message) {
    }

    /**
     * Asks which context holds a file.
     *
     * @param uri the file, as a {@code file:} URI, or an absolute path
     */
    public record ContextAtParams(String uri) {
    }

    /**
     * The context whose sources hold a file.
     *
     * @param contextId the context, or {@code null} when no context covers the file
     */
    public record ContextAtResult(String contextId) {
    }

    /**
     * Asks for one context to be checked, or for all of them.
     *
     * @param contextId the context to check, or {@code null} for every context
     */
    public record ValidateParams(String contextId) {
    }

    /**
     * The outcome of a check.
     *
     * @param problems the problems found, empty when the configuration is usable
     */
    public record ValidateResult(List<ProblemDto> problems) {
    }

    /**
     * A position in a file, as the IDE reports it.
     *
     * @param contextId the context whose sources hold the file
     * @param uri the file, as a {@code file:} URI
     * @param line the 1-based line of the caret
     * @param column the 1-based column of the caret
     */
    public record ResolveParams(String contextId, String uri, int line, int column) {
    }

    /**
     * A method, in wire form.
     *
     * @param className the declaring class, fully qualified
     * @param name the method name, or the class name for a constructor
     * @param parameterTypes the parameter types, fully qualified, in declaration order
     * @param constructor whether this is a constructor
     * @param startLine the 1-based line the range starts on, including a leading contract
     * @param endLine the 1-based line the declaration ends on
     */
    public record MethodDto(String className, String name, List<String> parameterTypes,
            boolean constructor, int startLine, int endLine) {
    }

    /**
     * A method the caller has already resolved.
     *
     * @param contextId the context whose sources hold the method
     * @param className the declaring class, fully qualified
     * @param name the method name, or the class name for a constructor
     * @param parameterTypes the parameter types, fully qualified
     */
    public record BrowseParams(String contextId, String className, String name,
            List<String> parameterTypes) {
    }

    /**
     * Requests the proof obligations of a context.
     *
     * @param contextId the context to list
     */
    public record ListObligationsParams(String contextId) {
    }

    /**
     * One option whose value in a saved proof differs from the value configured now.
     *
     * @param kind {@code taclet} or {@code strategy}; a taclet option changes what is proved,
     *        a strategy option only how the proof is searched
     * @param label the option, labelled as KeY labels it
     * @param saved the value the proof was made with
     * @param current the value configured for the obligation now
     */
    public record OptionDifferenceDto(String kind, String label, String saved, String current) {
    }

    /**
     * One proof obligation and its status.
     *
     * @param contractName the contract's name, which identifies it within its context
     * @param className the declaring class, fully qualified
     * @param target the method the contract specifies, as shown to the user
     * @param displayName the contract's own name, which distinguishes the specification
     *        cases of one method
     * @param label how the obligation reads to a user: the target as KeY writes it, with its
     *        parameters, and what tells it from the other contracts of that target where
     *        there are several. Decided here, so that every client reads the same
     * @param status one of {@code NONE}, {@code SAVED}, {@code OPEN},
     *        {@code CLOSED_BUT_LEMMAS_LEFT}, {@code CLOSED_BY_CACHE}, {@code CLOSED},
     *        {@code UNKNOWN}
     * @param statusExplanation a sentence for the user, shown where the icon alone is not
     *        enough
     * @param sourceFile the file the class is declared in, relative to the project root,
     *        so that a selection in the IDE can be matched to its obligations
     * @param classLine the 1-based line the class is declared on, 0 when unknown
     * @param targetLine the 1-based line the method is declared on, 0 when unknown
     * @param proofFile where the proof is expected, relative to the project root
     * @param proofFileExists whether that file is there
     * @param differingSettings the settings in which the saved proof differs from the ones
     *        configured for the obligation now; empty if there is no saved proof or none
     *        differ
     */
    public record ObligationDto(String contractName, String className, String target,
            String displayName, String label, String status, String statusExplanation,
            String sourceFile, int classLine, int targetLine, String proofFile,
            boolean proofFileExists, List<OptionDifferenceDto> differingSettings) {
    }

    /**
     * Notifies the IDE that the proof status of a context may have changed.
     *
     * @param contextId the context affected, or {@code null} when it is not known
     */
    public record ObligationsChangedDto(String contextId) {
    }

    /**
     * The proof obligations of a context.
     *
     * @param obligations the obligations, ordered by class and then by contract
     */
    public record ObligationsResult(List<ObligationDto> obligations) {
    }

    /**
     * Asks what to mark in the margin of a source file.
     *
     * @param uri the file, as a {@code file:} URI or as a path
     */
    public record MarksParams(String uri) {
    }

    /**
     * One line to mark, and what the mark says.
     *
     * @param line the 1-based line the mark belongs on
     * @param mark {@code CLOSED}, {@code LEMMAS_LEFT}, {@code OPEN}, or {@code UNJUDGED} for
     *        a declaration KeY has not judged
     * @param tooltip a sentence for the user, hovering the mark
     */
    public record MarkDto(int line, String mark, String tooltip) {
    }

    /**
     * One problem KeY found in a source, as it reported it.
     * <p>
     * KeY refuses a context whose Java or JML it cannot read, and says where. An editor shows
     * this at the line it names, which is what a reader needs to fix it, rather than the text
     * of the exception it arrived in.
     *
     * @param uri the file, as a {@code file:} URI, or {@code null} when KeY named none
     * @param line the 1-based line, 0 when KeY named none
     * @param column the 1-based column, 0 when KeY named none
     * @param message what KeY said about it
     */
    public record SourceProblem(String uri, int line, int column, String message) {
    }

    /**
     * What a failed context load carries besides its message.
     *
     * @param contextId the context that did not load
     * @param problems what KeY found, in the order of the files and lines it names
     */
    public record LoadFailure(String contextId, java.util.List<SourceProblem> problems) {
    }

    /**
     * A position in a source file, as a caret sits in it.
     *
     * @param uri the file, as a {@code file:} URI
     * @param line the 1-based line of the caret
     * @param column the 1-based column of the caret
     */
    public record PositionParams(String uri, int line, int column) {
    }

    /**
     * What a position in a source file stands for.
     * <p>
     * A caret inside a method means that method's contracts. A caret anywhere else in a file
     * means everything the file declares, because asking to verify while looking at a class
     * is a reasonable thing to do.
     *
     * @param contextId the context whose sources hold the file, or {@code null} when none
     *        covers it
     * @param contractNames the contracts the position stands for, empty when it stands for
     *        none
     * @param label how the position reads to a user, empty when it stands for nothing
     */
    public record PositionResult(String contextId, java.util.List<String> contractNames,
            String label) {
    }

    /**
     * What to mark in one source file.
     *
     * @param contextId the context the file belongs to, or {@code null} when none covers it
     * @param marks the lines to mark, ordered by line
     */
    public record MarksResult(String contextId, List<MarkDto> marks) {

        public MarksResult {
            marks = marks == null ? List.of() : List.copyOf(marks);
        }
    }

    /**
     * Requests the status icons for a listing.
     *
     * @param size the edge length in pixels
     */
    public record IconsParams(int size) {
    }

    /**
     * KeY's own status icons, so that a listing can show them without the plugin carrying
     * KeY's assets.
     *
     * @param icons {@code data:} URIs by status name, omitting the states KeY draws no icon
     *        for
     * @param darkIcons the same icons as a dark theme draws them, the ones KeY draws as a
     *        dark glyph inverted
     */
    public record IconsResult(java.util.Map<String, String> icons,
            java.util.Map<String, String> darkIcons) {
    }

    /**
     * Requests the proof obligation browser to be opened on one obligation.
     *
     * @param contextId the context holding it
     * @param contractName the contract to select
     */
    public record StartParams(String contextId, String contractName) {
    }

    /**
     * Requests proof obligations to be proved.
     * <p>
     * The caller chooses the id. It must not be blank, and must not be the id of a run that
     * is still running.
     *
     * @param runId the id of this run
     * @param contextId the context holding them
     * @param contractNames the contracts to attempt, or empty for every one of the context
     */
    public record ProveParams(String runId, String contextId, List<String> contractNames) {
    }

    /**
     * Requests a run to stop.
     *
     * @param runId the id of the run to stop
     */
    public record CancelParams(String runId) {
    }

    /**
     * The result of attempting one proof obligation.
     *
     * @param contractName the contract attempted
     * @param status the status the proof ended in
     * @param statusExplanation a sentence for the user
     * @param nodes the number of proof nodes
     * @param branches the number of branches
     * @param milliseconds how long the attempt took
     * @param proofFile where the proof was written, relative to the project root
     * @param message what went wrong alongside, empty if nothing did
     */
    public record ProofOutcomeDto(String contractName, String status, String statusExplanation,
            int nodes, int branches, long milliseconds, String proofFile, String message) {
    }

    /**
     * The result of a run.
     *
     * @param outcomes one per obligation attempted, in the order attempted
     * @param cancelled whether the run was stopped before finishing
     */
    public record ProveResult(List<ProofOutcomeDto> outcomes, boolean cancelled) {
    }

    /**
     * Reports the progress of a run, so that the IDE can show it.
     *
     * @param runId the id of the run this progress is about
     * @param contextId the context being proved
     * @param contractName the contract now being attempted
     * @param completed how many are finished
     * @param total how many were asked for
     */
    public record ProveProgressDto(String runId, String contextId, String contractName,
            int completed, int total) {
    }

    /**
     * Names the proof obligations a request acts on.
     *
     * @param contextId the context holding them
     * @param contractNames the contracts, or empty for every one of the context
     */
    public record ObligationsParams(String contextId, List<String> contractNames) {
    }

    /**
     * The result of removing saved proofs.
     *
     * @param removed how many files were deleted
     */
    public record RemovedResult(int removed) {
    }

    /**
     * Which contracts one obligation's proof used, as KeY reported them.
     *
     * @param contractName the obligation
     * @param known whether KeY has reported on it, which it has once its proof was run or
     *        read back in this session
     * @param uses the contracts its proof used, empty if it used none or none is known
     */
    public record UsedContractsDto(String contractName, boolean known, List<String> uses) {
    }

    /**
     * What KeY reported about the proofs of a context.
     *
     * @param obligations one entry per obligation KeY has reported on
     */
    public record DependenciesResult(List<UsedContractsDto> obligations) {
    }

    /**
     * Proof obligations that have settings configured but no longer exist.
     * <p>
     * Renaming or removing a method leaves the settings of its obligations in the
     * configuration, keyed by a contract name that no longer resolves.
     *
     * @param contractNames the names of those obligations
     */
    public record StaleOptionsResult(List<String> contractNames) {
    }

    /**
     * Where a prepared proof was saved.
     *
     * @param proofFile the file, relative to the project root
     */
    public record PreparedResult(String proofFile) {
    }

    /**
     * Reports what the bridge is doing, so that the IDE can show a status.
     *
     * @param state one of {@code starting}, {@code ready}, {@code loading}, {@code error}
     * @param detail a sentence for the user, or {@code null}
     */
    public record StateDto(String state, String detail) {
    }

    /**
     * A message for the user, since KeY's own log goes to a file.
     *
     * @param level {@code info}, {@code warn}, or {@code error}
     * @param text the message
     */
    public record LogDto(String level, String text) {
    }
}
