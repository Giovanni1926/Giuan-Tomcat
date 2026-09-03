package org.giuantomcat.tomcat;

import org.giuantomcat.tomcat.ClasspathResolver.Classpath;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ResourceConsolidator {

  private static final String MERGED_DIR = "giuan-merged";
  private static final String LOG_PREFIX = "[GiuanTomcat] consolidate: ";

  private ResourceConsolidator() {
  }

  public static final class Merged {
    public final String classesDir;
    public final String libDir;

    Merged(String classesDir, String libDir) {
      this.classesDir = classesDir;
      this.libDir = libDir;
    }
  }

  public static Merged consolidate(String catalinaBase, Classpath classpath) throws IOException {
    File mergedRoot = new File(catalinaBase, MERGED_DIR);
    deleteRecursivelySafe(mergedRoot);

    boolean hasClasses = !classpath.classesDirs.isEmpty();
    boolean hasJars = !classpath.libJars.isEmpty();

    File classesDir = new File(mergedRoot, "WEB-INF/classes");
    File libDir = new File(mergedRoot, "WEB-INF/lib");

    if (hasClasses) {
      mkdirs(classesDir);
      mergeClasses(classpath.classesDirs, classesDir);
    }
    if (hasJars) {
      mkdirs(libDir);
      mergeJars(classpath.libJars, libDir);
    }

    return new Merged(hasClasses ? classesDir.getAbsolutePath() : null,
        hasJars ? libDir.getAbsolutePath() : null);
  }

  private static void mergeClasses(List<String> classesDirs, File targetRoot) {
    Map<String, Integer> counts = new HashMap<>();
    for (String dir : classesDirs) {
      collectDirCounts(new File(dir), "", counts);
    }
    for (String dir : classesDirs) {
      linkClasses(new File(dir), targetRoot, "", counts);
    }
  }

  private static void mergeJars(List<String> jars, File libDir) {
    for (String jar : jars) {
      try {
        createFileLink(new File(libDir, new File(jar).getName()), new File(jar));
      } catch (IOException e) {
        System.out.println(LOG_PREFIX + "jar non consolidato " + jar + ": " + e.getMessage());
      }
    }
  }

  private static void collectDirCounts(File dir, String rel, Map<String, Integer> counts) {
    File[] children = dir.listFiles();
    if (children == null) {
      return;
    }
    for (File child : children) {
      if (isLink(child)) {
        continue;
      }
      if (child.isDirectory()) {
        String childRel = rel.isEmpty() ? child.getName() : rel + "/" + child.getName();
        counts.merge(childRel, 1, Integer::sum);
        collectDirCounts(child, childRel, counts);
      }
    }
  }

  private static void linkClasses(File srcDir, File targetDir, String rel,
                                  Map<String, Integer> counts) {
    File[] children = srcDir.listFiles();
    if (children == null) {
      return;
    }
    for (File child : children) {
      if (isLink(child)) {
        continue;
      }
      String childRel = rel.isEmpty() ? child.getName() : rel + "/" + child.getName();
      File targetChild = new File(targetDir, child.getName());
      try {
        if (child.isDirectory()) {
          if (counts.getOrDefault(childRel, 0) > 1) {
            mkdirs(targetChild);
            linkClasses(child, targetChild, childRel, counts);
          } else {
            createDirectoryLink(targetChild, child);
          }
        } else if (child.isFile()) {
          createFileLink(targetChild, child);
        }
      } catch (IOException e) {
        System.out.println(LOG_PREFIX + "elemento non consolidato " + child.getAbsolutePath()
            + ": " + e.getMessage());
      }
    }
  }

  private static void createDirectoryLink(File link, File target) throws IOException {
    deleteIfExists(link);
    if (isWindows()) {
      int exit = runCmd("mklink /J \"" + link.getAbsolutePath() + "\" \""
          + target.getAbsolutePath() + "\"");
      if (exit != 0) {
        throw new IOException("mklink /J failed (" + exit + "): " + link + " -> " + target);
      }
    } else {
      Files.createSymbolicLink(link.toPath(), target.toPath());
    }
  }

  private static void createFileLink(File link, File target) throws IOException {
    deleteIfExists(link);
    try {
      Files.createLink(link.toPath(), target.toPath());
    } catch (IOException e) {
      Files.copy(target.toPath(), link.toPath(), StandardCopyOption.REPLACE_EXISTING);
      System.out.println(LOG_PREFIX + "hard link non disponibile, copiato " + link + " <- "
          + target + " (" + e.getMessage() + ")");
    }
  }

  private static void deleteIfExists(File file) throws IOException {
    if (file.exists()) {
      Files.delete(file.toPath());
    }
  }

  private static void deleteRecursivelySafe(File root) throws IOException {
    if (!root.exists()) {
      return;
    }
    if (isWindows()) {
      runCmd("rmdir /s /q \"" + root.getAbsolutePath() + "\"");
    } else {
      Files.walkFileTree(root.toPath(), new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }

  private static int runCmd(String command) throws IOException {
    Process process = new ProcessBuilder("cmd", "/c", command).redirectErrorStream(true).start();
    try {
      return process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private static boolean isLink(File file) {
    try {
      return Files.isSymbolicLink(file.toPath());
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  private static void mkdirs(File dir) {
    if (!dir.exists()) {
      dir.mkdirs();
    }
  }
}
