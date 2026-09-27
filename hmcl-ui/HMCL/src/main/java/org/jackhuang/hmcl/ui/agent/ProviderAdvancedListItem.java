/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021 huangyuhui <huanghongxun2008@126.com> and contributors
 * Modifications for DShCraft direct HMCL fork Copyright (C) 2026 DShCraft contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import org.jackhuang.hmcl.agent.AgentProvider;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Provider identity row using the same 32-pixel left graphic and AdvancedListItem geometry as HMCL.
@NotNullByDefault
public final class ProviderAdvancedListItem extends AdvancedListItem {
    /// Fixed-size graphic container matching HMCL identity rows.
    private final ImageContainer imageContainer;

    /// Creates the provider identity row and binds it to the selected provider.
    public ProviderAdvancedListItem() {
        imageContainer = new ImageContainer(LEFT_GRAPHIC_SIZE);
        imageContainer.setMouseTransparent(true);
        AdvancedListItem.setAlignment(imageContainer, Pos.CENTER);
        imageContainer.setImage(FXUtils.newBuiltinImage("/assets/img/dshcraft-mark.png"));
        setLeftGraphic(imageContainer);

        AgentRepository repository = AgentRepository.get();
        titleProperty().bind(Bindings.createStringBinding(
                () -> {
                    @Nullable AgentProvider provider = repository.getSelectedProvider();
                    return provider == null ? i18n("agent.provider") : provider.getName();
                }, repository.selectedProviderProperty()));
        subtitleProperty().bind(Bindings.createStringBinding(
                () -> {
                    @Nullable AgentProvider provider = repository.getSelectedProvider();
                    return provider == null ? i18n("agent.provider.add") : provider.summaryProperty().get();
                }, repository.selectedProviderProperty()));
    }
}
