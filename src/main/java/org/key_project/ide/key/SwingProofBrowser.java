/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import javax.swing.SwingUtilities;

import de.uka.ilkd.key.gui.MainWindow;
import de.uka.ilkd.key.gui.ProofManagementDialog;
import de.uka.ilkd.key.java.ast.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.op.IObserverFunction;
import de.uka.ilkd.key.proof.init.InitConfig;

/**
 * Shows KeY's own proof obligation browser.
 * <p>
 * The dialog is modal, so showing it blocks until the user closes it. It is handed to the
 * event dispatch thread rather than opened on the thread serving the request, which lets
 * the bridge answer the request as soon as the dialog is on screen instead of when the
 * user is finished with it.
 */
public final class SwingProofBrowser implements ProofBrowser {

    @Override
    public void show(InitConfig initConfig, KeYJavaType type, IObserverFunction target,
            Runnable afterClose) {
        SwingUtilities.invokeLater(() -> {
            try {
                ProofManagementDialog.showInstance(initConfig, type, target);
            } finally {
                restoreInterface();
                afterClose.run();
            }
        });
    }

    /**
     * Re-enables KeY's interface after the browser closes.
     * <p>
     * Starting a proof disables the interface: {@code ProblemInitializer} reports progress,
     * and {@code WindowUserInterfaceControl.progressStarted} responds with
     * {@code stopInterface}. Its {@code progressStopped} leaves the interface disabled on
     * purpose, because KeY expects the {@code ProblemLoader} that opened the browser to
     * re-enable it once loading finishes, and the browser itself re-enables only from the
     * second proof onwards.
     * <p>
     * The bridge loads the environment and opens the browser itself, so no loader is
     * involved and nothing else restores the interface. Without this, KeY stays in automatic
     * mode with every action disabled: a window that ignores input although its event queue
     * is idle.
     */
    private static void restoreInterface() {
        MainWindow.getInstance().getMediator().startInterface(true);
    }
}
