/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies market filtering and raw-result paging before npm packages reach the install UI.
@NotNullByDefault
public class AgentPluginMarketTest {
    /// Keeps valid third-party and optional official packages while excluding Core internals.
    @Test
    public void filtersMarketResultsWithoutLosingPagination() throws IOException {
        String response = """
                {"total":125,"objects":[
                  {"package":{"name":"@deepseek-ai/dsh","version":"0.1.7","keywords":["dsh"]}},
                  {"package":{"name":"@deepseek-ai/dsh-web-app","version":"0.1.7"}},
                  {"package":{"name":"@deepseek-ai/dsh-subagent-codex","version":"0.1.7"}},
                  {"package":{"name":"@vendor/dsh-history","version":"2.1.0","description":"History","keywords":["dsh-plugin"]}},
                  {"package":{"name":"unrelated","version":"1.0.0"}},
                  {"package":{"name":"evil/../../path","version":"1.0.0","keywords":["dsh"]}},
                  {"package":"malformed"}
                ]}
                """;
        AgentNetworkService.NpmSearchResult result = AgentNetworkService.parseNpmSearch(response, 40);
        assertEquals(125, result.total());
        assertEquals(List.of("@deepseek-ai/dsh-subagent-codex", "@vendor/dsh-history"),
                result.plugins().stream().map(AgentNetworkService.NpmPlugin::name).toList());
        assertEquals("@vendor/dsh-history@2.1.0", result.plugins().get(1).packageSpec());
    }

    /// Malformed responses fail as IO errors that the UI can display and retry.
    @Test
    public void rejectsMalformedSearchResponses() {
        assertThrows(IOException.class, () -> AgentNetworkService.parseNpmSearch("{", 0));
        assertThrows(IOException.class, () -> AgentNetworkService.parseNpmSearch(
                "{\"total\":\"bad\",\"objects\":[]}", 0));
    }

    /// Rejects catalog-only npm packages while accepting versions with the upstream DSH Bundle contract.
    @Test
    public void requiresBundleMetadataForThirdPartyVersions() throws IOException {
        String response = """
                {"versions":{
                  "1.0.0":{"dsh":{"bundle":{"patch":"./cordis.patch.yml"}}},
                  "1.1.0":{"description":"npm catalog only"},
                  "1.2.0":{"dsh":{"bundle":{"patch":""}}}
                }}
                """;
        assertEquals(List.of("1.0.0"), AgentNetworkService.parsePluginVersions(response, "third-party-dsh"));
        assertEquals(List.of("1.2.0", "1.1.0", "1.0.0"),
                AgentNetworkService.parsePluginVersions(response, "@deepseek-ai/dsh-subagent-codex"));
    }
}
