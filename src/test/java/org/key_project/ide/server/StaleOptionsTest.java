/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.key_project.ide.Fixture;
import org.key_project.ide.config.ConfigStore;
import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.key.NoProofBrowser;
import org.key_project.ide.protocol.Dtos.ListObligationsParams;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers finding and dropping the settings of obligations that no longer exist.
 * <p>
 * Settings are kept by contract name. A method renamed or removed leaves its settings
 * behind under a name nothing answers to, and only the user may decide to drop them.
 */
class StaleOptionsTest {

    private static final Path FIXTURE = Fixture.root();

    /** A contract the fixture declares. */
    private static final String MAX =
        "com.example.core.ArrayUtils[com.example.core.ArrayUtils::max(int,int)]"
            + ".JML normal_behavior operation contract.0";

    /** A contract of a method that was renamed away. */
    private static final String GONE =
        "com.example.core.ArrayUtils[com.example.core.ArrayUtils::oldName(int,int)]"
            + ".JML normal_behavior operation contract.0";

    private Path projectRoot;
    private EnvironmentManager environments;
    private VerificationServiceImpl service;

    @BeforeAll
    static void initialiseLogging() {
        org.slf4j.LoggerFactory.getLogger(StaleOptionsTest.class);
    }

    @BeforeEach
    void copyFixtureAndCreateService() throws IOException {
        projectRoot = Files.createTempDirectory("key-ide-stale");
        Fixture.copyInto(projectRoot, "core");
        Files.createDirectories(projectRoot.resolve(".key"));
        Files.writeString(projectRoot.resolve(".key/settings.json"), """
            {
              "version": 1,
              "contexts": [
                { "id": "core", "javaSource": "core/src/main/java", "classpath": [], "includes": [] }
              ],
              "obligationOptions": {
                "core": {
                  "%s": { "taclet": {}, "strategy": { "LOOP_OPTIONS_KEY": "LOOP_NONE" }, "maxSteps": 0 },
                  "%s": { "taclet": {}, "strategy": { "LOOP_OPTIONS_KEY": "LOOP_NONE" }, "maxSteps": 0 }
                }
              }
            }
            """.formatted(MAX, GONE));
        BridgeSession session = new BridgeSession();
        session.initialize(projectRoot);
        environments = new EnvironmentManager();
        service = new VerificationServiceImpl(session, environments, new NoProofBrowser());
    }

    @AfterEach
    void dispose() throws IOException {
        environments.close();
        Fixture.remove(projectRoot);
    }

    @Test
    void theSettingsOfAMissingObligationAreFoundAndNoOthers() throws Exception {
        assertThat(service.staleOptions(new ListObligationsParams("core")).get()
                .contractNames()).containsExactly(GONE);
    }

    @Test
    void removingDropsOnlyTheSettingsOfMissingObligations() throws Exception {
        assertThat(service.removeStaleOptions(new ListObligationsParams("core")).get()
                .contractNames()).containsExactly(GONE);

        var stated = new ConfigStore(projectRoot).read().obligationOptions().get("core");
        assertThat(stated).containsOnlyKeys(MAX);
        assertThat(service.staleOptions(new ListObligationsParams("core")).get()
                .contractNames()).isEmpty();
    }
}
