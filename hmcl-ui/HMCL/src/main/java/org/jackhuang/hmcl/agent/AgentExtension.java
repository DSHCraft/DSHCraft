/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Objects;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public final class AgentExtension implements AgentEntry {
    /// Core capabilities that need no package installation.
    private static final @Unmodifiable Set<String> BUILTIN_IDS = Set.of("filesystem", "browser", "skills", "mcp-client");
    private final StringProperty id;
    private final StringProperty name;
    private final StringProperty type;
    private final StringProperty location;
    /// Resolved package version in the selected instance's isolated Profile.
    private final StringProperty installedVersion;
    private final BooleanProperty enabled;
    private final StringProperty summary = new SimpleStringProperty(this, "summary");
    private final StringProperty tag = new SimpleStringProperty(this, "tag");

    public AgentExtension(String id, String name, String type, String location, boolean enabled) {
        this.id = new SimpleStringProperty(this, "id", Objects.requireNonNullElse(id, "extension"));
        this.name = new SimpleStringProperty(this, "name", Objects.requireNonNullElse(name, "Extension"));
        this.type = new SimpleStringProperty(this, "type", Objects.requireNonNullElse(type, "Plugin"));
        this.location = new SimpleStringProperty(this, "location", Objects.requireNonNullElse(location, ""));
        this.installedVersion = new SimpleStringProperty(this, "installedVersion", "");
        this.enabled = new SimpleBooleanProperty(this, "enabled", enabled);
        summary.bind(Bindings.createStringBinding(() -> {
            if (BUILTIN_IDS.contains(this.id.get())) return i18n("agent.mod.builtin");
            String path = this.location.get();
            String version = this.installedVersion.get();
            String state;
            if ("MCP".equalsIgnoreCase(this.type.get())) {
                state = i18n(this.enabled.get() ? "agent.resources.enabled" : "agent.resources.disabled");
            } else if ("Skill".equalsIgnoreCase(this.type.get())) {
                state = i18n(this.enabled.get() ? "agent.resources.imported" : "agent.resources.not_imported");
            } else {
                state = version == null || version.isBlank()
                        ? i18n("agent.resources.not_installed") : i18n("agent.resources.installed", version);
            }
            return state + (path == null || path.isBlank() ? "" : "  /  " + path);
        }, this.id, this.type, this.location, this.enabled, this.installedVersion));
        tag.bind(this.type);
    }

    /// Distinguishes built-in Core capabilities from independently configured resources.
    public boolean isBuiltin() {
        return BUILTIN_IDS.contains(getId());
    }

    /// Bundles share the npm plugin lifecycle; MCP and Skill remain separate categories.
    public boolean belongsToCategory(String category) {
        if (isBuiltin()) return false;
        return category.equalsIgnoreCase(type.get())
                || "Plugin".equalsIgnoreCase(category) && "Bundle".equalsIgnoreCase(type.get());
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

    public StringProperty locationProperty() {
        return location;
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    /// Returns the resolved version from the selected isolated Profile.
    public StringProperty installedVersionProperty() {
        return installedVersion;
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
