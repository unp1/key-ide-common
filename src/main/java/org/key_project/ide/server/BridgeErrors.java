/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;

import org.key_project.ide.protocol.Dtos.LoadFailure;
import org.key_project.ide.protocol.Dtos.SourceProblem;

/**
 * The error codes this bridge defines, within the range JSON-RPC reserves for
 * implementations, so a client can react to a cause rather than to a message.
 */
public final class BridgeErrors {

    /** A request arrived before {@code initialize}. */
    public static final int NOT_INITIALIZED = -32001;

    /** The configuration file cannot be read or written. */
    public static final int CONFIG_UNREADABLE = -32002;

    /** The project declares no context with the requested id. */
    public static final int UNKNOWN_CONTEXT = -32003;

    /** KeY could not load the context's sources. */
    public static final int ENVIRONMENT_LOAD_FAILED = -32004;

    /** No method covers the requested position. */
    public static final int NO_METHOD_AT_POSITION = -32005;

    /** The named method is not in the loaded environment. */
    public static final int METHOD_NOT_FOUND = -32006;

    /** This bridge has no KeY, so it can serve configuration but not verification. */
    public static final int VERIFICATION_UNAVAILABLE = -32007;

    /** A run was asked for without a usable name, or under one already in use. */
    public static final int RUN_NOT_NAMED = -32008;

    private BridgeErrors() {
    }

    /**
     * Builds the exception LSP4J turns into an error response.
     *
     * @param code one of the codes above
     * @param message a sentence naming what went wrong
     * @return the exception to throw from a service method
     */
    public static ResponseErrorException failure(int code, String message) {
        return new ResponseErrorException(new ResponseError(code, message, null));
    }

    /**
     * Builds the error for a context KeY refused to load.
     * <p>
     * The message says which context and sums up the problems, so a client that shows only
     * messages still says something useful. The problems themselves ride along as data, so
     * a client that can mark a line does.
     *
     * @param contextId the context that did not load
     * @param problems what KeY found
     * @return the exception to throw from a service method
     */
    public static ResponseErrorException loadFailed(String contextId,
            java.util.List<SourceProblem> problems) {
        return new ResponseErrorException(new ResponseError(ENVIRONMENT_LOAD_FAILED,
            summarise(contextId, problems), new LoadFailure(contextId, problems)));
    }

    /**
     * How a failed load reads in one message.
     * <p>
     * One problem is quoted with its place. Several are counted, and the first is quoted, so
     * that the message stays a sentence and the rest are read from the data.
     */
    static String summarise(String contextId, java.util.List<SourceProblem> problems) {
        if (problems.isEmpty()) {
            return "KeY could not load context '" + contextId + "'.";
        }
        SourceProblem first = problems.get(0);
        String where = first.uri() == null ? ""
                : " at " + fileName(first.uri()) + (first.line() > 0 ? ":" + first.line() : "");
        if (problems.size() == 1) {
            return "KeY could not load context '" + contextId + "'" + where + ": "
                + first.message();
        }
        return "KeY could not load context '" + contextId + "': " + problems.size()
            + " problems, the first" + where + ": " + first.message();
    }

    private static String fileName(String uri) {
        int slash = uri.lastIndexOf('/');
        return slash < 0 ? uri : uri.substring(slash + 1);
    }
}
