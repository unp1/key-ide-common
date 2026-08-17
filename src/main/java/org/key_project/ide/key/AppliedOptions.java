/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.init.InitConfig;
import de.uka.ilkd.key.proof.init.Profile;
import de.uka.ilkd.key.settings.ProofIndependentSettings;
import de.uka.ilkd.key.settings.ProofSettings;
import de.uka.ilkd.key.settings.StrategySettings;
import de.uka.ilkd.key.strategy.StrategyFactory;
import de.uka.ilkd.key.strategy.StrategyProperties;

import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.config.ProverOptions;
import org.key_project.logic.Choice;
import org.key_project.logic.Name;
import org.key_project.util.collection.ImmutableSet;

/**
 * Puts the settings a proof is to be attempted with where KeY reads them.
 * <p>
 * The three kinds live in three places. Taclet options decide which taclets can be used in a proof, so they
 * belong to the configuration a proof is built from and have to be set before it is built.
 * Strategy options belong to the proof and are set once it exists. The prover belongs to
 * the running KeY and to nothing else, so it is set once for the bridge.
 */
public final class AppliedOptions {

    private AppliedOptions() {
    }

    /**
     * Activates the taclet options for proofs created from this InitConfig.
     * <p>
     * The complete set is activated every time: the options KeY selected while loading the
     * context, overridden by the ones this proof configures. Activating only the configured
     * ones would leave the choices of the previously created proof in place, and a proof
     * that configures none would inherit them.
     * <p>
     * The choices are also written to the InitConfig's settings. A proof stores those
     * settings in its file and KeY reads them back, so settings that disagreed with the
     * activated taclets would reopen the proof under the wrong options.
     *
     * @param initConfig the InitConfig proofs of this context are created from
     * @param options the settings to apply
     * @param loaded the taclet options KeY selected while loading the context
     */
    public static void applyTacletOptions(InitConfig initConfig, ProofOptions options,
            Map<String, String> loaded) {
        Map<String, String> wanted = new LinkedHashMap<>(loaded);
        wanted.putAll(options.taclet());
        if (wanted.isEmpty()) {
            return;
        }

        Map<String, Choice> chosen = new LinkedHashMap<>();
        initConfig.getActivatedChoices().forEach(choice -> chosen.put(choice.category(), choice));
        wanted.forEach((category, name) -> {
            Choice choice = initConfig.choiceNS().lookup(new Name(name));
            if (choice != null && choice.category().equals(category)) {
                chosen.put(category, choice);
            }
        });
        initConfig.setActivatedChoices(ImmutableSet.from(chosen.values()));

        // A context loaded from Java sources has no ProofSettings of its own, so a proof
        // created from it copies KeY's global defaults, which hold whatever was last saved
        // on this machine. Giving the InitConfig its own settings makes the proof copy
        // these instead.
        ProofSettings settings = initConfig.getSettings();
        if (settings == null) {
            settings = new ProofSettings(ProofSettings.DEFAULT_SETTINGS);
            initConfig.setSettings(settings);
        }
        Map<String, String> recorded =
            new TreeMap<>(settings.getChoiceSettings().getDefaultChoices());
        chosen.forEach((category, choice) -> recorded.put(category, choice.name().toString()));
        settings.getChoiceSettings().setDefaultChoices(recorded);
    }

    /**
     * Sets the strategy options a proof is to be run with.
     * <p>
     * The strategy is recreated from the properties. A strategy is created once from the
     * properties as they were, and changing them afterwards does not recreate it, so
     * setting them alone would leave the proof running its original strategy.
     *
     * @param proof the proof about to be run
     * @param options the settings to apply
     */
    public static void applyStrategyOptions(Proof proof, ProofOptions options) {
        StrategySettings settings = proof.getSettings().getStrategySettings();
        StrategyProperties properties = settings.getActiveStrategyProperties();
        options.strategy().forEach(properties::setProperty);
        settings.setActiveStrategyProperties(properties);
        if (options.maxSteps() > 0) {
            settings.setMaxSteps(options.maxSteps());
        }
        // KeY reads -1 as "no timeout", so it is a value like any other; 0 is what a level
        // that says nothing looks like.
        if (options.timeout() != 0) {
            settings.setTimeout(options.timeout());
        }
        proof.setActiveStrategy(factoryFor(proof).create(proof, properties));
    }

    /** The factory that builds the strategy this proof is set to use. */
    private static StrategyFactory factoryFor(Proof proof) {
        Profile profile = proof.getServices().getProfile();
        Name strategy = proof.getSettings().getStrategySettings().getStrategy();
        return profile.supportsStrategyFactory(strategy) ? profile.getStrategyFactory(strategy)
                : profile.getDefaultStrategyFactory();
    }

    /**
     * Selects the prover used by every run of this bridge.
     *
     * @param options which prover to use
     */
    public static void applyProver(ProverOptions options) {
        var general = ProofIndependentSettings.DEFAULT_INSTANCE.getGeneralSettings();
        general.setParallelProverEnabled(options.parallel());
        if (options.threads() > 0) {
            general.setParallelProverThreadCount(options.threads());
        }
    }
}
