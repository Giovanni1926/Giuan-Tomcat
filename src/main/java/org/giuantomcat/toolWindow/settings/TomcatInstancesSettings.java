package org.giuantomcat.toolWindow.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.giuantomcat.GiuanTomcatConstants;
import org.giuantomcat.toolWindow.model.TomcatInstance;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Application-level store of the managed Tomcat instances.
 *
 * <p>Persisted to {@code giuan-tomcat-instances.xml} under the IDE config folder. Only non-secret
 * instance fields are stored here; passwords are kept in {@code PasswordSafe}.
 */
@State(
    name = "GiuanTomcatInstances",
    storages = @Storage(GiuanTomcatConstants.INSTANCES_STORAGE_FILE))
public final class TomcatInstancesSettings implements PersistentStateComponent<TomcatInstancesSettings.State> {

  public static final class State {
    public List<TomcatInstance> instances = new ArrayList<>();
  }

  private State myState = new State();

  public static TomcatInstancesSettings getInstance() {
    return ApplicationManager.getApplication().getService(TomcatInstancesSettings.class);
  }

  @NotNull
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
    if (myState.instances == null) {
      myState.instances = new ArrayList<>();
    }
  }

  public List<TomcatInstance> getInstances() {
    return new ArrayList<>(myState.instances);
  }

  public void setInstances(List<TomcatInstance> instances) {
    myState.instances = new ArrayList<>(instances == null ? List.of() : instances);
  }

  public TomcatInstance findById(String id) {
    for (TomcatInstance instance : myState.instances) {
      if (instance.id.equals(id)) {
        return instance;
      }
    }
    return null;
  }
}
