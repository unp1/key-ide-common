/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Describes the KeY the bridge is running inside.
 * <p>
 * Both values are diagnostics for bug reports rather than gates. The version cannot
 * identify a build, since every build off KeY's main branch reports the same string, and
 * the digest is only meaningful when KeY was started from a jar.
 * <p>
 * Everything here goes through reflection, so that this class can be loaded and tested
 * with no KeY on the classpath.
 */
public final class KeyInstallation {

    /** Reported when KeY does not say, so callers never have to handle a null. */
    public static final String UNKNOWN = "unknown";

    private KeyInstallation() {
    }

    /**
     * The version KeY reports, including its internal revision.
     *
     * @return the version, or {@code unknown} if KeY is absent or silent
     */
    public static String version() {
        try {
            Class<?> constants = Class.forName("de.uka.ilkd.key.util.KeYConstants");
            Object version = constants.getField("VERSION").get(null);
            return version == null ? UNKNOWN : version.toString();
        } catch (ReflectiveOperationException | LinkageError e) {
            // The version is a diagnostic only, so a failure to read it, including one
            // caused by KeY's initialisation order, must not fail the startup.
            return UNKNOWN;
        }
    }

    /**
     * The digest of the jar KeY was loaded from, identifying a build where the version
     * cannot.
     *
     * @return the hex digest, or an empty string when KeY runs from loose classes
     */
    public static String jarSha256() {
        Path jar = jarLocation();
        if (jar == null) {
            return "";
        }
        try (InputStream stream = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * The jar holding KeY's user interface.
     *
     * @return the jar, or {@code null} when KeY is absent or not packaged as one
     */
    private static Path jarLocation() {
        try {
            Class<?> owner = Class.forName("de.uka.ilkd.key.gui.ProofManagementDialog");
            CodeSource source = owner.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Path.of(source.getLocation().toURI());
            return Files.isRegularFile(path) ? path : null;
        } catch (ReflectiveOperationException | java.net.URISyntaxException
                | IllegalArgumentException | LinkageError e) {
            return null;
        }
    }
}
