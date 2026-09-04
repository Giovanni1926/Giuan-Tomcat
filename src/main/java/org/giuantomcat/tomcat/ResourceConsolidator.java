package org.giuantomcat.tomcat;

import com.intellij.openapi.progress.ProgressIndicator;
import org.giuantomcat.GiuanTomcatConstants;
import org.giuantomcat.tomcat.ClasspathResolver.Classpath;
import org.giuantomcat.tomcat.link.FileLinker;
import org.giuantomcat.tomcat.link.FileLinkerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ResourceConsolidator {

  private static final FileLinker LINKER = FileLinkerFactory.get();
  private static final String LOG_PREFIX = "[GiuanTomcat] consolidate: ";
  private static final String SECTION_CLASSES = "C";
  private static final String SECTION_JARS = "J";

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

  private static final class Manifest {
    final List<String> classesDirs;
    final Map<String, JarInfo> jars;

    Manifest(List<String> classesDirs, Map<String, JarInfo> jars) {
      this.classesDirs = classesDirs;
      this.jars = jars;
    }
  }

  private static final class JarInfo {
    final long size;
    final long mtime;

    JarInfo(long size, long mtime) {
      this.size = size;
      this.mtime = mtime;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof JarInfo other)) {
        return false;
      }
      return size == other.size && mtime == other.mtime;
    }

    @Override
    public int hashCode() {
      return 31 * Long.hashCode(size) + Long.hashCode(mtime);
    }
  }

  public static Merged consolidate(File mergedRoot, Classpath classpath,
                                   ProgressIndicator indicator) throws IOException {
    mkdirs(mergedRoot);
    File manifestFile = new File(mergedRoot.getParentFile(), GiuanTomcatConstants.MERGED_MANIFEST_NAME);
    Manifest previous = readManifest(manifestFile);

    boolean hasClasses = !classpath.classesDirs.isEmpty();
    boolean hasJars = !classpath.libJars.isEmpty();

    List<String> desiredClasses = new ArrayList<>(classpath.classesDirs);
    Collections.sort(desiredClasses);
    Map<String, JarInfo> desiredJars = collectJarInfos(classpath.libJars);
    Map<String, JarInfo> previousJars = previous == null ? Map.of() : previous.jars;

    File classesDir = new File(mergedRoot, "WEB-INF/classes");
    File libDir = new File(mergedRoot, "WEB-INF/lib");

    boolean classesUnchanged = hasClasses && previous != null
        && previous.classesDirs.equals(desiredClasses) && classesDir.isDirectory();
    int classesWork = (!classesUnchanged && hasClasses) ? classpath.classesDirs.size() : 0;
    int jarsWork = hasJars ? jarsToProcess(previousJars, desiredJars) : 0;
    int total = classesWork + jarsWork;
    int[] progress = {0};

    if (hasClasses) {
      if (!classesUnchanged) {
        deleteRecursivelySafe(classesDir);
        mkdirs(classesDir);
        mergeClasses(classpath.classesDirs, classesDir, indicator, progress, total);
      }
    } else {
      deleteRecursivelySafe(classesDir);
    }

    if (hasJars) {
      mkdirs(libDir);
      reconcileJars(libDir, previousJars, desiredJars, indicator, progress, total);
    } else {
      deleteRecursivelySafe(libDir);
    }

    writeManifest(manifestFile, desiredClasses, desiredJars);

    return new Merged(hasClasses ? classesDir.getAbsolutePath() : null,
        hasJars ? libDir.getAbsolutePath() : null);
  }

  private static void mergeClasses(List<String> classesDirs, File targetRoot,
                                   ProgressIndicator indicator, int[] progress, int total) {
    Map<String, Integer> counts = new HashMap<>();
    for (String dir : classesDirs) {
      progress(indicator, "Scanning classes", dir, progress[0], total);
      collectDirCounts(new File(dir), "", counts);
    }
    for (String dir : classesDirs) {
      progress(indicator, "Linking classes (" + (progress[0] + 1) + "/" + total + ")", dir,
          progress[0], total);
      linkClasses(new File(dir), targetRoot, "", counts);
      progress[0]++;
    }
  }

  private static void reconcileJars(File libDir, Map<String, JarInfo> previous,
                                    Map<String, JarInfo> desired,
                                    ProgressIndicator indicator, int[] progress, int total)
      throws IOException {
    for (Map.Entry<String, JarInfo> entry : desired.entrySet()) {
      String path = entry.getKey();
      JarInfo prev = previous.get(path);
      if (prev != null && prev.equals(entry.getValue())) {
        continue;
      }
      File link = new File(libDir, new File(path).getName());
      progress(indicator,
          "Linking jar (" + (progress[0] + 1) + "/" + total + "): " + new File(path).getName(),
          path, progress[0], total);
      deleteIfExists(link);
      try {
        createFileLink(link, new File(path));
      } catch (IOException e) {
        System.out.println(LOG_PREFIX + "jar non consolidato " + path + ": " + e.getMessage());
      }
      progress[0]++;
    }
    for (String path : previous.keySet()) {
      if (desired.containsKey(path)) {
        continue;
      }
      File link = new File(libDir, new File(path).getName());
      progress(indicator,
          "Removing jar (" + (progress[0] + 1) + "/" + total + "): " + new File(path).getName(),
          path, progress[0], total);
      deleteIfExists(link);
      progress[0]++;
    }
  }

  private static int jarsToProcess(Map<String, JarInfo> previous, Map<String, JarInfo> desired) {
    int count = 0;
    for (Map.Entry<String, JarInfo> entry : desired.entrySet()) {
      JarInfo prev = previous.get(entry.getKey());
      if (prev == null || !prev.equals(entry.getValue())) {
        count++;
      }
    }
    for (String path : previous.keySet()) {
      if (!desired.containsKey(path)) {
        count++;
      }
    }
    return count;
  }

  private static Map<String, JarInfo> collectJarInfos(List<String> jars) {
    Map<String, JarInfo> map = new HashMap<>();
    for (String path : jars) {
      File file = new File(path);
      long size = -1;
      long mtime = -1;
      try {
        if (file.isFile()) {
          size = Files.size(file.toPath());
          mtime = Files.getLastModifiedTime(file.toPath()).toMillis();
        }
      } catch (IOException ignored) {
        // keep -1 so the jar is treated as changed
      }
      map.put(path, new JarInfo(size, mtime));
    }
    return map;
  }

  private static void progress(ProgressIndicator indicator, String text, String detail,
                               int done, int total) {
    if (indicator == null) {
      return;
    }
    indicator.setText(text);
    if (detail != null) {
      indicator.setText2(detail);
    }
    if (!indicator.isIndeterminate() && total > 0) {
      indicator.setFraction((double) done / total);
    }
    indicator.checkCanceled();
  }

  private static void collectDirCounts(File dir, String rel, Map<String, Integer> counts) {
    File[] children = dir.listFiles();
    if (children == null) {
      return;
    }
    for (File child : children) {
      if (LINKER.isLink(child.toPath())) {
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
      if (LINKER.isLink(child.toPath())) {
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
    LINKER.createDirectoryLink(link.toPath(), target.toPath());
  }

  private static void createFileLink(File link, File target) throws IOException {
    deleteIfExists(link);
    try {
      LINKER.createFileLink(link.toPath(), target.toPath());
    } catch (IOException e) {
      Files.copy(target.toPath(), link.toPath(), StandardCopyOption.REPLACE_EXISTING);
      System.out.println(LOG_PREFIX + "hard link non disponibile, copiato " + link + " <- "
          + target + " (" + e.getMessage() + ")");
    }
  }

  private static void deleteIfExists(File file) throws IOException {
    LINKER.deleteLink(file.toPath());
  }

  private static void deleteRecursivelySafe(File root) throws IOException {
    LINKER.deleteRecursively(root.toPath());
  }

  private static void mkdirs(File dir) {
    if (!dir.exists()) {
      dir.mkdirs();
    }
  }

  private static void writeManifest(File manifestFile, List<String> classesDirs,
                                    Map<String, JarInfo> jars) throws IOException {
    try (BufferedWriter writer =
             Files.newBufferedWriter(manifestFile.toPath(), StandardCharsets.UTF_8)) {
      writer.write(GiuanTomcatConstants.MANIFEST_HEADER + " " + GiuanTomcatConstants.MANIFEST_VERSION);
      writer.newLine();
      writer.write(SECTION_CLASSES);
      writer.newLine();
      for (String dir : classesDirs) {
        writer.write(dir);
        writer.newLine();
      }
      writer.write(SECTION_JARS);
      writer.newLine();
      for (Map.Entry<String, JarInfo> entry : jars.entrySet()) {
        writer.write(entry.getValue().size + "\t" + entry.getValue().mtime + "\t"
            + entry.getKey());
        writer.newLine();
      }
    }
  }

  private static Manifest readManifest(File manifestFile) {
    if (!manifestFile.isFile()) {
      return null;
    }
    try (BufferedReader reader =
             Files.newBufferedReader(manifestFile.toPath(), StandardCharsets.UTF_8)) {
      String header = reader.readLine();
      if (header == null || !header.startsWith(GiuanTomcatConstants.MANIFEST_HEADER)) {
        return null;
      }
      List<String> classesDirs = new ArrayList<>();
      Map<String, JarInfo> jars = new HashMap<>();
      String section = null;
      String line;
      while ((line = reader.readLine()) != null) {
        if (SECTION_CLASSES.equals(line)) {
          section = SECTION_CLASSES;
        } else if (SECTION_JARS.equals(line)) {
          section = SECTION_JARS;
        } else if (!line.isEmpty()) {
          if (SECTION_CLASSES.equals(section)) {
            classesDirs.add(line);
          } else if (SECTION_JARS.equals(section)) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) {
              try {
                jars.put(parts[2],
                    new JarInfo(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
              } catch (NumberFormatException ignored) {
                // skip malformed entry
              }
            }
          }
        }
      }
      return new Manifest(classesDirs, jars);
    } catch (IOException e) {
      return null;
    }
  }
}
