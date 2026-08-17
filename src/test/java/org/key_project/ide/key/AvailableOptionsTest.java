/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.List;

import de.uka.ilkd.key.proof.init.JavaProfile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.key_project.ide.protocol.Dtos.OptionCategoryDto;
import org.key_project.ide.protocol.Dtos.OptionValueDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers reading the strategy options out of the profile that describes them.
 * <p>
 * KeY describes them for its own settings dialog, and the bridge shows what that
 * description says rather than a wording of its own. These tests name options KeY has had
 * for a long time, so that a change in that description is reported here rather than by a
 * dialog that has quietly lost an option.
 */
class AvailableOptionsTest {

    /**
     * Sets logging up before any KeY class is touched, as the bridge does.
     * <p>
     * KeY's logback configuration installs a listener that asks KeY for its version, so
     * touching a KeY class first begins initialising that machinery from the middle and it
     * fails on itself.
     */
    @BeforeAll
    static void initialiseLogging() {
        org.slf4j.LoggerFactory.getLogger(AvailableOptionsTest.class);
    }

    private List<OptionCategoryDto> categories() {
        return AvailableOptions.strategyCategories(new JavaProfile());
    }

    private OptionCategoryDto category(String key) {
        return categories().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow(
            () -> new AssertionError("No strategy option is offered under the key " + key
                + ". The ones found are "
                + categories().stream().map(OptionCategoryDto::key).toList()));
    }

    @Test
    void theOptionsTheProfileDescribesAreOffered() {
        assertThat(categories()).extracting(OptionCategoryDto::key)
                .contains("SPLITTING_OPTIONS_KEY", "LOOP_OPTIONS_KEY", "METHOD_OPTIONS_KEY",
                    "OSS_OPTIONS_KEY", "NON_LIN_ARITH_OPTIONS_KEY");
    }

    @Test
    void anOptionOffersTheValuesItAccepts() {
        assertThat(category("SPLITTING_OPTIONS_KEY").values())
                .extracting(OptionValueDto::value)
                .containsExactlyInAnyOrder("SPLITTING_NORMAL", "SPLITTING_OFF",
                    "SPLITTING_DELAYED");
    }

    @Test
    void anOptionIsWordedAsKeyWordsIt() {
        assertThat(category("METHOD_OPTIONS_KEY").label()).isEqualTo("Method treatment");
        assertThat(category("SPLITTING_OPTIONS_KEY").label()).isEqualTo("Proof splitting");
        assertThat(category("NON_LIN_ARITH_OPTIONS_KEY").label())
                .isEqualTo("Arithmetic treatment");
    }

    @Test
    void aValueIsWordedAsKeyWordsIt() {
        assertThat(category("METHOD_OPTIONS_KEY").values()).extracting(OptionValueDto::label)
                .contains("Contract", "Expand", "None");
    }

    @Test
    void anOptionThatOnlyAppliesUnderAnotherIsOfferedInItsOwnRight() {
        // KeY shows "Expand local queries" underneath query treatment.
        assertThat(category("QUERYAXIOM_OPTIONS_KEY").values()).extracting(OptionValueDto::value)
                .containsExactlyInAnyOrder("QUERYAXIOM_ON", "QUERYAXIOM_OFF");
    }

    @Test
    void anOptionCarriesWhatItMeans() {
        assertThat(category("METHOD_OPTIONS_KEY").values())
                .allSatisfy(value -> assertThat(value.description()).isNotEmpty());
    }

    @Test
    void aLabelKeepsNoTrailingColon() {
        assertThat(categories()).allSatisfy(
            category -> assertThat(category.label()).doesNotEndWith(":"));
    }
}
