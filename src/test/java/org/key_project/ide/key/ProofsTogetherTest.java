/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.proof.init.JavaProfile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.key_project.ide.Fixture;
import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.config.VerificationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers reading saved proofs back into one environment.
 * <p>
 * A proof that used another method's contract is closed only but for lemmas until KeY
 * holds a closed proof of that contract too. Read one at a time, KeY never does; read
 * together, it does, and the verdict is KeY's.
 */
class ProofsTogetherTest {

    private static final Path FIXTURE = Fixture.root();

    private Path projectRoot;
    private KeYEnvironment<?> environment;
    private VerificationContext context;

    @BeforeAll
    static void initialiseLogging() {
        org.slf4j.LoggerFactory.getLogger(ProofsTogetherTest.class);
    }

    @BeforeEach
    void loadFixture() throws Exception {
        projectRoot = Files.createTempDirectory("key-ide-together");
        Fixture.copyInto(projectRoot, "core");
        context = new VerificationContext("core", projectRoot.resolve("core/src/main/java"),
            List.of(), null, List.of());
        environment = KeYEnvironment.load(new JavaProfile(), context.javaSource(), null, null,
            null, true);
    }

    @AfterEach
    void dispose() throws IOException {
        if (environment != null) {
            environment.dispose();
        }
        Fixture.remove(projectRoot);
    }

    private ProofObligations obligations() {
        return new ProofObligations(environment, context, ProofFiles.under(projectRoot));
    }

    private ProofObligations.Obligation obligation(String method) {
        return obligations().list().stream()
                .filter(o -> o.contract().getName().contains("::" + method + "("))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The fixture has no contract for " + method));
    }

    /** Proves both and saves them, then lets go, as a run does. */
    private void proveAndSave(String... methods) {
        ProofRunner runner = new ProofRunner(environment, context,
            ProofFiles.under(projectRoot), Map.of());
        try {
            for (String method : methods) {
                runner.prove(obligation(method), ProofOptions.NONE);
            }
        } finally {
            runner.release();
        }
    }

    @Test
    void proofsReadTogetherAreJudgedTogether() {
        proveAndSave("contains", "indexOf");

        List<ProofsTogether.Read> read = new ProofsTogether(environment, context)
                .readAll(List.of(obligation("contains"), obligation("indexOf")));

        try {
            assertThat(read).hasSize(2);
            assertThat(read).allSatisfy(one -> assertThat(one.proof())
                    .as("%s: %s", one.obligation().contract().getName(), one.message())
                    .isNotNull());
            // KeY has both in front of it, so the one that used the other is closed.
            assertThat(obligation("contains").status())
                    .isEqualTo(ProofObligations.Status.CLOSED);
        } finally {
            read.forEach(one -> {
                if (one.proof() != null) {
                    one.proof().dispose();
                }
            });
        }
    }

    @Test
    void aProofReadAloneRestsOnWhatKeyCannotSee() {
        proveAndSave("contains", "indexOf");

        List<ProofsTogether.Read> read = new ProofsTogether(environment, context)
                .readAll(List.of(obligation("contains")));

        try {
            assertThat(read).singleElement().satisfies(one -> {
                assertThat(one.proof()).isNotNull();
                assertThat(obligation("contains").status())
                        .isEqualTo(ProofObligations.Status.CLOSED_BUT_LEMMAS_LEFT);
            });
        } finally {
            read.forEach(one -> {
                if (one.proof() != null) {
                    one.proof().dispose();
                }
            });
        }
    }
}
