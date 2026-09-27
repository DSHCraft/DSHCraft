/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// Verifies that DSH console Web links stay within the local machine.
@NotNullByDefault
public class AgentRepositoryOutputTest {
    /// Disposable Profile directory for provider routing tests.
    @TempDir
    Path temporary;
    /// Captures only an announced loopback Web URL, including its session token.
    @Test
    public void acceptsLocalWebAnnouncement() {
        assertEquals("http://127.0.0.1:3080/?token=fixture",
                AgentRepository.extractLoopbackWebUrl("info dsh web: http://127.0.0.1:3080/?token=fixture"));
    }

    /// Rejects remote, unrelated and malformed URLs before exposing an Open Web action.
    @Test
    public void rejectsNonLocalOrUnannouncedUrls() {
        assertNull(AgentRepository.extractLoopbackWebUrl("info dsh web: https://example.com/session"));
        assertNull(AgentRepository.extractLoopbackWebUrl("info dsh web: http://localhost.evil.test:3080/"));
        assertNull(AgentRepository.extractLoopbackWebUrl("info https://127.0.0.1:3080/"));
        assertNull(AgentRepository.extractLoopbackWebUrl("info dsh web: ftp://127.0.0.1/session"));
    }

    /// Console output redacts both Provider and MCP credentials without altering ordinary text.
    @Test
    public void redactsAllActiveSecrets() {
        assertEquals("provider=[redacted] mcp=[redacted] plain=ok",
                AgentRepository.redactSecrets("provider=provider-fixture mcp=mcp-fixture plain=ok",
                        List.of("provider-fixture", "mcp-fixture")));
    }

    /// Generates DSH provider routing for supported protocols without serializing a secret value.
    @Test
    public void createsProviderYamlWithCredentialReference() throws Exception {
        String yaml = AgentRepository.providerProfileYaml("provider-a", "openai-responses",
                "https://api.openai.com/v1", "OPENAI_API_KEY", "gpt-5");
        assertTrue(yaml.contains("api: \"openai-responses\""));
        assertTrue(yaml.contains("apiKeyEnv: \"OPENAI_API_KEY\""));
        assertTrue(yaml.contains("model: \"gpt-5\""));
        assertThrows(IOException.class, () -> AgentRepository.providerProfileYaml("provider-a", "invalid",
                "https://example.test", "API_KEY", "model"));
        assertThrows(IOException.class, () -> AgentRepository.providerProfileYaml("provider-a", "anthropic-messages",
                "https://api.anthropic.com", "API-KEY", "model"));
        assertThrows(IOException.class, () -> AgentRepository.providerProfileYaml("provider-a",
                "openai-responses", "https://example.test", "NODE_OPTIONS", "model"));
    }

    /// Persisted instance environment entries cannot contain secrets or execution controls.
    @Test
    public void rejectsUnsafeInstanceEnvironmentNames() {
        assertThrows(IOException.class, () -> AgentRepository.validateInstanceEnvironmentName("OPENAI_API_KEY"));
        assertThrows(IOException.class, () -> AgentRepository.validateInstanceEnvironmentName("NODE_OPTIONS"));
        assertThrows(IOException.class, () -> AgentRepository.validateInstanceEnvironmentName("DSHCRAFT_TEST"));
        assertThrows(IOException.class, () -> AgentRepository.validateInstanceEnvironmentName("bad-name"));
    }

    /// A launcher-owned Profile follows protocol changes while keeping the credential as an env reference.
    @Test
    public void updatesManagedProviderProfileAfterProtocolChange() throws Exception {
        AgentProvider provider = new AgentProvider("fixture", "Fixture", "DeepSeek Official",
                "https://api.deepseek.com", "deepseek-flash", "DEEPSEEK_API_KEY", "openai-completions");
        AgentRepository.ensureProviderProfileConfig(temporary, "web", provider, "deepseek-flash");
        provider.protocolProperty().set("openai-responses");
        AgentRepository.ensureProviderProfileConfig(temporary, "web", provider, "deepseek-flash");
        String yaml = Files.readString(temporary.resolve("profiles/web/settings.yaml"));
        assertTrue(yaml.contains("api: \"openai-responses\""));
        assertTrue(yaml.contains("apiKeyEnv: \"DEEPSEEK_API_KEY\""));
    }

    /// Manual changes to a generated Profile remain untouched instead of being silently replaced.
    @Test
    public void preservesManuallyEditedProviderProfile() throws Exception {
        AgentProvider provider = new AgentProvider("fixture", "Fixture", "DeepSeek Official",
                "https://api.deepseek.com", "deepseek-flash", "DEEPSEEK_API_KEY", "openai-completions");
        AgentRepository.ensureProviderProfileConfig(temporary, "web", provider, "deepseek-flash");
        Path settings = temporary.resolve("profiles/web/settings.yaml");
        String manual = Files.readString(settings) + "# user setting\n";
        Files.writeString(settings, manual);
        provider.protocolProperty().set("openai-responses");
        assertThrows(IOException.class,
                () -> AgentRepository.ensureProviderProfileConfig(temporary, "web", provider, "deepseek-flash"));
        assertEquals(manual, Files.readString(settings));
    }
}
