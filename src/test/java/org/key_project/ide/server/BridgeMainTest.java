/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.key_project.ide.Fixture;
import org.key_project.ide.client.BridgeClient;
import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.protocol.Dtos.ContextDto;
import org.key_project.ide.protocol.Dtos.InitializeParams;
import org.key_project.ide.protocol.Dtos.ListObligationsParams;
import org.key_project.ide.protocol.Dtos.InitializeResult;
import org.key_project.ide.protocol.Dtos.LogDto;
import org.key_project.ide.protocol.Dtos.ObligationsChangedDto;
import org.key_project.ide.protocol.Dtos.ObligationsResult;
import org.key_project.ide.protocol.Dtos.ProjectConfigDto;
import org.key_project.ide.protocol.Dtos.ProveParams;
import org.key_project.ide.protocol.Dtos.ProveProgressDto;
import org.key_project.ide.protocol.Dtos.ProveResult;
import org.key_project.ide.protocol.Dtos.StateDto;
import org.key_project.ide.protocol.IdeClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the bridge an IDE runs: it lists and proves without a window ever appearing.
 * <p>
 * The test project is copied first, so proofs are written beside the copy.
 */
class BridgeMainTest {

    private static final Path FIXTURE = Fixture.root();
    private static final long TIMEOUT_SECONDS = 300;

    private Path projectRoot;
    private EnvironmentManager environments;
    private BridgeServer server;
    private BridgeClient client;
    private RecordingClient ide;

    @BeforeEach
    void startAndConnect() throws Exception {
        projectRoot = Files.createTempDirectory("key-ide-bridge");
        Fixture.copyInto(projectRoot, "core");

        Path runtimeDir = projectRoot.resolve("rt");
        environments = new EnvironmentManager();
        server = BridgeMain.start(runtimeDir, environments);
        ide = new RecordingClient();
        client = BridgeClient.connect(runtimeDir, ide);

        client.service().initialize(new InitializeParams("test", "0.0.1",
            BridgeInfo.PROTOCOL_VERSION, projectRoot.toString())).get(30, TimeUnit.SECONDS);
        client.service().getConfigService().set(new ProjectConfigDto(1,
            List.of(new ContextDto("core", "core/src/main/java", List.of(), null, List.of()))))
                .get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void stop() throws IOException {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
        if (environments != null) {
            environments.close();
        }
        Fixture.remove(projectRoot);
    }

    @Test
    void reportsThatItCanProve() throws Exception {
        InitializeResult result = client.service().initialize(new InitializeParams("test", "0.0.1",
            BridgeInfo.PROTOCOL_VERSION, projectRoot.toString())).get(30, TimeUnit.SECONDS);

        assertThat(result.capabilities()).contains("prove");
    }

    @Test
    void listsWhatCanBeProvedWithoutAWindow() throws Exception {
        ObligationsResult obligations = client.service().getVerificationService()
                .list(new ListObligationsParams("core")).get(TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);

        assertThat(obligations.obligations()).isNotEmpty();
        assertThat(java.awt.GraphicsEnvironment.isHeadless()
            || java.awt.Window.getWindows().length == 0).isTrue();
    }

    @Test
    void provesOneObligationAndSavesIt() throws Exception {
        String contract = contractNamed("getBalance");

        ProveResult result = client.service().getVerificationService()
                .prove(new ProveParams("a-run", "core", List.of(contract)))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(result.cancelled()).isFalse();
        assertThat(result.outcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.status()).isEqualTo("CLOSED");
            assertThat(outcome.nodes()).isGreaterThan(0);
            assertThat(projectRoot.resolve(outcome.proofFile())).exists();
        });
    }

    @Test
    void reportsProgressWhileProving() throws Exception {
        client.service().getVerificationService()
                .prove(new ProveParams("another-run", "core", List.of(contractNamed("max"))))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(ide.progress).isNotEmpty();
        assertThat(ide.progress.get(0).total()).isEqualTo(1);
        // Progress says which run it is about, so a client following two can tell them apart.
        assertThat(ide.progress).allSatisfy(p -> assertThat(p.runId()).isEqualTo("another-run"));
    }

    @Test
    void keepsSayingWhatARunEstablishedAfterTheProofIsDisposed() throws Exception {
        String contract = contractNamed("getBalance");
        client.service().getVerificationService()
                .prove(new ProveParams("closing", "core", List.of(contract)))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        ObligationsResult listed = client.service().getVerificationService()
                .list(new ListObligationsParams("core")).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(listed.obligations())
                .filteredOn(o -> o.contractName().equals(contract))
                .singleElement()
                .satisfies(o -> assertThat(o.status()).isEqualTo("CLOSED"));
    }

    @Test
    void reportsAnObligationAsUnprovedOnceItsProofFileIsGone() throws Exception {
        String contract = contractNamed("getBalance");
        client.service().getVerificationService()
                .prove(new ProveParams("to-delete", "core", List.of(contract)))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Deleting the file is how a user says the proof is gone, whether they do it here
        // or in the project view.
        Fixture.remove(projectRoot.resolve("proofs"));

        ObligationsResult listed = client.service().getVerificationService()
                .list(new ListObligationsParams("core")).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(listed.obligations())
                .filteredOn(o -> o.contractName().equals(contract))
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.status()).isEqualTo("NONE");
                    assertThat(o.proofFileExists()).isFalse();
                });
    }

    @Test
    void refusesARunThatIsNotNamed() {
        assertThatThrownBy(() -> client.service().getVerificationService()
                .prove(new ProveParams("  ", "core", List.of(contractNamed("max"))))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .hasMessageContaining("has to be named");
    }

    @Test
    void refusesASecondRunUnderANameAlreadyGoing() throws Exception {
        String contract = contractNamed("max");
        CompletableFuture<ProveResult> first = client.service().getVerificationService()
                .prove(new ProveParams("shared-name", "core", List.of(contract)));

        assertThatThrownBy(() -> client.service().getVerificationService()
                .prove(new ProveParams("shared-name", "core", List.of(contract)))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .hasMessageContaining("already going");

        first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void tellsTheProgressOfTwoRunsApart() throws Exception {
        CompletableFuture<ProveResult> one = client.service().getVerificationService()
                .prove(new ProveParams("run-one", "core", List.of(contractNamed("max"))));
        CompletableFuture<ProveResult> two = client.service().getVerificationService()
                .prove(new ProveParams("run-two", "core", List.of(contractNamed("getBalance"))));

        one.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        two.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(ide.progress).extracting(ProveProgressDto::runId)
                .contains("run-one", "run-two");
    }

    /** A name a run is finished with can be used again. */
    @Test
    void reusesTheNameOfARunThatHasFinished() throws Exception {
        String contract = contractNamed("max");
        client.service().getVerificationService()
                .prove(new ProveParams("reused", "core", List.of(contract)))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        ProveResult again = client.service().getVerificationService()
                .prove(new ProveParams("reused", "core", List.of(contract)))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(again.outcomes()).hasSize(1);
    }

    private String contractNamed(String part) throws Exception {
        return client.service().getVerificationService()
                .list(new ListObligationsParams("core")).get(TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)
                .obligations().stream().map(o -> o.contractName())
                .filter(name -> name.contains(part)).findFirst().orElseThrow();
    }


    /** Collects what the bridge pushes. */
    private static final class RecordingClient implements IdeClient {

        private final List<ProveProgressDto> progress = new CopyOnWriteArrayList<>();
        private final List<ObligationsChangedDto> changes = new ArrayList<>();

        @Override
        public void state(StateDto params) {
        }

        @Override
        public void log(LogDto params) {
        }

        @Override
        public synchronized void obligationsChanged(ObligationsChangedDto params) {
            changes.add(params);
        }

        @Override
        public void proveProgress(ProveProgressDto params) {
            progress.add(params);
        }
    }
}
