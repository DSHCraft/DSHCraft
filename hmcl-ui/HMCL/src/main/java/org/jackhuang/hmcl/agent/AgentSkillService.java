/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/// Installs a local skill bundle only into an instance's isolated DSH_HOME.
@NotNullByDefault
public final class AgentSkillService {
    /// Upper bound on entries copied from a selected skill folder.
    private static final int MAX_ENTRIES = 1000;
    /// Upper bound on the total selected skill size.
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    /// Prevents instantiation of the filesystem service.
    private AgentSkillService() {
    }

    /// Copies a user-selected `<name>/SKILL.md` bundle into this instance's DSH skill root.
    /// Existing skills are never overwritten, and a failed copy removes its staging directory.
    public static Path install(Path source, Path instanceHome) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IOException("Select a regular skill directory.");
        }
        String name = source.getFileName().toString();
        if (!name.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IOException("Skill folder name must use lowercase kebab-case.");
        }
        Path manifest = source.resolve("SKILL.md");
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The selected folder has no SKILL.md.");
        }
        if (Files.size(manifest) > 1024 * 1024) {
            throw new IOException("SKILL.md is too large.");
        }
        validateManifest(Files.readString(manifest), name);
        Path pathToCheck = instanceHome.toAbsolutePath().normalize();
        for (int depth = 0; pathToCheck != null && depth < 4; depth++, pathToCheck = pathToCheck.getParent()) {
            if (Files.isSymbolicLink(pathToCheck)) {
                throw new IOException("Instance DSH_HOME path cannot contain a symbolic link.");
            }
        }
        Path root = instanceHome.resolve("skills");
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Instance skills directory cannot be a symbolic link.");
        }
        Files.createDirectories(root);
        Path target = root.resolve(name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("This instance already contains skill " + name + ".");
        }
        Path staging = root.resolve("." + name + "-install-" + UUID.randomUUID());
        try {
            @Unmodifiable List<Path> entries;
            try (Stream<Path> walk = Files.walk(source)) {
                entries = walk.limit(MAX_ENTRIES + 1L).toList();
            }
            if (entries.size() > MAX_ENTRIES) {
                throw new IOException("Skill exceeds the import entry limit.");
            }
            long total = 0;
            for (Path entry : entries) {
                if (Files.isSymbolicLink(entry)) throw new IOException("Skill contains a symbolic link.");
                if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    total += Files.size(entry);
                    if (total > MAX_BYTES) {
                        throw new IOException("Skill exceeds the import size limit.");
                    }
                } else if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Skill contains an unsupported filesystem entry.");
                }
            }
            for (Path entry : entries) {
                Path destination = staging.resolve(source.relativize(entry));
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination);
                else Files.copy(entry, destination);
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staging, target);
            }
            return target;
        } finally {
            if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> walk = Files.walk(staging)) {
                    for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
                }
            }
        }
    }

    /// Checks the required DSH skill header before reporting the copied folder as installed.
    private static void validateManifest(String contents, String folderName) throws IOException {
        String[] lines = contents.split("\\R", 101);
        if (lines.length < 4 || !"---".equals(lines[0].trim())) {
            throw new IOException("SKILL.md needs YAML frontmatter.");
        }
        String name = "";
        String description = "";
        boolean closed = false;
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if ("---".equals(line)) {
                closed = true;
                break;
            }
            if (line.startsWith("name:")) name = unquote(line.substring(5).trim());
            if (line.startsWith("description:")) description = unquote(line.substring(12).trim());
        }
        if (!closed || !folderName.equals(name) || description.isBlank()) {
            throw new IOException("SKILL.md needs a matching name and nonempty description.");
        }
    }

    /// Removes simple YAML string quotes from one scalar value.
    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
