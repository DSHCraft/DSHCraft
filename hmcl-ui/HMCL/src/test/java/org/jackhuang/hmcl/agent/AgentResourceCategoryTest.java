/* DSHCraft contributors. GPL-3.0-or-later. */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/// Ensures distinct resource lifecycles never leak into each other's management category.
@NotNullByDefault
public final class AgentResourceCategoryTest {
    /// A plugin, optional Bundle, MCP server and imported skill have separate membership.
    @Test
    public void separatesUserResources() {
        for (String type : new String[]{"Plugin", "Bundle", "MCP", "Skill"}) {
            AgentExtension entry = new AgentExtension("fixture-" + type, type, type, "", false);
            assertEquals(type.equals("Plugin") || type.equals("Bundle"), entry.belongsToCategory("Plugin"));
            assertEquals(type.equals("MCP"), entry.belongsToCategory("MCP"));
            assertEquals(type.equals("Skill"), entry.belongsToCategory("Skill"));
        }
    }

    /// Capability markers cannot appear as installable packages, servers or imported skills.
    @Test
    public void excludesCoreCapabilities() {
        for (String id : new String[]{"filesystem", "browser", "skills", "mcp-client"}) {
            for (String type : new String[]{"Plugin", "MCP", "Skill"}) {
                AgentExtension entry = new AgentExtension(id, id, type, "", false);
                assertTrue(entry.isBuiltin());
                assertFalse(entry.belongsToCategory(type));
            }
        }
    }
}
