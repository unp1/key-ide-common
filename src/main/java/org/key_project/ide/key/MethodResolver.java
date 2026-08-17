/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import de.uka.ilkd.key.java.JavaInfo;
import de.uka.ilkd.key.java.ast.PositionInfo;
import de.uka.ilkd.key.java.ast.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.op.IProgramMethod;

/**
 * Finds the method a caret position is in.
 * <p>
 * The IDE sends a file and a position rather than a resolved name, so no plugin needs a
 * Java model of its own. This class works on a loaded environment and holds no GUI state,
 * which is what lets it be tested without starting KeY's user interface.
 */
public final class MethodResolver {

    private final JavaInfo javaInfo;

    /**
     * @param javaInfo the loaded environment to search
     */
    public MethodResolver(JavaInfo javaInfo) {
        this.javaInfo = javaInfo;
    }

    /**
     * Finds the innermost method whose range covers a position.
     *
     * @param file the source file the IDE is showing
     * @param line the 1-based line of the caret
     * @param column the 1-based column of the caret
     * @return the method, or empty when the position is outside every method
     */
    public Optional<ResolvedMethod> resolveAt(URI file, int line, int column) {
        Path target = normalize(file);
        if (target == null) {
            return Optional.empty();
        }
        return candidatesIn(target).stream().filter(method -> covers(method, line))
                .min(Comparator.comparingInt(ResolvedMethod::span)
                        .thenComparing(ResolvedMethod::className)
                        .thenComparing(ResolvedMethod::name));
    }

    /**
     * Finds a method by name, for a request that already knows which one it wants.
     *
     * @param className the declaring class, fully qualified
     * @param name the method name, or the class name for a constructor
     * @param parameterTypes the parameter types, fully qualified
     * @return the method, or empty when the class or the method is absent
     */
    public Optional<ResolvedMethod> find(String className, String name,
            List<String> parameterTypes) {
        KeYJavaType type = javaInfo.getKeYJavaType(className);
        if (type == null) {
            return Optional.empty();
        }
        return declaredIn(type).stream()
                .filter(method -> method.name().equals(name))
                .filter(method -> method.parameterTypes().equals(parameterTypes))
                .findFirst();
    }

    /**
     * Every method declared in a file, with the range resolution uses.
     *
     * @param file the source file
     * @return the methods, in no particular order
     */
    private List<ResolvedMethod> candidatesIn(Path file) {
        List<ResolvedMethod> candidates = new ArrayList<>();
        for (KeYJavaType type : javaInfo.getAllKeYJavaTypes()) {
            for (IProgramMethod method : methodsOf(type)) {
                ResolvedMethod resolved = describe(type, method, file);
                if (resolved != null) {
                    candidates.add(resolved);
                }
            }
        }
        return candidates;
    }

    /** Every method declared in a type, with the range resolution uses. */
    private List<ResolvedMethod> declaredIn(KeYJavaType type) {
        List<ResolvedMethod> declared = new ArrayList<>();
        for (IProgramMethod method : methodsOf(type)) {
            PositionInfo position = method.getPositionInfo();
            Path file = position.getURI().map(MethodResolver::normalize).orElse(null);
            ResolvedMethod resolved =
                file == null ? new ResolvedMethod(type, method, 0, 0) : describe(type, method, file);
            if (resolved != null) {
                declared.add(resolved);
            }
        }
        return declared;
    }

    /**
     * Constructors and methods together.
     * <p>
     * {@code getAllProgramMethodsLocallyDeclared} omits constructors, and also returns
     * synthetic members such as {@code $init} whose position is undefined. Those are
     * dropped by the position test rather than by name.
     */
    private List<IProgramMethod> methodsOf(KeYJavaType type) {
        List<IProgramMethod> methods = new ArrayList<>(javaInfo.getAllProgramMethodsLocallyDeclared(type));
        methods.addAll(javaInfo.getConstructors(type));
        return methods;
    }

    /**
     * Describes a method when it belongs to a file, extending its start over any comment
     * written above it.
     *
     * @return the description, or {@code null} when the method is elsewhere or synthetic
     */
    private ResolvedMethod describe(KeYJavaType type, IProgramMethod method, Path file) {
        PositionInfo position = method.getPositionInfo();
        Path declared = position.getURI().map(MethodResolver::normalize).orElse(null);
        if (declared == null || !declared.equals(file)) {
            return null;
        }
        int declarationLine = position.getStartPosition().line();
        int endLine = position.getEndPosition().line();
        if (declarationLine < 1 || endLine < declarationLine) {
            return null;
        }
        return new ResolvedMethod(type, method,
            LeadingComment.startLineOf(file, declarationLine), endLine);
    }

    private static boolean covers(ResolvedMethod method, int line) {
        return method.startLine() <= line && line <= method.endLine();
    }

    /**
     * Turns a file URI into a path two references to the same file agree on.
     *
     * @param uri the location to normalize
     * @return the real path, or {@code null} when the location is not a readable file
     */
    private static Path normalize(URI uri) {
        if (uri == null || !"file".equals(uri.getScheme())) {
            return null;
        }
        try {
            Path path = Path.of(uri);
            try {
                return path.toRealPath();
            } catch (IOException e) {
                return path.toAbsolutePath().normalize();
            }
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            return null;
        }
    }
}
