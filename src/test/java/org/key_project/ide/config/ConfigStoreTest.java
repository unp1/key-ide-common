/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers what a project's settings file says, and what it means when it says nothing. */
class ConfigStoreTest {

    @TempDir
    private Path projectRoot;

    @Test
    void readsBackTheProofDirectoryItWrote() throws Exception {
        ConfigStore store = new ConfigStore(projectRoot);
        VerificationContext core =
            new VerificationContext("core", Path.of("src"), List.of(), null, List.of());
        store.write(new ProjectConfig(ProjectConfig.CURRENT_VERSION, List.of(core),
            "verification"));

        assertThat(store.read().proofDirectory()).isEqualTo("verification");
    }

    @Test
    void aFileNamingNoProofDirectoryUsesTheDefaultOne() throws Exception {
        Files.createDirectories(projectRoot.resolve(".key"));
        Files.writeString(projectRoot.resolve(ConfigStore.RELATIVE_PATH), """
            {
              "version": 1,
              "contexts": [
                { "id": "core", "javaSource": "src", "classpath": [], "includes": [] }
              ]
            }
            """);

        ProjectConfig config = new ConfigStore(projectRoot).read();

        assertThat(config.proofDirectory()).isEqualTo(ProjectConfig.DEFAULT_PROOF_DIRECTORY);
        assertThat(config.contexts()).singleElement()
                .satisfies(context -> assertThat(context.id()).isEqualTo("core"));
    }

    @Test
    void aProjectWithNoFileUsesTheDefaultProofDirectory() throws Exception {
        assertThat(new ConfigStore(projectRoot).read().proofDirectory())
                .isEqualTo(ProjectConfig.DEFAULT_PROOF_DIRECTORY);
    }
}
