/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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

import java.util.Locale;

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
    /// MCP server add actions.
    private final TabHeader.Tab<AgentEditorPage> mcpTab = new TabHeader.Tab<>("dshMcpDownloads");
    /// Skill download and import actions.
    private final TabHeader.Tab<AgentEditorPage> skillTab = new TabHeader.Tab<>("dshSkillDownloads");
    /// npm tool plugin download catalog.
    private final TabHeader.Tab<AgentPluginMarketPage> toolTab = new TabHeader.Tab<>("dshToolDownloads");
    /// Upstream HMCL navigation controller for the content tabs.
    private final TabHeader tab;

    /// Builds the same category/transition skeleton as HMCL's native Downloads page.
    public AgentDownloadsPage() {
        coreTab.setNodeSupplier(AgentCoreDownloadPage::new);
        packTab.setNodeSupplier(this::packContent);
        pluginTab.setNodeSupplier(AgentPluginMarketPage::new);
        mcpTab.setNodeSupplier(this::mcpContent);
        skillTab.setNodeSupplier(this::skillContent);
        toolTab.setNodeSupplier(() -> new AgentPluginMarketPage("dsh tool"));
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

    /// Selects npm packages providing DSH tools.
    public void showTools() {
        tab.select(toolTab, false);
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

    /// Adds MCP endpoints to the current isolated instance.
    private AgentEditorPage mcpContent() {
        return new AgentEditorPage(i18n("agent.downloads.mcp"))
                .addAction(i18n("agent.downloads.mcp.http"), i18n("agent.downloads.mcp.http.subtitle"),
                        SVG.PUBLIC, AgentPages::addHttpMcp)
                .addAction(i18n("agent.downloads.mcp.stdio"), i18n("agent.downloads.mcp.stdio.subtitle"),
                        SVG.ADD_CIRCLE, AgentPages::addStdioMcp)
                .addAction(i18n("agent.downloads.installed"), i18n("agent.downloads.manage.subtitle"),
                        SVG.EXTENSION, () -> AgentPages.openInstanceCategory("MCP"));
    }

    /// Downloads single-file skills or imports a local skill bundle into the current instance.
    private AgentEditorPage skillContent() {
        return new AgentEditorPage(i18n("agent.downloads.skills"))
                .addAction(i18n("agent.downloads.skill.url"), i18n("agent.downloads.skill.url.subtitle"),
                        SVG.DOWNLOAD, AgentPages::downloadSkill)
                .addAction(i18n("agent.skill.import"), i18n("agent.skill.import.subtitle"),
                        SVG.FOLDER_OPEN, AgentPages::importSelectedSkill)
                .addAction(i18n("agent.downloads.installed"), i18n("agent.downloads.manage.subtitle"),
                        SVG.EXTENSION, () -> AgentPages.openInstanceCategory("SKILLS"));
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
