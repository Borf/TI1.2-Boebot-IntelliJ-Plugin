package com.avans.boebotplugin.gui;

import com.avans.boebotplugin.gui.BoeBotControlFrame;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class BoeBotControlFrameFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        String rootPath = ModuleRootManager.getInstance(ModuleManager.getInstance(project).getModules()[0]).getContentRoots()[0].getPath();
        String projectName = project.getName();

        // __TODO__ find main class
        //RunConfigurationFactory runConfig = new RunConfigurationFactory(RunManager.getInstance(project), ModuleManager.getInstance(project).getModules()[0], "");
        //runConfig.chooseMainClassForProject(project);
        BoeBotControlFrame controlFrame = new BoeBotControlFrame(rootPath, projectName, project);

        ContentFactory cf = ContentFactory.SERVICE.getInstance();
        Content content = cf.createContent(controlFrame, "", true);

        toolWindow.getContentManager().addContent(content);
    }
}
