/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.util.List;

import de.uka.ilkd.key.speclang.PositionedString;
import de.uka.ilkd.key.util.ExceptionTools;

import org.key_project.ide.protocol.Dtos.SourceProblem;
import org.key_project.util.parsing.Location;
import org.key_project.util.parsing.Position;

/**
 * What KeY found wrong with a source, read out of the failure it threw.
 * <p>
 * A load that fails on Java or JML fails with an exception, and the exception carries the
 * file and the line of each problem, sometimes several of them. KeY's own tools list them;
 * this turns that list into what goes on the wire, so an editor can mark the line rather than
 * show the exception.
 */
public final class SourceProblems {

    private SourceProblems() {
    }

    /**
     * The problems a failure reports, in the order of the files and lines they name.
     *
     * @param failure what KeY threw
     * @return at least one problem; a failure that names no place is one problem with none
     */
    public static List<SourceProblem> of(Throwable failure) {
        return ExceptionTools.getMessages(failure).stream()
                .map(SourceProblems::problem)
                .toList();
    }

    private static SourceProblem problem(PositionedString reported) {
        Location location = reported.getLocation();
        Position position = location == null ? Position.UNDEFINED : location.getPosition();
        String uri = location == null || location.getFileUri() == null ? null
                : location.getFileUri().toString();
        boolean placed = position != null && !Position.UNDEFINED.equals(position);
        return new SourceProblem(uri, placed ? position.line() : 0,
            placed ? position.column() : 0, reported.getText().strip());
    }
}
