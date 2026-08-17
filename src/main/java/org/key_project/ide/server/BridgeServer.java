/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.Closeable;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.lsp4j.jsonrpc.Launcher;

import org.key_project.ide.protocol.BridgeService;
import org.key_project.ide.protocol.IdeClient;
import org.key_project.ide.transport.Connection;
import org.key_project.ide.transport.EndpointFile;
import org.key_project.ide.transport.Transport;
import org.key_project.ide.transport.Transports;

/**
 * Listens for the IDE and serves one client at a time.
 * <p>
 * The address is published through the endpoint file rather than announced on standard
 * output, which carries KeY's log.
 */
public final class BridgeServer implements Closeable {

    /** What a server does once its client disconnects. */
    public enum AfterDisconnect {
        /**
         * Waits for another client. This suits a bridge inside KeY, whose window belongs
         * to the user and outlives a single editor session.
         */
        KEEP_LISTENING,
        /**
         * Stops. This suits a bridge that serves one editor only, where waiting leaves a
         * process behind whenever that editor exits without disconnecting.
         */
        STOP
    }

    private static final Logger LOGGER = System.getLogger(BridgeServer.class.getName());

    /**
     * How long a server waits for its first client.
     * <p>
     * The IDE connects as soon as it has read the endpoint file, so this expires only when
     * the IDE died between starting the bridge and connecting to it. Without the deadline
     * such a bridge waits for a client that never comes, and stays until the machine is
     * restarted.
     */
    private static final Duration FIRST_CLIENT_DEADLINE = Duration.ofMinutes(5);

    private final Transport transport;
    private final BridgeService service;
    private final AfterDisconnect afterDisconnect;
    private final Thread acceptor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean served = new AtomicBoolean();
    private final CountDownLatch stopped = new CountDownLatch(1);

    private volatile IdeClient client;

    private BridgeServer(Transport transport, BridgeService service,
            AfterDisconnect afterDisconnect) {
        this.transport = transport;
        this.service = service;
        this.afterDisconnect = afterDisconnect;
        this.acceptor = new Thread(this::acceptLoop, "key-ide-bridge-acceptor");
        this.acceptor.setDaemon(true);
    }

    /**
     * Binds a transport, publishes its address, and begins accepting clients.
     *
     * @param runtimeDir the directory shared with the IDE
     * @param service the implementation to expose
     * @param afterDisconnect what to do once a client disconnects
     * @return the running server
     * @throws IOException if the transport cannot be bound or the address published
     */
    public static BridgeServer start(Path runtimeDir, BridgeService service,
            AfterDisconnect afterDisconnect) throws IOException {
        Transport transport = Transports.bind(runtimeDir);
        BridgeServer server = new BridgeServer(transport, service, afterDisconnect);
        EndpointFile.write(runtimeDir, transport);
        server.acceptor.start();
        server.giveUpIfNobodyConnects();
        return server;
    }

    /** Stops a server that is still waiting for its first client after the deadline. */
    private void giveUpIfNobodyConnects() {
        Thread deadline = new Thread(() -> {
            try {
                Thread.sleep(FIRST_CLIENT_DEADLINE.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!served.get() && running.get()) {
                LOGGER.log(Level.WARNING, "No client connected within " + FIRST_CLIENT_DEADLINE
                    + "; stopping.");
                try {
                    close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "The transport did not close: " + e.getMessage());
                }
            }
        }, "key-ide-bridge-first-client-deadline");
        deadline.setDaemon(true);
        deadline.start();
    }

    /**
     * Waits until this server stops serving, either because it was closed or because its
     * client disconnected and it does not wait for another.
     *
     * @throws InterruptedException if the wait is interrupted
     */
    public void awaitStop() throws InterruptedException {
        stopped.await();
    }

    /** The address clients connect to. */
    public String endpoint() {
        return transport.endpoint();
    }

    /**
     * The connected client, for notifications.
     *
     * @return the client proxy, or {@code null} while none is connected
     */
    public IdeClient client() {
        return client;
    }

    private void acceptLoop() {
        try {
            while (running.get()) {
                try (Connection connection = transport.accept()) {
                    serve(connection);
                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.log(Level.WARNING, "A client connection ended: " + e.getMessage());
                    }
                }
                if (afterDisconnect == AfterDisconnect.STOP) {
                    LOGGER.log(Level.INFO, "The client is gone; stopping.");
                    running.set(false);
                }
            }
        } finally {
            stopped.countDown();
        }
    }

    /**
     * Runs the message loop for one client and returns when it disconnects.
     *
     * @param connection the accepted connection
     */
    private void serve(Connection connection) {
        Launcher<IdeClient> launcher = Launcher.createLauncher(service, IdeClient.class,
            connection.input(), connection.output());
        served.set(true);
        client = launcher.getRemoteProxy();
        try {
            launcher.startListening().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "The message loop stopped: " + e.getMessage());
        } finally {
            client = null;
        }
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        try {
            transport.close();
        } finally {
            stopped.countDown();
        }
    }
}
