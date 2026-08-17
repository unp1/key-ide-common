/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.key_project.ide.protocol.BridgeService;

/**
 * A bridge that serves configuration and nothing else.
 * <p>
 * Editing a project's KeY settings needs no prover: the configuration code reads paths and
 * checks them, and never touches KeY. Serving that from a bridge of its own lets an IDE
 * open its settings form at once, instead of starting KeY to read the classpath. KeY is
 * started when the user verifies something.
 * <p>
 * It runs with the bridge jar alone on the classpath. Nothing here loads a KeY class, so
 * a KeY installation is not needed and is not looked for.
 * <p>
 * It serves one editor only and stops when that editor disconnects. An editor that is
 * killed or crashes never disconnects, and waiting for another client would leave a process
 * behind each time.
 *
 * <pre>
 * java -cp key-ide-core-all.jar org.key_project.ide.server.ConfigBridgeMain &lt;runtime-directory&gt;
 * </pre>
 */
public final class ConfigBridgeMain {

    /** The version this bridge reports. */
    private static final String BRIDGE_VERSION = "0.1.0-dev";

    private ConfigBridgeMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: ConfigBridgeMain <runtime-directory>");
            System.exit(2);
        }

        try (BridgeServer server = start(Path.of(args[0]))) {
            server.awaitStop();
        }
        // A bridge that has served its client is finished, whatever threads remain.
        System.exit(0);
    }

    /**
     * Starts a configuration-only bridge.
     *
     * @param runtimeDir the directory shared with the IDE
     * @return the running server, which stops when its client disconnects
     * @throws IOException if the transport cannot be bound or the address published
     */
    public static BridgeServer start(Path runtimeDir) throws IOException {
        BridgeInfo info = new BridgeInfo("", "", BRIDGE_VERSION, BridgeInfo.PROTOCOL_VERSION,
            List.of("config"));

        // The service needs to close the server it is served by, so the reference is set
        // once the server exists.
        AtomicReference<BridgeServer> server = new AtomicReference<>();
        BridgeService service = new BridgeServiceImpl(info, new BridgeSession(),
            new UnavailableVerificationService(), () -> closeQuietly(server.get()));
        server.set(BridgeServer.start(runtimeDir, service, BridgeServer.AfterDisconnect.STOP));
        return server.get();
    }

    private static void closeQuietly(BridgeServer server) {
        if (server == null) {
            return;
        }
        try {
            server.close();
        } catch (IOException e) {
            // The server is being shut down; nothing here can act on a failure to close.
        }
    }
}
