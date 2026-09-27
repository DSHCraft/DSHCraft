/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXPopup;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.terracotta.TerracottaMetadata;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.agent.AgentAdvancedListItem;
import org.jackhuang.hmcl.ui.agent.AgentListPopupMenu;
import org.jackhuang.hmcl.ui.agent.AgentPages;
import org.jackhuang.hmcl.ui.agent.ProviderAdvancedListItem;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.account.AccountListPopupMenu;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.instances.GameAdvancedListItem;
import org.jackhuang.hmcl.ui.instances.GameListPopupMenu;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.ui.nbt.NBTEditorPage;
import org.jackhuang.hmcl.ui.nbt.NBTFileType;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class RootPage extends DecoratorAnimatedPage implements DecoratorPage {
    private MainPage mainPage = null;

    public RootPage() {
        getStyleClass().remove("gray-background");
        getLeft().getStyleClass().add("gray-background");
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return getMainPage().stateProperty();
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    public MainPage getMainPage() {
        if (mainPage == null) {
            mainPage = new MainPage();
        }
        return mainPage;
    }

    private static class Skin extends DecoratorAnimatedPageSkin<RootPage> {

        protected Skin(RootPage control) {
            super(control);

            AgentRepository repo = AgentRepository.get();

            // Direct adaptation of HMCL's first AccountAdvancedListItem identity row.
            ProviderAdvancedListItem providerItem = new ProviderAdvancedListItem();
            providerItem.setOnAction(e -> Controllers.navigate(AgentPages.providers()));

            // Direct adaptation of HMCL's second GameAdvancedListItem identity row.
            AgentAdvancedListItem currentInstanceItem = new AgentAdvancedListItem();
            currentInstanceItem.setOnAction(e -> {
                if (repo.getSelectedInstance() == null) {
                    Controllers.navigate(AgentPages.instances());
                } else {
                    AgentPages.openInstance(repo.getSelectedInstance());
                }
            });
            FXUtils.onScroll(currentInstanceItem, repo.getInstances(),
                    list -> Math.max(0, list.indexOf(repo.getSelectedInstance())),
                    repo::setSelectedInstance);
            FXUtils.onSecondaryButtonClicked(currentInstanceItem, () -> AgentListPopupMenu.show(
                    currentInstanceItem,
                    JFXPopup.PopupVPosition.TOP,
                    JFXPopup.PopupHPosition.LEFT,
                    currentInstanceItem.getWidth(),
                    0,
                    repo.getInstances()));

            AdvancedListItem instancesItem = new AdvancedListItem();
            instancesItem.setLeftIcon(SVG.FORMAT_LIST_BULLETED);
            instancesItem.setTitle(i18n("agent.instances"));
            instancesItem.setOnAction(e -> Controllers.navigate(AgentPages.instances()));

            AdvancedListItem downloadsItem = new AdvancedListItem();
            downloadsItem.setLeftIcon(SVG.DOWNLOAD);
            downloadsItem.setTitle(i18n("agent.downloads"));
            downloadsItem.setOnAction(e -> Controllers.navigate(AgentPages.downloads()));

            AdvancedListItem launcherSettingsItem = new AdvancedListItem();
            launcherSettingsItem.setLeftIcon(SVG.SETTINGS);
            launcherSettingsItem.setTitle(i18n("settings"));
            launcherSettingsItem.setOnAction(e -> {
                AgentPages.settings().showGeneral();
                Controllers.navigate(AgentPages.settings());
            });

            AdvancedListItem configItem = new AdvancedListItem();
            configItem.setLeftIcon(SVG.SCRIPT);
            configItem.setTitle(i18n("agent.config"));
            configItem.setSubtitle(repo.getConfigFile().toString());
            configItem.setOnAction(e -> FXUtils.showFileInExplorer(repo.getConfigFile()));

            AdvancedListBox sideBar = new AdvancedListBox()
                    .startCategory(i18n("agent.provider").toUpperCase(Locale.ROOT))
                    .add(providerItem)
                    .startCategory(i18n("agent.instance").toUpperCase(Locale.ROOT))
                    .add(currentInstanceItem)
                    .add(instancesItem)
                    .add(downloadsItem)
                    .startCategory(i18n("settings.launcher.general").toUpperCase(Locale.ROOT))
                    .add(launcherSettingsItem)
                    .add(configItem)
                    .addNavigationDrawerItem(i18n("about"), SVG.INFO, () -> {
                        AgentPages.settings().showAbout();
                        Controllers.navigate(AgentPages.settings());
                    });

            setLeft(sideBar);
            setCenter(getSkinnable().getMainPage());
        }
    }
}
