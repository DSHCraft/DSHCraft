/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 * Modifications for DShCraft direct HMCL fork Copyright (C) 2026 DShCraft contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui.agent;

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.WindowEvent;
import org.jackhuang.hmcl.agent.AgentInstance;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Direct Agent adaptation of HMCL GameListPopupMenu; dimensions, style classes and node hierarchy are preserved.
@NotNullByDefault
public final class AgentListPopupMenu extends StackPane {
    /// Owner property key used to ensure only one popup is shown at a time.
    private static final String KEY = AgentListPopupMenu.class.getName() + ".popup";

    /// Hides the popup currently owned by a node when one is showing.
    public static boolean hideShowing(Node owner) {
        JFXPopup popup = (JFXPopup) owner.getProperties().get(KEY);
        if (popup != null && popup.isShowing()) {
            popup.hide();
            return true;
        } else {
            return false;
        }
    }

    /// Shows an Agent instance selection popup at the requested HMCL popup position.
    public static void show(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                            double initOffsetX, double initOffsetY, List<AgentInstance> instances) {
        if (!hideShowing(owner)) {
            showAndGetPopup(owner, vAlign, hAlign, initOffsetX, initOffsetY, instances);
        }
    }

    /// Shows and returns an Agent instance selection popup using HMCL GameListPopupMenu's exact geometry.
    public static JFXPopup showAndGetPopup(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                                           double initOffsetX, double initOffsetY, List<AgentInstance> instances) {
        AgentListPopupMenu menu = new AgentListPopupMenu();
        menu.getItems().setAll(instances.stream().map(AgentListItem::new).toList());
        JFXPopup popup = new JFXPopup(menu);
        owner.getProperties().put(KEY, popup);
        popup.addEventFilter(WindowEvent.WINDOW_HIDDEN, event -> owner.getProperties().remove(KEY, popup));
        popup.show(owner, vAlign, hAlign, initOffsetX, initOffsetY, true);
        return popup;
    }

    /// HMCL JFX list view used by the popup.
    private final JFXListView<AgentListItem<AgentInstance>> listView = new JFXListView<>();
    /// Binding used to swap between the list and its empty placeholder.
    private final BooleanBinding isEmpty = Bindings.isEmpty(listView.getItems());

    /// Creates the popup with the same max height, fixed cell size and width as HMCL GameListPopupMenu.
    public AgentListPopupMenu() {
        setMaxHeight(365);
        getStyleClass().add("popup-menu-content");
        listView.setCellFactory(Cell::new);
        listView.setFixedCellSize(50);
        listView.setPrefWidth(300);
        listView.prefHeightProperty().bind(Bindings.size(getItems()).multiply(50).add(2));

        Label placeholder = new Label(i18n("agent.instance.add"));
        placeholder.setStyle("-fx-padding: 10px; -fx-text-fill: -monet-on-surface-variant; -fx-font-style: italic;");
        FXUtils.onChangeAndOperate(isEmpty, empty -> getChildren().setAll(empty ? placeholder : listView));
    }

    /// Returns the mutable popup display items.
    public ObservableList<AgentListItem<AgentInstance>> getItems() {
        return listView.getItems();
    }

    /// Direct Agent adaptation of HMCL GameListPopupMenu.Cell.
    private static final class Cell extends ListCell<AgentListItem<AgentInstance>> {
        /// Row graphic container.
        private final Region graphic;
        /// HMCL ripple wrapper.
        private final RipplerContainer ripplerContainer;
        /// Fixed 32-pixel HMCL image container.
        private final ImageContainer imageView;
        /// HMCL two-line content control.
        private final TwoLineListItem content;
        /// HMCL tag binding.
        private final StringProperty tag = new SimpleStringProperty();

        /// Creates a cell with the same layout as HMCL GameListPopupMenu.Cell.
        public Cell(ListView<AgentListItem<AgentInstance>> listView) {
            setPadding(Insets.EMPTY);

            imageView = new ImageContainer(32);
            imageView.setMouseTransparent(true);
            BorderPane.setAlignment(imageView, Pos.CENTER);

            content = new TwoLineListItem();
            content.setMouseTransparent(true);
            FXUtils.onChangeAndOperate(tag, value -> {
                content.getTags().clear();
                if (StringUtils.isNotBlank(value)) content.addTag(value);
            });

            BorderPane container = new BorderPane();
            container.getStyleClass().add("container");
            container.setPickOnBounds(false);
            container.setLeft(imageView);
            container.setCenter(content);

            ripplerContainer = new RipplerContainer(container);

            StackPane rootPane = new StackPane();
            rootPane.getStyleClass().add("advanced-list-item");
            rootPane.getChildren().setAll(ripplerContainer);
            rootPane.maxWidthProperty().bind(listView.widthProperty().subtract(5));

            FXUtils.onClicked(rootPane, () -> {
                AgentListItem<AgentInstance> item = getItem();
                if (item != null) {
                    AgentRepository.get().setSelectedInstance(item.getEntry());
                    if (getScene().getWindow() instanceof JFXPopup popup) popup.hide();
                }
            });
            graphic = rootPane;
        }

        /// Rebinds the direct-copy row controls to the new Agent item.
        @Override
        protected void updateItem(AgentListItem<AgentInstance> item, boolean empty) {
            AgentListItem<AgentInstance> oldItem = getItem();
            boolean oldEmpty = isEmpty();
            super.updateItem(item, empty);
            if (oldItem == item && oldEmpty == empty) return;

            ripplerContainer.releaseRippleImmediately();
            imageView.imageProperty().unbind();
            content.titleProperty().unbind();
            content.subtitleProperty().unbind();
            tag.unbind();

            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(graphic);
                imageView.imageProperty().bind(item.imageProperty());
                content.titleProperty().bind(item.titleProperty());
                content.subtitleProperty().bind(item.subtitleProperty());
                tag.bind(item.tagProperty());
            }
        }
    }
}
