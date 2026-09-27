/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXPopup;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.directory.GameDirectoryListItem;
import org.jackhuang.hmcl.ui.directory.GameDirectoryPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("instance.manage")));
    /// Navigation drawer items for configured game directories.
    @SuppressWarnings("FieldCanBeLocal")
    private final ObservableList<GameDirectoryListItem> gameDirectoryListItems;

    public GameListPage() {
        gameDirectoryListItems = MappedObservableList.create(GameDirectoryManager.getGameDirectories(), gameDirectory -> {
            GameDirectoryListItem item = new GameDirectoryListItem(gameDirectory);
            FXUtils.setLimitWidth(item, 200);
            FXUtils.onSecondaryButtonClicked(item, () -> {
                PopupMenu menu = new PopupMenu();
                JFXPopup popup = new JFXPopup(menu);
                menu.getContent().add(new IconedMenuItem(
                        SVG.EDIT,
                        i18n("button.edit"),
                        () -> Controllers.navigate(new GameDirectoryPage(gameDirectory)),
                        popup));
                popup.show(item, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, item.getWidth(), 0);
            });
            return item;
        });

        {
            ScrollPane pane = new ScrollPane();
            VBox.setVgrow(pane, Priority.ALWAYS);
            {
                AdvancedListItem addGameDirectoryItem = new AdvancedListItem();
                addGameDirectoryItem.getStyleClass().add("navigation-drawer-item");
                addGameDirectoryItem.setTitle(i18n("game_directory.new"));
                addGameDirectoryItem.setLeftIcon(SVG.ADD_CIRCLE);
                addGameDirectoryItem.setOnAction(e -> Controllers.navigate(new GameDirectoryPage(null)));

                pane.setFitToWidth(true);
                VBox wrapper = new VBox();
                wrapper.getStyleClass().add("advanced-list-box-content");
                VBox box = new VBox();
                box.setFillWidth(true);
                Bindings.bindContent(box.getChildren(), gameDirectoryListItems);
                wrapper.getChildren().setAll(box, addGameDirectoryItem);
                pane.setContent(wrapper);
            }
            FXUtils.smoothScrolling(pane);

            AdvancedListBox bottomLeftCornerList = new AdvancedListBox()
                    .addNavigationDrawerItem(i18n("settings.type.global.manage"), SVG.SETTINGS, this::modifyGlobalGameSettings);
            FXUtils.setLimitHeight(bottomLeftCornerList, 40 + 12 * 2);
            setLeft(pane, bottomLeftCornerList);
        }

        setCenter(new GameList());

        FXUtils.applyDragListener(this, file -> ModpackHelper.isFileModpackByExtension(file) || "json".equalsIgnoreCase(FileUtils.getNameWithoutExtension(file)), files -> {
            Path file = files.get(0);

            if (ModpackHelper.isFileModpackByExtension(file)) {
                Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(GameDirectoryManager.getSelectedRepository(), file), i18n("install.modpack"));
            } else if ("json".equalsIgnoreCase(FileUtils.getExtension(file))) {
                Instances.installFromJson(GameDirectoryManager.getSelectedRepository(), file);
            }
        });
    }

    public void modifyGlobalGameSettings() {
        Instances.modifyGlobalSettings(GameDirectoryManager.getSelectedRepository());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /// Minecraft business adapter over the shared physical HMCL searchable-list implementation.
    private static class GameList extends SearchableListPage<GameListItem> {
        /// Weak listeners associated with the currently selected game repository.
        private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

        /// Creates the Minecraft adapter while preserving the original refresh/failure behavior.
        public GameList() {
            super(FXCollections.observableArrayList());
            GameDirectoryManager.registerVersionsListener(this::loadVersions);
            setOnFailedAction(e -> Instances.addNewGame());
        }

        /// Replaces visible Minecraft entries when the selected game repository changes.
        @FXThread
        private void loadVersions(HMCLGameRepository repository) {
            listenerHolder.clear();
            setLoading(true);
            setFailedReason(null);

            List<GameListItem> instanceItems = repository.getDisplayInstances()
                    .map(GameListItem::new)
                    .toList();

            sourceItems().setAll(instanceItems);
            if (instanceItems.isEmpty()) {
                setFailedReason(i18n("instance.empty.hint"));
            }
            setLoading(false);
        }

        /// Recreates HMCL's original substring/regex instance search semantics.
        @Override
        protected Predicate<GameListItem> createSearchPredicate(String searchText) {
            if (searchText == null || searchText.isEmpty()) {
                return item -> true;
            }
            if (searchText.startsWith("regex:")) {
                String regex = searchText.substring("regex:".length());
                try {
                    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    return item -> pattern.matcher(item.getId()).find();
                } catch (PatternSyntaxException e) {
                    return item -> false;
                }
            }
            return item -> item.getId().toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT));
        }

        /// Refreshes the selected Minecraft repository.
        @Override
        protected void refreshList() {
            GameDirectoryManager.getSelectedRepository().refreshAsync().start();
        }

        /// Supplies the original HMCL refresh/new-game/modpack toolbar actions.
        @Override
        protected List<ToolbarAction> toolbarActions() {
            return List.of(
                    new ToolbarAction(i18n("button.refresh"), SVG.REFRESH, this::refreshList),
                    new ToolbarAction(i18n("install.new_game"), SVG.DOWNLOAD, Instances::addNewGame),
                    new ToolbarAction(i18n("install.modpack"), SVG.PACKAGE2, Instances::importModpack));
        }

        /// Creates the original HMCL Minecraft row cell.
        @Override
        protected ListCell<GameListItem> createListCell() {
            return new GameListCell();
        }
    }
}
