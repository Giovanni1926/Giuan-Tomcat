package org.giuantomcat.runConfiguration.runner;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.giuantomcat.runConfiguration.settings.GiuanTomcatRunConfigurationOptions;
import org.giuantomcat.runConfiguration.ui.GiuanTomcatSettingsEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

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

  public String getCatalinaHome() {
    return getOptions().getCatalinaHome();
  }

  public void setCatalinaHome(String catalinaHome) {
    getOptions().setCatalinaHome(catalinaHome);
  }

  public String getCatalinaBase() {
    return getOptions().getCatalinaBase();
  }

  public void setCatalinaBase(String catalinaBase) {
    getOptions().setCatalinaBase(catalinaBase);
  }

  public String getWebContent() {
    return getOptions().getWebContent();
  }

  public void setWebContent(String webContent) {
    getOptions().setWebContent(webContent);
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
  public Set<String> getModulesSkipJarScan() {
    Set<String> names = getOptions().getModulesSkipJarScan();
    return names != null ? names : Collections.emptySet();
  }

  public void setModulesSkipJarScan(Set<String> modulesSkipJarScan) {
    getOptions().setModulesSkipJarScan(modulesSkipJarScan);
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
