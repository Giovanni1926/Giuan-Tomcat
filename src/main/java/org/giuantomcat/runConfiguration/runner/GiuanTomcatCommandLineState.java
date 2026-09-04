package org.giuantomcat.runConfiguration.runner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.progress.ProgressManager;
import org.giuantomcat.tomcat.CatalinaBaseGenerator;
import org.giuantomcat.tomcat.ClasspathResolver;
import org.giuantomcat.tomcat.GiuanTomcatPaths;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class GiuanTomcatCommandLineState extends JavaCommandLineState {

  private static final String BOOTSTRAP_JAR = "bin/bootstrap.jar";
  private static final String TOMCAT_JULI_JAR = "bin/tomcat-juli.jar";
  private static final String CATALINA_BOOTSTRAP_MAIN = "org.apache.catalina.startup.Bootstrap";
  private static final String BOOTSTRAP_START_ARG = "start";
  private static final String LOG_MANAGER = "org.apache.juli.ClassLoaderLogManager";

  private final GiuanTomcatRunConfiguration myConfiguration;

  public GiuanTomcatCommandLineState(@NotNull GiuanTomcatRunConfiguration configuration,
                                     @NotNull ExecutionEnvironment environment) {
    super(environment);
    myConfiguration = configuration;
  }

  @NotNull
  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    Project project = myConfiguration.getProject();
    String catalinaHome = myConfiguration.getCatalinaHome();
    File catalinaBase = GiuanTomcatPaths.catalinaBase(project, myConfiguration.getName());

    ClasspathResolver.Classpath classpath =
        ClasspathResolver.resolve(project, myConfiguration.getModuleNames(),
            myConfiguration.getJarSkipTokens());

    try {
      CatalinaBaseGenerator.generate(
          catalinaHome, project, myConfiguration.getName(),
          myConfiguration.getWebContent(), myConfiguration.getContextPath(),
          myConfiguration.getHttpPort(), myConfiguration.getShutdownPort(),
          myConfiguration.isSkipAnnotationScan(),
          classpath,
          ProgressManager.getInstance().getProgressIndicator());
    } catch (IOException e) {
      throw new ExecutionException("Failed to generate catalina base", e);
    }

    JavaParameters parameters = new JavaParameters();
    parameters.setWorkingDirectory(catalinaBase.getAbsolutePath());
    parameters.getClassPath().add(new File(catalinaHome, BOOTSTRAP_JAR).getAbsolutePath());
    parameters.getClassPath().add(new File(catalinaHome, TOMCAT_JULI_JAR).getAbsolutePath());

    for (String dir : classpath.classesDirs) {
      parameters.getClassPath().add(dir);
    }

    parameters.getVMParametersList().addProperty("catalina.home", catalinaHome);
    parameters.getVMParametersList().addProperty("catalina.base", catalinaBase.getAbsolutePath());
    parameters.getVMParametersList().addProperty("java.util.logging.manager", LOG_MANAGER);
    parameters.getVMParametersList().addProperty("java.util.logging.config.file",
        new File(catalinaBase, "conf/logging.properties").getAbsolutePath());

    parameters.setMainClass(CATALINA_BOOTSTRAP_MAIN);
    parameters.getProgramParametersList().add(BOOTSTRAP_START_ARG);

    Sdk jdk = null;
    String dcevmJdkPath = null;
    if (myConfiguration.isHotSwapEnabled()) {
      String agentPath = myConfiguration.getHotswapAgentPath();
      if (isBlank(agentPath)) {
        throw new ExecutionException(
            "HotSwap is enabled but hotswap-agent.jar is not configured. Open the run configuration and set it up.");
      }
      File agentFile = new File(agentPath);
      if (!agentFile.isFile()) {
        throw new ExecutionException("hotswap-agent.jar non trovato: " + agentPath
            + ". Scaricalo da https://github.com/HotswapProjects/HotswapAgent/releases "
            + "e selezionalo nella configurazione HotSwap.");
      }
      boolean isDebug =
          DefaultDebugExecutor.EXECUTOR_ID.equals(getEnvironment().getExecutor().getId());
      parameters.getVMParametersList().add(
          "-javaagent:" + agentPath + (isDebug ? "" : "=autoHotswap=true"));
      parameters.getVMParametersList().add("-XXaltjvm=dcevm");
      if (!classpath.classesDirs.isEmpty()) {
        parameters.getVMParametersList().addProperty("hotswap.extraClasspath",
            String.join(File.pathSeparator, classpath.classesDirs));
        generateAgentProperties(classpath.classesDirs.get(0), catalinaBase, !isDebug);
      }
      dcevmJdkPath = myConfiguration.getDcevmJdkPath();
    }

    if (!isBlank(dcevmJdkPath)) {
      jdk = resolveJdk(dcevmJdkPath);
    }
    if (jdk == null) {
      jdk = ProjectRootManager.getInstance(project).getProjectSdk();
    }
    if (jdk == null) {
      jdk = findModuleSdk(project);
    }
    if (jdk != null) {
      parameters.setJdk(jdk);
    }

    return parameters;
  }

  private Sdk resolveJdk(String homePath) {
    String normalized = new File(homePath).getAbsolutePath();
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      String sdkHome = sdk.getHomePath();
      if (sdkHome != null && new File(sdkHome).getAbsolutePath().equalsIgnoreCase(normalized)) {
        return sdk;
      }
    }
    try {
      return JavaSdk.getInstance().createJdk("DCEVM JDK", homePath);
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static void generateAgentProperties(String classesDir, File catalinaBase,
                                              boolean autoHotswap) {
    try {
      File props = new File(classesDir, "hotswap-agent.properties");
      String logFile = new File(catalinaBase, "logs/hotswap-agent.log").getAbsolutePath()
          .replace('\\', '/');
      String content = "LOGGER=debug\nLOGFILE=" + logFile + "\n"
          + "disabledPlugins=proxy,ClassInitPlugin,AnonymousClassPatch\n";
      if (autoHotswap) {
        content += "autoHotswap=true\n";
      }
      java.nio.file.Files.write(props.toPath(),
          content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException ignored) {
      // diagnostics only
    }
  }

  private Sdk findModuleSdk(Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (myConfiguration.getModuleNames().contains(module.getName())) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null) {
          return sdk;
        }
      }
    }
    return null;
  }
}
