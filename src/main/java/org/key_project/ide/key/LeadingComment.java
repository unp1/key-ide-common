/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Finds where a method's specification begins.
 * <p>
 * KeY records a method's position from its declaration, so the JML contract written above it
 * lies outside that range. Verification is often requested with the caret in the contract,
 * which a plain containment test resolves to nothing. The range a method occupies for
 * resolution therefore starts at the first line of the comment block attached to it.
 */
final class LeadingComment {

    private LeadingComment() {
    }

    /**
     * The first line of the comment block that precedes a declaration.
     *
     * @param file the source file
     * @param declarationLine the 1-based line the declaration starts on
     * @return the 1-based line resolution should treat as the start, which is
     *         {@code declarationLine} when nothing precedes it
     */
    static int startLineOf(Path file, int declarationLine) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return declarationLine;
        }
        if (declarationLine < 1 || declarationLine > lines.size()) {
            return declarationLine;
        }

        int start = declarationLine;
        int index = declarationLine - 2; // the line above the declaration, 0-based
        while (index >= 0) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("//")) {
                index--;
                continue;
            }
            if (line.endsWith("*/")) {
                int opening = openingLineOf(lines, index);
                if (opening < 0) {
                    break;
                }
                start = opening + 1;
                index = opening - 1;
                continue;
            }
            break;
        }
        return start;
    }

    /**
     * Walks up to the line opening a block comment.
     *
     * @param lines the file
     * @param closingIndex the 0-based line holding the closing delimiter
     * @return the 0-based line holding the opening delimiter, or -1 if there is none
     */
    private static int openingLineOf(List<String> lines, int closingIndex) {
        for (int index = closingIndex; index >= 0; index--) {
            if (lines.get(index).trim().startsWith("/*")) {
                return index;
            }
        }
        return -1;
    }
}
