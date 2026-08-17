/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;

import org.key_project.ide.config.ConfigStore;
import org.key_project.ide.config.ProjectConfig;
import org.key_project.ide.config.VerificationContext;

/**
 * Reads the project's contexts for the services that need them, so the configuration file
 * is opened in one place.
 */
final class ProjectContexts {

    private ProjectContexts() {
    }

    /**
     * The configuration of the project this session serves.
     *
     * @param session the session holding the project root
     * @return the stored configuration, empty when the project has no file yet
     */
    static ProjectConfig read(BridgeSession session) {
        try {
            return new ConfigStore(session.projectRoot()).read();
        } catch (IOException e) {
            throw BridgeErrors.failure(BridgeErrors.CONFIG_UNREADABLE, e.getMessage());
        }
    }

    /**
     * One context, with its paths made absolute.
     *
     * @param session the session holding the project root
     * @param contextId the context to look up
     * @return the context, ready to hand to KeY
     */
    static VerificationContext resolved(BridgeSession session, String contextId) {
        return read(session).context(contextId)
                .orElseThrow(() -> BridgeErrors.failure(BridgeErrors.UNKNOWN_CONTEXT,
                    "The project declares no context with the id '" + contextId + "'."))
                .resolveAgainst(session.projectRoot());
    }
}
