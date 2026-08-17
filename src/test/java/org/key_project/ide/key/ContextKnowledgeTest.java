/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.key_project.ide.key.ContextKnowledge.OnDisk;
import org.key_project.ide.key.ContextKnowledge.Verdict;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the model of what KeY says about a context: when a kept answer still holds, when
 * it is dropped, and when KeY is asked again.
 * <p>
 * KeY is replaced by a stub that answers from a table, which is what makes these cases
 * testable at all: every one of them is a question about bookkeeping, and running a prover
 * to ask it would leave the bookkeeping untested for everything but the one path a proof
 * happens to take. The stub also counts what it was asked, so that asking too often or too
 * seldom is a failure rather than a matter of taste.
 */
class ContextKnowledgeTest {

    private static final String CLOSED = "CLOSED";
    private static final String LEMMAS_LEFT = "CLOSED_BUT_LEMMAS_LEFT";
    private static final String SAVED = "SAVED";
    private static final String NONE = "NONE";

    /**
     * KeY, as far as this test is concerned.
     * <p>
     * It is told which contract uses which, and which proofs are saved, and answers the
     * way KeY answers: a proof whose used contracts all have closed proofs among those it
     * was handed is closed, and one whose used contract is missing or open is closed only
     * but for lemmas.
     */
    private static final class StubKey implements ContextKnowledge.Oracle {

        private final Map<String, List<String>> uses = new LinkedHashMap<>();
        private final Set<String> saved = new LinkedHashSet<>();
        private final List<List<String>> asked = new ArrayList<>();

        void uses(String contractName, String... used) {
            uses.put(contractName, List.of(used));
        }

        void save(String... contractNames) {
            saved.addAll(List.of(contractNames));
        }

        void remove(String contractName) {
            saved.remove(contractName);
        }

        @Override
        public List<Verdict> judge(List<String> contractNames) {
            asked.add(List.copyOf(contractNames));
            List<Verdict> said = new ArrayList<>();
            for (String contractName : contractNames) {
                if (saved.contains(contractName)) {
                    said.add(new Verdict(contractName, statusOf(contractName),
                        uses.getOrDefault(contractName, List.of())));
                }
            }
            return said;
        }

        /** Closed when everything it used has a saved proof that is itself closed. */
        private String statusOf(String contractName) {
            return uses.getOrDefault(contractName, List.of()).stream()
                    .allMatch(used -> saved.contains(used) && CLOSED.equals(statusOf(used)))
                            ? CLOSED
                            : LEMMAS_LEFT;
        }

        List<OnDisk> onDisk(String... contractNames) {
            List<OnDisk> obligations = new ArrayList<>();
            for (String contractName : contractNames) {
                boolean exists = saved.contains(contractName);
                obligations.add(new OnDisk(contractName, exists, exists ? SAVED : NONE));
            }
            return obligations;
        }
    }

    private StubKey key;
    private ContextKnowledge knowledge;
    private final List<String> told = new ArrayList<>();

    @BeforeEach
    void createKnowledge() {
        key = new StubKey();
        knowledge = new ContextKnowledge("core", key, told::add);
        key.uses("contains", "indexOf");
    }

    /** What a run or a replay reports: KeY judged these, with the files as they are. */
    private void keyJudged(String... contractNames) {
        knowledge.keyJudged(key.judge(List.of(contractNames)),
            key.onDisk("contains", "indexOf", "max"));
    }

    private Map<String, String> state() {
        return knowledge.stateOf(key.onDisk("contains", "indexOf", "max"));
    }

    @Test
    void anObligationNobodyHasProvedIsWhatTheFileSystemSays() {
        assertThat(state()).containsEntry("contains", NONE).containsEntry("max", NONE);
    }

    @Test
    void whatKeySaidIsWhatIsReported() {
        key.save("contains");
        keyJudged("contains");

        assertThat(state()).containsEntry("contains", LEMMAS_LEFT);
    }

    @Test
    void aVerdictStandsWhileTheProofsItWasAboutStand() {
        key.save("contains", "indexOf");
        keyJudged("contains", "indexOf");
        int askedSoFar = key.asked.size();

        assertThat(state()).containsEntry("contains", CLOSED);
        assertThat(state()).containsEntry("contains", CLOSED);

        assertThat(key.asked).as("nothing changed, so KeY is not asked again")
                .hasSize(askedSoFar);
    }

    @Test
    void removingAProofUnsettlesWhatUsedItAndKeyIsAskedAgain() {
        key.save("contains", "indexOf", "max");
        keyJudged("contains", "indexOf", "max");
        assertThat(state()).containsEntry("contains", CLOSED).containsEntry("max", CLOSED);

        key.remove("indexOf");

        Map<String, String> state = state();
        assertThat(state).as("KeY is asked and says what the proof is worth now")
                .containsEntry("contains", LEMMAS_LEFT);
        assertThat(state).containsEntry("indexOf", NONE);
        assertThat(state).as("max used nothing that changed").containsEntry("max", CLOSED);
    }

    @Test
    void provingWhatWasMissingClosesTheProofThatUsedIt() {
        key.save("contains");
        keyJudged("contains");
        assertThat(state()).containsEntry("contains", LEMMAS_LEFT);

        // A later run proves the lemma. KeY judges both, since both are in front of it.
        key.save("indexOf");
        keyJudged("contains", "indexOf");

        assertThat(state()).containsEntry("contains", CLOSED).containsEntry("indexOf", CLOSED);
    }

    @Test
    void provingOnlyTheLemmaStillClosesTheProofThatUsedIt() {
        key.save("contains");
        keyJudged("contains");
        assertThat(state()).containsEntry("contains", LEMMAS_LEFT);

        // The later run holds the lemma alone, so KeY says nothing about contains then.
        // What it said before no longer holds, and it is asked again.
        key.save("indexOf");
        keyJudged("indexOf");

        assertThat(state()).containsEntry("contains", CLOSED);
    }

    @Test
    void aProofThatAppearsUnsettlesWhatCouldUseIt() {
        key.save("contains");
        keyJudged("contains");

        // The lemma's proof appears from elsewhere: a checkout, a copy, another window.
        key.save("indexOf");

        assertThat(state()).as("KeY is asked, and says both are closed")
                .containsEntry("contains", CLOSED)
                .containsEntry("indexOf", CLOSED);
    }

    @Test
    void aReloadedContextKnowsNothingUntilKeyIsAskedAgain() {
        key.save("contains", "indexOf");
        keyJudged("contains", "indexOf");
        assertThat(state()).containsEntry("contains", CLOSED);

        knowledge.reloaded();

        assertThat(state()).as("a proof of another load of the sources says nothing here")
                .containsEntry("contains", SAVED)
                .containsEntry("indexOf", SAVED);
    }

    @Test
    void nothingIsClosedWhileSomethingItUsedIsNot() {
        // The invariant the whole model exists for, checked after each kind of news.
        key.save("contains", "indexOf", "max");
        keyJudged("contains", "indexOf", "max");
        assertConsistent();

        key.remove("indexOf");
        assertConsistent();

        key.save("indexOf");
        assertConsistent();

        knowledge.reloaded();
        assertConsistent();
    }

    @Test
    void subscribersHearWhenTheAnswerChanges() {
        key.save("contains", "indexOf");
        keyJudged("contains", "indexOf");
        told.clear();

        key.remove("indexOf");
        state();

        assertThat(told).as("the views are told, rather than finding out by asking")
                .contains("core");
    }

    /** No obligation may be reported closed while a contract it used is not. */
    private void assertConsistent() {
        Map<String, String> state = state();
        Map<String, List<String>> used = knowledge.usedContracts();
        state.forEach((contractName, status) -> {
            if (CLOSED.equals(status)) {
                used.getOrDefault(contractName, List.of()).forEach(usedContract ->
                    assertThat(state.get(usedContract))
                            .as("%s is closed, so %s must be too", contractName, usedContract)
                            .isEqualTo(CLOSED));
            }
        });
    }
}
