/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * One set of paths that KeY can load, corresponding to the arguments of
 * {@code KeYEnvironment.load(location, classPaths, bootClassPath, includes)}.
 * <p>
 * Paths are held as they appear in the configuration file and may be relative.
 * {@link #resolveAgainst(Path)} turns them into absolute paths.
 *
 * @param id identifies the context within a project, non-empty and unique
 * @param javaSource the directory holding the sources to verify
 * @param classpath entries holding Java sources KeY reads as library classes
 * @param bootclasspath directory replacing KeY's internal JavaRedux, or {@code null}
 * @param includes additional {@code .key} files to include
 */
public record VerificationContext(String id, Path javaSource, List<Path> classpath,
        Path bootclasspath, List<Path> includes, ProofOptions options) {

    /**
     * A context whose proofs are attempted with the project's settings.
     *
     * @param id identifies the context within the project
     * @param javaSource the directory holding the sources to verify
     * @param classpath entries holding Java sources KeY reads as library classes
     * @param bootclasspath a directory replacing KeY's internal JavaRedux, or null
     * @param includes additional {@code .key} files
     */
    public VerificationContext(String id, Path javaSource, List<Path> classpath,
            Path bootclasspath, List<Path> includes) {
        this(id, javaSource, classpath, bootclasspath, includes, ProofOptions.NONE);
    }

    public VerificationContext {
        options = options == null ? ProofOptions.NONE : options;
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(javaSource, "javaSource");
        classpath = List.copyOf(classpath);
        includes = List.copyOf(includes);
    }

    /**
     * A context with the same identity whose paths are all absolute.
     *
     * @param projectRoot the directory relative paths are written against
     * @return an equivalent context with absolute paths
     */
    public VerificationContext resolveAgainst(Path projectRoot) {
        return new VerificationContext(id,
            absolute(projectRoot, javaSource),
            classpath.stream().map(p -> absolute(projectRoot, p)).toList(),
            bootclasspath == null ? null : absolute(projectRoot, bootclasspath),
            includes.stream().map(p -> absolute(projectRoot, p)).toList(),
            options);
    }

    private static Path absolute(Path projectRoot, Path path) {
        return projectRoot.resolve(path).normalize();
    }
}
