/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises both transports against a real client, since the point of the transport is
 * that a second process can reach it.
 */
class TransportTest {

    private static final byte[] GREETING = "hello\n".getBytes(StandardCharsets.UTF_8);

    private final ExecutorService clients = Executors.newSingleThreadExecutor();

    @AfterEach
    void stopClients() {
        clients.shutdownNow();
    }

    @Test
    void carriesBytesOverAUnixDomainSocket(@TempDir Path runtimeDir) throws Exception {
        try (Transport transport = UnixDomainTransport.bind(runtimeDir.resolve("bridge.sock"))) {
            Path socketPath = Path.of(transport.endpoint().substring("unix:".length()));
            Future<byte[]> client = clients.submit(() -> {
                try (SocketChannel channel =
                    SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
                    return exchange(channel);
                }
            });

            try (Connection connection = transport.accept()) {
                echo(connection);
            }

            assertThat(client.get(10, TimeUnit.SECONDS)).isEqualTo(GREETING);
        }
    }

    @Test
    void carriesBytesOverALoopbackPortAfterTheTokenIsAccepted() throws Exception {
        try (LoopbackTransport transport = LoopbackTransport.bind()) {
            int port = port(transport.endpoint());
            String token = transport.token();
            Future<byte[]> client = clients.submit(() -> {
                try (SocketChannel channel = SocketChannel.open(
                    new InetSocketAddress("127.0.0.1", port))) {
                    write(channel, (token + "\n").getBytes(StandardCharsets.US_ASCII));
                    return exchange(channel);
                }
            });

            try (Connection connection = transport.accept()) {
                echo(connection);
            }

            assertThat(client.get(10, TimeUnit.SECONDS)).isEqualTo(GREETING);
        }
    }

    @Test
    void refusesALoopbackClientPresentingTheWrongToken() throws Exception {
        try (LoopbackTransport transport = LoopbackTransport.bind()) {
            int port = port(transport.endpoint());
            clients.submit(() -> {
                try (SocketChannel channel = SocketChannel.open(
                    new InetSocketAddress("127.0.0.1", port))) {
                    write(channel, "not-the-token\n".getBytes(StandardCharsets.US_ASCII));
                    Thread.sleep(200);
                }
                return null;
            });

            assertThatThrownBy(transport::accept)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("wrong token");
        }
    }

    @Test
    void issuesADifferentTokenPerTransport() throws Exception {
        try (LoopbackTransport first = LoopbackTransport.bind();
                LoopbackTransport second = LoopbackTransport.bind()) {
            assertThat(first.token()).isNotEqualTo(second.token());
            assertThat(first.token()).hasSizeGreaterThan(32);
        }
    }

    @Test
    void prefersAUnixDomainSocketWhereOneIsAvailable(@TempDir Path runtimeDir) throws Exception {
        try (Transport transport = Transports.bind(runtimeDir.resolve("rt"))) {
            assertThat(transport.endpoint()).startsWith("unix:");
            assertThat(transport.token()).isEmpty();
        }
    }

    @Test
    void fallsBackToALoopbackPortWhenTheSocketPathIsTooLong(@TempDir Path runtimeDir)
            throws Exception {
        Path deep = runtimeDir;
        while (deep.toString().length() < 120) {
            deep = deep.resolve("nested-directory-making-the-path-long");
        }

        try (Transport transport = Transports.bind(deep)) {
            assertThat(transport.endpoint()).startsWith("tcp:127.0.0.1:");
            assertThat(transport.token()).isNotEmpty();
        }
    }

    @Test
    void publishesTheAddressWhereAClientCanReadIt(@TempDir Path runtimeDir) throws Exception {
        try (Transport transport = Transports.bind(runtimeDir.resolve("rt"))) {
            EndpointFile.write(runtimeDir.resolve("rt"), transport);

            Map<String, String> published = EndpointFile.read(runtimeDir.resolve("rt"));

            assertThat(published).containsEntry("endpoint", transport.endpoint())
                    .containsEntry("token", transport.token());
        }
    }

    private static int port(String endpoint) {
        return Integer.parseInt(endpoint.substring(endpoint.lastIndexOf(':') + 1));
    }

    /** Sends the greeting and reads the echo the bridge sends back. */
    private static byte[] exchange(SocketChannel channel) throws IOException {
        write(channel, GREETING);
        byte[] response = new byte[GREETING.length];
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(response);
        while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
            // keep reading until the echo is complete
        }
        return response;
    }

    private static void write(SocketChannel channel, byte[] bytes) throws IOException {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /** Reads the greeting from a connection and writes it straight back. */
    private static void echo(Connection connection) throws IOException {
        InputStream input = connection.input();
        OutputStream output = connection.output();
        byte[] received = input.readNBytes(GREETING.length);
        output.write(received);
        output.flush();
    }
}
