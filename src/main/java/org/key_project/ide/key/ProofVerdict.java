/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.List;

import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.mgt.ProofStatus;
import de.uka.ilkd.key.speclang.Contract;

/**
 * Queries KeY for the status of a loaded proof and for the contracts it used.
 * <p>
 * Neither is determined here. KeY can answer only while the proof is loaded, and its answer
 * depends on the other proofs loaded with it: whether a proof is closed or closed only but
 * for lemmas follows from the proofs in the same specification repository.
 */
public final class ProofVerdict {

    private ProofVerdict() {
    }

    /**
     * The status of the proof as reported by KeY.
     *
     * @param proof a loaded, undisposed proof
     * @return the status, {@code UNKNOWN} if KeY reports none
     */
    public static ProofObligations.Status statusOf(Proof proof) {
        ProofStatus status = proof.mgt().getStatus();
        if (status == null) {
            return ProofObligations.Status.UNKNOWN;
        }
        if (status.getProofClosed()) {
            return ProofObligations.Status.CLOSED;
        }
        if (status.getProofOpen()) {
            return ProofObligations.Status.OPEN;
        }
        if (status.getProofClosedButLemmasLeft()) {
            return ProofObligations.Status.CLOSED_BUT_LEMMAS_LEFT;
        }
        if (status.getProofClosedByCache()) {
            return ProofObligations.Status.CLOSED_BY_CACHE;
        }
        return ProofObligations.Status.UNKNOWN;
    }

    /**
     * The contracts the proof used, as reported by KeY.
     *
     * @param proof a loaded, undisposed proof
     * @return the contract names, sorted
     */
    public static List<String> usedContracts(Proof proof) {
        return proof.mgt().getUsedContracts().stream().map(Contract::getName).sorted().toList();
    }
}
