/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.proof.init.InitConfig;
import de.uka.ilkd.key.proof.init.JavaProfile;
import de.uka.ilkd.key.proof.io.ProblemLoaderException;

/**
 * KeY's rules, read without a project.
 * <p>
 * Which options a proof can be attempted with is KeY's own answer: the taclet options come
 * from the rule files it reads and the strategy options from its profile, and neither depends
 * on the Java a project holds. Reading them from a loaded context would make opening a
 * settings page wait for the whole project to be parsed, and would leave a project that
 * declares no context yet with no options to show at all.
 * <p>
 * KeY has to load something to have rules, so this loads the least there is: a problem file
 * naming no source. It is read once and kept, since the rules do not change while the process
 * runs.
 */
public final class RuleBase {

    /** A problem KeY can load without being given any Java: the rules, and nothing else. */
    private static final String EMPTY_PROBLEM = "\\problem { true }\n";

    private static volatile InitConfig loaded;

    private RuleBase() {
    }

    /**
     * KeY's rules, loaded on first use and kept.
     *
     * @return the configuration the rule files were read into
     * @throws ProblemLoaderException if KeY cannot read its own rules, which means the KeY on
     *         the classpath is not usable
     */
    public static InitConfig initConfig() throws ProblemLoaderException {
        InitConfig known = loaded;
        if (known != null) {
            return known;
        }
        synchronized (RuleBase.class) {
            if (loaded == null) {
                loaded = read();
            }
            return loaded;
        }
    }

    private static InitConfig read() throws ProblemLoaderException {
        Path problem = writeProblem();
        try {
            KeYEnvironment<?> environment =
                KeYEnvironment.load(new JavaProfile(), problem, null, null, null, true);
            // The environment holds a proof of the empty problem, which nothing needs. The
            // configuration it was read into is what carries the rules, and outlives it.
            InitConfig config = environment.getInitConfig();
            environment.dispose();
            return config;
        } finally {
            try {
                Files.deleteIfExists(problem);
                Files.deleteIfExists(problem.getParent());
            } catch (IOException e) {
                // A temporary file left behind is not worth failing over.
            }
        }
    }

    private static Path writeProblem() {
        try {
            Path directory = Files.createTempDirectory("key-ide-rules");
            Path problem = directory.resolve("rules.key");
            Files.writeString(problem, EMPTY_PROBLEM);
            return problem;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the problem KeY reads its rules from",
                e);
        }
    }
}
