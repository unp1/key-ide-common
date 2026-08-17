/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import de.uka.ilkd.key.java.ast.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.op.IObserverFunction;
import de.uka.ilkd.key.proof.init.InitConfig;

/**
 * Shows KeY's proof obligation browser with a method already selected.
 * <p>
 * The interface exists so the service can be tested without a user interface: showing a
 * modal dialog is the one step that needs a running KeY window.
 */
public interface ProofBrowser {

    /**
     * Opens the browser on a target.
     *
     * @param initConfig the loaded environment the browser lists contracts from
     * @param type the class to select
     * @param target the method to select within it
     * @param afterClose run once the browser is closed, when any proof the user started
     *        exists
     */
    void show(InitConfig initConfig, KeYJavaType type, IObserverFunction target,
            Runnable afterClose);
}
