/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 * Modifications for DShCraft direct HMCL fork Copyright (C) 2026 DShCraft contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.agent.AgentEntry;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;

/// HMCL-style display adapter for an Agent business entry.
@NotNullByDefault
public final class AgentListItem<T extends AgentEntry> {
    /// Business entry represented by this list item.
    private final T entry;
    /// HMCL built-in 32-pixel launcher image used in list rows.
    private final ReadOnlyObjectWrapper<Image> image = new ReadOnlyObjectWrapper<>();

    /// Creates a display adapter without introducing any custom CSS or layout.
    public AgentListItem(T entry) {
        this.entry = entry;
        image.set(FXUtils.newBuiltinImage("/assets/img/dshcraft-mark.png"));
    }

    /// Returns the backing business entry.
    public T getEntry() {
        return entry;
    }

    /// Returns the stable identifier used by filtering and configuration.
    public String getId() {
        return entry.getId();
    }

    /// Returns the HMCL row title property.
    public ReadOnlyStringProperty titleProperty() {
        return entry.nameProperty();
    }

    /// Returns the HMCL row subtitle property.
    public ReadOnlyStringProperty subtitleProperty() {
        return entry.summaryProperty();
    }

    /// Returns the HMCL row tag property.
    public ReadOnlyStringProperty tagProperty() {
        return entry.tagProperty();
    }

    /// Returns the HMCL row image property.
    public ReadOnlyObjectProperty<Image> imageProperty() {
        return image.getReadOnlyProperty();
    }
}
