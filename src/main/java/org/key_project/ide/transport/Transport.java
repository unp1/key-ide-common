/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.transport;

import java.io.Closeable;
import java.io.IOException;

/**
 * A listening endpoint the IDE connects to.
 * <p>
 * A transport supplies an exclusive stream pair between two participants.
 */
public interface Transport extends Closeable {

    /**
     * The address a client connects to, in the form written to the endpoint file.
     *
     * @return {@code unix:<path>} or {@code tcp:<host>:<port>}
     */
    String endpoint();

    /**
     * The secret a client presents on connecting, empty when the transport needs none.
     * <p>
     * A Unix domain socket is protected by the permissions of its directory, so it needs
     * no token. A loopback port is reachable by every process on the machine and does.
     *
     * @return the token, or an empty string
     */
    String token();

    /**
     * Waits for a client and returns its connection. Blocks until one arrives.
     *
     * @return the accepted connection
     * @throws IOException if listening fails, or the client fails authentication
     */
    Connection accept() throws IOException;
}
