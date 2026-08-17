/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the validator against the test project, so the rules it encodes stay tied to
 * what KeY actually accepts.
 */
class ConfigValidatorTest {

    private static final Path PROJECT_ROOT =
        Path.of("src/test/fixture").toAbsolutePath().normalize();

    private final ConfigValidator validator = new ConfigValidator();

    @Test
    void acceptsASourceDirectoryWithoutClasspath() {
        VerificationContext core = context("core", "core/src/main/java", List.of());

        List<ConfigProblem> problems = validator.validate(core.resolveAgainst(PROJECT_ROOT));

        assertThat(problems).isEmpty();
    }

    @Test
    void acceptsAZipClasspathEntryHoldingSources() {
        VerificationContext web =
            context("web", "web/src/main/java", List.of("libs/pricing-sources.jar"));

        List<ConfigProblem> problems = validator.validate(web.resolveAgainst(PROJECT_ROOT));

        assertThat(problems).isEmpty();
    }

    @Test
    void acceptsADirectoryClasspathEntryHoldingSources() {
        VerificationContext web =
            context("web-srcdir", "web/src/main/java", List.of("libs/pricing-src"));

        List<ConfigProblem> problems = validator.validate(web.resolveAgainst(PROJECT_ROOT));

        assertThat(problems).isEmpty();
    }

    @Test
    void rejectsAClasspathEntryHoldingCompiledClassesOnly(@TempDir Path temp) throws IOException {
        Files.createDirectories(temp.resolve("com/example"));
        Files.writeString(temp.resolve("com/example/Compiled.class"), "not really bytecode");
        VerificationContext context =
            new VerificationContext("compiled", PROJECT_ROOT.resolve("core/src/main/java"),
                List.of(temp), null, List.of());

        List<ConfigProblem> problems = validator.validate(context);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.isError()).isTrue();
            assertThat(problem.field()).isEqualTo("classpath[0]");
            assertThat(problem.message()).contains("no .java or .jml files");
        });
    }

    @Test
    void rejectsABootclasspathThatIsAFile() {
        VerificationContext context =
            new VerificationContext("boot", PROJECT_ROOT.resolve("core/src/main/java"), List.of(),
                PROJECT_ROOT.resolve("libs/pricing-sources.jar"), List.of());

        List<ConfigProblem> problems = validator.validate(context);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.field()).isEqualTo("bootclasspath");
            assertThat(problem.message()).contains("only a directory");
        });
    }

    @Test
    void rejectsAMissingSourceDirectory() {
        VerificationContext context = context("gone", "does/not/exist", List.of());

        List<ConfigProblem> problems = validator.validate(context.resolveAgainst(PROJECT_ROOT));

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.field()).isEqualTo("javaSource");
            assertThat(problem.message()).contains("does not exist");
        });
    }

    @Test
    void reportsAnIdUsedByTwoContexts() {
        ProjectConfig config = new ProjectConfig(ProjectConfig.CURRENT_VERSION,
            List.of(context("core", "core/src/main/java", List.of()),
                context("core", "web/src/main/java", List.of("libs/pricing-src"))));

        List<ConfigProblem> problems = validator.validate(config, PROJECT_ROOT);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.field()).isEqualTo("id");
            assertThat(problem.message()).contains("more than one context");
        });
    }

    private static VerificationContext context(String id, String javaSource,
            List<String> classpath) {
        return new VerificationContext(id, Path.of(javaSource),
            classpath.stream().map(Path::of).toList(), null, List.of());
    }
}
