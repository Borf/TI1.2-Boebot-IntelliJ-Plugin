import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class IntelliJHelper {

    private Project project;

    public IntelliJHelper(Project project) {
        this.project = project;
    }

    public List<String> getMainClasses() {
        /*Module module = ModuleManager.getInstance(this.project).getModules()[0];
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        moduleRootManager.*/

        VirtualFile[] vFiles = ProjectRootManager.getInstance(project).getContentSourceRoots();
        //String sourceRootsList = Arrays.stream(vFiles).map(VirtualFile::getUrl).collect(Collectors.joining("\n"));

        //Messages.showInfoMessage("Source roots for the " + project.getName() + " plugin:\n" + sourceRootsList, "Project Properties");

        return null;
    }
}
