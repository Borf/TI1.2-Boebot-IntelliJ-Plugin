import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowManager;

public class HelloAction extends AnAction {
    public HelloAction() {
        super("Hello");
    }
    public static AnActionEvent event;
    public void actionPerformed(AnActionEvent event) {
        HelloAction.event = event;
        Project project = event.getProject();
//        Messages.showMessageDialog(project, "Hello world!", "Greeting", Messages.getInformationIcon());

        if (project == null) {
            return;
        }

        ToolWindowManager.getInstance(project).getToolWindow("Boebot Uploader").show(null);

    }
}