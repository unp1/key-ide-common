/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.key_project.ide.config.VerificationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the summary notices the changes that make a loaded environment wrong.
 */
class SourceSnapshotTest {

    @TempDir
    Path workDir;

    private Path sources;
    private VerificationContext context;

    @BeforeEach
    void createSources() throws IOException {
        sources = workDir.resolve("src");
        Files.createDirectories(sources.resolve("com/example"));
        Files.writeString(sources.resolve("com/example/One.java"),
            "package com.example;\npublic class One { }\n");
        context = new VerificationContext("test", sources, List.of(), null, List.of());
    }

    @Test
    void matchesItselfWhileNothingChanges() {
        SourceSnapshot before = SourceSnapshot.of(context);

        assertThat(SourceSnapshot.of(context)).isEqualTo(before);
    }

    @Test
    void differsAfterAFileIsEdited() throws IOException {
        SourceSnapshot before = SourceSnapshot.of(context);

        Files.writeString(sources.resolve("com/example/One.java"),
            "package com.example;\npublic class One { public int f() { return 1; } }\n");

        assertThat(SourceSnapshot.of(context)).isNotEqualTo(before);
    }

    @Test
    void differsAfterAFileIsAdded() throws IOException {
        SourceSnapshot before = SourceSnapshot.of(context);

        Files.writeString(sources.resolve("com/example/Two.java"),
            "package com.example;\npublic class Two { }\n");

        assertThat(SourceSnapshot.of(context)).isNotEqualTo(before);
    }

    @Test
    void differsAfterAFileIsDeleted() throws IOException {
        Files.writeString(sources.resolve("com/example/Two.java"),
            "package com.example;\npublic class Two { }\n");
        SourceSnapshot before = SourceSnapshot.of(context);

        Files.delete(sources.resolve("com/example/Two.java"));

        assertThat(SourceSnapshot.of(context)).isNotEqualTo(before);
    }

    @Test
    void differsWhenAClasspathEntryChanges() throws IOException {
        Path library = workDir.resolve("library.jar");
        Files.writeString(library, "first");
        VerificationContext withLibrary =
            new VerificationContext("test", sources, List.of(library), null, List.of());
        SourceSnapshot before = SourceSnapshot.of(withLibrary);

        Files.writeString(library, "second, and longer");

        assertThat(SourceSnapshot.of(withLibrary)).isNotEqualTo(before);
    }

    @Test
    void neverMatchesWhenTheSourcesCannotBeRead() {
        VerificationContext missing = new VerificationContext("test",
            workDir.resolve("absent"), List.of(), null, List.of());

        SourceSnapshot snapshot = SourceSnapshot.of(missing);

        assertThat(snapshot.complete()).isFalse();
        // An unreadable tree says nothing about whether it changed, so it is loaded again
        // and reports its own error rather than being served from a kept environment.
        assertThat(snapshot).isNotEqualTo(SourceSnapshot.of(missing));
    }
}
