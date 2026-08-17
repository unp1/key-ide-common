/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import com.google.common.html.HtmlEscapers;
import de.uka.ilkd.key.proof.init.InitConfig;
import de.uka.ilkd.key.proof.init.Profile;
import de.uka.ilkd.key.settings.StrategySettings;
import de.uka.ilkd.key.strategy.StrategyProperties;
import de.uka.ilkd.key.strategy.definition.AbstractStrategyPropertyDefinition;
import de.uka.ilkd.key.strategy.definition.OneOfStrategyPropertyDefinition;
import de.uka.ilkd.key.strategy.definition.StrategyPropertyValueDefinition;
import de.uka.ilkd.key.strategy.definition.StrategySettingsDefinition;

import org.key_project.ide.protocol.Dtos.AvailableOptionsDto;
import org.key_project.ide.protocol.Dtos.OptionCategoryDto;
import org.key_project.ide.protocol.Dtos.OptionValueDto;
import org.key_project.ide.protocol.Dtos.ProofOptionsDto;

/**
 * What a loaded context offers to choose from, so that a form can offer it too.
 * <p>
 * Taclet options and strategy options are read directly from KeY. The taclet options are
 * the choices KeY read from the rule files of this context. The strategy options come from the
 * profile. Explaining text is also queried directly from KeY to avoid inconsistencies (out-of-date
 * information).
 */
public final class AvailableOptions {

    /** Where KeY keeps what each taclet option means. */
    private static final String TACLET_EXPLANATIONS =
        "/de/uka/ilkd/key/gui/help/choiceExplanations.xml";

    private static Properties tacletExplanations;

    /**
     * The strategy options per profile, read once.
     * <p>
     * Reading them traverses the profile's whole settings definition, and listing a context
     * needs them once per proof obligation to determine how a saved proof's settings differ.
     */
    private static final Map<Profile, List<OptionCategoryDto>> STRATEGY_OPTIONS =
        new java.util.concurrent.ConcurrentHashMap<>();

    private AvailableOptions() {
    }

    /**
     * The options a context offers, and the values used where no level configures one.
     *
     * @param initConfig the InitConfig the context was loaded into
     * @param loadedChoices the taclet options KeY selected while loading the context
     * @return the available options and the default values
     */
    public static AvailableOptionsDto of(InitConfig initConfig,
            Map<String, String> loadedChoices) {
        return new AvailableOptionsDto(tacletCategories(initConfig),
            strategyCategories(initConfig.getProfile()),
            new ProofOptionsDto(loadedChoices, strategyDefaults(initConfig.getProfile()),
                new StrategySettings().getMaxSteps(), new StrategySettings().getTimeout()));
    }

    /**
     * The taclet options activated in an InitConfig, by category.
     *
     * @param initConfig the InitConfig to read
     * @return the activated choice per category, as a full choice name
     */
    public static Map<String, String> chosen(InitConfig initConfig) {
        Map<String, String> chosen = new LinkedHashMap<>();
        initConfig.getActivatedChoices()
                .forEach(choice -> chosen.put(choice.category(), choice.name().toString()));
        return chosen;
    }

    /**
     * The taclet option categories and the choices each one accepts.
     *
     * @param initConfig the InitConfig to read
     * @return the categories, in alphabetical order
     */
    private static List<OptionCategoryDto> tacletCategories(InitConfig initConfig) {
        Map<String, List<OptionValueDto>> byCategory = new TreeMap<>();
        initConfig.choiceNS().elements()
                .forEach(choice -> byCategory
                        .computeIfAbsent(choice.category(), category -> new ArrayList<>())
                        .add(new OptionValueDto(choice.name().toString(),
                            option(choice.name().toString()), "")));

        List<OptionCategoryDto> categories = new ArrayList<>();
        byCategory.forEach((category, values) -> {
            values.sort((left, right) -> left.label().compareTo(right.label()));
            categories.add(
                new OptionCategoryDto(category, category, explanationOf(category), values));
        });
        return categories;
    }

    /**
     * The strategy options incl. their suboptions
     * <p>
     * An option may describe further options that only make sense under it, such as how
     * local queries are expanded under query treatment. Those are listed after the option
     * they belong to rather than inside it, since each is chosen on its own.
     *
     * @param profile the profile of the loaded context
     * @return the options, in the order the profile lists them
     */
    public static List<OptionCategoryDto> strategyCategories(Profile profile) {
        return STRATEGY_OPTIONS.computeIfAbsent(profile, of -> {
            StrategySettingsDefinition definition =
                of.getDefaultStrategyFactory().getSettingsDefinition();
            List<OptionCategoryDto> categories = new ArrayList<>();
            definition.getProperties().forEach(property -> collect(property, categories));
            return List.copyOf(categories);
        });
    }

    private static void collect(AbstractStrategyPropertyDefinition property,
            List<OptionCategoryDto> categories) {
        if (property instanceof OneOfStrategyPropertyDefinition one) {
            List<OptionValueDto> values = new ArrayList<>();
            for (StrategyPropertyValueDefinition value : one.getValues()) {
                values.add(new OptionValueDto(value.getApiValue(), value.getValue(),
                    text(value.getTooltip())));
            }
            if (!values.isEmpty()) {
                categories.add(new OptionCategoryDto(one.getApiKey(), label(one.getName()),
                    text(one.getTooltip()), values));
            }
        }
        property.getSubProperties().forEach(sub -> collect(sub, categories));
    }

    /** The default value of every strategy option */
    private static Map<String, String> strategyDefaults(Profile profile) {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (OptionCategoryDto category : strategyCategories(profile)) {
            String value = StrategyProperties.getDefaultProperty(category.key());
            if (value != null) {
                defaults.put(category.key(), value);
            }
        }
        return defaults;
    }

    /**
     * Description for the specified taclet option
     *
     * @param category the category to explain
     * @return the explanation, or an empty string when none available
     */
    private static synchronized String explanationOf(String category) {
        if (tacletExplanations == null) {
            tacletExplanations = new Properties();
            try (InputStream source =
                AvailableOptions.class.getResourceAsStream(TACLET_EXPLANATIONS)) {
                if (source != null) {
                    tacletExplanations.loadFromXML(source);
                }
            } catch (IOException e) {
                // In case querying for a description errs we simply ignore the error
                // as the strategy option can be provided without an explanation as well
            }
        }
        return text(tacletExplanations.getProperty(category));
    }

    /** The option part of a choice named {@code category:option}. */
    private static String option(String choice) {
        // we should hava a getChoice method in Choice
        int colon = choice.indexOf(':');
        return colon < 0 ? choice : choice.substring(colon + 1);
    }

    /** A name as a label, without the colon KeY writes where it labels a row. */
    private static String label(String name) {
        String trimmed = text(name).trim();
        return trimmed.endsWith(":") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** Removes HTML tags and replaces a br-tag by a newline '\n'. */
    private static String text(String value) {
        return value == null ? ""
                : value.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "").trim();
    }
}
