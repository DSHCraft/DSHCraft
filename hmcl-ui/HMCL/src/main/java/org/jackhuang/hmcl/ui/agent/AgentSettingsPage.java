/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021 huangyuhui <huanghongxun2008@126.com> and contributors
 * Modifications for DShCraft direct HMCL fork Copyright (C) 2026 DShCraft contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TabControl;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.main.PersonalizationPage;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * HMCL LauncherSettingsPage shell reused for Agent settings.
 * The appearance tab is upstream PersonalizationPage without a reimplementation.
 */
@NotNullByDefault
public final class AgentSettingsPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("settings")));
    private final TransitionPane transitionPane = new TransitionPane();
    private final TabControl.Tab<AgentGeneralSettingsPane> generalTab = new TabControl.Tab<>("agentGeneral");
    private final TabControl.Tab<PersonalizationPage> appearanceTab = new TabControl.Tab<>("personalizationPage");
    private final TabControl.Tab<AgentAboutPane> aboutTab = new TabControl.Tab<>("agentAbout");
    private final TabHeader tab;

    public AgentSettingsPage() {
        generalTab.setNodeSupplier(AgentGeneralSettingsPane::new);
        appearanceTab.setNodeSupplier(PersonalizationPage::new);
        aboutTab.setNodeSupplier(AgentAboutPane::new);
        tab = new TabHeader(transitionPane, generalTab, appearanceTab, aboutTab);
        tab.select(generalTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("agent").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, generalTab, i18n("settings.launcher.general"), SVG.TUNE)
                .startCategory(i18n("launcher").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, appearanceTab, i18n("settings.launcher.appearance"), SVG.STYLE, SVG.STYLE_FILL)
                .startCategory(i18n("help").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, aboutTab, i18n("about"), SVG.INFO, SVG.INFO_FILL);
        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);
        setCenter(transitionPane);
    }

    public void showGeneral() {
        tab.select(generalTab, false);
    }

    public void showAppearance() {
        tab.select(appearanceTab, false);
    }

    public void showAbout() {
        tab.select(aboutTab, false);
    }

    @Override
    public void onPageShown() {
        tab.onPageShown();
    }

    @Override
    public void onPageHidden() {
        tab.onPageHidden();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
