package org.giuantomcat.toolWindow;

import org.giuantomcat.toolWindow.model.TomcatApplication;
import org.giuantomcat.toolWindow.model.TomcatInstance;

/** Callbacks fired by the application rows of the server-manager tool window. */
public interface ApplicationActions {

  void start(TomcatInstance instance, TomcatApplication application);

  void stop(TomcatInstance instance, TomcatApplication application);

  void reload(TomcatInstance instance, TomcatApplication application);

  void undeploy(TomcatInstance instance, TomcatApplication application);
}
