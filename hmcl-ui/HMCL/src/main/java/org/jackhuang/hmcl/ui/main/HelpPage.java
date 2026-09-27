/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.main;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class HelpPage extends SpinnerPane {

    private final VBox content;

    public HelpPage() {
        content = new VBox();
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        ComponentList doc = new ComponentList();
        var launcher = LineButton.createExternalLinkButton(Metadata.PUBLISH_URL + "/blob/main/README.md");
        launcher.setLargeTitle(true);
        launcher.setTitle("DSHCraft");
        launcher.setSubtitle(i18n("help.dshcraft.detail"));
        var dsh = LineButton.createExternalLinkButton("https://deepseek-harness.github.io/deepseek-harness/");
        dsh.setLargeTitle(true);
        dsh.setTitle(i18n("help.dsh.docs"));
        dsh.setSubtitle(i18n("help.dsh.docs.detail"));
        var issues = LineButton.createExternalLinkButton(Metadata.CONTACT_URL);
        issues.setLargeTitle(true);
        issues.setTitle(i18n("contact.feedback.github"));
        issues.setSubtitle(i18n("contact.feedback.github.statement"));
        doc.getContent().setAll(launcher, dsh, issues);
        content.getChildren().setAll(ComponentList.createComponentListTitle(i18n("help.doc")), doc);
    }
}
