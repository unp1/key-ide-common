/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.util.List;

/**
 * What the bridge reports about itself and the KeY it loaded.
 * <p>
 * The KeY details are diagnostics for bug reports, not a gate: every build off KeY's main
 * branch reports the same version, and locally built jars carry an empty internal
 * revision. Whether the bridge can drive a given KeY is decided by probing for the API
 * members it uses, not by comparing these strings.
 *
 * @param keyVersion the version KeY reports
 * @param keyJarSha256 identifies the jar the bridge is running against
 * @param bridgeVersion the version of this bridge
 * @param protocolVersion the protocol version the bridge speaks
 * @param capabilities the operations this bridge supports
 */
public record BridgeInfo(String keyVersion, String keyJarSha256, String bridgeVersion,
        int protocolVersion, List<String> capabilities) {

    /** The protocol version this implementation speaks. */
    public static final int PROTOCOL_VERSION = 1;

    public BridgeInfo {
        capabilities = List.copyOf(capabilities);
    }
}
