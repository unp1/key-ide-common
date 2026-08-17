/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.transport;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Chooses the transport for this machine.
 * <p>
 * A Unix domain socket is preferred on every platform that has one, which is Linux,
 * macOS, and Windows 10 and later. It is protected by file permissions and needs no
 * token. Where it is unavailable, or where the socket path would be too long, a loopback
 * port with a token is used instead.
 */
public final class Transports {

    private static final Logger LOGGER = System.getLogger(Transports.class.getName());

    /** The socket file created inside the runtime directory. */
    public static final String SOCKET_NAME = "bridge.sock";

    /**
     * Set to {@code tcp} to skip the Unix domain socket.
     * <p>
     * A client whose runtime has no Unix domain sockets cannot connect to one even where
     * this JVM can bind it, which is a real possibility on Windows. Such a client asks for
     * a loopback port instead.
     */
    public static final String TRANSPORT_PROPERTY = "key.ide.transport";

    private Transports() {
    }

    /**
     * Creates the runtime directory if needed and binds the best available transport.
     *
     * @param runtimeDir the directory shared with the client
     * @return a listening transport
     * @throws IOException if neither transport can be bound
     */
    public static Transport bind(Path runtimeDir) throws IOException {
        createPrivateDirectory(runtimeDir);
        if ("tcp".equalsIgnoreCase(System.getProperty(TRANSPORT_PROPERTY))) {
            LOGGER.log(Level.INFO, "Using a loopback port, as " + TRANSPORT_PROPERTY + " asks.");
            return LoopbackTransport.bind();
        }
        try {
            return UnixDomainTransport.bind(runtimeDir.resolve(SOCKET_NAME));
        } catch (IOException e) {
            LOGGER.log(Level.INFO,
                "Falling back to a loopback port, because a Unix domain socket is unavailable: "
                    + e.getMessage());
            return LoopbackTransport.bind();
        }
    }

    /**
     * Creates a directory only its owner can enter, so the socket inside it is reachable
     * only by this user.
     * <p>
     * Windows has no POSIX permissions. There the directory inherits the access rules of
     * its parent, which is why callers place the runtime directory under the per-user
     * temporary directory.
     *
     * @param directory the directory to create
     * @throws IOException if it cannot be created
     */
    private static void createPrivateDirectory(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory, PosixFilePermissions
                    .asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(directory);
        }
    }
}
