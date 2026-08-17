/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.client;

import java.io.IOException;

/**
 * Thrown when the address in the runtime directory belongs to a bridge that has gone.
 * <p>
 * It is separate from other failures because the remedy is different: the client deletes
 * the address and launches KeY again, rather than reporting a refused connection to the
 * user.
 */
public class StaleBridgeException extends IOException {

    private static final long serialVersionUID = 1L;

    public StaleBridgeException(String message) {
        super(message);
    }
}
