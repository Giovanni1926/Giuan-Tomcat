package org.giuantomcat.tomcat;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.giuantomcat.runConfiguration.settings.SkipTokens;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClasspathResolver {

  public static final class Classpath {
    public final List<String> classesDirs = new ArrayList<>();
    public final List<String> libJars = new ArrayList<>();
    public final Set<String> skippedTldJarNames = new LinkedHashSet<>();
    public final Set<String> skippedPluggabilityJarNames = new LinkedHashSet<>();
  }

  private ClasspathResolver() {
  }

  public static Classpath resolve(Project project, Set<String> moduleNames,
                                  Set<String> jarSkipTokens) {
    Classpath classpath = new Classpath();
    Set<String> tokens = jarSkipTokens == null ? Set.of() : jarSkipTokens;
    Set<String> classesSet = new LinkedHashSet<>();
    Set<String> jarsSet = new LinkedHashSet<>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!moduleNames.contains(module.getName())) {
        continue;
      }
      ModuleDependencies deps = ModuleDependencies.forModule(module);
      String moduleName = module.getName();

      if (deps.hasTargetClasses()) {
        for (java.io.File dir : deps.targetClassesDirs) {
          classesSet.add(dir.getAbsolutePath());
        }
      } else {
        System.out.println("[GiuanTomcat] target/classes not found for module "
            + moduleName);
      }

      for (java.io.File jar : deps.compileJars) {
        String path = jar.getAbsolutePath();
        jarsSet.add(path);
        String name = jar.getName();
        if (SkipTokens.isJarSkippedTld(tokens, moduleName, name)) {
          classpath.skippedTldJarNames.add(name);
        }
        if (SkipTokens.isJarSkippedPluggable(tokens, moduleName, name)) {
          classpath.skippedPluggabilityJarNames.add(name);
        }
      }
    }

    System.out.println("[GiuanTomcat] classes dirs: " + classesSet);
    System.out.println("[GiuanTomcat] lib jars: " + jarsSet);
    System.out.println("[GiuanTomcat] skipped tld jar scan: " + classpath.skippedTldJarNames);
    System.out.println(
        "[GiuanTomcat] skipped pluggability jar scan: " + classpath.skippedPluggabilityJarNames);

    classpath.classesDirs.addAll(classesSet);
    classpath.libJars.addAll(jarsSet);
    return classpath;
  }
}
