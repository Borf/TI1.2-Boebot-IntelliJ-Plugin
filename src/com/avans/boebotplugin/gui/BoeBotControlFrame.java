package com.avans.boebotplugin.gui;

import com.avans.boebotplugin.services.Settings;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.task.ProjectTaskManager;
import com.intellij.task.ProjectTaskNotification;
import com.intellij.task.ProjectTaskResult;
import com.jcraft.jsch.*;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

public class BoeBotControlFrame extends JPanel implements ActionListener {


    private String packageDirectory;
    private String outputRoot;

    private JComboBox<String> mainClass;
    private JComboBox<String> versions;
    private JTextArea log;
    private JLabel status;


    private Session session = null;
    private JSch jsch = null;
    private Channel execChannel = null;
    private InputStream execIn = null;
    private InputStream execErr = null;

    private Thread connectThread = null;
    private String projectName;
    private Project project;

    private ImageIcon statusConnected = new ImageIcon(getClass().getResource("/icons/status_connected.png"));
    private ImageIcon statusError = new ImageIcon(getClass().getResource("/icons/status_error.png"));

    public BoeBotControlFrame(final String packageDirectory, final String projectName, Project project) {
        this.project = project;
        if (this.project == null)
            throw new InvalidParameterException("Project cannot be null");

        String[] outputPaths = CompilerPaths.getOutputPaths(ModuleManager.getInstance(this.project).getModules());
        if (outputPaths.length == 0) {

            ApplicationManager.getApplication().invokeLater(() -> Messages.showDialog(
                    project,
                    "Please set an out path in your project preferences",
                    "Error opening panel",
                    "Please set an outpath in the project preferences and restart IntelliJ",
                    new String[]{Messages.getOkButton()},
                    0,
                    0,
                    Messages.getErrorIcon()
            ));
            return;
        }
        outputRoot = outputPaths[0];

        this.projectName = projectName.replace(' ', '_');
        this.packageDirectory = packageDirectory;
        setSize(800, 600);

        this.setLayout(new BorderLayout());

        this.add(new JScrollPane(log = new JTextArea()), BorderLayout.CENTER);
        log.setFont(new Font("Monospaced", Font.PLAIN, 14));
        log.setEditable(false);
        log.setBorder(BorderFactory.createEtchedBorder());
        DefaultCaret caret = (DefaultCaret) log.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));
        this.add(topPanel, BorderLayout.NORTH);

        topPanel.add(new JLabel("Status"));
        topPanel.add(this.status = new JLabel());
        topPanel.add(new JLabel("Main Class"));
        topPanel.add(this.mainClass = new JComboBox<String>(new String[]{""}));
        topPanel.add(new JLabel("Versions"));
        topPanel.add(this.versions = new JComboBox<String>());

        DumbService.getInstance(this.project).runWhenSmart(new Runnable() {
            @Override
            public void run() {

                findMainClass();
            }
        });

        final JButton uploadButton = new JButton("Upload");
        final JButton runButton = new JButton("Run");
        final JButton clearButton = new JButton("Clear");

        status.setIcon(statusError);
        uploadButton.setEnabled(false);
        runButton.setEnabled(false);
        clearButton.setEnabled(true);

        this.mainClass.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                findMainClass();
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });


        this.versions.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent arg0) {
                String selected = (String) versions.getSelectedItem();
                versions.removeAllItems();
                for (String item : getVersions())
                    versions.addItem(item);
                if (selected != null)
                    versions.setSelectedItem(selected);
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent arg0) {
            }

            public void popupMenuCanceled(PopupMenuEvent arg0) {
            }
        });


        topPanel.add(uploadButton);
        uploadButton.addActionListener(arg0 -> {
            log.setText("");
            ProjectTaskManager.getInstance(project).rebuild(ModuleManager.getInstance(project).getModules(), new ProjectTaskNotification() {
                @Override
                public void finished(@NotNull ProjectTaskResult projectTaskResult) {
                    log.append("Compilation result: " +
                            projectTaskResult.getErrors() + " errors, " +
                            projectTaskResult.getWarnings() + " warnings\n");
                    if (projectTaskResult.isAborted())
                        log.append("Compilation aborted!!!! Please check your code\n");
                    else
                        (new Thread() {
                            public void run() {
                                if (!BoeBotControlFrame.this.isVisible())
                                    return;

                                closeRunningApplication();
                                delay(100);

                                if (session.isConnected()) {
                                    DateFormat df = new SimpleDateFormat("yyyy-MM-dd_hh.mm.ss");
                                    Date now = Calendar.getInstance().getTime();
                                    String version = df.format(now);

                                    versions.addItem(version);
                                    versions.setSelectedItem(version);

                                    uploadFiles();
                                    runCode();
                                }
                            }
                        }).start();
                }
            });
        });

        topPanel.add(runButton);

        runButton.addActionListener(arg0 -> {
            if (session.isConnected()) {
                closeRunningApplication();
                log.setText("");
                runCode();
            }
        });

        topPanel.add(clearButton);
        clearButton.addActionListener(e -> {
            log.setText("");
        });

        new Timer(10, this).start();


        jsch = new JSch();
        session = null;


        connectThread = new Thread(() -> {
            while (true) {
                try {
                    if (session == null || !session.isConnected()) {
                        status.setIcon(statusError);
                        uploadButton.setEnabled(false);
                        runButton.setEnabled(false);


                        if (session != null)
                            session.disconnect();

                        String ip = ServiceManager.getService(project, Settings.class).ip;

                        System.out.println("Connecting to " + ip);
                        session = jsch.getSession("pi", ip);

                        session.setPassword("pi");
                        Properties config = new Properties();
                        config.put("StrictHostKeyChecking", "no");
                        session.setConfig(config);

                        session.connect(20000);

                        if (session.isConnected()) {
                            status.setIcon(statusConnected);
                            uploadButton.setEnabled(true);
                            runButton.setEnabled(true);
                        }
                    } else
                        session.sendKeepAliveMsg();

                } catch (JSchException e) {
                    e.printStackTrace();
                    session = null;
                } catch (Exception e) {
                    session = null;
                    e.printStackTrace();
                }
                delay(2000);
            }
        });
        connectThread.start();

        setVisible(true);
    }


    private void scanDir(PsiDirectory dir) {
        for (PsiDirectory sub : dir.getSubdirectories())
            scanDir(sub);

        System.out.println(dir.getName());
        for (PsiFile file : dir.getFiles()) {
            if (file instanceof PsiJavaFile) {
                PsiJavaFile javaFile = (PsiJavaFile) file;

                for (PsiClass clazz : javaFile.getClasses()) {
                    for (PsiMethod method : clazz.getAllMethods()) {
                        if (method.hasModifier(JvmModifier.STATIC) && method.getName().equals("main") && method.hasParameters()) {
                            String name = clazz.getQualifiedName();
                            boolean found = false;
                            for (int i = 0; i < this.mainClass.getItemCount(); i++)
                                if (this.mainClass.getItemAt(i).equals(name))
                                    found = true;

                            if (!found)
                                this.mainClass.addItem(clazz.getQualifiedName());
                        }
                    }
                }
            }

        }
    }

    private void findMainClass() {
        PsiDirectory root = PsiManager.getInstance(project).findDirectory(project.getBaseDir());
        this.mainClass.removeAllItems();
        scanDir(root);
    }

    private String getSelectedVersion() {
        Object selectedVersion = this.versions.getSelectedItem();
        if (selectedVersion == null)
            return null;
        return (String)selectedVersion;
    }

    void runCode() {
        runCode(false);
    }

    void runCode(boolean suspend) {
        try {
            if (this.execChannel != null) {
                this.execIn.close();
                this.execChannel.disconnect();
            }

            String command =
                    "cd /home/pi/upload/" + this.projectName + "/" + getSelectedVersion() + "; " +
                            "sudo killall -q java; " +
                            "sleep 0.5; " +
                            "echo " + this.projectName + "/" + getSelectedVersion() + " > /home/pi/upload/lastrun;\n" +
                            "echo " + this.mainClass.getSelectedItem() + " >> /home/pi/upload/lastrun;\n" +
                            "sudo java -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=" + (suspend ? "y" : "n") + " -cp \".:/home/pi/BoeBotLib/BoeBotLib.jar\" -Djava.library.path=/home/pi/BoeBotLib " + BoeBotControlFrame.this.mainClass.getSelectedItem();

            execChannel = session.openChannel("exec");
            ((ChannelExec) execChannel).setCommand(command);
            execChannel.setInputStream(null);
            // ((ChannelExec)channel).setErrStream(System.out);
            // ((ChannelExec)channel).setOutputStream(System.out);

            execIn = execChannel.getInputStream();
            execErr = ((ChannelExec) execChannel).getErrStream();


            execChannel.connect();
            log.append("Running " + mainClass.getSelectedItem() + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSchException e) {
            e.printStackTrace();
        }
    }

    //timer
    public void actionPerformed(ActionEvent arg0) {
        byte[] tmp = new byte[1024];

        if (execChannel != null && execChannel.isConnected()) {
            try {
                while (execIn.available() > 0) {
                    int i = execIn.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    if (!new String(tmp, 0, i).equals("Listening for transport dt_socket at address: 8000\n"))
                        log.append(new String(tmp, 0, i));
                }
                while (execErr.available() > 0) {
                    int i = execErr.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    log.append(new String(tmp, 0, i));
                }

                if (execChannel.isClosed()) {
                    if (execIn.available() > 0 && execErr.available() > 0)
                        return;
                    System.out.println("exit-status: "
                            + execChannel.getExitStatus());
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                execIn = null;
                execErr = null;
                execChannel.disconnect();
                session.disconnect();
                session = null;
            }
        }
    }


    void uploadFiles() {
        //TODO: use https://intellij-support.jetbrains.com/hc/en-us/community/posts/206800625-Implementing-a-ClassInstrumentingCompiler-how-to-get-the-generated-class-files
        //findMainClass();
        final ArrayList<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(Paths.get(packageDirectory), new FileVisitor<Path>() {
                public FileVisitResult postVisitDirectory(Path arg0, IOException arg1) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult preVisitDirectory(Path arg0, BasicFileAttributes arg1) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFile(Path arg0, BasicFileAttributes arg1) throws IOException {
                    if (arg0.toString().endsWith(".class") || arg0.toString().endsWith(".java"))
                        files.add(arg0);
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFileFailed(Path arg0, IOException arg1) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        String version = (String) versions.getSelectedItem();
        log.append("New version is called '" + version + "'\n");
        log.append("Uploading " + files.size() + " files....");
        mkdir("/home/pi/upload/" + projectName, session);
        mkdir("/home/pi/upload/" + projectName + "/" + version, session);

        String sourcePath = packageDirectory.replace('\\', '/') + "/src";
        String outputPath = outputRoot.replace('\\', '/');
        for (Path p : files) {
            String path = p.toString().replace('\\', '/');
            String fullPath = outputPath;
            if (path.contains(sourcePath))
                fullPath = sourcePath;

            System.out.println("Path: " + path);
            System.out.println("Full Path: " + fullPath);

            if (Paths.get(fullPath).relativize(p).getParent() != null) {
                mkdir("/home/pi/upload/" + projectName + "/" + version + "/" + Paths.get(fullPath).relativize(p).getParent().toString().replace('\\', '/'), session);
                sendFile(p.toString(), "/home/pi/upload/" + projectName + "/" + version + "/" + Paths.get(fullPath).relativize(p).toString().replace('\\', '/'), session);
            } else {
                sendFile(p.toString(), "/home/pi/upload/" + projectName + "/" + version + "/" + p.getFileName(), session);
            }
            log.append(".");
        }
        log.append("done\n");
    }


    private void mkdir(String path, Session session) {
        //statusLog.append("Making path " + path + "\n");

        String command = "mkdir -p " + path;
        try {
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            channel.connect();
            while (channel.isConnected())
                delay(1);

            channel.disconnect();

        } catch (JSchException e) {
            e.printStackTrace();
        }


    }


    void sendFile(String lFile, String rFile, Session session) {
        try {
            //statusLog.append("Uploading " + lFile + " to " + rFile + "\n"); // out.flush();
            boolean ptimestamp = true;

            // exec 'scp -t rFile' remotely
            String command = "scp " + (ptimestamp ? "-p" : "") + " -t '" + rFile + "'";
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            if (checkAck(in) != 0) {
                log.append("Checkack for sending file returned true");
                return;
            }

            File _lfile = new File(lFile);

            if (ptimestamp) {
                command = "T" + (_lfile.lastModified() / 1000) + " 0";
                // The access time should be sent here,
                // but it is not accessible with JavaAPI ;-<
                command += (" " + (_lfile.lastModified() / 1000) + " 0\n");
                out.write(command.getBytes());
                out.flush();
                if (checkAck(in) != 0) {
                    log.append("Checkack did not return 0\n");
                    return;
                }
            }

            // send "C0644 filesize filename", where filename should not
            // include '/'
            long filesize = _lfile.length();
            command = "C0666 " + filesize + " ";
            if (lFile.lastIndexOf('/') > 0) {
                command += "'" + lFile.substring(lFile.lastIndexOf('/') + 1) + "'";
            } else if (lFile.lastIndexOf('\\') > 0) {
                command += "'" + lFile.substring(lFile.lastIndexOf('\\') + 1) + "'";
            } else {
                command += "'" + lFile + "'";
            }
            command += "\n";

            out.write(command.getBytes());
            out.flush();
            if (checkAck(in) != 0) {
                return;
            }

            // send a content of lFile
            FileInputStream fis;
            fis = new FileInputStream(lFile);

            byte[] buf = new byte[1024];
            while (true) {
                int len = fis.read(buf, 0, buf.length);
                if (len <= 0)
                    break;
                out.write(buf, 0, len); // out.flush();
            }
            fis.close();
            fis = null;
            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();
            if (checkAck(in) != 0) {
                log.append("\n\nAck is not 0\n\n\n");
                return;
            }
            out.close();

            channel.disconnect();

        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSchException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        // 1 for error,
        // 2 for fatal error,
        // -1
        if (b == 0)
            return b;
        if (b == -1)
            return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            } while (c != '\n');
            if (b == 1) { // error
                log.append(sb.toString()); // out.flush();
            }
            if (b == 2) { // fatal error
                log.append(sb.toString()); // out.flush();
            }
        }
        return b;
    }


    void delay(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public ArrayList<String> getVersions() {
        ArrayList<String> result = new ArrayList<String>();

        String command = "ls /home/pi/upload/" + projectName;
        try {
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            // get I/O streams for remote scp
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();
            channel.connect();

            long timeout = System.currentTimeMillis() + 1000;

            while (in.available() == 0 && System.currentTimeMillis() < timeout)
                delay(1);

            String name = "";
            while (in.available() > 0) {
                int i = in.read();
                if (i == '\n') {
                    result.add(name);
                    name = "";
                } else
                    name += (char) i;
            }
            if (name != "")
                result.add(name);

            channel.disconnect();

        } catch (JSchException | IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private void closeRunningApplication() {
        try {
            String ip = ServiceManager.getService(project, Settings.class).ip;

            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, 9999), 500);
            socket.getOutputStream().write(0);
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
