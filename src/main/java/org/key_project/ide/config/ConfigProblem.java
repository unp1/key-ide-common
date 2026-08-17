/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

/**
 * A configuration problem reported to the IDE, to be shown on the settings form before a
 * context is loaded.
 *
 * @param severity whether the context can be loaded at all
 * @param contextId the context the problem belongs to, empty for project-wide problems
 * @param field the field it belongs to, such as {@code javaSource} or {@code classpath[0]}
 * @param message a sentence naming what is wrong
 */
public record ConfigProblem(Severity severity, String contextId, String field, String message) {

    public enum Severity {
        /** Loading this context fails. */
        ERROR,
        /** Loading succeeds, but probably not as intended. */
        WARNING
    }

    public static ConfigProblem error(String contextId, String field, String message) {
        return new ConfigProblem(Severity.ERROR, contextId, field, message);
    }

    public static ConfigProblem warning(String contextId, String field, String message) {
        return new ConfigProblem(Severity.WARNING, contextId, field, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
