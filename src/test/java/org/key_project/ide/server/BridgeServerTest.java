/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.protocol.BridgeService;
import org.key_project.ide.protocol.Dtos.ContextDto;
import org.key_project.ide.protocol.Dtos.InitializeParams;
import org.key_project.ide.protocol.Dtos.InitializeResult;
import org.key_project.ide.protocol.Dtos.LogDto;
import org.key_project.ide.protocol.Dtos.ObligationsChangedDto;
import org.key_project.ide.protocol.Dtos.ProveProgressDto;
import org.key_project.ide.protocol.Dtos.ProjectConfigDto;
import org.key_project.ide.protocol.Dtos.StateDto;
import org.key_project.ide.protocol.Dtos.ValidateParams;
import org.key_project.ide.protocol.Dtos.ValidateResult;
import org.key_project.ide.protocol.IdeClient;
import org.key_project.ide.transport.EndpointFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the bridge the way a plugin will: over a real socket, with an LSP4J client on
 * the other end.
 */
class BridgeServerTest {

    private static final Path FIXTURE = Path.of("src/test/fixture").toAbsolutePath().normalize();
    private static final long TIMEOUT_SECONDS = 10;

    @TempDir
    Path workDir;

    private BridgeServer server;
    private SocketChannel channel;
    private BridgeService bridge;
    private RecordingClient ideClient;

    @BeforeEach
    void startServerAndConnect() throws Exception {
        Path runtimeDir = workDir.resolve("rt");
        BridgeInfo info = new BridgeInfo("3.1.0-dev", "sha-of-the-jar", "0.1.0-dev",
            BridgeInfo.PROTOCOL_VERSION, List.of("config"));
        BridgeSession session = new BridgeSession();
        server = BridgeServer.start(runtimeDir,
            new BridgeServiceImpl(info, session, verificationService(session), () -> {
            }), BridgeServer.AfterDisconnect.KEEP_LISTENING);

        Map<String, String> published = EndpointFile.read(runtimeDir);
        channel = connect(published);

        ideClient = new RecordingClient();
        Launcher<BridgeService> launcher = Launcher.createLauncher(ideClient, BridgeService.class,
            Channels.newInputStream(channel), Channels.newOutputStream(channel));
        launcher.startListening();
        bridge = launcher.getRemoteProxy();
    }

    @AfterEach
    void stop() throws IOException {
        if (channel != null) {
            channel.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void reportsWhatItIsRunningAgainst() throws Exception {
        InitializeResult result = initialize();

        assertThat(result.keyVersion()).isEqualTo("3.1.0-dev");
        assertThat(result.keyJarSha256()).isEqualTo("sha-of-the-jar");
        assertThat(result.protocolVersion()).isEqualTo(BridgeInfo.PROTOCOL_VERSION);
        assertThat(result.capabilities()).contains("config");
    }

    @Test
    void returnsAnEmptyConfigurationForAProjectWithoutOne() throws Exception {
        initialize();

        ProjectConfigDto config = await(bridge.getConfigService().get());

        assertThat(config.contexts()).isEmpty();
    }

    @Test
    void storesAConfigurationAndReadsItBack() throws Exception {
        initialize();
        ProjectConfigDto written = new ProjectConfigDto(1,
            List.of(new ContextDto("core", FIXTURE.resolve("core/src/main/java").toString(),
                List.of(), null, List.of())));

        await(bridge.getConfigService().set(written));
        ProjectConfigDto read = await(bridge.getConfigService().get());

        assertThat(read.contexts()).singleElement().satisfies(context -> {
            assertThat(context.id()).isEqualTo("core");
            assertThat(context.javaSource())
                    .isEqualTo(FIXTURE.resolve("core/src/main/java").toString());
        });
        assertThat(projectFile()).exists().content(StandardCharsets.UTF_8).contains("\"core\"");
    }

    @Test
    void acceptsTheFixturesCoreContext() throws Exception {
        initialize();
        await(bridge.getConfigService().set(new ProjectConfigDto(1,
            List.of(new ContextDto("core", FIXTURE.resolve("core/src/main/java").toString(),
                List.of(), null, List.of())))));

        ValidateResult result = await(bridge.getConfigService().validate(new ValidateParams(null)));

        assertThat(result.problems()).isEmpty();
    }

    @Test
    void reportsAClasspathEntryWithoutSourcesOverTheWire() throws Exception {
        initialize();
        await(bridge.getConfigService().set(new ProjectConfigDto(1,
            List.of(new ContextDto("web", FIXTURE.resolve("web/src/main/java").toString(),
                List.of(workDir.toString()), null, List.of())))));

        ValidateResult result = await(bridge.getConfigService().validate(new ValidateParams("web")));

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo("ERROR");
            assertThat(problem.field()).isEqualTo("classpath[0]");
            assertThat(problem.message()).contains("no .java or .jml files");
        });
    }

    @Test
    void refusesAnUnknownContextId() throws Exception {
        initialize();

        assertThatThrownBy(
            () -> await(bridge.getConfigService().validate(new ValidateParams("absent"))))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(ResponseErrorException.class)
                    .satisfies(error -> assertThat(
                        ((ResponseErrorException) error).getResponseError().getCode())
                                .isEqualTo(BridgeErrors.UNKNOWN_CONTEXT));
    }

    @Test
    void refusesConfigurationCallsBeforeInitialize() {
        assertThatThrownBy(() -> await(bridge.getConfigService().get()))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(ResponseErrorException.class)
                .satisfies(error -> assertThat(
                    ((ResponseErrorException) error).getResponseError().getCode())
                            .isEqualTo(BridgeErrors.NOT_INITIALIZED));
    }

    @Test
    void deliversNotificationsToTheIde() throws Exception {
        initialize();

        server.client().state(new StateDto("ready", "KeY is up"));

        assertThat(ideClient.awaitState()).satisfies(state -> {
            assertThat(state.state()).isEqualTo("ready");
            assertThat(state.detail()).isEqualTo("KeY is up");
        });
    }

    /** These tests exercise the configuration half, so the browser is never reached. */
    private static VerificationServiceImpl verificationService(BridgeSession session) {
        return new VerificationServiceImpl(session, new EnvironmentManager(),
            (initConfig, type, target, afterClose) -> {
            });
    }

    private InitializeResult initialize() throws Exception {
        return await(bridge.initialize(new InitializeParams("test-client", "0.0.1",
            BridgeInfo.PROTOCOL_VERSION, workDir.toString())));
    }

    private Path projectFile() {
        return workDir.resolve(".key").resolve("settings.json");
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static SocketChannel connect(Map<String, String> published) throws IOException {
        String endpoint = published.get("endpoint");
        if (endpoint.startsWith("unix:")) {
            return SocketChannel
                    .open(UnixDomainSocketAddress.of(endpoint.substring("unix:".length())));
        }
        String[] parts = endpoint.substring("tcp:".length()).split(":");
        SocketChannel channel =
            SocketChannel.open(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
        java.nio.ByteBuffer token = java.nio.ByteBuffer
                .wrap((published.get("token") + "\n").getBytes(StandardCharsets.US_ASCII));
        while (token.hasRemaining()) {
            channel.write(token);
        }
        return channel;
    }

    /** Collects what the bridge pushes, so notifications can be asserted. */
    private static final class RecordingClient implements IdeClient {

        private final List<StateDto> states = new ArrayList<>();

        @Override
        public synchronized void state(StateDto params) {
            states.add(params);
            notifyAll();
        }

        @Override
        public void log(LogDto params) {
            // The tests assert on state changes only.
        }

        @Override
        public void obligationsChanged(ObligationsChangedDto params) {
        }

        @Override
        public void proveProgress(ProveProgressDto params) {
        }

        synchronized StateDto awaitState() throws InterruptedException {
            long deadline = System.currentTimeMillis() + TIMEOUT_SECONDS * 1000;
            while (states.isEmpty() && System.currentTimeMillis() < deadline) {
                wait(200);
            }
            return states.isEmpty() ? null : states.get(0);
        }
    }
}
