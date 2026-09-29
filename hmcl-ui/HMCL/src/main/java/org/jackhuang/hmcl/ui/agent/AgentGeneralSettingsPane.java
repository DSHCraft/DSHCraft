/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020 huangyuhui <huanghongxun2008@126.com> and contributors
 * Modifications for DShCraft direct HMCL fork Copyright (C) 2026 DShCraft contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.agent.AgentNetworkService;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.LineTextPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Direct Agent-content adaptation of HMCL SettingsPage's ScrollPane/VBox/ComponentList skeleton.
@NotNullByDefault
public final class AgentGeneralSettingsPane extends ScrollPane {
    /// Creates general Agent settings using HMCL SettingsPage spacing, padding and scrolling behavior.
    public AgentGeneralSettingsPane() {
        AgentRepository repository = AgentRepository.get();
        setFitToWidth(true);

        VBox rootPane = new VBox(10);
        rootPane.setPadding(new Insets(10));
        setContent(rootPane);
        FXUtils.smoothScrolling(this);

        ComponentList profiles = new ComponentList();

        LineButton providers = LineButton.createNavigationButton();
        providers.setTitle(i18n("agent.providers"));
        providers.setSubtitle(i18n("agent.providers.subtitle"));
        providers.trailingTextProperty().bind(Bindings.size(repository.getProviders()).asString());
        providers.setLeading(SVG.PERSON);
        providers.setOnAction(event -> Controllers.navigate(AgentPages.providers()));

        LineButton instances = LineButton.createNavigationButton();
        instances.setTitle(i18n("agent.instances"));
        instances.setSubtitle(i18n("agent.instances.subtitle"));
        instances.trailingTextProperty().bind(Bindings.size(repository.getInstances()).asString());
        instances.setLeading(SVG.FORMAT_LIST_BULLETED);
        instances.setOnAction(event -> Controllers.navigate(AgentPages.instances()));

        LineButton extensions = LineButton.createNavigationButton();
        extensions.setTitle(i18n("agent.downloads"));
        extensions.setSubtitle(i18n("agent.extensions.subtitle"));
        extensions.trailingTextProperty().bind(Bindings.size(repository.getExtensions()).asString());
        extensions.setLeading(SVG.EXTENSION);
        extensions.setOnAction(event -> Controllers.navigate(AgentPages.downloads()));

        profiles.getContent().setAll(providers, instances, extensions);
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("agent.settings.profiles")),
                profiles);

        ComponentList dataTools = new ComponentList();
        LineButton importPack = actionRow(i18n("agent.pack.import"), i18n("agent.pack.import.subtitle"), SVG.PACKAGE2, AgentPages::importPack);
        LineButton exportBackup = actionRow(i18n("agent.backup.export"), i18n("agent.backup.export.subtitle"), SVG.OUTPUT, AgentPages::exportBackup);
        LineButton importBackup = actionRow(i18n("agent.backup.import"), i18n("agent.backup.import.subtitle"), SVG.DOWNLOAD, AgentPages::importBackup);
        dataTools.getContent().setAll(importPack, exportBackup, importBackup);
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("agent.settings.portability")),
                dataTools);

        ComponentList networkTools = new ComponentList();
        LineButton catalogRegistry = registryRow(i18n("agent.download.catalog.source"), true, repository);
        LineButton packageRegistry = registryRow(i18n("agent.download.package.source"), false, repository);
        LineButton coreCatalog = actionRow(i18n("agent.core.catalog"), i18n("agent.core.catalog.subtitle"), SVG.REFRESH,
                () -> Controllers.navigate(AgentPages.downloads()));
        LineButton extensionCatalog = actionRow(i18n("agent.extension.catalog"), i18n("agent.extension.catalog.subtitle"), SVG.EXTENSION, AgentPages::importExtensionCatalog);
        LineButton diagnostics = actionRow(i18n("agent.diagnostics.export"), i18n("agent.diagnostics.export.subtitle"), SVG.INFO, AgentPages::exportDiagnostics);
        networkTools.getContent().setAll(catalogRegistry, packageRegistry, coreCatalog, extensionCatalog, diagnostics);
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("agent.settings.tools")),
                networkTools);

        ComponentList storage = new ComponentList();
        LineTextPane config = new LineTextPane();
        config.setTitle(i18n("agent.config"));
        config.setSubtitle(i18n("agent.config.subtitle"));
        config.setText(repository.getConfigFile().toString());
        config.setLeading(SVG.SCRIPT);
        storage.getContent().add(config);

        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("agent.settings.storage")),
                storage);
    }

    /// Builds a standard HMCL action row without introducing any parallel CSS or custom widget implementation.
    private static LineButton actionRow(String title, String subtitle, SVG icon, Runnable action) {
        LineButton row = new LineButton();
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.setLeading(icon);
        row.setOnAction(event -> action.run());
        return row;
    }

    /// Creates an HMCL-native registry selection row for catalog queries or package downloads.
    private static LineButton registryRow(String title, boolean catalog, AgentRepository repository) {
        LineButton row = new LineButton();
        row.setTitle(title);
        row.setSubtitle(registryLabel(catalog ? repository.getCatalogRegistry() : repository.getPackageRegistry()));
        row.setLeading(SVG.DOWNLOAD);
        row.setOnAction(event -> {
            String[] ids = {"official", "npmmirror", "huawei", "tencent", "custom"};
            String[] labels = {"npm (Official)", "npmmirror", "Huawei Cloud npm", "Tencent npm", i18n("agent.download.source.custom")};
            String current = catalog ? repository.getCatalogRegistry() : repository.getPackageRegistry();
            int selected = current.equals("https://registry.npmmirror.com") ? 1
                    : current.equals("https://repo.huaweicloud.com/repository/npm") ? 2
                    : current.equals("https://mirrors.cloud.tencent.com/npm") ? 3
                    : current.equals(AgentNetworkService.OFFICIAL_REGISTRY) ? 0 : 4;
            PromptDialogPane.Builder.CandidatesQuestion choice =
                    new PromptDialogPane.Builder.CandidatesQuestion(i18n("agent.download.source.choose"), labels)
                            .setSelectedIndex(selected);
            Controllers.prompt(new PromptDialogPane.Builder(i18n("agent.download.source.title"),
                    (questions, callback) -> callback.resolve()).addQuestion(choice))
                    .thenAccept(questions -> {
                        Integer index = choice.getValue();
                        if (index == null) index = selected;
                        String source = ids[index];
                        if ("custom".equals(source)) {
                            String initial = selected == 4 ? current : "";
                            Controllers.prompt(i18n("agent.download.source.custom"), (url, callback) -> {
                                try {
                                    AgentNetworkService.validateRegistry("custom", url);
                                    callback.resolve();
                                } catch (Exception error) {
                                    callback.reject(error.getMessage());
                                }
                            }, initial).thenAccept(url -> saveRegistry(catalog, source, url, repository, row));
                        } else {
                            saveRegistry(catalog, source, "", repository, row);
                        }
                    });
        });
        return row;
    }

    /// Validates and persists a selected registry, then refreshes its settings-row label.
    private static void saveRegistry(boolean catalog, String source, String custom,
                                     AgentRepository repository, LineButton row) {
        try {
            repository.setDownloadRegistry(catalog, source, custom);
            row.setSubtitle(registryLabel(catalog ? repository.getCatalogRegistry() : repository.getPackageRegistry()));
        } catch (Exception error) {
            LOG.warning("Failed to save DSHCraft npm registry", error);
            Controllers.dialog(error.getMessage(), i18n("message.error"), MessageDialogPane.MessageType.WARNING);
        }
    }

    /// Formats known registry URLs as concise labels while showing a custom URL verbatim.
    private static String registryLabel(String registry) {
        if (registry.equals(AgentNetworkService.OFFICIAL_REGISTRY)) return "npm (Official)";
        if (registry.equals("https://registry.npmmirror.com")) return "npmmirror";
        if (registry.equals("https://repo.huaweicloud.com/repository/npm")) return "Huawei Cloud npm";
        if (registry.equals("https://mirrors.cloud.tencent.com/npm")) return "Tencent npm";
        return registry;
    }
}
