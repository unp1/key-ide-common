/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.key_project.ide.config.ConfigProblem;
import org.key_project.ide.config.ConfigStore;
import org.key_project.ide.config.ConfigValidator;
import org.key_project.ide.config.ProjectConfig;
import org.key_project.ide.config.VerificationContext;
import org.key_project.ide.key.ProofTrash;
import org.key_project.ide.protocol.ConfigService;
import org.key_project.ide.protocol.Dtos.ContextAtParams;
import org.key_project.ide.protocol.Dtos.ContextAtResult;
import org.key_project.ide.protocol.Dtos.ProjectConfigDto;
import org.key_project.ide.protocol.Dtos.PrunedResult;
import org.key_project.ide.protocol.Dtos.SetOptionsParams;
import org.key_project.ide.protocol.Dtos.SetProverParams;
import org.key_project.ide.protocol.Dtos.TrashPolicyDto;
import org.key_project.ide.protocol.Dtos.ValidateParams;
import org.key_project.ide.protocol.Dtos.ValidateResult;
import org.key_project.ide.protocol.ProtocolMapper;

/**
 * Serves the configuration half of the protocol from the project's settings file.
 */
public final class ConfigServiceImpl implements ConfigService {

    private final BridgeSession session;
    private final ConfigValidator validator = new ConfigValidator();

    public ConfigServiceImpl(BridgeSession session) {
        this.session = session;
    }

    @Override
    public CompletableFuture<ProjectConfigDto> get() {
        return CompletableFuture.completedFuture(ProtocolMapper.toDto(read()));
    }

    @Override
    public CompletableFuture<Void> set(ProjectConfigDto config) {
        try {
            store().write(ProtocolMapper.toModel(config));
        } catch (IOException e) {
            throw BridgeErrors.failure(BridgeErrors.CONFIG_UNREADABLE, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<ContextAtResult> contextAt(ContextAtParams params) {
        Path file = fileOf(params.uri());
        Path root = session.projectRoot();
        String best = null;
        int bestDepth = -1;
        for (VerificationContext context : read().contexts()) {
            Path source = root.resolve(context.javaSource()).toAbsolutePath().normalize();
            if (file.startsWith(source) && source.getNameCount() > bestDepth) {
                best = context.id();
                bestDepth = source.getNameCount();
            }
        }
        return CompletableFuture.completedFuture(new ContextAtResult(best));
    }

    /** A file named either as a {@code file:} URI or as a path. */
    private static Path fileOf(String uri) {
        try {
            return Path.of(URI.create(uri)).toAbsolutePath().normalize();
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            return Path.of(uri).toAbsolutePath().normalize();
        }
    }

    @Override
    public CompletableFuture<ValidateResult> validate(ValidateParams params) {
        ProjectConfig config = read();
        Path root = session.projectRoot();
        String contextId = params == null ? null : params.contextId();

        List<ConfigProblem> problems = contextId == null
                ? validator.validate(config, root)
                : validator.validate(context(config, contextId).resolveAgainst(root));

        return CompletableFuture.completedFuture(
            new ValidateResult(problems.stream().map(ProtocolMapper::toDto).toList()));
    }

    @Override
    public CompletableFuture<ProjectConfigDto> setOptions(SetOptionsParams params) {
        ProjectConfig config = read();
        String contextId = params.contextId();
        if (contextId != null) {
            // Fails here rather than writing settings for a context that does not exist.
            context(config, contextId);
        }
        return write(config.withOptions(contextId, params.contractNames(),
            ProtocolMapper.toModel(params.change())));
    }

    @Override
    public CompletableFuture<ProjectConfigDto> setProver(SetProverParams params) {
        return write(read().withProver(ProtocolMapper.toModel(params.prover())));
    }

    @Override
    public CompletableFuture<PrunedResult> pruneTrash(TrashPolicyDto policy) {
        Path proofsRoot = session.projectRoot().resolve(read().proofDirectory());
        try {
            ProofTrash.Pruned pruned = switch (policy.mode() == null ? "NEVER" : policy.mode()) {
                case "EMPTY" -> ProofTrash.empty(proofsRoot);
                case "BELOW_SIZE" -> ProofTrash.keepBelow(proofsRoot,
                    Math.max(0L, policy.megabytes()) * 1024L * 1024L);
                case "OLDER_THAN" -> ProofTrash.dropOlderThan(proofsRoot,
                    Math.max(0, policy.days()));
                default -> new ProofTrash.Pruned(0, 0);
            };
            return CompletableFuture.completedFuture(
                new PrunedResult(pruned.files(), pruned.bytes()));
        } catch (IOException e) {
            throw BridgeErrors.failure(BridgeErrors.CONFIG_UNREADABLE,
                "The trash could not be pruned: " + e.getMessage());
        }
    }

    /**
     * Stores an edited configuration and returns it.
     *
     * @param config the configuration to store
     * @return the same configuration, so that a form can show the result without reading the
     *         file again
     */
    private CompletableFuture<ProjectConfigDto> write(ProjectConfig config) {
        try {
            store().write(config);
        } catch (IOException e) {
            throw BridgeErrors.failure(BridgeErrors.CONFIG_UNREADABLE, e.getMessage());
        }
        return CompletableFuture.completedFuture(ProtocolMapper.toDto(config));
    }

    private VerificationContext context(ProjectConfig config, String contextId) {
        return config.context(contextId)
                .orElseThrow(() -> BridgeErrors.failure(BridgeErrors.UNKNOWN_CONTEXT,
                    "The project declares no context with the id '" + contextId + "'."));
    }

    private ProjectConfig read() {
        try {
            return store().read();
        } catch (IOException e) {
            throw BridgeErrors.failure(BridgeErrors.CONFIG_UNREADABLE, e.getMessage());
        }
    }

    private ConfigStore store() {
        return new ConfigStore(session.projectRoot());
    }
}
