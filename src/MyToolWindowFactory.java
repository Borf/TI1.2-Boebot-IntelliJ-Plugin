import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MyToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        BoeBotControlFrame controlFrame = new BoeBotControlFrame("", "", "mainclass");

        ContentFactory cf = ContentFactory.SERVICE.getInstance();
        Content content = cf.createContent(controlFrame, "", true);

        toolWindow.getContentManager().addContent(content);
    }
}
