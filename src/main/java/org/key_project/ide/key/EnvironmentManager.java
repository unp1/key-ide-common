/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.proof.init.InitConfig;
import de.uka.ilkd.key.proof.init.JavaProfile;
import de.uka.ilkd.key.proof.io.ProblemLoaderException;

import org.key_project.ide.config.VerificationContext;

/**
 * Loads contexts into KeY and caches them, since loading takes seconds and a user verifies
 * several methods of the same project in a row.
 * <p>
 * A cached environment is checked against the files it was loaded from. An environment
 * holding edited sources would report positions from the previous text and verify the
 * previous code, so a context whose sources changed is loaded again.
 */
public final class EnvironmentManager implements AutoCloseable {

    private static final Logger LOGGER = System.getLogger(EnvironmentManager.class.getName());

    /**
     * A loaded context, with the state of the files it was loaded from and the taclet
     * options KeY selected while loading them.
     */
    private record Loaded(KeYEnvironment<?> environment, SourceSnapshot snapshot,
            Map<String, String> choices) {
    }

    private final Map<String, Loaded> environments = new ConcurrentHashMap<>();

    /** Told the id of a context whenever its environment is dropped. */
    private final List<Consumer<String>> onDiscard = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener that is notified whenever a loaded context is discarded, either
     * because its sources changed or on an explicit discard.
     * <p>
     * Anything derived from proofs of that environment applies to it alone: a contract
     * proved for one version of the sources says nothing about the same contract in
     * another. Listeners use this to discard what they cached for the context.
     *
     * @param listener notified with the context id
     */
    public void onDiscard(Consumer<String> listener) {
        onDiscard.add(listener);
    }

    /**
     * The environment of a context, loaded on first use and after its sources change.
     *
     * @param context a context whose paths are absolute
     * @return an environment holding the current state of the files
     * @throws ProblemLoaderException if KeY cannot load the sources, most often because a
     *         classpath entry is missing
     */
    public KeYEnvironment<?> environmentFor(VerificationContext context)
            throws ProblemLoaderException {
        SourceSnapshot current = SourceSnapshot.of(context);
        Loaded loaded = environments.get(context.id());
        if (loaded != null) {
            if (loaded.snapshot().equals(current)) {
                return loaded.environment();
            }
            LOGGER.log(Level.INFO,
                "Reloading context '" + context.id() + "': its sources have changed.");
            discard(context.id());
        }

        // Each context gets a profile of its own. KeY keeps its one-step simplifier on the
        // profile, and that simplifier serves one proof at a time: it drops what it knows
        // about the last proof when it is asked about another. Contexts prove at the same
        // time, so with the shared profile each would keep unsettling the other's proof.
        KeYEnvironment<?> environment = KeYEnvironment.load(new JavaProfile(),
            context.javaSource(),
            context.classpath().isEmpty() ? null : context.classpath(), context.bootclasspath(),
            context.includes().isEmpty() ? null : context.includes(), true);

        // The summary is taken before loading, so an edit made while KeY was reading is
        // noticed on the next request rather than being taken for the loaded state.
        Loaded raced = environments.putIfAbsent(context.id(), new Loaded(environment, current,
            AvailableOptions.chosen(environment.getInitConfig())));
        if (raced != null) {
            environment.dispose();
            return raced.environment();
        }
        return environment;
    }

    /**
     * The configuration a context was loaded into, if it is loaded.
     *
     * Answers without loading anything, so a caller that only wants to know what KeY offers
     * can use what a loaded context knows and read KeY's rules otherwise.
     *
     * @param contextId the context to ask about
     * @return its configuration, or empty when the context is not loaded
     */
    public Optional<InitConfig> configOf(String contextId) {
        Loaded loaded = contextId == null ? null : environments.get(contextId);
        return loaded == null ? Optional.empty()
                : Optional.of(loaded.environment().getInitConfig());
    }

    /**
     * The taclet options KeY chose when it read a context.
     * <p>
     * Building a proof sets the options that proof is to use, which changes what the loaded
     * configuration says. This is what KeY chose before any proof was built, so a form can
     * show what a level inherits rather than what the last proof happened to use.
     *
     * @param contextId the context to ask about
     * @return the chosen option of every category, empty when the context is not loaded
     */
    public Map<String, String> loadedChoices(String contextId) {
        Loaded loaded = environments.get(contextId);
        return loaded == null ? Map.of() : loaded.choices();
    }

    /**
     * Drops a loaded context, so the next request loads it again.
     *
     * @param contextId the context to discard
     */
    public void discard(String contextId) {
        Loaded loaded = environments.remove(contextId);
        if (loaded != null) {
            loaded.environment().dispose();
            onDiscard.forEach(listener -> listener.accept(contextId));
        }
    }

    @Override
    public void close() {
        environments.keySet().forEach(this::discard);
    }
}
