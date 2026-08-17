/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.key_project.ide.key.ProofObligations.Status;
import org.key_project.ide.protocol.Dtos.MarkDto;
import org.key_project.ide.protocol.Dtos.ObligationDto;

/**
 * What to mark in the margin of a source file.
 *
 * A method is marked by its weakest proof obligation, and a class by everything declared in
 * it. Every editor shows this, so the rule is decided once here rather than once per editor,
 * and a mark means the same thing wherever it is read.
 */
public final class SourceMarks {

    /** How far the proofs of one declaration have got. */
    public enum Mark {
        /** Every obligation is closed. */
        CLOSED,
        /** Every obligation is closed, some of them resting on contracts that are not. */
        LEMMAS_LEFT,
        /** An obligation has goals left. */
        OPEN,
        /** KeY has not judged every obligation, so how far the declaration has got is unknown. */
        UNJUDGED
    }

    private SourceMarks() {
    }

    /**
     * The lines to mark in one file.
     *
     * @param file the source file, absolute
     * @param projectRoot the root the obligations name their source file against
     * @param obligations everything the context can be asked to prove
     * @return the marks, ordered by line
     */
    public static List<MarkDto> of(Path file, Path projectRoot, List<ObligationDto> obligations) {
        Map<Integer, List<ObligationDto>> byLine = new LinkedHashMap<>();
        for (ObligationDto obligation : obligations) {
            if (!file.equals(projectRoot.resolve(obligation.sourceFile()).normalize())) {
                continue;
            }
            // The method the contract is about and the class it is declared in, so that the
            // mark beside the class stands for everything in it.
            for (int line : new int[] { obligation.targetLine(), obligation.classLine() }) {
                if (line > 0) {
                    byLine.computeIfAbsent(line, at -> new ArrayList<>()).add(obligation);
                }
            }
        }

        List<MarkDto> marks = new ArrayList<>();
        byLine.forEach((line, ofLine) -> marks.add(new MarkDto(line, markOf(ofLine).name(),
            tooltipOf(ofLine))));
        marks.sort((left, right) -> Integer.compare(left.line(), right.line()));
        return marks;
    }

    /**
     * The mark for a declaration whose obligations are in these states.
     *
     * @param obligations the obligations of one declaration
     * @return the mark it carries
     */
    public static Mark markOf(List<ObligationDto> obligations) {
        List<String> states = obligations.stream().map(ObligationDto::status).toList();
        if (states.isEmpty()) {
            return Mark.UNJUDGED;
        }
        if (states.contains(Status.OPEN.name())) {
            return Mark.OPEN;
        }
        if (states.contains(Status.NONE.name()) || states.contains(Status.SAVED.name())
                || states.contains(Status.UNKNOWN.name())) {
            return Mark.UNJUDGED;
        }
        if (states.contains(Status.CLOSED_BUT_LEMMAS_LEFT.name())) {
            return Mark.LEMMAS_LEFT;
        }
        return Mark.CLOSED;
    }

    /**
     * The sentence a mark shows when it is hovered.
     *
     * @param obligations the obligations of one declaration
     * @return the explanation of the one obligation, or how many of several are proved
     */
    private static String tooltipOf(List<ObligationDto> obligations) {
        if (obligations.size() == 1) {
            return obligations.get(0).statusExplanation();
        }
        long proved = obligations.stream().filter(SourceMarks::isClosed).count();
        return proved == obligations.size()
                ? obligations.size() + " proof obligations, all proved"
                : obligations.size() + " proof obligations, " + proved + " proved";
    }

    private static boolean isClosed(ObligationDto obligation) {
        return obligation.status().equals(Status.CLOSED.name())
                || obligation.status().equals(Status.CLOSED_BY_CACHE.name());
    }
}
