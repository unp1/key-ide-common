/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.transport;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An accepted client connection, reduced to the stream pair a JSON-RPC layer needs.
 */
public interface Connection extends Closeable {

    /** Bytes arriving from the client. */
    InputStream input();

    /** Bytes going to the client. */
    OutputStream output();
}
