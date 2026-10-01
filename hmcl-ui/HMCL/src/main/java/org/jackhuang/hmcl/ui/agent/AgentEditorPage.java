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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.LineToggleButton;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;

/// Agent editor using HMCL SettingsPage's ScrollPane/VBox/ComponentList visual skeleton.
@NotNullByDefault
public final class AgentEditorPage extends DecoratorAnimatedPage implements DecoratorPage {
    /// Decorator title state.
    private final ReadOnlyObjectWrapper<State> state;
    /// HMCL settings-style root list.
    private final VBox rootPane = new VBox(10);
    /// HMCL row container.
    private ComponentList list = new ComponentList();

    /// Creates an editor with HMCL SettingsPage padding and smooth scrolling.
    public AgentEditorPage(String title) {
        state = new ReadOnlyObjectWrapper<>(State.fromTitle(title));
        getLeft().setManaged(false);
        getLeft().setVisible(false);
        rootPane.setPadding(new Insets(10));
        rootPane.getChildren().add(list);

        ScrollPane scrollPane = new ScrollPane(rootPane);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setCenter(scrollPane);
    }

    /// Starts an HMCL settings section without merging unrelated settings into one list.
    public AgentEditorPage addSection(String title) {
        if (list.getContent().isEmpty()) {
            rootPane.getChildren().add(rootPane.getChildren().indexOf(list), ComponentList.createComponentListTitle(title));
        } else {
            list = new ComponentList();
            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(title), list);
        }
        return this;
    }

    /// Adds an editable HMCL LineButton row bound to a string property.
    public AgentEditorPage addText(String title, String subtitle, StringProperty property, boolean allowBlank) {
        LineButton row = new LineButton();
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.trailingTextProperty().bind(property);
        row.setTrailingIcon(SVG.EDIT);
        row.setOnAction(event -> Controllers.prompt(title, (value, handler) -> {
            String normalized = value == null ? "" : value.trim();
            if (!allowBlank && normalized.isEmpty()) {
                handler.reject(title + " cannot be empty");
                return;
            }
            property.set(normalized);
            handler.resolve();
        }, property.get()));
        list.getContent().add(row);
        return this;
    }

    /// Adds an HMCL candidate row bound to one persisted protocol or endpoint selection.
    public AgentEditorPage addChoice(String title, String subtitle, StringProperty property, String... choices) {
        LineButton row = new LineButton();
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.trailingTextProperty().bind(property);
        row.setTrailingIcon(SVG.EDIT);
        row.setOnAction(event -> {
            PromptDialogPane.Builder.CandidatesQuestion question =
                    new PromptDialogPane.Builder.CandidatesQuestion(title, choices);
            Controllers.prompt(new PromptDialogPane.Builder(title, (questions, handler) -> handler.resolve())
                    .addQuestion(question)).thenAccept(ignored -> {
                Integer index = question.getValue();
                if (index != null && index >= 0 && index < choices.length) property.set(choices[index]);
            });
        });
        list.getContent().add(row);
        return this;
    }

    /// Adds a choice row that stores stable values while displaying readable labels.
    public AgentEditorPage addLabeledChoice(String title, String subtitle, StringProperty property,
                                            String[] values, String[] labels) {
        if (values.length != labels.length || values.length == 0) {
            throw new IllegalArgumentException("Choice values and labels must have the same non-zero length");
        }
        LineButton row = new LineButton();
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.setTrailingIcon(SVG.EDIT);
        row.trailingTextProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            int index = java.util.Arrays.asList(values).indexOf(property.get());
            return index >= 0 ? labels[index] : property.get();
        }, property));
        row.setOnAction(event -> {
            PromptDialogPane.Builder.CandidatesQuestion question =
                    new PromptDialogPane.Builder.CandidatesQuestion(title, labels);
            Controllers.prompt(new PromptDialogPane.Builder(title, (questions, handler) -> handler.resolve())
                    .addQuestion(question)).thenAccept(ignored -> {
                Integer index = question.getValue();
                if (index != null && index >= 0 && index < values.length) property.set(values[index]);
            });
        });
        list.getContent().add(row);
        return this;
    }

    /// Displays a stable identifier in an HMCL row without allowing reference-breaking edits.
    public AgentEditorPage addReadOnlyText(String title, String subtitle, StringProperty property) {
        LineButton row = new LineButton();
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.trailingTextProperty().bind(property);
        row.setMouseTransparent(true);
        row.setFocusTraversable(false);
        list.getContent().add(row);
        return this;
    }

    /// Adds an HMCL LineToggleButton row bound bidirectionally to a boolean property.
    public AgentEditorPage addToggle(String title, String subtitle, BooleanProperty property) {
        LineToggleButton row = new LineToggleButton();
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.selectedProperty().bindBidirectional(property);
        list.getContent().add(row);
        return this;
    }

    /// Adds an HMCL LineButton action row.
    public AgentEditorPage addAction(String title, String subtitle, SVG icon, Runnable action) {
        LineButton row = new LineButton();
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.setTrailingIcon(icon);
        row.setOnAction(event -> action.run());
        list.getContent().add(row);
        return this;
    }

    /// Returns the decorator title state.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
