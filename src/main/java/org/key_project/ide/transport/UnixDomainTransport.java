/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.transport;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A transport over a Unix domain socket, available on Linux, macOS, and Windows 10 and
 * later. Access is governed by the permissions of the directory holding the socket file,
 * so no token is needed.
 */
public final class UnixDomainTransport implements Transport {

    /**
     * The longest socket path that is portable. The underlying {@code sun_path} field
     * holds 104 bytes on macOS and 108 on Linux, and binding a longer path fails.
     */
    private static final int MAX_PATH_LENGTH = 100;

    private final ServerSocketChannel server;
    private final Path socketPath;

    private UnixDomainTransport(ServerSocketChannel server, Path socketPath) {
        this.server = server;
        this.socketPath = socketPath;
    }

    /**
     * Binds a socket at the given path.
     *
     * @param socketPath the file to create, which must not exist
     * @return the listening transport
     * @throws IOException if the platform has no Unix domain sockets, the path is too
     *         long, or binding fails
     */
    public static UnixDomainTransport bind(Path socketPath) throws IOException {
        String path = socketPath.toAbsolutePath().toString();
        if (path.length() > MAX_PATH_LENGTH) {
            throw new IOException("The socket path is " + path.length()
                + " characters, above the portable limit of " + MAX_PATH_LENGTH + ": " + path);
        }
        Files.deleteIfExists(socketPath);

        ServerSocketChannel server;
        try {
            server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        } catch (UnsupportedOperationException e) {
            throw new IOException("This platform has no Unix domain sockets.", e);
        }
        try {
            server.bind(UnixDomainSocketAddress.of(socketPath));
        } catch (IOException e) {
            server.close();
            throw e;
        }
        return new UnixDomainTransport(server, socketPath);
    }

    @Override
    public String endpoint() {
        return "unix:" + socketPath.toAbsolutePath();
    }

    @Override
    public String token() {
        return "";
    }

    @Override
    public Connection accept() throws IOException {
        SocketChannel channel = server.accept();
        return new ChannelConnection(channel);
    }

    @Override
    public void close() throws IOException {
        try {
            server.close();
        } finally {
            // The socket file outlives the channel and would block the next bind.
            Files.deleteIfExists(socketPath);
        }
    }
}
