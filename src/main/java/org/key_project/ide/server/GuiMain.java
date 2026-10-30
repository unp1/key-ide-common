/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2,
 * or (at your option) any later version.
 * SPDX-License-Identifier: GPL-2.0-or-later */
package org.key_project.ide.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import de.uka.ilkd.key.core.Main;
import de.uka.ilkd.key.gui.MainWindow;

/**
 * KeY's own window, kept open for the proofs an IDE asks to look at.
 * <p>
 * An IDE that opens every proof in a window of its own leaves the user with one window per
 * proof. This starts KeY's window once, loads the first proof it is given, and then loads
 * every further proof the IDE sends: one absolute path per line on standard input. The IDE
 * keeps the process and writes a line to it as long as it is alive, and starts a new one
 * when it is not, which is what makes reusing the window a choice the IDE can offer.
 * <p>
 * Standard input is the channel because the IDE owns both ends of it already: no port to
 * pick, nothing to publish, and it dies with the process. A KeY the user started by hand
 * has no such channel and cannot be reached this way.
 */
public final class GuiMain {

    private GuiMain() {
    }

    /**
     * Starts KeY's window on the given proof, then loads a proof per line of input.
     *
     * @param args one element, the absolute path of the first proof to open
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: GuiMain <proof-file>");
            System.exit(2);
        }
        Main.main(new String[] { args[0] });
        readLoadRequests();
    }

    /**
     * Loads a proof per line of standard input, until it closes.
     * <p>
     * The end of the stream is not the end of the window: the IDE that started this may be
     * closed while the user goes on working in KeY, so the window stays and only the
     * channel is gone.
     */
    private static void readLoadRequests() {
        try (BufferedReader requests =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = requests.readLine()) != null) {
                Path proof = Path.of(line.strip());
                if (!Files.isRegularFile(proof)) {
                    System.err.println("no proof at " + proof);
                    continue;
                }
                SwingUtilities.invokeLater(() -> {
                    if (MainWindow.hasInstance()) {
                        MainWindow.getInstance().loadProblem(proof);
                    }
                });
            }
        } catch (IOException e) {
            // The IDE is gone. The window stays for the user to finish with.
        }
    }
}
