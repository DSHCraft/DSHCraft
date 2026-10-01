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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.agent.AgentEntry;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.SearchableListPage;
import org.jackhuang.hmcl.ui.TwoLineActionListCell;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.MenuSeparator;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Agent business adapter over HMCL's shared game-list/search/list-cell visual primitives.
///
/// The search toolbar, fade transition, spinner, placeholder and list hierarchy are physically shared with
/// `GameListPage` through [SearchableListPage]; this class only supplies Agent data/actions.
@NotNullByDefault
public final class AgentListPage<T extends AgentEntry> extends DecoratorAnimatedPage implements DecoratorPage {
    /// Decorator title state.
    private final ReadOnlyObjectWrapper<State> state;
    /// Shared HMCL searchable list with Agent callbacks.
    private final NativeList list;
    /// Whether this list owns HMCL's Provider navigation rail.
    private final boolean showProviderSidebar;
    /// Whether the list's third toolbar action imports a local extension catalog.
    private final boolean showExtensionCatalogAction;
    /// Creation and import actions belong to the instance browser navigation rail.
    private boolean navigationToolbar;
    /// Resource categories open their own editors instead of pretending every resource can launch.
    private boolean resourceList;
    /// Optional catalog navigation replaces creation of an empty local entry.
    private @Nullable Runnable addNavigationAction;

    /// Routes the add button into a catalog while preserving normal add behavior for other resource types.
    public void setAddNavigationAction(Runnable action) {
        addNavigationAction = action;
    }

    /// Hides generic catalog/toggle controls in a typed resource management page.
    public void useResourceList() {
        resourceList = true;
    }

    /// Keeps refresh and search in the list toolbar while navigation owns creation actions.
    public void useNavigationToolbar() {
        navigationToolbar = true;
    }

    /// Creates one Agent list page while keeping all visual behavior in HMCL shared controls.
    public AgentListPage(
            String title,
            ObservableList<T> source,
            Supplier<T> addAction,
            Runnable reloadAction,
            Consumer<T> openAction,
            Consumer<T> selectAction,
            Consumer<T> deleteAction,
            @Nullable Consumer<T> runAction,
            Predicate<T> selectedPredicate,
            @Nullable ObservableValue<?> selectionSignal,
            String addLabel,
            String runTooltip,
            boolean showProviderSidebar,
            boolean showExtensionCatalogAction) {
        this(title, source, addAction, reloadAction, openAction, selectAction, deleteAction, runAction,
                selectedPredicate, selectionSignal, addLabel, runTooltip, showProviderSidebar,
                showExtensionCatalogAction, null);
    }

    /// Creates an Agent list with an optional instance-specific Pack export action.
    public AgentListPage(
            String title,
            ObservableList<T> source,
            Supplier<T> addAction,
            Runnable reloadAction,
            Consumer<T> openAction,
            Consumer<T> selectAction,
            Consumer<T> deleteAction,
            @Nullable Consumer<T> runAction,
            Predicate<T> selectedPredicate,
            @Nullable ObservableValue<?> selectionSignal,
            String addLabel,
            String runTooltip,
            boolean showProviderSidebar,
            boolean showExtensionCatalogAction,
            @Nullable Consumer<T> packExportAction) {
        state = new ReadOnlyObjectWrapper<>(State.fromTitle(title));
        this.showProviderSidebar = showProviderSidebar;
        this.showExtensionCatalogAction = showExtensionCatalogAction;
        list = new NativeList(source, addAction, reloadAction, openAction, selectAction, deleteAction,
                runAction, selectedPredicate, selectionSignal, addLabel, runTooltip, packExportAction);

        if (showProviderSidebar) {
            AgentRepository repository = AgentRepository.get();
        ObservableList<AdvancedListItem> providerItems = MappedObservableList.create(
                repository.getProviders(), provider -> {
                    AdvancedListItem item = new AdvancedListItem();
                    FXUtils.setLimitWidth(item, 200);
                    item.setTitle(provider.getName());
                    item.subtitleProperty().bind(provider.summaryProperty());
                    item.setLeftIcon(SVG.PERSON);
                    item.setOnAction(event -> repository.setSelectedProvider(provider));
                    FXUtils.onSecondaryButtonClicked(item, () -> AgentPages.openProvider(provider));
                    return item;
                });

        ScrollPane pane = new ScrollPane();
        VBox.setVgrow(pane, Priority.ALWAYS);
        AdvancedListItem addProviderItem = new AdvancedListItem();
        addProviderItem.getStyleClass().add("navigation-drawer-item");
        addProviderItem.setTitle(i18n("agent.provider.add"));
        addProviderItem.setLeftIcon(SVG.ADD_CIRCLE);
        addProviderItem.setOnAction(event -> AgentPages.openProvider(repository.addProvider()));

        pane.setFitToWidth(true);
        VBox wrapper = new VBox();
        wrapper.getStyleClass().add("advanced-list-box-content");
        VBox box = new VBox();
        box.setFillWidth(true);
        Bindings.bindContent(box.getChildren(), providerItems);
        wrapper.getChildren().setAll(box, addProviderItem);
        pane.setContent(wrapper);
        FXUtils.smoothScrolling(pane);

        AdvancedListBox bottomLeftCornerList = new AdvancedListBox()
                .addNavigationDrawerItem(i18n("settings"), SVG.SETTINGS,
                        () -> org.jackhuang.hmcl.ui.Controllers.navigate(AgentPages.settings()));
        FXUtils.setLimitHeight(bottomLeftCornerList, 40 + 12 * 2);
        setLeft(pane, bottomLeftCornerList);
        } else {
            setLeft(new AdvancedListBox());
            getLeft().setManaged(false);
            getLeft().setVisible(false);
        }
        setCenter(list);
    }

    /// Returns the decorator title state.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /// Supplies Agent data/actions to the same shared searchable-list implementation used by Minecraft.
    private final class NativeList extends SearchableListPage<T> {
        /// Adds an entry.
        private final Supplier<T> addAction;
        /// Reloads persistent state.
        private final Runnable reloadAction;
        /// Opens an entry editor.
        private final Consumer<T> openAction;
        /// Selects or enables an entry.
        private final Consumer<T> selectAction;
        /// Deletes an entry.
        private final Consumer<T> deleteAction;
        /// Optional launch/toggle action shown in the rocket position.
        private final @Nullable Consumer<T> runAction;
        /// Reports whether the row is currently selected.
        private final Predicate<T> selectedPredicate;
        /// Text for HMCL's add toolbar button.
        private final String addLabel;
        /// Tooltip for the optional rocket action.
        private final String runTooltip;
        /// Optional instance-specific Pack export action.
        private final @Nullable Consumer<T> packExportAction;

        /// Initializes the shared HMCL list with Agent callbacks.
        private NativeList(
                ObservableList<T> source,
                Supplier<T> addAction,
                Runnable reloadAction,
                Consumer<T> openAction,
                Consumer<T> selectAction,
                Consumer<T> deleteAction,
                @Nullable Consumer<T> runAction,
                Predicate<T> selectedPredicate,
                @Nullable ObservableValue<?> selectionSignal,
                String addLabel,
                String runTooltip,
                @Nullable Consumer<T> packExportAction) {
            super(source);
            this.addAction = addAction;
            this.reloadAction = reloadAction;
            this.openAction = openAction;
            this.selectAction = selectAction;
            this.deleteAction = deleteAction;
            this.runAction = runAction;
            this.selectedPredicate = selectedPredicate;
            this.addLabel = addLabel;
            this.runTooltip = runTooltip;
            this.packExportAction = packExportAction;
            setRowRefreshSignal(selectionSignal);
        }

        /// Creates HMCL's normal substring/regex search predicate for Agent metadata.
        @Override
        protected Predicate<T> createSearchPredicate(@Nullable String searchText) {
            if (searchText == null || searchText.isEmpty()) return item -> true;
            if (searchText.startsWith("regex:")) {
                String expression = searchText.substring("regex:".length());
                try {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                            expression, java.util.regex.Pattern.CASE_INSENSITIVE);
                    return item -> pattern.matcher(item.getId()).find() || pattern.matcher(item.getName()).find();
                } catch (java.util.regex.PatternSyntaxException ignored) {
                    return item -> false;
                }
            }
            String needle = searchText.toLowerCase(Locale.ROOT);
            return item -> contains(item.getId(), needle)
                    || contains(item.getName(), needle)
                    || contains(item.summaryProperty().get(), needle)
                    || contains(item.tagProperty().get(), needle);
        }

        /// Reloads Agent persistent state through the supplied business callback.
        @Override
        protected void refreshList() {
            reloadAction.run();
        }

        /// Supplies Agent actions before the shared HMCL search button.
        @Override
        protected List<ToolbarAction> toolbarActions() {
            List<ToolbarAction> actions = new ArrayList<>();
            actions.add(new ToolbarAction(i18n("button.refresh"), SVG.REFRESH, this::refreshList));
            if (navigationToolbar) return actions;
            actions.add(new ToolbarAction(addLabel, SVG.ADD_CIRCLE, () -> {
                if (addNavigationAction != null) {
                    addNavigationAction.run();
                    return;
                }
                T added = addAction.get();
                if (added != null) openAction.accept(added);
            }));
            if (resourceList) return actions;
            actions.add(showExtensionCatalogAction
                    ? new ToolbarAction(i18n("agent.extension.catalog"), SVG.PACKAGE2,
                            AgentPages::importExtensionCatalog)
                    : new ToolbarAction(i18n("agent.resources.plugins"), SVG.PACKAGE2,
                            () -> org.jackhuang.hmcl.ui.Controllers.navigate(AgentPages.extensions())));
            return actions;
        }

        /// Creates the Agent-bound version of HMCL's game-list row.
        @Override
        protected ListCell<T> createListCell() {
            return new EntryCell();
        }

        /// Case-insensitive helper used by the search predicate.
        private boolean contains(@Nullable String value, String needle) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
        }

        /// Agent business bindings over the shared physical HMCL two-line action row.
        private final class EntryCell extends TwoLineActionListCell<T> {
            /// Optional rocket action button supplied by the caller.
            private final @Nullable JFXButton runButton;

            /// Creates only Agent-specific buttons; the row hierarchy itself lives in `TwoLineActionListCell`.
            private EntryCell() {
                runButton = runAction == null
                        ? null
                        : createActionButton(SVG.ROCKET_LAUNCH, runTooltip, runAction);
            }

            /// Selects or enables the Agent business item.
            @Override
            protected void selectItem(T item) {
                selectAction.accept(item);
            }

            /// Opens the Agent editor on the row's primary click.
            @Override
            protected void openItem(T item) {
                openAction.accept(item);
            }

            /// Creates the HMCL popup-menu container with Agent callbacks.
            @Override
            protected JFXPopup createPopup(T item) {
                PopupMenu menu = new PopupMenu();
                JFXPopup popup = new JFXPopup(menu);
                if (runAction != null) {
                    menu.getContent().add(new IconedMenuItem(SVG.ROCKET_LAUNCH, runTooltip,
                            () -> runAction.accept(item), popup));
                    menu.getContent().add(new MenuSeparator());
                }
                if (packExportAction != null) {
                    menu.getContent().add(new IconedMenuItem(SVG.OUTPUT, i18n("agent.pack.export"),
                            () -> packExportAction.accept(item), popup));
                    menu.getContent().add(new MenuSeparator());
                }
                menu.getContent().add(new IconedMenuItem(SVG.EDIT, i18n("button.edit"),
                        () -> openAction.accept(item), popup));
                menu.getContent().add(new IconedMenuItem(SVG.DELETE, i18n("button.delete"),
                        () -> deleteAction.accept(item), popup));
                return popup;
            }

            /// Binds the Agent display name.
            @Override
            protected StringProperty titleProperty(T item) {
                return item.nameProperty();
            }

            /// Binds the Agent summary.
            @Override
            protected StringProperty subtitleProperty(T item) {
                return item.summaryProperty();
            }

            /// Binds the Agent tag.
            @Override
            protected StringProperty tagProperty(T item) {
                return item.tagProperty();
            }

            /// Reflects the externally managed Agent selection state.
            @Override
            protected void bindSelection(T item) {
                selectionButton.setVisible(!resourceList);
                selectionButton.setManaged(!resourceList);
                selectionButton.setSelected(selectedPredicate.test(item));
            }

            /// Uses DShCraft's built-in icon in the shared 32-pixel HMCL image container.
            @Override
            protected void bindImage(T item) {
                imageView.setImage(FXUtils.newBuiltinImage("/assets/img/dshcraft-mark.png"));
            }

            /// Adds the optional Agent run/toggle action before the shared manage button.
            @Override
            protected void populateRightActions(T item) {
                if (runButton != null) {
                    rightActions.getChildren().add(runButton);
                }
            }
        }
    }
}
