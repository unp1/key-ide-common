/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.uka.ilkd.key.control.KeYEnvironment;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.key_project.ide.Fixture;
import org.key_project.ide.config.VerificationContext;
import org.key_project.ide.key.ProofObligations.Obligation;
import org.key_project.ide.key.ProofObligations.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lists the test project's proof obligations and reports how far each has got.
 */
class ProofObligationsTest {

    private static final Path SOURCE_FIXTURE =
        Path.of("src/test/fixture").toAbsolutePath().normalize();

    /**
     * The project these tests read.
     * <p>
     * The fixture is copied without its proofs, so what these tests see does not depend on
     * what anyone has proved in it by hand.
     */
    private static Path FIXTURE;

    private static KeYEnvironment<?> environment;
    private static VerificationContext context;

    @BeforeAll
    static void loadFixture() throws Exception {
        FIXTURE = Files.createTempDirectory("key-ide-obligations");
        copySources(SOURCE_FIXTURE.resolve("core"), FIXTURE.resolve("core"));
        context = new VerificationContext("core", FIXTURE.resolve("core/src/main/java"),
            List.of(), null, List.of());
        environment = KeYEnvironment.load(context.javaSource(), null, null, null);
    }

    @AfterAll
    static void disposeFixture() throws IOException {
        if (environment != null) {
            environment.dispose();
        }
        deleteTree(FIXTURE);
    }

    private static void copySources(Path from, Path to) throws IOException {
        try (var entries = Files.walk(from)) {
            for (Path entry : entries.toList()) {
                Path target = to.resolve(from.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(entry, target);
                }
            }
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var entries = Files.walk(directory)) {
            entries.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A temporary directory left behind is not worth failing a test over.
                }
            });
        }
    }

    @Test
    void listsTheContractsOfTheProjectsOwnClasses() {
        List<Obligation> obligations = obligations();

        assertThat(obligations).isNotEmpty();
        assertThat(obligations).allSatisfy(obligation -> assertThat(obligation.type().getFullName())
                .startsWith("com.example.core"));
        assertThat(obligations).anySatisfy(obligation -> assertThat(
            obligation.contract().getName()).contains("deposit"));
    }

    @Test
    void namesAnObligationByItsTargetWithParameters() {
        // Two methods of one name differ only in their parameters, and a reader has to see
        // which one a row is about.
        assertThat(obligations()).extracting(Obligation::label)
                .contains("deposit(int) \u2014 normal_behavior operation contract.0",
                    "getBalance()", "withdraw(int)");
    }

    @Test
    void tellsTheContractsOfOneTargetApart() {
        // A target with one contract is named after the target alone; one with several
        // carries the kind and number KeY gives each of them.
        assertThat(obligations()).extracting(Obligation::label)
                .contains("deposit(int) \u2014 normal_behavior operation contract.0",
                    "deposit(int) \u2014 normal_behavior operation contract.1")
                .doesNotContain("deposit(int)");
    }

    @Test
    void placesAConstructorAtItsDeclaration() {
        // KeY normalises a constructor and the normalised form carries no position, so the
        // line has to come from the declaration it was made from.
        assertThat(obligations())
                .filteredOn(obligation -> obligation.label().startsWith("Account("))
                .allSatisfy(obligation -> assertThat(obligation.targetLine())
                        .isGreaterThan(obligation.classLine()));
    }

    @Test
    void reportsAnObligationWithNoProofAsHavingNone() {
        // Whichever obligations have no proof saved for them: the fixture may hold saved
        // proofs from someone trying the plugins by hand, so nothing here assumes it does
        // not.
        assertThat(obligations()).filteredOn(o -> !o.proofFileExists())
                .isNotEmpty()
                .allSatisfy(o -> assertThat(o.status()).isEqualTo(Status.NONE));
    }

    @Test
    void neverReportsAProofAsClosedWithoutKeySayingSo() {
        // Nothing is loaded and nothing is saved, so no obligation may claim to be proved.
        assertThat(obligations()).noneSatisfy(obligation -> assertThat(obligation.status())
                .isIn(Status.CLOSED, Status.CLOSED_BY_CACHE, Status.CLOSED_BUT_LEMMAS_LEFT));
    }

    @Test
    void explainsEveryStatusInASentence() {
        for (Status status : Status.values()) {
            assertThat(status.explanation()).isNotBlank().endsWith(".");
        }
    }

    @Test
    void saysOfEveryStatusWhetherTheContractIsProved() {
        // The reader of these sentences wants to know whether the contract is proved, so
        // each of them answers that before anything else.
        for (Status status : Status.values()) {
            assertThat(status.explanation()).startsWith(switch (status) {
                case CLOSED, CLOSED_BY_CACHE, CLOSED_BUT_LEMMAS_LEFT -> "Proved";
                case NONE, SAVED, OPEN -> "Not proved";
                case UNKNOWN -> "Unknown";
            });
        }
    }

    @Test
    void expectsAProofBesideTheSourceItBelongsTo() {
        Obligation obligation = obligations().stream()
                .filter(o -> o.contract().getName().contains("deposit")).findFirst().orElseThrow();

        Path relative = FIXTURE.relativize(obligation.proofFile());

        assertThat(relative.toString()).startsWith("proofs/core/com/example/core/");
        assertThat(relative.getFileName().toString()).endsWith(".proof").contains("deposit");
    }

    @Test
    void reportsAnObligationWithASavedProofAsSaved() throws IOException {
        Obligation before = obligations().stream()
                .filter(o -> o.contract().getName().contains("withdraw"))
                .filter(o -> !o.proofFileExists()).findFirst().orElseThrow();
        Files.createDirectories(before.proofFile().getParent());
        Files.writeString(before.proofFile(), "not a real proof, only its presence matters");
        try {
            Obligation after = obligations().stream()
                    .filter(o -> o.contract().getName().equals(before.contract().getName()))
                    .findFirst().orElseThrow();

            // Saved says a file is there, not that the proof closes.
            assertThat(after.status()).isEqualTo(Status.SAVED);
            assertThat(after.proofFileExists()).isTrue();
        } finally {
            Files.deleteIfExists(before.proofFile());
            deleteEmptyParents(before.proofFile().getParent());
        }
    }

    @Test
    void givesANewProofTheFileThisLayoutExpects() throws Exception {
        Obligation obligation = obligations().stream()
                .filter(o -> o.contract().getName().contains("getBalance")).findFirst()
                .orElseThrow();
        var proof = environment.createProof(
            obligation.contract().createProofObl(environment.getInitConfig()));
        try {
            new ProofObligations(environment, context, ProofFiles.under(FIXTURE)).assignProofFiles();

            // KeY proposes a save location from the proof's file, so setting it is what
            // puts a saved proof where this layout looks for it.
            assertThat(proof.getProofFile()).isEqualTo(obligation.proofFile());
            assertThat(obligation.proofFile().getParent()).exists();
        } finally {
            proof.dispose();
            deleteEmptyParents(obligation.proofFile().getParent());
        }
    }

    @Test
    void picksOutTheObligationsAboutOneMethod() {
        ResolvedMethod clamp = new MethodResolver(environment.getJavaInfo())
                .find("com.example.core.ArrayUtils", "clamp", List.of("int", "int", "int"))
                .orElseThrow();

        List<Obligation> about = ProofObligations.about(obligations(), clamp.method());

        assertThat(about).hasSize(1);
        assertThat(about.get(0).contract().getName()).contains("clamp(int,int,int)");
        assertThat(about.get(0).label()).isEqualTo("clamp(int,int,int)");
    }

    @Test
    void tellsAnOverloadFromTheMethodItSharesItsNameWith() {
        MethodResolver resolver = new MethodResolver(environment.getJavaInfo());
        ResolvedMethod ofThree = resolver
                .find("com.example.core.ArrayUtils", "clamp", List.of("int", "int", "int"))
                .orElseThrow();
        ResolvedMethod ofOne = resolver
                .find("com.example.core.ArrayUtils", "clamp", List.of("int")).orElseThrow();

        // The two are declared under one name, and KeY writes a target without its parameter
        // types, so anything matching by name would return both for either of them.
        List<Obligation> aboutThree = ProofObligations.about(obligations(), ofThree.method());
        List<Obligation> aboutOne = ProofObligations.about(obligations(), ofOne.method());

        assertThat(aboutThree).hasSize(1);
        assertThat(aboutOne).hasSize(1);
        assertThat(aboutThree.get(0).contract().getName()).contains("clamp(int,int,int)");
        assertThat(aboutOne.get(0).contract().getName()).contains("clamp(int)");
    }

    @Test
    void reportsAnObligationWithAStartedProofAsOpen() throws Exception {
        Obligation before = obligations().stream()
                .filter(o -> o.contract().getName().contains("maximum")).findFirst().orElseThrow();
        assertThat(before.status()).isEqualTo(Status.NONE);

        var proof = environment.createProof(
            before.contract().createProofObl(environment.getInitConfig()));
        try {
            Obligation after = obligations().stream()
                    .filter(o -> o.contract().getName().equals(before.contract().getName()))
                    .findFirst().orElseThrow();

            assertThat(after.status()).isEqualTo(Status.OPEN);
        } finally {
            proof.dispose();
        }
    }

    @Test
    void namesTheProofFileTheWayKeyWouldSaveIt() {
        String name = ProofFiles.fileNameOf(
            "Account[Account::deposit(int)].JML normal_behavior operation contract.0");

        assertThat(name).endsWith(".proof").doesNotContain("\\").doesNotContain("?");
    }

    @Test
    void servesKeysOwnIconsForTheStatesKeyDraws() {
        var icons = StatusIcons.asDataUris(16);

        assertThat(icons).containsKeys(Status.OPEN.name(), Status.CLOSED.name());
        assertThat(icons.get(Status.CLOSED.name())).startsWith("data:image/png;base64,");
    }

    private static List<Obligation> obligations() {
        return new ProofObligations(environment, context, ProofFiles.under(FIXTURE)).list();
    }

    /** Removes the directories the test created, up to the proofs directory. */
    private static void deleteEmptyParents(Path directory) throws IOException {
        Path proofs = ProofFiles.under(FIXTURE).root();
        Path current = directory;
        while (current != null && current.startsWith(proofs) && !current.equals(proofs)) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
        Files.deleteIfExists(proofs);
    }
}
