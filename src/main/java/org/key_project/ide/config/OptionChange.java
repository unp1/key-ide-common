/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A change to the settings configured at one level.
 * <p>
 * The form sends only the fields the user changed. This matters when several proof
 * obligations are edited at once: each keeps the settings that were not changed, instead of
 * being overwritten with the complete settings of one of them.
 * <p>
 * Clearing a field is separate from setting one. Setting configures a value; clearing falls
 * back to the level above, which no value can express.
 *
 * @param taclet the taclet options to set, by category
 * @param tacletCleared the taclet categories to leave to the level above
 * @param strategy the strategy options to set, by key
 * @param strategyCleared the strategy keys to leave to the level above
 * @param maxSteps how many rule applications to allow, 0 to leave it to the level above,
 *        or null if the form did not change it
 * @param timeout how long one attempt may take in milliseconds, -1 for no timeout, 0 to
 *        leave it to the level above, or null if the form did not change it
 */
public record OptionChange(Map<String, String> taclet, List<String> tacletCleared,
        Map<String, String> strategy, List<String> strategyCleared, Integer maxSteps,
        Long timeout) {

    /** An edit that touches nothing, so every level keeps what it stated. */
    public static final OptionChange NOTHING =
        new OptionChange(Map.of(), List.of(), Map.of(), List.of(), null, null);

    /**
     * A change that leaves the timeout as it is.
     *
     * @param taclet the taclet options to set
     * @param tacletCleared the taclet categories to clear
     * @param strategy the strategy options to set
     * @param strategyCleared the strategy keys to clear
     * @param maxSteps the limit on rule applications
     */
    public OptionChange(Map<String, String> taclet, List<String> tacletCleared,
            Map<String, String> strategy, List<String> strategyCleared, Integer maxSteps) {
        this(taclet, tacletCleared, strategy, strategyCleared, maxSteps, null);
    }

    public OptionChange {
        taclet = taclet == null ? Map.of() : Map.copyOf(taclet);
        tacletCleared = tacletCleared == null ? List.of() : List.copyOf(tacletCleared);
        strategy = strategy == null ? Map.of() : Map.copyOf(strategy);
        strategyCleared = strategyCleared == null ? List.of() : List.copyOf(strategyCleared);
    }

    /**
     * The settings of a level after this change has been applied.
     *
     * @param stated what that level states now
     * @return what it states afterwards
     */
    public ProofOptions applyTo(ProofOptions stated) {
        return new ProofOptions(
            edited(stated.taclet(), taclet, tacletCleared),
            edited(stated.strategy(), strategy, strategyCleared),
            maxSteps == null ? stated.maxSteps() : maxSteps,
            timeout == null ? stated.timeout() : timeout);
    }

    private static Map<String, String> edited(Map<String, String> stated, Map<String, String> set,
            List<String> cleared) {
        if (set.isEmpty() && cleared.isEmpty()) {
            return stated;
        }
        Map<String, String> edited = new LinkedHashMap<>(stated);
        edited.putAll(set);
        cleared.forEach(edited::remove);
        return edited;
    }
}
