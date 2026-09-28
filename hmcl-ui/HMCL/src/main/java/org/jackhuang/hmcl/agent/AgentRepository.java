/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Persistent Agent business state for the HMCL-native fork.
 *
 * All visual behavior stays inside upstream HMCL controls. This repository only
 * owns providers, Agent instances, extensions and process lifecycle.
 */
@NotNullByDefault
public final class AgentRepository {
    /// Identifies launcher-owned provider YAML; the digest detects later manual edits.
    private static final String MANAGED_PROFILE_PREFIX = "# DSHCraft managed provider profile sha256=";
    /// Maximum number of console lines retained in memory for the active process.
    private static final int MAX_CONSOLE_LINES = 2000;
    /// Maximum characters displayed from one process output line.
    private static final int MAX_CONSOLE_LINE_LENGTH = 4096;
    /// Environment names that can change the launcher or Node execution itself.
    private static final @Unmodifiable Set<String> RESERVED_ENV_NAMES = Set.of(
            "DSH_HOME", "DSH_PROFILE", "DSH_WEB_PORT", "NODE_OPTIONS", "NODE_PATH",
            "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "PATH", "PATHEXT", "COMSPEC");
    /// Secret-like names are never accepted from persisted per-instance environment entries.
    private static final Pattern SECRET_ENV_NAME = Pattern.compile(
            "(?i).*(?:KEY|TOKEN|SECRET|PASSWORD|PASS|CREDENTIAL).*" );
    /// Verified official Bundle catalog entries; their installed state is instance-local.
    private static final @Unmodifiable List<AgentExtension> OFFICIAL_MODS = List.of(
            new AgentExtension("codex-subagent", "Codex Subagent", "Bundle", "@deepseek-ai/dsh-subagent-codex", false),
            new AgentExtension("claude-subagent", "Claude Code Subagent", "Bundle", "@deepseek-ai/dsh-subagent-claude-code", false),
            new AgentExtension("agent-team-web", "Agent Team Web Profile", "Bundle", "@deepseek-ai/dsh-experimental-agent-team-web-profile", false));
    /// Core capabilities verified in the installed DSH package graph; they are not external Mods.
    private static final @Unmodifiable List<AgentExtension> BUILTIN_CAPABILITIES = List.of(
            new AgentExtension("filesystem", "Filesystem", "Tool", "", false),
            new AgentExtension("browser", "Web Tools", "Tool", "", false),
            new AgentExtension("skills", "Skills", "Skill", "", false),
            new AgentExtension("mcp-client", "MCP Client", "MCP", "", false));

    /// Delays disk-backed repository loading until the application actually requests it.
    private static final class Holder {
        /// Singleton repository owned by the HMCL application.
        private static final AgentRepository INSTANCE = new AgentRepository();
    }

    private final ObservableList<AgentProvider> providers = FXCollections.observableArrayList();
    private final ObservableList<AgentInstance> instances = FXCollections.observableArrayList();
    private final ObservableList<AgentExtension> extensions = FXCollections.observableArrayList();

    private final ObjectProperty<AgentProvider> selectedProvider = new SimpleObjectProperty<>(this, "selectedProvider");
    private final ObjectProperty<AgentInstance> selectedInstance = new SimpleObjectProperty<>(this, "selectedInstance");
    private final BooleanProperty running = new SimpleBooleanProperty(this, "running", false);
    /// In-memory output from the currently managed DSH process.
    private final ObservableList<String> consoleLines = FXCollections.observableArrayList();
    /// Validated loopback URL announced by the active Web Profile.
    private final StringProperty webUrl = new SimpleStringProperty(this, "webUrl", "");

    private final Path configFile = Metadata.HMCL_USER_HOME.resolve("dshcraft-agent.properties");
    private final Properties raw = new Properties();
    private volatile Process process;
    /// Process most recently terminated by the user's explicit Stop action.
    private volatile @Nullable Process stopRequested;
    private boolean loading;

    private AgentRepository() {
        load();
        selectedProvider.addListener((observable, oldValue, newValue) -> saveIfReady());
        selectedInstance.addListener((observable, oldValue, newValue) -> {
            syncSelectedExtensions();
            saveIfReady();
        });
    }

    public static AgentRepository get() {
        return Holder.INSTANCE;
    }

    public Path getConfigFile() {
        return configFile;
    }

    /// Returns the npm registry used for version metadata requests.
    public String getCatalogRegistry() {
        return registry("downloads.catalog.source", "downloads.catalog.custom", "official");
    }

    /// Returns the npm registry used by pnpm and DSH plugin package operations.
    public String getPackageRegistry() {
        return registry("downloads.package.source", "downloads.package.custom", "official");
    }

    /// Persists the selected registry for catalog or package downloads.
    public synchronized void setDownloadRegistry(boolean catalog, String source, String customUrl) throws IOException {
        String sourceKey = catalog ? "downloads.catalog.source" : "downloads.package.source";
        String customKey = catalog ? "downloads.catalog.custom" : "downloads.package.custom";
        String normalized = AgentNetworkService.validateRegistry(source, customUrl);
        raw.setProperty(sourceKey, source);
        raw.setProperty(customKey, normalized);
        save();
    }

    /// Resolves a predefined or custom npm registry from persisted settings.
    private String registry(String sourceKey, String customKey, String fallback) {
        String source = raw.getProperty(sourceKey, fallback);
        if ("custom".equals(source)) {
            try {
                return AgentNetworkService.validateRegistry(source, raw.getProperty(customKey, ""));
            } catch (IOException error) {
                LOG.warning("Invalid saved DSHCraft download registry; using npm official registry", error);
                return AgentNetworkService.OFFICIAL_REGISTRY;
            }
        }
        try {
            return AgentNetworkService.validateRegistry(source, "");
        } catch (IOException error) {
            return AgentNetworkService.OFFICIAL_REGISTRY;
        }
    }

    public ObservableList<AgentProvider> getProviders() {
        return providers;
    }

    public ObservableList<AgentInstance> getInstances() {
        return instances;
    }

    public ObservableList<AgentExtension> getExtensions() {
        return extensions;
    }

    public ObjectProperty<AgentProvider> selectedProviderProperty() {
        return selectedProvider;
    }

    public AgentProvider getSelectedProvider() {
        return selectedProvider.get();
    }

    public void setSelectedProvider(AgentProvider provider) {
        selectedProvider.set(provider);
    }

    public ObjectProperty<AgentInstance> selectedInstanceProperty() {
        return selectedInstance;
    }

    public AgentInstance getSelectedInstance() {
        return selectedInstance.get();
    }

    public void setSelectedInstance(AgentInstance instance) {
        selectedInstance.set(instance);
        if (instance != null) {
            AgentProvider provider = findProvider(instance.providerIdProperty().get());
            if (provider != null) {
                setSelectedProvider(provider);
            }
        }
    }

    public BooleanProperty runningProperty() {
        return running;
    }

    public boolean isRunning() {
        return running.get();
    }

    /// Returns the bounded in-memory DSH stdout/stderr feed for the HMCL console page.
    public ObservableList<String> getConsoleLines() {
        return consoleLines;
    }

    /// Exposes the active Profile's validated local Web URL.
    public StringProperty webUrlProperty() {
        return webUrl;
    }

    public AgentProvider addProvider() {
        AgentProvider provider = new AgentProvider(
                uniqueId("provider"),
                "DeepSeek Official",
                "DeepSeek Official",
                "https://api.deepseek.com",
                "",
                "DEEPSEEK_API_KEY",
                "openai-completions");
        wireAutoSave(provider);
        providers.add(provider);
        setSelectedProvider(provider);
        save();
        return provider;
    }

    /// Creates a new browser-manageable Agent instance with DSh defaults.
    public AgentInstance addInstance() {
        AgentProvider provider = getSelectedProvider();
        if (provider == null && !providers.isEmpty()) {
            provider = providers.get(0);
        }
        AgentInstance instance = new AgentInstance(
                uniqueId("agent"),
                "New DSH",
                provider == null ? "default" : provider.getId(),
                "",
                "latest",
                "web",
                "web",
                "3080",
                provider == null ? "" : provider.modelProperty().get(),
                "",
                "dsh",
                "",
                "");
        wireAutoSave(instance);
        instances.add(instance);
        setSelectedInstance(instance);
        save();
        return instance;
    }

    public AgentExtension addExtension() {
        return addExtension("Plugin");
    }

    /// Creates an extension catalog entry for one HMCL Downloads category.
    public AgentExtension addExtension(String type) {
        AgentExtension extension = new AgentExtension(uniqueId("extension"), "New Extension", type, "", false);
        wireAutoSave(extension);
        extensions.add(extension);
        save();
        return extension;
    }

    public void removeProvider(AgentProvider provider) throws IOException {
        if (provider == null || providers.size() <= 1) {
            return;
        }
        if (AgentSecretStore.isSupported()) AgentSecretStore.delete(provider.getId());
        providers.remove(provider);
        if (Objects.equals(getSelectedProvider(), provider)) {
            setSelectedProvider(providers.get(0));
        }
        for (AgentInstance instance : instances) {
            if (Objects.equals(instance.providerIdProperty().get(), provider.getId())) {
                instance.providerIdProperty().set(getSelectedProvider().getId());
            }
        }
        save();
    }

    /// Removes an Agent instance and its saved child-process environment overrides.
    public void removeInstance(AgentInstance instance) throws IOException {
        if (instance == null || instances.size() <= 1) {
            return;
        }
        DshModService.validateInstanceId(instance.getId());
        if (process != null && process.isAlive() && instance == getSelectedInstance()) {
            stop();
            if (process != null && process.isAlive()) {
                throw new IOException("The running DSH process did not stop; instance data was kept");
            }
        }
        deleteManagedDshHome(Metadata.HMCL_USER_HOME.resolve("dshcraft").resolve("instances"), instance.getId());
        instances.remove(instance);
        clearInstanceEnvironment(instance.getId());
        if (Objects.equals(getSelectedInstance(), instance)) {
            setSelectedInstance(instances.get(0));
        }
        save();
    }

    /// Deletes only one launcher-managed DSH_HOME, preserving its sibling Workspace directory.
    static void deleteManagedDshHome(Path instancesRoot, String instanceId) throws IOException {
        DshModService.validateInstanceId(instanceId);
        Path root = instancesRoot.toAbsolutePath().normalize();
        Path instanceDirectory = root.resolve(instanceId).normalize();
        Path dshHome = instanceDirectory.resolve("dsh-home");
        if (!instanceDirectory.startsWith(root) || instanceDirectory.equals(root)
                || !dshHome.startsWith(root) || dshHome.equals(root)) {
            throw new IOException("Refusing to delete DSH_HOME outside the managed instances directory");
        }
        for (Path checked : List.of(root, instanceDirectory, dshHome)) {
            if (Files.isSymbolicLink(checked)) {
                throw new IOException("Refusing to delete DSH_HOME through a symbolic link: " + checked);
            }
        }
        if (!Files.exists(dshHome)) return;
        Files.walkFileTree(dshHome, new SimpleFileVisitor<>() {
            /// Validates the entire tree before deletion so a later link cannot leave a partial cleanup.
            @Override
            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes)
                    throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IOException("Refusing to delete linked DSH_HOME content: " + file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Files.walkFileTree(dshHome, new SimpleFileVisitor<>() {
            /// Rejects links instead of following or deleting anything through them.
            @Override
            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes)
                    throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IOException("Refusing to delete linked DSH_HOME content: " + file);
                }
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            /// Removes an empty managed directory after its regular contents are gone.
            @Override
            public FileVisitResult postVisitDirectory(Path directory, @Nullable IOException error)
                    throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void removeExtension(AgentExtension extension) {
        if (extension == null) {
            return;
        }
        extensions.remove(extension);
        save();
    }

    public AgentProvider findProvider(String id) {
        if (id == null) return null;
        for (AgentProvider provider : providers) {
            if (id.equals(provider.getId())) return provider;
        }
        return null;
    }

    public AgentInstance findInstance(String id) {
        if (id == null) return null;
        for (AgentInstance instance : instances) {
            if (id.equals(instance.getId())) return instance;
        }
        return null;
    }

    /// Returns the isolated DSh home directory for one instance.
    public Path getInstanceHome(AgentInstance instance) {
        String directoryName = safe(instance.getId()).replaceAll("[^A-Za-z0-9._-]", "_");
        if (directoryName.isBlank() || directoryName.equals(".") || directoryName.equals("..")) {
            directoryName = "agent";
        }
        return Metadata.HMCL_USER_HOME.resolve("dshcraft").resolve("instances").resolve(directoryName).resolve("dsh-home");
    }

    /// Reports whether a Mod belongs to a particular DSH instance, not to the global catalog.
    public boolean hasExtension(AgentInstance instance, AgentExtension extension) {
        return extensionIds(instance).contains(extension.getId());
    }

    /// Updates one instance's installed Mod mapping after a successful DSH Profile operation.
    public void setExtensionInstalled(AgentInstance instance, AgentExtension extension, boolean installed) {
        Set<String> ids = extensionIds(instance);
        if (installed) ids.add(extension.getId());
        else ids.remove(extension.getId());
        instance.extensionIdsProperty().set(String.join(",", ids));
        syncSelectedExtensions();
    }

    /// Reconciles package-backed entries with the target instance's actual Profile dependencies.
    public void refreshInstalledMods(AgentInstance instance) throws IOException {
        Map<String, String> installed = DshModService.installedPackageVersions(getInstanceHome(instance),
                instance.profileNameProperty().get());
        Set<String> ids = extensionIds(instance);
        for (AgentExtension extension : extensions) {
            if (!isPackageBacked(extension)) continue;
            String spec = safe(extension.locationProperty().get()).trim();
            if (spec.isEmpty()) continue;
            String packageName = DshModService.packageName(spec);
            String installedVersion = installed.get(packageName);
            extension.installedVersionProperty().set(installedVersion == null ? "" : installedVersion);
            if (installedVersion != null) ids.add(extension.getId());
            else ids.remove(extension.getId());
        }
        instance.extensionIdsProperty().set(String.join(",", ids));
        syncSelectedExtensions();
    }

    /// Distinguishes DSH npm Bundles from MCP/Skill metadata that needs separate configuration.
    private static boolean isPackageBacked(AgentExtension extension) {
        String type = safe(extension.typeProperty().get()).trim();
        return "plugin".equalsIgnoreCase(type) || "bundle".equalsIgnoreCase(type);
    }

    /// Parses instance-local Mod IDs without changing their stable catalog identifiers.
    private static Set<String> extensionIds(AgentInstance instance) {
        Set<String> ids = new LinkedHashSet<>();
        for (String id : safe(instance.extensionIdsProperty().get()).split("[,\\s]+")) {
            if (!id.isBlank()) ids.add(id);
        }
        return ids;
    }

    /// Projects the selected instance's Mod state into HMCL's shared list-cell controls.
    private void syncSelectedExtensions() {
        AgentInstance selected = getSelectedInstance();
        if (selected == null) return;
        boolean before = loading;
        loading = true;
        try {
            for (AgentExtension extension : extensions) {
                extension.enabledProperty().set(hasExtension(selected, extension));
            }
        } finally {
            loading = before;
        }
    }

    /// Replaces the complete browser-manageable Agent state after validating an imported backup.
    public synchronized void replaceState(
            List<AgentProvider> importedProviders,
            List<AgentInstance> importedInstances,
            List<AgentExtension> importedExtensions,
            @Nullable String selectedProviderId,
            @Nullable String selectedInstanceId) {
        loading = true;
        try {
            raw.keySet().removeIf(key -> String.valueOf(key).matches("instance\\.[^.]+\\.env\\..+"));
            providers.clear();
            instances.clear();
            extensions.clear();
            for (AgentProvider provider : importedProviders) {
                wireAutoSave(provider);
                providers.add(provider);
            }
            for (AgentInstance instance : importedInstances) {
                wireAutoSave(instance);
                instances.add(instance);
            }
            for (AgentExtension extension : importedExtensions) {
                wireAutoSave(extension);
                extensions.add(extension);
            }
            addBuiltinCapabilities();
            if (providers.isEmpty()) {
                AgentProvider provider = new AgentProvider("default", "DeepSeek Official", "DeepSeek Official",
                        "https://api.deepseek.com", "", "DEEPSEEK_API_KEY", "openai-completions");
                wireAutoSave(provider);
                providers.add(provider);
            }
            if (instances.isEmpty()) {
                AgentInstance instance = new AgentInstance("default", "Default DSH", providers.get(0).getId(),
                        "", "latest", "web", "web", "3080", providers.get(0).modelProperty().get(), "",
                        "dsh", "", "");
                wireAutoSave(instance);
                instances.add(instance);
            }
            AgentProvider provider = findProvider(selectedProviderId);
            selectedProvider.set(provider != null ? provider : providers.get(0));
            AgentInstance instance = findInstance(selectedInstanceId);
            selectedInstance.set(instance != null ? instance : instances.get(0));
        } finally {
            loading = false;
        }
        save();
    }

    /// Adds an imported extension only when another entry with the same stable ID does not exist.
    public synchronized boolean addImportedExtension(AgentExtension extension) {
        for (AgentExtension current : extensions) {
            if (current.getId().equals(extension.getId())) {
                return false;
            }
        }
        wireAutoSave(extension);
        extensions.add(extension);
        save();
        return true;
    }

    /// Adds imported Provider routing metadata without importing any secret value.
    public synchronized boolean addImportedProvider(AgentProvider provider) {
        if (findProvider(provider.getId()) != null) return false;
        wireAutoSave(provider);
        providers.add(provider);
        save();
        return true;
    }

    /// Adds an imported Agent instance while preserving selection and auto-save wiring.
    public synchronized AgentInstance addImportedInstance(AgentInstance instance) {
        wireAutoSave(instance);
        instances.add(instance);
        setSelectedInstance(instance);
        save();
        return instance;
    }

    /// Returns configuration problems that can be detected without installing or launching DSh.
    public List<String> validateInstanceConfiguration(AgentInstance instance) {
        List<String> problems = new ArrayList<>();
        try {
            DshModService.validateInstanceId(instance.getId());
        } catch (IOException error) {
            problems.add(error.getMessage());
        }
        if (findProvider(instance.providerIdProperty().get()) == null) {
            problems.add("Provider ID does not exist: " + safe(instance.providerIdProperty().get()));
        }
        String coreVersion = safe(instance.coreVersionProperty().get()).trim();
        if (coreVersion.isEmpty()) {
            problems.add("DSh Core version is empty.");
        }
        String profile = safe(instance.profileNameProperty().get()).trim();
        try {
            DshModService.validateProfile(profile);
        } catch (IOException error) {
            problems.add(error.getMessage());
        }
        String portText = safe(instance.webPortProperty().get()).trim();
        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                problems.add("Web port must be between 1 and 65535.");
            }
        } catch (NumberFormatException e) {
            problems.add("Web port is not a number: " + portText);
        }
        for (String extensionId : safe(instance.extensionIdsProperty().get()).split("[,\\s]+")) {
            if (extensionId.isBlank()) continue;
            boolean found = extensions.stream().anyMatch(extension -> extensionId.equals(extension.getId()));
            if (!found) {
                problems.add("Extension ID does not exist: " + extensionId);
            }
        }
        return problems;
    }

    /// Launches the selected instance after validating its portable configuration and preparing isolated DSH_HOME.
    public synchronized Process launchSelected() throws IOException {
        AgentInstance instance = getSelectedInstance();
        if (instance == null) {
            throw new IOException("No Agent instance is selected");
        }
        List<String> configurationProblems = validateInstanceConfiguration(instance);
        if (!configurationProblems.isEmpty()) {
            throw new IOException(String.join("\n", configurationProblems));
        }
        if (process != null && process.isAlive()) {
            return process;
        }

        Path cli = DshModService.installedCoreCli(DshModService.runtimeRoot(),
                safe(instance.coreVersionProperty().get()).trim());
        String profile = safe(instance.profileNameProperty().get()).trim();
        String template = safe(instance.profileTemplateProperty().get()).trim();
        DshModService.validateProfile(template);
        Path dshHome = getInstanceHome(instance);
        List<String> command = launchCommand(cli, profile, template,
                safe(instance.webPortProperty().get()).trim(),
                Files.isDirectory(dshHome.resolve("profiles").resolve(profile)));
        List<AgentExtension> enabledMcp = extensions.stream()
                .filter(extension -> "MCP".equalsIgnoreCase(extension.typeProperty().get())
                        && !"mcp-client".equals(extension.getId()) && hasExtension(instance, extension))
                .toList();
        Map<String, String> mcpSecrets = new LinkedHashMap<>();
        Set<String> mcpTokenIds = new LinkedHashSet<>();
        if (AgentSecretStore.isSupported()) {
            for (AgentExtension extension : enabledMcp) {
                String token = AgentSecretStore.getMcp(extension.getId());
                if (token != null && !token.isBlank()) {
                    mcpSecrets.put(AgentMcpService.environmentVariable(extension.getId()), token);
                    mcpTokenIds.add(extension.getId());
                }
            }
        }
        Path mcpPatch = AgentMcpService.ensurePatch(dshHome, enabledMcp, mcpTokenIds);
        if (mcpPatch != null) {
            addProfileOverlay(command, mcpPatch);
        }
        command.addAll(splitArguments(instance.argumentsProperty().get()));

        ProcessBuilder builder = new ProcessBuilder(command);
        String workingDirectory = safe(instance.workingDirectoryProperty().get()).trim();
        Path defaultDirectory = dshHome.getParent();
        if (defaultDirectory == null) throw new IOException("Instance DSH_HOME has no parent directory");
        Path cwd = workingDirectory.isEmpty() ? defaultDirectory : Path.of(workingDirectory);
        if (workingDirectory.isEmpty()) Files.createDirectories(cwd);
        if (!Files.isDirectory(cwd)) throw new IOException("Workspace directory does not exist: " + cwd);
        builder.directory(cwd.toFile());

        AgentProvider provider = findProvider(instance.providerIdProperty().get());
        if (provider != null) {
            putIfNotBlank(builder.environment(), "DSHCRAFT_PROVIDER_ID", provider.getId());
            putIfNotBlank(builder.environment(), "DSHCRAFT_PROVIDER_TYPE", provider.typeProperty().get());
            String apiKey = AgentSecretStore.resolve(provider);
            putIfNotBlank(builder.environment(), "DSH_LAUNCHER_PROVIDER_API_KEY", apiKey);
            if (safe(provider.protocolProperty().get()).startsWith("anthropic-")) {
                putIfNotBlank(builder.environment(), "ANTHROPIC_API_KEY", apiKey);
            } else if (safe(provider.typeProperty().get()).toLowerCase().contains("deepseek")) {
                putIfNotBlank(builder.environment(), "DEEPSEEK_API_KEY", apiKey);
                putIfNotBlank(builder.environment(), "DEEPSEEK_BASE_URL", provider.baseUrlProperty().get());
            } else {
                putIfNotBlank(builder.environment(), "OPENAI_API_KEY", apiKey);
                putIfNotBlank(builder.environment(), "OPENAI_BASE_URL", provider.baseUrlProperty().get());
            }
            putIfNotBlank(builder.environment(), safe(provider.apiKeyEnvProperty().get()), apiKey);
            String instanceModel = safe(instance.modelProperty().get()).trim();
            putIfNotBlank(builder.environment(), "OPENAI_MODEL", instanceModel.isEmpty() ? provider.modelProperty().get() : instanceModel);
        }
        Files.createDirectories(dshHome);
        if (provider != null) ensureProviderProfileConfig(dshHome, profile, provider,
                safe(instance.modelProperty().get()).isBlank() ? provider.modelProperty().get() : instance.modelProperty().get());
        putIfNotBlank(builder.environment(), "DSHCRAFT_CORE_VERSION", instance.coreVersionProperty().get());
        putIfNotBlank(builder.environment(), "DSH_PROFILE", instance.profileNameProperty().get());
        putIfNotBlank(builder.environment(), "DSH_WEB_PORT", instance.webPortProperty().get());

        String envPrefix = "instance." + instance.getId() + ".env.";
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.startsWith(envPrefix) && key.length() > envPrefix.length()) {
                String environmentName = key.substring(envPrefix.length());
                validateInstanceEnvironmentName(environmentName);
                putIfNotBlank(builder.environment(), environmentName, String.valueOf(entry.getValue()));
            }
        }
        builder.environment().put("DSH_HOME", dshHome.toString());
        builder.environment().putAll(mcpSecrets);
        DshModService.configureWindowsModuleFallback(builder, cli);

        Process started = builder.start();
        process = started;
        stopRequested = null;
        consoleLines.clear();
        webUrl.set("");
        List<String> redactions = new ArrayList<>(mcpSecrets.values());
        String providerSecret = apiKeyForLogRedaction(builder.environment());
        if (providerSecret != null && !providerSecret.isBlank()) redactions.add(providerSecret);
        @Unmodifiable List<String> secretSnapshot = List.copyOf(redactions);
        pumpOutput(started, started.getInputStream(), "stdout", secretSnapshot);
        pumpOutput(started, started.getErrorStream(), "stderr", secretSnapshot);
        running.set(true);
        started.onExit().thenRun(() -> Platform.runLater(() -> {
            if (process == started) {
                running.set(false);
                addConsoleLine("[process] DSH exited with code " + started.exitValue());
            }
        }));
        return started;
    }

    /// Creates or updates launcher-owned DSH provider YAML without overwriting user-edited Profile settings.
    static void ensureProviderProfileConfig(Path dshHome, String profile, AgentProvider provider, String model)
            throws IOException {
        DshModService.validateProfile(profile);
        Path profileDirectory = dshHome.resolve("profiles").resolve(profile);
        Path settings = profileDirectory.resolve("settings.yaml");
        String protocol = safe(provider.protocolProperty().get());
        if (!Set.of("openai-completions", "openai-responses", "anthropic-messages").contains(protocol)) {
            throw new IOException("Unsupported DSH Provider protocol: " + protocol);
        }
        String keyEnv = safe(provider.apiKeyEnvProperty().get()).isBlank()
                ? protocol.equals("anthropic-messages") ? "ANTHROPIC_API_KEY" : "OPENAI_API_KEY"
                : provider.apiKeyEnvProperty().get();
        String body = providerProfileYaml(provider.getId(), protocol, safe(provider.baseUrlProperty().get()).trim(), keyEnv,
                model.trim());
        String yaml = MANAGED_PROFILE_PREFIX + sha256(body) + "\n" + body;
        Files.createDirectories(profileDirectory);
        if (Files.isSymbolicLink(settings)) throw new IOException("Refusing to replace a linked DSH Profile settings file");
        if (!Files.exists(settings)) {
            Files.writeString(settings, yaml, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
            return;
        }
        String current = Files.readString(settings, StandardCharsets.UTF_8);
        if (current.equals(yaml)) return;
        if (!current.startsWith(MANAGED_PROFILE_PREFIX)) {
            if (current.equals(body)) {
                // Older launcher output is safe to mark as managed only when byte-for-byte unchanged.
                replaceProfileSettings(profileDirectory, settings, yaml);
                return;
            }
            throw new IOException("Existing DSH Profile settings.yaml is not launcher-managed; choose a new Profile or update it manually");
        }
        int lineEnd = current.indexOf('\n');
        if (lineEnd < 0 || !current.substring(0, lineEnd).equals(
                MANAGED_PROFILE_PREFIX + sha256(current.substring(lineEnd + 1)))) {
            throw new IOException("DSH Profile settings.yaml was edited outside the launcher; refusing to overwrite it");
        }
        replaceProfileSettings(profileDirectory, settings, yaml);
    }

    /// Atomically replaces only an already verified launcher-owned Profile settings file.
    private static void replaceProfileSettings(Path directory, Path settings, String yaml) throws IOException {
        Path staged = Files.createTempFile(directory, ".dshcraft-provider-", ".yaml");
        try {
            Files.writeString(staged, yaml, StandardCharsets.UTF_8);
            try {
                Files.move(staged, settings, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, settings, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /// Returns a stable digest of generated YAML without including any API secret material.
    private static String sha256(String value) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("SHA-256 is unavailable for DSH Profile settings", unavailable);
        }
    }

    /// Creates DSH's provider routing overlay with a credential reference, never secret material.
    static String providerProfileYaml(String providerId, String protocol, String baseUrl, String apiKeyEnv, String model)
            throws IOException {
        if (!Set.of("openai-completions", "openai-responses", "anthropic-messages").contains(protocol)) {
            throw new IOException("Unsupported DSH Provider protocol: " + protocol);
        }
        if (baseUrl == null || baseUrl.isBlank()) throw new IOException("Provider Base URL is required");
        if (apiKeyEnv == null || !apiKeyEnv.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new IOException("Provider API Key environment variable name is invalid");
        }
        validateProviderEnvironmentName(apiKeyEnv);
        String yaml = "llm-pi-ai:\n"
                + "  providers:\n"
                + "    " + yamlScalar(providerId) + ":\n"
                + "      api: " + yamlScalar(protocol) + "\n"
                + "      baseURL: " + yamlScalar(baseUrl) + "\n"
                + "      apiKeyEnv: " + yamlScalar(apiKeyEnv) + "\n"
                + "      models:\n"
                + "        - id: " + yamlScalar(model == null ? "" : model.trim()) + "\n"
                + "agent-default-model:\n"
                + "  provider: " + yamlScalar(providerId) + "\n"
                + "  model: " + yamlScalar(model == null ? "" : model.trim()) + "\n";
        return yaml;
    }

    /// Quotes a YAML string using JSON string syntax, which is a valid YAML double-quoted scalar.
    private static String yamlScalar(String value) {
        return new com.google.gson.Gson().toJson(value);
    }

    /// Returns the active Provider key only for removing it from displayed process output.
    private static @Nullable String apiKeyForLogRedaction(Map<String, String> environment) {
        String key = environment.get("DSH_LAUNCHER_PROVIDER_API_KEY");
        return key == null || key.isBlank() ? environment.get("DEEPSEEK_API_KEY") : key;
    }

    /// Rejects persisted instance variables that could inject secrets or alter launcher execution.
    static void validateInstanceEnvironmentName(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new IOException("Instance environment variable name is invalid: " + name);
        }
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        if (RESERVED_ENV_NAMES.contains(upper) || upper.startsWith("DSHCRAFT_")
                || upper.startsWith("PNPM_CONFIG_") || SECRET_ENV_NAME.matcher(upper).matches()) {
            throw new IOException("Instance environment variable is reserved or secret-bearing: " + name);
        }
    }

    /// Rejects Provider key references that could alter Node, Java, or DSH execution.
    static void validateProviderEnvironmentName(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new IOException("Provider API Key environment variable name is invalid: " + name);
        }
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        if (RESERVED_ENV_NAMES.contains(upper) || upper.startsWith("DSHCRAFT_")
                || upper.startsWith("PNPM_CONFIG_")) {
            throw new IOException("Provider API Key environment variable is reserved: " + name);
        }
    }

    /// Drains one process stream on a daemon thread and forwards bounded lines to JavaFX.
    private void pumpOutput(Process started, InputStream input, String stream, @Unmodifiable List<String> secrets) {
        Thread reader = new Thread(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    String url = extractLoopbackWebUrl(line);
                    String visible = line.length() > MAX_CONSOLE_LINE_LENGTH
                            ? line.substring(0, MAX_CONSOLE_LINE_LENGTH) + " [truncated]" : line;
                    visible = redactSecrets(visible, secrets);
                    String entry = "[" + stream + "] " + visible;
                    Platform.runLater(() -> {
                        if (process != started) return;
                        addConsoleLine(entry);
                        if (url != null) webUrl.set(url);
                    });
                }
            } catch (IOException error) {
                Platform.runLater(() -> {
                    if (process == started && started.isAlive()) addConsoleLine("[" + stream + "] stream closed unexpectedly");
                });
            }
        }, "dshcraft-" + stream + "-" + started.pid());
        reader.setDaemon(true);
        reader.start();
    }

    /// Removes every active Provider and MCP secret from one console line.
    static String redactSecrets(String line, @Unmodifiable List<String> secrets) {
        String result = line;
        for (String secret : secrets) {
            if (!secret.isBlank()) result = result.replace(secret, "[redacted]");
        }
        return result;
    }

    /// Adds one line while preventing a long-running Profile from growing launcher memory without bound.
    private void addConsoleLine(String entry) {
        consoleLines.add(entry);
        if (consoleLines.size() > MAX_CONSOLE_LINES) {
            consoleLines.remove(0, consoleLines.size() - MAX_CONSOLE_LINES);
        }
    }

    /// Accepts only a DSH Web announcement pointing to this machine.
    static @Nullable String extractLoopbackWebUrl(String line) {
        if (!line.contains("dsh web:")) return null;
        int start = line.indexOf("http://");
        if (start < 0) start = line.indexOf("https://");
        if (start < 0) return null;
        String candidate = line.substring(start).split("\\s", 2)[0]
                .replaceAll("[),;]+$", "");
        try {
            URI parsed = new URI(candidate);
            String host = parsed.getHost();
            if (host == null || !("http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme()))) return null;
            if (!("localhost".equalsIgnoreCase(host) || host.matches("127(?:\\.[0-9]{1,3}){3}")
                    || "::1".equals(host) || "[::1]".equals(host))) return null;
            return parsed.toString();
        } catch (URISyntaxException error) {
            return null;
        }
    }

    /// Builds the CLI command for an isolated Profile without invoking a global DSH shim.
    static List<String> launchCommand(Path cli, String profile, String template, String port,
                                      boolean profileExists) {
        List<String> command = new ArrayList<>();
        command.add(DshModService.managedNodeExecutable());
        command.add(cli.toString());
        command.add("--profile");
        command.add(profile);
        if (!profileExists && !profile.equals(template) && !isShippedProfile(profile)) {
            command.add("--from-default-profile");
            command.add(template);
        }
        if ("web".equals(template)) {
            command.add("--port");
            command.add(port);
            command.add("--no-open");
        }
        return command;
    }

    /// Identifies DSH profiles that are supplied directly by the installed Core package.
    private static boolean isShippedProfile(String profile) {
        return Set.of("acp", "web", "headless", "sdk", "sdk-minimal").contains(profile);
    }

    /// Places DSH's profile overlay before Web app flags, which Commander forwards to the inner app.
    static void addProfileOverlay(List<String> command, Path patch) {
        command.add(2, "--patch");
        command.add(3, patch.toString());
    }

    public synchronized void stop() {
        Process stopping = process;
        if (stopping == null) return;
        stopRequested = stopping;
        webUrl.set("");
        if (!stopping.isAlive()) {
            if (process == stopping) process = null;
            running.set(false);
            return;
        }
        stopping.descendants().forEach(ProcessHandle::destroyForcibly);
        stopping.destroyForcibly();
        try {
            if (!stopping.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                addConsoleLine("[process] DSH did not exit after stop request");
                return;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            addConsoleLine("[process] Interrupted while waiting for DSH to stop");
            return;
        }
        if (process == stopping) {
            process = null;
            running.set(false);
            addConsoleLine("[process] DSH stopped");
        }
    }

    /// Reports whether a process exit was caused by the user's Stop action.
    public boolean wasStopRequested(Process candidate) {
        return stopRequested == candidate;
    }

    /// Loads persistent Agent state, including backward-compatible defaults for fields added after v1.2.
    public synchronized void load() {
        loading = true;
        try {
            raw.clear();
            if (Files.isRegularFile(configFile)) {
                try (InputStream input = Files.newInputStream(configFile)) {
                    raw.load(input);
                } catch (IOException e) {
                    LOG.warning("Failed to load DShCraft Agent config: " + configFile, e);
                }
            }

            providers.clear();
            instances.clear();
            extensions.clear();

            int providerCount = intValue("provider.count", 0);
            for (int i = 0; i < providerCount; i++) {
                AgentProvider provider = new AgentProvider(
                        value("provider." + i + ".id", uniqueId("provider")),
                        value("provider." + i + ".name", "Provider " + (i + 1)),
                        value("provider." + i + ".type", "OpenAI-compatible"),
                        value("provider." + i + ".baseUrl", ""),
                        value("provider." + i + ".model", ""),
                        value("provider." + i + ".apiKeyEnv", "OPENAI_API_KEY"),
                        value("provider." + i + ".protocol", "openai-completions"));
                wireAutoSave(provider);
                providers.add(provider);
            }

            int instanceCount = intValue("instance.count", 0);
            for (int i = 0; i < instanceCount; i++) {
                AgentInstance instance = new AgentInstance(
                        value("instance." + i + ".id", uniqueId("agent")),
                        value("instance." + i + ".name", "DSH " + (i + 1)),
                        value("instance." + i + ".providerId", "default"),
                        value("instance." + i + ".description", ""),
                        value("instance." + i + ".coreVersion", "latest"),
                        value("instance." + i + ".profileName", "web"),
                        value("instance." + i + ".profileTemplate", "web"),
                        value("instance." + i + ".webPort", "3080"),
                        value("instance." + i + ".model", ""),
                        value("instance." + i + ".extensionIds", ""),
                        value("instance." + i + ".command", "dsh"),
                        value("instance." + i + ".arguments", ""),
                        value("instance." + i + ".workingDirectory", ""));
                wireAutoSave(instance);
                instances.add(instance);
            }

            int extensionCount = intValue("extension.count", 0);
            for (int i = 0; i < extensionCount; i++) {
                AgentExtension extension = new AgentExtension(
                        value("extension." + i + ".id", uniqueId("extension")),
                        value("extension." + i + ".name", "Extension " + (i + 1)),
                        value("extension." + i + ".type", "Plugin"),
                        value("extension." + i + ".location", ""),
                        booleanValue("extension." + i + ".enabled", false));
                wireAutoSave(extension);
                extensions.add(extension);
            }
            if (!raw.containsKey("extension.count")) {
                for (AgentExtension official : OFFICIAL_MODS) {
                    AgentExtension extension = new AgentExtension(official.getId(), official.getName(),
                            official.typeProperty().get(), official.locationProperty().get(), false);
                    wireAutoSave(extension);
                    extensions.add(extension);
                }
            }
            addBuiltinCapabilities();

            if (providers.isEmpty()) {
                AgentProvider provider = new AgentProvider(
                        "default",
                        "DeepSeek Official",
                        "DeepSeek Official",
                        "https://api.deepseek.com",
                        "",
                        "DEEPSEEK_API_KEY",
                        "openai-completions");
                wireAutoSave(provider);
                providers.add(provider);
            }
            if (instances.isEmpty()) {
                AgentInstance instance = new AgentInstance(
                        "default",
                        "Default DSH",
                        providers.get(0).getId(),
                        "",
                        "latest",
                        "web",
                        "web",
                        "3080",
                        providers.get(0).modelProperty().get(),
                        "",
                        "dsh",
                        "",
                        "");
                wireAutoSave(instance);
                instances.add(instance);
            }

            AgentProvider provider = findProvider(raw.getProperty("selected.provider"));
            selectedProvider.set(provider != null ? provider : providers.get(0));
            AgentInstance instance = findInstance(raw.getProperty("selected.instance"));
            selectedInstance.set(instance != null ? instance : instances.get(0));
        } finally {
            loading = false;
        }
        try {
            refreshInstalledMods(getSelectedInstance());
        } catch (IOException error) {
            LOG.warning("Failed to reconcile installed DSH Mods", error);
            syncSelectedExtensions();
        }
        save();
    }

    /// Persists browser-manageable Agent state while retaining separately stored instance environment overrides.
    public synchronized void save() {
        if (loading) return;

        clearManagedKeys("provider.");
        clearManagedKeys("extension.");
        clearIndexedInstanceKeysExceptEnvironment();

        raw.setProperty("provider.count", Integer.toString(providers.size()));
        for (int i = 0; i < providers.size(); i++) {
            AgentProvider provider = providers.get(i);
            raw.setProperty("provider." + i + ".id", safe(provider.idProperty().get()));
            raw.setProperty("provider." + i + ".name", safe(provider.nameProperty().get()));
            raw.setProperty("provider." + i + ".type", safe(provider.typeProperty().get()));
            raw.setProperty("provider." + i + ".baseUrl", safe(provider.baseUrlProperty().get()));
            raw.setProperty("provider." + i + ".model", safe(provider.modelProperty().get()));
            raw.setProperty("provider." + i + ".apiKeyEnv", safe(provider.apiKeyEnvProperty().get()));
            raw.setProperty("provider." + i + ".protocol", safe(provider.protocolProperty().get()));
        }

        raw.setProperty("instance.count", Integer.toString(instances.size()));
        for (int i = 0; i < instances.size(); i++) {
            AgentInstance instance = instances.get(i);
            raw.setProperty("instance." + i + ".id", safe(instance.idProperty().get()));
            raw.setProperty("instance." + i + ".name", safe(instance.nameProperty().get()));
            raw.setProperty("instance." + i + ".providerId", safe(instance.providerIdProperty().get()));
            raw.setProperty("instance." + i + ".description", safe(instance.descriptionProperty().get()));
            raw.setProperty("instance." + i + ".coreVersion", safe(instance.coreVersionProperty().get()));
            raw.setProperty("instance." + i + ".profileName", safe(instance.profileNameProperty().get()));
            raw.setProperty("instance." + i + ".profileTemplate", safe(instance.profileTemplateProperty().get()));
            raw.setProperty("instance." + i + ".webPort", safe(instance.webPortProperty().get()));
            raw.setProperty("instance." + i + ".model", safe(instance.modelProperty().get()));
            raw.setProperty("instance." + i + ".extensionIds", safe(instance.extensionIdsProperty().get()));
            raw.setProperty("instance." + i + ".command", safe(instance.commandProperty().get()));
            raw.setProperty("instance." + i + ".arguments", safe(instance.argumentsProperty().get()));
            raw.setProperty("instance." + i + ".workingDirectory", safe(instance.workingDirectoryProperty().get()));
        }

        raw.setProperty("extension.count", Integer.toString(extensions.size()));
        for (int i = 0; i < extensions.size(); i++) {
            AgentExtension extension = extensions.get(i);
            raw.setProperty("extension." + i + ".id", safe(extension.idProperty().get()));
            raw.setProperty("extension." + i + ".name", safe(extension.nameProperty().get()));
            raw.setProperty("extension." + i + ".type", safe(extension.typeProperty().get()));
            raw.setProperty("extension." + i + ".location", safe(extension.locationProperty().get()));
            raw.setProperty("extension." + i + ".enabled", Boolean.toString(extension.enabledProperty().get()));
        }

        if (getSelectedProvider() != null) raw.setProperty("selected.provider", getSelectedProvider().getId());
        if (getSelectedInstance() != null) raw.setProperty("selected.instance", getSelectedInstance().getId());

        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream output = Files.newOutputStream(configFile)) {
                raw.store(output, "DSHCraft DSH HMCL-native configuration");
            }
        } catch (IOException e) {
            LOG.warning("Failed to save DShCraft Agent config: " + configFile, e);
        }
    }

    private void saveIfReady() {
        if (!loading) save();
    }

    private void wireAutoSave(AgentProvider provider) {
        provider.idProperty().addListener(observable -> saveIfReady());
        provider.nameProperty().addListener(observable -> saveIfReady());
        provider.typeProperty().addListener(observable -> saveIfReady());
        provider.baseUrlProperty().addListener(observable -> saveIfReady());
        provider.modelProperty().addListener(observable -> saveIfReady());
        provider.apiKeyEnvProperty().addListener(observable -> saveIfReady());
        provider.protocolProperty().addListener(observable -> saveIfReady());
    }

    /// Wires every portable instance field to repository persistence.
    private void wireAutoSave(AgentInstance instance) {
        instance.idProperty().addListener(observable -> saveIfReady());
        instance.nameProperty().addListener(observable -> saveIfReady());
        instance.providerIdProperty().addListener(observable -> saveIfReady());
        instance.descriptionProperty().addListener(observable -> saveIfReady());
        instance.coreVersionProperty().addListener(observable -> saveIfReady());
        instance.profileNameProperty().addListener(observable -> saveIfReady());
        instance.profileTemplateProperty().addListener(observable -> saveIfReady());
        instance.webPortProperty().addListener(observable -> saveIfReady());
        instance.modelProperty().addListener(observable -> saveIfReady());
        instance.extensionIdsProperty().addListener(observable -> {
            if (instance == getSelectedInstance()) syncSelectedExtensions();
            saveIfReady();
        });
        instance.commandProperty().addListener(observable -> saveIfReady());
        instance.argumentsProperty().addListener(observable -> saveIfReady());
        instance.workingDirectoryProperty().addListener(observable -> saveIfReady());
    }

    private void wireAutoSave(AgentExtension extension) {
        extension.idProperty().addListener(observable -> saveIfReady());
        extension.nameProperty().addListener(observable -> saveIfReady());
        extension.typeProperty().addListener(observable -> saveIfReady());
        extension.locationProperty().addListener(observable -> saveIfReady());
        extension.enabledProperty().addListener(observable -> saveIfReady());
    }

    /// Keeps built-in DSH capabilities visible in the matching HMCL Downloads categories.
    private void addBuiltinCapabilities() {
        for (AgentExtension builtin : BUILTIN_CAPABILITIES) {
            boolean exists = extensions.stream().anyMatch(extension -> builtin.getId().equals(extension.getId()));
            if (exists) continue;
            AgentExtension entry = new AgentExtension(builtin.getId(), builtin.getName(),
                    builtin.typeProperty().get(), "", false);
            wireAutoSave(entry);
            extensions.add(entry);
        }
    }

    private String value(String key, String fallback) {
        return raw.getProperty(key, fallback);
    }

    private int intValue(String key, int fallback) {
        try {
            return Integer.parseInt(raw.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean booleanValue(String key, boolean fallback) {
        String value = raw.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    /// Removes saved per-instance environment values when an instance is deleted.
    private void clearInstanceEnvironment(String instanceId) {
        String prefix = "instance." + instanceId + ".env.";
        raw.keySet().removeIf(key -> String.valueOf(key).startsWith(prefix));
    }

    private void clearManagedKeys(String prefix) {
        raw.keySet().removeIf(key -> String.valueOf(key).startsWith(prefix));
    }

    private void clearIndexedInstanceKeysExceptEnvironment() {
        raw.keySet().removeIf(keyObject -> {
            String key = String.valueOf(keyObject);
            if (!key.startsWith("instance.")) return false;
            if (key.matches("instance\\.[^.]+\\.env\\..+")) return false;
            return true;
        });
    }

    private String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void putIfNotBlank(Map<String, String> environment, String key, String value) {
        if (value != null && !value.isBlank()) {
            environment.put(key, value);
        }
    }

    static List<String> splitArguments(String value) {
        ArrayList<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;

        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                token.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (quoted) {
                if (ch == quote) quoted = false;
                else token.append(ch);
            } else if (ch == '\'' || ch == '"') {
                quoted = true;
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                if (!token.isEmpty()) {
                    result.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(ch);
            }
        }
        if (escaped) token.append('\\');
        if (!token.isEmpty()) result.add(token.toString());
        return result;
    }
}
