/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import de.uka.ilkd.key.control.KeYEnvironment;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves positions against the test project, loaded into a real KeY environment.
 * <p>
 * Loading takes seconds, so one environment serves the whole class.
 */
class MethodResolverTest {

    private static final Path FIXTURE = Path.of("src/test/fixture").toAbsolutePath().normalize();
    private static final Path ACCOUNT =
        FIXTURE.resolve("core/src/main/java/com/example/core/Account.java");
    private static final Path ARRAY_UTILS =
        FIXTURE.resolve("core/src/main/java/com/example/core/ArrayUtils.java");

    private static KeYEnvironment<?> environment;
    private static MethodResolver resolver;

    @BeforeAll
    static void loadFixture() throws Exception {
        environment = KeYEnvironment.load(FIXTURE.resolve("core/src/main/java"), null, null, null);
        resolver = new MethodResolver(environment.getJavaInfo());
    }

    @AfterAll
    static void disposeFixture() {
        if (environment != null) {
            environment.dispose();
        }
    }

    @Test
    void findsAMethodFromAPositionInsideItsBody() {
        // Account.deposit occupies lines 26 to 28.
        Optional<ResolvedMethod> found = resolver.resolveAt(ACCOUNT.toUri(), 27, 9);

        assertThat(found).hasValueSatisfying(method -> {
            assertThat(method.className()).isEqualTo("com.example.core.Account");
            assertThat(method.name()).isEqualTo("deposit");
            assertThat(method.parameterTypes()).containsExactly("int");
            assertThat(method.isConstructor()).isFalse();
        });
    }

    @Test
    void findsAMethodFromAPositionInsideItsContract() {
        // The contract of Account.deposit occupies lines 20 to 25, above the declaration.
        // KeY's own range starts at line 26, so this is the case a plain containment test
        // would miss, and it is the position a user most naturally right-clicks.
        Optional<ResolvedMethod> found = resolver.resolveAt(ACCOUNT.toUri(), 22, 12);

        assertThat(found).hasValueSatisfying(method -> {
            assertThat(method.name()).isEqualTo("deposit");
            assertThat(method.startLine()).isEqualTo(20);
        });
    }

    @Test
    void findsAConstructorByName() {
        Optional<ResolvedMethod> found =
            resolver.find("com.example.core.Account", "Account", List.of());

        assertThat(found).hasValueSatisfying(method -> {
            assertThat(method.isConstructor()).isTrue();
            assertThat(method.className()).isEqualTo("com.example.core.Account");
        });
    }

    @Test
    void doesNotReachAConstructorFromAPosition() {
        // Account() occupies lines 16 to 18 in the source, but KeY records no position
        // for a constructor: getConstructors returns it with an undefined PositionInfo
        // and no file. Positional resolution therefore cannot reach it, and a right-click
        // inside a constructor has to be answered by name instead. This test records the
        // limitation so a future KeY that fixes it is noticed here.
        Optional<ResolvedMethod> found = resolver.resolveAt(ACCOUNT.toUri(), 17, 9);

        assertThat(found).isEmpty();
    }

    @Test
    void findsAMethodWithAnArrayParameter() {
        // ArrayUtils.maximum occupies lines 27 to 43.
        Optional<ResolvedMethod> found = resolver.resolveAt(ARRAY_UTILS.toUri(), 35, 13);

        assertThat(found).hasValueSatisfying(method -> {
            assertThat(method.name()).isEqualTo("maximum");
            assertThat(method.parameterTypes()).containsExactly("int[]");
        });
    }

    @Test
    void reportsNothingForAPositionOutsideEveryMethod() {
        // Line 1 is the package declaration.
        Optional<ResolvedMethod> found = resolver.resolveAt(ACCOUNT.toUri(), 1, 1);

        assertThat(found).isEmpty();
    }

    @Test
    void reportsNothingForAFileThatIsNotInTheContext() {
        Optional<ResolvedMethod> found =
            resolver.resolveAt(URI.create("file:///nowhere/Absent.java"), 10, 1);

        assertThat(found).isEmpty();
    }

    @Test
    void findsAMethodByName() {
        Optional<ResolvedMethod> found =
            resolver.find("com.example.core.ArrayUtils", "max", List.of("int", "int"));

        assertThat(found).hasValueSatisfying(
            method -> assertThat(method.name()).isEqualTo("max"));
    }

    @Test
    void reportsNothingForAMethodWithDifferentParameterTypes() {
        Optional<ResolvedMethod> found =
            resolver.find("com.example.core.ArrayUtils", "max", List.of("long"));

        assertThat(found).isEmpty();
    }
}
