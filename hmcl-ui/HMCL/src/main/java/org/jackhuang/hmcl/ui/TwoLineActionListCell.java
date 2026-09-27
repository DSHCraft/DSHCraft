/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 * Shared extraction for DShCraft direct HMCL fork Copyright (C) 2026 DShCraft contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXRadioButton;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Shared extraction of the visual/node shell from HMCL's original `GameListCell`.
///
/// Minecraft and DShCraft rows now use the same physical `md-list-cell`, radio selector, 32-pixel image,
/// two-line content, right action container, ripple behavior, click handling and manage-popup geometry.
/// Subclasses only provide business bindings and callbacks.
@NotNullByDefault
public abstract class TwoLineActionListCell<T> extends ListCell<T> {
    /// Ripple-wrapped row graphic.
    private final RipplerContainer graphic;
    /// Original HMCL row root.
    private final BorderPane cellRoot;
    /// Fixed 32-pixel image container.
    protected final ImageContainer imageView;
    /// Two-line title/subtitle/tag content.
    protected final TwoLineListItem content;
    /// Single-selection radio button.
    protected final JFXRadioButton selectionButton;
    /// Container for row-specific action buttons followed by the shared manage button.
    protected final HBox rightActions;
    /// Tag bridge used by `TwoLineListItem`.
    private final StringProperty tag = new javafx.beans.property.SimpleStringProperty();
    /// Shared three-dot manage action.
    private final JFXButton manageButton;

    /// Builds the exact reusable visual hierarchy from HMCL's original `GameListCell`.
    protected TwoLineActionListCell() {
        cellRoot = new BorderPane();
        cellRoot.getStyleClass().add("md-list-cell");
        cellRoot.setPadding(new Insets(8, 8, 8, 0));
        graphic = new RipplerContainer(cellRoot);

        selectionButton = new JFXRadioButton() {
            /// Dispatches HMCL's selection event without allowing a second click to clear the active row.
            @Override
            public void fire() {
                if (!isDisable() && !isSelected()) {
                    fireEvent(new ActionEvent());
                    T item = TwoLineActionListCell.this.getItem();
                    if (item != null) {
                        selectItem(item);
                    }
                }
            }
        };
        cellRoot.setLeft(selectionButton);
        BorderPane.setAlignment(selectionButton, Pos.CENTER);

        HBox center = new HBox();
        BorderPane.setMargin(center, new Insets(0, 0, 0, 8));
        center.setMouseTransparent(true);
        cellRoot.setCenter(center);
        center.setPrefWidth(Region.USE_PREF_SIZE);
        center.setSpacing(8);
        center.setAlignment(Pos.CENTER_LEFT);

        imageView = new ImageContainer(32);
        content = new TwoLineListItem();
        BorderPane.setAlignment(content, Pos.CENTER);
        FXUtils.onChangeAndOperate(tag, value -> {
            content.getTags().clear();
            if (StringUtils.isNotBlank(value)) {
                content.addTag(value);
            }
        });
        center.getChildren().setAll(imageView, content);

        rightActions = new HBox();
        cellRoot.setRight(rightActions);
        rightActions.setAlignment(Pos.CENTER_RIGHT);

        manageButton = FXUtils.newToggleButton4(SVG.MORE_VERT);
        manageButton.setOnAction(event -> {
            T item = getItem();
            if (item == null) return;
            JFXPopup popup = createPopup(item);
            JFXPopup.PopupVPosition position = determineOptimalPopupPosition(cellRoot, popup);
            popup.show(cellRoot, position, JFXPopup.PopupHPosition.RIGHT, 0,
                    position == JFXPopup.PopupVPosition.TOP ? cellRoot.getHeight() : -cellRoot.getHeight());
        });
        BorderPane.setAlignment(manageButton, Pos.CENTER);
        FXUtils.installFastTooltip(manageButton, i18n("settings.game.management"));

        cellRoot.setCursor(Cursor.HAND);
        graphic.setOnMouseClicked(event -> {
            T item = getItem();
            if (item == null) return;
            if (event.getButton() == MouseButton.PRIMARY) {
                if (event.getClickCount() == 1) {
                    openItem(item);
                }
            } else if (event.getButton() == MouseButton.SECONDARY) {
                JFXPopup popup = createPopup(item);
                JFXPopup.PopupVPosition position = determineOptimalPopupPosition(cellRoot, popup);
                popup.show(cellRoot, position, JFXPopup.PopupHPosition.LEFT, event.getX(),
                        position == JFXPopup.PopupVPosition.TOP ? event.getY() : event.getY() - cellRoot.getHeight());
            }
        });
    }

    /// Selects the business item represented by this row.
    protected abstract void selectItem(T item);

    /// Opens the business editor/management page for this row.
    protected abstract void openItem(T item);

    /// Creates the context/manage popup for this row.
    protected abstract JFXPopup createPopup(T item);

    /// Returns the title property to bind into HMCL's two-line content.
    protected abstract ObservableStringValue titleProperty(T item);

    /// Returns the subtitle property to bind into HMCL's two-line content.
    protected abstract ObservableStringValue subtitleProperty(T item);

    /// Returns the optional tag property to bind into HMCL's row tag area.
    protected abstract ObservableStringValue tagProperty(T item);

    /// Binds or assigns the radio selection state for a non-empty row.
    protected abstract void bindSelection(T item);

    /// Binds or assigns the 32-pixel image for a non-empty row.
    protected abstract void bindImage(T item);

    /// Appends row-specific actions before the shared manage button.
    protected abstract void populateRightActions(T item);

    /// Creates a standard HMCL icon action button whose callback always uses the row's current item.
    protected final JFXButton createActionButton(SVG icon, String tooltip, Consumer<T> action) {
        JFXButton button = FXUtils.newToggleButton4(icon);
        button.setOnAction(event -> {
            T item = getItem();
            if (item != null) {
                action.accept(item);
            }
        });
        BorderPane.setAlignment(button, Pos.CENTER);
        FXUtils.installFastTooltip(button, tooltip);
        return button;
    }

    /// Rebinds the shared HMCL visual shell to the current business item.
    @Override
    public final void updateItem(T item, boolean empty) {
        T oldItem = getItem();
        boolean oldEmpty = isEmpty();
        super.updateItem(item, empty);
        if (oldItem == item && oldEmpty == empty) return;

        graphic.releaseRippleImmediately();
        imageView.imageProperty().unbind();
        content.titleProperty().unbind();
        content.subtitleProperty().unbind();
        tag.unbind();
        selectionButton.selectedProperty().unbind();
        rightActions.getChildren().clear();

        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        setGraphic(graphic);
        bindSelection(item);
        bindImage(item);
        content.titleProperty().bind(titleProperty(item));
        content.subtitleProperty().bind(subtitleProperty(item));
        tag.bind(tagProperty(item));
        populateRightActions(item);
        rightActions.getChildren().add(manageButton);
    }
}
