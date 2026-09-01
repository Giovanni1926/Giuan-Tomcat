package org.giuantomcat.runConfiguration;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;

public final class GiuanTomcatConfigurationType extends ConfigurationTypeBase {

  public static final String ID = "GiuanTomcatRunConfiguration";

  public GiuanTomcatConfigurationType() {
    super(ID, "Giuan Tomcat", "Giuan Tomcat run configuration type",
        NotNullLazyValue.createValue(() ->
            IconLoader.getIcon("/icons/tomcat.svg", GiuanTomcatConfigurationType.class)));
    addFactory(new GiuanTomcatConfigurationFactory(this));
  }
}
