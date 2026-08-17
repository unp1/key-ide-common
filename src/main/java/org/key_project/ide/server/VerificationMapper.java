/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.server;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import de.uka.ilkd.key.proof.init.Profile;

import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.key.ContextKnowledge;
import org.key_project.ide.key.ProofObligations;
import org.key_project.ide.key.ProofRunner;
import org.key_project.ide.key.SavedSettings;
import org.key_project.ide.protocol.Dtos.ObligationDto;
import org.key_project.ide.protocol.Dtos.OptionDifferenceDto;
import org.key_project.ide.protocol.Dtos.ProofOutcomeDto;

/**
 * Maps what the bridge holds about a proof into wire form.
 * <p>
 * Paths cross the wire relative to the project, since that is how the IDE addresses its own
 * files. Paths outside the project stay absolute.
 */
final class VerificationMapper {

    private final Path projectRoot;

    VerificationMapper(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    /**
     * One obligation as a listing shows it.
     *
     * @param obligation the proof obligation
     * @param status the status KeY reports for it
     * @param differingSettings the settings in which its saved proof differs from the
     *        configured ones
     */
    ObligationDto obligation(ProofObligations.Obligation obligation, String status,
            List<OptionDifferenceDto> differingSettings) {
        return new ObligationDto(obligation.contract().getName(),
            obligation.type().getFullName(), obligation.contract().getTarget().name().toString(),
            obligation.contract().getDisplayName(), obligation.label(), status,
            ProofObligations.Status.valueOf(status).explanation(), relative(obligation.sourceFile()),
            obligation.classLine(), obligation.targetLine(), relative(obligation.proofFile()),
            obligation.proofFileExists(), differingSettings);
    }

    /** The result of attempting or reading one proof. */
    ProofOutcomeDto outcome(ProofRunner.Outcome outcome) {
        return outcome(outcome, outcome.status().name());
    }

    /**
     * The result of attempting or reading one proof, under the status that stands for it
     * now, which is not the one of the attempt where KeY has been asked again since.
     *
     * @param outcome what the attempt or the read measured
     * @param status the status the context's knowledge holds
     */
    ProofOutcomeDto outcome(ProofRunner.Outcome outcome, String status) {
        ProofObligations.Status stands = ProofObligations.Status.valueOf(status);
        return new ProofOutcomeDto(outcome.contractName(), stands.name(), stands.explanation(),
            outcome.nodes(), outcome.branches(), outcome.milliseconds(),
            relative(outcome.proofFile()), outcome.message());
    }

    /** The result reported for an obligation whose proof file is missing. */
    ProofOutcomeDto noSavedProof(ProofObligations.Obligation obligation) {
        return new ProofOutcomeDto(obligation.contract().getName(),
            ProofObligations.Status.NONE.name(), ProofObligations.Status.NONE.explanation(),
            0, 0, 0, relative(obligation.proofFile()), "There is no saved proof to replay.");
    }

    /** A path as the IDE addresses it. */
    String relative(Path path) {
        Path target = path.toAbsolutePath().normalize();
        return target.startsWith(projectRoot) ? projectRoot.relativize(target).toString()
                : target.toString();
    }

    /** A status reported by KeY, in the form a context's cache stores. */
    static ContextKnowledge.Verdict verdict(ProofRunner.Outcome outcome) {
        return new ContextKnowledge.Verdict(outcome.contractName(), outcome.status().name(),
            outcome.usedContracts());
    }

    /** The obligations of a context, with the state found on disk. */
    static List<ContextKnowledge.OnDisk> onDisk(List<ProofObligations.Obligation> obligations) {
        return obligations.stream()
                .map(obligation -> new ContextKnowledge.OnDisk(obligation.contract().getName(),
                    obligation.proofFileExists(), obligation.status().name()))
                .toList();
    }

    /**
     * The settings in which a saved proof differs from the ones configured for its
     * obligation now.
     *
     * @return the differences, empty if there is no saved proof or none differ
     */
    static List<OptionDifferenceDto> differingSettings(ProofObligations.Obligation obligation,
            ProofOptions options, Map<String, String> loadedChoices, Profile profile) {
        if (!obligation.proofFileExists()) {
            return List.of();
        }
        SavedSettings saved = SavedSettings.of(obligation.proofFile());
        return saved == null ? List.of() : saved.differencesFrom(loadedChoices, options, profile);
    }
}
