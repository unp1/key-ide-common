/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.key.NoProofBrowser;
import org.key_project.ide.protocol.Dtos.ListObligationsParams;
import org.key_project.ide.protocol.Dtos.LoadFailure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers what a user is told when KeY cannot read a source.
 * <p>
 * A JML mistake is the ordinary case: someone edits a contract, saves, and KeY refuses the
 * file. What comes back has to name the file and the line, so the mistake can be found, and
 * has to be a sentence, so a client that shows only messages still says something useful.
 */
class LoadFailureTest {

    private Path project;
    private EnvironmentManager environments;
    private VerificationServiceImpl service;

    @BeforeEach
    void createBrokenProject() throws IOException {
        project = Files.createTempDirectory("key-ide-broken");
        Path source = project.resolve("src/com/example");
        Files.createDirectories(source);
        // The `ensures` clause is not closed: KeY's JML parser refuses it.
        Files.writeString(source.resolve("Broken.java"), """
            package com.example;

            public class Broken {
                /*@ public normal_behavior
                  @   ensures \\result == (a + b ;
                  @*/
                public static int add(int a, int b) {
                    return a + b;
                }
            }
            """);
        Files.createDirectories(project.resolve(".key"));
        Files.writeString(project.resolve(".key/settings.json"), """
            {
              "version": 1,
              "contexts": [
                { "id": "broken", "javaSource": "src", "classpath": [], "includes": [] }
              ]
            }
            """);

        BridgeSession session = new BridgeSession();
        session.initialize(project);
        environments = new EnvironmentManager();
        service = new VerificationServiceImpl(session, environments, new NoProofBrowser());
    }

    @AfterEach
    void cleanUp() throws IOException {
        environments.close();
        try (var files = Files.walk(project)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void namesTheFileAndTheLineKeyRefused() {
        assertThatThrownBy(() -> await(service.list(new ListObligationsParams("broken"))))
                .isInstanceOf(ResponseErrorException.class)
                .satisfies(error -> {
                    ResponseErrorException failure = (ResponseErrorException) error;
                    assertThat(failure.getResponseError().getCode())
                            .isEqualTo(BridgeErrors.ENVIRONMENT_LOAD_FAILED);

                    LoadFailure detail = detailOf(failure);
                    assertThat(detail.contextId()).isEqualTo("broken");
                    assertThat(detail.problems()).isNotEmpty().anySatisfy(problem -> {
                        assertThat(problem.uri()).endsWith("Broken.java");
                        assertThat(problem.line()).isEqualTo(5);
                        assertThat(problem.message()).isNotBlank();
                    });
                });
    }

    @Test
    void saysWhichContextAndWhereInTheMessageAlone() {
        assertThatThrownBy(() -> await(service.list(new ListObligationsParams("broken"))))
                .isInstanceOf(ResponseErrorException.class)
                .satisfies(error -> {
                    String message = ((ResponseErrorException) error).getResponseError()
                            .getMessage();
                    // A client that shows only messages still learns which context, which
                    // file and which line, and what KeY said, without a stack trace.
                    assertThat(message)
                            .contains("'broken'")
                            .contains("Broken.java:5")
                            .doesNotContain("Exception")
                            .doesNotContain("\tat ");
                });
    }

    /** The error's data, as a client would read it out of the JSON. */
    private static LoadFailure detailOf(ResponseErrorException failure) {
        Object data = failure.getResponseError().getData();
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), LoadFailure.class);
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        }
    }
}
