/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.uka.ilkd.key.nparser.KeyAst;
import de.uka.ilkd.key.nparser.ParsingFacade;
import de.uka.ilkd.key.proof.init.Profile;
import de.uka.ilkd.key.settings.ProofSettings;
import de.uka.ilkd.key.strategy.StrategyProperties;

import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.protocol.Dtos.OptionCategoryDto;
import org.key_project.ide.protocol.Dtos.OptionDifferenceDto;
import org.key_project.ide.protocol.Dtos.OptionValueDto;

/**
 * The settings recorded in a saved proof, and their difference to the settings currently
 * configured for its proof obligation.
 * <p>
 * A proof file records its settings in a leading settings block. They say nothing about
 * whether the proof still closes, only under which options it was created. A proof created
 * under different taclet options proved a different statement; one created under different
 * strategy options proved the same statement along another path.
 *
 * @param taclet the taclet options the proof was created with, by category
 * @param strategy the strategy options it was created with, by key
 */
public record SavedSettings(Map<String, String> taclet, Map<String, String> strategy) {

    /** A settings block that has been read, kept as long as the file does not change. */
    private record Read(FileTime modified, long size, SavedSettings settings) {
    }

    private static final Map<Path, Read> READ = new ConcurrentHashMap<>();

    /**
     * The settings recorded in a saved proof.
     * <p>
     * Results are cached by modification time and size, since listing a context reads the
     * settings of every saved proof each time.
     *
     * @param proofFile the saved proof
     * @return its settings, or null if the file does not exist or cannot be parsed
     */
    public static SavedSettings of(Path proofFile) {
        FileTime modified;
        long size;
        try {
            modified = Files.getLastModifiedTime(proofFile);
            size = Files.size(proofFile);
        } catch (IOException e) {
            return null;
        }
        Read read = READ.get(proofFile);
        if (read != null && read.modified().equals(modified) && read.size() == size) {
            return read.settings();
        }
        SavedSettings settings = parse(proofFile);
        if (settings != null) {
            READ.put(proofFile, new Read(modified, size, settings));
        }
        return settings;
    }

    private static SavedSettings parse(Path proofFile) {
        try {
            KeyAst.File parsed = ParsingFacade.parseFile(proofFile);
            ProofSettings settings = parsed.findProofSettings();
            if (settings == null) {
                return null;
            }
            Map<String, String> strategy = new LinkedHashMap<>();
            StrategyProperties properties =
                settings.getStrategySettings().getActiveStrategyProperties();
            properties.stringPropertyNames()
                    .forEach(key -> strategy.put(key, properties.getProperty(key)));
            return new SavedSettings(
                new LinkedHashMap<>(settings.getChoiceSettings().getDefaultChoices()), strategy);
        } catch (IOException | RuntimeException e) {
            // A proof file that cannot be parsed is reported when it is replayed. Here it
            // simply has no settings to compare.
            return null;
        }
    }

    /**
     * The differences between these settings and the ones currently configured.
     *
     * @param loadedChoices the taclet options KeY selected while loading the context
     * @param options what the obligation states, with its context and project laid under
     * @param profile the profile of the loaded context, which words the strategy options
     * @return each option that differs, worded as KeY words it; empty when none does
     */
    public List<OptionDifferenceDto> differencesFrom(Map<String, String> loadedChoices,
            ProofOptions options, Profile profile) {
        List<OptionDifferenceDto> differences = new ArrayList<>();

        Map<String, String> currentTaclet = new LinkedHashMap<>(loadedChoices);
        currentTaclet.putAll(options.taclet());
        currentTaclet.forEach((category, current) -> {
            String saved = taclet.get(category);
            if (saved != null && !saved.equals(current)) {
                differences.add(new OptionDifferenceDto("taclet", category, option(saved),
                    option(current)));
            }
        });

        for (OptionCategoryDto category : AvailableOptions.strategyCategories(profile)) {
            String current = options.strategy().getOrDefault(category.key(),
                StrategyProperties.getDefaultProperty(category.key()));
            String saved = strategy.get(category.key());
            if (saved != null && current != null && !saved.equals(current)) {
                differences.add(new OptionDifferenceDto("strategy", category.label(),
                    labelOf(category, saved), labelOf(category, current)));
            }
        }
        return differences;
    }

    /** The option part of a choice named {@code category:option}. */
    private static String option(String choice) {
        int colon = choice.indexOf(':');
        return colon < 0 ? choice : choice.substring(colon + 1);
    }

    /** What KeY calls a value, or the value itself when KeY does not list it. */
    private static String labelOf(OptionCategoryDto category, String value) {
        return category.values().stream().filter(v -> v.value().equals(value))
                .map(OptionValueDto::label).findFirst().orElse(value);
    }
}
