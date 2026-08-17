/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.key_project.ide.key.ProofObligations.Status;
import org.key_project.ide.protocol.Dtos.MarkDto;
import org.key_project.ide.protocol.Dtos.ObligationDto;
import org.key_project.ide.server.SourceMarks.Mark;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the mark a margin shows for a declaration.
 * <p>
 * One line stands for every obligation of a method, and a class line for every obligation of
 * the class, so the mark has to say how far the weakest of them has got.
 */
class SourceMarksTest {

    private static final Path ROOT = Path.of("/project");

    @Test
    void aDeclarationWhoseObligationsAreAllClosedIsMarkedClosed() {
        assertThat(SourceMarks.markOf(List.of(obligation(Status.CLOSED, 10, 4),
            obligation(Status.CLOSED_BY_CACHE, 10, 4)))).isEqualTo(Mark.CLOSED);
    }

    @Test
    void oneObligationRestingOnALemmaMarksTheDeclaration() {
        assertThat(SourceMarks.markOf(List.of(obligation(Status.CLOSED, 10, 4),
            obligation(Status.CLOSED_BUT_LEMMAS_LEFT, 10, 4)))).isEqualTo(Mark.LEMMAS_LEFT);
    }

    @Test
    void oneOpenObligationMarksTheDeclarationOpen() {
        assertThat(SourceMarks.markOf(List.of(obligation(Status.CLOSED, 10, 4),
            obligation(Status.OPEN, 10, 4)))).isEqualTo(Mark.OPEN);
    }

    @Test
    void anObligationKeyHasNotJudgedLeavesTheDeclarationUnjudged() {
        assertThat(SourceMarks.markOf(List.of(obligation(Status.CLOSED, 10, 4),
            obligation(Status.NONE, 10, 4)))).isEqualTo(Mark.UNJUDGED);
        assertThat(SourceMarks.markOf(List.of(obligation(Status.SAVED, 10, 4))))
                .isEqualTo(Mark.UNJUDGED);
        assertThat(SourceMarks.markOf(List.of(obligation(Status.UNKNOWN, 10, 4))))
                .isEqualTo(Mark.UNJUDGED);
    }

    @Test
    void anOpenProofIsReportedEvenWhereAnotherWasNeverAttempted() {
        assertThat(SourceMarks.markOf(List.of(obligation(Status.NONE, 10, 4),
            obligation(Status.OPEN, 10, 4)))).isEqualTo(Mark.OPEN);
    }

    @Test
    void theMethodAndItsClassAreBothMarked() {
        List<MarkDto> marks = SourceMarks.of(ROOT.resolve("src/Account.java"), ROOT,
            List.of(obligation(Status.CLOSED, 27, 9)));

        assertThat(marks).extracting(MarkDto::line).containsExactly(9, 27);
        assertThat(marks).extracting(MarkDto::mark).containsOnly(Mark.CLOSED.name());
    }

    @Test
    void aClassCarriesTheWeakestMarkOfWhatIsDeclaredInIt() {
        List<MarkDto> marks = SourceMarks.of(ROOT.resolve("src/Account.java"), ROOT,
            List.of(obligation(Status.CLOSED, 27, 9), obligation(Status.OPEN, 41, 9)));

        assertThat(marks).contains(new MarkDto(9, Mark.OPEN.name(),
            "2 proof obligations, 1 proved"));
        assertThat(marks).extracting(MarkDto::line).containsExactly(9, 27, 41);
    }

    @Test
    void oneObligationIsExplainedInKeysOwnWords() {
        List<MarkDto> marks = SourceMarks.of(ROOT.resolve("src/Account.java"), ROOT,
            List.of(obligation(Status.CLOSED, 27, 0)));

        assertThat(marks).singleElement()
                .satisfies(mark -> assertThat(mark.tooltip())
                        .isEqualTo(Status.CLOSED.explanation()));
    }

    @Test
    void aFileWithoutObligationsIsNotMarked() {
        assertThat(SourceMarks.of(ROOT.resolve("src/Other.java"), ROOT,
            List.of(obligation(Status.CLOSED, 27, 9)))).isEmpty();
    }

    private static ObligationDto obligation(Status status, int targetLine, int classLine) {
        return new ObligationDto("contract " + targetLine, "com.example.Account",
            "com.example.Account::deposit", "JML normal_behavior operation contract 0",
            "deposit(int)", status.name(), status.explanation(), "src/Account.java", classLine,
            targetLine, "proofs/x.proof", true, List.of());
    }
}
