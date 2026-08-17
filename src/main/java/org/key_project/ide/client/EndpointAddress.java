/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.key_project.ide.transport.EndpointFile;

/**
 * The endpoint address of the bridge
 *
 * @param endpoint {@code unix:<path>} or {@code tcp:<host>:<port>}, empty on failure
 * @param token the secret a loopback connection presents, empty for a Unix socket
 * @param pid the process serving the address, {@code 0} when unknown
 * @param error why the bridge refused to start, empty when it started
 */
public record EndpointAddress(String endpoint, String token, long pid, String error) {

    /** true iff a startup failure occurred. */
    public boolean isFailure() {
        return !error.isEmpty();
    }

    /** true if a unix domain socket is used, false for a loopback port. */
    public boolean isUnixSocket() {
        return endpoint.startsWith("unix:");
    }

    /**
     * Checks liveness of the server
     * <p>
     * Used to disambiguate between a dead process or
     * one that is currently starting up and hence not yet responding.
     *
     * @return true when the publishing process is alive, and when no pid was published
     */
    public boolean isServerAlive() {
        return pid == 0 || ProcessHandle.of(pid).isPresent();
    }

    /**
     * Reads the published address of a bridge.
     *
     * @param runtimeDir the directory shared with the bridge
     * @return the address, which may describe a failure
     * @throws IOException if the file is missing or unreadable
     */
    public static EndpointAddress read(Path runtimeDir) throws IOException {
        Map<String, String> values = EndpointFile.read(runtimeDir);
        return new EndpointAddress(values.getOrDefault("endpoint", ""),
            values.getOrDefault("token", ""), parsePid(values.get("pid")),
            values.getOrDefault("error", ""));
    }

    private static long parsePid(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
