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
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.speclang.Contract;
import de.uka.ilkd.key.strategy.StrategyProperties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.key_project.ide.Fixture;
import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.config.VerificationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers that the taclet options a proof is asked for are the ones it is built with.
 * <p>
 * Which taclets exist is decided before the proof is built, and a proof records what it was
 * built with. Both have to say what was asked for: the first decides what the prover may
 * do, the second decides what KeY reads back when the saved proof is opened again.
 */
class AppliedOptionsTest {

    private static final Path FIXTURE = Fixture.root();

    private static Path projectRoot;
    private static KeYEnvironment<?> environment;
    private static Map<String, String> loaded;

    @BeforeAll
    static void loadFixture() throws Exception {
        org.slf4j.LoggerFactory.getLogger(AppliedOptionsTest.class);
        projectRoot = Files.createTempDirectory("key-ide-options");
        Fixture.copyInto(projectRoot, "core");
        VerificationContext context = new VerificationContext("core",
            projectRoot.resolve("core/src/main/java"), List.of(), null, List.of());
        environment = KeYEnvironment.load(context.javaSource(), null, null, null);
        loaded = AvailableOptions.chosen(environment.getInitConfig());
    }

    @AfterAll
    static void dispose() throws IOException {
        if (environment != null) {
            environment.dispose();
        }
        Fixture.remove(projectRoot);
    }

    /** Any contract of the fixture, since the options are not a property of the contract. */
    private Contract anyContract() {
        return environment.getSpecificationRepository().getAllContracts().stream().findFirst()
                .orElseThrow(() -> new AssertionError("The fixture declares no contract."));
    }

    private Proof build(ProofOptions options) throws Exception {
        AppliedOptions.applyTacletOptions(environment.getInitConfig(), options, loaded);
        return environment
                .createProof(anyContract().createProofObl(environment.getInitConfig()));
    }

    @Test
    void aProofIsBuiltWithTheTacletOptionItWasAskedFor() throws Exception {
        Proof proof = build(new ProofOptions(
            Map.of("methodExpansion", "methodExpansion:noRestriction"), Map.of(), 0));
        try {
            assertThat(AvailableOptions.chosen(proof.getInitConfig()))
                    .containsEntry("methodExpansion", "methodExpansion:noRestriction");
        } finally {
            proof.dispose();
        }
    }

    @Test
    void aProofRecordsTheTacletOptionItWasBuiltWith() throws Exception {
        Proof proof = build(new ProofOptions(
            Map.of("methodExpansion", "methodExpansion:noRestriction"), Map.of(), 0));
        try {
            assertThat(proof.getSettings().getChoiceSettings().getDefaultChoices())
                    .containsEntry("methodExpansion", "methodExpansion:noRestriction");
        } finally {
            proof.dispose();
        }
    }

    @Test
    void aProofThatStatesNothingIsBuiltWithWhatTheContextWasLoadedWith() throws Exception {
        Proof first = build(new ProofOptions(
            Map.of("methodExpansion", "methodExpansion:noRestriction"), Map.of(), 0));
        first.dispose();

        Proof second = build(ProofOptions.NONE);
        try {
            assertThat(AvailableOptions.chosen(second.getInitConfig()))
                    .containsEntry("methodExpansion", loaded.get("methodExpansion"));
        } finally {
            second.dispose();
        }
    }

    @Test
    void aProofRunsTheStrategyItsOptionsDescribe() throws Exception {
        Proof proof = build(ProofOptions.NONE);
        try {
            AppliedOptions.applyStrategyOptions(proof, new ProofOptions(Map.of(),
                Map.of(StrategyProperties.LOOP_OPTIONS_KEY,
                    StrategyProperties.LOOP_SCOPE_INV_TACLET),
                0));

            assertThat(proof.getSettings().getStrategySettings()
                    .getActiveStrategyProperty(StrategyProperties.LOOP_OPTIONS_KEY))
                            .isEqualTo(StrategyProperties.LOOP_SCOPE_INV_TACLET);
            // The strategy an open goal runs is what decides a proof, not the settings.
            assertThat(proof.openGoals().head().getGoalStrategy())
                    .isSameAs(proof.getActiveStrategy());
        } finally {
            proof.dispose();
        }
    }

    @Test
    void aLaterProofIsBuiltWithItsOwnTacletOption() throws Exception {
        Proof first = build(new ProofOptions(
            Map.of("methodExpansion", "methodExpansion:noRestriction"), Map.of(), 0));
        first.dispose();

        Proof second = build(new ProofOptions(
            Map.of("methodExpansion", "methodExpansion:modularOnly"), Map.of(), 0));
        try {
            assertThat(AvailableOptions.chosen(second.getInitConfig()))
                    .containsEntry("methodExpansion", "methodExpansion:modularOnly");
            assertThat(second.getSettings().getChoiceSettings().getDefaultChoices())
                    .containsEntry("methodExpansion", "methodExpansion:modularOnly");
        } finally {
            second.dispose();
        }
    }
}
