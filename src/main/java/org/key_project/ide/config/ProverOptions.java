/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

/**
 * Which prover the bridge runs proofs with.
 * <p>
 * Configured once per project and nowhere else. KeY selects the single-threaded or the
 * parallel prover from a setting that belongs to the prover rather than to a proof, so one
 * bridge has one selection: two contexts proving at the same time cannot differ in it.
 *
 * @param parallel whether to use the multi-core prover
 * @param threads how many workers it may use, or 0 to let KeY choose from the machine
 */
public record ProverOptions(boolean parallel, int threads) {

    /** KeY's own choice: the single-threaded prover. */
    public static final ProverOptions DEFAULT = new ProverOptions(false, 0);

    public ProverOptions {
        threads = Math.max(threads, 0);
    }
}
