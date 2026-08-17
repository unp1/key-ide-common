/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.key_project.ide.client.BridgeClient;
import org.key_project.ide.protocol.Dtos.ContextAtParams;
import org.key_project.ide.protocol.Dtos.ContextAtResult;
import org.key_project.ide.protocol.Dtos.ContextDto;
import org.key_project.ide.protocol.Dtos.InitializeParams;
import org.key_project.ide.protocol.Dtos.InitializeResult;
import org.key_project.ide.protocol.Dtos.LogDto;
import org.key_project.ide.protocol.Dtos.ObligationsChangedDto;
import org.key_project.ide.protocol.Dtos.ProveProgressDto;
import org.key_project.ide.protocol.Dtos.ProjectConfigDto;
import org.key_project.ide.protocol.Dtos.ResolveParams;
import org.key_project.ide.protocol.Dtos.StateDto;
import org.key_project.ide.protocol.Dtos.ValidateParams;
import org.key_project.ide.protocol.Dtos.ValidateResult;
import org.key_project.ide.protocol.IdeClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the bridge an IDE runs to edit settings.
 * <p>
 * The point of this bridge is that it needs no KeY, so the test drives the whole protocol
 * without loading one.
 */
class ConfigBridgeMainTest {

    private static final Path FIXTURE = Path.of("src/test/fixture").toAbsolutePath().normalize();

    @TempDir
    Path workDir;

    private BridgeServer server;
    private BridgeClient client;

    @BeforeEach
    void startAndConnect() throws IOException {
        server = ConfigBridgeMain.start(workDir.resolve("rt"));
        client = BridgeClient.connect(workDir.resolve("rt"), new SilentClient());
    }

    @AfterEach
    void stop() throws IOException {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void reportsThatItServesConfigurationOnly() throws Exception {
        InitializeResult result = initialize(workDir);

        assertThat(result.capabilities()).containsExactly("config");
        assertThat(result.bridgeVersion()).isEqualTo("0.1.0-dev");
    }

    @Test
    void answersAPingBeforeItHasBeenInitialised() throws Exception {
        // A client that has stopped hearing from a bridge asks this to tell a slow answer
        // from one that has stopped answering, so it is answered whatever the session is.
        assertThat(client.service().ping().get(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void readsAndWritesAProjectConfigurationWithoutKey() throws Exception {
        initialize(workDir);
        ProjectConfigDto written = new ProjectConfigDto(1, List.of(new ContextDto("core",
            FIXTURE.resolve("core/src/main/java").toString(), List.of(), null, List.of())));

        client.service().getConfigService().set(written).get(10, TimeUnit.SECONDS);
        ProjectConfigDto read =
            client.service().getConfigService().get().get(10, TimeUnit.SECONDS);

        assertThat(read.contexts()).singleElement()
                .satisfies(context -> assertThat(context.id()).isEqualTo("core"));
    }

    @Test
    void namesTheContextWhoseSourcesHoldAFile() throws Exception {
        initialize(workDir);
        client.service().getConfigService().set(new ProjectConfigDto(1, List.of(
            new ContextDto("core", FIXTURE.resolve("core/src/main/java").toString(), List.of(),
                null, List.of()))))
                .get(10, TimeUnit.SECONDS);

        ContextAtResult found = client.service().getConfigService()
                .contextAt(new ContextAtParams(FIXTURE
                        .resolve("core/src/main/java/com/example/core/Account.java").toUri()
                        .toString()))
                .get(10, TimeUnit.SECONDS);

        assertThat(found.contextId()).isEqualTo("core");
    }

    @Test
    void namesNoContextForAFileOutsideEveryOne() throws Exception {
        initialize(workDir);

        ContextAtResult found = client.service().getConfigService()
                .contextAt(new ContextAtParams(FIXTURE.resolve("nowhere/Other.java").toString()))
                .get(10, TimeUnit.SECONDS);

        assertThat(found.contextId()).isNull();
    }

    @Test
    void validatesPathsWithoutKey() throws Exception {
        initialize(workDir);
        client.service().getConfigService().set(new ProjectConfigDto(1,
            List.of(new ContextDto("broken", workDir.resolve("absent").toString(), List.of(), null,
                List.of()))))
                .get(10, TimeUnit.SECONDS);

        ValidateResult result = client.service().getConfigService()
                .validate(new ValidateParams("broken")).get(10, TimeUnit.SECONDS);

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.field()).isEqualTo("javaSource");
            assertThat(problem.message()).contains("does not exist");
        });
    }

    @Test
    void stopsOnceItsClientIsGone() throws Exception {
        initialize(workDir);

        // An editor that is killed never says goodbye, so the connection closing is the
        // only signal this bridge gets that it is no longer needed.
        client.close();
        client = null;

        assertThat(awaitStop(server)).isTrue();
    }

    @Test
    void saysThatVerifyingNeedsKey() throws Exception {
        initialize(workDir);

        assertThatThrownBy(() -> client.service().getVerificationService()
                .verifyAt(new ResolveParams("core", FIXTURE.toUri().toString(), 1, 1))
                .get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .cause()
                        .isInstanceOf(ResponseErrorException.class)
                        .hasMessageContaining("Start KeY to verify");
    }

    /**
     * Waits a little for the server to stop, so the test does not depend on how quickly
     * the accept loop notices the closed connection.
     */
    private static boolean awaitStop(BridgeServer server) throws InterruptedException {
        Thread waiting = new Thread(() -> {
            try {
                server.awaitStop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiting.setDaemon(true);
        waiting.start();
        waiting.join(10_000);
        return !waiting.isAlive();
    }

    private InitializeResult initialize(Path projectRoot) throws Exception {
        return client.service().initialize(new InitializeParams("test", "0.0.1",
            BridgeInfo.PROTOCOL_VERSION, projectRoot.toString())).get(10, TimeUnit.SECONDS);
    }

    private static final class SilentClient implements IdeClient {
        @Override
        public void state(StateDto params) {
        }

        @Override
        public void log(LogDto params) {
        }

        @Override
        public void obligationsChanged(ObligationsChangedDto params) {
        }

        @Override
        public void proveProgress(ProveProgressDto params) {
        }
    }
}
