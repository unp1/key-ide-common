/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.strategy.StrategyProperties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.key_project.ide.Fixture;
import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.config.VerificationContext;
import org.key_project.ide.protocol.Dtos.OptionDifferenceDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers reading the settings a saved proof was made with, and comparing them with what
 * its obligation would be attempted with now.
 */
class SavedSettingsTest {

    private static final Path FIXTURE = Fixture.root();

    private static Path projectRoot;
    private static KeYEnvironment<?> environment;
    private static VerificationContext context;
    private static Map<String, String> loaded;
    private static ProofRunner runner;

    @BeforeAll
    static void loadFixture() throws Exception {
        org.slf4j.LoggerFactory.getLogger(SavedSettingsTest.class);
        projectRoot = Files.createTempDirectory("key-ide-saved-settings");
        Fixture.copyInto(projectRoot, "core");
        context = new VerificationContext("core", projectRoot.resolve("core/src/main/java"),
            List.of(), null, List.of());
        environment = KeYEnvironment.load(context.javaSource(), null, null, null);
        loaded = AvailableOptions.chosen(environment.getInitConfig());
        runner = new ProofRunner(environment, context, ProofFiles.under(projectRoot), loaded);
    }

    @AfterAll
    static void dispose() throws IOException {
        if (environment != null) {
            environment.dispose();
        }
        Fixture.remove(projectRoot);
    }

    private ProofObligations.Obligation anyObligation() {
        return new ProofObligations(environment, context, ProofFiles.under(projectRoot)).list()
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("The fixture declares no contract."));
    }

    private static ProofOptions options(String tacletCategory, String tacletChoice,
            String strategyKey, String strategyValue) {
        return new ProofOptions(
            tacletCategory == null ? Map.of() : Map.of(tacletCategory, tacletChoice),
            strategyKey == null ? Map.of() : Map.of(strategyKey, strategyValue), 0);
    }

    @Test
    void aPreparedProofRecordsTheSettingsItWasPreparedWith() throws Exception {
        ProofObligations.Obligation obligation = anyObligation();
        Path saved = runner.prepare(obligation, options("methodExpansion",
            "methodExpansion:noRestriction", StrategyProperties.LOOP_OPTIONS_KEY,
            StrategyProperties.LOOP_SCOPE_INV_TACLET));

        SavedSettings settings = SavedSettings.of(saved);

        assertThat(settings).isNotNull();
        assertThat(settings.taclet()).containsEntry("methodExpansion",
            "methodExpansion:noRestriction");
        assertThat(settings.strategy()).containsEntry(StrategyProperties.LOOP_OPTIONS_KEY,
            StrategyProperties.LOOP_SCOPE_INV_TACLET);
    }

    @Test
    void aProofMadeWithTheCurrentSettingsDiffersInNothing() throws Exception {
        ProofObligations.Obligation obligation = anyObligation();
        ProofOptions options = options("methodExpansion", "methodExpansion:noRestriction",
            StrategyProperties.LOOP_OPTIONS_KEY, StrategyProperties.LOOP_SCOPE_INV_TACLET);
        Path saved = runner.prepare(obligation, options);

        assertThat(SavedSettings.of(saved).differencesFrom(loaded, options,
            environment.getInitConfig().getProfile())).isEmpty();
    }

    @Test
    void aChangedTacletOptionIsReportedAsATacletDifference() throws Exception {
        ProofObligations.Obligation obligation = anyObligation();
        Path saved = runner.prepare(obligation,
            options("methodExpansion", "methodExpansion:noRestriction", null, null));

        List<OptionDifferenceDto> differences = SavedSettings.of(saved).differencesFrom(loaded,
            options("methodExpansion", "methodExpansion:modularOnly", null, null),
            environment.getInitConfig().getProfile());

        assertThat(differences).singleElement().satisfies(difference -> {
            assertThat(difference.kind()).isEqualTo("taclet");
            assertThat(difference.label()).isEqualTo("methodExpansion");
            assertThat(difference.saved()).isEqualTo("noRestriction");
            assertThat(difference.current()).isEqualTo("modularOnly");
        });
    }

    @Test
    void aChangedStrategyOptionIsReportedInKeysWords() throws Exception {
        ProofObligations.Obligation obligation = anyObligation();
        Path saved = runner.prepare(obligation, options(null, null,
            StrategyProperties.LOOP_OPTIONS_KEY, StrategyProperties.LOOP_NONE));

        List<OptionDifferenceDto> differences = SavedSettings.of(saved).differencesFrom(loaded,
            options(null, null, StrategyProperties.LOOP_OPTIONS_KEY,
                StrategyProperties.LOOP_SCOPE_INV_TACLET),
            environment.getInitConfig().getProfile());

        assertThat(differences).singleElement().satisfies(difference -> {
            assertThat(difference.kind()).isEqualTo("strategy");
            assertThat(difference.label()).isEqualTo("Loop treatment");
            assertThat(difference.saved()).isEqualTo("None");
            assertThat(difference.current()).isEqualTo("Invariant (Loop Scope)");
        });
    }

    @Test
    void aFileThatIsNotThereHasNoSettings() {
        assertThat(SavedSettings.of(projectRoot.resolve("nowhere.proof"))).isNull();
    }
}
