/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.key;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.java.ast.PositionInfo;
import de.uka.ilkd.key.java.ast.abstraction.KeYJavaType;
import de.uka.ilkd.key.java.ast.abstraction.Type;
import de.uka.ilkd.key.java.ast.declaration.MethodDeclaration;
import de.uka.ilkd.key.java.ast.declaration.TypeDeclaration;
import de.uka.ilkd.key.logic.op.IObserverFunction;
import de.uka.ilkd.key.logic.op.IProgramMethod;
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.mgt.ProofStatus;
import de.uka.ilkd.key.proof.mgt.SpecificationRepository;
import de.uka.ilkd.key.speclang.Contract;

import org.key_project.ide.config.VerificationContext;
import org.key_project.util.collection.ImmutableSet;

/**
 * Lists the proof obligations of a context with their status.
 * <p>
 * A proof loaded in KeY reports the status the proof obligation browser shows on its start
 * button. A proof that exists only as a file is reported as saved: the file shows that a
 * proof was stored, not that it closes.
 */
public final class ProofObligations {

    private static final Logger LOGGER = System.getLogger(ProofObligations.class.getName());

    /**
     * The status of a proof obligation. A KeY status this bridge does not know is reported
     * as unknown.
     */
    public enum Status {
        /** No proof is loaded and none is saved. */
        NONE("Not proved: no proof exists for this contract."),
        /** A proof file exists; whether it closes is unknown until it is replayed. */
        SAVED("Not proved yet: a proof is saved but has not been replayed against the "
            + "current sources."),
        /** KeY holds a proof with goals left. */
        OPEN("Not proved: the proof has goals left."),
        /** KeY holds a proof that is closed except for lemmas. */
        CLOSED_BUT_LEMMAS_LEFT(
            "Proved, but it uses contracts that are not proved themselves."),
        /** KeY holds a proof closed by reusing a cached proof. */
        CLOSED_BY_CACHE("Proved, reusing a cached proof."),
        /** KeY holds a closed proof. */
        CLOSED("Proved."),
        /** KeY reported a state this bridge does not recognise. */
        UNKNOWN("Unknown: KeY reported a proof state this version of the bridge does not "
            + "recognise.");

        private final String explanation;

        Status(String explanation) {
            this.explanation = explanation;
        }

        /** A sentence for the user, shown where the icon alone is not enough. */
        public String explanation() {
            return explanation;
        }
    }

    /**
     * One proof obligation.
     *
     * @param contract the contract, which also identifies it within a context
     * @param type the class declaring the target
     * @param status its status
     * @param label how it reads to a user: the target as KeY writes it, and what tells it
     *        from the other contracts of that target where there are several
     * @param sourceFile the file the class is declared in
     * @param classLine the 1-based line the class is declared on, 0 if unknown
     * @param targetLine the 1-based line the target is declared on, 0 if unknown
     * @param proofFile the file its proof is stored in
     * @param proofFileExists whether that file exists
     */
    public record Obligation(Contract contract, KeYJavaType type, Status status, String label,
            Path sourceFile, int classLine, int targetLine, Path proofFile,
            boolean proofFileExists) {
    }

    private final KeYEnvironment<?> environment;
    private final VerificationContext context;
    private final ProofFiles proofs;

    /**
     * @param environment a loaded context
     * @param context the context it was loaded from, with absolute paths
     * @param proofs the layout of the project's proof directory
     */
    public ProofObligations(KeYEnvironment<?> environment, VerificationContext context,
            ProofFiles proofs) {
        this.environment = environment;
        this.context = context;
        this.proofs = proofs;
    }

    /**
     * The proof obligations of this context. Contracts of types outside its own sources
     * are excluded, which leaves out the classes KeY ships and everything on the classpath.
     *
     * @return the obligations, ordered by class and then by contract
     */
    public List<Obligation> list() {
        SpecificationRepository specRepo =
            environment.getInitConfig().getServices().getSpecificationRepository();

        List<Obligation> obligations = new ArrayList<>();
        for (Contract contract : specRepo.getAllContracts()) {
            KeYJavaType type = contract.getKJT();
            URI typeFile = fileOf(type).orElse(null);
            if (typeFile == null || !declaredInThisContext(typeFile)) {
                continue;
            }
            Path proofFile = proofs.expectedFile(context.id(),
                context.javaSource(), typeFile, contract.getName());
            boolean exists = Files.isRegularFile(proofFile);
            obligations.add(new Obligation(contract, type, statusOf(specRepo, contract, exists),
                signatureOf(contract), Path.of(typeFile), lineOf(type),
                lineOf(contract.getTarget()), proofFile, exists));
        }
        obligations.sort(Comparator.comparing((Obligation o) -> o.type().getFullName())
                .thenComparing(o -> o.contract().getName()));
        return label(obligations);
    }

    /**
     * Names each obligation as a user reads it.
     * <p>
     * A contract is named after its target, which is what a reader recognises. A target with
     * several contracts would appear once per contract under the same name, so those carry
     * the kind of contract and its number as well.
     *
     * @param obligations the obligations of one context
     * @return the same obligations, each with its label
     */
    private static List<Obligation> label(List<Obligation> obligations) {
        Map<String, Long> perTarget = obligations.stream()
                .collect(Collectors.groupingBy(Obligation::label, Collectors.counting()));
        return obligations.stream()
                .map(obligation -> perTarget.getOrDefault(obligation.label(), 0L) > 1
                        ? withLabel(obligation,
                            obligation.label() + " \u2014 " + kindOf(obligation.contract()))
                        : obligation)
                .toList();
    }

    /**
     * The obligations about one method.
     * <p>
     * A contract's target is the method it is about, so this compares the method itself
     * rather than what it is called. Its name does not distinguish it: KeY writes a target
     * without its parameter types, so two overloads read alike, and its declared line is
     * unknown for the targets KeY normalises, such as a constructor.
     *
     * @param obligations the obligations to pick from
     * @param method the method the caret sits in
     * @return those about that method, in the order they were given
     */
    public static List<Obligation> about(List<Obligation> obligations, IProgramMethod method) {
        return obligations.stream()
                .filter(obligation -> method.equals(obligation.contract().getTarget()))
                .toList();
    }

    private static Obligation withLabel(Obligation obligation, String label) {
        return new Obligation(obligation.contract(), obligation.type(), obligation.status(), label,
            obligation.sourceFile(), obligation.classLine(), obligation.targetLine(),
            obligation.proofFile(), obligation.proofFileExists());
    }

    /**
     * The target as KeY writes it, with its parameters.
     * <p>
     * KeY names a contract for its class, then the target in brackets, then the kind of
     * contract: {@code com.example.Account[com.example.Account::deposit(int)].JML
     * normal_behavior operation contract.0}. What identifies it to a reader is the target,
     * and the parameters are part of that: two methods of one name differ only there.
     *
     * @param contract the contract to name
     * @return the target, or the contract's name where it is not written that way
     */
    private static String signatureOf(Contract contract) {
        String name = contract.getName();
        int open = name.indexOf('[');
        int close = name.indexOf(']');
        if (open < 0 || close < open) {
            return contract.getDisplayName();
        }
        String target = name.substring(open + 1, close);
        int method = target.lastIndexOf("::");
        return method < 0 ? target : target.substring(method + 2);
    }

    /**
     * Which contract of its target this is.
     *
     * @param contract the contract to describe
     * @return the kind and number KeY gives it
     */
    private static String kindOf(Contract contract) {
        String name = contract.getName();
        int after = name.indexOf("].");
        String kind = after < 0 ? contract.getDisplayName() : name.substring(after + 2);
        return kind.startsWith("JML ") ? kind.substring(4) : kind;
    }

    /**
     * Assigns the expected proof file to every proof that has none yet.
     * <p>
     * KeY derives the save location from the proof's file and otherwise falls back to the
     * directory its file chooser last used. A proof left without one would be saved outside
     * the project's proof directory, where this layout does not look for it.
     *
     * @return how many proofs were given a file
     */
    public int assignProofFiles() {
        int assigned = 0;
        SpecificationRepository specRepo =
            environment.getInitConfig().getServices().getSpecificationRepository();
        for (Obligation obligation : list()) {
            for (Proof proof : specRepo.getProofs(obligation.contract())) {
                if (proof.getProofFile() != null) {
                    continue;
                }
                try {
                    Files.createDirectories(obligation.proofFile().getParent());
                    proof.setProofFile(obligation.proofFile());
                    assigned++;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not prepare " + obligation.proofFile() + ": "
                        + e.getMessage());
                }
            }
        }
        return assigned;
    }

    /**
     * The contract with the given name, which is how the protocol identifies it.
     *
     * @param contractName the contract's name
     * @return the contract, or empty when this context declares no such contract
     */
    public Optional<Contract> contractNamed(String contractName) {
        return list().stream().map(Obligation::contract)
                .filter(contract -> contract.getName().equals(contractName)).findFirst();
    }

    /**
     * The status of a contract.
     * <p>
     * A proof loaded in the session is one being worked on in a KeY window, and its own
     * status is reported. Otherwise the proof file decides: an existing file is reported as
     * saved, a missing one as not started.
     *
     * @param specRepo where loaded proofs are registered
     * @param contract the contract to report on
     * @param proofFileExists whether a proof was saved for it
     * @return the status
     */
    private Status statusOf(SpecificationRepository specRepo, Contract contract,
            boolean proofFileExists) {
        Proof proof = preferablyClosed(specRepo.getProofs(contract));
        if (proof == null) {
            return proofFileExists ? Status.SAVED : Status.NONE;
        }
        ProofStatus status = proof.mgt().getStatus();
        if (status == null) {
            return Status.UNKNOWN;
        }
        if (status.getProofClosed()) {
            return Status.CLOSED;
        }
        if (status.getProofOpen()) {
            return Status.OPEN;
        }
        if (status.getProofClosedButLemmasLeft()) {
            return Status.CLOSED_BUT_LEMMAS_LEFT;
        }
        if (status.getProofClosedByCache()) {
            return Status.CLOSED_BY_CACHE;
        }
        return Status.UNKNOWN;
    }

    /**
     * The proof the browser reports on: a closed one, otherwise one closed but for lemmas,
     * otherwise any.
     *
     * @param proofs the proofs KeY holds for a contract
     * @return the proof to report on, or {@code null} when there is none
     */
    private static Proof preferablyClosed(ImmutableSet<Proof> proofs) {
        Proof fallback = null;
        for (Proof proof : proofs) {
            ProofStatus status = proof.mgt().getStatus();
            if (status != null && status.getProofClosed()) {
                return proof;
            }
            if (fallback == null || (status != null && status.getProofClosedButLemmasLeft())) {
                fallback = proof;
            }
        }
        return fallback;
    }

    private boolean declaredInThisContext(URI typeFile) {
        try {
            Path file = Path.of(typeFile).toAbsolutePath().normalize();
            return file.startsWith(context.javaSource().toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * The line a class is declared on.
     *
     * @param type the class
     * @return the 1-based line, or 0 if KeY records no position
     */
    private static int lineOf(KeYJavaType type) {
        Type javaType = type.getJavaType();
        if (javaType instanceof TypeDeclaration declaration) {
            return lineOf(declaration.getPositionInfo());
        }
        return 0;
    }

    /**
     * The line a contract's target is declared on.
     * <p>
     * A method carries its own position. A constructor does not: KeY normalises it, and the
     * normalised form has none, while the declaration it was made from has the one the
     * source was read at.
     *
     * @param target what the contract is about
     * @return the 1-based line, or 0 for a target KeY did not read from a file
     */
    private static int lineOf(IObserverFunction target) {
        if (!(target instanceof IProgramMethod method)) {
            return 0;
        }
        int declared = lineOf(method.getPositionInfo());
        if (declared > 0) {
            return declared;
        }
        MethodDeclaration declaration = method.getMethodDeclaration();
        return declaration == null ? 0 : lineOf(declaration.getPositionInfo());
    }

    private static int lineOf(PositionInfo position) {
        return position == null || position.getStartPosition() == null ? 0
                : Math.max(position.getStartPosition().line(), 0);
    }

    private static Optional<URI> fileOf(KeYJavaType type) {
        Type javaType = type.getJavaType();
        if (javaType instanceof TypeDeclaration declaration) {
            PositionInfo position = declaration.getPositionInfo();
            return position.getURI().map(URI::normalize);
        }
        return Optional.empty();
    }
}
