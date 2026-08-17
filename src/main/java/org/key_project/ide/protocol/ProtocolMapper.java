/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.protocol;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import org.key_project.ide.config.ConfigProblem;
import org.key_project.ide.config.OptionChange;
import org.key_project.ide.config.ProjectConfig;
import org.key_project.ide.config.ProofOptions;
import org.key_project.ide.config.ProverOptions;
import org.key_project.ide.config.VerificationContext;
import org.key_project.ide.protocol.Dtos.ContextDto;
import org.key_project.ide.protocol.Dtos.OptionChangeDto;
import org.key_project.ide.protocol.Dtos.ProblemDto;
import org.key_project.ide.protocol.Dtos.ProjectConfigDto;
import org.key_project.ide.protocol.Dtos.ProofOptionsDto;
import org.key_project.ide.protocol.Dtos.ProverOptionsDto;

/**
 * Translates between the wire form and the model.
 * <p>
 * The wire form is also the file form, and a hand-edited file may leave fields out, so
 * every list is treated as empty when absent.
 */
public final class ProtocolMapper {

    private ProtocolMapper() {
    }

    public static ProjectConfig toModel(ProjectConfigDto dto) {
        int version = dto.version() == 0 ? ProjectConfig.CURRENT_VERSION : dto.version();
        return new ProjectConfig(version, orEmpty(dto.contexts()).stream()
                .map(ProtocolMapper::toModel).toList(), dto.proofDirectory(),
            toModel(dto.options()), toModel(dto.prover()), toModel(dto.obligationOptions()));
    }

    public static VerificationContext toModel(ContextDto dto) {
        return new VerificationContext(orBlank(dto.id()), Path.of(orBlank(dto.javaSource())),
            orEmpty(dto.classpath()).stream().map(Path::of).toList(),
            dto.bootclasspath() == null ? null : Path.of(dto.bootclasspath()),
            orEmpty(dto.includes()).stream().map(Path::of).toList(), toModel(dto.options()));
    }

    /**
     * @param dto the settings configured at a level, or null if none are
     * @return the settings, empty if none are configured
     */
    public static ProofOptions toModel(ProofOptionsDto dto) {
        return dto == null ? ProofOptions.NONE
                : new ProofOptions(dto.taclet(), dto.strategy(), dto.maxSteps(),
                    dto.timeout());
    }

    /**
     * @param dto the fields the form changed, or null if it changed none
     * @return the change, one that changes nothing if the form changed no field
     */
    public static OptionChange toModel(OptionChangeDto dto) {
        return dto == null ? OptionChange.NOTHING
                : new OptionChange(dto.taclet(), dto.tacletCleared(), dto.strategy(),
                    dto.strategyCleared(), dto.maxSteps(), dto.timeout());
    }

    /**
     * @param dto the prover to use, or null if the project configures none
     * @return the prover, KeY's own if none is configured
     */
    public static ProverOptions toModel(ProverOptionsDto dto) {
        return dto == null ? ProverOptions.DEFAULT
                : new ProverOptions(dto.parallel(), dto.threads());
    }

    private static Map<String, Map<String, ProofOptions>> toModel(
            Map<String, Map<String, ProofOptionsDto>> dto) {
        if (dto == null) {
            return Map.of();
        }
        Map<String, Map<String, ProofOptions>> model = new LinkedHashMap<>();
        dto.forEach((contextId, byContract) -> {
            Map<String, ProofOptions> options = new LinkedHashMap<>();
            byContract.forEach((contract, o) -> options.put(contract, toModel(o)));
            model.put(contextId, options);
        });
        return model;
    }

    /**
     * @param options the settings configured at a level
     * @return the wire form, or null if no setting is configured
     */
    public static ProofOptionsDto toDto(ProofOptions options) {
        return options == null || options.isEmpty() ? null
                : new ProofOptionsDto(options.taclet(), options.strategy(), options.maxSteps(),
                    options.timeout());
    }

    private static Map<String, Map<String, ProofOptionsDto>> toDto(
            Map<String, Map<String, ProofOptions>> model) {
        Map<String, Map<String, ProofOptionsDto>> dto = new LinkedHashMap<>();
        model.forEach((contextId, byContract) -> {
            Map<String, ProofOptionsDto> options = new LinkedHashMap<>();
            byContract.forEach((contract, o) -> {
                ProofOptionsDto stated = toDto(o);
                if (stated != null) {
                    options.put(contract, stated);
                }
            });
            if (!options.isEmpty()) {
                dto.put(contextId, options);
            }
        });
        return dto;
    }

    public static ProjectConfigDto toDto(ProjectConfig config) {
        return new ProjectConfigDto(config.version(),
            config.contexts().stream().map(ProtocolMapper::toDto).toList(),
            config.proofDirectory(), toDto(config.options()),
            new ProverOptionsDto(config.prover().parallel(), config.prover().threads()),
            toDto(config.obligationOptions()));
    }

    public static ContextDto toDto(VerificationContext context) {
        return new ContextDto(context.id(), context.javaSource().toString(),
            context.classpath().stream().map(Path::toString).toList(),
            context.bootclasspath() == null ? null : context.bootclasspath().toString(),
            context.includes().stream().map(Path::toString).toList(),
            toDto(context.options()));
    }

    public static ProblemDto toDto(ConfigProblem problem) {
        return new ProblemDto(problem.severity().name(), problem.contextId(), problem.field(),
            problem.message());
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String orBlank(String value) {
        return value == null ? "" : value;
    }
}
