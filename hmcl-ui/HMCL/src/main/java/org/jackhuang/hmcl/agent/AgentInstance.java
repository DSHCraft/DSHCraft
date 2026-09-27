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

/// Mutable DSh Agent instance metadata displayed through the HMCL-native instance UI.
@NotNullByDefault
public final class AgentInstance implements AgentEntry {
    /// Stable launcher identifier.
    private final StringProperty id;
    /// User-facing instance name.
    private final StringProperty name;
    /// Provider identifier used by this instance.
    private final StringProperty providerId;
    /// Optional human-readable description.
    private final StringProperty description;
    /// Requested `@deepseek-ai/dsh` version or channel.
    private final StringProperty coreVersion;
    /// DSh profile name inside the isolated DSH_HOME.
    private final StringProperty profileName;
    /// DSh profile template used when provisioning a profile.
    private final StringProperty profileTemplate;
    /// Web profile port stored as text so it can use the standard HMCL text editor row.
    private final StringProperty webPort;
    /// Per-instance model override. Blank means use the selected provider model.
    private final StringProperty model;
    /// Comma-separated extension identifiers enabled for this instance.
    private final StringProperty extensionIds;
    /// Executable used to launch this instance.
    private final StringProperty command;
    /// Command-line arguments passed to the executable.
    private final StringProperty arguments;
    /// Workspace / process working directory.
    private final StringProperty workingDirectory;
    /// Derived two-line-list subtitle.
    private final StringProperty summary = new SimpleStringProperty(this, "summary");
    /// Derived list tag.
    private final StringProperty tag = new SimpleStringProperty(this, "tag");

    /// Creates an instance using the original v1.2-compatible field set.
    public AgentInstance(String id, String name, String providerId, String command, String arguments, String workingDirectory) {
        this(id, name, providerId, "", "latest", "web", "web", "3080", "", "", command, arguments, workingDirectory);
    }

    /// Creates a fully-described DSh instance without requiring any desktop-only runtime operation.
    public AgentInstance(
            String id,
            String name,
            String providerId,
            String description,
            String coreVersion,
            String profileName,
            String profileTemplate,
            String webPort,
            String model,
            String extensionIds,
            String command,
            String arguments,
            String workingDirectory) {
        this.id = new SimpleStringProperty(this, "id", Objects.requireNonNullElse(id, "agent"));
        this.name = new SimpleStringProperty(this, "name", Objects.requireNonNullElse(name, "Agent"));
        this.providerId = new SimpleStringProperty(this, "providerId", Objects.requireNonNullElse(providerId, "default"));
        this.description = new SimpleStringProperty(this, "description", Objects.requireNonNullElse(description, ""));
        this.coreVersion = new SimpleStringProperty(this, "coreVersion", Objects.requireNonNullElse(coreVersion, "latest"));
        this.profileName = new SimpleStringProperty(this, "profileName", Objects.requireNonNullElse(profileName, "web"));
        this.profileTemplate = new SimpleStringProperty(this, "profileTemplate", Objects.requireNonNullElse(profileTemplate, "web"));
        this.webPort = new SimpleStringProperty(this, "webPort", Objects.requireNonNullElse(webPort, "3080"));
        this.model = new SimpleStringProperty(this, "model", Objects.requireNonNullElse(model, ""));
        this.extensionIds = new SimpleStringProperty(this, "extensionIds", Objects.requireNonNullElse(extensionIds, ""));
        this.command = new SimpleStringProperty(this, "command", Objects.requireNonNullElse(command, "dsh"));
        this.arguments = new SimpleStringProperty(this, "arguments", Objects.requireNonNullElse(arguments, ""));
        this.workingDirectory = new SimpleStringProperty(this, "workingDirectory", Objects.requireNonNullElse(workingDirectory, ""));
        summary.bind(Bindings.createStringBinding(() -> {
            String version = safe(this.coreVersion.get(), "latest");
            String profile = safe(this.profileName.get(), "web");
            return "DSH " + version + "  /  " + profile;
        }, this.coreVersion, this.profileName));
        tag.bind(this.coreVersion);
    }

    /// Returns the stable ID property.
    @Override
    public StringProperty idProperty() {
        return id;
    }

    /// Returns the display-name property.
    @Override
    public StringProperty nameProperty() {
        return name;
    }

    /// Returns the provider ID property.
    public StringProperty providerIdProperty() {
        return providerId;
    }

    /// Returns the description property.
    public StringProperty descriptionProperty() {
        return description;
    }

    /// Returns the DSh core version property.
    public StringProperty coreVersionProperty() {
        return coreVersion;
    }

    /// Returns the DSh profile name property.
    public StringProperty profileNameProperty() {
        return profileName;
    }

    /// Returns the DSh profile template property.
    public StringProperty profileTemplateProperty() {
        return profileTemplate;
    }

    /// Returns the web profile port property.
    public StringProperty webPortProperty() {
        return webPort;
    }

    /// Returns the per-instance model override property.
    public StringProperty modelProperty() {
        return model;
    }

    /// Returns the comma-separated extension-ID property.
    public StringProperty extensionIdsProperty() {
        return extensionIds;
    }

    /// Returns the executable command property.
    public StringProperty commandProperty() {
        return command;
    }

    /// Returns the command-line argument property.
    public StringProperty argumentsProperty() {
        return arguments;
    }

    /// Returns the workspace / working-directory property.
    public StringProperty workingDirectoryProperty() {
        return workingDirectory;
    }

    /// Returns the derived list subtitle property.
    @Override
    public StringProperty summaryProperty() {
        return summary;
    }

    /// Returns the derived list tag property.
    @Override
    public StringProperty tagProperty() {
        return tag;
    }

    /// Normalizes a possibly blank value for compact derived labels.
    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
