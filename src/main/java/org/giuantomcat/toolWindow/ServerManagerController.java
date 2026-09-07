package org.giuantomcat.toolWindow;

import org.giuantomcat.toolWindow.api.TomcatManagerClient;
import org.giuantomcat.toolWindow.model.TomcatApplication;
import org.giuantomcat.toolWindow.model.TomcatInstance;
import org.giuantomcat.toolWindow.settings.TomcatCredentialsStore;
import org.giuantomcat.toolWindow.settings.TomcatInstancesSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State and logic of the server-manager tool window, separated from the view construction
 * (see {@code STYLE.md}).
 *
 * <p>Holds the ordered list of managed instances (persisted at application level) plus the last
 * known snapshot of each instance: its deployed applications and, if the last refresh failed, the
 * error message to show on the corresponding tree node.
 */
public final class ServerManagerController {

  private final TomcatInstancesSettings mySettings;
  private final List<TomcatInstance> myInstances = new ArrayList<>();
  private final Map<String, List<TomcatApplication>> myApplicationsById = new LinkedHashMap<>();
  private final Map<String, String> myErrorsById = new LinkedHashMap<>();

  public ServerManagerController() {
    mySettings = TomcatInstancesSettings.getInstance();
    myInstances.addAll(mySettings.getInstances());
  }

  public List<TomcatInstance> getInstances() {
    return new ArrayList<>(myInstances);
  }

  public boolean isEmpty() {
    return myInstances.isEmpty();
  }

  public void addInstance(TomcatInstance instance) {
    myInstances.add(instance);
    persist();
  }

  public void updateInstance(TomcatInstance instance) {
    for (int i = 0; i < myInstances.size(); i++) {
      if (myInstances.get(i).id.equals(instance.id)) {
        myInstances.set(i, instance);
        break;
      }
    }
    persist();
  }

  public void removeInstance(String id) {
    myInstances.removeIf(instance -> instance.id.equals(id));
    myApplicationsById.remove(id);
    myErrorsById.remove(id);
    persist();
  }

  public TomcatInstance findById(String id) {
    for (TomcatInstance instance : myInstances) {
      if (instance.id.equals(id)) {
        return instance;
      }
    }
    return null;
  }

  // ---- per-instance runtime snapshot ----

  public void clearSnapshots() {
    myApplicationsById.clear();
    myErrorsById.clear();
  }

  public void setApplications(String instanceId, List<TomcatApplication> applications) {
    myApplicationsById.put(instanceId, new ArrayList<>(applications));
  }

  public List<TomcatApplication> getApplications(String instanceId) {
    List<TomcatApplication> applications = myApplicationsById.get(instanceId);
    return applications == null ? List.of() : applications;
  }

  public void setError(String instanceId, String error) {
    myErrorsById.put(instanceId, error);
  }

  public void clearError(String instanceId) {
    myErrorsById.remove(instanceId);
  }

  public String getError(String instanceId) {
    return myErrorsById.get(instanceId);
  }

  // ---- client factories ----

  public TomcatCredentialsStore credentials(TomcatInstance instance) {
    return new TomcatCredentialsStore(instance.id);
  }

  public TomcatManagerClient managerClient(TomcatInstance instance) {
    return new TomcatManagerClient(instance, credentials(instance).getManagerPassword());
  }

  private void persist() {
    mySettings.setInstances(myInstances);
  }
}
