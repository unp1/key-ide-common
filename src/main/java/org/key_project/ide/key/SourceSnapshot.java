/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.key_project.ide.config.VerificationContext;

/**
 * A summary of the files a context is loaded from, used to detect changes to them.
 * <p>
 * A loaded environment holds the sources as they were read. Verifying against it after an
 * edit would report positions from the previous text and verify the previous code, which is
 * a wrong result rather than an outdated one, so a context whose files changed is loaded
 * again.
 *
 * @param fileCount the number of files, which detects an addition or a deletion
 * @param newestModification the latest modification time, which detects an edit
 * @param totalSize the total size, which detects an edit that kept the modification time
 * @param complete whether all files could be read; an incomplete summary never matches, so
 *        an unreadable tree is loaded again and reports its own error
 */
public record SourceSnapshot(long fileCount, long newestModification, long totalSize,
        boolean complete) {

    /** Extensions KeY reads from a source directory or a classpath entry. */
    private static final List<String> SOURCE_EXTENSIONS = List.of(".java", ".jml");

    /**
     * Summarises every file a context passes to KeY.
     *
     * @param context a context whose paths are absolute
     * @return the summary, marked incomplete if a file could not be read
     */
    public static SourceSnapshot of(VerificationContext context) {
        Accumulator accumulator = new Accumulator();
        accumulator.add(context.javaSource());
        context.classpath().forEach(accumulator::add);
        context.includes().forEach(accumulator::add);
        if (context.bootclasspath() != null) {
            accumulator.add(context.bootclasspath());
        }
        return accumulator.snapshot();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SourceSnapshot that)) {
            return false;
        }
        // An incomplete summary covers files that could not be read, so it cannot show
        // that they are unchanged and must never compare equal.
        return complete && that.complete && fileCount == that.fileCount
                && newestModification == that.newestModification && totalSize == that.totalSize;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(fileCount * 31 + newestModification * 17 + totalSize);
    }

    /** Collects the summary while walking. */
    private static final class Accumulator {

        private long fileCount;
        private long newestModification;
        private long totalSize;
        private boolean complete = true;

        /**
         * Adds a path, walking it if it is a directory.
         *
         * @param path a source directory, a classpath entry, or an include
         */
        void add(Path path) {
            try {
                if (Files.isDirectory(path)) {
                    try (Stream<Path> files = Files.walk(path)) {
                        files.filter(Files::isRegularFile).filter(Accumulator::isSource)
                                .forEach(this::addFile);
                    }
                } else if (Files.isRegularFile(path)) {
                    // A zip or an include is summarised by the file itself: its contents
                    // cannot change without the file changing.
                    addFile(path);
                } else {
                    complete = false;
                }
            } catch (IOException | RuntimeException e) {
                complete = false;
            }
        }

        private static boolean isSource(Path file) {
            String name = file.getFileName().toString();
            return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
        }

        private void addFile(Path file) {
            try {
                fileCount++;
                totalSize += Files.size(file);
                newestModification =
                    Math.max(newestModification, Files.getLastModifiedTime(file).toMillis());
            } catch (IOException e) {
                complete = false;
            }
        }

        SourceSnapshot snapshot() {
            return new SourceSnapshot(fileCount, newestModification, totalSize, complete);
        }
    }
}
