/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.key.NoProofBrowser;
import org.key_project.ide.Fixture;
import org.key_project.ide.protocol.Dtos.DependenciesResult;
import org.key_project.ide.protocol.Dtos.ListObligationsParams;
import org.key_project.ide.protocol.Dtos.ObligationsParams;
import org.key_project.ide.protocol.Dtos.ObligationDto;
import org.key_project.ide.protocol.Dtos.ProveParams;
import org.key_project.ide.protocol.Dtos.ProveResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what a proof that relied on another contract is reported as, and what it says it
 * used.
 * <p>
 * KeY calls such a proof closed but for lemmas until it holds a closed proof of the used
 * contract. Both the verdict and the contracts used are KeY's, asked for while KeY still
 * holds the proofs of the run.
 */
class DependencyStatusTest {

    private static final Path FIXTURE = Fixture.root();

    private Path projectRoot;
    private EnvironmentManager environments;
    private VerificationServiceImpl service;

    @BeforeAll
    static void initialiseLogging() {
        org.slf4j.LoggerFactory.getLogger(DependencyStatusTest.class);
    }

    @BeforeEach
    void copyFixtureAndCreateService() throws IOException {
        projectRoot = Files.createTempDirectory("key-ide-dependencies");
        Fixture.copyInto(projectRoot, "core");
        // The fixture is also the project the sandbox IDE opens, so its own settings file
        // holds whatever was last tried there. This test states its own.
        Files.createDirectories(projectRoot.resolve(".key"));
        Files.writeString(projectRoot.resolve(".key/settings.json"), """
            {
              "version": 1,
              "contexts": [
                { "id": "core", "javaSource": "core/src/main/java", "classpath": [], "includes": [] }
              ]
            }
            """);
        BridgeSession session = new BridgeSession();
        session.initialize(projectRoot);
        environments = new EnvironmentManager();
        service = new VerificationServiceImpl(session, environments, new NoProofBrowser());
    }

    @AfterEach
    void dispose() throws IOException {
        environments.close();
        Fixture.remove(projectRoot);
    }

    private ObligationDto listed(String method) throws Exception {
        return service.list(new ListObligationsParams("core")).get().obligations().stream()
                .filter(o -> o.contractName().contains("::" + method + "("))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The fixture has no contract for " + method));
    }

    private ProveResult prove(String runId, String contractName) throws Exception {
        return service.prove(new ProveParams(runId, "core", List.of(contractName))).get();
    }

    @Test
    void aProofThatUsedAnUnprovedContractIsClosedButForLemmas() throws Exception {
        String contains = listed("contains").contractName();

        ProveResult result = prove("run-1", contains);

        assertThat(result.outcomes()).singleElement()
                .satisfies(o -> assertThat(o.status()).isEqualTo("CLOSED_BUT_LEMMAS_LEFT"));
        assertThat(listed("contains").status()).isEqualTo("CLOSED_BUT_LEMMAS_LEFT");
    }

    @Test
    void aRunThatProvesBothIsToldByKeyThatBothAreClosed() throws Exception {
        String contains = listed("contains").contractName();
        String indexOf = listed("indexOf").contractName();

        // Both proofs are made in one run, so KeY has both in front of it when it is asked,
        // and it is KeY that says the one which used the other is closed.
        ProveResult result = service.prove(
            new ProveParams("run-1", "core", List.of(contains, indexOf))).get();

        assertThat(result.outcomes()).extracting(o -> o.status())
                .containsExactly("CLOSED", "CLOSED");
        assertThat(listed("contains").status()).isEqualTo("CLOSED");
    }

    @Test
    void aProofSaysWhichContractsItUsed() throws Exception {
        String contains = listed("contains").contractName();
        String indexOf = listed("indexOf").contractName();
        prove("run-1", contains);

        DependenciesResult reported =
            service.dependencies(new ListObligationsParams("core")).get();

        assertThat(reported.obligations()).singleElement().satisfies(used -> {
            assertThat(used.contractName()).isEqualTo(contains);
            assertThat(used.known()).isTrue();
            assertThat(used.uses()).containsExactly(indexOf);
        });
    }

    @Test
    void nothingIsReportedAboutAProofKeyHasNotHeld() throws Exception {
        assertThat(service.dependencies(new ListObligationsParams("core")).get().obligations())
                .isEmpty();
    }

    @Test
    void replayingBothTellsKeyWhatItNeedsToCallTheDependentProofClosed() throws Exception {
        String contains = listed("contains").contractName();
        String indexOf = listed("indexOf").contractName();
        service.prove(new ProveParams("run-1", "core", List.of(contains, indexOf))).get();

        // A context loaded again knows nothing until the proofs are read back. Read into
        // one environment, KeY has both in front of it and says the dependent one is
        // closed; that is the verdict, not a conclusion drawn here.
        environments.discard("core");
        ProveResult replayed =
            service.replay(new ObligationsParams("core", List.of(contains, indexOf))).get();

        assertThat(replayed.outcomes()).extracting(o -> o.status())
                .containsExactlyInAnyOrder("CLOSED", "CLOSED");
        assertThat(listed("contains").status()).isEqualTo("CLOSED");
    }

    @Test
    void provingTheLemmaLaterMakesKeyCallTheProofThatUsedItClosed() throws Exception {
        String contains = listed("contains").contractName();
        String indexOf = listed("indexOf").contractName();
        prove("run-1", contains);
        assertThat(listed("contains").status()).isEqualTo("CLOSED_BUT_LEMMAS_LEFT");

        // The lemma is proved on its own. Its run reads the saved proof of contains in
        // beside it, so KeY has both and says what it says about each.
        prove("run-2", indexOf);

        assertThat(listed("contains").status()).isEqualTo("CLOSED");
    }

    @Test
    void aProofIsClosedWhenTheLemmaItUsesHasASavedClosedProof() throws Exception {
        String contains = listed("contains").contractName();
        String indexOf = listed("indexOf").contractName();
        prove("run-1", indexOf);

        // The run holds the proof of contains alone, so KeY judges it without the proof of
        // indexOf beside it. Asked again with every saved proof loaded, KeY says what the
        // saved proof of indexOf is worth to it.
        ProveResult result = prove("run-2", contains);

        assertThat(result.outcomes()).singleElement()
                .satisfies(o -> assertThat(o.status()).isEqualTo("CLOSED"));
        assertThat(listed("contains").status()).isEqualTo("CLOSED");
    }

    @Test
    void removingTheLemmaProofUnsettlesOnlyWhatUsedIt() throws Exception {
        String contains = listed("contains").contractName();
        String indexOf = listed("indexOf").contractName();
        String max = listed("max").contractName();
        service.prove(new ProveParams("run-1", "core", List.of(contains, indexOf, max))).get();
        assertThat(listed("contains").status()).isEqualTo("CLOSED");
        assertThat(listed("max").status()).isEqualTo("CLOSED");

        // KeY judged contains with the proof of indexOf in front of it, so removing that
        // proof unsettles the verdict on contains, and KeY is asked what it is worth now.
        Files.delete(projectRoot.resolve(listed("indexOf").proofFile()));

        assertThat(listed("contains").status()).isEqualTo("CLOSED_BUT_LEMMAS_LEFT");
        assertThat(listed("indexOf").status()).isEqualTo("NONE");
        // max used nothing that changed, so KeY's word about it still stands.
        assertThat(listed("max").status()).isEqualTo("CLOSED");
    }

    @Test
    void whatKeySaidIsForgottenWhenTheContextIsLoadedAgain() throws Exception {
        String contains = listed("contains").contractName();
        prove("run-1", contains);
        assertThat(listed("contains").status()).isEqualTo("CLOSED_BUT_LEMMAS_LEFT");

        // A contract proved under one load of the sources says nothing about the same name
        // under another, so what KeY said goes with the environment it said it in.
        environments.discard("core");

        // Nothing said under one load of the sources is carried into another, and the
        // proofs are not read back until they are asked for.
        assertThat(listed("contains").status()).isEqualTo("SAVED");
        assertThat(service.dependencies(new ListObligationsParams("core")).get().obligations())
                .isEmpty();
    }
}
