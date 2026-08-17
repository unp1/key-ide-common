/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.util.concurrent.CompletableFuture;

import org.key_project.ide.protocol.Dtos.AvailableOptionsDto;
import org.key_project.ide.protocol.Dtos.AvailableOptionsParams;
import org.key_project.ide.protocol.Dtos.BrowseParams;
import org.key_project.ide.protocol.Dtos.CancelParams;
import org.key_project.ide.protocol.Dtos.DependenciesResult;
import org.key_project.ide.protocol.Dtos.IconsParams;
import org.key_project.ide.protocol.Dtos.IconsResult;
import org.key_project.ide.protocol.Dtos.ListObligationsParams;
import org.key_project.ide.protocol.Dtos.MarksParams;
import org.key_project.ide.protocol.Dtos.MarksResult;
import org.key_project.ide.protocol.Dtos.MethodDto;
import org.key_project.ide.protocol.Dtos.ObligationsResult;
import org.key_project.ide.protocol.Dtos.PositionParams;
import org.key_project.ide.protocol.Dtos.PositionResult;
import org.key_project.ide.protocol.Dtos.ResolveParams;
import org.key_project.ide.protocol.Dtos.ObligationsParams;
import org.key_project.ide.protocol.Dtos.PreparedResult;
import org.key_project.ide.protocol.Dtos.ProveParams;
import org.key_project.ide.protocol.Dtos.RemovedResult;
import org.key_project.ide.protocol.Dtos.ProveResult;
import org.key_project.ide.protocol.Dtos.StaleOptionsResult;
import org.key_project.ide.protocol.Dtos.StartParams;
import org.key_project.ide.protocol.VerificationService;

/**
 * Stands in for verification in a bridge that has no KeY.
 * <p>
 * The configuration half of the protocol needs nothing of KeY, so an IDE can serve its
 * settings form from a bridge that never starts a prover. Verification needs KeY, and this
 * reports that rather than failing obscurely.
 */
public final class UnavailableVerificationService implements VerificationService {

    @Override
    public CompletableFuture<MethodDto> resolveAt(ResolveParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<Void> browse(BrowseParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<MethodDto> verifyAt(ResolveParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<ObligationsResult> list(ListObligationsParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<Void> start(StartParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<MarksResult> marks(MarksParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<IconsResult> icons(IconsParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<PositionResult> at(PositionParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<ProveResult> prove(ProveParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<Void> cancel(CancelParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<ProveResult> replay(ObligationsParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<RemovedResult> removeProof(ObligationsParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<AvailableOptionsDto> availableOptions(AvailableOptionsParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<PreparedResult> prepare(StartParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<DependenciesResult> dependencies(ListObligationsParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<StaleOptionsResult> staleOptions(ListObligationsParams params) {
        throw unavailable();
    }

    @Override
    public CompletableFuture<StaleOptionsResult> removeStaleOptions(ListObligationsParams params) {
        throw unavailable();
    }

    private static RuntimeException unavailable() {
        return BridgeErrors.failure(BridgeErrors.VERIFICATION_UNAVAILABLE,
            "This bridge serves configuration only. Start KeY to verify.");
    }
}
