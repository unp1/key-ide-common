/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.lsp4j.jsonrpc.Launcher;

import org.key_project.ide.protocol.BridgeService;
import org.key_project.ide.protocol.IdeClient;
import org.key_project.ide.transport.EndpointFile;

/**
 * Reference implementation of a client interface communicating with the bridge
 * <p>
 * The IntelliJ plugin uses this directly. The VS Code extension reimplements it in TypeScript,
 * so the steps here are the reference:
 * read the address, refuse a stale one, present the token when the transport asks for it, and shut down in
 * an order that does not make the message layer complain.
 */
public final class BridgeClient implements Closeable {

    private final SocketChannel channel;
    private final BridgeService service;
    private final Future<Void> listening;
    private final AtomicBoolean closed = new AtomicBoolean();

    private BridgeClient(SocketChannel channel, BridgeService service, Future<Void> listening) {
        this.channel = channel;
        this.service = service;
        this.listening = listening;
    }

    /**
     * Connects to the bridge using the specified runtime directory which contains the
     * bridges address
     *
     * @param runtimeDir the directory shared with the bridge
     * @param localClient the client on the IDE side
     * @return the connected client
     * @throws StaleBridgeException if the address belongs to a terminated process, in
     *         which case the caller discards the address and relaunches KeY
     * @throws IOException if the bridge reported a startup failure, or connecting failed
     */
    public static BridgeClient connect(Path runtimeDir, IdeClient localClient) throws IOException {
        EndpointAddress address = EndpointAddress.read(runtimeDir);
        if (address.isFailure()) {
            throw new IOException("The bridge did not start: " + address.error());
        }
        if (!address.isServerAlive()) {
            throw new StaleBridgeException("The address in " + runtimeDir
                + " belongs to process " + address.pid() + ", which is no longer running.");
        }

        SocketChannel channel = open(address);
        try {
            Launcher<BridgeService> launcher = Launcher.createLauncher(localClient,
                BridgeService.class, Channels.newInputStream(channel),
                Channels.newOutputStream(channel));
            Future<Void> listening = launcher.startListening();
            return new BridgeClient(channel, launcher.getRemoteProxy(), listening);
        } catch (RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /**
     * Removes an address, so the next launch is not mistaken for a running bridge.
     *
     * @param runtimeDir the directory shared with the bridge
     * @throws IOException if the file cannot be removed
     */
    public static void discardAddress(Path runtimeDir) throws IOException {
        Files.deleteIfExists(runtimeDir.resolve(EndpointFile.NAME));
    }

    /** The bridge, as a proxy that turns calls into requests. */
    public BridgeService service() {
        return service;
    }

    /**
     * Stops listening and then closes the connection.
     * <p>
     * The order matters. Closing the channel while the reader is still blocked on it
     * raises an asynchronous-close failure inside the message layer, which surfaces as a
     * stack trace in the IDE log for what is in fact an ordinary shutdown.
     */
    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        listening.cancel(true);
        channel.close();
    }

    private static SocketChannel open(EndpointAddress address) throws IOException {
        if (address.isUnixSocket()) {
            String path = address.endpoint().substring("unix:".length());
            return SocketChannel.open(UnixDomainSocketAddress.of(path));
        }
        String hostAndPort = address.endpoint().substring("tcp:".length());
        int separator = hostAndPort.lastIndexOf(':');
        SocketChannel channel = SocketChannel.open(
            new InetSocketAddress(hostAndPort.substring(0, separator),
                Integer.parseInt(hostAndPort.substring(separator + 1))));
        try {
            writeToken(channel, address.token());
        } catch (IOException e) {
            channel.close();
            throw e;
        }
        return channel;
    }

    private static void writeToken(SocketChannel channel, String token) throws IOException {
        ByteBuffer buffer =
            ByteBuffer.wrap((token + "\n").getBytes(StandardCharsets.US_ASCII));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
