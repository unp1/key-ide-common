/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.time.Duration;
import java.time.Instant;

import de.uka.ilkd.key.proof.init.InitConfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers reading KeY's options without a project.
 * <p>
 * A settings page offers these before any context is declared, and must not wait for a
 * project to be parsed to do it.
 */
class RuleBaseTest {

    @Test
    void readsTheOptionsWithoutBeingGivenAnyJava() throws Exception {
        InitConfig config = RuleBase.initConfig();

        assertThat(AvailableOptions.of(config, java.util.Map.of()).taclet())
                .isNotEmpty()
                .anySatisfy(category -> assertThat(category.key()).isEqualTo("javaLoopTreatment"));
        assertThat(AvailableOptions.strategyCategories(config.getProfile())).isNotEmpty();
    }

    @Test
    void keepsWhatItReadRatherThanReadingAgain() throws Exception {
        RuleBase.initConfig();

        Instant before = Instant.now();
        InitConfig again = RuleBase.initConfig();
        Duration second = Duration.between(before, Instant.now());

        assertThat(again).isNotNull();
        // Reading KeY's rules takes seconds; answering from what was read takes none.
        assertThat(second).isLessThan(Duration.ofMillis(500));
    }

    @Test
    void offersTheChoicesKeyWouldUseByDefault() throws Exception {
        InitConfig config = RuleBase.initConfig();

        assertThat(AvailableOptions.chosen(config))
                .isNotEmpty()
                .containsKey("javaLoopTreatment");
    }
}
