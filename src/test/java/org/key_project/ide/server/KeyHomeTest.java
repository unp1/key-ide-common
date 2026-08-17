/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what is reset in the home a client gives the bridge.
 * <p>
 * A proof attempted with its own options would otherwise leave them behind as the defaults
 * of the next start, and a log or a cache is worth keeping across starts.
 */
class KeyHomeTest {

    @Test
    void settingsAreRemovedAndTheRestIsKept(@TempDir Path home) throws IOException {
        Files.createDirectories(home.resolve("v3.1"));
        Path settings = Files.writeString(home.resolve("v3.1/proof-settings.json"), "{}");
        Path colors = Files.writeString(home.resolve("v3.1/colors.json"), "{}");
        Path log = Files.writeString(home.resolve("v3.1/key.log"), "started");
        Files.createDirectories(home.resolve("cache"));

        assertThat(KeyHome.resetSettings(home)).containsExactly(settings);
        assertThat(Files.exists(settings)).isFalse();
        assertThat(Files.exists(colors)).isTrue();
        assertThat(Files.exists(log)).isTrue();
        assertThat(Files.isDirectory(home.resolve("cache"))).isTrue();
    }

    @Test
    void aHomeThatDoesNotExistIsLeftAlone(@TempDir Path parent) {
        assertThat(KeyHome.resetSettings(parent.resolve("never-made"))).isEmpty();
    }

    @Test
    void aFileThatIsNotASettingsFileSurvivesItsName(@TempDir Path home) throws IOException {
        Path data = Files.writeString(home.resolve("proof-cache.json"), "{}");
        Path text = Files.writeString(home.resolve("settings.txt"), "not json");

        assertThat(KeyHome.resetSettings(home)).isEmpty();
        assertThat(Files.exists(data)).isTrue();
        assertThat(Files.exists(text)).isTrue();
    }
}
