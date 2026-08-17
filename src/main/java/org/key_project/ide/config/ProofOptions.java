/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The settings a proof is attempted with.
 * <p>
 * A project configures these once, a context may override the project, and a single proof
 * obligation may override its context. Each level configures only what it changes, so a
 * setting configured at no level is the one KeY uses by default.
 *
 * @param taclet the taclet options, by the category they belong to
 * @param strategy the strategy options, by their key in KeY's strategy properties
 * @param maxSteps how many rule applications a run may make, or 0 to leave KeY's own
 * @param timeout how long one attempt may take, in milliseconds; -1 is KeY's "no timeout",
 *        and 0 leaves the level above to say
 */
public record ProofOptions(Map<String, String> taclet, Map<String, String> strategy,
        int maxSteps, long timeout) {

    /** Settings that change nothing. */
    public static final ProofOptions NONE = new ProofOptions(Map.of(), Map.of(), 0, 0);

    public ProofOptions {
        taclet = taclet == null ? Map.of() : Map.copyOf(taclet);
        strategy = strategy == null ? Map.of() : Map.copyOf(strategy);
        maxSteps = Math.max(maxSteps, 0);
        timeout = Math.max(timeout, -1);
    }

    /**
     * Settings without a timeout of their own.
     *
     * @param taclet the taclet options
     * @param strategy the strategy options
     * @param maxSteps the limit on rule applications
     */
    public ProofOptions(Map<String, String> taclet, Map<String, String> strategy, int maxSteps) {
        this(taclet, strategy, maxSteps, 0);
    }

    /**
     * These settings, overridden by the given ones.
     *
     * @param override what the more specific level says
     * @return the settings to attempt a proof with
     */
    public ProofOptions mergedWith(ProofOptions override) {
        if (override == null) {
            return this;
        }
        return new ProofOptions(overlay(taclet, override.taclet),
            overlay(strategy, override.strategy),
            override.maxSteps > 0 ? override.maxSteps : maxSteps,
            override.timeout != 0 ? override.timeout : timeout);
    }

    /** Whether these settings say anything at all. */
    public boolean isEmpty() {
        return taclet.isEmpty() && strategy.isEmpty() && maxSteps == 0 && timeout == 0;
    }

    private static Map<String, String> overlay(Map<String, String> base,
            Map<String, String> over) {
        if (over.isEmpty()) {
            return base;
        }
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(over);
        return Map.copyOf(merged);
    }
}
