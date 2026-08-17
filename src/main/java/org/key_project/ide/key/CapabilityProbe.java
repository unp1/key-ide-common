/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the KeY on the classpath exposes the API on which the bridge relies.
 * <p>
 * KeY is supplied by the user, so it may be any build. Its version string cannot decide
 * the question: every build off KeY's main branch reports the same version, and a locally
 * built jar carries an empty internal revision. Querying the API capability and finding
 * one missing at startup gives a message naming it instead of a
 * reflective failure later when the user uses a feature.
 * <p>
 * The lookups go through reflection on purpose. Referring to these members directly would
 * make the class fail to link, before any message could be produced.
 */
public final class CapabilityProbe {

    /**
     * One member the bridge depends on.
     *
     * @param className the declaring class
     * @param methodName the method
     * @param parameterTypes the parameter types, by name
     */
    public record Requirement(String className, String methodName, List<String> parameterTypes) {

        /** A description naming the member, for the message shown when it is absent. */
        public String describe() {
            return className + "#" + methodName + "(" + String.join(", ", parameterTypes) + ")";
        }
    }

    private static final String PROOF_MANAGEMENT_DIALOG =
        "de.uka.ilkd.key.gui.ProofManagementDialog";
    private static final String KEY_ENVIRONMENT = "de.uka.ilkd.key.control.KeYEnvironment";
    private static final String JAVA_INFO = "de.uka.ilkd.key.java.JavaInfo";
    private static final String KEY_JAVA_TYPE = "de.uka.ilkd.key.java.ast.abstraction.KeYJavaType";

    /** Everything the bridge calls on KeY. */
    private static final List<Requirement> REQUIREMENTS = List.of(
        new Requirement(PROOF_MANAGEMENT_DIALOG, "showInstance",
            List.of("de.uka.ilkd.key.proof.init.InitConfig", KEY_JAVA_TYPE,
                "de.uka.ilkd.key.logic.op.IObserverFunction")),
        new Requirement(KEY_ENVIRONMENT, "load",
            List.of("java.nio.file.Path", "java.util.List", "java.nio.file.Path",
                "java.util.List")),
        new Requirement(JAVA_INFO, "getKeYJavaType", List.of("java.lang.String")),
        new Requirement(JAVA_INFO, "getAllProgramMethodsLocallyDeclared", List.of(KEY_JAVA_TYPE)),
        new Requirement(JAVA_INFO, "getConstructors", List.of(KEY_JAVA_TYPE)));

    private final ClassLoader loader;

    /** Probes the classpath this bridge was loaded from. */
    public CapabilityProbe() {
        this(CapabilityProbe.class.getClassLoader());
    }

    /**
     * @param loader the loader to resolve KeY classes through
     */
    public CapabilityProbe(ClassLoader loader) {
        this.loader = loader;
    }

    /** The requirements this probe checks. */
    public static List<Requirement> requirements() {
        return REQUIREMENTS;
    }

    /**
     * Looks for every member the bridge needs.
     *
     * @return descriptions of the members that are absent, empty when KeY can be driven
     */
    public List<String> missingMembers() {
        List<String> missing = new ArrayList<>();
        for (Requirement requirement : REQUIREMENTS) {
            if (!isPresent(requirement)) {
                missing.add(requirement.describe());
            }
        }
        return missing;
    }

    private boolean isPresent(Requirement requirement) {
        try {
            Class<?> owner = Class.forName(requirement.className(), false, loader);
            Class<?>[] parameters = new Class<?>[requirement.parameterTypes().size()];
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = resolve(requirement.parameterTypes().get(i));
            }
            owner.getMethod(requirement.methodName(), parameters);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
    }

    private Class<?> resolve(String className) throws ClassNotFoundException {
        return Class.forName(className, false, loader);
    }
}
