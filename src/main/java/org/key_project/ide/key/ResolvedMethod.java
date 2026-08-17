/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.List;

import de.uka.ilkd.key.java.ast.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.op.IProgramMethod;

/**
 * A method found in a loaded environment, with the range resolution matched against.
 * <p>
 * This holds KeY types and therefore never leaves the bridge. The service turns it into
 * the wire form.
 *
 * @param type the class declaring the method
 * @param method the method itself, which is also the proof obligation's target
 * @param startLine the 1-based line the range starts on, including any leading comment
 * @param endLine the 1-based line the declaration ends on
 */
public record ResolvedMethod(KeYJavaType type, IProgramMethod method, int startLine,
        int endLine) {

    /** The declaring class, fully qualified. */
    public String className() {
        return type.getFullName();
    }

    /** The method name, or the class name for a constructor. */
    public String name() {
        return method.getName();
    }

    /** Whether this is a constructor rather than a method. */
    public boolean isConstructor() {
        return method.isConstructor();
    }

    /**
     * The parameter types, fully qualified, in declaration order.
     * <p>
     * The names come from the sort rather than from {@code KeYJavaType.getFullName}, which
     * reports an array as its JVM descriptor: {@code int[]} appears there as {@code [I}.
     * The sort names an array the way the source does, and agrees with the full name for
     * every other type.
     */
    public List<String> parameterTypes() {
        return method.getParamTypes().toList().stream()
                .map(type -> type.getSort().name().toString()).toList();
    }

    /** How many lines the range covers, used to prefer the innermost match. */
    public int span() {
        return endLine - startLine;
    }
}
