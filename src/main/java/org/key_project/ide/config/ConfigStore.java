/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import org.key_project.ide.protocol.Dtos.ProjectConfigDto;
import org.key_project.ide.protocol.ProtocolMapper;

/**
 * Reads and writes a project's {@code .key/settings.json}.
 * <p>
 * The bridge owns this file so the schema is implemented once rather than once per IDE.
 * The file is meant to be readable and diffable, so it is written indented and the reader
 * tolerates missing fields.
 */
public final class ConfigStore {

    /** Where the file sits, relative to the project root. */
    public static final Path RELATIVE_PATH = Path.of(".key", "settings.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path projectRoot;

    /**
     * @param projectRoot the directory holding the {@code .key} directory
     */
    public ConfigStore(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    /** The file this store reads and writes, whether or not it exists. */
    public Path file() {
        return projectRoot.resolve(RELATIVE_PATH);
    }

    /**
     * Reads the configuration.
     *
     * @return the stored configuration, or an empty one if the project has no file yet
     * @throws IOException if the file exists but cannot be read or parsed
     */
    public ProjectConfig read() throws IOException {
        Path file = file();
        if (!Files.exists(file)) {
            return ProjectConfig.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ProjectConfigDto dto = GSON.fromJson(reader, ProjectConfigDto.class);
            if (dto == null) {
                throw new IOException("The configuration file " + file + " is empty.");
            }
            return ProtocolMapper.toModel(dto);
        } catch (JsonParseException e) {
            throw new IOException("The configuration file " + file + " is not valid JSON: "
                + e.getMessage(), e);
        }
    }

    /**
     * Writes the configuration, creating the {@code .key} directory if needed.
     *
     * @param config the configuration to store
     * @throws IOException if the file cannot be written
     */
    public void write(ProjectConfig config) throws IOException {
        Path file = file();
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(ProtocolMapper.toDto(config), writer);
            writer.write('\n');
        }
    }
}
