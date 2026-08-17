/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The directory KeY keeps its own files in, when a client gave the bridge one.
 * <p>
 * KeY writes its settings there as it goes: every settings object it makes saves itself
 * whenever one of its values changes, so a proof attempted with its own options leaves them
 * behind as the defaults of the next start. The settings files are therefore reset at every
 * start, and the logs and the caches stay.
 * <p>
 * This happens only for a home the bridge was told to use. A KeY started by a user keeps its
 * own settings, and the bridge never touches them.
 */
public final class KeyHome {

    /** The property a client sets to give KeY a home, which is KeY's own. */
    public static final String PROPERTY = "key.home";

    private KeyHome() {
    }

    /**
     * Resets the settings of the home this bridge was given.
     * <p>
     * Called before any KeY class is loaded, since loading one writes the files this
     * removes.
     *
     * @return the files that were removed
     */
    public static List<Path> resetSettings() {
        String given = System.getProperty(PROPERTY);
        if (given == null || given.isBlank()) {
            return List.of();
        }
        return resetSettings(Path.of(given));
    }

    /**
     * Resets the settings files below a directory.
     *
     * @param home the directory KeY was told to use
     * @return the files that were removed, empty when the directory holds none or does not
     *         exist
     */
    public static List<Path> resetSettings(Path home) {
        if (!Files.isDirectory(home)) {
            return List.of();
        }
        try (Stream<Path> found = Files.walk(home)) {
            List<Path> settings = found.filter(KeyHome::isSettingsFile).toList();
            for (Path file : settings) {
                Files.deleteIfExists(file);
            }
            return settings;
        } catch (IOException e) {
            // A home that cannot be read is left as it is. KeY then starts from whatever is
            // in it, which is the behaviour without this reset.
            return List.of();
        }
    }

    private static boolean isSettingsFile(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file) && name.endsWith(".json") && name.contains("settings");
    }
}
