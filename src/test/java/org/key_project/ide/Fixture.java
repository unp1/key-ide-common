/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The Java project the tests load.
 *
 * A test that writes anything, and proving writes proofs, works on a copy of it in a
 * temporary directory. The original is under source control and is also read while the
 * plugin's sandbox is open, so a test that wrote into it would fail whenever someone was
 * using the sandbox.
 */
public final class Fixture {

    private static final Path ROOT = Path.of("src/test/fixture").toAbsolutePath().normalize();

    private Fixture() {
    }

    /** The fixture where it lives, for a test that only reads. */
    public static Path root() {
        return ROOT;
    }

    /**
     * Copies parts of the fixture into a temporary directory.
     *
     * @param parts what to copy, as paths inside the fixture, for example {@code core}
     * @return the temporary project root, to be removed with {@link #remove(Path)}
     * @throws IOException if the copy fails
     */
    public static Path copyOf(String... parts) throws IOException {
        Path projectRoot = Files.createTempDirectory("key-ide-test");
        copyInto(projectRoot, parts);
        return projectRoot;
    }

    /**
     * Copies parts of the fixture into a directory that already exists.
     *
     * @param projectRoot where to copy them
     * @param parts what to copy, as paths inside the fixture
     * @throws IOException if the copy fails
     */
    public static void copyInto(Path projectRoot, String... parts) throws IOException {
        for (String part : parts) {
            copy(ROOT.resolve(part), projectRoot.resolve(part));
        }
    }

    /**
     * Removes a copy.
     *
     * @param projectRoot what {@link #copyOf} returned, or null
     * @throws IOException if walking it fails
     */
    public static void remove(Path projectRoot) throws IOException {
        if (projectRoot == null || !Files.exists(projectRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : paths.toList()) {
                Path target = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }
}
