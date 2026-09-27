/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.property.ReadOnlyStringProperty;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.TwoLineActionListCell;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.MenuSeparator;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Minecraft business adapter over the shared physical HMCL `GameListCell` visual shell.
@NotNullByDefault
public final class GameListCell extends TwoLineActionListCell<GameListItem> {
    /// Update action retained from the original HMCL row.
    private final JFXButton upgradeButton;
    /// Test-launch action retained from the original HMCL row.
    private final JFXButton launchButton;

    /// Creates the original HMCL Minecraft row actions on top of the shared row shell.
    public GameListCell() {
        upgradeButton = createActionButton(SVG.UPDATE, i18n("instance.update"), GameListItem::update);
        launchButton = createActionButton(SVG.ROCKET_LAUNCH, i18n("instance.launch.test"), GameListItem::testGame);
    }

    /// Selects the underlying Minecraft instance.
    @Override
    protected void selectItem(GameListItem item) {
        item.getRepository().setSelectedInstance(item.getGameInstance());
    }

    /// Opens Minecraft instance settings on the row's primary click.
    @Override
    protected void openItem(GameListItem item) {
        item.modifyGameSettings();
    }

    /// Recreates the original HMCL Minecraft manage popup unchanged.
    @Override
    protected JFXPopup createPopup(GameListItem item) {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);
        menu.getContent().setAll(
                new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch.test"), item::testGame, popup),
                new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), item::generateLaunchScript, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.SETTINGS, i18n("instance.manage.manage"), item::modifyGameSettings, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.EDIT, i18n("instance.manage.rename"), item::rename, popup),
                new IconedMenuItem(SVG.FOLDER_COPY, i18n("instance.manage.duplicate"), item::duplicate, popup),
                new IconedMenuItem(SVG.DELETE, i18n("instance.manage.remove"), item::remove, popup),
                new IconedMenuItem(SVG.OUTPUT, i18n("modpack.export"), item::export, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.FOLDER_OPEN, i18n("folder.game"), item::browse, popup));
        return popup;
    }

    /// Binds the original row title.
    @Override
    protected ReadOnlyStringProperty titleProperty(GameListItem item) {
        return item.titleProperty();
    }

    /// Binds the original row subtitle.
    @Override
    protected ReadOnlyStringProperty subtitleProperty(GameListItem item) {
        return item.subtitleProperty();
    }

    /// Binds the original row tag.
    @Override
    protected ReadOnlyStringProperty tagProperty(GameListItem item) {
        return item.tagProperty();
    }

    /// Binds the original Minecraft selection property.
    @Override
    protected void bindSelection(GameListItem item) {
        selectionButton.selectedProperty().bind(item.selectedProperty());
    }

    /// Binds the original Minecraft instance icon.
    @Override
    protected void bindImage(GameListItem item) {
        imageView.imageProperty().bind(item.imageProperty());
    }

    /// Adds the original conditional update action and test-launch action before the shared manage button.
    @Override
    protected void populateRightActions(GameListItem item) {
        if (item.canUpdate()) {
            rightActions.getChildren().add(upgradeButton);
        }
        rightActions.getChildren().add(launchButton);
    }
}
