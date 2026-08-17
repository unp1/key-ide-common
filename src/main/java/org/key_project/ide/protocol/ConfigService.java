/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.protocol;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

import org.key_project.ide.protocol.Dtos.ContextAtParams;
import org.key_project.ide.protocol.Dtos.ContextAtResult;
import org.key_project.ide.protocol.Dtos.ProjectConfigDto;
import org.key_project.ide.protocol.Dtos.PrunedResult;
import org.key_project.ide.protocol.Dtos.SetOptionsParams;
import org.key_project.ide.protocol.Dtos.SetProverParams;
import org.key_project.ide.protocol.Dtos.TrashPolicyDto;
import org.key_project.ide.protocol.Dtos.ValidateParams;
import org.key_project.ide.protocol.Dtos.ValidateResult;

/**
 * Reads and writes the project's {@code .key/settings.json}.
 * <p>
 * The bridge owns the file so the schema has one implementation rather than one per IDE.
 * Clients render a form over these calls.
 */
@JsonSegment("config")
public interface ConfigService {

    /**
     * Reads the configuration, returning an empty one when the project has no file yet.
     *
     * @return the configuration as stored
     */
    @JsonRequest
    CompletableFuture<ProjectConfigDto> get();

    /**
     * Replaces the configuration, creating the file and its directory if needed.
     *
     * @param config the configuration to store
     * @return completion, once the file is written
     */
    @JsonRequest
    CompletableFuture<Void> set(ProjectConfigDto config);

    /**
     * Checks paths against the rules KeY imposes, without loading anything.
     *
     * @param params the context to check, or all of them
     * @return the problems found
     */
    @JsonRequest
    CompletableFuture<ValidateResult> validate(ValidateParams params);

    /**
     * The context whose sources hold a file, preferring the most specific one.
     * <p>
     * Every client needs this to act on the file being edited, and the answer follows from
     * the source directories in the configuration, which this bridge already owns.
     *
     * @param params the file to place
     * @return the context, or one naming no context when none covers the file
     */
    @JsonRequest
    CompletableFuture<ContextAtResult> contextAt(ContextAtParams params);

    /**
     * Changes the settings configured at one level, keeping the fields the form did not
     * change.
     *
     * @param params the level to change, and the fields the form changed
     * @return the configuration after the change
     */
    @JsonRequest
    CompletableFuture<ProjectConfigDto> setOptions(SetOptionsParams params);

    /**
     * Sets which prover runs the proofs.
     *
     * @param params the prover to use
     * @return the configuration after the change
     */
    @JsonRequest
    CompletableFuture<ProjectConfigDto> setProver(SetProverParams params);

    /**
     * Deletes the replaced proofs the policy no longer keeps.
     * <p>
     * The policy is the user's rather than the project's, so it arrives with the request
     * rather than being read from the project's file.
     *
     * @param policy how the trash is kept
     * @return what was deleted
     */
    @JsonRequest
    CompletableFuture<PrunedResult> pruneTrash(TrashPolicyDto policy);
}
