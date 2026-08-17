/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.nio.file.Path;

/**
 * What {@code initialize} establishes and the rest of the session needs.
 * <p>
 * The project root arrives with the first request rather than at construction, because
 * the bridge process is started before it knows which project it serves.
 */
public final class BridgeSession {

    private volatile Path projectRoot;

    /**
     * Records the project this session serves.
     * <p>
     * The path is made absolute here. A client that names its project relatively would
     * otherwise have every path of the session compared against a relative one, and
     * relativizing an absolute path against a relative one fails rather than answering.
     *
     * @param projectRoot the path relative paths are written against
     */
    public void initialize(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    /**
     * The project root.
     *
     * @return the root recorded by {@code initialize}
     * @throws org.eclipse.lsp4j.jsonrpc.ResponseErrorException if {@code initialize} has
     *         not run
     */
    public Path projectRoot() {
        Path root = projectRoot;
        if (root == null) {
            throw BridgeErrors.failure(BridgeErrors.NOT_INITIALIZED,
                "The bridge has not been initialized. Send 'initialize' first.");
        }
        return root;
    }
}
