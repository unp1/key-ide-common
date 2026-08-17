/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import de.uka.ilkd.key.java.ast.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.op.IObserverFunction;
import de.uka.ilkd.key.proof.init.InitConfig;

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.key_project.ide.key.EnvironmentManager;
import org.key_project.ide.key.ProofBrowser;
import org.key_project.ide.protocol.Dtos.BrowseParams;
import org.key_project.ide.protocol.Dtos.AvailableOptionsDto;
import org.key_project.ide.protocol.Dtos.AvailableOptionsParams;
import org.key_project.ide.protocol.Dtos.MethodDto;
import org.key_project.ide.protocol.Dtos.PositionParams;
import org.key_project.ide.protocol.Dtos.PositionResult;
import org.key_project.ide.protocol.Dtos.ResolveParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the path from a caret position to a proof obligation browser.
 * <p>
 * The browser is the only step needing a KeY window, so it is replaced by a recorder.
 * Everything before it, including loading the context and resolving the method, runs for
 * real against the test project.
 */
class VerificationServiceTest {

    private static final Path FIXTURE = Path.of("src/test/fixture").toAbsolutePath().normalize();
    private static final String ACCOUNT_URI =
        FIXTURE.resolve("core/src/main/java/com/example/core/Account.java").toUri().toString();

    private EnvironmentManager environments;
    private RecordingBrowser browser;
    private VerificationServiceImpl service;

    @BeforeEach
    void createService() {
        BridgeSession session = new BridgeSession();
        session.initialize(FIXTURE);
        environments = new EnvironmentManager();
        browser = new RecordingBrowser();
        service = new VerificationServiceImpl(session, environments, browser);
    }

    @AfterEach
    void disposeEnvironments() {
        environments.close();
    }

    @Test
    void resolvesAPositionWithoutOpeningAnything() throws Exception {
        MethodDto method = await(service.resolveAt(new ResolveParams("core", ACCOUNT_URI, 27, 9)));

        assertThat(method.className()).isEqualTo("com.example.core.Account");
        assertThat(method.name()).isEqualTo("deposit");
        assertThat(method.parameterTypes()).containsExactly("int");
        assertThat(browser.shown).isEmpty();
    }

    @Test
    void opensTheBrowserOnTheMethodUnderTheCaret() throws Exception {
        MethodDto method = await(service.verifyAt(new ResolveParams("core", ACCOUNT_URI, 27, 9)));

        assertThat(method.name()).isEqualTo("deposit");
        assertThat(browser.shown).singleElement().satisfies(shown -> {
            assertThat(shown.type().getFullName()).isEqualTo("com.example.core.Account");
            assertThat(shown.target().name().toString()).contains("deposit");
        });
    }

    @Test
    void opensTheBrowserOnAMethodNamedDirectly() throws Exception {
        await(service.browse(
            new BrowseParams("core", "com.example.core.Account", "withdraw", List.of("int"))));

        assertThat(browser.shown).singleElement().satisfies(shown -> assertThat(
            shown.target().name().toString()).contains("withdraw"));
    }

    @Test
    void answersWhatACaretInAMethodStandsFor() throws Exception {
        PositionResult at = await(service.at(new PositionParams(ACCOUNT_URI, 27, 9)));

        assertThat(at.contextId()).isEqualTo("core");
        assertThat(at.label()).isEqualTo("com.example.core.Account.deposit(int)");
        assertThat(at.contractNames()).isNotEmpty()
                .allSatisfy(name -> assertThat(name).contains("deposit(int)"));
    }

    @Test
    void answersWithTheWholeFileForACaretOutsideEveryMethod() throws Exception {
        PositionResult at = await(service.at(new PositionParams(ACCOUNT_URI, 1, 1)));

        assertThat(at.contextId()).isEqualTo("core");
        assertThat(at.label()).isEqualTo("Account.java");
        assertThat(at.contractNames())
                .hasSizeGreaterThan(await(service.at(new PositionParams(ACCOUNT_URI, 27, 9)))
                        .contractNames().size());
    }

    @Test
    void answersWithNothingForAFileNoContextCovers() throws Exception {
        String outside = FIXTURE.resolve("nowhere/Absent.java").toUri().toString();

        PositionResult at = await(service.at(new PositionParams(outside, 1, 1)));

        assertThat(at.contextId()).isNull();
        assertThat(at.contractNames()).isEmpty();
    }

    @Test
    void offersTheOptionsWithoutLoadingAnything() throws Exception {
        // The settings page asks for these before anything is proved, and a project may not
        // declare a context at all yet. Neither may make it wait for a context to load.
        AvailableOptionsDto offered = await(service.availableOptions(
            new AvailableOptionsParams(null)));

        assertThat(offered.taclet()).isNotEmpty();
        assertThat(offered.strategy()).isNotEmpty();
        assertThat(offered.defaults().taclet()).isNotEmpty();
    }

    @Test
    void offersTheOptionsForAContextItHasNotLoaded() throws Exception {
        AvailableOptionsDto offered = await(service.availableOptions(
            new AvailableOptionsParams("core")));

        assertThat(offered.taclet()).isNotEmpty();
        assertThat(environments.configOf("core")).isEmpty();
    }

    @Test
    void refusesAPositionThatCoversNoMethod() {
        assertThatThrownBy(
            () -> await(service.resolveAt(new ResolveParams("core", ACCOUNT_URI, 1, 1))))
                    .isInstanceOf(ResponseErrorException.class)
                    .satisfies(error -> assertThat(code(error))
                            .isEqualTo(BridgeErrors.NO_METHOD_AT_POSITION));
    }

    @Test
    void refusesAMethodTheContextDoesNotHold() {
        assertThatThrownBy(() -> await(service.browse(
            new BrowseParams("core", "com.example.core.Account", "absent", List.of()))))
                    .isInstanceOf(ResponseErrorException.class)
                    .satisfies(error -> assertThat(code(error))
                            .isEqualTo(BridgeErrors.METHOD_NOT_FOUND));
    }

    @Test
    void refusesAContextTheProjectDoesNotDeclare() {
        assertThatThrownBy(() -> await(service.resolveAt(new ResolveParams("absent",
            ACCOUNT_URI, 27, 9))))
                    .isInstanceOf(ResponseErrorException.class)
                    .satisfies(error -> assertThat(code(error))
                            .isEqualTo(BridgeErrors.UNKNOWN_CONTEXT));
    }

    private static int code(Throwable error) {
        return ((ResponseErrorException) error).getResponseError().getCode();
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        }
    }

    /** Records what would have been shown, so the service runs without a KeY window. */
    private static final class RecordingBrowser implements ProofBrowser {

        private final List<Shown> shown = new java.util.ArrayList<>();

        @Override
        public void show(InitConfig initConfig, KeYJavaType type, IObserverFunction target,
                Runnable afterClose) {
            shown.add(new Shown(type, target));
            afterClose.run();
        }

        private record Shown(KeYJavaType type, IObserverFunction target) {
        }
    }
}
