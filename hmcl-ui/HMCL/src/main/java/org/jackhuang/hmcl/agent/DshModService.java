/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

/// Runs DSH's own plugin command against one isolated instance Profile.
@NotNullByDefault
public final class DshModService {
    /// Maximum duration of one plugin operation.
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(180);
    /// Maximum duration of an isolated Core installation.
    private static final Duration CORE_TIMEOUT = Duration.ofSeconds(600);
    /// Maximum output retained for a single operation.
    private static final int OUTPUT_LIMIT = 1024 * 1024;

    /// Supported Profile package operations.
    public enum Action {
        INSTALL, REMOVE, UPDATE
    }

    /// Result returned only after the DSH command exits successfully.
    @NotNullByDefault
    public record Result(String output, boolean restartRequired, String coreVersion,
                         @Nullable String installedPackageName) {
    }

    /// Prevents construction of a stateless service.
    private DshModService() {
    }

    /// Returns the configured isolated Core cache, or the DSHCraft-managed default.
    public static Path runtimeRoot() {
        String configured = System.getProperty("dshcraft.runtime.root", "").trim();
        return configured.isEmpty()
                ? Metadata.HMCL_USER_HOME.resolve("dshcraft").resolve("runtimes")
                : Path.of(configured).toAbsolutePath().normalize();
    }

    /// Resolves an already installed Core without silently using a global dsh executable.
    public static Path installedCoreCli(Path runtimes, String version) throws IOException {
        validateVersion(version);
        Path cli = runtimes.toAbsolutePath().normalize().resolve(version)
                .resolve("node_modules").resolve("@deepseek-ai")
                .resolve("dsh").resolve("lib").resolve("bin.js");
        if (!Files.isRegularFile(cli)) {
            throw new IOException("DSH Core " + version + " is not installed. Install it from the instance page first.");
        }
        return cli;
    }

    /// Reports whether a concrete Core is ready; the `latest` channel still needs resolution.
    public static boolean isCoreInstalled(Path runtimes, String version) throws IOException {
        if ("latest".equals(version)) return false;
        validateVersion(version);
        return Files.isRegularFile(runtimes.toAbsolutePath().normalize().resolve(version)
                .resolve("node_modules").resolve("@deepseek-ai")
                .resolve("dsh").resolve("lib").resolve("bin.js"));
    }

    /// Lists complete, concrete Core versions already present in the managed runtime cache.
    public static @Unmodifiable List<String> installedCoreVersions(Path runtimes) throws IOException {
        Path root = runtimes.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) return List.of();
        List<String> versions = new ArrayList<>();
        try (var entries = Files.list(root)) {
            for (Path runtime : entries.toList()) {
                String version = runtime.getFileName().toString();
                try {
                    validateVersion(version);
                } catch (IOException invalid) {
                    continue;
                }
                if (Files.isSymbolicLink(runtime) || !Files.isDirectory(runtime, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                Path cli = runtime.resolve("node_modules/@deepseek-ai/dsh/lib/bin.js");
                if (!Files.isRegularFile(cli, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                Path current = runtime;
                boolean linked = false;
                for (String part : List.of("node_modules", "@deepseek-ai", "dsh", "lib", "bin.js")) {
                    current = current.resolve(part);
                    if (Files.isSymbolicLink(current)) {
                        linked = true;
                        break;
                    }
                }
                if (!linked) versions.add(version);
            }
        }
        versions.sort((first, second) -> VersionNumber.compare(second, first));
        return List.copyOf(versions);
    }

    /// Returns the configured Node executable used with an isolated Core CLI.
    public static String managedNodeExecutable() {
        return nodeExecutable();
    }

    /// Lets Windows resolve shipped Profile modules from the isolated Core package.
    public static void configureWindowsModuleFallback(ProcessBuilder builder, Path cli) throws IOException {
        if (!isWindows()) return;
        Path runtime = cli.getParent().getParent().getParent().getParent().getParent();
        Path packageFile = runtime.resolve("package.json");
        Path loader = runtime.resolve("launcher-module-fallback.mjs");
        Files.writeString(loader, """
                import { pathToFileURL } from 'node:url'

                const fallbackPackage = process.env.DSH_LAUNCHER_RUNTIME_PACKAGE_JSON
                const fallbackParent = fallbackPackage ? pathToFileURL(fallbackPackage).href : undefined

                export async function resolve(specifier, context, nextResolve) {
                  try {
                    return await nextResolve(specifier, context)
                  } catch (error) {
                    const bare = !specifier.startsWith('.') && !specifier.startsWith('/') && !specifier.includes(':')
                    if (!fallbackParent || !bare || context.parentURL === fallbackParent) throw error
                    return nextResolve(specifier, { ...context, parentURL: fallbackParent })
                  }
                }
                """, StandardCharsets.UTF_8);
        String existing = builder.environment().getOrDefault("NODE_OPTIONS", "").trim();
        String option = "--experimental-loader=" + loader.toUri();
        builder.environment().put("NODE_OPTIONS", existing.isEmpty() ? option : existing + " " + option);
        builder.environment().put("DSH_LAUNCHER_RUNTIME_PACKAGE_JSON", packageFile.toString());
    }

    /// Runs an operation for an instance, using its own DSH_HOME.
    public static Result run(AgentRepository repository, AgentInstance instance,
                             @Nullable AgentExtension extension, Action action)
            throws IOException, InterruptedException {
        String spec = extension == null ? null : extension.locationProperty().get();
        return run(runtimeRoot(), repository.getInstanceHome(instance),
                instance.coreVersionProperty().get(), instance.profileNameProperty().get(), spec, action,
                repository.getPackageRegistry());
    }

    /// Runs a Profile operation with explicit roots, allowing disposable integration tests.
    public static Result run(Path runtimes, Path home, String version, String profile,
                             @Nullable String spec, Action action) throws IOException, InterruptedException {
        return run(runtimes, home, version, profile, spec, action, AgentNetworkService.OFFICIAL_REGISTRY);
    }

    /// Runs a Profile operation against the selected npm-compatible package registry.
    public static Result run(Path runtimes, Path home, String version, String profile,
                             @Nullable String spec, Action action, String registry) throws IOException, InterruptedException {
        validateProfile(profile);
        @Nullable String localPackageName = action == Action.INSTALL && spec != null && isLocalTarball(spec)
                ? packageNameFromTarball(Path.of(spec)) : null;
        String normalizedRegistry = AgentNetworkService.validateRegistryUrlForCommand(registry);
        String resolvedVersion = resolveCoreVersionForMod(runtimes, version, normalizedRegistry);
        Path runtime = ensureCore(runtimes, resolvedVersion, normalizedRegistry);
        String concreteVersion = runtime.getFileName().toString();
        Path cli = runtime.resolve("node_modules")
                .resolve("@deepseek-ai").resolve("dsh").resolve("lib").resolve("bin.js");
        List<String> command = new ArrayList<>();
        command.add(nodeExecutable());
        command.add(cli.toString());
        command.add("plugin");
        command.add("--profile");
        command.add(profile);
        if (action == Action.UPDATE && (spec == null || spec.isBlank())) {
            command.add("update");
        } else {
            String packageSpec = validateSpec(spec);
            switch (action) {
                case INSTALL -> {
                    command.add("add");
                    command.add(pinOfficial(packageSpec, concreteVersion));
                }
                case REMOVE -> {
                    command.add("remove");
                    command.add(packageName(packageSpec));
                }
                case UPDATE -> {
                    command.add("update");
                    command.add(packageName(packageSpec));
                }
            }
        }

        Files.createDirectories(home);
        Path cwd = home.getParent();
        if (cwd == null) throw new IOException("Instance DSH_HOME has no parent directory");
        Files.createDirectories(cwd);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("DSH_HOME", home.toString());
        builder.environment().put("PNPM_CONFIG_STORE_DIR",
                home.resolve("profiles").resolve(profile).resolve(".pnpm-store").toString());
        builder.environment().put("PNPM_CONFIG_PACKAGE_IMPORT_METHOD", "copy");
        builder.environment().put("PNPM_CONFIG_NODE_LINKER", "hoisted");
        builder.environment().put("PNPM_CONFIG_REGISTRY", normalizedRegistry);
        String output = runCommand(builder, COMMAND_TIMEOUT, "dsh plugin");
        if (spec != null && !spec.isBlank()) {
            boolean installed = installedPackages(home, profile).contains(
                    localPackageName == null ? packageName(spec.trim()) : localPackageName);
            if (installed == (action == Action.REMOVE)) {
                throw new IOException("dsh plugin finished but Profile package.json did not reflect the Mod change");
            }
        }
        return new Result(output, true, concreteVersion, localPackageName);
    }

    /// Resolves `latest` for a Mod operation, using a complete cached Core if the catalog is unreachable.
    static String resolveCoreVersionForMod(Path runtimes, String version, String registry)
            throws IOException, InterruptedException {
        if (!"latest".equals(version)) return version;
        try {
            return AgentNetworkService.fetchDshCatalog(registry).latest();
        } catch (IOException catalogError) {
            if (!AgentNetworkService.isTransientTransportFailure(catalogError)) throw catalogError;
            List<String> cached = installedCoreVersions(runtimes);
            if (cached.isEmpty()) {
                throw new IOException("Cannot resolve latest DSH Core and no downloaded version is available", catalogError);
            }
            return cached.get(0);
        }
    }

    /// Recognizes an absolute npm package archive passed through the native plugin CLI.
    private static boolean isLocalTarball(String spec) {
        try {
            Path path = Path.of(spec);
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return path.isAbsolute() && (name.endsWith(".tgz") || name.endsWith(".tar.gz"));
        } catch (RuntimeException error) {
            return false;
        }
    }

    /// Reads the npm package identity from a local archive before using it as a DSH plugin spec.
    public static String packageNameFromTarball(Path archive) throws IOException {
        if (!Files.isRegularFile(archive, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.size(archive) > 128L * 1024 * 1024)
            throw new IOException("Local npm package must be a regular archive under 128 MiB");
        try (InputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
            long scanned = 0;
            for (int entry = 0; entry < 4096; entry++) {
                byte[] header = input.readNBytes(512);
                if (header.length != 512) break;
                String path = tarText(header, 0, 100);
                if (path.isEmpty()) break;
                long size;
                try {
                    size = Long.parseLong(tarText(header, 124, 12).trim(), 8);
                } catch (NumberFormatException error) {
                    throw new IOException("Invalid npm archive entry size", error);
                }
                if (size < 0 || size > 256L * 1024 * 1024) throw new IOException("npm archive entry is too large");
                long padded = (size + 511) / 512 * 512;
                scanned += 512 + padded;
                if (scanned > 256L * 1024 * 1024) throw new IOException("npm archive is too large");
                if ("package/package.json".equals(path)) {
                    if (size > 1024 * 1024) throw new IOException("npm package.json is too large");
                    byte[] manifest = input.readNBytes((int) size);
                    if (manifest.length != size) throw new IOException("Truncated npm package.json");
                    try {
                        JsonObject json = JsonParser.parseString(new String(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
                        String name = json.get("name").getAsString();
                        if (name.matches("(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*")) return name;
                    } catch (RuntimeException error) {
                        throw new IOException("Invalid npm package.json", error);
                    }
                    throw new IOException("Invalid npm package name in archive");
                }
                input.skipNBytes(padded);
            }
        } catch (java.util.zip.ZipException error) {
            throw new IOException("Invalid npm package archive", error);
        }
        throw new IOException("npm archive has no package/package.json");
    }

    /// Decodes a fixed-width TAR header field without interpreting archive file contents.
    private static String tarText(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) end++;
        return new String(header, offset, end - offset, StandardCharsets.US_ASCII);
    }

    /// Installs a concrete npm DSH Core into the launcher-managed runtime cache.
    public static synchronized Path ensureCore(Path runtimes, String requestedVersion)
            throws IOException, InterruptedException {
        return ensureCore(runtimes, requestedVersion, AgentNetworkService.OFFICIAL_REGISTRY);
    }

    /// Installs a concrete npm DSH Core from the selected package registry.
    public static synchronized Path ensureCore(Path runtimes, String requestedVersion, String registry)
            throws IOException, InterruptedException {
        String version = requestedVersion;
        if ("latest".equals(version)) {
            version = AgentNetworkService.fetchDshCatalog(registry).latest();
        }
        String normalizedRegistry = AgentNetworkService.validateRegistryUrlForCommand(registry);
        validateVersion(version);
        Path root = runtimes.toAbsolutePath().normalize();
        Path runtime = root.resolve(version);
        Path cli = runtime.resolve("node_modules").resolve("@deepseek-ai")
                .resolve("dsh").resolve("lib").resolve("bin.js");
        if (Files.isRegularFile(cli)) return runtime;
        if (Files.exists(runtime)) {
            throw new IOException("Incomplete DSH Core runtime already exists: " + runtime);
        }
        Files.createDirectories(root);
        String node = nodeExecutable();
        String pnpm = System.getProperty("dshcraft.pnpm.executable",
                isWindows() ? "pnpm.cmd" : "pnpm");
        String nodeVersion = runCommand(new ProcessBuilder(node, "--version"),
                Duration.ofSeconds(15), "Node.js version check").trim().replaceFirst("^v", "");
        String[] parts = nodeVersion.split("\\.");
        if (parts.length < 2) throw new IOException("Cannot parse Node.js version");
        int major;
        int minor;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
        } catch (NumberFormatException error) {
            throw new IOException("Cannot parse Node.js version", error);
        }
        if (!((major == 22 && minor >= 19) || (major == 24 && minor >= 2) || major > 24)) {
            throw new IOException("DSH requires Node 22.19+ (22.x) or Node 24.2+");
        }

        Path staging = Files.createTempDirectory(root, "." + version + ".installing-");
        try {
            Path selfCheckHome = staging.resolve("self-check-home");
            Files.createDirectories(selfCheckHome);
            JsonObject manifest = new JsonObject();
            manifest.addProperty("name", "dshcraft-runtime");
            manifest.addProperty("private", true);
            JsonObject dependencies = new JsonObject();
            dependencies.addProperty("@deepseek-ai/dsh", version);
            manifest.add("dependencies", dependencies);
            Files.writeString(staging.resolve("package.json"), manifest.toString(), StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("pnpm-workspace.yaml"),
                    "allowBuilds:\n  '@deepseek-ai/dsh-subprocess-local': true\n"
                            + "  '@google/genai': false\n  koffi: true\n"
                            + "  node-addon-require-builtin: false\n"
                            + "  node-pty: true\n  protobufjs: false\n",
                    StandardCharsets.UTF_8);
            Path store = staging.resolve(".pnpm-store");
            ProcessBuilder install = new ProcessBuilder(pnpm, "install", "--prod");
            install.directory(staging.toFile());
            install.redirectErrorStream(true);
            install.environment().put("PNPM_CONFIG_AUTO_INSTALL_PEERS", "true");
            install.environment().put("PNPM_CONFIG_STORE_DIR", store.toString());
            install.environment().put("PNPM_CONFIG_PACKAGE_IMPORT_METHOD", "copy");
            install.environment().put("PNPM_CONFIG_NODE_LINKER", "hoisted");
            install.environment().put("PNPM_CONFIG_REGISTRY", normalizedRegistry);
            install.environment().put("DSH_HOME", selfCheckHome.toString());
            runCommand(install, CORE_TIMEOUT, "pnpm install DSH Core");
            Path stagedCli = staging.resolve("node_modules").resolve("@deepseek-ai")
                    .resolve("dsh").resolve("lib").resolve("bin.js");
            if (!Files.isRegularFile(stagedCli)) {
                throw new IOException("Core install finished without the DSH CLI");
            }
            ProcessBuilder verify = new ProcessBuilder(node, stagedCli.toString(), "--help");
            verify.directory(staging.toFile());
            verify.environment().put("DSH_HOME", selfCheckHome.toString());
            runCommand(verify, Duration.ofSeconds(45), "DSH Core self-check");
            if (Files.exists(store)) removeGeneratedTree(root, store);
            if (Files.exists(selfCheckHome)) removeGeneratedTree(root, selfCheckHome);
            Files.move(staging, runtime);
            return runtime;
        } finally {
            if (Files.exists(staging)) removeGeneratedTree(root, staging);
        }
    }

    /// Removes only a generated staging/store directory within the managed runtime root.
    private static void removeGeneratedTree(Path root, Path target) throws IOException {
        Path checkedRoot = root.toAbsolutePath().normalize();
        Path checkedTarget = target.toAbsolutePath().normalize();
        if (!checkedTarget.startsWith(checkedRoot) || checkedTarget.equals(checkedRoot)) {
            throw new IOException("Refusing to remove a path outside the DSH runtime cache");
        }
        Files.walkFileTree(checkedTarget, new SimpleFileVisitor<>() {
            /// Deletes one generated file without following filesystem links.
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            /// Deletes a generated directory after its contents have been visited.
            @Override
            public FileVisitResult postVisitDirectory(Path directory, @Nullable IOException error)
                    throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /// Reads real Profile dependencies; missing Profiles have no installed external Mods.
    public static @Unmodifiable Set<String> installedPackages(Path home, String profile) throws IOException {
        return installedPackageVersions(home, profile).keySet();
    }

    /// Reads installed npm package names and resolved versions from one isolated DSH Profile.
    public static @Unmodifiable Map<String, String> installedPackageVersions(Path home, String profile) throws IOException {
        validateProfile(profile);
        Path packageFile = home.resolve("profiles").resolve(profile).resolve("package.json");
        if (!Files.isRegularFile(packageFile)) return Map.of();
        try (Reader input = Files.newBufferedReader(packageFile, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(input);
            if (!root.isJsonObject()) throw new IOException("DSH Profile package.json must be an object");
            JsonObject dependencies = root.getAsJsonObject().getAsJsonObject("dependencies");
            if (dependencies == null) return Map.of();
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : dependencies.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    String declared = entry.getValue().getAsString();
                    String installed = declared;
                    if (declared.startsWith("file:")
                            && entry.getKey().matches("(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*")) {
                        Path manifest = packageFile.getParent().resolve("node_modules")
                                .resolve(entry.getKey()).resolve("package.json");
                        if (Files.isRegularFile(manifest)) {
                            try (Reader packageInput = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
                                JsonObject packageJson = JsonParser.parseReader(packageInput).getAsJsonObject();
                                JsonElement version = packageJson.get("version");
                                if (version != null && version.isJsonPrimitive()
                                        && version.getAsJsonPrimitive().isString())
                                    installed = version.getAsString();
                            } catch (IOException | RuntimeException ignored) {
                                // Keep the Profile declaration if the installed manifest is unavailable.
                            }
                        }
                    }
                    result.put(entry.getKey(), installed);
                }
            }
            return Map.copyOf(result);
        } catch (IllegalStateException | com.google.gson.JsonParseException error) {
            throw new IOException("Invalid DSH Profile package.json", error);
        }
    }

    /// Pins official optional Bundles to the selected DSH Core unless already versioned.
    static String pinOfficial(String spec, String version) {
        if (!spec.startsWith("@deepseek-ai/dsh-")) return spec;
        int slash = spec.indexOf('/');
        return spec.substring(slash + 1).contains("@") ? spec : spec + "@" + version;
    }

    /// Removes a package by name rather than by its install-version suffix.
    public static String packageName(String spec) {
        if (spec.startsWith("@")) {
            int slash = spec.indexOf('/');
            if (slash > 0) {
                int suffix = spec.indexOf('@', slash + 1);
                return suffix < 0 ? spec : spec.substring(0, suffix);
            }
        } else if (!spec.contains(":")) {
            int suffix = spec.indexOf('@');
            return suffix < 0 ? spec : spec.substring(0, suffix);
        }
        return spec;
    }

    /// Rejects unsupported or unsafe Profile names before any filesystem access.
    static void validateProfile(String profile) throws IOException {
        if (profile == null || !profile.matches("[A-Za-z0-9._-]{1,128}")
                || profile.contains("..") || profile.endsWith(".")
                || profile.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$")) {
            throw new IOException("Invalid DSH Profile name");
        }
    }

    /// Keeps instance directory names portable and rejects Windows device aliases.
    static void validateInstanceId(String id) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")
                || id.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")) {
            throw new IOException("Invalid DSH instance ID");
        }
    }

    /// Requires a concrete npm SemVer rather than a mutable npm dist-tag.
    static void validateVersion(String version) throws IOException {
        if (version == null || !version.matches(
                "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?")) {
            throw new IOException("Select a published DSH Core version before managing Mods");
        }
    }

    /// Rejects package references that could leak credentials into CLI logs or Profile metadata.
    static String validateSpec(@Nullable String spec) throws IOException {
        if (spec == null || spec.isBlank() || spec.indexOf('\n') >= 0 || spec.indexOf('\r') >= 0) {
            throw new IOException("Mod package spec is empty or invalid");
        }
        String value = spec.trim();
        if (value.matches("(?i).*(?:api[-_]?key|token|secret|password|auth)\\s*[:=].*")) {
            throw new IOException("Put package credentials in the OS credential store, not a package spec");
        }
        if (value.matches("(?i)^(?:git\\+)?https?://.*")) {
            try {
                URI uri = URI.create(value.replaceFirst("(?i)^git\\+", ""));
                if (uri.getUserInfo() != null || uri.getRawQuery() != null) {
                    throw new IOException("Package URLs must not include credentials or query parameters");
                }
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid Mod package URL", error);
            }
        }
        return value;
    }

    /// Captures bounded output while enforcing the deadline after the direct process exits.
    private static String runCommand(ProcessBuilder builder, Duration timeout, String label)
            throws IOException, InterruptedException {
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicReference<IOException> readError = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    synchronized (output) {
                        int remaining = OUTPUT_LIMIT - output.size();
                        if (remaining > 0) output.write(buffer, 0, Math.min(count, remaining));
                    }
                }
            } catch (IOException error) {
                readError.set(error);
            }
        }, "dshcraft-mod-output");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                throw new IOException(label + " exceeded " + timeout.toSeconds() + " seconds");
            }
            reader.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            if (reader.isAlive()) {
                terminate(process);
                throw new IOException(label + " output stream remained open past the deadline");
            }
            if (readError.get() != null) throw readError.get();
            String text = output.toString(StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException(label + " failed (exit " + process.exitValue() + "):\n" + text);
            }
            return text;
        } catch (InterruptedException interrupted) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    /// Stops the command and any still-visible descendants on timeout or interruption.
    private static void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
        if (isWindows()) {
            try {
                new ProcessBuilder("taskkill", "/PID", Long.toString(process.pid()), "/T", "/F")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start().waitFor(5, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
    }

    /// Reports whether Windows requires the `.exe` launcher shim.
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /// Returns the Node executable selected for managed DSH subprocesses.
    private static String nodeExecutable() {
        return System.getProperty("dshcraft.node.executable", isWindows() ? "node.exe" : "node");
    }
}
