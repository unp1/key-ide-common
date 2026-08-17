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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.key_project.ide.Fixture;
import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.config.VerificationContext;
import org.key_project.ide.key.ProofObligations.Obligation;
import org.key_project.ide.key.ProofObligations.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves an obligation of the test project without a window, which is how a proof is
 * ordinarily attempted.
 * <p>
 * The project is copied first, so a run writes its proofs beside the copy and leaves the
 * fixture as it was.
 */
class ProofRunnerTest {

    private static final Path FIXTURE = Fixture.root();

    private static Path projectRoot;
    private static KeYEnvironment<?> environment;
    private static VerificationContext context;

    @BeforeAll
    static void copyAndLoadFixture() throws Exception {
        projectRoot = Files.createTempDirectory("key-ide-runner");
        Fixture.copyInto(projectRoot, "core");
        context = new VerificationContext("core", projectRoot.resolve("core/src/main/java"),
            List.of(), null, List.of());
        environment = KeYEnvironment.load(context.javaSource(), null, null, null);
    }

    @AfterAll
    static void dispose() throws IOException {
        if (environment != null) {
            environment.dispose();
        }
        if (projectRoot != null) {
            Fixture.remove(projectRoot);
        }
    }

    @Test
    void provesAnObligationAndSavesTheProof() {
        Obligation obligation = obligation("getBalance");
        ProofRunner runner =
            new ProofRunner(environment, context, ProofFiles.under(projectRoot), Map.of());

        ProofRunner.Attempt attempt = runner.prove(obligation, ProofOptions.NONE);
        ProofRunner.Outcome outcome = attempt.outcome();
        runner.release();

        assertThat(outcome.status()).isEqualTo(Status.CLOSED);
        assertThat(outcome.nodes()).isGreaterThan(0);
        assertThat(outcome.message()).isEmpty();
        assertThat(outcome.proofFile()).exists();
    }

    @Test
    void holdsTheProofUntilItIsReleasedSoThatKeyCanBeAskedAboutIt() {
        ProofRunner runner =
            new ProofRunner(environment, context, ProofFiles.under(projectRoot), Map.of());

        ProofRunner.Attempt attempt = runner.prove(obligation("max"), ProofOptions.NONE);

        // KeY still holds it, and so still answers about it.
        assertThat(attempt.proof().isDisposed()).isFalse();
        assertThat(obligation("max").status()).isEqualTo(Status.CLOSED);

        runner.release();

        // Let go of, so the listing knows only that a file is there.
        assertThat(attempt.proof().isDisposed()).isTrue();
        assertThat(obligation("max").status()).isEqualTo(Status.SAVED);
    }

    @Test
    void keepsAnEarlierProofWhenRunAgain() throws IOException {
        Obligation obligation = obligation("contains");
        ProofRunner runner = new ProofRunner(environment, context, ProofFiles.under(projectRoot), Map.of());

        runner.prove(obligation, ProofOptions.NONE);
        runner.prove(obligation, ProofOptions.NONE);

        Path archive = ProofFiles.under(projectRoot).root()
                .resolve(ProofTrash.TRASH_DIRECTORY);
        try (Stream<Path> archived = Files.walk(archive)) {
            assertThat(archived.filter(Files::isRegularFile)).isNotEmpty();
        }
    }

    private static Obligation obligation(String named) {
        return new ProofObligations(environment, context, ProofFiles.under(projectRoot)).list().stream()
                .filter(o -> o.contract().getName().contains(named)).findFirst().orElseThrow();
    }

}
