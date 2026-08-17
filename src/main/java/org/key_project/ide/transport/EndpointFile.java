/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.transport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * The file through which the bridge publishes its address for the IDE to connect to.
 * <p>
 * The bridge writes to this file its connection address, once it is listening (after startup).
 * The client waits for the file to appear. To avoid that a client reads a half-written
 * address the file is written atomically.
 * <p>
 * The format is one {@code key=value} per line, UTF-8:
 *
 * <pre>
 * endpoint=unix:/tmp/key-ide-1234/bridge.sock
 * pid=4711
 * token=
 * </pre>
 */
public final class EndpointFile {

    /** The file name inside the runtime directory. */
    public static final String NAME = "endpoint";

    private EndpointFile() {
    }

    /**
     * Publishes the address of a listening transport.
     *
     * @param runtimeDir the directory shared with the client
     * @param transport the transport already bound
     * @return the file written
     * @throws IOException if the file cannot be written
     */
    public static Path write(Path runtimeDir, Transport transport) throws IOException {
        Map<String, String> values = new TreeMap<>();
        values.put("endpoint", transport.endpoint());
        values.put("token", transport.token());
        // The process that serves this address. A file left behind by a crash names a
        // process that is gone, which is how a client tells a dead address from a bridge
        // that is still starting: connecting fails immediately in both cases.
        values.put("pid", Long.toString(ProcessHandle.current().pid()));
        return write(runtimeDir, values);
    }

    /**
     * Publishes a startup failure instead of an address.
     * <p>
     * The client waits for this one file either way, so a bridge that cannot start says
     * so here rather than leaving the IDE to time out.
     *
     * @param runtimeDir the directory shared with the client
     * @param message a sentence naming what went wrong
     * @return the file written
     * @throws IOException if the file cannot be written
     */
    public static Path writeError(Path runtimeDir, String message) throws IOException {
        Map<String, String> values = new TreeMap<>();
        values.put("error", message.replace('\n', ' '));
        return write(runtimeDir, values);
    }

    private static Path write(Path runtimeDir, Map<String, String> values) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }

        Path target = runtimeDir.resolve(NAME);
        Path temp = runtimeDir.resolve(NAME + ".tmp");
        Files.writeString(temp, text.toString(), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    /**
     * Reads a published address, for tests and for clients written in Java.
     *
     * @param runtimeDir the directory shared with the bridge
     * @return the values found in the file
     * @throws IOException if the file is missing or unreadable
     */
    public static Map<String, String> read(Path runtimeDir) throws IOException {
        Map<String, String> values = new TreeMap<>();
        for (String line : Files.readAllLines(runtimeDir.resolve(NAME), StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator >= 0) {
                values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return values;
    }
}
