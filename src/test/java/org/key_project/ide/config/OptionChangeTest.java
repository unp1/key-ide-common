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
 * Covers editing the settings of one level.
 * <p>
 * A form sends the fields the user touched. What matters is what happens to the fields it
 * did not touch, and what happens when a field is cleared rather than set.
 */
class OptionChangeTest {

    private static final String DEPOSIT = "com.example.Account[deposit(int)].JML.0";
    private static final String WITHDRAW = "com.example.Account[withdraw(int)].JML.0";

    private ProjectConfig config() {
        VerificationContext core = new VerificationContext("core", Path.of("src"), List.of(),
            null, List.of(), ProofOptions.NONE);
        return new ProjectConfig(ProjectConfig.CURRENT_VERSION, List.of(core), "proofs");
    }

    private OptionChange sets(String key, String value) {
        return new OptionChange(Map.of(), List.of(), Map.of(key, value), List.of(), null);
    }

    @Test
    void anEditThatSetsOneFieldLeavesTheOthers() {
        ProjectConfig edited = config()
                .withOptions(null, List.of(), sets("SPLITTING_OPTIONS_KEY", "SPLITTING_DELAYED"))
                .withOptions(null, List.of(), sets("OSS_OPTIONS_KEY", "OSS_OFF"));

        assertThat(edited.options().strategy())
                .containsEntry("SPLITTING_OPTIONS_KEY", "SPLITTING_DELAYED")
                .containsEntry("OSS_OPTIONS_KEY", "OSS_OFF");
    }

    @Test
    void anEditOfSeveralObligationsKeepsWhatEachOfThemSaid() {
        ProjectConfig config = config()
                .withOptions("core", List.of(DEPOSIT), sets("OSS_OPTIONS_KEY", "OSS_OFF"))
                .withOptions("core", List.of(WITHDRAW),
                    new OptionChange(Map.of(), List.of(), Map.of(), List.of(), 50000));

        ProjectConfig edited = config.withOptions("core", List.of(DEPOSIT, WITHDRAW),
            sets("SPLITTING_OPTIONS_KEY", "SPLITTING_OFF"));

        assertThat(edited.optionsFor("core", DEPOSIT).strategy())
                .containsEntry("OSS_OPTIONS_KEY", "OSS_OFF")
                .containsEntry("SPLITTING_OPTIONS_KEY", "SPLITTING_OFF");
        assertThat(edited.optionsFor("core", WITHDRAW).maxSteps()).isEqualTo(50000);
        assertThat(edited.optionsFor("core", WITHDRAW).strategy())
                .containsEntry("SPLITTING_OPTIONS_KEY", "SPLITTING_OFF");
    }

    @Test
    void clearingAFieldLetsTheLevelAboveThrough() {
        ProjectConfig config = config()
                .withOptions(null, List.of(), sets("SPLITTING_OPTIONS_KEY", "SPLITTING_NORMAL"))
                .withOptions("core", List.of(DEPOSIT),
                    sets("SPLITTING_OPTIONS_KEY", "SPLITTING_OFF"));

        ProjectConfig edited = config.withOptions("core", List.of(DEPOSIT), new OptionChange(
            Map.of(), List.of(), Map.of(), List.of("SPLITTING_OPTIONS_KEY"), null));

        assertThat(edited.optionsFor("core", DEPOSIT).strategy())
                .containsEntry("SPLITTING_OPTIONS_KEY", "SPLITTING_NORMAL");
    }

    @Test
    void anObligationThatStatesNothingIsLeftOutOfTheFile() {
        ProjectConfig config = config()
                .withOptions("core", List.of(DEPOSIT), sets("OSS_OPTIONS_KEY", "OSS_OFF"));

        ProjectConfig edited = config.withOptions("core", List.of(DEPOSIT), new OptionChange(
            Map.of(), List.of(), Map.of(), List.of("OSS_OPTIONS_KEY"), null));

        assertThat(edited.obligationOptions()).isEmpty();
    }

    @Test
    void anEditOfAContextAppliesToItsObligations() {
        ProjectConfig edited = config().withOptions("core", List.of(),
            new OptionChange(Map.of("intRules", "intRules:javaSemantics"), List.of(), Map.of(),
                List.of(), 20000));

        assertThat(edited.optionsFor("core", DEPOSIT).taclet())
                .containsEntry("intRules", "intRules:javaSemantics");
        assertThat(edited.optionsFor("core", DEPOSIT).maxSteps()).isEqualTo(20000);
    }

    @Test
    void theProverIsStatedByTheProjectAlone() {
        ProjectConfig edited = config().withProver(new ProverOptions(true, 8));

        assertThat(edited.prover()).isEqualTo(new ProverOptions(true, 8));
    }
}
