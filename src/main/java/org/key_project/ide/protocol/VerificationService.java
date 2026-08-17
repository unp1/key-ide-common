/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.protocol;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

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
import org.key_project.ide.protocol.Dtos.ObligationsParams;
import org.key_project.ide.protocol.Dtos.PreparedResult;
import org.key_project.ide.protocol.Dtos.ProveParams;
import org.key_project.ide.protocol.Dtos.RemovedResult;
import org.key_project.ide.protocol.Dtos.ProveResult;
import org.key_project.ide.protocol.Dtos.ResolveParams;
import org.key_project.ide.protocol.Dtos.StaleOptionsResult;
import org.key_project.ide.protocol.Dtos.StartParams;

/**
 * Everything the IDE can ask about proofs: what a context can prove, the status of each
 * obligation, running and replaying proofs, and the options they are attempted with.
 */
public interface VerificationService {

    /**
     * Resolves the method a position is in, without opening anything.
     *
     * @param params the file and position
     * @return the method found
     */
    @JsonRequest("method/resolveAt")
    CompletableFuture<MethodDto> resolveAt(ResolveParams params);

    /**
     * Opens KeY's proof obligation browser with a method selected.
     * <p>
     * The browser is modal, so the answer arrives once it is on screen rather than when
     * the user closes it.
     *
     * @param params the method to select
     * @return completion, once the browser has been handed to the user interface
     */
    @JsonRequest("po/browse")
    CompletableFuture<Void> browse(BrowseParams params);

    /**
     * Resolves a position and opens the browser on what it finds, which is what the
     * right-click action calls.
     *
     * @param params the file and position
     * @return the method the browser was opened on
     */
    @JsonRequest("key/verifyAt")
    CompletableFuture<MethodDto> verifyAt(ResolveParams params);

    /**
     * Lists the proof obligations of a context with their status.
     *
     * @param params the context to list
     * @return the obligations
     */
    @JsonRequest("po/list")
    CompletableFuture<ObligationsResult> list(ListObligationsParams params);

    /**
     * Opens the proof obligation browser on one listed obligation.
     *
     * @param params the obligation to select
     * @return completion, once the browser has been handed to the user interface
     */
    @JsonRequest("po/start")
    CompletableFuture<Void> start(StartParams params);

    /**
     * What a position in a source file stands for.
     *
     * A caret inside a method means the contracts about that method, and a caret anywhere
     * else in the file means everything the file declares. Which method a caret sits in, and
     * which contracts are about it, both follow from what KeY loaded, so they are decided
     * here: an editor matching them by name would take a method for its overloads, and one
     * matching them by line would miss the targets KeY records no position for.
     *
     * @param params the file and the position in it
     * @return what the position stands for, empty when no context covers the file
     */
    @JsonRequest("po/at")
    CompletableFuture<PositionResult> at(PositionParams params);

    /**
     * What to mark in the margin of a source file, by line.
     *
     * A declaration is marked by its weakest proof obligation: one open proof marks the
     * method open, and it counts as proved only once every one of its obligations is closed.
     * A class carries the mark of everything declared in it. Every client shows this, and it
     * follows from the status KeY reports, so it is decided here and read the same way in
     * every editor.
     *
     * @param params the file to mark
     * @return the lines to mark, empty when no context covers the file
     */
    @JsonRequest("po/marks")
    CompletableFuture<MarksResult> marks(MarksParams params);

    /**
     * KeY's own status icons, so that a listing can show them without the plugin carrying
     * KeY's assets.
     *
     * @param params the size to draw them at
     * @return the icons, by status name
     */
    @JsonRequest("po/icons")
    CompletableFuture<IconsResult> icons(IconsParams params);

    /**
     * Attempts obligations with KeY's automatic strategy, without a user interface.
     * <p>
     * The answer arrives when the run ends. Progress is reported meanwhile, and the run can
     * be stopped, so the caller is not blocked.
     *
     * @param params the obligations to attempt
     * @return what came of each
     */
    @JsonRequest("po/prove")
    CompletableFuture<ProveResult> prove(ProveParams params);

    /**
     * Stops one run. The proofs it finished before the stop are kept, and other runs
     * continue.
     *
     * @param params the run to stop
     * @return completion, once the run has been asked to stop
     */
    @JsonRequest("po/cancel")
    CompletableFuture<Void> cancel(CancelParams params);

    /**
     * Reads saved proofs back and reports the status KeY gives them.
     * <p>
     * A saved proof file only shows that a proof was saved. Replaying establishes whether it
     * still closes against the sources as they are now.
     *
     * @param params the obligations whose proofs to read
     * @return the status of each proof
     */
    @JsonRequest("po/replay")
    CompletableFuture<ProveResult> replay(ObligationsParams params);

    /**
     * Deletes saved proofs.
     *
     * @param params the obligations whose proofs to remove
     * @return how many files were deleted
     */
    @JsonRequest("po/removeProof")
    CompletableFuture<RemovedResult> removeProof(ObligationsParams params);

    /**
     * Builds one proof with the settings configured for its obligation and saves it without
     * attempting it, so that a KeY window can open it.
     *
     * @param params the obligation to prepare
     * @return where the proof was saved
     */
    @JsonRequest("po/prepare")
    CompletableFuture<PreparedResult> prepare(StartParams params);

    /**
     * Which contracts the proofs of a context used, as KeY reported them.
     * <p>
     * One entry per obligation, with the contracts its proof used. Whether that forms a tree
     * or a cycle is for the caller to determine; this reports only what KeY reported.
     *
     * @param params the context to report on
     * @return what KeY reported about each obligation
     */
    @JsonRequest("po/dependencies")
    CompletableFuture<DependenciesResult> dependencies(ListObligationsParams params);

    /**
     * Names the obligations of a context that have settings configured but no longer exist.
     *
     * @param params the context to look through
     * @return the names, empty if every obligation with settings exists
     */
    @JsonRequest("options/stale")
    CompletableFuture<StaleOptionsResult> staleOptions(ListObligationsParams params);

    /**
     * Removes the settings of the obligations of a context that no longer exist.
     *
     * @param params the context to clean
     * @return the names whose settings were dropped
     */
    @JsonRequest("options/removeStale")
    CompletableFuture<StaleOptionsResult> removeStaleOptions(ListObligationsParams params);

    /**
     * The options a context offers, loading the context if it is not loaded yet.
     *
     * @param params the context to ask about
     * @return the taclet and strategy options, and the values used where no level configures
     *         one
     */
    @JsonRequest("options/available")
    CompletableFuture<AvailableOptionsDto> availableOptions(AvailableOptionsParams params);
}
