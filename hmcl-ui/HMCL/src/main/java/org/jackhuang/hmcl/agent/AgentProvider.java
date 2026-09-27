/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

@NotNullByDefault
public final class AgentProvider implements AgentEntry {
    private final StringProperty id;
    private final StringProperty name;
    private final StringProperty type;
    private final StringProperty baseUrl;
    private final StringProperty model;
    private final StringProperty apiKeyEnv;
    /// DSH-supported provider protocol identifier written into the instance Profile config.
    private final StringProperty protocol;
    private final StringProperty summary = new SimpleStringProperty(this, "summary");
    private final StringProperty tag = new SimpleStringProperty(this, "tag");

    public AgentProvider(String id, String name, String type, String baseUrl, String model, String apiKeyEnv) {
        this(id, name, type, baseUrl, model, apiKeyEnv, "openai-completions");
    }

    /// Creates one Provider with an explicit DSH-supported wire protocol.
    public AgentProvider(String id, String name, String type, String baseUrl, String model, String apiKeyEnv, String protocol) {
        this.id = new SimpleStringProperty(this, "id", Objects.requireNonNullElse(id, "provider"));
        this.name = new SimpleStringProperty(this, "name", Objects.requireNonNullElse(name, "Provider"));
        this.type = new SimpleStringProperty(this, "type", Objects.requireNonNullElse(type, "OpenAI-compatible"));
        this.baseUrl = new SimpleStringProperty(this, "baseUrl", Objects.requireNonNullElse(baseUrl, ""));
        this.model = new SimpleStringProperty(this, "model", Objects.requireNonNullElse(model, ""));
        this.apiKeyEnv = new SimpleStringProperty(this, "apiKeyEnv", Objects.requireNonNullElse(apiKeyEnv, "OPENAI_API_KEY"));
        this.protocol = new SimpleStringProperty(this, "protocol", Objects.requireNonNullElse(protocol, "openai-completions"));
        summary.bind(Bindings.createStringBinding(() -> {
            String typeValue = this.type.get();
            String modelValue = this.model.get();
            if (modelValue == null || modelValue.isBlank()) {
                return typeValue == null ? "" : typeValue;
            }
            return (typeValue == null || typeValue.isBlank()) ? modelValue : typeValue + "  /  " + modelValue;
        }, this.type, this.model));
        tag.bind(this.type);
    }

    @Override
    public StringProperty idProperty() {
        return id;
    }

    @Override
    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty typeProperty() {
        return type;
    }

    public StringProperty baseUrlProperty() {
        return baseUrl;
    }

    public StringProperty modelProperty() {
        return model;
    }

    public StringProperty apiKeyEnvProperty() {
        return apiKeyEnv;
    }

    /// Returns the DSH provider protocol property.
    public StringProperty protocolProperty() {
        return protocol;
    }

    @Override
    public StringProperty summaryProperty() {
        return summary;
    }

    @Override
    public StringProperty tagProperty() {
        return tag;
    }
}
