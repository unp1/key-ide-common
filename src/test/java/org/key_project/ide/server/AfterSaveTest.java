/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2,
 * or (at your option) any later version.
 * SPDX-License-Identifier: GPL-2.0-or-later */
package org.key_project.ide.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what a save asks to be proved.
 * <p>
 * Saving is not a request to prove the project, so what is attempted is exactly the proofs
 * the edit broke, the contracts it added, and the unproved contracts a closed-but-waiting
 * proof rests on, followed as far as they lead.
 */
class AfterSaveTest {

    @Test
    void aSavedProofThatNoLongerClosesIsBroken() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("stillFine", "CLOSED");
        status.put("cameApart", "OPEN");
        status.put("neverProved", "NONE");

        assertThat(AfterSave.broken(status)).containsExactly("cameApart");
    }

    @Test
    void aContractThatWasNeverProvedIsNotBroken() {
        assertThat(AfterSave.broken(Map.of("neverProved", "NONE"))).isEmpty();
    }

    @Test
    void followsTheLemmasAsFarAsTheyLead() {
        // top closes but waits for middle, middle's proof rests on bottom, and neither
        // middle nor bottom is proved: both are asked for, bottom through middle.
        Map<String, String> status = new LinkedHashMap<>();
        status.put("top", "CLOSED_BUT_LEMMAS_LEFT");
        status.put("middle", "NONE");
        status.put("bottom", "NONE");
        Map<String, List<String>> uses = Map.of(
            "top", List.of("middle"),
            "middle", List.of("bottom"));

        assertThat(AfterSave.lemmasLeft(status, uses)).containsExactly("middle", "bottom");
    }

    @Test
    void aProvedLemmaIsNotAskedForAgain() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("top", "CLOSED_BUT_LEMMAS_LEFT");
        status.put("provedLemma", "CLOSED");
        status.put("openLemma", "OPEN");

        List<String> asked = AfterSave.lemmasLeft(status,
            Map.of("top", List.of("provedLemma", "openLemma")));

        assertThat(asked).containsExactly("openLemma");
    }

    @Test
    void aCycleAmongLemmasEnds() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("top", "CLOSED_BUT_LEMMAS_LEFT");
        status.put("left", "NONE");
        status.put("right", "NONE");
        Map<String, List<String>> uses = Map.of(
            "top", List.of("left"),
            "left", List.of("right"),
            "right", List.of("left"));

        assertThat(AfterSave.lemmasLeft(status, uses)).containsExactly("left", "right");
    }

    @Test
    void aContractTheLastListingDidNotCarryIsAdded() {
        assertThat(AfterSave.added(Set.of("old"), Set.of("old", "fresh")))
                .containsExactly("fresh");
    }

    @Test
    void nothingCountsAsAddedWithoutAListingToCompareAgainst() {
        // Reading every unproved contract of a freshly opened project as new would prove
        // the whole project on the first save.
        assertThat(AfterSave.added(null, Set.of("one", "two"))).isEmpty();
    }
}
