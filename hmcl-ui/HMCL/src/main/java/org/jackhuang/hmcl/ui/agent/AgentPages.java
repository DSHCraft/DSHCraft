/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import org.jackhuang.hmcl.agent.AgentBackupService;
import org.jackhuang.hmcl.agent.AgentDiagnostics;
import org.jackhuang.hmcl.agent.AgentExtension;
import org.jackhuang.hmcl.agent.AgentInstance;
import org.jackhuang.hmcl.agent.AgentNetworkService;
import org.jackhuang.hmcl.agent.AgentMcpService;
import org.jackhuang.hmcl.agent.AgentPackService;
import org.jackhuang.hmcl.agent.AgentProvider;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.agent.AgentSecretStore;
import org.jackhuang.hmcl.agent.AgentSkillService;
import org.jackhuang.hmcl.agent.DshModService;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Lazy page registry and HMCL-native actions for DShCraft Agent business pages.
@NotNullByDefault
public final class AgentPages {
    /// Lazily-created provider list page.
    private static @Nullable AgentListPage<AgentProvider> providers;
    /// Lazily-created instance list page.
    private static @Nullable AgentListPage<AgentInstance> instances;
    /// Lazily-created extension list page.
    private static @Nullable AgentListPage<AgentExtension> extensions;
    /// Lazily-created settings shell.
    private static @Nullable AgentSettingsPage settings;
    /// Lazily-created live DSH output page.
    private static @Nullable AgentConsolePage console;
    /// Lazily-created HMCL unified Downloads page.
    private static @Nullable AgentDownloadsPage downloads;
    /// Prevents concurrent mutations of the same DSH Profile.
    private static final Set<String> BUSY_PROFILES = ConcurrentHashMap.newKeySet();
    /// Verified provider and protocol pairs shown in the existing HMCL choice dialog.
    private static final @Unmodifiable List<ProviderPreset> PROVIDER_PRESETS = List.of(
            new ProviderPreset("DeepSeek Official · Chat Completions", "DeepSeek Official", "https://api.deepseek.com", "openai-completions", "DEEPSEEK_API_KEY", "deepseek-flash"),
            new ProviderPreset("DeepSeek Official · Responses", "DeepSeek Official", "https://api.deepseek.com", "openai-responses", "DEEPSEEK_API_KEY", "deepseek-flash"),
            new ProviderPreset("OpenAI · Chat Completions", "OpenAI-compatible", "https://api.openai.com/v1", "openai-completions", "OPENAI_API_KEY", ""),
            new ProviderPreset("OpenAI · Responses", "OpenAI-compatible", "https://api.openai.com/v1", "openai-responses", "OPENAI_API_KEY", ""),
            new ProviderPreset("OpenRouter · Chat Completions", "OpenAI-compatible", "https://openrouter.ai/api/v1", "openai-completions", "OPENROUTER_API_KEY", ""),
            new ProviderPreset("OpenRouter · Responses", "OpenAI-compatible", "https://openrouter.ai/api/v1", "openai-responses", "OPENROUTER_API_KEY", ""),
            new ProviderPreset("Google Gemini · Chat Completions", "OpenAI-compatible", "https://generativelanguage.googleapis.com/v1beta/openai", "openai-completions", "GEMINI_API_KEY", ""),
            new ProviderPreset("Anthropic · Messages", "Anthropic-compatible", "https://api.anthropic.com", "anthropic-messages", "ANTHROPIC_API_KEY", ""),
            new ProviderPreset("Ollama Local · Chat Completions", "Local OpenAI-compatible", "http://localhost:11434/v1", "openai-completions", "OLLAMA_API_KEY", ""),
            new ProviderPreset("vLLM Local · Chat Completions", "Local OpenAI-compatible", "http://127.0.0.1:8000/v1", "openai-completions", "OPENAI_API_KEY", ""),
            new ProviderPreset("Custom · Chat Completions", "OpenAI-compatible", "", "openai-completions", "OPENAI_API_KEY", ""),
            new ProviderPreset("Custom · Responses", "OpenAI-compatible", "", "openai-responses", "OPENAI_API_KEY", ""),
            new ProviderPreset("Custom · Anthropic Messages", "Anthropic-compatible", "", "anthropic-messages", "ANTHROPIC_API_KEY", ""));

    /// Endpoint metadata kept separate from its presentation in HMCL's existing candidate dialog.
    private record ProviderPreset(String label, String type, String baseUrl, String protocol,
                                  String apiKeyEnv, String defaultModel) {
    }

    /// Prevents construction of this page registry.
    private AgentPages() {
    }

    /// Returns the persistent Agent settings shell.
    public static AgentSettingsPage settings() {
        if (settings == null) settings = new AgentSettingsPage();
        return settings;
    }

    /// Opens the HMCL-native DSH console for the active managed process.
    public static void showConsole() {
        if (console == null) console = new AgentConsolePage();
        Controllers.navigate(console);
    }

    /// Returns the shared HMCL Downloads shell for Core, Packs and Mod categories.
    public static AgentDownloadsPage downloads() {
        if (downloads == null) downloads = new AgentDownloadsPage();
        return downloads;
    }

    /// Returns the HMCL-native provider list page.
    public static AgentListPage<AgentProvider> providers() {
        if (providers == null) {
            AgentRepository repo = AgentRepository.get();
            providers = new AgentListPage<>(
                    i18n("agent.providers"),
                    repo.getProviders(),
                    repo::addProvider,
                    repo::load,
                    AgentPages::openProvider,
                    repo::setSelectedProvider,
                    AgentPages::removeProvider,
                    repo::setSelectedProvider,
                    provider -> provider == repo.getSelectedProvider(),
                    repo.selectedProviderProperty(),
                    i18n("agent.provider.add"),
                    i18n("agent.provider.current"), true, false);
        }
        return providers;
    }

    /// Returns the HMCL-native Agent instance list page.
    public static AgentListPage<AgentInstance> instances() {
        if (instances == null) {
            AgentRepository repo = AgentRepository.get();
            instances = new AgentListPage<>(
                    i18n("agent.instances"),
                    repo.getInstances(),
                    repo::addInstance,
                    repo::load,
                    AgentPages::openInstance,
                    repo::setSelectedInstance,
                    AgentPages::removeInstance,
                    AgentPages::launch,
                    instance -> instance == repo.getSelectedInstance(),
                    repo.selectedInstanceProperty(),
                    i18n("agent.instance.add"),
                    i18n("agent.launch"), false, false);
        }
        return instances;
    }

    /// Returns the HMCL-native Plugin/MCP/Skill list page.
    public static AgentListPage<AgentExtension> extensions() {
        AgentRepository repo = AgentRepository.get();
        AgentInstance selected = repo.getSelectedInstance();
        if (selected != null) {
            try {
                repo.refreshInstalledMods(selected);
            } catch (IOException error) {
                showError(i18n("agent.mod.sync.failed"), error);
            }
        }
        if (extensions == null) {
            extensions = new AgentListPage<>(
                    i18n("agent.extensions"),
                    repo.getExtensions(),
                    repo::addExtension,
                    repo::load,
                    AgentPages::openExtension,
                    AgentPages::toggleMod,
                    AgentPages::removeModEntry,
                    AgentPages::toggleMod,
                    extension -> repo.getSelectedInstance() != null
                            && repo.hasExtension(repo.getSelectedInstance(), extension),
                    repo.selectedInstanceProperty(),
                    i18n("agent.extension.add"),
                    i18n("agent.extension.toggle"), false, true);
        }
        return extensions;
    }

    /// Opens a provider editor with online model discovery while preserving the HMCL settings-row visual implementation.
    public static void openProvider(AgentProvider provider) {
        AgentEditorPage page = new AgentEditorPage(provider.getName())
                .addText(i18n("agent.field.name"), i18n("agent.field.name.provider.subtitle"), provider.nameProperty(), false)
                .addReadOnlyText(i18n("agent.field.id"), i18n("agent.field.id.subtitle"), provider.idProperty())
                .addLabeledChoice(i18n("agent.provider.protocol"), i18n("agent.provider.protocol.subtitle"),
                        provider.protocolProperty(),
                        new String[]{"openai-completions", "openai-responses", "anthropic-messages"},
                        new String[]{"Chat Completions", "Responses", "Anthropic Messages"})
                .addText(i18n("agent.field.base_url"), i18n("agent.field.base_url.subtitle"), provider.baseUrlProperty(), true)
                .addText(i18n("agent.field.model"), i18n("agent.field.model.subtitle"), provider.modelProperty(), true)
                .addText(i18n("agent.field.api_key_env"), i18n("agent.field.api_key_env.subtitle"), provider.apiKeyEnvProperty(), true);
        if (AgentSecretStore.isSupported()) {
            page.addAction(i18n("agent.provider.secret.title"), i18n("agent.provider.secret.subtitle"), SVG.EDIT,
                            () -> Controllers.dialog(new AgentSecretDialogPane(provider)))
                    .addAction(i18n("agent.provider.secret.delete"), i18n("agent.provider.secret.delete.subtitle"), SVG.DELETE,
                            () -> Controllers.confirm(i18n("agent.provider.secret.delete.confirm"),
                                    i18n("agent.provider.secret.delete"),
                                    () -> deleteProviderSecret(provider), () -> {}));
        }
        page.addAction(i18n("agent.provider.presets"), i18n("agent.provider.presets.subtitle"), SVG.DOWNLOAD,
                        () -> applyProviderPreset(provider))
                .addAction(i18n("agent.models.discover"), i18n("agent.models.discover.subtitle"), SVG.REFRESH,
                        () -> discoverModels(provider))
                .addAction(i18n("agent.config.open"), AgentRepository.get().getConfigFile().toString(), SVG.FOLDER_OPEN,
                        () -> FXUtils.showFileInExplorer(AgentRepository.get().getConfigFile()));
        Controllers.navigate(page);
    }

    /// Applies one verified provider/protocol pair using the existing HMCL candidate dialog.
    private static void applyProviderPreset(AgentProvider provider) {
        String[] names = PROVIDER_PRESETS.stream().map(ProviderPreset::label).toArray(String[]::new);
        PromptDialogPane.Builder.CandidatesQuestion choice = new PromptDialogPane.Builder.CandidatesQuestion(
                i18n("agent.provider.presets.choose"), names);
        Controllers.prompt(new PromptDialogPane.Builder(i18n("agent.provider.presets"),
                (questions, handler) -> handler.resolve()).addQuestion(choice)).thenAccept(ignored -> {
            Integer index = choice.getValue();
            if (index == null || index < 0 || index >= PROVIDER_PRESETS.size()) return;
            ProviderPreset preset = PROVIDER_PRESETS.get(index);
            if (AgentSecretStore.isSupported()) {
                try {
                    if (AgentSecretStore.has(provider.getId())) AgentSecretStore.delete(provider.getId());
                } catch (IOException error) {
                    showError(i18n("agent.provider.secret.delete.failed"), error);
                    return;
                }
            }
            boolean endpointChanged = !provider.baseUrlProperty().get().equals(preset.baseUrl())
                    || !provider.protocolProperty().get().equals(preset.protocol());
            provider.nameProperty().set(preset.label());
            provider.typeProperty().set(preset.type());
            provider.protocolProperty().set(preset.protocol());
            provider.apiKeyEnvProperty().set(preset.apiKeyEnv());
            provider.baseUrlProperty().set(preset.baseUrl());
            if (endpointChanged) provider.modelProperty().set(preset.defaultModel());
            Controllers.showToast(i18n("agent.provider.preset.applied", preset.label()));
        });
    }

    /// Deletes an OS credential before removing its Provider metadata.
    private static void removeProvider(AgentProvider provider) {
        try {
            AgentRepository.get().removeProvider(provider);
        } catch (IOException error) {
            showError(i18n("agent.provider.secret.delete.failed"), error);
        }
    }

    /// Deletes one Provider secret without deleting the Provider itself.
    private static void deleteProviderSecret(AgentProvider provider) {
        try {
            AgentSecretStore.delete(provider.getId());
            Controllers.showToast(i18n("agent.provider.secret.deleted"));
        } catch (IOException error) {
            showError(i18n("agent.provider.secret.delete.failed"), error);
        }
    }

    /// Opens the instance's Core and launch settings without provider creation controls.
    public static void openInstance(AgentInstance instance) {
        AgentRepository repository = AgentRepository.get();
        AgentEditorPage page = new AgentEditorPage(instance.getName())
                .addText(i18n("agent.field.name"), i18n("agent.field.name.instance.subtitle"), instance.nameProperty(), false)
                .addText(i18n("agent.field.description"), i18n("agent.field.description.subtitle"), instance.descriptionProperty(), true)
                .addReadOnlyText(i18n("agent.field.id"), i18n("agent.field.id.subtitle"), instance.idProperty())
                .addReadOnlyText(i18n("agent.field.core_version"), i18n("agent.field.core_version.subtitle"), instance.coreVersionProperty())
                .addAction(i18n("agent.core.select"), i18n("agent.core.select.subtitle"), SVG.DOWNLOAD,
                        () -> selectCoreVersion(instance))
                .addAction(i18n("agent.core.browse"), i18n("agent.core.browse.subtitle"), SVG.SEARCH,
                        () -> {
                            repository.setSelectedInstance(instance);
                            AgentDownloadsPage downloadPage = downloads();
                            downloadPage.showCore();
                            Controllers.navigate(downloadPage);
                        })
                .addAction(i18n("agent.core.install"), i18n("agent.core.install.subtitle"), SVG.DOWNLOAD,
                        () -> installCore(instance))
                .addLabeledChoice(i18n("agent.instance.provider.select"), i18n("agent.instance.provider.select.subtitle"),
                        instance.providerIdProperty(),
                        repository.getProviders().stream().map(AgentProvider::getId).toArray(String[]::new),
                        repository.getProviders().stream().map(AgentProvider::getName).toArray(String[]::new))
                .addText(i18n("agent.field.profile_name"), i18n("agent.field.profile_name.subtitle"), instance.profileNameProperty(), false)
                .addText(i18n("agent.field.profile_template"), i18n("agent.field.profile_template.subtitle"), instance.profileTemplateProperty(), false)
                .addText(i18n("agent.field.web_port"), i18n("agent.field.web_port.subtitle"), instance.webPortProperty(), false)
                .addText(i18n("agent.field.model"), i18n("agent.field.model.instance.subtitle"), instance.modelProperty(), true)
                .addText(i18n("agent.field.extension_ids"), i18n("agent.field.extension_ids.subtitle"), instance.extensionIdsProperty(), true)
                .addAction(i18n("agent.extensions"), i18n("agent.mod.instance.subtitle"), SVG.EXTENSION,
                        () -> {
                            AgentDownloadsPage downloadPage = downloads();
                            downloadPage.showPlugins();
                            Controllers.navigate(downloadPage);
                        })
                .addText(i18n("agent.field.arguments"), i18n("agent.field.arguments.subtitle"), instance.argumentsProperty(), true)
                .addText(i18n("agent.field.cwd"), i18n("agent.field.cwd.subtitle"), instance.workingDirectoryProperty(), true)
                .addAction(i18n("agent.pack.export"), i18n("agent.pack.export.subtitle"), SVG.OUTPUT,
                        () -> exportPack(instance))
                .addAction(i18n("agent.instance.validate"), i18n("agent.instance.validate.subtitle"), SVG.CHECK,
                        () -> validateInstance(instance))
                .addAction(i18n("agent.instance.home.open"), AgentRepository.get().getInstanceHome(instance).toString(), SVG.FOLDER_OPEN,
                        () -> showInstanceHome(instance))
                .addAction(i18n("agent.console"), i18n("agent.console.subtitle"), SVG.SCRIPT,
                        AgentPages::showConsole)
                .addAction(i18n("agent.launch"), i18n("agent.launch.subtitle"), SVG.ROCKET_LAUNCH,
                        () -> launch(instance));
        Controllers.navigate(page);
    }

    /// Opens an extension editor using only upstream HMCL controls.
    public static void openExtension(AgentExtension extension) {
        AgentEditorPage page = new AgentEditorPage(extension.getName())
                .addText(i18n("agent.field.name"), i18n("agent.field.name.extension.subtitle"), extension.nameProperty(), false)
                .addReadOnlyText(i18n("agent.field.id"), i18n("agent.field.id.subtitle"), extension.idProperty())
                .addText(i18n("agent.field.type"), i18n("agent.field.type.extension.subtitle"), extension.typeProperty(), false)
                .addText(i18n("agent.field.location"),
                        "mcp".equalsIgnoreCase(extension.typeProperty().get())
                                ? i18n("agent.mcp.location.subtitle") : i18n("agent.field.location.subtitle"),
                        extension.locationProperty(), true);
        if (isPackageBacked(extension)) {
            page.addAction(i18n("agent.mod.install"), i18n("agent.mod.install.subtitle"), SVG.DOWNLOAD,
                            () -> manageMod(extension, DshModService.Action.INSTALL))
                    .addAction(i18n("agent.mod.remove"), i18n("agent.mod.remove.subtitle"), SVG.DELETE,
                            () -> manageMod(extension, DshModService.Action.REMOVE))
                    .addAction(i18n("agent.mod.update"), i18n("agent.mod.update.subtitle"), SVG.REFRESH,
                            () -> manageMod(extension, DshModService.Action.UPDATE));
        } else if ("skill".equalsIgnoreCase(extension.typeProperty().get())
                && !"skills".equals(extension.getId())) {
            page.addAction(i18n("agent.skill.import"), i18n("agent.skill.import.subtitle"), SVG.FOLDER_OPEN,
                    () -> importSkill(extension));
        } else if ("mcp".equalsIgnoreCase(extension.typeProperty().get())
                && !"mcp-client".equals(extension.getId())) {
            page.addAction(i18n("agent.mcp.connect"), i18n("agent.mcp.connect.subtitle"), SVG.PUBLIC,
                            () -> requestMcpEnabled(extension, true))
                    .addAction(i18n("agent.mcp.disconnect"), i18n("agent.mcp.disconnect.subtitle"), SVG.DELETE,
                            () -> setMcpEnabled(extension, false));
            if (AgentSecretStore.isSupported() && !AgentMcpService.isStdio(extension.locationProperty().get())) {
                page.addAction(i18n("agent.mcp.token.save"), i18n("agent.mcp.token.subtitle"), SVG.EDIT,
                                () -> Controllers.dialog(new AgentSecretDialogPane(extension)))
                        .addAction(i18n("agent.mcp.token.delete"), i18n("agent.mcp.token.delete.subtitle"), SVG.DELETE,
                                () -> Controllers.confirm(i18n("agent.mcp.token.delete.confirm"),
                                        i18n("agent.mcp.token.delete"),
                                        () -> deleteMcpToken(extension), () -> {}));
            }
        } else {
            page.addAction(i18n("agent.mod.metadata"), i18n("agent.mod.metadata.subtitle"), SVG.INFO,
                    () -> Controllers.dialog(i18n("agent.mod.metadata.subtitle"),
                            i18n("agent.mod.metadata"), MessageDialogPane.MessageType.INFO));
        }
        Controllers.navigate(page);
    }

    /// Warns before a locally configured stdio server can execute on the next DSH launch.
    private static void requestMcpEnabled(AgentExtension extension, boolean enabled) {
        if (enabled && AgentMcpService.isStdio(extension.locationProperty().get())) {
            Controllers.confirm(i18n("agent.mcp.stdio.confirm"), i18n("agent.mcp.connect"),
                    () -> setMcpEnabled(extension, true), () -> {});
        } else {
            setMcpEnabled(extension, enabled);
        }
    }

    /// Enables or disables one HTTP MCP server in the selected isolated instance.
    private static void setMcpEnabled(AgentExtension extension, boolean enabled) {
        AgentRepository repository = AgentRepository.get();
        AgentInstance instance = repository.getSelectedInstance();
        if (instance == null) {
            Controllers.dialog(i18n("agent.mod.instance.required"),
                    i18n("message.error"), MessageDialogPane.MessageType.WARNING);
            return;
        }
        try {
            List<AgentExtension> selected = repository.getExtensions().stream()
                    .filter(entry -> "MCP".equalsIgnoreCase(entry.typeProperty().get())
                            && !"mcp-client".equals(entry.getId())
                            && (entry == extension ? enabled : repository.hasExtension(instance, entry)))
                    .toList();
            Set<String> bearerTokens = new java.util.LinkedHashSet<>();
            if (AgentSecretStore.isSupported()) {
                for (AgentExtension entry : selected) {
                    if (AgentSecretStore.hasMcp(entry.getId())) bearerTokens.add(entry.getId());
                }
            }
            AgentMcpService.ensurePatch(repository.getInstanceHome(instance), selected, bearerTokens);
            repository.setExtensionInstalled(instance, extension, enabled);
            repository.save();
            Controllers.showToast(i18n(enabled ? "agent.mcp.connected" : "agent.mcp.disconnected", extension.getName()));
        } catch (IOException error) {
            showError(i18n("agent.mcp.failed"), error);
        }
    }

    /// Removes one MCP token from the OS credential store without touching its endpoint metadata.
    private static void deleteMcpToken(AgentExtension extension) {
        try {
            AgentSecretStore.deleteMcp(extension.getId());
            Controllers.showToast(i18n("agent.mcp.token.deleted"));
        } catch (IOException error) {
            showError(i18n("agent.mcp.token.delete.failed"), error);
        }
    }

    /// Imports a local skill bundle into the selected instance's isolated DSH_HOME.
    private static void importSkill(AgentExtension extension) {
        AgentRepository repository = AgentRepository.get();
        AgentInstance instance = repository.getSelectedInstance();
        if (instance == null) {
            Controllers.dialog(i18n("agent.mod.instance.required"),
                    i18n("message.error"), MessageDialogPane.MessageType.WARNING);
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(i18n("agent.skill.import"));
        @Nullable Path source = Controllers.showDialog(chooser);
        if (source == null) return;
        Controllers.showToast(i18n("agent.skill.import.running"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return AgentSkillService.install(source, repository.getInstanceHome(instance));
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }).whenComplete((installed, error) -> Platform.runLater(() -> {
            if (error != null) {
                showError(i18n("agent.skill.import.failed"), rootCause(error));
                return;
            }
            extension.nameProperty().set(installed.getFileName().toString());
            extension.locationProperty().set(source.toString());
            repository.setExtensionInstalled(instance, extension, true);
            repository.save();
            Controllers.showToast(i18n("agent.skill.import.success", installed.getFileName()));
        }));
    }

    /// Only npm-backed Plugin/Bundle entries can be passed to DSH's plugin command.
    private static boolean isPackageBacked(AgentExtension extension) {
        String type = extension.typeProperty().get();
        return "plugin".equalsIgnoreCase(type) || "bundle".equalsIgnoreCase(type);
    }

    /// Toggles the selected DSH instance's installed package rather than catalog metadata.
    static void toggleMod(AgentExtension extension) {
        AgentRepository repository = AgentRepository.get();
        AgentInstance selected = repository.getSelectedInstance();
        if ("mcp".equalsIgnoreCase(extension.typeProperty().get())
                && !"mcp-client".equals(extension.getId()) && selected != null) {
            requestMcpEnabled(extension, !repository.hasExtension(selected, extension));
        } else if (!isPackageBacked(extension)) {
            Controllers.dialog(i18n("agent.mod.metadata.subtitle"),
                    i18n("agent.mod.metadata"), MessageDialogPane.MessageType.INFO);
        } else if (selected == null) {
            Controllers.dialog(i18n("agent.mod.instance.required"),
                    i18n("message.error"), MessageDialogPane.MessageType.WARNING);
        } else {
            manageMod(extension, repository.hasExtension(selected, extension)
                    ? DshModService.Action.REMOVE : DshModService.Action.INSTALL);
        }
    }

    /// Keeps a catalog entry until its package is absent from every instance Profile.
    static void removeModEntry(AgentExtension extension) {
        AgentRepository repository = AgentRepository.get();
        try {
            for (AgentInstance instance : repository.getInstances()) {
                if (repository.hasExtension(instance, extension)
                        || (isPackageBacked(extension) && !extension.locationProperty().get().isBlank()
                        && DshModService.installedPackages(repository.getInstanceHome(instance),
                        instance.profileNameProperty().get()).contains(packageName(extension.locationProperty().get())))) {
                    Controllers.dialog(i18n("agent.mod.catalog.in.use"),
                            i18n("message.error"), MessageDialogPane.MessageType.WARNING);
                    return;
                }
            }
            if ("mcp".equalsIgnoreCase(extension.typeProperty().get())
                    && !"mcp-client".equals(extension.getId()) && AgentSecretStore.isSupported()) {
                AgentSecretStore.deleteMcp(extension.getId());
            }
            repository.removeExtension(extension);
        } catch (IOException error) {
            showError(i18n("agent.mod.sync.failed"), error);
        }
    }

    /// Extracts the npm package name for read-only installed-state checks.
    private static String packageName(String spec) {
        String value = spec.trim();
        int slash = value.indexOf('/');
        int versionAt = value.startsWith("@")
                ? value.indexOf('@', slash + 1)
                : value.indexOf('@');
        return versionAt < 0 ? value : value.substring(0, versionAt);
    }

    /// Executes a DSH Mod operation asynchronously and refreshes only the target instance.
    public static void manageMod(AgentExtension extension, DshModService.Action action) {
        AgentRepository repository = AgentRepository.get();
        AgentInstance instance = repository.getSelectedInstance();
        if (instance == null) {
            Controllers.dialog(i18n("agent.mod.instance.required"),
                    i18n("message.error"), MessageDialogPane.MessageType.WARNING);
            return;
        }
        List<String> problems = repository.validateInstanceConfiguration(instance);
        if (!problems.isEmpty()) {
            Controllers.dialog(String.join("\n", problems),
                    i18n("agent.instance.validate.failed"), MessageDialogPane.MessageType.WARNING);
            return;
        }
        String version = instance.coreVersionProperty().get();
        String profile = instance.profileNameProperty().get();
        String spec = extension.locationProperty().get();
        Path home = repository.getInstanceHome(instance);
        String busyKey = home + ":" + profile;
        if (!BUSY_PROFILES.add(busyKey)) {
            Controllers.showToast(i18n("agent.mod.busy"));
            return;
        }
        Controllers.showToast(i18n("agent.mod.running"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return DshModService.run(DshModService.runtimeRoot(), home, version, profile, spec, action,
                        repository.getPackageRegistry());
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(error);
            }
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            BUSY_PROFILES.remove(busyKey);
            if (error != null) {
                showError(i18n("agent.mod.failed"), rootCause(error));
                return;
            }
            try {
                if (!version.equals(result.coreVersion())) instance.coreVersionProperty().set(result.coreVersion());
                repository.refreshInstalledMods(instance);
                Controllers.showToast(i18n("agent.mod.success", extension.getName()));
            } catch (IOException ioError) {
                showError(i18n("agent.mod.sync.failed"), ioError);
            }
        }));
    }

    /// Installs the selected npm DSH Core without reusing the user's global DSH installation.
    public static void installCore(AgentInstance instance) {
        installCore(instance, instance.coreVersionProperty().get());
    }

    /// Installs a requested Core before committing that version to the instance.
    public static void installCore(AgentInstance instance, String requested) {
        String busyKey = "core:" + requested;
        if (!BUSY_PROFILES.add(busyKey)) {
            Controllers.showToast(i18n("agent.mod.busy"));
            return;
        }
        Controllers.showToast(i18n("agent.core.install.running"));
        CompletableFuture.supplyAsync(() -> {
            try {
                AgentRepository repository = AgentRepository.get();
                return DshModService.ensureCore(DshModService.runtimeRoot(), requested,
                        repository.getPackageRegistry());
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(error);
            }
        }).whenComplete((runtime, error) -> Platform.runLater(() -> {
            BUSY_PROFILES.remove(busyKey);
            if (error != null) {
                showError(i18n("agent.core.install.failed"), rootCause(error));
            } else {
                String concrete = runtime.getFileName().toString();
                instance.coreVersionProperty().set(concrete);
                Controllers.showToast(i18n("agent.core.install.success", concrete));
            }
        }));
    }


    /// Validates browser-manageable instance metadata before any native runtime action is attempted.
    public static void validateInstance(AgentInstance instance) {
        List<String> problems = AgentRepository.get().validateInstanceConfiguration(instance);
        if (problems.isEmpty()) {
            Controllers.dialog(i18n("agent.instance.validate.success"), i18n("message.success"), MessageDialogPane.MessageType.SUCCESS);
        } else {
            Controllers.dialog(String.join("\n", problems), i18n("agent.instance.validate.failed"), MessageDialogPane.MessageType.WARNING);
        }
    }

    /// Creates and reveals the isolated DSH_HOME so the action also works before the first launch.
    private static void showInstanceHome(AgentInstance instance) {
        Path home = AgentRepository.get().getInstanceHome(instance);
        try {
            Files.createDirectories(home);
            FXUtils.showFileInExplorer(home);
        } catch (IOException | RuntimeException e) {
            showError(i18n("agent.instance.home.failed"), e);
        }
    }

    /// Launches one Agent instance through the existing repository process lifecycle.
    public static void launch(AgentInstance instance) {
        AgentRepository repo = AgentRepository.get();
        repo.setSelectedInstance(instance);
        try {
            repo.save();
            String version = instance.coreVersionProperty().get().trim();
            if (DshModService.isCoreInstalled(DshModService.runtimeRoot(), version)) {
                repo.launchSelected();
                showConsole();
                return;
            }
            String busyKey = "launch:" + instance.getId();
            if (!BUSY_PROFILES.add(busyKey)) {
                Controllers.showToast(i18n("agent.mod.busy"));
                return;
            }
            Controllers.showToast(i18n("agent.core.install.running"));
            String registry = repo.getPackageRegistry();
            CompletableFuture.supplyAsync(() -> {
                try {
                    return DshModService.ensureCore(DshModService.runtimeRoot(), version, registry);
                } catch (IOException | InterruptedException error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new CompletionException(error);
                }
            }).whenComplete((runtime, failure) -> Platform.runLater(() -> {
                BUSY_PROFILES.remove(busyKey);
                if (failure != null) {
                    showError(i18n("agent.core.install.failed"), rootCause(failure));
                    return;
                }
                if (repo.getSelectedInstance() != instance
                        || !version.equals(instance.coreVersionProperty().get())) return;
                instance.coreVersionProperty().set(runtime.getFileName().toString());
                Controllers.showToast(i18n("agent.core.install.success", runtime.getFileName().toString()));
                try {
                    repo.launchSelected();
                    showConsole();
                } catch (IOException | RuntimeException error) {
                    showError(i18n("agent.launch.failed"), error);
                }
            }));
        } catch (IOException | RuntimeException e) {
            showError(i18n("agent.launch.failed"), e);
        }
    }

    /// Confirms deletion of one DSH instance before removing its isolated home.
    private static void removeInstance(AgentInstance instance) {
        Controllers.confirm(i18n("agent.instance.delete.confirm", instance.getName()),
                i18n("agent.instance.delete"), () -> {
                    try {
                        AgentRepository.get().removeInstance(instance);
                    } catch (IOException error) {
                        showError(i18n("agent.instance.delete.failed"), error);
                    }
                }, () -> {});
    }

    /// Exports a sanitized JSON backup of all browser-manageable configuration.
    public static void exportBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("agent.backup.export"));
        chooser.setInitialFileName("dshcraft-backup-" + LocalDate.now() + ".json");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("agent.backup.file"), "*.json"));
        @Nullable Path selected = Controllers.showSaveDialog(chooser);
        if (selected == null) return;
        Path output = ensureExtension(selected, ".json");
        try {
            AgentBackupService.exportBackup(AgentRepository.get(), output);
            Controllers.dialog(i18n("agent.backup.export.success", output), i18n("message.success"), MessageDialogPane.MessageType.SUCCESS);
        } catch (IOException | RuntimeException e) {
            showError(i18n("agent.backup.export.failed"), e);
        }
    }

    /// Imports a sanitized configuration backup after full validation.
    public static void importBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("agent.backup.import"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("agent.backup.file"), "*.json"));
        @Nullable Path selected = Controllers.showOpenDialog(chooser);
        if (selected == null) return;
        try {
            AgentBackupService.importBackup(AgentRepository.get(), selected);
            Controllers.dialog(i18n("agent.backup.import.success"), i18n("message.success"), MessageDialogPane.MessageType.SUCCESS);
        } catch (IOException | RuntimeException e) {
            showError(i18n("agent.backup.import.failed"), e);
        }
    }

    /// Exports the selected instance to the v1 JSON `.dshpack` format without secrets, workspace or session data.
    public static void exportPack(AgentInstance instance) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("agent.pack.export"));
        chooser.setInitialFileName(safeFileName(instance.getName()) + ".dshpack");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("agent.pack.file"), "*.dshpack", "*.json"));
        @Nullable Path selected = Controllers.showSaveDialog(chooser);
        if (selected == null) return;
        Path output = ensureExtension(selected, ".dshpack");
        try {
            AgentPackService.exportInstance(AgentRepository.get(), instance, output);
            Controllers.dialog(i18n("agent.pack.export.success", output), i18n("message.success"), MessageDialogPane.MessageType.SUCCESS);
        } catch (IOException | RuntimeException e) {
            showError(i18n("agent.pack.export.failed"), e);
        }
    }

    /// Installs a portable `.dshpack` in an isolated DSH_HOME before committing its instance.
    public static void importPack() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("agent.pack.import"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("agent.pack.file"), "*.dshpack", "*.json"));
        @Nullable Path selected = Controllers.showOpenDialog(chooser);
        if (selected == null) return;
        AgentRepository repository = AgentRepository.get();
        AgentPackService.PreparedPack prepared;
        try {
            prepared = AgentPackService.prepareImport(repository, selected);
        } catch (IOException | RuntimeException e) {
            showError(i18n("agent.pack.import.failed"), e);
            return;
        }
        String busyKey = "pack:" + selected.toAbsolutePath().normalize();
        if (!BUSY_PROFILES.add(busyKey)) {
            Controllers.showToast(i18n("agent.mod.busy"));
            return;
        }
        Path home = repository.getInstanceHome(prepared.instance());
        Path instancesRoot = home.getParent().getParent();
        Controllers.showToast(i18n("agent.pack.import.running"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return AgentPackService.installPrepared(prepared, DshModService.runtimeRoot(), instancesRoot);
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(error);
            }
        }).whenComplete((version, error) -> Platform.runLater(() -> {
            BUSY_PROFILES.remove(busyKey);
            if (error != null) {
                showError(i18n("agent.pack.import.failed"), rootCause(error));
                return;
            }
            try {
                AgentInstance instance = AgentPackService.commitInstalled(repository, prepared, version);
                Controllers.dialog(i18n("agent.pack.import.success", instance.getName()),
                        i18n("message.success"), MessageDialogPane.MessageType.SUCCESS);
                openInstance(instance);
            } catch (IOException commitError) {
                try {
                    AgentPackService.discardPrepared(prepared, instancesRoot);
                } catch (IOException cleanupError) {
                    commitError.addSuppressed(cleanupError);
                }
                showError(i18n("agent.pack.import.failed"), commitError);
            }
        }));
    }

    /// Exports the current secret-redacted diagnostics report as text.
    public static void exportDiagnostics() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("agent.diagnostics.export"));
        chooser.setInitialFileName("dshcraft-diagnostics-" + LocalDate.now() + ".txt");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("agent.diagnostics.file"), "*.txt", "*.log"));
        @Nullable Path selected = Controllers.showSaveDialog(chooser);
        if (selected == null) return;
        Path output = ensureExtension(selected, ".txt");
        try {
            AgentDiagnostics.writeReport(AgentRepository.get(), output);
            Controllers.dialog(i18n("agent.diagnostics.export.success", output), i18n("message.success"), MessageDialogPane.MessageType.SUCCESS);
        } catch (IOException | RuntimeException e) {
            showError(i18n("agent.diagnostics.export.failed"), e);
        }
    }

    /// Discovers real provider models in the background and lets the user select one with HMCL's prompt control.
    public static void discoverModels(AgentProvider provider) {
        Controllers.showToast(i18n("agent.models.discover.running"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return AgentNetworkService.discoverModels(provider);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }).whenComplete((models, error) -> Platform.runLater(() -> {
            if (error != null) {
                showError(i18n("agent.models.discover.failed"), rootCause(error));
                return;
            }
            if (models == null || models.isEmpty()) {
                Controllers.dialog(i18n("agent.models.empty"), i18n("agent.models.discover"), MessageDialogPane.MessageType.INFO);
                return;
            }
            PromptDialogPane.Builder.CandidatesQuestion question = new PromptDialogPane.Builder.CandidatesQuestion(
                    i18n("agent.models.select"), models.toArray(String[]::new));
            Controllers.prompt(new PromptDialogPane.Builder(i18n("agent.models.discover"), (questions, handler) -> handler.resolve())
                    .addQuestion(question)).thenAccept(ignored -> {
                Integer index = question.getValue();
                if (index != null && index >= 0 && index < models.size()) {
                    provider.modelProperty().set(models.get(index));
                    Controllers.showToast(i18n("agent.models.selected", models.get(index)));
                }
            });
        }));
    }

    /// Selects a Core version already installed in the launcher-managed runtime cache.
    public static void selectCoreVersion(AgentInstance instance) {
        List<String> versions;
        try {
            versions = DshModService.installedCoreVersions(DshModService.runtimeRoot());
        } catch (IOException error) {
            showError(i18n("agent.core.select.failed"), error);
            return;
        }
        if (versions.isEmpty()) {
            Controllers.dialog(i18n("agent.core.select.empty"), i18n("agent.core.select"), MessageDialogPane.MessageType.INFO);
            return;
        }
        PromptDialogPane.Builder.CandidatesQuestion question = new PromptDialogPane.Builder.CandidatesQuestion(
                i18n("agent.core.select.prompt"), versions.toArray(String[]::new));
        Controllers.prompt(new PromptDialogPane.Builder(i18n("agent.core.select.prompt.title"),
                (questions, handler) -> handler.resolve()).addQuestion(question)).thenAccept(ignored -> {
            Integer index = question.getValue();
            if (index != null && index >= 0 && index < versions.size()) {
                instance.coreVersionProperty().set(versions.get(index));
                Controllers.showToast(i18n("agent.core.selected", versions.get(index)));
            }
        });
    }

    /// Imports or refreshes basic Plugin/MCP/Skill metadata from a user-supplied browser-style JSON catalog.
    public static void importExtensionCatalog() {
        PromptDialogPane.Builder.StringQuestion question = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.extension.catalog.url"), "https://").setPromptText("https://example.com/plugins.json");
        Controllers.prompt(new PromptDialogPane.Builder(i18n("agent.extension.catalog"), (questions, handler) -> handler.resolve())
                .addQuestion(question)).thenAccept(ignored -> {
            String url = question.getValue();
            if (url == null || url.isBlank() || "https://".equals(url.trim())) return;
            Controllers.showToast(i18n("agent.extension.catalog.running"));
            CompletableFuture.supplyAsync(() -> {
                try {
                    return AgentNetworkService.fetchExtensionCatalog(url);
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
            }).whenComplete((entries, error) -> Platform.runLater(() -> {
                if (error != null) {
                    showError(i18n("agent.extension.catalog.failed"), rootCause(error));
                    return;
                }
                AgentRepository repository = AgentRepository.get();
                int added = 0;
                int updated = 0;
                for (AgentNetworkService.ExtensionCatalogEntry entry : entries) {
                    AgentExtension existing = repository.getExtensions().stream()
                            .filter(extension -> extension.getId().equals(entry.id()))
                            .findFirst().orElse(null);
                    if (existing == null) {
                        repository.addImportedExtension(new AgentExtension(
                                entry.id(), entry.name(), entry.kind(), entry.packageSpec(), entry.enabled()));
                        added++;
                    } else {
                        existing.nameProperty().set(entry.name());
                        existing.typeProperty().set(entry.kind());
                        existing.locationProperty().set(entry.packageSpec());
                        updated++;
                    }
                }
                repository.save();
                Controllers.dialog(i18n("agent.extension.catalog.success", added, updated),
                        i18n("message.success"), MessageDialogPane.MessageType.SUCCESS);
            }));
        });
    }

    /// Shows the latest npm dist-tags and published versions without changing any instance.
    public static void showDshCatalog() {
        Controllers.showToast(i18n("agent.core.catalog.running"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return AgentNetworkService.fetchDshCatalog(AgentRepository.get().getCatalogRegistry());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }).whenComplete((catalog, error) -> Platform.runLater(() -> {
            if (error != null) {
                showError(i18n("agent.core.catalog.failed"), rootCause(error));
                return;
            }
            StringBuilder text = new StringBuilder();
            text.append("latest: ").append(catalog.latest()).append('\n');
            text.append("next: ").append(catalog.next()).append('\n');
            text.append("alpha: ").append(catalog.alpha()).append("\n\n");
            catalog.releases().stream().limit(30).forEach(release -> text.append(release.version())
                    .append(release.publishedAt().isBlank() ? "" : "  " + release.publishedAt())
                    .append(release.deprecated().isBlank() ? "" : "  [deprecated: " + release.deprecated() + "]")
                    .append('\n'));
            Controllers.dialog(text.toString().trim(), i18n("agent.core.catalog"), MessageDialogPane.MessageType.INFO);
        }));
    }

    /// Ensures a save target ends with the requested extension.
    private static Path ensureExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        if (fileName.toLowerCase().endsWith(extension.toLowerCase())) return path;
        return path.resolveSibling(fileName + extension);
    }

    /// Converts an arbitrary instance name into a conservative portable filename stem.
    private static String safeFileName(String value) {
        String result = value == null ? "dsh-instance" : value.replaceAll("[^A-Za-z0-9._-]+", "-");
        return result.isBlank() ? "dsh-instance" : result;
    }

    /// Unwraps completion wrappers so diagnostics show the actual network failure.
    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /// Displays a consistent HMCL error dialog containing the actionable exception details.
    private static void showError(String title, Throwable throwable) {
        Controllers.dialog(
                StringUtils.escapeXmlAttribute(title + "\n\n" + StringUtils.getStackTrace(throwable)),
                i18n("message.error"),
                MessageDialogPane.MessageType.ERROR);
    }
}
