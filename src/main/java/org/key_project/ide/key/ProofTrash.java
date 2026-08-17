/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Holds the proofs that were replaced by a later run.
 * <p>
 * Every run saves its proof, and the previous proof may be the better one: it may be closed
 * where the new one is not, or contain interactive steps nobody wants to repeat. It is
 * therefore moved here instead of being overwritten, and can be restored by hand. The
 * contents mirror the layout of the proof directory and are grouped by the time of the run,
 * so they can be deleted as a whole or by age.
 */
public final class ProofTrash {

    /** The directory inside {@code proofs/} that holds what was replaced. */
    public static final String TRASH_DIRECTORY = ".trash";

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ProofTrash() {
    }

    /**
     * Moves an existing proof file into the trash.
     *
     * @param proofsRoot the {@code proofs/} directory of the project
     * @param proofFile the file about to be written
     * @return where the earlier proof went, or empty when there was none
     * @throws IOException if it exists but cannot be moved, in which case it must not be
     *         overwritten either
     */
    /**
     * What deleting removed.
     *
     * @param files how many files were deleted
     * @param bytes how many bytes they took
     */
    public record Pruned(int files, long bytes) {
    }

    /**
     * Empties the trash.
     *
     * @param proofsRoot the {@code proofs/} directory of the project
     * @return what was thrown away
     * @throws IOException if a file cannot be deleted
     */
    public static Pruned empty(Path proofsRoot) throws IOException {
        return prune(proofsRoot, batch -> true);
    }

    /**
     * Throws away the oldest batches until the trash is below a size.
     *
     * @param proofsRoot the {@code proofs/} directory of the project
     * @param maxBytes the size to get below
     * @return what was thrown away
     * @throws IOException if a file cannot be deleted
     */
    public static Pruned keepBelow(Path proofsRoot, long maxBytes) throws IOException {
        List<Path> batches = batches(proofsRoot);
        long total = 0;
        for (Path batch : batches) {
            total += sizeOf(batch);
        }
        // Batches are named by the moment they were made, so name order is age order and
        // the first ones are the oldest.
        int drop = 0;
        while (total > maxBytes && drop < batches.size()) {
            total -= sizeOf(batches.get(drop));
            drop++;
        }
        List<Path> doomed = batches.subList(0, drop);
        return prune(proofsRoot, doomed::contains);
    }

    /**
     * Throws away every batch older than a number of days.
     *
     * @param proofsRoot the {@code proofs/} directory of the project
     * @param days the age at which a batch is thrown away
     * @return what was thrown away
     * @throws IOException if a file cannot be deleted
     */
    public static Pruned dropOlderThan(Path proofsRoot, int days) throws IOException {
        LocalDateTime limit = LocalDateTime.now().minusDays(days);
        return prune(proofsRoot, batch -> madeAt(batch).map(at -> at.isBefore(limit)).orElse(false));
    }

    /** The batches in the trash, oldest first; each is one moment's worth of moved proofs. */
    private static List<Path> batches(Path proofsRoot) throws IOException {
        Path trash = proofsRoot.resolve(TRASH_DIRECTORY);
        if (!Files.isDirectory(trash)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(trash)) {
            return children.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(batch -> batch.getFileName().toString()))
                    .toList();
        }
    }

    /** When a batch was made, read from its name; empty for a directory not made here. */
    private static Optional<LocalDateTime> madeAt(Path batch) {
        try {
            return Optional.of(LocalDateTime.parse(batch.getFileName().toString(), STAMP));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static long sizeOf(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            long total = 0;
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                total += Files.size(file);
            }
            return total;
        }
    }

    /** Deletes the batches a rule picks, and says what went. */
    private static Pruned prune(Path proofsRoot, Predicate<Path> doomed) throws IOException {
        int files = 0;
        long bytes = 0;
        for (Path batch : batches(proofsRoot)) {
            if (!doomed.test(batch)) {
                continue;
            }
            try (Stream<Path> contents = Files.walk(batch)) {
                for (Path path : contents.sorted(Comparator.reverseOrder()).toList()) {
                    if (Files.isRegularFile(path)) {
                        bytes += Files.size(path);
                        files++;
                    }
                    Files.delete(path);
                }
            }
        }
        return new Pruned(files, bytes);
    }

    public static Optional<Path> moveToTrash(Path proofsRoot, Path proofFile) throws IOException {
        if (!Files.isRegularFile(proofFile)) {
            return Optional.empty();
        }
        Path relative = proofsRoot.toAbsolutePath().normalize()
                .relativize(proofFile.toAbsolutePath().normalize());
        Path target = proofsRoot.resolve(TRASH_DIRECTORY)
                .resolve(LocalDateTime.now().format(STAMP)).resolve(relative);
        Files.createDirectories(target.getParent());
        Files.move(proofFile, target, StandardCopyOption.REPLACE_EXISTING);
        return Optional.of(target);
    }
}
