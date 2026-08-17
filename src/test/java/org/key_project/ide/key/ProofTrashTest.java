/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that a proof is never lost to a later run.
 */
class ProofTrashTest {

    @TempDir
    Path workDir;

    @Test
    void keepsTheProofThatWasThere() throws IOException {
        Path proofs = workDir.resolve("proofs");
        Path proof = proofs.resolve("core/com/example/Account.proof");
        Files.createDirectories(proof.getParent());
        Files.writeString(proof, "the earlier proof");

        Optional<Path> discarded = ProofTrash.moveToTrash(proofs, proof);

        assertThat(discarded).isPresent();
        assertThat(Files.readString(discarded.orElseThrow())).isEqualTo("the earlier proof");
        // Moved rather than copied, so the place the new proof goes is free.
        assertThat(Files.exists(proof)).isFalse();
    }

    @Test
    void keepsTheLayoutItCameFrom() throws IOException {
        Path proofs = workDir.resolve("proofs");
        Path proof = proofs.resolve("core/com/example/Account.proof");
        Files.createDirectories(proof.getParent());
        Files.writeString(proof, "the earlier proof");

        Path discarded = ProofTrash.moveToTrash(proofs, proof).orElseThrow();

        Path relative = proofs.resolve(ProofTrash.TRASH_DIRECTORY).relativize(discarded);
        // A timestamp, then the path it had, so the whole of it can be wiped by age.
        assertThat(relative.subpath(1, relative.getNameCount()).toString())
                .isEqualTo("core/com/example/Account.proof");
    }

    /**
     * A batch as the trash makes them, named by the moment it was made.
     *
     * @param stamp the moment, in the trash's own form
     * @param bytes how large its one file is
     */
    private Path batch(String stamp, int bytes) throws IOException {
        Path file = workDir.resolve("proofs/.trash/" + stamp + "/core/A.proof");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[bytes]);
        return file.getParent().getParent();
    }

    @Test
    void emptyingThrowsEveryBatchAway() throws IOException {
        batch("20260101-120000", 100);
        batch("20260102-120000", 100);

        ProofTrash.Pruned pruned = ProofTrash.empty(workDir.resolve("proofs"));

        assertThat(pruned).isEqualTo(new ProofTrash.Pruned(2, 200));
        assertThat(Files.list(workDir.resolve("proofs/.trash"))).isEmpty();
    }

    @Test
    void keepingBelowASizeThrowsTheOldestAwayFirstAndStopsOnceBelow() throws IOException {
        Path oldest = batch("20260101-120000", 100);
        Path middle = batch("20260102-120000", 100);
        Path newest = batch("20260103-120000", 100);

        ProofTrash.Pruned pruned = ProofTrash.keepBelow(workDir.resolve("proofs"), 250);

        assertThat(pruned).isEqualTo(new ProofTrash.Pruned(1, 100));
        assertThat(Files.exists(oldest)).isFalse();
        assertThat(Files.exists(middle)).isTrue();
        assertThat(Files.exists(newest)).isTrue();
    }

    @Test
    void aTrashAlreadyBelowTheSizeIsLeftAlone() throws IOException {
        Path only = batch("20260101-120000", 100);

        assertThat(ProofTrash.keepBelow(workDir.resolve("proofs"), 1000))
                .isEqualTo(new ProofTrash.Pruned(0, 0));
        assertThat(Files.exists(only)).isTrue();
    }

    @Test
    void droppingByAgeKeepsWhatIsYoungEnough() throws IOException {
        Path old = batch("20200101-120000", 100);
        Path recent = batch(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")), 100);

        ProofTrash.Pruned pruned = ProofTrash.dropOlderThan(workDir.resolve("proofs"), 30);

        assertThat(pruned).isEqualTo(new ProofTrash.Pruned(1, 100));
        assertThat(Files.exists(old)).isFalse();
        assertThat(Files.exists(recent)).isTrue();
    }

    @Test
    void pruningWithoutATrashIsNothing() throws IOException {
        assertThat(ProofTrash.empty(workDir.resolve("proofs")))
                .isEqualTo(new ProofTrash.Pruned(0, 0));
    }

    @Test
    void doesNothingWhenThereIsNoEarlierProof() throws IOException {
        Path proofs = workDir.resolve("proofs");
        Files.createDirectories(proofs);

        Optional<Path> discarded =
            ProofTrash.moveToTrash(proofs, proofs.resolve("core/com/example/Absent.proof"));

        assertThat(discarded).isEmpty();
    }
}
