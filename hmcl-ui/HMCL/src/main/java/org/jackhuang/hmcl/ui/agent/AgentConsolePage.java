/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays the active DSH process streams using HMCL's page and list controls.
@NotNullByDefault
public final class AgentConsolePage extends DecoratorAnimatedPage implements DecoratorPage {
    /// Decorator title for the DSH console.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("agent.console")));

    /// Creates a bounded live console with open-Web, stop and clear actions.
    public AgentConsolePage() {
        AgentRepository repository = AgentRepository.get();
        Label status = new Label();
        status.textProperty().bind(Bindings.when(repository.runningProperty())
                .then(i18n("agent.console.running")).otherwise(i18n("agent.console.stopped")));

        JFXButton openWeb = new JFXButton(i18n("agent.console.open_web"));
        openWeb.disableProperty().bind(repository.runningProperty().not().or(repository.webUrlProperty().isEmpty()));
        openWeb.setOnAction(event -> FXUtils.openLink(repository.webUrlProperty().get()));

        JFXButton stop = new JFXButton(i18n("agent.console.stop"));
        stop.disableProperty().bind(repository.runningProperty().not());
        stop.setOnAction(event -> repository.stop());

        JFXButton clear = new JFXButton(i18n("agent.console.clear"));
        clear.setOnAction(event -> repository.getConsoleLines().clear());

        HBox toolbar = new HBox(12, status, openWeb, stop, clear);
        toolbar.setPadding(new Insets(12));
        JFXListView<String> output = new JFXListView<>();
        output.setItems(repository.getConsoleLines());
        output.setCellFactory(list -> new ListCell<>() {
            /// Wraps long process lines without changing their stored text.
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setWrapText(true);
            }
        });
        output.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 12px;");
        repository.getConsoleLines().addListener((ListChangeListener<String>) change -> {
            if (!output.getItems().isEmpty()) output.scrollTo(output.getItems().size() - 1);
        });
        VBox root = new VBox(toolbar, output);
        VBox.setVgrow(output, Priority.ALWAYS);
        setCenter(root);
    }

    /// Returns the HMCL decorator title state.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
