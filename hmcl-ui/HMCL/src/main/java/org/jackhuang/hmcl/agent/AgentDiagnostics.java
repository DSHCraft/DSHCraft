/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jackhuang.hmcl.Metadata;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Produces a support report that deliberately excludes API-key values, process environment values and session data.
@NotNullByDefault
public final class AgentDiagnostics {
    /// Prevents construction of this utility class.
    private AgentDiagnostics() {
    }

    /// Builds a compact human-readable diagnostics report for the current launcher state.
    public static String createReport(AgentRepository repository) {
        StringBuilder report = new StringBuilder();
        report.append("DShCraft Agent diagnostics\n");
        report.append("generatedAt=").append(Instant.now()).append('\n');
        report.append("launcherVersion=").append(Metadata.VERSION).append('\n');
        report.append("javaVersion=").append(System.getProperty("java.version", "unknown")).append('\n');
        report.append("javaVendor=").append(System.getProperty("java.vendor", "unknown")).append('\n');
        report.append("os=").append(System.getProperty("os.name", "unknown")).append(' ')
                .append(System.getProperty("os.version", "unknown")).append(' ')
                .append(System.getProperty("os.arch", "unknown")).append('\n');
        report.append("configFile=").append(repository.getConfigFile()).append('\n');
        report.append("providers=").append(repository.getProviders().size()).append('\n');
        report.append("instances=").append(repository.getInstances().size()).append('\n');
        report.append("extensions=").append(repository.getExtensions().size()).append('\n');
        report.append("selectedProvider=")
                .append(repository.getSelectedProvider() == null ? "<none>" : repository.getSelectedProvider().getId()).append('\n');
        report.append("selectedInstance=")
                .append(repository.getSelectedInstance() == null ? "<none>" : repository.getSelectedInstance().getId()).append('\n');
        report.append('\n');

        for (AgentProvider provider : repository.getProviders()) {
            String envName = provider.apiKeyEnvProperty().get();
            report.append("[provider ").append(provider.getId()).append("]\n");
            report.append("name=").append(provider.getName()).append('\n');
            report.append("type=").append(provider.typeProperty().get()).append('\n');
            report.append("endpoint=").append(safeEndpoint(provider.baseUrlProperty().get())).append('\n');
            report.append("model=").append(provider.modelProperty().get()).append('\n');
            report.append("apiKeyEnv=").append(envName == null ? "" : envName).append('\n');
            report.append("apiKeyPresent=")
                    .append(envName != null && !envName.isBlank() && System.getenv(envName.trim()) != null).append('\n');
        }
        report.append('\n');

        for (AgentInstance instance : repository.getInstances()) {
            report.append("[instance ").append(instance.getId()).append("]\n");
            report.append("name=").append(instance.getName()).append('\n');
            report.append("providerId=").append(instance.providerIdProperty().get()).append('\n');
            report.append("coreVersion=").append(instance.coreVersionProperty().get()).append('\n');
            report.append("profile=").append(instance.profileNameProperty().get()).append('\n');
            report.append("profileTemplate=").append(instance.profileTemplateProperty().get()).append('\n');
            report.append("webPort=").append(instance.webPortProperty().get()).append('\n');
            report.append("model=").append(instance.modelProperty().get()).append('\n');
            report.append("extensions=").append(instance.extensionIdsProperty().get()).append('\n');
            report.append("command=").append(instance.commandProperty().get()).append('\n');
            report.append("commandOnPath=").append(resolveCommand(instance.commandProperty().get())).append('\n');
            report.append("workspace=").append(instance.workingDirectoryProperty().get()).append('\n');
            report.append("dshHome=").append(repository.getInstanceHome(instance)).append('\n');
            List<String> problems = repository.validateInstanceConfiguration(instance);
            report.append("validation=").append(problems.isEmpty() ? "ok" : "failed").append('\n');
            for (String problem : problems) {
                report.append("validationProblem=").append(problem).append('\n');
            }
        }
        return report.toString();
    }

    /// Writes the sanitized diagnostics report as UTF-8 text.
    public static void writeReport(AgentRepository repository, Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, createReport(repository), StandardCharsets.UTF_8);
    }

    /// Reduces endpoints to scheme/authority/path and strips query or fragment data that could contain tokens.
    private static String safeEndpoint(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            URI uri = URI.create(raw.trim());
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) {
            return "<invalid-url>";
        }
    }

    /// Resolves a configured command against PATH without executing it.
    private static String resolveCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) return "false";
        String command = rawCommand.trim();
        Path direct = Path.of(command);
        if (direct.isAbsolute() || command.contains("/") || command.contains("\\")) {
            return Boolean.toString(Files.isRegularFile(direct));
        }
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) return "false";
        List<String> candidates = new ArrayList<>();
        candidates.add(command);
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") && !command.contains(".")) {
            candidates.add(command + ".exe");
            candidates.add(command + ".cmd");
            candidates.add(command + ".bat");
        }
        for (String directory : pathValue.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) continue;
            for (String candidate : candidates) {
                if (Files.isRegularFile(Path.of(directory, candidate))) {
                    return "true (" + Path.of(directory, candidate) + ")";
                }
            }
        }
        return "false";
    }
}
