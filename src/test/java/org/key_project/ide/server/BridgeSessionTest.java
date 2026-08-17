/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the project root the whole session is read against.
 * <p>
 * Every path the bridge reports is written relative to it, and a proof a run replaces is
 * moved to a trash below it, so a root that is not absolute fails a session in places far
 * from where it was named.
 */
class BridgeSessionTest {

    @Test
    void aRelativeRootIsRecordedAbsolutely() {
        BridgeSession session = new BridgeSession();

        session.initialize(Path.of("."));

        assertThat(session.projectRoot()).isAbsolute();
        assertThat(session.projectRoot()).isEqualTo(Path.of("").toAbsolutePath().normalize());
    }

    @Test
    void anAbsoluteRootIsKeptAsItIs() {
        BridgeSession session = new BridgeSession();
        Path root = Path.of("").toAbsolutePath();

        session.initialize(root);

        assertThat(session.projectRoot()).isEqualTo(root);
    }

    @Test
    void aSessionThatWasNotInitialisedSaysSo() {
        assertThatThrownBy(() -> new BridgeSession().projectRoot())
                .hasMessageContaining("initialize");
    }
}
