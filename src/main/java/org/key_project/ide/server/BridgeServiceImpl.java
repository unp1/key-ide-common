/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.key_project.ide.protocol.BridgeService;
import org.key_project.ide.protocol.ConfigService;
import org.key_project.ide.protocol.Dtos.InitializeParams;
import org.key_project.ide.protocol.Dtos.InitializeResult;
import org.key_project.ide.protocol.VerificationService;

/**
 * Serves the protocol. Everything KeY-specific reaches it through the constructor, so the
 * protocol can be exercised without a KeY installation.
 */
public final class BridgeServiceImpl implements BridgeService {

    private final BridgeInfo info;
    private final BridgeSession session;
    private final ConfigService configService;
    private final VerificationService verificationService;
    private final Runnable shutdown;

    /**
     * @param info what the bridge reports about itself and its KeY
     * @param session where the project root is recorded
     * @param verificationService serves the verification half of the protocol, supplied from
     *        outside so this class stays free of KeY types
     * @param shutdown run when the IDE sends {@code exit}
     */
    public BridgeServiceImpl(BridgeInfo info, BridgeSession session,
            VerificationService verificationService, Runnable shutdown) {
        this.info = info;
        this.session = session;
        this.verificationService = verificationService;
        this.shutdown = shutdown;
        this.configService = new ConfigServiceImpl(session);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        session.initialize(Path.of(params.projectRoot()));
        return CompletableFuture.completedFuture(
            new InitializeResult(info.keyVersion(), info.keyJarSha256(), info.bridgeVersion(),
                info.protocolVersion(), info.capabilities()));
    }

    @Override
    public CompletableFuture<Boolean> ping() {
        // Answered from the message loop itself. Whatever the bridge is proving runs on
        // another thread, so an answer here says the bridge is still serving.
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    @Override
    public void exit() {
        shutdown.run();
    }

    @Override
    public ConfigService getConfigService() {
        return configService;
    }

    @Override
    public VerificationService getVerificationService() {
        return verificationService;
    }
}
