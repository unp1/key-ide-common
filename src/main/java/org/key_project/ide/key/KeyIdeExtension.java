/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.uka.ilkd.key.control.AutoModeListener;
import de.uka.ilkd.key.core.KeYMediator;
import de.uka.ilkd.key.gui.MainWindow;
import de.uka.ilkd.key.gui.extension.api.KeYGuiExtension;
import de.uka.ilkd.key.proof.ProofEvent;

import org.key_project.ide.protocol.Dtos.ObligationsChangedDto;
import org.key_project.ide.protocol.IdeClient;
import org.key_project.ide.protocol.VerificationService;
import org.key_project.ide.server.BridgeInfo;
import org.key_project.ide.server.BridgeServer;
import org.key_project.ide.server.BridgeServiceImpl;
import org.key_project.ide.server.BridgeSession;
import org.key_project.ide.server.VerificationServiceImpl;
import org.key_project.ide.transport.EndpointFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the bridge inside a KeY that an IDE launched.
 * <p>
 * KeY finds this class through the service loader, so it is present in every KeY run that
 * has the bridge on its classpath. It stays inert unless the IDE named a runtime directory,
 * so a KeY started by a user is unaffected.
 */
@KeYGuiExtension.Info(name = "KeY IDE bridge",
    description = "Serves the IntelliJ and VS Code plugins over JSON-RPC",
    experimental = false, optional = false, priority = 0)
public final class KeyIdeExtension implements KeYGuiExtension, KeYGuiExtension.Startup {

    /**
     * The property naming the directory the bridge and the IDE share. The IDE passes it
     * on KeY's command line, and its absence means KeY was not started by an IDE.
     */
    public static final String RUNTIME_DIR_PROPERTY = "key.ide.runtimeDir";

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyIdeExtension.class);

    /** The version this bridge reports to a client. */
    private static final String BRIDGE_VERSION = "0.1.0-dev";

    /** What this bridge can do. */
    private static final List<String> CAPABILITIES = List.of("config", "verify");

    private final EnvironmentManager environments = new EnvironmentManager();

    private BridgeServer server;
    private Path runtimeDir;

    @Override
    public void init(MainWindow window, KeYMediator mediator) {
        String configured = System.getProperty(RUNTIME_DIR_PROPERTY);
        if (configured == null || configured.isBlank()) {
            LOGGER.debug("The KeY IDE bridge stays inactive: no {} was given.",
                RUNTIME_DIR_PROPERTY);
            return;
        }
        start(Path.of(configured), mediator);
    }

    /**
     * Checks that this KeY can be driven and, if so, begins serving.
     *
     * @param directory the directory shared with the IDE
     * @param mediator the mediator whose proof runs are reported to the IDE
     */
    private void start(Path directory, KeYMediator mediator) {
        runtimeDir = directory;
        List<String> missing = new CapabilityProbe().missingMembers();
        if (!missing.isEmpty()) {
            refuse("This KeY does not expose the members the bridge calls: "
                + String.join("; ", missing));
            return;
        }
        try {
            BridgeInfo info = new BridgeInfo(KeyInstallation.version(), KeyInstallation.jarSha256(),
                BRIDGE_VERSION, BridgeInfo.PROTOCOL_VERSION, CAPABILITIES);
            BridgeSession session = new BridgeSession();
            VerificationService verification =
                new VerificationServiceImpl(session, environments, new SwingProofBrowser());
            // KeY's window belongs to the user and outlives a single editor session, so
            // this bridge waits for the next client rather than stopping with the first.
            server = BridgeServer.start(runtimeDir,
                new BridgeServiceImpl(info, session, verification, this::stop),
                BridgeServer.AfterDisconnect.KEEP_LISTENING);
            reportProofChanges(mediator);
            LOGGER.info("The KeY IDE bridge is listening on {}.", server.endpoint());
        } catch (IOException e) {
            refuse("The bridge could not start: " + e.getMessage());
        }
    }

    /**
     * Notifies the IDE when a proof run ends, so that a listing of proof states does not go
     * stale while it is shown.
     *
     * @param mediator the mediator whose proof control reports runs
     */
    private void reportProofChanges(KeYMediator mediator) {
        mediator.getUI().getProofControl().addAutoModeListener(new AutoModeListener() {
            @Override
            public void autoModeStarted(ProofEvent event) {
                // Nothing has changed yet.
            }

            @Override
            public void autoModeStopped(ProofEvent event) {
                IdeClient client = server == null ? null : server.client();
                if (client != null) {
                    client.obligationsChanged(new ObligationsChangedDto(null));
                }
            }
        });
    }

    /**
     * Reports a startup failure where the IDE is already waiting for it, rather than
     * leaving it to time out.
     *
     * @param message a sentence naming what went wrong
     */
    private void refuse(String message) {
        LOGGER.error("The KeY IDE bridge did not start. {}", message);
        try {
            Files.createDirectories(runtimeDir);
            EndpointFile.writeError(runtimeDir, message);
        } catch (IOException e) {
            LOGGER.error("The failure could not be published to {}: {}", runtimeDir,
                e.getMessage());
        }
    }

    /** Stops serving and removes the address, leaving KeY itself running. */
    private void stop() {
        try {
            environments.close();
            if (server != null) {
                server.close();
            }
            Files.deleteIfExists(runtimeDir.resolve(EndpointFile.NAME));
        } catch (IOException e) {
            LOGGER.warn("The KeY IDE bridge did not shut down cleanly: {}", e.getMessage());
        }
    }
}
