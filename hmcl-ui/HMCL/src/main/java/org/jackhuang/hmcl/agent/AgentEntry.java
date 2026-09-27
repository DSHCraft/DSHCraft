/*
 * DShCraft direct HMCL fork
 * Copyright (C) 2026 DShCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import org.jetbrains.annotations.NotNullByDefault;

import javafx.beans.property.StringProperty;

/** Common display contract for HMCL-native Agent list pages. */
@NotNullByDefault
public interface AgentEntry {
    StringProperty idProperty();

    StringProperty nameProperty();

    StringProperty summaryProperty();

    StringProperty tagProperty();

    default String getId() {
        return idProperty().get();
    }

    default String getName() {
        return nameProperty().get();
    }
}
