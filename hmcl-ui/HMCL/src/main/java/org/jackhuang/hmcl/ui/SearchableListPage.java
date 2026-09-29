/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020 huangyuhui <huanghongxun2008@126.com> and contributors
 * Shared extraction for DShCraft direct HMCL fork Copyright (C) 2026 DShCraft contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Shared extraction of HMCL `GameListPage.GameListSkin`.
///
/// Both Minecraft's game list and DShCraft's Agent lists use this exact physical toolbar/search/list
/// implementation, preventing the Agent UI from drifting into a second screenshot-based imitation.
@NotNullByDefault
public abstract class SearchableListPage<T> extends ListPageBase<T> {
    /// Live source list supplied by the business adapter.
    private final ObservableList<T> sourceList;
    /// Live filtered view used by the original HMCL search toolbar behavior.
    private final FilteredList<T> filteredList;
    /// Optional observable that forces row refreshes when selection state changes externally.
    private @Nullable ObservableValue<?> refreshSignal;

    /// Wraps a live source list with HMCL's search/filter presentation.
    protected SearchableListPage(ObservableList<T> source) {
        sourceList = source;
        filteredList = new FilteredList<>(source);
        setItems(filteredList);
    }

    /// Returns the live source behind the filtered list for callers that need to replace its contents.
    protected final ObservableList<T> sourceItems() {
        return sourceList;
    }

    /// Registers an observable that should refresh visible rows when it changes.
    protected final void setRowRefreshSignal(@Nullable ObservableValue<?> signal) {
        refreshSignal = signal;
    }

    /// Builds the search predicate used after HMCL's 100 ms debounce.
    protected abstract Predicate<T> createSearchPredicate(@Nullable String searchText);

    /// Reloads or refreshes source data.
    protected abstract void refreshList();

    /// Supplies normal-toolbar actions placed before HMCL's shared search action.
    protected abstract List<ToolbarAction> toolbarActions();

    /// Creates the row cell used by this list.
    protected abstract ListCell<T> createListCell();

    /// Message shown when this list has no rows and local search is inactive.
    protected String emptyListMessage() {
        return "";
    }

    /// Creates the shared HMCL GameList-style skin.
    @Override
    protected final Skin<?> createDefaultSkin() {
        return new SearchableListSkin<>(this);
    }

    /// One HMCL toolbar action specification.
    public record ToolbarAction(@Nullable String text, SVG icon, Runnable action,
                                @Nullable String tooltip) {
        /// Uses the visible label as the hover description by default.
        public ToolbarAction(@Nullable String text, SVG icon, Runnable action) {
            this(text, icon, action, text);
        }
    }

    /// Shared physical skin extracted from `GameListPage.GameListSkin` with only data callbacks parameterized.
    private static final class SearchableListSkin<T> extends SkinBase<SearchableListPage<T>> {
        /// HMCL fade-transition host for normal/search toolbars.
        private final TransitionPane toolbarPane;
        /// Search toolbar node.
        private final HBox searchBar;
        /// Normal action toolbar node.
        private final HBox toolbarNormal;
        /// Whether search mode is active.
        private final BooleanProperty searching = new SimpleBooleanProperty(false);
        /// HMCL/JFoenix search input.
        private final JFXTextField searchField;
        /// Backing JFoenix list view.
        private final JFXListView<T> listView;

        /// Builds the exact notice-pane / ComponentList / SpinnerPane structure used by HMCL's game list.
        private SearchableListSkin(SearchableListPage<T> skinnable) {
            super(skinnable);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");
            listView = new JFXListView<>();

            toolbarPane = new TransitionPane();
            searchBar = new HBox();
            toolbarNormal = new HBox();

            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            PauseTransition pause = new PauseTransition(Duration.millis(100));
            pause.setOnFinished(event -> skinnable.filteredList.setPredicate(
                    skinnable.createSearchPredicate(searchField.getText())));
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (searching.get() || !StringUtils.isBlank(newValue)) {
                    pause.setRate(1);
                    pause.playFromStart();
                }
            });

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE, () -> {
                changeToolbar(toolbarNormal);
                searchField.clear();
                pause.stop();
                skinnable.filteredList.setPredicate(null);
                searching.set(false);
            });
            onEscPressed(searchField, closeSearchBar::fire);
            searchBar.getChildren().setAll(searchField, closeSearchBar);

            for (ToolbarAction action : skinnable.toolbarActions()) {
                JFXButton button = createToolbarButton2(action.text(), action.icon(), action.action());
                if (action.tooltip() != null) FXUtils.installFastTooltip(button, action.tooltip());
                toolbarNormal.getChildren().add(button);
            }
            toolbarNormal.getChildren().add(createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar)));

            toolbarPane.setContent(toolbarNormal, ContainerAnimations.FADE);
            FXUtils.setOverflowHidden(toolbarPane, 8);
            root.getContent().add(toolbarPane);

            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.loadingProperty().bind(skinnable.loadingProperty());
            center.failedReasonProperty().bind(skinnable.failedReasonProperty());

            listView.setCellFactory(ignored -> skinnable.createListCell());
            listView.setItems(skinnable.getItems());
            ignoreEvent(listView, KeyEvent.KEY_PRESSED, event -> event.getCode() == KeyCode.ESCAPE);

            StackPane placeholderContainer = new StackPane();
            placeholderContainer.getStyleClass().add("notice-pane");
            Label placeholderLabel = new Label();
            placeholderLabel.textProperty().bind(Bindings.when(searching)
                    .then(i18n("search.no_results_found"))
                    .otherwise(skinnable.emptyListMessage()));
            placeholderContainer.getChildren().add(placeholderLabel);
            listView.setPlaceholder(placeholderContainer);

            center.setContent(listView);
            root.getContent().add(center);

            pane.getChildren().setAll(root);
            getChildren().setAll(pane);

            if (skinnable.refreshSignal != null) {
                skinnable.refreshSignal.addListener((observable, oldValue, newValue) -> listView.refresh());
            }
        }

        /// Switches between normal and search toolbars using HMCL's original fade transition.
        private void changeToolbar(HBox newToolbar) {
            Node oldToolbar = toolbarPane.getCurrentNode();
            if (newToolbar != oldToolbar) {
                toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
                if (newToolbar == searchBar) {
                    runInFX(searchField::requestFocus);
                    searching.set(true);
                }
            }
        }
    }
}
