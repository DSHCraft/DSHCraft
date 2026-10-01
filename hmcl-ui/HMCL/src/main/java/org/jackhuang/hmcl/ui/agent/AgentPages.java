/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.application.Platform;
import javafx.scene.Node;
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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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
    private static @Nullable AgentInstanceWorkspacePage instances;
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
    public static AgentInstanceWorkspacePage instances() {
        if (instances == null) instances = new AgentInstanceWorkspacePage();
        return instances;
    }

    /// Returns the HMCL-native Plugin/MCP/Skill list page.
    public static AgentListPage<AgentExtension> extensions() {
        AgentRepository repo = AgentRepository.get();
        AgentInstance instance = repo.getSelectedInstance();
        if (instance == null) throw new IllegalStateException("No DSH instance selected");
        return newInstanceExtensionPage(instance, "Plugin");
    }

    /// Creates a dedicated instance-bound extension list, keeping the global catalog page intact.
    static AgentListPage<AgentExtension> newInstanceExtensionPage(AgentInstance instance, String category) {
        AgentRepository repo = AgentRepository.get();
        Runnable refresh = () -> {
            try {
                repo.refreshInstalledMods(instance);
            } catch (IOException error) {
                showError(i18n("agent.mod.sync.failed"), error);
            }
        };
        if ("Plugin".equals(category)) refresh.run();
        String title = resourceTitle(category);
        javafx.collections.transformation.FilteredList<AgentExtension> items =
                new javafx.collections.transformation.FilteredList<>(repo.getExtensions(),
                        extension -> extension.belongsToCategory(category));
        AgentListPage<AgentExtension> page = new AgentListPage<>(title, items,
                () -> {
                    AgentExtension entry = repo.addExtension(category);
                    entry.nameProperty().set(i18n("agent.resources.new." + category.toLowerCase(java.util.Locale.ROOT)));
                    return entry;
                }, refresh, AgentPages::openExtension, AgentPages::openExtension, AgentPages::removeModEntry,
                null, extension -> repo.hasExtension(instance, extension),
                repo.selectedInstanceProperty(), i18n("agent.resources.add." + category.toLowerCase(java.util.Locale.ROOT)),
                "", false, false);
        page.useResourceList();
        if ("Plugin".equals(category)) {
            page.setAddNavigationAction(() -> {
                @Nullable AgentInstance current = repo.findInstance(instance.getId());
                if (current == null) {
                    Controllers.dialog(i18n("agent.instance.empty"), i18n("message.error"), MessageDialogPane.MessageType.WARNING);
                    return;
                }
                repo.setSelectedInstance(current);
                AgentDownloadsPage catalog = downloads();
                catalog.showPlugins();
                Controllers.navigate(catalog);
            });
        }
        return page;
    }

    /// Provides separate navigation names for resources with different lifecycle contracts.
    static String resourceTitle(String category) {
        return i18n(switch (category) {
            case "Plugin" -> "agent.resources.plugins";
            case "MCP" -> "agent.resources.mcp";
            case "Skill" -> "agent.resources.skills";
            default -> throw new IllegalArgumentException("Unsupported resource category: " + category);
        });
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
        Controllers.navigate(new AgentInstanceManagementPage(instance.getId()));
    }

    /// Returns from resource downloads to the current instance's native category management.
    static void openInstanceCategory(String category) {
        @Nullable AgentInstance selected = requireSelectedInstance();
        if (selected == null) return;
        AgentInstanceManagementPage page = new AgentInstanceManagementPage(selected.getId());
        page.showCategory(switch (category) {
            case "MCP" -> "mcp";
            case "SKILLS" -> "skills";
            case "PLUGINS", "TOOLS" -> "mods";
            default -> "settings";
        });
        Controllers.navigate(page);
    }

    /// Builds and navigates the version/Profile editor for a selected instance.
    static void populateInstanceManagement(AgentInstance instance, AgentInstanceManagementPage page) {
        AgentRepository repository = AgentRepository.get();
        AgentEditorPage overview = new AgentEditorPage(instance.getName())
                .addSection(i18n("agent.workspace.basic"))
                .addText(i18n("agent.field.name"), "", instance.nameProperty(), false)
                .addText(i18n("agent.field.description"), "", instance.descriptionProperty(), true)
                .addSection(i18n("agent.workspace.service"))
                .addLabeledChoice(i18n("agent.instance.provider.select"), "",
                        instance.providerIdProperty(),
                        repository.getProviders().stream().map(AgentProvider::getId).toArray(String[]::new),
                        repository.getProviders().stream().map(AgentProvider::getName).toArray(String[]::new))
                .addText(i18n("agent.field.model"), i18n("agent.workspace.model.hint"), instance.modelProperty(), true)
                .addText(i18n("agent.field.cwd"), i18n("agent.workspace.cwd.hint"), instance.workingDirectoryProperty(), true);
        AgentEditorPage versions = new AgentEditorPage(i18n("agent.workspace.components"))
                .addSection(i18n("agent.field.core_version"))
                .addReadOnlyText(i18n("agent.field.core_version"), "", instance.coreVersionProperty())
                .addAction(i18n("agent.core.select"), "", SVG.DOWNLOAD, () -> selectCoreVersion(instance))
                .addAction(i18n("agent.core.browse"), "", SVG.SEARCH, () -> {
                    repository.setSelectedInstance(instance);
                    AgentDownloadsPage downloadPage = downloads();
                    downloadPage.showCore();
                    Controllers.navigate(downloadPage);
                })
                .addAction(i18n("agent.core.install"), "", SVG.DOWNLOAD, () -> installCore(instance))
                .addAction(i18n("agent.resources.plugins"), "", SVG.EXTENSION, () -> {
                    repository.setSelectedInstance(instance);
                    Controllers.navigate(extensions());
                })
                .addAction(i18n("agent.plugin.custom.add"), i18n("agent.workspace.drop.hint"), SVG.ADD_CIRCLE,
                        () -> requestCustomPlugin(instance));
        AgentEditorPage advanced = new AgentEditorPage(i18n("agent.workspace.advanced"))
                .addReadOnlyText(i18n("agent.field.id"), "", instance.idProperty())
                .addText(i18n("agent.field.profile_name"), "", instance.profileNameProperty(), false)
                .addText(i18n("agent.field.profile_template"), "", instance.profileTemplateProperty(), false)
                .addText(i18n("agent.field.web_port"), "", instance.webPortProperty(), false)
                .addText(i18n("agent.field.extension_ids"), "", instance.extensionIdsProperty(), true)
                .addText(i18n("agent.field.arguments"), "", instance.argumentsProperty(), true)
                .addAction(i18n("agent.instance.validate"), "", SVG.CHECK, () -> validateInstance(instance))
                .addAction(i18n("agent.instance.home.open"), "", SVG.FOLDER_OPEN, () -> showInstanceHome(instance));
        versions.addSection(i18n("agent.resources.builtin"));
        for (AgentExtension capability : repository.getExtensions()) {
            if (capability.isBuiltin()) versions.addReadOnlyText(capability.getName(), "",
                    new javafx.beans.property.SimpleStringProperty(i18n("agent.mod.builtin")));
        }
        page.showSections(instance, overview, versions, advanced);
        FXUtils.applyDragListener(page, file -> Files.isRegularFile(file)
                        && isNpmTarball(file), files -> installCustomPlugin(instance, files.get(0).toString()));
    }

    /// Identifies npm package archives accepted by the instance version manager.
    private static boolean isNpmTarball(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".tgz") || name.endsWith(".tar.gz");
    }

    /// Requests a custom npm plugin spec in the selected instance's version manager.
    private static void requestCustomPlugin(AgentInstance instance) {
        Controllers.prompt(i18n("agent.plugin.custom.add"), (spec, handler) -> {
            String value = spec == null ? "" : spec.trim();
            if (!value.matches("(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*(?:@[0-9A-Za-z.+-]+)?")) {
                handler.reject(i18n("agent.plugin.custom.invalid"));
                return;
            }
            handler.resolve();
        }, "").thenAccept(spec -> installCustomPlugin(instance, spec.trim()));
    }

    /// Sends a custom package name or dropped local npm archive through DSH's native plugin operation.
    private static void installCustomPlugin(AgentInstance instance, String spec) {
        AgentExtension entry = new AgentExtension("pending-plugin", spec, "Plugin", spec, false);
        runModOperation(entry, instance, DshModService.Action.INSTALL, true);
    }

    /// Removes an instance from the embedded workspace after the same confirmation used by the standalone list.
    static void removeInstanceFromWorkspace(AgentInstance instance) {
        removeInstance(instance, () -> {
            Controllers.navigate(instances());
        });
    }

    /// Opens an extension editor using only upstream HMCL controls.
    public static void openExtension(AgentExtension extension) {
        AgentEditorPage page = new AgentEditorPage(extension.getName())
                .addText(i18n("agent.field.name"), "", extension.nameProperty(), false)
                .addReadOnlyText(i18n("agent.field.type"), "", extension.typeProperty());
        if (isPackageBacked(extension)) {
            page.addText(i18n("agent.resources.package"), i18n("agent.resources.package.hint"), extension.locationProperty(), false);
        } else if ("MCP".equalsIgnoreCase(extension.typeProperty().get())) {
            page.addText(i18n("agent.resources.connection"), i18n("agent.mcp.location.subtitle"), extension.locationProperty(), false);
        } else {
            page.addReadOnlyText(i18n("agent.resources.skill.source"), "", extension.locationProperty());
        }
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

    /// Configures a remote HTTP MCP server in the selected instance without storing credentials in its URL.
    public static void addHttpMcp() {
        addMcpServer(false);
    }

    /// Configures a local stdio MCP process using DSH's existing MCP client overlay.
    public static void addStdioMcp() {
        addMcpServer(true);
    }

    /// Collects a server name and one validated endpoint or local command descriptor.
    private static void addMcpServer(boolean stdio) {
        AgentInstance instance = requireSelectedInstance();
        if (instance == null) return;
        PromptDialogPane.Builder.StringQuestion name = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.downloads.mcp.name"), "");
        PromptDialogPane.Builder.StringQuestion address = new PromptDialogPane.Builder.StringQuestion(
                i18n(stdio ? "agent.downloads.mcp.command" : "agent.downloads.mcp.url"),
                stdio ? "" : "https://");
        if (stdio) address.setPromptText("{\"command\":\"npx\",\"args\":[\"-y\",\"package-name\"]}");
        Controllers.prompt(new PromptDialogPane.Builder(
                i18n(stdio ? "agent.downloads.mcp.stdio" : "agent.downloads.mcp.http"),
                (questions, handler) -> {
                    String label = name.getValue() == null ? "" : name.getValue().trim();
                    if (label.isEmpty() || label.length() > 120) {
                        handler.reject(i18n("agent.downloads.mcp.name.invalid"));
                        return;
                    }
                    try {
                        if (stdio) AgentMcpService.validateStdioDescriptor(address.getValue());
                        else AgentMcpService.validateEndpoint(address.getValue());
                        handler.resolve();
                    } catch (IOException error) {
                        handler.reject(error.getMessage());
                    }
                }).addQuestion(name).addQuestion(address)).thenAccept(ignored -> {
            AgentRepository repository = AgentRepository.get();
            if (!repository.getInstances().contains(instance)) return;
            String id = "mcp-" + java.util.UUID.randomUUID().toString().substring(0, 18);
            String location = stdio ? "stdio:" + address.getValue().trim() : address.getValue().trim();
            AgentExtension server = new AgentExtension(id, name.getValue().trim(), "MCP", location, true);
            try {
                List<AgentExtension> enabled = new java.util.ArrayList<>(repository.getExtensions().stream()
                        .filter(entry -> "MCP".equalsIgnoreCase(entry.typeProperty().get())
                                && !"mcp-client".equals(entry.getId())
                                && repository.hasExtension(instance, entry)).toList());
                enabled.add(server);
                Set<String> bearerTokens = new java.util.LinkedHashSet<>();
                if (AgentSecretStore.isSupported()) {
                    for (AgentExtension entry : enabled) {
                        if (AgentSecretStore.hasMcp(entry.getId())) bearerTokens.add(entry.getId());
                    }
                }
                AgentMcpService.ensurePatch(repository.getInstanceHome(instance), enabled, bearerTokens);
                repository.addImportedExtension(server);
                repository.setExtensionInstalled(instance, server, true);
                repository.save();
                Controllers.showToast(i18n("agent.downloads.mcp.added", server.getName()));
            } catch (IOException error) {
                showError(i18n("agent.mcp.failed"), error);
            }
        });
    }

    /// Imports a local skill bundle from Downloads into the selected isolated DSH_HOME.
    public static void importSelectedSkill() {
        AgentInstance instance = requireSelectedInstance();
        if (instance == null) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(i18n("agent.skill.import"));
        @Nullable Path source = Controllers.showDialog(chooser);
        if (source != null) installSelectedSkill(instance, source.toString(),
                () -> AgentSkillService.install(source, AgentRepository.get().getInstanceHome(instance)));
    }

    /// Downloads a public single-file SKILL.md into the selected isolated DSH_HOME.
    public static void downloadSkill() {
        AgentInstance instance = requireSelectedInstance();
        if (instance == null) return;
        Controllers.prompt(i18n("agent.downloads.skill.url"), (value, handler) -> {
            try {
                AgentSkillService.remoteSkillName(URI.create(value == null ? "" : value.trim()));
                handler.resolve();
            } catch (IllegalArgumentException | IOException error) {
                handler.reject(error.getMessage());
            }
        }, "https://raw.githubusercontent.com/<owner>/<repo>/<ref>/<skill>/SKILL.md")
                .thenAccept(url -> installSelectedSkill(instance, url.trim(),
                        () -> AgentSkillService.download(URI.create(url.trim()),
                                AgentRepository.get().getInstanceHome(instance))));
    }

    /// Runs one skill transfer and registers it only after the isolated installation succeeds.
    private static void installSelectedSkill(AgentInstance instance, String source,
                                             java.util.concurrent.Callable<Path> operation) {
        Controllers.showToast(i18n("agent.skill.import.running"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return operation.call();
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(error);
            }
        }).whenComplete((installed, error) -> Platform.runLater(() -> {
            if (error != null) {
                showError(i18n("agent.skill.import.failed"), rootCause(error));
                return;
            }
            AgentRepository repository = AgentRepository.get();
            if (!repository.getInstances().contains(instance)) return;
            String skillName = installed.getFileName().toString();
            String id = "skill-" + java.util.UUID.nameUUIDFromBytes(
                    skillName.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 18);
            AgentExtension entry = repository.getExtensions().stream()
                    .filter(extension -> extension.getId().equals(id)).findFirst().orElse(null);
            if (entry == null) {
                entry = new AgentExtension(id, skillName, "Skill", source, true);
                repository.addImportedExtension(entry);
            }
            repository.setExtensionInstalled(instance, entry, true);
            repository.save();
            Controllers.showToast(i18n("agent.skill.import.success", skillName));
        }));
    }

    /// Requires a selected instance before any download or MCP configuration begins.
    private static @Nullable AgentInstance requireSelectedInstance() {
        AgentInstance instance = AgentRepository.get().getSelectedInstance();
        if (instance == null) Controllers.dialog(i18n("agent.mod.instance.required"),
                i18n("message.error"), MessageDialogPane.MessageType.WARNING);
        return instance;
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
        runModOperation(extension, instance, action, false);
    }

    /// Installs a market result into the instance selected when the action began.
    public static void installMarketPlugin(AgentInstance instance, AgentNetworkService.NpmPlugin plugin,
                                           String version) {
        AgentExtension entry = new AgentExtension("pending-plugin", plugin.name(), "Plugin",
                plugin.name() + "@" + version, false);
        runModOperation(entry, instance, DshModService.Action.INSTALL, true);
    }

    /// Runs one native plugin operation against a captured instance and records successful market installs.
    private static void runModOperation(AgentExtension extension, AgentInstance instance,
                                        DshModService.Action action, boolean marketInstall) {
        AgentRepository repository = AgentRepository.get();
        if (!repository.getInstances().contains(instance)) {
            Controllers.dialog(i18n("agent.mod.instance.required"), i18n("message.error"),
                    MessageDialogPane.MessageType.WARNING);
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
        String registry = repository.getPackageRegistry();
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
                        registry);
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
                if (marketInstall) {
                    String installedName = result.installedPackageName() == null
                            ? DshModService.packageName(spec) : result.installedPackageName();
                    String installedSpec = result.installedPackageName() == null ? spec : installedName;
                    AgentExtension existing = repository.getExtensions().stream()
                            .filter(item -> installedName.equals(DshModService.packageName(item.locationProperty().get())))
                            .findFirst().orElse(null);
                    if (existing == null) {
                        String id = "npm-" + java.util.UUID.nameUUIDFromBytes(
                                installedName.getBytes(StandardCharsets.UTF_8));
                        repository.addImportedExtension(new AgentExtension(id, installedName, "Plugin",
                                installedSpec, false));
                    } else existing.locationProperty().set(installedSpec);
                }
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
                Controllers.dialog(coreInstallFailureDialog(instance, requested, rootCause(error), false));
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
    static void showInstanceHome(AgentInstance instance) {
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
                watchLaunchFailure(instance, repo.launchSelected());
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
                    Controllers.dialog(coreInstallFailureDialog(instance, version, rootCause(failure), true));
                    return;
                }
                if (repo.getSelectedInstance() != instance
                        || !version.equals(instance.coreVersionProperty().get())) return;
                instance.coreVersionProperty().set(runtime.getFileName().toString());
                Controllers.showToast(i18n("agent.core.install.success", runtime.getFileName().toString()));
                try {
                    watchLaunchFailure(instance, repo.launchSelected());
                    showConsole();
                } catch (IOException | RuntimeException error) {
                    showLaunchFailure(instance, error);
                }
            }));
        } catch (IOException | RuntimeException e) {
            showLaunchFailure(instance, e);
        }
    }

    /// Confirms deletion of one DSH instance before removing its isolated home.
    private static void removeInstance(AgentInstance instance) {
        removeInstance(instance, () -> {});
    }

    /// Confirms deletion and runs a view refresh after the repository selects the next instance.
    private static void removeInstance(AgentInstance instance, Runnable after) {
        Controllers.confirm(i18n("agent.instance.delete.confirm", instance.getName()),
                i18n("agent.instance.delete"), () -> {
                    try {
                        AgentRepository.get().removeInstance(instance);
                        after.run();
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

    /// Opens the selected instance's configurable `.dshpack` export flow.
    public static void exportPack(AgentInstance instance) {
        AgentPackService.ExportOptions defaults = AgentPackService.ExportOptions.defaults(instance);
        PromptDialogPane.Builder.StringQuestion name = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.pack.export.name"), defaults.name()).setPromptText(i18n("agent.pack.export.name.prompt"));
        PromptDialogPane.Builder.StringQuestion description = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.pack.export.description"), defaults.description());
        PromptDialogPane.Builder.StringQuestion model = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.pack.export.model"), defaults.model());
        PromptDialogPane.Builder.StringQuestion profile = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.pack.export.profile"), defaults.profileName());
        PromptDialogPane.Builder.StringQuestion template = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.pack.export.template"), defaults.profileTemplate());
        PromptDialogPane.Builder.StringQuestion extensions = new PromptDialogPane.Builder.StringQuestion(
                i18n("agent.pack.export.extensions"), defaults.extensionIds());
        PromptDialogPane.Builder.BooleanQuestion includeCore = new PromptDialogPane.Builder.BooleanQuestion(
                i18n("agent.pack.export.include.core"), defaults.includeCore());
        PromptDialogPane.Builder.BooleanQuestion includeProfile = new PromptDialogPane.Builder.BooleanQuestion(
                i18n("agent.pack.export.include.profile"), defaults.includeProfile());
        PromptDialogPane.Builder.BooleanQuestion includeModel = new PromptDialogPane.Builder.BooleanQuestion(
                i18n("agent.pack.export.include.model"), defaults.includeModel());
        PromptDialogPane.Builder.BooleanQuestion includeExtensions = new PromptDialogPane.Builder.BooleanQuestion(
                i18n("agent.pack.export.include.extensions"), defaults.includeExtensions());
        PromptDialogPane.Builder.BooleanQuestion includeProvider = new PromptDialogPane.Builder.BooleanQuestion(
                i18n("agent.pack.export.include.provider"), defaults.includeProvider());
        PromptDialogPane.Builder builder = new PromptDialogPane.Builder(i18n("agent.pack.export"), (questions, handler) -> handler.resolve())
                .setPrefWidth(760);
        builder.addQuestion(new PromptDialogPane.Builder.HintQuestion(i18n("agent.pack.export.hint")))
                .addQuestion(name).addQuestion(description).addQuestion(includeCore).addQuestion(includeProfile)
                .addQuestion(profile).addQuestion(template).addQuestion(includeModel).addQuestion(model)
                .addQuestion(includeExtensions).addQuestion(extensions).addQuestion(includeProvider)
                .addQuestion(new PromptDialogPane.Builder.HintQuestion(i18n("agent.pack.export.api.never")));
        Controllers.prompt(builder).thenAccept(ignored -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n("agent.pack.export"));
            chooser.setInitialFileName(safeFileName(name.getValue()) + ".dshpack");
            chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("agent.pack.file"), "*.dshpack", "*.json"));
            @Nullable Path selected = Controllers.showSaveDialog(chooser);
            if (selected == null) return;
            Path output = ensureExtension(selected, ".dshpack");
            AgentPackService.ExportOptions options = new AgentPackService.ExportOptions(
                    name.getValue(), description.getValue(), instance.coreVersionProperty().get(),
                    profile.getValue(), template.getValue(), instance.webPortProperty().get(), model.getValue(),
                    extensions.getValue(), Boolean.TRUE.equals(includeCore.getValue()),
                    Boolean.TRUE.equals(includeProfile.getValue()), Boolean.TRUE.equals(includeModel.getValue()),
                    Boolean.TRUE.equals(includeExtensions.getValue()), Boolean.TRUE.equals(includeProvider.getValue()));
            try {
                AgentPackService.exportInstance(AgentRepository.get(), instance, output, options);
                Controllers.dialog(i18n("agent.pack.export.success", output), i18n("message.success"), MessageDialogPane.MessageType.SUCCESS);
            } catch (IOException | RuntimeException e) {
                showError(i18n("agent.pack.export.failed"), e);
            }
        });
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

    /// Watches a started DSH process and opens the support dialog for a non-zero early exit.
    private static void watchLaunchFailure(AgentInstance instance, Process process) {
        process.onExit().thenRun(() -> {
            if (process.exitValue() != 0) {
                Platform.runLater(() -> {
                    if (!AgentRepository.get().wasStopRequested(process)) {
                        showLaunchFailure(instance,
                                new IOException("DSH exited with code " + process.exitValue()));
                    }
                });
            }
        });
    }

    /// Displays an HMCL-style actionable launch report with a sanitized copy/export path.
    private static void showLaunchFailure(AgentInstance instance, Throwable throwable) {
        AgentRepository repository = AgentRepository.get();
        String report = launchFailureReport(instance, throwable, repository.getConsoleLines());
        MessageDialogPane dialog = new MessageDialogPane(
                StringUtils.escapeXmlAttribute(report), i18n("agent.launch.failed"),
                MessageDialogPane.MessageType.ERROR);
        com.jfoenix.controls.JFXButton copy = new com.jfoenix.controls.JFXButton(i18n("agent.launch.failure.copy"));
        copy.setOnAction(event -> FXUtils.copyText(report));
        dialog.addButton(copy);
        com.jfoenix.controls.JFXButton export = new com.jfoenix.controls.JFXButton(i18n("agent.launch.failure.export"));
        export.setOnAction(event -> exportLaunchFailure(report, instance));
        dialog.addButton(export);
        com.jfoenix.controls.JFXButton console = new com.jfoenix.controls.JFXButton(i18n("agent.launch.failure.console"));
        console.setOnAction(event -> showConsole());
        dialog.addButton(console);
        dialog.addButton(new com.jfoenix.controls.JFXButton(i18n("button.ok")));
        Controllers.dialog(dialog);
    }

    /// Builds a support report from the exception and already-redacted DSH output.
    static String launchFailureReport(AgentInstance instance, Throwable throwable, List<String> consoleLines) {
        StringBuilder report = new StringBuilder();
        report.append(i18n("agent.launch.failure.warning")).append("\n\n")
                .append("instance=").append(instance.getId()).append("\n")
                .append("name=").append(instance.getName()).append("\n")
                .append("coreVersion=").append(instance.coreVersionProperty().get()).append("\n")
                .append("profile=").append(instance.profileNameProperty().get()).append("\n")
                .append("reason=\n").append(StringUtils.getStackTrace(throwable)).append("\n")
                .append("console=\n");
        consoleLines.forEach(line -> report.append(line).append('\n'));
        return report.toString();
    }

    /// Writes the same support report to a user-selected text file.
    private static void exportLaunchFailure(String report, AgentInstance instance) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("agent.launch.failure.export"));
        chooser.setInitialFileName(safeFileName(instance.getName()) + "-dsh-launch-failure.log");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("agent.diagnostics.file"), "*.log", "*.txt"));
        @Nullable Path selected = Controllers.showSaveDialog(chooser);
        if (selected == null) return;
        Path output = ensureExtension(selected, ".log");
        try {
            Files.writeString(output, report, StandardCharsets.UTF_8);
            Controllers.showToast(i18n("agent.launch.failure.exported", output));
        } catch (IOException error) {
            showError(i18n("agent.launch.failure.export"), error);
        }
    }

    /// Offers recovery for a failed Core operation without displaying a stack trace by default.
    static MessageDialogPane coreInstallFailureDialog(AgentInstance instance, String requested,
                                                      Throwable failure, boolean launchAfter) {
        @Nullable String causeMessage = rootCause(failure).getMessage();
        String reason = causeMessage == null || causeMessage.isBlank() ? failure.getClass().getSimpleName() : causeMessage;
        if (reason.length() > 600) reason = reason.substring(0, 600) + "…";
        String message = i18n("agent.core.failure.help") + "\n\n"
                + i18n("agent.core.failure.source", AgentRepository.get().getPackageRegistry()) + "\n\n" + reason;
        return new MessageDialogPane.Builder(StringUtils.escapeXmlAttribute(message),
                i18n("agent.core.install.failed"), MessageDialogPane.MessageType.ERROR)
                .addAction(i18n("agent.core.failure.retry"), () -> Platform.runLater(() -> {
                    @Nullable AgentInstance current = AgentRepository.get().findInstance(instance.getId());
                    if (current == null) return;
                    if (launchAfter) launch(current);
                    else installCore(current, requested);
                }))
                .addAction(i18n("agent.core.failure.settings"), () -> Platform.runLater(() -> {
                    AgentSettingsPage page = settings();
                    page.showGeneral();
                    Controllers.navigate(page);
                }))
                .addAction(i18n("agent.core.failure.details"), () -> Platform.runLater(() ->
                        showError(i18n("agent.core.install.failed"), failure)))
                .ok(null).build();
    }

    /// Displays explicitly requested or otherwise unhandled exception details in an HMCL dialog.
    private static void showError(String title, Throwable throwable) {
        Controllers.dialog(
                StringUtils.escapeXmlAttribute(title + "\n\n" + StringUtils.getStackTrace(throwable)),
                i18n("message.error"),
                MessageDialogPane.MessageType.ERROR);
    }
}
