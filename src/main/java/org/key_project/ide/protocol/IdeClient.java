/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.protocol;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

import org.key_project.ide.protocol.Dtos.LogDto;
import org.key_project.ide.protocol.Dtos.ObligationsChangedDto;
import org.key_project.ide.protocol.Dtos.ProveProgressDto;
import org.key_project.ide.protocol.Dtos.StateDto;

/**
 * The notifications the bridge sends to the IDE.
 */
public interface IdeClient {

    /**
     * Reports a change of state, so the IDE can show whether KeY is ready.
     *
     * @param params the new state
     */
    @JsonNotification("key/state")
    void state(StateDto params);

    /**
     * Forwards a message meant for the user.
     *
     * @param params the message
     */
    @JsonNotification("log/message")
    void log(LogDto params);

    /**
     * Reports that the proof status of a context may have changed, so a listing is stale.
     *
     * @param params which context is affected
     */
    @JsonNotification("po/changed")
    void obligationsChanged(ObligationsChangedDto params);

    /**
     * Reports how far a run has got.
     *
     * @param params what is being attempted and how much is left
     */
    @JsonNotification("po/progress")
    void proveProgress(ProveProgressDto params);
}
