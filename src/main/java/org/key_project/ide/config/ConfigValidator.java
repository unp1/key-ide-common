/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks a configuration against the rules KeY imposes on its load parameters.
 * <p>
 * KeY enforces these rules while loading, where a violation appears as a parse failure or
 * an unresolved symbol far from its cause. Checking them here lets the settings form name
 * the offending field.
 */
public final class ConfigValidator {

    /** Extensions KeY reads from a classpath entry. */
    private static final List<String> SOURCE_EXTENSIONS = List.of(".java", ".jml");

    /**
     * Checks every context of a project.
     *
     * @param config the configuration as read from the file
     * @param projectRoot the directory relative paths are written against
     * @return the problems found, ordered by context and then by field
     */
    public List<ConfigProblem> validate(ProjectConfig config, Path projectRoot) {
        List<ConfigProblem> problems = new ArrayList<>(duplicateIds(config));
        for (VerificationContext context : config.contexts()) {
            problems.addAll(validate(context.resolveAgainst(projectRoot)));
        }
        return problems;
    }

    /**
     * Checks one context whose paths are already absolute.
     *
     * @param context a context as returned by {@link VerificationContext#resolveAgainst(Path)}
     * @return the problems found, ordered by field
     */
    public List<ConfigProblem> validate(VerificationContext context) {
        List<ConfigProblem> problems = new ArrayList<>();
        String id = context.id();

        if (id.isBlank()) {
            problems.add(ConfigProblem.error(id, "id", "A context needs a non-empty id."));
        }

        validateJavaSource(context, problems);
        for (int i = 0; i < context.classpath().size(); i++) {
            validateClasspathEntry(id, context.classpath().get(i), "classpath[" + i + "]",
                problems);
        }
        validateBootclasspath(context, problems);
        for (int i = 0; i < context.includes().size(); i++) {
            validateInclude(id, context.includes().get(i), "includes[" + i + "]", problems);
        }
        return problems;
    }

    private List<ConfigProblem> duplicateIds(ProjectConfig config) {
        List<ConfigProblem> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (VerificationContext context : config.contexts()) {
            if (!seen.add(context.id())) {
                problems.add(ConfigProblem.error(context.id(), "id",
                    "The id '" + context.id() + "' is used by more than one context."));
            }
        }
        return problems;
    }

    private void validateJavaSource(VerificationContext context, List<ConfigProblem> problems) {
        Path javaSource = context.javaSource();
        if (!Files.exists(javaSource)) {
            problems.add(ConfigProblem.error(context.id(), "javaSource",
                "The source directory " + javaSource + " does not exist."));
        } else if (!Files.isDirectory(javaSource)) {
            problems.add(ConfigProblem.error(context.id(), "javaSource",
                "The source " + javaSource + " is a file. KeY loads a directory of sources."));
        }
    }

    private void validateClasspathEntry(String contextId, Path entry, String field,
            List<ConfigProblem> problems) {
        if (!Files.exists(entry)) {
            problems.add(ConfigProblem.error(contextId, field,
                "The classpath entry " + entry + " does not exist."));
            return;
        }
        try {
            if (!containsJavaSources(entry)) {
                problems.add(ConfigProblem.error(contextId, field, "The classpath entry " + entry
                    + " holds no .java or .jml files. KeY reads sources from a classpath entry,"
                    + " not compiled classes."));
            }
        } catch (IOException e) {
            problems.add(ConfigProblem.error(contextId, field,
                "The classpath entry " + entry + " cannot be read: " + e.getMessage()));
        }
    }

    private void validateBootclasspath(VerificationContext context, List<ConfigProblem> problems) {
        Path bootclasspath = context.bootclasspath();
        if (bootclasspath == null) {
            return;
        }
        if (!Files.exists(bootclasspath)) {
            problems.add(ConfigProblem.error(context.id(), "bootclasspath",
                "The boot classpath " + bootclasspath + " does not exist."));
        } else if (!Files.isDirectory(bootclasspath)) {
            problems.add(ConfigProblem.error(context.id(), "bootclasspath", "The boot classpath "
                + bootclasspath + " is a file. KeY accepts only a directory here."));
        }
    }

    private void validateInclude(String contextId, Path include, String field,
            List<ConfigProblem> problems) {
        if (!Files.exists(include)) {
            problems.add(ConfigProblem.error(contextId, field,
                "The include " + include + " does not exist."));
        } else if (Files.isDirectory(include)) {
            problems.add(ConfigProblem.error(contextId, field,
                "The include " + include + " is a directory. KeY includes single .key files."));
        }
    }

    /**
     * Reports whether an entry holds at least one file KeY would read from a classpath.
     *
     * @param entry an existing directory or zip file
     * @return true if a {@code .java} or {@code .jml} file is present
     * @throws IOException if the entry cannot be read
     */
    private boolean containsJavaSources(Path entry) throws IOException {
        if (Files.isDirectory(entry)) {
            try (Stream<Path> files = Files.walk(entry)) {
                return files.anyMatch(p -> Files.isRegularFile(p) && hasSourceExtension(p.toString()));
            }
        }
        try (ZipFile zip = new ZipFile(entry.toFile())) {
            return zip.stream().map(ZipEntry::getName).anyMatch(this::hasSourceExtension);
        }
    }

    private boolean hasSourceExtension(String name) {
        return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
