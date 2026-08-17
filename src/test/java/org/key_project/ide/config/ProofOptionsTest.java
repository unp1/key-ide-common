/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers which settings a proof is attempted with.
 * <p>
 * A project states settings once, a context may differ from the project, and one
 * obligation may differ from its context. Each level states only its differences.
 */
class ProofOptionsTest {

    private static final String CONTRACT = "com.example.Account[deposit(int)].JML.0";

    private ProjectConfig config(ProofOptions project, ProofOptions context,
            Map<String, ProofOptions> obligations) {
        VerificationContext core = new VerificationContext("core", Path.of("src"), List.of(),
            null, List.of(), context);
        return new ProjectConfig(ProjectConfig.CURRENT_VERSION, List.of(core), "proofs",
            project, ProverOptions.DEFAULT, Map.of("core", obligations));
    }

    @Test
    void anObligationInheritsWhatTheProjectSays() {
        ProjectConfig config = config(
            new ProofOptions(Map.of("intRules", "intRules:javaSemantics"), Map.of(), 0),
            ProofOptions.NONE, Map.of());

        assertThat(config.optionsFor("core", CONTRACT).taclet())
                .containsEntry("intRules", "intRules:javaSemantics");
    }

    @Test
    void aContextOverridesTheProjectAndKeepsTheRest() {
        ProjectConfig config = config(
            new ProofOptions(Map.of("intRules", "intRules:javaSemantics",
                "runtimeExceptions", "runtimeExceptions:ban"), Map.of(), 20000),
            new ProofOptions(Map.of("intRules", "intRules:arithmeticSemanticsIgnoringOF"),
                Map.of(), 0),
            Map.of());

        ProofOptions effective = config.optionsFor("core", CONTRACT);

        assertThat(effective.taclet())
                .containsEntry("intRules", "intRules:arithmeticSemanticsIgnoringOF")
                .containsEntry("runtimeExceptions", "runtimeExceptions:ban");
        assertThat(effective.maxSteps()).isEqualTo(20000);
    }

    @Test
    void anObligationOverridesItsContext() {
        ProjectConfig config = config(
            new ProofOptions(Map.of(), Map.of("SPLITTING_OPTIONS_KEY", "SPLITTING_NORMAL"), 10000),
            new ProofOptions(Map.of(), Map.of("SPLITTING_OPTIONS_KEY", "SPLITTING_DELAYED"), 0),
            Map.of(CONTRACT, new ProofOptions(Map.of(), Map.of(), 500000)));

        ProofOptions effective = config.optionsFor("core", CONTRACT);

        assertThat(effective.strategy())
                .containsEntry("SPLITTING_OPTIONS_KEY", "SPLITTING_DELAYED");
        assertThat(effective.maxSteps()).isEqualTo(500000);
    }

    @Test
    void theSettingsOfAContextLeaveOutWhatItsObligationsSay() {
        ProjectConfig config = config(ProofOptions.NONE,
            new ProofOptions(Map.of(), Map.of(), 10000),
            Map.of(CONTRACT, new ProofOptions(Map.of(), Map.of(), 500000)));

        assertThat(config.optionsFor("core", null).maxSteps()).isEqualTo(10000);
    }

    @Test
    void anObligationOfAnotherContextIsNotAffected() {
        ProjectConfig config = config(ProofOptions.NONE, ProofOptions.NONE,
            Map.of(CONTRACT, new ProofOptions(Map.of(), Map.of(), 500000)));

        assertThat(config.optionsFor("web", CONTRACT).maxSteps()).isZero();
    }

    @Test
    void aProjectThatStatesNothingLeavesEveryChoiceToKeY() {
        ProjectConfig config = ProjectConfig.empty();

        assertThat(config.optionsFor("core", CONTRACT).isEmpty()).isTrue();
        assertThat(config.prover()).isEqualTo(ProverOptions.DEFAULT);
    }
    @Test
    void aTimeoutOfMinusOneIsAValueAndZeroIsSilence() {
        // KeY reads -1 as "no timeout", so a level can state it; 0 is what a level that
        // states nothing looks like, and the level above then decides.
        ProofOptions project = new ProofOptions(Map.of(), Map.of(), 0, 60_000);
        ProofOptions never = new ProofOptions(Map.of(), Map.of(), 0, -1);
        ProofOptions silent = new ProofOptions(Map.of(), Map.of(), 0, 0);

        assertThat(project.mergedWith(never).timeout()).isEqualTo(-1);
        assertThat(project.mergedWith(silent).timeout()).isEqualTo(60_000);
        assertThat(silent.isEmpty()).isTrue();
        assertThat(never.isEmpty()).isFalse();
    }
}
