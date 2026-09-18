package org.giuantomcat.runConfiguration.runner;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.giuantomcat.runConfiguration.settings.GiuanTomcatRunConfigurationOptions;
import org.giuantomcat.runConfiguration.ui.GiuanTomcatSettingsEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GiuanTomcatRunConfiguration
    extends RunConfigurationBase<GiuanTomcatRunConfigurationOptions>
    implements ModuleRunProfile {

  public GiuanTomcatRunConfiguration(@NotNull Project project,
                                     @NotNull ConfigurationFactory factory,
                                     @NotNull String name) {
    super(project, factory, name);
  }

  @NotNull
  @Override
  protected GiuanTomcatRunConfigurationOptions getOptions() {
    return (GiuanTomcatRunConfigurationOptions) super.getOptions();
  }

  /**
   * Paths are stored with IDE path macros (e.g. {@code $PROJECT_DIR$}) whenever possible, so
   * copying or moving the project does not leave stale absolute paths behind: getters expand the
   * macros, setters collapse them.
   */
  public String getCatalinaHome() {
    return expand(getOptions().getCatalinaHome());
  }

  public void setCatalinaHome(String catalinaHome) {
    getOptions().setCatalinaHome(collapse(catalinaHome));
  }

  public String getWebContent() {
    return expand(getOptions().getWebContent());
  }

  public void setWebContent(String webContent) {
    getOptions().setWebContent(collapse(webContent));
  }

  private String expand(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }
    return PathMacroManager.getInstance(getProject()).expandPath(path);
  }

  private String collapse(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }
    return PathMacroManager.getInstance(getProject()).collapsePath(path);
  }

  public String getContextPath() {
    return getOptions().getContextPath();
  }

  public void setContextPath(String contextPath) {
    getOptions().setContextPath(contextPath);
  }

  public String getHttpPort() {
    return getOptions().getHttpPort();
  }

  public void setHttpPort(String httpPort) {
    getOptions().setHttpPort(httpPort);
  }

  public String getShutdownPort() {
    return getOptions().getShutdownPort();
  }

  public void setShutdownPort(String shutdownPort) {
    getOptions().setShutdownPort(shutdownPort);
  }

  @NotNull
  public Set<String> getModuleNames() {
    Set<String> names = getOptions().getModuleNames();
    return names != null ? names : Collections.emptySet();
  }

  public void setModuleNames(Set<String> moduleNames) {
    getOptions().setModuleNames(moduleNames);
  }

  @NotNull
  public Set<String> getJarSkipTokens() {
    Set<String> tokens = getOptions().getJarSkipTokens();
    return tokens != null ? tokens : Collections.emptySet();
  }

  public void setJarSkipTokens(Set<String> jarSkipTokens) {
    getOptions().setJarSkipTokens(jarSkipTokens);
  }

  public boolean isSkipAnnotationScan() {
    return getOptions().isSkipAnnotationScan();
  }

  public void setSkipAnnotationScan(boolean skipAnnotationScan) {
    getOptions().setSkipAnnotationScan(skipAnnotationScan);
  }

  public boolean isHotSwapEnabled() {
    return getOptions().isHotSwapEnabled();
  }

  public void setHotSwapEnabled(boolean hotSwapEnabled) {
    getOptions().setHotSwapEnabled(hotSwapEnabled);
  }

  public String getDcevmJdkPath() {
    return getOptions().getDcevmJdkPath();
  }

  public void setDcevmJdkPath(String dcevmJdkPath) {
    getOptions().setDcevmJdkPath(dcevmJdkPath);
  }

  public String getHotswapAgentPath() {
    return getOptions().getHotswapAgentPath();
  }

  public void setHotswapAgentPath(String hotswapAgentPath) {
    getOptions().setHotswapAgentPath(hotswapAgentPath);
  }

  /**
   * Stable identity of this configuration, persisted in its options.
   *
   * <p>It is generated on first use and never changes, so the generated runtime folder (CATALINA_BASE,
   * merged tree, run lock) stays bound to this configuration even if it is renamed. If two
   * configurations end up sharing the same identity (a copy that kept the options, or a manually
   * duplicated project file), the one appearing later in the run manager list is assigned a fresh
   * identity, so two configurations with the same name never share the same instance.
   */
  @NotNull
  public String getRuntimeKey() {
    String runtimeId = getOptions().getRuntimeId();
    if (runtimeId == null || runtimeId.isBlank()) {
      runtimeId = newRuntimeId();
      getOptions().setRuntimeId(runtimeId);
      return runtimeId;
    }
    List<RunConfiguration> configurations =
        RunManager.getInstance(getProject()).getAllConfigurationsList();
    int myIndex = indexOfIdentity(configurations, this);
    if (myIndex < 0) {
      // Transient configuration (not registered in the run manager): keep the persisted identity.
      return runtimeId;
    }
    for (int i = 0; i < configurations.size(); i++) {
      RunConfiguration candidate = configurations.get(i);
      if (candidate == this || !(candidate instanceof GiuanTomcatRunConfiguration other)) {
        continue;
      }
      if (runtimeId.equals(other.getOptions().getRuntimeId()) && myIndex > i) {
        runtimeId = newRuntimeId();
        getOptions().setRuntimeId(runtimeId);
        System.out.println("[GiuanTomcat] configurazione omonima rilevata: nuovo runtime id per \""
            + getName() + "\"");
        return runtimeId;
      }
    }
    return runtimeId;
  }

  private static String newRuntimeId() {
    return UUID.randomUUID().toString();
  }

  private static int indexOfIdentity(List<RunConfiguration> configurations,
                                     RunConfiguration target) {
    for (int i = 0; i < configurations.size(); i++) {
      if (configurations.get(i) == target) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    validateSettings();
  }

  @Override
  public void checkSettingsBeforeRun() throws RuntimeConfigurationException {
    validateSettings();
  }

  /**
   * Fails fast with a clear message when the configured paths are missing, instead of letting
   * Tomcat fail later with an obscure "main resource set ... is not a directory" error.
   */
  private void validateSettings() throws RuntimeConfigurationException {
    String webContent = getWebContent();
    if (webContent == null || webContent.isBlank()) {
      throw new RuntimeConfigurationException("Specifica il campo \"Web content (docBase)\".");
    }
    if (!new File(webContent).isDirectory()) {
      throw new RuntimeConfigurationException("Web content non trovato: " + webContent
          + ". Aggiorna il campo \"Web content\" della run configuration.");
    }
    String catalinaHome = getCatalinaHome();
    if (catalinaHome == null || catalinaHome.isBlank()) {
      throw new RuntimeConfigurationException("Specifica il campo CATALINA_HOME.");
    }
    if (!new File(catalinaHome, "bin/bootstrap.jar").isFile()) {
      throw new RuntimeConfigurationException("CATALINA_HOME non valido (manca bin/bootstrap.jar): "
          + catalinaHome);
    }
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GiuanTomcatSettingsEditor(getProject());
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor,
                                  @NotNull ExecutionEnvironment environment) {
    return new GiuanTomcatCommandLineState(this, environment);
  }
}
