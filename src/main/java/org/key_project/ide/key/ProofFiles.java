/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.net.URI;
import java.nio.file.Path;

import de.uka.ilkd.key.util.MiscTools;

import org.key_project.ide.config.ProjectConfig;

/**
 * The layout of a project's proof directory.
 * <p>
 * Proofs are stored in one directory of the project, below a directory per context, and
 * from there they mirror the source tree. The mirroring follows the file the type is
 * declared in, so a nested type is stored beside its own file.
 * <p>
 * The file name is the one KeY proposes when saving, so a proof saved from KeY's dialog is
 * found here.
 *
 * @param projectRoot the project
 * @param directory the directory holding the proofs, relative to the project root
 */
public record ProofFiles(Path projectRoot, String directory) {

    public ProofFiles {
        directory = directory == null || directory.isBlank()
                ? ProjectConfig.DEFAULT_PROOF_DIRECTORY
                : directory;
    }

    /**
     * The layout of a project that uses the default proof directory.
     *
     * @param projectRoot the project
     * @return the layout
     */
    public static ProofFiles under(Path projectRoot) {
        return new ProofFiles(projectRoot, ProjectConfig.DEFAULT_PROOF_DIRECTORY);
    }

    /**
     * The directory that contains all proofs of the project.
     *
     * @return the absolute path, which does not have to exist yet
     */
    public Path root() {
        return projectRoot.resolve(directory);
    }

    /**
     * The file a contract's proof is expected in.
     *
     * @param contextId the context the contract belongs to
     * @param javaSource the context's source directory, which the layout mirrors
     * @param typeFile the file the contract's type is declared in
     * @param contractName the contract's name, as KeY reports it
     * @return the expected path, which need not exist
     */
    public Path expectedFile(String contextId, Path javaSource, URI typeFile,
            String contractName) {
        Path proofs = root().resolve(contextId);
        Path packageDirectories = packageDirectoriesOf(javaSource, typeFile);
        Path parent = packageDirectories == null ? proofs : proofs.resolve(packageDirectories);
        return parent.resolve(fileNameOf(contractName));
    }

    /**
     * The name KeY proposes for a proof of this contract.
     *
     * @param contractName the contract's name
     * @return the file name, including the extension
     */
    public static String fileNameOf(String contractName) {
        return MiscTools.toValidFileName(contractName) + ".proof";
    }

    /**
     * The directories between a source root and a type's file.
     *
     * @return the relative directories, or {@code null} when the file lies outside the
     *         source root or its location is unknown
     */
    private static Path packageDirectoriesOf(Path javaSource, URI typeFile) {
        if (typeFile == null || !"file".equals(typeFile.getScheme())) {
            return null;
        }
        try {
            Path file = Path.of(typeFile).toAbsolutePath().normalize();
            Path root = javaSource.toAbsolutePath().normalize();
            if (!file.startsWith(root)) {
                return null;
            }
            Path relative = root.relativize(file).getParent();
            return relative == null || relative.toString().isEmpty() ? null : relative;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
