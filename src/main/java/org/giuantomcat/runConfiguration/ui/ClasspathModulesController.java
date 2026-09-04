package org.giuantomcat.runConfiguration.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.giuantomcat.runConfiguration.settings.SkipTokens;
import org.giuantomcat.tomcat.ModuleDependencies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stato e logica della selezione moduli + skip granulare (separato dalla costruzione della view). */
public final class ClasspathModulesController {

  private final Project myProject;
  private final List<Module> mySelected = new ArrayList<>();
  private final Set<String> mySkipTokens = new LinkedHashSet<>();
  private String myFocusModuleName = null;

  public ClasspathModulesController(Project project) {
    myProject = project;
  }

  public void loadState(Set<String> selectedNames, Set<String> skipTokens) {
    mySelected.clear();
    if (selectedNames != null) {
      for (String name : selectedNames) {
        Module module = findModule(name);
        if (module != null && !containsModule(name)) {
          mySelected.add(module);
        }
      }
    }
    mySkipTokens.clear();
    if (skipTokens != null) {
      for (String token : skipTokens) {
        // scarta token malformati o residui di formati rimossi (es. annotation per-modulo)
        if (SkipTokens.Parsed.of(token) != null) {
          mySkipTokens.add(token);
        }
      }
    }
  }

  public Set<String> selectedNames() {
    Set<String> names = new LinkedHashSet<>();
    for (Module module : mySelected) {
      names.add(module.getName());
    }
    return names;
  }

  public Set<String> skipTokens() {
    return new LinkedHashSet<>(mySkipTokens);
  }

  public List<Module> getSelectedModules() {
    return new ArrayList<>(mySelected);
  }

  public boolean containsModule(String name) {
    for (Module module : mySelected) {
      if (module.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  public List<Module> availableModules() {
    List<Module> result = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (!containsModule(module.getName())) {
        result.add(module);
      }
    }
    return result;
  }

  public void addToSelected(Collection<Module> modules) {
    if (modules == null) {
      return;
    }
    for (Module module : modules) {
      if (module != null && !containsModule(module.getName())) {
        mySelected.add(module);
      }
    }
  }

  public void removeFromSelected(Collection<String> moduleNames) {
    if (moduleNames == null) {
      return;
    }
    for (String name : moduleNames) {
      mySelected.removeIf(module -> module.getName().equals(name));
      mySkipTokens.removeIf(token -> {
        SkipTokens.Parsed parsed = SkipTokens.Parsed.of(token);
        return parsed != null && parsed.module().equals(name);
      });
    }
    if (myFocusModuleName != null && !containsModule(myFocusModuleName)) {
      myFocusModuleName = null;
    }
  }

  public void removeAllSelected() {
    mySelected.clear();
    mySkipTokens.clear();
    myFocusModuleName = null;
  }

  public void setFocusModuleName(String moduleName) {
    myFocusModuleName = moduleName;
  }

  /** Modulo correntemente selezionato per il pannello skip, o il primo selezionato. */
  public Module getFocusModule() {
    if (myFocusModuleName != null) {
      for (Module module : mySelected) {
        if (module.getName().equals(myFocusModuleName)) {
          return module;
        }
      }
    }
    return mySelected.isEmpty() ? null : mySelected.get(0);
  }

  private Module findModule(String name) {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (module.getName().equals(name)) {
        return module;
      }
    }
    return null;
  }

  public Module resolveModule(String name) {
    return findModule(name);
  }

  // ---- flag granulari ----

  public boolean isTldSkipped(Module module, String jarName) {
    return mySkipTokens.contains(SkipTokens.jarToken(module.getName(), jarName, SkipTokens.FLAG_TLD));
  }

  public boolean isPluggableSkipped(Module module, String jarName) {
    return mySkipTokens.contains(
        SkipTokens.jarToken(module.getName(), jarName, SkipTokens.FLAG_PLUGGABLE));
  }

  public void setTldSkipped(Module module, String jarName, boolean skip) {
    setJarFlag(module.getName(), jarName, SkipTokens.FLAG_TLD, skip);
  }

  public void setPluggableSkipped(Module module, String jarName, boolean skip) {
    setJarFlag(module.getName(), jarName, SkipTokens.FLAG_PLUGGABLE, skip);
  }

  private void setJarFlag(String module, String jarName, String flag, boolean skip) {
    String token = SkipTokens.jarToken(module, jarName, flag);
    if (skip) {
      mySkipTokens.add(token);
    } else {
      mySkipTokens.remove(token);
    }
  }

  public void setAllJarsFlag(Module module, String flag, boolean skip) {
    String moduleName = module.getName();
    for (String jarName : ModuleDependencies.forModule(module).jarNames()) {
      setJarFlag(moduleName, jarName, flag, skip);
    }
  }
}
