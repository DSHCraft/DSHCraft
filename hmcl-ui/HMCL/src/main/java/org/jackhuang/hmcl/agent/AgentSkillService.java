/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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
    /// A remote single-file skill cannot carry bundled resources.
    private static final int MAX_REMOTE_SKILL_BYTES = 1024 * 1024;

    /// Prevents instantiation of the filesystem service.
    private AgentSkillService() {
    }

    /// Downloads one public SKILL.md and installs it through the same isolated path as a local bundle.
    public static Path download(URI source, Path instanceHome) throws IOException, InterruptedException {
        String name = remoteSkillName(source);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(20))
                .header("Accept", "text/markdown, text/plain").GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] contents;
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Skill download returned HTTP " + response.statusCode());
            contents = body.readNBytes(MAX_REMOTE_SKILL_BYTES + 1);
        }
        if (contents.length > MAX_REMOTE_SKILL_BYTES) throw new IOException("Remote SKILL.md is too large.");
        Path temporary = Files.createTempDirectory("dshcraft-skill-");
        Path bundle = temporary.resolve(name);
        try {
            Files.createDirectory(bundle);
            Files.write(bundle.resolve("SKILL.md"), contents);
            return install(bundle, instanceHome);
        } finally {
            Files.deleteIfExists(bundle.resolve("SKILL.md"));
            Files.deleteIfExists(bundle);
            Files.deleteIfExists(temporary);
        }
    }

    /// Accepts public single-file skill URLs without embedded credentials or query secrets.
    public static String remoteSkillName(@Nullable URI source) throws IOException {
        if (source == null || source.getHost() == null || source.getUserInfo() != null
                || source.getRawQuery() != null || source.getRawFragment() != null
                || !("https".equalsIgnoreCase(source.getScheme())
                    || "http".equalsIgnoreCase(source.getScheme())
                    && ("localhost".equalsIgnoreCase(source.getHost())
                        || "127.0.0.1".equals(source.getHost()) || "::1".equals(source.getHost()))))
            throw new IOException("Skill URL must be HTTPS or local HTTP without credentials or query data.");
        @Nullable String path = source.getPath();
        if (path == null || !path.endsWith("/SKILL.md"))
            throw new IOException("Skill URL must end with /<name>/SKILL.md.");
        int end = path.length() - "/SKILL.md".length();
        int start = path.lastIndexOf('/', end - 1) + 1;
        String name = path.substring(start, end);
        if (!name.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IOException("Skill URL must contain a kebab-case folder name.");
        return name;
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
        @Nullable Path pathToCheck = instanceHome.toAbsolutePath().normalize();
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
