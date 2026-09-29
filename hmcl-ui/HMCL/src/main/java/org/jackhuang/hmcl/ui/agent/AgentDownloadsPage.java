/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.FilteredList;
import org.jackhuang.hmcl.agent.AgentExtension;
import org.jackhuang.hmcl.agent.AgentInstance;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// One HMCL Downloads shell for DSH Core, Packs, Plugins, MCP, Skills and Tools.
@NotNullByDefault
public final class AgentDownloadsPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    /// Decorator title displayed above the shared Downloads shell.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("agent.downloads")));
    /// HMCL transition host for category content.
    private final TransitionPane transitionPane = new TransitionPane();
    /// DSH Core npm version list tab.
    private final TabHeader.Tab<AgentCoreDownloadPage> coreTab = new TabHeader.Tab<>("dshCoreDownloads");
    /// Portable Pack import/export tab.
    private final TabHeader.Tab<AgentEditorPage> packTab = new TabHeader.Tab<>("dshPackDownloads");
    /// Package-backed Plugin and Bundle download tab.
    private final TabHeader.Tab<AgentPluginMarketPage> pluginTab = new TabHeader.Tab<>("dshPluginDownloads");
    /// MCP metadata and configuration tab.
    private final TabHeader.Tab<AgentListPage<AgentExtension>> mcpTab = new TabHeader.Tab<>("dshMcpDownloads");
    /// Skill metadata and configuration tab.
    private final TabHeader.Tab<AgentListPage<AgentExtension>> skillTab = new TabHeader.Tab<>("dshSkillDownloads");
    /// Tool metadata and configuration tab.
    private final TabHeader.Tab<AgentListPage<AgentExtension>> toolTab = new TabHeader.Tab<>("dshToolDownloads");
    /// Upstream HMCL navigation controller for the content tabs.
    private final TabHeader tab;

    /// Builds the same category/transition skeleton as HMCL's native Downloads page.
    public AgentDownloadsPage() {
        coreTab.setNodeSupplier(AgentCoreDownloadPage::new);
        packTab.setNodeSupplier(this::packContent);
        pluginTab.setNodeSupplier(AgentPluginMarketPage::new);
        mcpTab.setNodeSupplier(() -> extensionContent("MCP", i18n("agent.downloads.mcp")));
        skillTab.setNodeSupplier(() -> extensionContent("Skill", i18n("agent.downloads.skills")));
        toolTab.setNodeSupplier(() -> extensionContent("Tool", i18n("agent.downloads.tools")));
        tab = new TabHeader(transitionPane, coreTab, packTab, pluginTab, mcpTab, skillTab, toolTab);
        tab.select(coreTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("agent.downloads.core.group").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, coreTab, i18n("agent.downloads.core"), SVG.DOWNLOAD)
                .addNavigationDrawerTab(tab, packTab, i18n("agent.downloads.packs"), SVG.PACKAGE2)
                .startCategory(i18n("agent.downloads.mods.group").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, pluginTab, i18n("agent.downloads.plugins"), SVG.EXTENSION)
                .addNavigationDrawerTab(tab, mcpTab, i18n("agent.downloads.mcp"), SVG.PUBLIC)
                .addNavigationDrawerTab(tab, skillTab, i18n("agent.downloads.skills"), SVG.SCRIPT)
                .addNavigationDrawerTab(tab, toolTab, i18n("agent.downloads.tools"), SVG.TUNE);
        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);
        setCenter(transitionPane);
    }

    /// Selects the Plugin/Bundle category when navigating from an instance's Mod action.
    public void showPlugins() {
        tab.select(pluginTab, false);
    }

    /// Selects the online DSH Core version catalog.
    public void showCore() {
        tab.select(coreTab, false);
    }

    /// Creates HMCL-style Pack actions for the selected DSH instance.
    private AgentEditorPage packContent() {
        AgentEditorPage page = new AgentEditorPage(i18n("agent.downloads.packs"));
        page.addAction(i18n("agent.pack.import"), i18n("agent.pack.import.subtitle"), SVG.DOWNLOAD,
                AgentPages::importPack);
        AgentInstance selected = AgentRepository.get().getSelectedInstance();
        if (selected != null) {
            page.addAction(i18n("agent.pack.export"), i18n("agent.pack.export.subtitle"), SVG.OUTPUT,
                    () -> AgentPages.exportPack(selected));
        }
        return page;
    }

    /// Filters one category while retaining the shared HMCL search and action list controls.
    private AgentListPage<AgentExtension> extensionContent(String category, String title) {
        AgentRepository repository = AgentRepository.get();
        AgentInstance selected = repository.getSelectedInstance();
        if (selected != null) {
            try {
                repository.refreshInstalledMods(selected);
            } catch (IOException ignored) {
                // The list remains usable; the selected Profile may not exist yet.
            }
        }
        FilteredList<AgentExtension> items = new FilteredList<>(repository.getExtensions(), extension ->
                category.equalsIgnoreCase(extension.typeProperty().get()));
        return new AgentListPage<>(title, items,
                () -> repository.addExtension(category), repository::load, AgentPages::openExtension,
                AgentPages::openExtension,
                AgentPages::removeModEntry,
                null,
                extension -> Set.of("filesystem", "browser", "skills", "mcp-client").contains(extension.getId())
                        || repository.getSelectedInstance() != null
                        && repository.hasExtension(repository.getSelectedInstance(), extension),
                repository.selectedInstanceProperty(), i18n("agent.extension.add"),
                i18n("agent.extension.toggle"), false, true);
    }

    /// Returns the HMCL decorator title.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /// Forwards show events to the native HMCL tab controller.
    @Override
    public void onPageShown() {
        tab.onPageShown();
    }

    /// Forwards hide events to the native HMCL tab controller.
    @Override
    public void onPageHidden() {
        tab.onPageHidden();
    }
}
