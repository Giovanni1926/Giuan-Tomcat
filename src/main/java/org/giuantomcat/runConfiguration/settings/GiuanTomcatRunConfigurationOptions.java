package org.giuantomcat.runConfiguration.settings;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;
import org.giuantomcat.GiuanTomcatConstants;

import java.util.Set;

public class GiuanTomcatRunConfigurationOptions extends RunConfigurationOptions {

  private final StoredProperty<String> myCatalinaHome =
      string("").provideDelegate(this, "catalinaHome");

  private final StoredProperty<String> myWebContent =
      string("").provideDelegate(this, "webContent");

  private final StoredProperty<String> myContextPath =
      string(GiuanTomcatConstants.DEFAULT_CONTEXT_PATH).provideDelegate(this, "contextPath");

  private final StoredProperty<String> myHttpPort =
      string(GiuanTomcatConstants.DEFAULT_HTTP_PORT).provideDelegate(this, "httpPort");

  private final StoredProperty<String> myShutdownPort =
      string(GiuanTomcatConstants.DEFAULT_SHUTDOWN_PORT).provideDelegate(this, "shutdownPort");

  private final StoredProperty<Set<String>> myModuleNames =
      stringSet().provideDelegate(this, "moduleNames");

  private final StoredProperty<Set<String>> myJarSkipTokens =
      stringSet().provideDelegate(this, "jarSkipTokens");

  private final StoredProperty<Boolean> mySkipAnnotationScan =
      property(false).provideDelegate(this, "skipAnnotationScan");

  private final StoredProperty<Boolean> myHotSwapEnabled =
      property(false).provideDelegate(this, "hotSwapEnabled");

  private final StoredProperty<String> myDcevmJdkPath =
      string("").provideDelegate(this, "dcevmJdkPath");

  private final StoredProperty<String> myHotswapAgentPath =
      string("").provideDelegate(this, "hotswapAgentPath");

  public String getCatalinaHome() {
    return myCatalinaHome.getValue(this);
  }

  public void setCatalinaHome(String catalinaHome) {
    myCatalinaHome.setValue(this, catalinaHome);
  }

  public String getWebContent() {
    return myWebContent.getValue(this);
  }

  public void setWebContent(String webContent) {
    myWebContent.setValue(this, webContent);
  }

  public String getContextPath() {
    return myContextPath.getValue(this);
  }

  public void setContextPath(String contextPath) {
    myContextPath.setValue(this, contextPath);
  }

  public String getHttpPort() {
    return myHttpPort.getValue(this);
  }

  public void setHttpPort(String httpPort) {
    myHttpPort.setValue(this, httpPort);
  }

  public String getShutdownPort() {
    return myShutdownPort.getValue(this);
  }

  public void setShutdownPort(String shutdownPort) {
    myShutdownPort.setValue(this, shutdownPort);
  }

  public Set<String> getModuleNames() {
    return myModuleNames.getValue(this);
  }

  public void setModuleNames(Set<String> moduleNames) {
    myModuleNames.setValue(this, moduleNames);
  }

  public Set<String> getJarSkipTokens() {
    return myJarSkipTokens.getValue(this);
  }

  public void setJarSkipTokens(Set<String> jarSkipTokens) {
    myJarSkipTokens.setValue(this, jarSkipTokens);
  }

  public boolean isSkipAnnotationScan() {
    return mySkipAnnotationScan.getValue(this);
  }

  public void setSkipAnnotationScan(boolean skipAnnotationScan) {
    mySkipAnnotationScan.setValue(this, skipAnnotationScan);
  }

  public boolean isHotSwapEnabled() {
    return myHotSwapEnabled.getValue(this);
  }

  public void setHotSwapEnabled(boolean hotSwapEnabled) {
    myHotSwapEnabled.setValue(this, hotSwapEnabled);
  }

  public String getDcevmJdkPath() {
    return myDcevmJdkPath.getValue(this);
  }

  public void setDcevmJdkPath(String dcevmJdkPath) {
    myDcevmJdkPath.setValue(this, dcevmJdkPath);
  }

  public String getHotswapAgentPath() {
    return myHotswapAgentPath.getValue(this);
  }

  public void setHotswapAgentPath(String hotswapAgentPath) {
    myHotswapAgentPath.setValue(this, hotswapAgentPath);
  }
}
