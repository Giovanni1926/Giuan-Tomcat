package org.giuantomcat.tomcat;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dependencies of a module, reusable both at run time (classpath assembly) and in the editor
 * ("Manage skips" panel): library jars in compile scope and {@code target/classes} folders.
 */
public final class ModuleDependencies {

  public final List<File> compileJars;
  public final List<File> targetClassesDirs;

  private ModuleDependencies(List<File> compileJars, List<File> targetClassesDirs) {
    this.compileJars = Collections.unmodifiableList(compileJars);
    this.targetClassesDirs = Collections.unmodifiableList(targetClassesDirs);
  }

  public static ModuleDependencies forModule(Module module) {
    Set<File> jars = new LinkedHashSet<>();
    Set<File> classesDirs = new LinkedHashSet<>();

    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
      File targetClasses = new File(contentRoot.getPath(), "target/classes");
      if (targetClasses.isDirectory()) {
        classesDirs.add(targetClasses.getAbsoluteFile());
      }
    }

    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry libraryEntry) {
        if (libraryEntry.getScope() != DependencyScope.COMPILE) {
          continue;
        }
        for (VirtualFile root : libraryEntry.getRootFiles(OrderRootType.CLASSES)) {
          String path = root.getPath();
          if (path.endsWith("!/")) {
            path = path.substring(0, path.length() - 2);
          }
          if (path.toLowerCase().endsWith(".jar")) {
            jars.add(new File(path));
          }
        }
      }
    }

    return new ModuleDependencies(new ArrayList<>(jars), new ArrayList<>(classesDirs));
  }

  public Set<String> jarNames() {
    Set<String> names = new LinkedHashSet<>();
    for (File jar : compileJars) {
      names.add(jar.getName());
    }
    return names;
  }

  public boolean hasTargetClasses() {
    return !targetClassesDirs.isEmpty();
  }
}
