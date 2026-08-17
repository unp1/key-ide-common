/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.protocol.Dtos.InitializeParams;
import org.key_project.ide.protocol.Dtos.InitializeResult;
import org.key_project.ide.protocol.Dtos.LogDto;
import org.key_project.ide.protocol.Dtos.ObligationsChangedDto;
import org.key_project.ide.protocol.Dtos.ProveProgressDto;
import org.key_project.ide.protocol.Dtos.StateDto;
import org.key_project.ide.protocol.IdeClient;
import org.key_project.ide.server.BridgeInfo;
import org.key_project.ide.server.BridgeServer;
import org.key_project.ide.server.BridgeServiceImpl;
import org.key_project.ide.server.BridgeSession;
import org.key_project.ide.server.VerificationServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers what a plugin has to get right when it attaches to a bridge: refusing an address
 * left behind by a crash, reporting a refusal to start, and shutting down without making
 * the message layer log a failure for an ordinary close.
 */
class BridgeClientTest {

    @TempDir
    Path workDir;

    private BridgeServer server;

    @AfterEach
    void stopServer() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void talksToARunningBridge() throws Exception {
        Path runtimeDir = startServer();

        try (BridgeClient client = BridgeClient.connect(runtimeDir, new SilentClient())) {
            InitializeResult result = client.service()
                    .initialize(new InitializeParams("test", "0.0.1", BridgeInfo.PROTOCOL_VERSION,
                        workDir.toString()))
                    .get(10, TimeUnit.SECONDS);

            assertThat(result.keyVersion()).isEqualTo("3.1.0-dev");
        }
    }

    @Test
    void refusesAnAddressLeftBehindByACrash() throws Exception {
        Path runtimeDir = workDir.resolve("rt");
        Files.createDirectories(runtimeDir);
        // A pid that no process holds, standing in for a bridge that has gone.
        long deadPid = findUnusedPid();
        Files.writeString(runtimeDir.resolve("endpoint"),
            "endpoint=unix:" + runtimeDir.resolve("bridge.sock") + "\ntoken=\npid=" + deadPid
                + "\n",
            StandardCharsets.UTF_8);

        assertThatThrownBy(() -> BridgeClient.connect(runtimeDir, new SilentClient()))
                .isInstanceOf(StaleBridgeException.class)
                .hasMessageContaining(String.valueOf(deadPid))
                .hasMessageContaining("no longer running");
    }

    @Test
    void reportsWhyTheBridgeRefusedToStart() throws Exception {
        Path runtimeDir = workDir.resolve("rt");
        Files.createDirectories(runtimeDir);
        Files.writeString(runtimeDir.resolve("endpoint"),
            "error=This KeY does not expose ProofManagementDialog#showInstance\n",
            StandardCharsets.UTF_8);

        assertThatThrownBy(() -> BridgeClient.connect(runtimeDir, new SilentClient()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("ProofManagementDialog#showInstance");
    }

    @Test
    void discardsAnAddressSoTheNextLaunchIsNotMistakenForARunningBridge() throws Exception {
        Path runtimeDir = startServer();
        assertThat(runtimeDir.resolve("endpoint")).exists();

        BridgeClient.discardAddress(runtimeDir);

        assertThat(Files.exists(runtimeDir.resolve("endpoint"))).isFalse();
    }

    @Test
    void closesWithoutMakingTheMessageLayerLogAFailure() throws Exception {
        Path runtimeDir = startServer();
        CollectingHandler handler = new CollectingHandler();
        Logger lsp4jLogger = Logger.getLogger("org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer");
        lsp4jLogger.addHandler(handler);

        try {
            BridgeClient client = BridgeClient.connect(runtimeDir, new SilentClient());
            client.service().initialize(new InitializeParams("test", "0.0.1",
                BridgeInfo.PROTOCOL_VERSION, workDir.toString())).get(10, TimeUnit.SECONDS);
            client.close();
            client.close();

            Thread.sleep(300);
            assertThat(handler.thrown()).isEmpty();
        } finally {
            lsp4jLogger.removeHandler(handler);
        }
    }

    private Path startServer() throws IOException {
        Path runtimeDir = workDir.resolve("rt");
        BridgeInfo info = new BridgeInfo("3.1.0-dev", "digest", "0.1.0-dev",
            BridgeInfo.PROTOCOL_VERSION, List.of("config"));
        BridgeSession session = new BridgeSession();
        server = BridgeServer.start(runtimeDir, new BridgeServiceImpl(info, session,
            new VerificationServiceImpl(session, new EnvironmentManager(),
                (initConfig, type, target, afterClose) -> {
                }),
            () -> {
            }), BridgeServer.AfterDisconnect.KEEP_LISTENING);
        return runtimeDir;
    }

    /** A pid high enough that no process holds it, checked before use. */
    private static long findUnusedPid() {
        for (long candidate = 900000; candidate < 999999; candidate++) {
            if (ProcessHandle.of(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Every candidate pid is in use.");
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

    /** Collects the failures the message layer logs, so a clean close can be asserted. */
    private static final class CollectingHandler extends Handler {

        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        List<LogRecord> thrown() {
            return records.stream().filter(r -> r.getThrown() != null).toList();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        {
            setLevel(Level.ALL);
        }
    }
}
