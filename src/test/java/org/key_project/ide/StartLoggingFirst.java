/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.LoggerFactory;

/**
 * Starts logging before any test touches KeY.
 * <p>
 * KeY registers a listener in its logback configuration that asks
 * {@code PathConfig.getWritingPaths()} where to write, and that reads
 * {@code KeYResourceManager.getManager()}. Logging starts at the first message anyone logs, so
 * when that first message comes from KeYResourceManager's own initialisation the manager is
 * still being built and reads back as null, which fails with an ExceptionInInitializerError and
 * leaves logging broken for the rest of the process: every later test that touches KeY fails
 * too, whatever it was testing.
 * <p>
 * Which class logs first depends on which test runs first, so this made the suite pass or fail
 * on the order its classes happened to be in. Starting logging here settles it: by the time any
 * KeY class is loaded, logging is configured and the listener has already run.
 */
public final class StartLoggingFirst implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        LoggerFactory.getLogger(StartLoggingFirst.class).debug("logging started before KeY");
    }
}
