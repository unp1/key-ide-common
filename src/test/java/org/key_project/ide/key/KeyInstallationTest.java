/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks what the bridge reports about the KeY it runs inside.
 */
class KeyInstallationTest {

    @Test
    void readsTheVersionKeyReports() {
        String version = KeyInstallation.version();

        assertThat(version).startsWith("3.1.0-dev");
    }

    @Test
    void reportsADigestOnlyWhenKeyCameFromAJar() {
        String digest = KeyInstallation.jarSha256();

        assertThat(digest).matches("|[0-9a-f]{64}");
    }
}
