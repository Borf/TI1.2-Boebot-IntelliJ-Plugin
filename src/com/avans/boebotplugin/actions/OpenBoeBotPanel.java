package com.avans.boebotplugin.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;

public class OpenBoeBotPanel extends AnAction {
    public OpenBoeBotPanel() {
        super("Open BoeBot Panel");
    }
    public static AnActionEvent event;
    public void actionPerformed(AnActionEvent event) {
        OpenBoeBotPanel.event = event;
        Project project = event.getProject();
//        Messages.showMessageDialog(project, "Hello world!", "Greeting", Messages.getInformationIcon());

        if (project == null) {
            return;
        }

        ToolWindowManager.getInstance(project).getToolWindow("Boebot Uploader").show(null);

    }
}