/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.key_project.ide.key.CapabilityProbe;
import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.key.KeyInstallation;
import org.key_project.ide.key.NoProofBrowser;
import org.key_project.ide.protocol.BridgeService;

/**
 * The bridge an IDE runs to work with KeY.
 * <p>
 * It loads sources, lists the proof obligations, resolves positions, and runs proofs, all
 * without a user interface. KeY's window is not started here: inspecting a proof is a
 * separate task, and proofs are saved, so a window that needs one opens the file.
 * <p>
 * That separation is what makes this the ordinary case. Proving needs a prover, not a
 * prover's user interface, and a window that opens whenever a listing is read is a window
 * the user did not ask for.
 *
 * <pre>
 * java -cp key-exe.jar:key-ide-core-all.jar org.key_project.ide.server.BridgeMain &lt;runtime-directory&gt;
 * </pre>
 */
public final class BridgeMain {

    /** The version this bridge reports. */
    private static final String BRIDGE_VERSION = "0.1.0-dev";

    /** What this bridge can do. Inspecting a proof is not among them. */
    private static final List<String> CAPABILITIES = List.of("config", "verify", "prove");

    private BridgeMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: BridgeMain <runtime-directory>");
            System.exit(2);
        }
        Path runtimeDir = Path.of(args[0]);
        // Before any KeY class is loaded, since loading one writes the files this removes.
        KeyHome.resetSettings();
        initialiseLogging();
        List<String> missing = new CapabilityProbe().missingMembers();
        if (!missing.isEmpty()) {
            org.key_project.ide.transport.EndpointFile.writeError(runtimeDir,
                "This KeY does not expose the members the bridge calls: "
                    + String.join("; ", missing));
            System.exit(3);
        }

        try (EnvironmentManager environments = new EnvironmentManager();
                BridgeServer server = start(runtimeDir, environments)) {
            server.awaitStop();
        }
        // Loading a context makes KeY read its settings, which touches Swing and starts the
        // event thread. That thread never ends and keeps the JVM alive, so returning from
        // main would leave the bridge running with no client.
        System.exit(0);
    }

    /**
     * Sets logging up before any KeY class is touched.
     * <p>
     * KeY's logback configuration installs a listener that reads its configuration
     * directory, which asks KeY for its version. Touching a KeY class first starts that
     * initialisation in the middle, where it fails on itself. KeY's own startup configures
     * logging first, so this does the same.
     */
    private static void initialiseLogging() {
        org.slf4j.LoggerFactory.getLogger(BridgeMain.class);
    }

    /**
     * Starts a headless bridge.
     *
     * @param runtimeDir the directory shared with the IDE
     * @param environments where loaded contexts are kept
     * @return the running server, which stops when its client disconnects
     * @throws IOException if the transport cannot be bound or the address published
     */
    public static BridgeServer start(Path runtimeDir, EnvironmentManager environments)
            throws IOException {
        initialiseLogging();
        BridgeInfo info = new BridgeInfo(KeyInstallation.version(), KeyInstallation.jarSha256(),
            BRIDGE_VERSION, BridgeInfo.PROTOCOL_VERSION, CAPABILITIES);
        BridgeSession session = new BridgeSession();

        // The server is needed to reach the client that progress is reported to, and the
        // service is needed to build the server, so the reference is set afterwards.
        AtomicReference<BridgeServer> server = new AtomicReference<>();
        VerificationServiceImpl verification = new VerificationServiceImpl(session, environments,
            new NoProofBrowser(), () -> server.get() == null ? null : server.get().client());
        BridgeService service = new BridgeServiceImpl(info, session, verification,
            () -> closeQuietly(server.get()));

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
