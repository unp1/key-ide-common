/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.List;
import java.util.ServiceLoader;

import de.uka.ilkd.key.gui.extension.api.KeYGuiExtension;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ties the members the bridge calls to the KeY it is built against.
 * <p>
 * KeY moves its classes between packages from time to time, as the move of
 * {@code KeYJavaType} into {@code java.ast.abstraction} shows. This test fails when that
 * happens, rather than leaving a user to meet the failure at startup.
 */
class CapabilityProbeTest {

    @Test
    void findsEveryMemberInTheKeyItIsBuiltAgainst() {
        List<String> missing = new CapabilityProbe().missingMembers();

        assertThat(missing).isEmpty();
    }

    @Test
    void namesTheMembersItCouldNotFind() {
        CapabilityProbe withoutKey = new CapabilityProbe(ClassLoader.getPlatformClassLoader());

        List<String> missing = withoutKey.missingMembers();

        assertThat(missing).hasSameSizeAs(CapabilityProbe.requirements());
        assertThat(missing).anySatisfy(description -> assertThat(description)
                .contains("de.uka.ilkd.key.gui.ProofManagementDialog#showInstance")
                .contains("de.uka.ilkd.key.java.ast.abstraction.KeYJavaType"));
    }

    @Test
    void describesARequirementWithItsParameterTypes() {
        CapabilityProbe.Requirement requirement = CapabilityProbe.requirements().stream()
                .filter(r -> r.methodName().equals("load")).findFirst().orElseThrow();

        assertThat(requirement.describe())
                .isEqualTo("de.uka.ilkd.key.control.KeYEnvironment#load(java.nio.file.Path,"
                    + " java.util.List, java.nio.file.Path, java.util.List)");
    }

    @Test
    void isRegisteredWithKeysServiceLoader() {
        List<KeYGuiExtension> extensions =
            ServiceLoader.load(KeYGuiExtension.class).stream().map(ServiceLoader.Provider::get)
                    .toList();

        assertThat(extensions).anySatisfy(extension -> {
            assertThat(extension).isInstanceOf(KeyIdeExtension.class);
            KeYGuiExtension.Info info = extension.getClass()
                    .getAnnotation(KeYGuiExtension.Info.class);
            assertThat(info.name()).isEqualTo("KeY IDE bridge");
            assertThat(info.experimental()).isFalse();
        });
    }

    @Test
    void implementsTheStartupHookKeyCalls() {
        assertThat(new KeyIdeExtension()).isInstanceOf(KeYGuiExtension.Startup.class);
    }
}
