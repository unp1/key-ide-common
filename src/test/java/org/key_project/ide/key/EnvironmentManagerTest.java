/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.uka.ilkd.key.control.KeYEnvironment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.key_project.ide.config.VerificationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that an edited project is verified as it is now.
 * <p>
 * Loading is what makes these tests slow, and it is also what they are about, so the class
 * keeps to one reload cycle.
 */
class EnvironmentManagerTest {

    @TempDir
    Path workDir;

    private Path sources;
    private VerificationContext context;
    private EnvironmentManager manager;

    @BeforeEach
    void createProject() throws IOException {
        sources = workDir.resolve("src");
        Files.createDirectories(sources.resolve("com/example"));
        Files.writeString(sources.resolve("com/example/Counter.java"), """
                package com.example;

                public final class Counter {

                    private /*@ spec_public @*/ int value;

                    /*@ public normal_behavior
                      @   ensures value == \\old(value);
                      @   assignable \\nothing;
                      @*/
                    public /*@ pure @*/ int get() {
                        return value;
                    }
                }
                """);
        context = new VerificationContext("counter", sources, List.of(), null, List.of());
        manager = new EnvironmentManager();
    }

    @AfterEach
    void dispose() {
        manager.close();
    }

    @Test
    void keepsALoadedContextWhileItsSourcesAreUnchanged() throws Exception {
        KeYEnvironment<?> first = manager.environmentFor(context);
        KeYEnvironment<?> second = manager.environmentFor(context);

        assertThat(second).isSameAs(first);
    }

    @Test
    void verifiesAMethodAddedSinceTheContextWasLoaded() throws Exception {
        KeYEnvironment<?> before = manager.environmentFor(context);
        assertThat(new MethodResolver(before.getJavaInfo())
                .find("com.example.Counter", "get", List.of())).isPresent();

        Files.writeString(sources.resolve("com/example/Counter.java"), """
                package com.example;

                public final class Counter {

                    private /*@ spec_public @*/ int value;

                    /*@ public normal_behavior
                      @   ensures value == \\old(value);
                      @   assignable \\nothing;
                      @*/
                    public /*@ pure @*/ int get() {
                        return value;
                    }

                    /*@ public normal_behavior
                      @   requires value < Integer.MAX_VALUE;
                      @   ensures value == \\old(value) + 1;
                      @   assignable value;
                      @*/
                    public void increment() {
                        value++;
                    }
                }
                """);

        KeYEnvironment<?> after = manager.environmentFor(context);

        assertThat(after).isNotSameAs(before);
        assertThat(new MethodResolver(after.getJavaInfo())
                .find("com.example.Counter", "increment", List.of()))
                        .hasValueSatisfying(method -> assertThat(method.name())
                                .isEqualTo("increment"));
    }
}
