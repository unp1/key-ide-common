/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * A transport over a TCP port bound to the loopback address, used where Unix domain
 * sockets are unavailable.
 * <p>
 * Every process on the machine can reach a loopback port, so a client proves it was
 * launched by the IDE by sending a token as its first line. The token is generated per
 * process and travels to the client out of band, through the endpoint file.
 */
public final class LoopbackTransport implements Transport {

    /** Bytes read while looking for the end of the token line, before giving up. */
    private static final int MAX_TOKEN_LINE = 512;

    private final ServerSocketChannel server;
    private final int port;
    private final byte[] expectedToken;
    private final String token;

    private LoopbackTransport(ServerSocketChannel server, int port, String token) {
        this.server = server;
        this.port = port;
        this.token = token;
        this.expectedToken = token.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Binds a port chosen by the operating system on the loopback address.
     *
     * @return the listening transport
     * @throws IOException if binding fails
     */
    public static LoopbackTransport bind() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        try {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        } catch (IOException e) {
            server.close();
            throw e;
        }
        int port = ((InetSocketAddress) server.getLocalAddress()).getPort();
        return new LoopbackTransport(server, port, generateToken());
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String endpoint() {
        return "tcp:" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + port;
    }

    @Override
    public String token() {
        return token;
    }

    @Override
    public Connection accept() throws IOException {
        SocketChannel channel = server.accept();
        ChannelConnection connection = new ChannelConnection(channel);
        try {
            authenticate(connection);
        } catch (IOException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    /**
     * Consumes the token line and checks it, leaving the stream positioned at the first
     * message byte.
     *
     * @param connection the freshly accepted connection
     * @throws IOException if the token is absent, malformed, or wrong
     */
    private void authenticate(Connection connection) throws IOException {
        byte[] presented = readTokenLine(connection.input());
        if (!MessageDigest.isEqual(expectedToken, presented)) {
            rejectQuietly(connection.output());
            throw new IOException("A client connected with the wrong token.");
        }
    }

    private byte[] readTokenLine(InputStream input) throws IOException {
        byte[] buffer = new byte[MAX_TOKEN_LINE];
        int length = 0;
        while (length < buffer.length) {
            int read = input.read();
            if (read < 0) {
                throw new IOException("A client closed the connection before sending a token.");
            }
            if (read == '\n') {
                return java.util.Arrays.copyOf(buffer, length);
            }
            buffer[length++] = (byte) read;
        }
        throw new IOException("A client sent no token within " + MAX_TOKEN_LINE + " bytes.");
    }

    private void rejectQuietly(OutputStream output) {
        try {
            output.write("unauthorized\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
        } catch (IOException ignored) {
            // The client is being closed anyway; the refusal is a courtesy.
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
