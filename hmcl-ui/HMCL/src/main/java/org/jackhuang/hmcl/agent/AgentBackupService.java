/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Imports and exports the browser-manageable DShCraft state without secret values or runtime files.
@NotNullByDefault
public final class AgentBackupService {
    /// Stable backup format marker.
    public static final String FORMAT = "dshcraft-agent-backup";
    /// Current backup schema version.
    public static final int FORMAT_VERSION = 1;
    /// Maximum accepted backup size to avoid accidentally loading an unrelated huge file.
    private static final long MAX_BACKUP_BYTES = 8L * 1024L * 1024L;

    /// Prevents construction of this utility class.
    private AgentBackupService() {
    }

    /// Serializes all browser-manageable state into a sanitized JSON backup.
    public static void exportBackup(AgentRepository repository, Path output) throws IOException {
        BackupDocument document = createDocument(repository);
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, JsonUtils.GSON.toJson(document), StandardCharsets.UTF_8);
    }

    /// Reads, validates and atomically replaces the repository state from a sanitized JSON backup.
    public static void importBackup(AgentRepository repository, Path input) throws IOException {
        if (!Files.isRegularFile(input)) {
            throw new IOException("Backup file does not exist: " + input);
        }
        long size = Files.size(input);
        if (size <= 0 || size > MAX_BACKUP_BYTES) {
            throw new IOException("Backup size is invalid: " + size + " bytes");
        }
        BackupDocument document;
        try {
            document = JsonUtils.GSON.fromJson(Files.readString(input, StandardCharsets.UTF_8), BackupDocument.class);
        } catch (RuntimeException e) {
            throw new IOException("Backup JSON is malformed", e);
        }
        validate(document);

        List<AgentProvider> providers = new ArrayList<>();
        for (ProviderData item : document.providers()) {
            providers.add(new AgentProvider(item.id(), item.name(), item.type(), item.baseUrl(), item.model(), item.apiKeyEnv(), item.protocol()));
        }
        List<AgentInstance> instances = new ArrayList<>();
        for (InstanceData item : document.instances()) {
            instances.add(new AgentInstance(
                    item.id(), item.name(), item.providerId(), item.description(), item.coreVersion(), item.profileName(),
                    item.profileTemplate(), item.webPort(), item.model(), item.extensionIds(), item.command(), item.arguments(),
                    item.workingDirectory()));
        }
        List<AgentExtension> extensions = new ArrayList<>();
        for (ExtensionData item : document.extensions()) {
            extensions.add(new AgentExtension(item.id(), item.name(), item.type(), item.location(), item.enabled()));
        }
        repository.replaceState(providers, instances, extensions, document.selectedProviderId(), document.selectedInstanceId());
    }

    /// Builds an immutable transfer document from the current repository state.
    public static BackupDocument createDocument(AgentRepository repository) {
        List<ProviderData> providers = repository.getProviders().stream()
                .map(provider -> new ProviderData(
                        provider.getId(), provider.getName(), provider.typeProperty().get(), provider.baseUrlProperty().get(),
                        provider.modelProperty().get(), provider.apiKeyEnvProperty().get(), provider.protocolProperty().get()))
                .toList();
        List<InstanceData> instances = repository.getInstances().stream()
                .map(instance -> new InstanceData(
                        instance.getId(), instance.getName(), instance.providerIdProperty().get(), instance.descriptionProperty().get(),
                        instance.coreVersionProperty().get(), instance.profileNameProperty().get(), instance.profileTemplateProperty().get(),
                        instance.webPortProperty().get(), instance.modelProperty().get(), instance.extensionIdsProperty().get(),
                        instance.commandProperty().get(), instance.argumentsProperty().get(), instance.workingDirectoryProperty().get()))
                .toList();
        List<ExtensionData> extensions = repository.getExtensions().stream()
                .map(extension -> new ExtensionData(
                        extension.getId(), extension.getName(), extension.typeProperty().get(), extension.locationProperty().get(),
                        extension.enabledProperty().get()))
                .toList();
        String selectedProviderId = repository.getSelectedProvider() == null ? "" : repository.getSelectedProvider().getId();
        String selectedInstanceId = repository.getSelectedInstance() == null ? "" : repository.getSelectedInstance().getId();
        return new BackupDocument(
                FORMAT,
                FORMAT_VERSION,
                Metadata.VERSION,
                Instant.now().toString(),
                selectedProviderId,
                selectedInstanceId,
                providers,
                instances,
                extensions);
    }

    /// Validates the file envelope, required IDs and all cross-references before changing live state.
    private static void validate(BackupDocument document) throws IOException {
        if (document == null || !FORMAT.equals(document.format()) || document.formatVersion() != FORMAT_VERSION) {
            throw new IOException("Unsupported DShCraft backup format");
        }
        if (document.providers() == null || document.instances() == null || document.extensions() == null) {
            throw new IOException("Backup is missing state collections");
        }
        Set<String> providerIds = uniqueIds(document.providers().stream().map(ProviderData::id).toList(), "provider");
        Set<String> extensionIds = uniqueIds(document.extensions().stream().map(ExtensionData::id).toList(), "extension");
        uniqueIds(document.instances().stream().map(InstanceData::id).toList(), "instance");
        for (InstanceData instance : document.instances()) {
            requireText(instance.name(), "Instance name");
            requireText(instance.providerId(), "Instance provider ID");
            if (!providerIds.contains(instance.providerId())) {
                throw new IOException("Instance references an unknown provider: " + instance.providerId());
            }
            for (String extensionId : AgentPackService.parseIds(instance.extensionIds())) {
                if (!extensionIds.contains(extensionId)) {
                    throw new IOException("Instance references an unknown extension: " + extensionId);
                }
            }
        }
        for (ProviderData provider : document.providers()) {
            requireText(provider.name(), "Provider name");
        }
        for (ExtensionData extension : document.extensions()) {
            requireText(extension.name(), "Extension name");
        }
    }

    /// Validates ID uniqueness and returns the resulting ID set.
    private static Set<String> uniqueIds(List<String> values, String kind) throws IOException {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            requireText(value, kind + " ID");
            if ("instance".equals(kind)) DshModService.validateInstanceId(value);
            if (!result.add(value)) {
                throw new IOException("Duplicate " + kind + " ID: " + value);
            }
        }
        return result;
    }

    /// Rejects blank required text fields.
    private static void requireText(String value, String field) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(field + " cannot be blank");
        }
    }

    /// Complete versioned backup envelope.
    public record BackupDocument(
            String format,
            int formatVersion,
            String createdWith,
            String exportedAt,
            String selectedProviderId,
            String selectedInstanceId,
            List<ProviderData> providers,
            List<InstanceData> instances,
            List<ExtensionData> extensions) {
    }

    /// Sanitized provider metadata; only the environment-variable name is exported, never its secret value.
    public record ProviderData(String id, String name, String type, String baseUrl, String model, String apiKeyEnv, String protocol) {
    }

    /// Sanitized instance metadata; process environment values and runtime files are intentionally excluded.
    public record InstanceData(
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
    }

    /// Sanitized extension metadata.
    public record ExtensionData(String id, String name, String type, String location, boolean enabled) {
    }
}
