package org.giuantomcat.runConfiguration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.giuantomcat.runConfiguration.runner.GiuanTomcatRunConfiguration;
import org.giuantomcat.runConfiguration.settings.GiuanTomcatRunConfigurationOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GiuanTomcatConfigurationFactory extends ConfigurationFactory {

  protected GiuanTomcatConfigurationFactory(ConfigurationType type) {
    super(type);
  }

  @Override
  public @NotNull String getId() {
    return GiuanTomcatConfigurationType.ID;
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new GiuanTomcatRunConfiguration(project, this, "Giuan Tomcat");
  }

  @Nullable
  @Override
  public Class<? extends BaseState> getOptionsClass() {
    return GiuanTomcatRunConfigurationOptions.class;
  }
}
