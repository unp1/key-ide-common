/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.protocol;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

import org.key_project.ide.protocol.Dtos.InitializeParams;
import org.key_project.ide.protocol.Dtos.InitializeResult;

/**
 * Everything the IDE can ask of the bridge.
 * <p>
 * The delegate splits the namespace, so configuration methods arrive as
 * {@code config/get} and the rest as plain names.
 */
public interface BridgeService {

    /**
     * Checks that client and bridge can work together.
     *
     * @param params the client identifying itself and naming the project root
     * @return the bridge's identity and capabilities
     */
    @JsonRequest
    CompletableFuture<InitializeResult> initialize(InitializeParams params);

    /**
     * Answers that the bridge is still serving.
     * <p>
     * The message loop answers this whatever else the bridge is doing, since proofs run on
     * threads of their own. A client whose request has passed its deadline can therefore
     * tell a slow answer from a bridge that has stopped answering at all.
     *
     * @return {@code true}, as the answer itself is the point
     */
    @JsonRequest("ping")
    CompletableFuture<Boolean> ping();

    /** Asks the bridge to stop. */
    @JsonNotification
    void exit();

    /** The configuration half of the protocol, reached as {@code config/*}. */
    @JsonDelegate
    ConfigService getConfigService();

    /** The verification half of the protocol: listing, proving, and replaying. */
    @JsonDelegate
    VerificationService getVerificationService();
}
