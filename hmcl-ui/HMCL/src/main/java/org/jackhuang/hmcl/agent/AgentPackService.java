/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/// Reads and writes the JSON-based `.dshpack` format used by the earlier browser prototype.
@NotNullByDefault
public final class AgentPackService {
    /// Pack format marker retained for compatibility with the v0.4 browser implementation.
    public static final String FORMAT = "dsh-launcher-pack";
    /// Current `.dshpack` schema version.
    public static final int FORMAT_VERSION = 1;
    /// Maximum accepted pack file size.
    private static final long MAX_PACK_BYTES = 4L * 1024L * 1024L;

    /// Prevents construction of this utility class.
    private AgentPackService() {
    }

    /// Exports one instance using the default portable selection: all safe metadata, no Provider route or API key.
    public static void exportInstance(AgentRepository repository, AgentInstance instance, Path output) throws IOException {
        exportInstance(repository, instance, output, ExportOptions.defaults(instance));
    }

    /// Exports one instance using an explicit selection without workspace paths, secrets or sessions.
    public static void exportInstance(AgentRepository repository, AgentInstance instance, Path output,
                                      ExportOptions options) throws IOException {
        Set<String> referenced = new LinkedHashSet<>(parseIds(options.extensionIds()));
        List<PackExtension> extensions = new ArrayList<>();
        for (String id : referenced) {
            AgentExtension extension = repository.getExtensions().stream()
                    .filter(candidate -> id.equals(candidate.getId())).findFirst()
                    .orElseThrow(() -> new IOException("Pack references an unknown Mod: " + id));
            String kind = normalize(extension.typeProperty().get(), "");
            String spec;
            if ("plugin".equalsIgnoreCase(kind) || "bundle".equalsIgnoreCase(kind)) {
                spec = portablePackageSpec(extension.locationProperty().get());
            } else if (Set.of("filesystem", "browser", "skills", "mcp-client").contains(id)) {
                spec = "";
            } else {
                throw new IOException("Pack cannot export local MCP/Skill/Tool configuration: " + id);
            }
            extensions.add(new PackExtension(id, extension.getName(), kind.toLowerCase(Locale.ROOT),
                    spec, repository.hasExtension(instance, extension)));
        }
        String coreVersion = options.includeCore() ? normalize(options.coreVersion(), "latest") : "";
        String profileName = options.includeProfile() ? normalize(options.profileName(), "web") : "";
        String profileTemplate = options.includeProfile() ? normalize(options.profileTemplate(), "web") : "";
        String webPort = options.includeProfile() ? normalize(options.webPort(), "3080") : "";
        PackMetadata pack = new PackMetadata(
                "export-" + instance.getId(),
                normalize(options.name(), instance.getName() + " Pack"),
                normalize(options.description(), instance.descriptionProperty().get()),
                coreVersion,
                options.includeModel() ? normalize(options.model(), "") : "",
                List.copyOf(referenced),
                List.of("export"),
                profileName,
                profileTemplate,
                webPort);
        ProviderMetadata provider = null;
        if (options.includeProvider()) {
            AgentProvider selected = repository.findProvider(instance.providerIdProperty().get());
            if (selected == null) throw new IOException("Pack Provider is no longer available: " + instance.providerIdProperty().get());
            provider = new ProviderMetadata(selected.getId(), selected.getName(), selected.typeProperty().get(),
                    selected.baseUrlProperty().get(), selected.modelProperty().get(),
                    selected.apiKeyEnvProperty().get(), selected.protocolProperty().get());
        }
        PackArchive archive = new PackArchive(
                FORMAT,
                FORMAT_VERSION,
                Metadata.VERSION,
                Instant.now().toString(),
                pack,
                extensions,
                provider);
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent == null ? Path.of(".").toAbsolutePath() : parent,
                ".dshcraft-pack-", ".part");
        try {
            Files.writeString(staged, JsonUtils.GSON.toJson(archive), StandardCharsets.UTF_8);
            Files.move(staged, absolute, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(staged, absolute, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /// Validates a `.dshpack` and prepares a new instance without changing repository state.
    public static PreparedPack prepareImport(AgentRepository repository, Path input) throws IOException {
        if (!Files.isRegularFile(input)) {
            throw new IOException("Pack file does not exist: " + input);
        }
        long size = Files.size(input);
        if (size <= 0 || size > MAX_PACK_BYTES) {
            throw new IOException("Pack size is invalid: " + size + " bytes");
        }
        PackArchive archive;
        try {
            archive = JsonUtils.GSON.fromJson(Files.readString(input, StandardCharsets.UTF_8), PackArchive.class);
        } catch (RuntimeException e) {
            throw new IOException("Pack JSON is malformed", e);
        }
        validate(archive);
        PackMetadata pack = archive.pack();

        Map<String, AgentExtension> existing = repository.getExtensions().stream()
                .collect(Collectors.toMap(AgentExtension::getId, extension -> extension, (first, second) -> first));
        Map<String, PackExtension> incoming = archive.plugins().stream()
                .collect(Collectors.toMap(PackExtension::id, extension -> extension));
        List<String> installSpecs = new ArrayList<>();
        for (String extensionId : pack.pluginIds()) {
            AgentExtension current = existing.get(extensionId);
            PackExtension supplied = incoming.get(extensionId);
            if (current == null && supplied == null) {
                throw new IOException("Pack references extension metadata that is not available: " + extensionId);
            }
            if (current != null && supplied != null && !normalize(supplied.packageSpec(), "").isBlank()
                    && !normalize(supplied.packageSpec(), "").equals(normalize(current.locationProperty().get(), ""))) {
                throw new IOException("Pack Mod ID conflicts with the existing catalog: " + extensionId);
            }
            String kind = current == null ? normalize(supplied.kind(), "") : normalize(current.typeProperty().get(), "");
            String spec = current == null ? normalize(supplied.packageSpec(), "") : normalize(current.locationProperty().get(), "");
            if ("plugin".equalsIgnoreCase(kind) || "bundle".equalsIgnoreCase(kind)) {
                installSpecs.add(portablePackageSpec(spec));
            } else if (!Set.of("filesystem", "browser", "skills", "mcp-client").contains(extensionId) || !spec.isBlank()) {
                throw new IOException("Pack Mod requires manual Profile configuration: " + extensionId);
            }
        }
        List<AgentExtension> newExtensions = new ArrayList<>();
        for (PackExtension extension : archive.plugins()) {
            if (!existing.containsKey(extension.id())) {
                newExtensions.add(new AgentExtension(extension.id(), extension.name(),
                        normalize(extension.kind(), "Plugin"), normalize(extension.packageSpec(), ""), false));
            }
        }

        List<AgentProvider> newProviders = new ArrayList<>();
        AgentProvider provider = archive.provider() == null ? null : repository.findProvider(archive.provider().id());
        if (provider == null && archive.provider() != null) {
            ProviderMetadata metadata = archive.provider();
            provider = new AgentProvider(metadata.id(), metadata.name(), metadata.type(), metadata.baseUrl(),
                    metadata.model(), metadata.apiKeyEnv(), metadata.protocol());
            newProviders.add(provider);
        }
        if (provider == null) provider = repository.getSelectedProvider();
        if (provider == null && !repository.getProviders().isEmpty()) {
            provider = repository.getProviders().get(0);
        }
        String providerId = provider == null ? "default" : provider.getId();
        String instanceModel = normalize(pack.model(), provider == null ? "" : provider.modelProperty().get());
        int port = availablePort(repository, pack.webPort());
        AgentInstance instance = new AgentInstance(
                uniqueId(),
                stripPackSuffix(pack.name()),
                providerId,
                pack.description(),
                normalize(pack.coreVersion(), "latest"),
                normalize(pack.profileName(), "web"),
                normalize(pack.profileTemplate(), "web"),
                Integer.toString(port),
                instanceModel,
                String.join(",", pack.pluginIds()),
                "dsh",
                "",
                "");
        return new PreparedPack(instance, List.copyOf(newExtensions), List.copyOf(installSpecs), List.copyOf(newProviders));
    }

    /// Installs the selected Core and package-backed Mods before the instance becomes visible.
    public static String installPrepared(PreparedPack prepared, Path runtimes, Path instancesRoot)
            throws IOException, InterruptedException {
        DshModService.validateInstanceId(prepared.instance().getId());
        Path root = instancesRoot.toAbsolutePath().normalize();
        Path instanceDir = root.resolve(prepared.instance().getId()).normalize();
        if (!instanceDir.startsWith(root) || instanceDir.equals(root) || Files.exists(instanceDir)) {
            throw new IOException("Pack instance directory is unsafe or already exists");
        }
        Path home = instanceDir.resolve("dsh-home");
        try {
            String registry = AgentRepository.get().getPackageRegistry();
            Path runtime = DshModService.ensureCore(runtimes,
                    prepared.instance().coreVersionProperty().get(), registry);
            String version = runtime.getFileName().toString();
            Files.createDirectories(home);
            for (String spec : prepared.installSpecs()) {
                DshModService.run(runtimes, home, version,
                        prepared.instance().profileNameProperty().get(), spec,
                        DshModService.Action.INSTALL, registry);
            }
            return version;
        } catch (IOException | InterruptedException error) {
            if (Files.exists(instanceDir)) {
                try {
                    removeGeneratedTree(root, instanceDir);
                } catch (IOException cleanup) {
                    error.addSuppressed(cleanup);
                }
            }
            throw error;
        }
    }

    /// Commits a fully installed Pack on the JavaFX thread.
    public static AgentInstance commitInstalled(AgentRepository repository, PreparedPack prepared, String version)
            throws IOException {
        Set<String> installed = DshModService.installedPackages(repository.getInstanceHome(prepared.instance()),
                prepared.instance().profileNameProperty().get());
        for (String spec : prepared.installSpecs()) {
            if (!installed.contains(DshModService.packageName(spec))) {
                throw new IOException("Pack installation did not appear in the DSH Profile: " + DshModService.packageName(spec));
            }
        }
        prepared.instance().coreVersionProperty().set(version);
        for (AgentExtension extension : prepared.newExtensions()) {
            repository.addImportedExtension(extension);
        }
        for (AgentProvider provider : prepared.newProviders()) {
            repository.addImportedProvider(provider);
        }
        return repository.addImportedInstance(prepared.instance());
    }

    /// Removes a prepared Pack's generated instance data if post-install verification cannot commit it.
    public static void discardPrepared(PreparedPack prepared, Path instancesRoot) throws IOException {
        DshModService.validateInstanceId(prepared.instance().getId());
        Path root = instancesRoot.toAbsolutePath().normalize();
        Path target = root.resolve(prepared.instance().getId()).normalize();
        if (Files.exists(target)) removeGeneratedTree(root, target);
    }

    /// Accepts portable package specs but never imports local files or credential-bearing URLs.
    static String portablePackageSpec(String spec) throws IOException {
        String checked = DshModService.validateSpec(spec);
        if (checked.matches("(?i)^(?:file:|link:|workspace:|[A-Za-z]:[\\\\/]|[./~]|\\\\\\\\).*")) {
            throw new IOException("Pack cannot install a local package reference");
        }
        return checked;
    }

    /// Selects the first unused Web port at or above the Pack's requested port.
    private static int availablePort(AgentRepository repository, String requested) throws IOException {
        int port;
        try {
            port = Integer.parseInt(normalize(requested, "3080"));
        } catch (NumberFormatException error) {
            throw new IOException("Pack Web port is invalid", error);
        }
        if (port < 1024 || port > 65535) throw new IOException("Pack Web port is invalid");
        Set<Integer> used = new LinkedHashSet<>();
        for (AgentInstance instance : repository.getInstances()) {
            try {
                used.add(Integer.parseInt(instance.webPortProperty().get()));
            } catch (NumberFormatException ignored) {
                // Existing invalid metadata does not make a valid new Pack port unusable.
            }
        }
        while (used.contains(port) && port < 65535) port++;
        if (used.contains(port)) throw new IOException("No free Pack Web port is available");
        return port;
    }

    /// Deletes only the new instance directory under the explicit managed root after failure.
    private static void removeGeneratedTree(Path root, Path target) throws IOException {
        Path checkedRoot = root.toAbsolutePath().normalize();
        Path checkedTarget = target.toAbsolutePath().normalize();
        if (!checkedTarget.startsWith(checkedRoot) || checkedTarget.equals(checkedRoot)) {
            throw new IOException("Refusing to remove data outside the managed instance root");
        }
        Files.walkFileTree(checkedTarget, new SimpleFileVisitor<>() {
            /// Removes a generated file without following symbolic links.
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            /// Removes each generated directory after its children.
            @Override
            public FileVisitResult postVisitDirectory(Path directory, java.io.IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /// Splits the compact comma-separated extension list used by the HMCL-native editor.
    public static List<String> parseIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : value.split("[,;\\s]+")) {
            if (!token.isBlank()) {
                result.add(token.trim());
            }
        }
        return List.copyOf(result);
    }

    /// Validates the pack envelope and fields that can be checked without touching desktop runtime state.
    private static void validate(PackArchive archive) throws IOException {
        if (archive == null || !FORMAT.equals(archive.format()) || archive.formatVersion() != FORMAT_VERSION || archive.pack() == null) {
            throw new IOException("Unsupported .dshpack format");
        }
        if (archive.plugins() == null) {
            throw new IOException("Pack extension metadata is missing");
        }
        PackMetadata pack = archive.pack();
        if (pack.name() == null || pack.name().isBlank()) {
            throw new IOException("Pack name cannot be blank");
        }
        String version = normalize(pack.coreVersion(), "");
        if (!"latest".equals(version)) DshModService.validateVersion(version);
        String profile = normalize(pack.profileName(), "web");
        DshModService.validateProfile(profile);
        if (!Set.of("web", "headless", "sdk", "sdk-minimal", "acp")
                .contains(normalize(pack.profileTemplate(), "web"))) {
            throw new IOException("Pack Profile template is not supported");
        }
        Set<String> known = new LinkedHashSet<>();
        for (PackExtension extension : archive.plugins()) {
            if (extension.id() == null || extension.id().isBlank() || extension.name() == null || extension.name().isBlank()) {
                throw new IOException("Pack contains invalid extension metadata");
            }
            DshModService.validateInstanceId(extension.id());
            if (!known.add(extension.id())) {
                throw new IOException("Pack contains duplicate extension ID: " + extension.id());
            }
            String kind = normalize(extension.kind(), "");
            if (!Set.of("plugin", "bundle", "mcp", "skill", "tool").contains(kind.toLowerCase(Locale.ROOT))) {
                throw new IOException("Pack contains unsupported Mod type: " + kind);
            }
            String spec = normalize(extension.packageSpec(), "");
            if ("plugin".equalsIgnoreCase(kind) || "bundle".equalsIgnoreCase(kind)) {
                if (!spec.isBlank()) portablePackageSpec(spec);
            } else if (!spec.isBlank()) {
                throw new IOException("Pack cannot contain a local MCP/Skill/Tool reference");
            }
        }
        if (pack.pluginIds() == null || pack.tags() == null) {
            throw new IOException("Pack metadata collections are missing");
        }
        for (String id : pack.pluginIds()) DshModService.validateInstanceId(id);
        if (archive.provider() != null) validateProviderMetadata(archive.provider());
    }

    /// Validates portable Provider routing without accepting credentials in endpoint or environment metadata.
    private static void validateProviderMetadata(ProviderMetadata provider) throws IOException {
        DshModService.validateInstanceId(provider.id());
        if (provider.name() == null || provider.name().isBlank() || provider.baseUrl() == null
                || provider.baseUrl().isBlank() || provider.protocol() == null
                || !Set.of("openai-completions", "openai-responses", "anthropic-messages").contains(provider.protocol())) {
            throw new IOException("Pack Provider metadata is invalid");
        }
        AgentRepository.validateProviderEnvironmentName(provider.apiKeyEnv());
        try {
            java.net.URI uri = java.net.URI.create(provider.baseUrl());
            if (!Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IOException("Pack Provider URL must not contain credentials or query data");
            }
        } catch (IllegalArgumentException error) {
            throw new IOException("Pack Provider URL is invalid", error);
        }
    }

    /// Produces an import-only instance identifier that cannot collide with exported stable IDs by accident.
    private static String uniqueId() {
        return "agent-" + UUID.randomUUID();
    }

    /// Removes the conventional ` Pack` suffix when creating the imported instance name.
    private static String stripPackSuffix(String name) {
        String trimmed = name.trim();
        return trimmed.toLowerCase(Locale.ROOT).endsWith(" pack") ? trimmed.substring(0, trimmed.length() - 5).trim() : trimmed;
    }

    /// Returns `fallback` for null or blank strings.
    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /// Versioned `.dshpack` envelope.
    public record PackArchive(
            String format,
            int formatVersion,
            String createdWith,
            String exportedAt,
            PackMetadata pack,
            List<PackExtension> plugins,
            ProviderMetadata provider) {
    }

    /// Portable instance metadata compatible with the earlier browser pack shape.
    public record PackMetadata(
            String id,
            String name,
            String description,
            String coreVersion,
            String model,
            List<String> pluginIds,
            List<String> tags,
            String profileName,
            String profileTemplate,
            String webPort) {
    }

    /// Portable extension metadata. `kind` and `packageSpec` preserve the v0.4 browser field names.
    public record PackExtension(String id, String name, String kind, String packageSpec, boolean enabled) {
    }

    /// Portable Provider routing metadata. It never contains an API key value.
    public record ProviderMetadata(String id, String name, String type, String baseUrl,
                                   String model, String apiKeyEnv, String protocol) {
    }

    /// User-selected export fields. API keys are deliberately not represented and cannot be exported.
    public record ExportOptions(String name, String description, String coreVersion,
                                String profileName, String profileTemplate, String webPort,
                                String model, String extensionIds, boolean includeCore,
                                boolean includeProfile, boolean includeModel, boolean includeExtensions,
                                boolean includeProvider) {
        /// Creates the safe default selection from an instance.
        public static ExportOptions defaults(AgentInstance instance) {
            return new ExportOptions(instance.getName() + " Pack", instance.descriptionProperty().get(),
                    instance.coreVersionProperty().get(), instance.profileNameProperty().get(),
                    instance.profileTemplateProperty().get(), instance.webPortProperty().get(),
                    instance.modelProperty().get(), instance.extensionIdsProperty().get(),
                    true, true, true, true, false);
        }

        /// Returns the extension IDs only when the user opted into extension metadata.
        public String extensionIds() {
            return includeExtensions ? extensionIds : "";
        }
    }

    /// Validated import plan that does not mutate live Launcher state.
    public record PreparedPack(AgentInstance instance, @Unmodifiable List<AgentExtension> newExtensions,
                               @Unmodifiable List<String> installSpecs,
                               @Unmodifiable List<AgentProvider> newProviders) {
        /// Compatibility constructor for tests and callers that do not import Provider metadata.
        public PreparedPack(AgentInstance instance, List<AgentExtension> newExtensions, List<String> installSpecs) {
            this(instance, newExtensions, installSpecs, List.of());
        }
    }
}
