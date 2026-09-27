/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022 huangyuhui <huanghongxun2008@126.com> and contributors
 * Modifications for DShCraft direct HMCL fork Copyright (C) 2026 DShCraft contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui.agent;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Direct Agent-content adaptation of HMCL AboutPage's SpinnerPane and ComponentList hierarchy.
@NotNullByDefault
public final class AgentAboutPane extends SpinnerPane {
    /// Creates an about page whose layout controls are copied from HMCL AboutPage.
    public AgentAboutPane() {
        VBox content = new VBox();
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        ComponentList about = new ComponentList();

        LineButton launcher = new LineButton();
        launcher.setLargeTitle(true);
        launcher.setLeading(FXUtils.newBuiltinImage("/assets/img/dshcraft-mark.png"), 48);
        launcher.setTitle(Metadata.FULL_NAME);
        launcher.setSubtitle(Metadata.VERSION);

        LineButton upstream = LineButton.createExternalLinkButton("https://github.com/HMCL-dev/HMCL");
        upstream.setLargeTitle(true);
        upstream.setLeading(SVG.SCRIPT);
        upstream.setTitle("Hello Minecraft! Launcher");
        upstream.setSubtitle(i18n("agent.about.hmcl_fork"));
        about.getContent().setAll(launcher, upstream);

        ComponentList legal = new ComponentList();
        LineButton license = LineButton.createExternalLinkButton(Metadata.PUBLISH_URL);
        license.setLargeTitle(true);
        license.setTitle(i18n("about.open_source"));
        license.setSubtitle("DSHCraft GPL-3.0-or-later; upstream HMCL notices retained");
        legal.getContent().setAll(license);

        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("about")),
                about,
                ComponentList.createComponentListTitle(i18n("about.legal")),
                legal);
    }
}
