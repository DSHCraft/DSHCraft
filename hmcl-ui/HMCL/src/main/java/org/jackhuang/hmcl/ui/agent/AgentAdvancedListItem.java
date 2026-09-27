/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020 huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.agent.AgentInstance;
import org.jackhuang.hmcl.agent.AgentRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Current-Agent identity row using the same AdvancedListItem proportions as HMCL GameAdvancedListItem.
@NotNullByDefault
public final class AgentAdvancedListItem extends AdvancedListItem {
    /// Fixed-size image container matching HMCL's current-instance row.
    private final ImageContainer imageContainer;

    /// Creates the current-Agent row and binds it to the selected Agent instance.
    public AgentAdvancedListItem() {
        imageContainer = new ImageContainer(LEFT_GRAPHIC_SIZE);
        imageContainer.setMouseTransparent(true);
        AdvancedListItem.setAlignment(imageContainer, Pos.CENTER);
        imageContainer.setImage(FXUtils.newBuiltinImage("/assets/img/dshcraft-mark.png"));
        setLeftGraphic(imageContainer);

        AgentRepository repository = AgentRepository.get();
        titleProperty().bind(Bindings.createStringBinding(
                () -> repository.getSelectedInstance() == null ? i18n("agent.instance") : i18n("agent.instance.current"),
                repository.selectedInstanceProperty()));
        subtitleProperty().bind(Bindings.createStringBinding(
                () -> {
                    @Nullable AgentInstance instance = repository.getSelectedInstance();
                    return instance == null ? i18n("agent.instance.add") : instance.getName();
                }, repository.selectedInstanceProperty()));
    }
}
