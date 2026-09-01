package org.giuantomcat.tomcat;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClasspathResolver {

  public static final class Classpath {
    public final List<String> classesDirs = new ArrayList<>();
    public final List<String> libJars = new ArrayList<>();
  }

  private ClasspathResolver() {
  }

  public static Classpath resolve(Project project, Set<String> moduleNames) {
    Classpath classpath = new Classpath();
    Set<String> classesSet = new LinkedHashSet<>();
    Set<String> jarsSet = new LinkedHashSet<>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!moduleNames.contains(module.getName())) {
        continue;
      }

      for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
        File targetClasses = new File(contentRoot.getPath(), "target/classes");
        if (targetClasses.isDirectory()) {
          classesSet.add(targetClasses.getAbsolutePath());
        } else {
          System.out.println("[GiuanTomcat] target/classes not found for module "
              + module.getName() + ": " + targetClasses.getAbsolutePath());
        }
      }

      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry libraryEntry) {
          DependencyScope scope = libraryEntry.getScope();
          if (scope == DependencyScope.COMPILE) {
            for (VirtualFile root : libraryEntry.getFiles(OrderRootType.CLASSES)) {
              String path = root.getPath();
              if (path.endsWith("!/")) {
                path = path.substring(0, path.length() - 2);
              }
              if (path.toLowerCase().endsWith(".jar")) {
                jarsSet.add(path);
              }
            }
          }
        }
      }
    }

    System.out.println("[GiuanTomcat] classes dirs: " + classesSet);
    System.out.println("[GiuanTomcat] lib jars: " + jarsSet);

    classpath.classesDirs.addAll(classesSet);
    classpath.libJars.addAll(jarsSet);
    return classpath;
  }
}
