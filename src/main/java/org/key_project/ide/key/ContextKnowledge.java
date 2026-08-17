/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Encapsulate knowledge about the proofs of one context as provided by KeY.
 * <p>
 * This class does <strong>not</strong> analyse a proof by itself. The information whether
 * a proof is closed, or closed only but for lemmas is queries from KeY.
 * The reason this class exists is that KeY can only be asked while the proofs are available.
 * For memory reasons, these are disposed rather eagerly, so we cache the relevant information
 * in this class which can be queried by the IDE. The whole difficulty is knowing
 * when a kept answer might no longer be true.
 * <p>
 * It considers three kinds of information and gives one answer:
 * <ul>
 * <li>the proofs on disk have changed, which is told by whoever noticed;
 * <li>KeY has just judged some proofs, which a run or a replay reports;
 * <li>the context was loaded again, after which nothing said before applies.
 * </ul>
 * Asked for the state it brings itself up to date first: what a change may have invalidated
 * is dropped, and KeY is requeried.
 * <br>
 * Clients interested in the status of proof(s) can register and are informed about updates.
 */
public final class ContextKnowledge {

    /** Information about the status of a proof as provided by KeY. */
    public record Verdict(String contractName, String status, List<String> usedContracts) {

        public Verdict {
            usedContracts = usedContracts == null ? List.of() : List.copyOf(usedContracts);
        }
    }

    /**
     * Information about a proof obligation that is stored.
     *
     * @param contractName the obligation
     * @param proofFileExists whether a proof of it is saved
     * @param statusWithoutKey status information available without querying KeY, which is whether a file
     *        exists
     */
    public record OnDisk(String contractName, boolean proofFileExists, String statusWithoutKey) {
    }

    /**
     * Interface to the interface querying KeY about the status of a set of contracts which
     * must be load into one ProofEnvironment (such that proof dependency tracking works)
     */
    public interface Oracle {

        /**
         * Asks KeY about the saved proofs of these obligations, all at once.
         *
         * @param contractNames the obligations whose saved proofs to read
         * @return what KeY said about each proof it could read
         */
        List<Verdict> judge(List<String> contractNames);
    }

    /** The status KeY gives a proof that closes but rests on contracts that are not proved. */
    private static final String CLOSED_BUT_LEMMAS_LEFT =
        ProofObligations.Status.CLOSED_BUT_LEMMAS_LEFT.name();

    private final String contextId;
    private final Oracle oracle;

    /**
     * Notified in case of a status change
     *
     * <p>
     * There is only one listener given at creation time.
     */
    private final Consumer<String> changed;

    /** The proof status by contract. */
    private final Map<String, Verdict> known = new LinkedHashMap<>();

    /** The proof obligations whose status were saved when KeY said it, or null before anything. */
    private Set<String> judged;

    /**
     * The contracts whose cached status was invalidated and has to be requeried.
     * <p>
     * Invalidating is not discarding: a status invalidated because a proof file changed is
     * requeried on the next query, while the cache discarded on a context reload is not,
     * since refilling it means replaying every proof of the context.
     */
    private final Set<String> toRequery = new HashSet<>();

    /**
     * @param contextId the context this knowledge is about, passed to the listener
     * @param oracle queries KeY for the status of a set of contracts
     * @param changed notified whenever a cached status changes
     */
    public ContextKnowledge(String contextId, Oracle oracle, Consumer<String> changed) {
        this.contextId = contextId;
        this.oracle = oracle;
        this.changed = changed;
    }

    /**
     * The status of the given proof obligations, requeried from KeY where the cache is out
     * of date.
     *
     * @param obligations the proof obligations with their stored state
     * @return the status by contract: the cached status where KeY has provided one, the
     *         stored state otherwise
     */
    public Map<String, String> stateOf(List<OnDisk> obligations) {
        bringUpToDate(obligations);
        synchronized (this) {
            Map<String, String> state = new LinkedHashMap<>();
            for (OnDisk obligation : obligations) {
                Verdict verdict = known.get(obligation.contractName());
                state.put(obligation.contractName(),
                    verdict == null ? obligation.statusWithoutKey() : verdict.status());
            }
            return state;
        }
    }

    /**
     * The contracts each proof used, as reported by KeY, for the proofs KeY has judged.
     *
     * @return the used contracts by contract
     */
    public synchronized Map<String, List<String>> usedContracts() {
        Map<String, List<String>> used = new LinkedHashMap<>();
        known.forEach((contractName, verdict) -> used.put(contractName, verdict.usedContracts()));
        return used;
    }

    /**
     * Caches the status KeY has just provided, as reported by a run or a replay.
     *
     * @param verdicts the status KeY provided for the proofs it had loaded together
     * @param obligations the proof obligations with their stored state
     */
    public void keyJudged(List<Verdict> verdicts, List<OnDisk> obligations) {
        synchronized (this) {
            Set<String> judgedNow = new HashSet<>();
            verdicts.forEach(verdict -> {
                known.put(verdict.contractName(), verdict);
                judgedNow.add(verdict.contractName());
            });
            Set<String> savedNow = saved(obligations);
            // KeY judged these against each other only. A proof using one of them was not
            // loaded and may have a different status now, so its cached status is dropped.
            Set<String> invalidated = new HashSet<>(judgedNow);
            addDependents(invalidated);
            invalidated.removeAll(judgedNow);
            invalidate(invalidated, savedNow);
            requeryLemmasLeft(verdicts, judgedNow, savedNow);
            judged = savedNow;
        }
        notifyListener();
    }

    /**
     * Invalidates the status of proofs KeY called closed but for lemmas while it did not
     * hold every saved proof.
     * <p>
     * That status means the proof rests on contracts KeY held no closed proof of. A saved
     * proof KeY did not load may be such a proof, so the status stands only where KeY was
     * given every saved proof of the context. Where it was not, the status is requeried,
     * and the query loads all of them.
     *
     * @param verdicts the status KeY just provided
     * @param judgedNow the contracts KeY held, which are the ones it reported on
     * @param savedProofs the contracts with a saved proof
     */
    private void requeryLemmasLeft(List<Verdict> verdicts, Set<String> judgedNow,
            Set<String> savedProofs) {
        if (judgedNow.containsAll(savedProofs)) {
            return;
        }
        verdicts.stream().filter(verdict -> CLOSED_BUT_LEMMAS_LEFT.equals(verdict.status()))
                .map(Verdict::contractName).forEach(toRequery::add);
    }

    /**
     * Discards the whole cache because the context was loaded again.
     * <p>
     * A status obtained for one version of the sources says nothing about the same contract
     * in another. The cache is not refilled here: that means replaying every proof of the
     * context, which is the user's decision.
     */
    public void reloaded() {
        synchronized (this) {
            known.clear();
            toRequery.clear();
            judged = null;
        }
        notifyListener();
    }

    /**
     * Invalidates the cached status affected by added or removed proof files, and requeries
     * KeY for it.
     * <p>
     * Adding or removing a proof file invalidates the status of that contract, of every
     * contract whose proof used it, and of their users in turn. A proof that used none of
     * them was judged against an unchanged set of proofs, so its status still holds. The
     * dependencies are the ones KeY reported, so this decides which contracts to requery,
     * never what their status is.
     * <p>
     * KeY judges a proof against the proofs loaded with it, so the query covers every saved
     * proof of the context, not only the invalidated ones.
     *
     * @param obligations the proof obligations with their stored state
     */
    private void bringUpToDate(List<OnDisk> obligations) {
        List<String> query;
        synchronized (this) {
            Set<String> savedNow = saved(obligations);
            if (judged != null && !judged.equals(savedNow)) {
                Set<String> invalidated = changed(judged, savedNow);
                addDependents(invalidated);
                invalidate(invalidated, savedNow);
                judged = savedNow;
            }
            if (toRequery.isEmpty()) {
                return;
            }
            query = new ArrayList<>(savedNow);
        }

        // Outside the lock: the query loads proofs, and the listener may query the status.
        List<Verdict> verdicts = oracle.judge(query);
        synchronized (this) {
            verdicts.forEach(verdict -> known.put(verdict.contractName(), verdict));
            toRequery.clear();
        }
        notifyListener();
    }

    /**
     * Removes the cached status of the given contracts and notes which of them to requery.
     *
     * @param invalidated the contracts whose cached status no longer holds
     * @param savedProofs the contracts with a saved proof, the only ones KeY can be asked
     *        about
     */
    private void invalidate(Set<String> invalidated, Set<String> savedProofs) {
        invalidated.forEach(known::remove);
        invalidated.stream().filter(savedProofs::contains).forEach(toRequery::add);
    }

    /** The contracts with a saved proof. */
    private static Set<String> saved(List<OnDisk> obligations) {
        return obligations.stream().filter(OnDisk::proofFileExists).map(OnDisk::contractName)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** The proof obligations whose saved proof either was removed or freshly created. */
    private static Set<String> changed(Set<String> before, Set<String> now) {
        Set<String> changed = new HashSet<>(before);
        changed.removeAll(now);
        Set<String> appeared = new HashSet<>(now);
        appeared.removeAll(before);
        changed.addAll(appeared);
        return changed;
    }

    /** Adds every contract whose proof used one of these, transitively. */
    private void addDependents(Set<String> invalidated) {
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Verdict verdict : known.values()) {
                if (!invalidated.contains(verdict.contractName())
                        && verdict.usedContracts().stream().anyMatch(invalidated::contains)) {
                    invalidated.add(verdict.contractName());
                    grew = true;
                }
            }
        }
    }

    private void notifyListener() {
        changed.accept(contextId);
    }
}
