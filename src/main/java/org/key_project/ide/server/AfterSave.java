/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2,
 * or (at your option) any later version.
 * SPDX-License-Identifier: GPL-2.0-or-later */
package org.key_project.ide.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.key_project.ide.key.ProofObligations.Status;

/**
 * What a save asks to be proved.
 * <p>
 * Saving a source is not a request to prove the project: a contract that was never proved
 * stays untouched. What the save does ask for is what the edit broke, a saved proof that no
 * longer closes; what the edit added, a contract the last listing did not carry; and the
 * contracts a closed-but-waiting proof rests on, followed as far as they lead, since a lemma
 * may rest on lemmas of its own.
 * <p>
 * This is decided here rather than in each editor, so that saving means the same wherever
 * the save was made.
 */
final class AfterSave {

    private AfterSave() {
    }

    /**
     * The proofs the edit broke: saved, and no longer closing.
     *
     * @param status the state of every obligation, by contract name
     * @return the contract names, in the order given
     */
    static List<String> broken(Map<String, String> status) {
        List<String> names = new ArrayList<>();
        status.forEach((name, state) -> {
            if (!isClosed(state) && !Status.NONE.name().equals(state)
                    && !Status.CLOSED_BUT_LEMMAS_LEFT.name().equals(state)) {
                names.add(name);
            }
        });
        return names;
    }

    /**
     * The contracts the closed-but-waiting proofs rest on that are not proved themselves.
     *
     * @param status the state of every obligation, by contract name
     * @param uses which contracts each obligation's proof used
     * @return the unproved contracts, in the order they were reached
     */
    static List<String> lemmasLeft(Map<String, String> status, Map<String, List<String>> uses) {
        List<String> waiting = status.entrySet().stream()
                .filter(entry -> Status.CLOSED_BUT_LEMMAS_LEFT.name().equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        Set<String> unproved = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>(waiting);
        Deque<String> pending = new ArrayDeque<>(waiting);
        while (!pending.isEmpty()) {
            for (String used : uses.getOrDefault(pending.removeFirst(), List.of())) {
                if (!seen.add(used) || isClosed(status.getOrDefault(used, Status.NONE.name()))) {
                    continue;
                }
                unproved.add(used);
                pending.addLast(used);
            }
        }
        return List.copyOf(unproved);
    }

    /**
     * The contracts the last listing did not carry.
     * <p>
     * With no earlier listing to compare against, nothing counts as added: reading every
     * unproved contract of a freshly opened project as new would prove the whole project on
     * the first save.
     *
     * @param before the contract names the context carried before, or {@code null} when it
     *        was never listed
     * @param current the contract names it carries now
     * @return the names that are new, in the order given
     */
    static List<String> added(Set<String> before, Set<String> current) {
        if (before == null) {
            return List.of();
        }
        return current.stream().filter(name -> !before.contains(name)).toList();
    }

    private static boolean isClosed(String status) {
        return Status.CLOSED.name().equals(status) || Status.CLOSED_BY_CACHE.name().equals(status);
    }
}
