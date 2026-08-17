/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import de.uka.ilkd.key.java.ast.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.op.IObserverFunction;
import de.uka.ilkd.key.proof.init.InitConfig;

/**
 * Stands in for the browser in a bridge that has no user interface.
 * <p>
 * A headless bridge proves and reports. Inspecting a proof happens in a KeY window of its
 * own, opened on the saved proof file. This implementation reports that rather than starting
 * a window.
 */
public final class NoProofBrowser implements ProofBrowser {

    @Override
    public void show(InitConfig initConfig, KeYJavaType type, IObserverFunction target,
            Runnable afterClose) {
        throw new UnsupportedOperationException(
            "This bridge has no user interface. Open the proof file in KeY to look at a proof.");
    }
}
