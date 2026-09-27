/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.ui.agent;

import com.jfoenix.controls.JFXPasswordField;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.agent.AgentProvider;
import org.jackhuang.hmcl.agent.AgentExtension;
import org.jackhuang.hmcl.agent.AgentSecretStore;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// HMCL-native password dialog that sends Provider keys or MCP tokens directly to the OS credential store.
@NotNullByDefault
public final class AgentSecretDialogPane extends DialogPane {
    /// Provider receiving the secret.
    private final @Nullable AgentProvider provider;
    /// MCP server receiving a bearer token, when this is an MCP dialog.
    private final @Nullable AgentExtension mcp;
    /// Masked input that is never bound to launcher persistence.
    private final JFXPasswordField password = new JFXPasswordField();

    /// Creates a credential dialog without reading or displaying an existing key.
    public AgentSecretDialogPane(AgentProvider provider) {
        this.provider = provider;
        this.mcp = null;
        setTitle(i18n("agent.provider.secret.title"));
        Label explanation = new Label(i18n("agent.provider.secret.subtitle"));
        explanation.setWrapText(true);
        password.setPromptText(i18n("agent.provider.secret.prompt"));
        password.setMinWidth(320);
        password.textProperty().addListener((observable, oldValue, newValue) ->
                setValid(newValue != null && !newValue.isBlank()));
        setValid(false);
        VBox content = new VBox(12, explanation, password);
        content.setPadding(new Insets(8, 0, 10, 0));
        setBody(content);
    }

    /// Creates the same masked OS-credential dialog for an MCP bearer token.
    public AgentSecretDialogPane(AgentExtension mcp) {
        this.provider = null;
        this.mcp = mcp;
        setTitle(i18n("agent.mcp.token.save"));
        Label explanation = new Label(i18n("agent.mcp.token.subtitle"));
        explanation.setWrapText(true);
        password.setPromptText(i18n("agent.mcp.token.prompt"));
        password.setMinWidth(320);
        password.textProperty().addListener((observable, oldValue, newValue) ->
                setValid(newValue != null && !newValue.isBlank()));
        setValid(false);
        VBox content = new VBox(12, explanation, password);
        content.setPadding(new Insets(8, 0, 10, 0));
        setBody(content);
    }

    /// Saves the key and clears the password field before closing the dialog.
    @Override
    protected void onAccept() {
        setLoading();
        try {
            if (mcp != null) AgentSecretStore.setMcp(mcp.getId(), password.getText());
            else if (provider != null) AgentSecretStore.set(provider.getId(), password.getText());
            else throw new IOException("No credential target is selected");
            password.clear();
            onSuccess();
            Controllers.showToast(i18n(mcp != null ? "agent.mcp.token.saved" : "agent.provider.secret.saved"));
        } catch (IOException error) {
            onFailure(error.getMessage());
        }
    }
}
