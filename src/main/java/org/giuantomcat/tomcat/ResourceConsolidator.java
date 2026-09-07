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
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the consolidated {@code WEB-INF/classes} and {@code WEB-INF/lib} trees under the merged
 * root.
 *
 * <p>The classes tree is merged with directory junctions for the packages owned by a single
 * module (the junction reflects the live content, so those packages are never touched again) and
 * per-file hard links only for the files that cannot be covered by a junction: root files and the
 * files living in packages shared by more than one module.
 *
 * <p>The manifest records, per module, the root files and a digest of the direct files of every
 * package. At each run the desired layout is compared with the previous one and only the missing
 * junctions / changed shared packages / changed root files are reconciled: everything unchanged is
 * left untouched.
 */
public final class ResourceConsolidator {

  private static final FileLinker LINKER = FileLinkerFactory.get();
  private static final String LOG_PREFIX = "[GiuanTomcat] consolidate: ";
  private static final String SECTION_CLASSES = "C";
  private static final String SECTION_JARS = "J";
  private static final String SECTION_FILES = "F";
  private static final String TAG_MODULE = "M";
  private static final String TAG_ROOT_FILE = "RF";
  private static final String TAG_DIR = "D";

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
    final Map<String, EntryInfo> jars;
    final Map<String, ModuleState> modules;

    Manifest(List<String> classesDirs, Map<String, EntryInfo> jars,
             Map<String, ModuleState> modules) {
      this.classesDirs = classesDirs;
      this.jars = jars;
      this.modules = modules;
    }
  }

  private static final class ModuleState {
    final Map<String, EntryInfo> rootFiles;
    final Map<String, String> dirs;

    ModuleState(Map<String, EntryInfo> rootFiles, Map<String, String> dirs) {
      this.rootFiles = rootFiles;
      this.dirs = dirs;
    }
  }

  private static final class EntryInfo {
    final long size;
    final long mtime;

    EntryInfo(long size, long mtime) {
      this.size = size;
      this.mtime = mtime;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof EntryInfo other)) {
        return false;
      }
      return size == other.size && mtime == other.mtime;
    }

    @Override
    public int hashCode() {
      return 31 * Long.hashCode(size) + Long.hashCode(mtime);
    }
  }

  private enum Kind {
    AGGREGATE,
    JUNCTION
  }

  /**
   * Filesystem layout derived from the module states: which directories exist as real nodes in the
   * merged tree (aggregate dirs for shared packages, junctions for exclusive ones) and the
   * immediate children of every aggregate node.
   */
  private static final class Tree {
    final Map<String, Integer> counts = new HashMap<>();
    final Map<String, Kind> kinds = new HashMap<>();
    final Map<String, String> junctionOwners = new HashMap<>();
    final Map<String, List<String>> children = new HashMap<>();
  }

  private static final class Reconcile {

    private final File classesRoot;
    private final Map<String, ModuleState> cur;
    private final Map<String, ModuleState> prev;
    private final List<String> curModules;
    private final Tree curTree;
    private final Tree prevTree;
    private final ProgressIndicator indicator;

    Reconcile(File classesRoot, Map<String, ModuleState> cur,
              Map<String, ModuleState> prev, ProgressIndicator indicator) {
      this.classesRoot = classesRoot;
      this.cur = cur;
      this.prev = prev;
      this.curModules = new ArrayList<>(cur.keySet());
      Collections.sort(curModules);
      this.curTree = buildTree(cur);
      this.prevTree = buildTree(prev);
      this.indicator = indicator;
    }

    void run() throws IOException {
      if (!classesRoot.isDirectory()) {
        mkdirs(classesRoot);
      }
      aggregate("", classesRoot, false);
    }

    private void aggregate(String rel, File dir, boolean force) throws IOException {
      boolean root = rel.isEmpty();
      boolean rebuilt;
      if (root) {
        rebuilt = !dir.isDirectory();
        if (rebuilt) {
          mkdirs(dir);
        }
        if (rebuilt || !rootUnchanged()) {
          report("Resyncing classes root");
          resyncDirect(dir, "", curModules);
        }
      } else if (force || !dir.exists()) {
        rebuild(dir, rel);
        rebuilt = true;
      } else {
        List<String> curOwners = sortedOwners(cur, rel);
        List<String> prevOwners = sortedOwners(prev, rel);
        boolean prevAggregate = prevTree.kinds.get(rel) == Kind.AGGREGATE;
        if (prevAggregate && prevOwners.equals(curOwners)) {
          boolean digestsSame = true;
          for (String module : curOwners) {
            if (!prev.get(module).dirs.get(rel).equals(cur.get(module).dirs.get(rel))) {
              digestsSame = false;
              break;
            }
          }
          if (digestsSame) {
            rebuilt = false;
          } else {
            report("Resyncing " + rel);
            resyncDirect(dir, rel, curOwners);
            rebuilt = false;
          }
        } else {
          // Previously a junction (or new/owner-changed node): replace the whole node.
          rebuild(dir, rel);
          rebuilt = true;
        }
      }

      List<String> curChildren = sortedChildren(curTree.children.get(rel));
      Set<String> curNames = new HashSet<>(curChildren);
      for (String name : curChildren) {
        String childRel = rel.isEmpty() ? name : rel + "/" + name;
        File target = new File(dir, name);
        if (curTree.kinds.get(childRel) == Kind.AGGREGATE) {
          aggregate(childRel, target, rebuilt);
        } else {
          ensureJunction(childRel, target, rebuilt);
        }
      }

      boolean prevHadChildren = root || prevTree.kinds.get(rel) == Kind.AGGREGATE;
      if (prevHadChildren) {
        List<String> prevChildren = sortedChildren(prevTree.children.get(rel));
        for (String name : prevChildren) {
          if (curNames.contains(name)) {
            continue;
          }
          File stale = new File(dir, name);
          if (stale.exists()) {
            report("Removing " + (rel.isEmpty() ? name : rel + "/" + name));
            deleteRecursively(stale);
          }
        }
      }
    }

    private void ensureJunction(String rel, File target, boolean force) throws IOException {
      String owner = curTree.junctionOwners.get(rel);
      boolean same = !force && prevTree.kinds.get(rel) == Kind.JUNCTION
          && owner != null && owner.equals(prevTree.junctionOwners.get(rel));
      if (same) {
        return;
      }
      if (target.exists()) {
        report("Rebuilding " + rel);
        deleteRecursively(target);
      }
      createDirectoryLink(target, sourceDir(owner, rel));
    }

    private void rebuild(File dir, String rel) throws IOException {
      if (dir.exists()) {
        report("Rebuilding " + rel);
        deleteRecursively(dir);
      }
      mkdirs(dir);
      List<String> owners = sortedOwners(cur, rel);
      linkDirectFiles(dir, rel, owners);
    }

    private void resyncDirect(File dir, String rel, List<String> owners) throws IOException {
      deleteDirectFiles(dir);
      linkDirectFiles(dir, rel, owners);
    }

    private void linkDirectFiles(File dir, String rel, List<String> owners) throws IOException {
      for (String module : owners) {
        File src = sourceDir(module, rel);
        if (!src.isDirectory()) {
          continue;
        }
        File[] children = src.listFiles();
        if (children == null) {
          continue;
        }
        for (File child : children) {
          if (LINKER.isLink(child.toPath()) || !child.isFile()) {
            continue;
          }
          createFileLink(new File(dir, child.getName()), child);
        }
      }
    }

    private void deleteDirectFiles(File dir) throws IOException {
      File[] children = dir.listFiles();
      if (children == null) {
        return;
      }
      for (File child : children) {
        if (child.isFile()) {
          deleteIfExists(child);
        }
      }
    }

    private boolean rootUnchanged() {
      if (!cur.keySet().equals(prev.keySet())) {
        return false;
      }
      for (Map.Entry<String, ModuleState> entry : cur.entrySet()) {
        ModuleState previous = prev.get(entry.getKey());
        if (previous == null || !previous.rootFiles.equals(entry.getValue().rootFiles)) {
          return false;
        }
      }
      return true;
    }

    private void report(String text) {
      if (indicator == null) {
        return;
      }
      indicator.setText2(text);
      indicator.checkCanceled();
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
    Map<String, EntryInfo> desiredJars = collectJarInfos(classpath.libJars);
    Map<String, EntryInfo> previousJars = previous == null ? Map.of() : previous.jars;
    Map<String, ModuleState> previousModules = previous == null ? Map.of() : previous.modules;

    File classesDir = new File(mergedRoot, "WEB-INF/classes");
    File libDir = new File(mergedRoot, "WEB-INF/lib");

    Map<String, ModuleState> currentModules = new LinkedHashMap<>();
    if (hasClasses) {
      for (String dir : classpath.classesDirs) {
        currentModules.put(dir, scanModule(new File(dir)));
      }
      new Reconcile(classesDir, currentModules, previousModules, indicator).run();
    } else {
      deleteRecursively(classesDir);
    }

    int jarsWork = hasJars ? jarsToProcess(previousJars, desiredJars) : 0;
    int[] progress = {0};
    if (hasJars) {
      mkdirs(libDir);
      reconcileJars(libDir, previousJars, desiredJars, indicator, progress, jarsWork);
    } else {
      deleteRecursively(libDir);
    }

    writeManifest(manifestFile, desiredClasses, desiredJars, currentModules);

    return new Merged(hasClasses ? classesDir.getAbsolutePath() : null,
        hasJars ? libDir.getAbsolutePath() : null);
  }

  private static ModuleState scanModule(File root) {
    Map<String, EntryInfo> rootFiles = new LinkedHashMap<>();
    Map<String, String> dirs = new LinkedHashMap<>();
    scanDir(root, "", rootFiles, dirs);
    return new ModuleState(rootFiles, dirs);
  }

  private static void scanDir(File dir, String rel, Map<String, EntryInfo> rootFiles,
                              Map<String, String> dirs) {
    File[] children = dir.listFiles();
    if (children == null) {
      return;
    }
    Map<String, EntryInfo> direct = new HashMap<>();
    List<File> subDirs = new ArrayList<>();
    for (File child : children) {
      if (LINKER.isLink(child.toPath())) {
        continue;
      }
      if (child.isDirectory()) {
        subDirs.add(child);
      } else if (child.isFile()) {
        EntryInfo info = entryInfo(child);
        if (info != null) {
          direct.put(child.getName(), info);
        }
      }
    }
    if (rel.isEmpty()) {
      rootFiles.putAll(direct);
    } else {
      dirs.put(rel, digestFiles(direct));
    }
    for (File sub : subDirs) {
      String childRel = rel.isEmpty() ? sub.getName() : rel + "/" + sub.getName();
      scanDir(sub, childRel, rootFiles, dirs);
    }
  }

  private static EntryInfo entryInfo(File file) {
    try {
      BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      return new EntryInfo(attrs.size(), attrs.lastModifiedTime().toMillis());
    } catch (IOException e) {
      return null;
    }
  }

  private static String digestFiles(Map<String, EntryInfo> files) {
    List<String> names = new ArrayList<>(files.keySet());
    Collections.sort(names);
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      for (String name : names) {
        EntryInfo info = files.get(name);
        md.update((name + '\0' + info.size + '\0' + info.mtime + '\n')
            .getBytes(StandardCharsets.UTF_8));
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 non disponibile", e);
    }
  }

  private static Tree buildTree(Map<String, ModuleState> modules) {
    Tree tree = new Tree();
    Set<String> rels = new HashSet<>();
    for (ModuleState state : modules.values()) {
      for (String rel : state.dirs.keySet()) {
        rels.add(rel);
        tree.counts.merge(rel, 1, Integer::sum);
      }
    }
    for (String rel : rels) {
      if (!isReal(rel, tree.counts)) {
        continue;
      }
      tree.children.computeIfAbsent(parentOf(rel), k -> new ArrayList<>()).add(nameOf(rel));
      if (tree.counts.get(rel) > 1) {
        tree.kinds.put(rel, Kind.AGGREGATE);
      } else {
        tree.kinds.put(rel, Kind.JUNCTION);
        for (Map.Entry<String, ModuleState> entry : modules.entrySet()) {
          if (entry.getValue().dirs.containsKey(rel)) {
            tree.junctionOwners.put(rel, entry.getKey());
            break;
          }
        }
      }
    }
    for (List<String> list : tree.children.values()) {
      Collections.sort(list);
    }
    return tree;
  }

  private static boolean isReal(String rel, Map<String, Integer> counts) {
    int idx = rel.indexOf('/');
    while (idx > 0) {
      String parent = rel.substring(0, idx);
      if (counts.getOrDefault(parent, 0) < 2) {
        return false;
      }
      idx = rel.indexOf('/', idx + 1);
    }
    return true;
  }

  private static String parentOf(String rel) {
    int idx = rel.lastIndexOf('/');
    return idx < 0 ? "" : rel.substring(0, idx);
  }

  private static String nameOf(String rel) {
    int idx = rel.lastIndexOf('/');
    return rel.substring(idx + 1);
  }

  private static List<String> sortedOwners(Map<String, ModuleState> modules, String rel) {
    List<String> owners = new ArrayList<>();
    for (Map.Entry<String, ModuleState> entry : modules.entrySet()) {
      if (entry.getValue().dirs.containsKey(rel)) {
        owners.add(entry.getKey());
      }
    }
    Collections.sort(owners);
    return owners;
  }

  private static List<String> sortedChildren(List<String> children) {
    if (children == null) {
      return List.of();
    }
    List<String> sorted = new ArrayList<>(children);
    Collections.sort(sorted);
    return sorted;
  }

  private static File sourceDir(String classesDir, String rel) {
    return rel.isEmpty() ? new File(classesDir)
        : new File(classesDir, rel.replace('/', File.separatorChar));
  }

  private static void reconcileJars(File libDir, Map<String, EntryInfo> previous,
                                    Map<String, EntryInfo> desired,
                                    ProgressIndicator indicator, int[] progress, int total)
      throws IOException {
    for (Map.Entry<String, EntryInfo> entry : desired.entrySet()) {
      String path = entry.getKey();
      EntryInfo prev = previous.get(path);
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

  private static int jarsToProcess(Map<String, EntryInfo> previous, Map<String, EntryInfo> desired) {
    int count = 0;
    for (Map.Entry<String, EntryInfo> entry : desired.entrySet()) {
      EntryInfo prev = previous.get(entry.getKey());
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

  private static Map<String, EntryInfo> collectJarInfos(List<String> jars) {
    Map<String, EntryInfo> map = new HashMap<>();
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
      map.put(path, new EntryInfo(size, mtime));
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

  private static void deleteRecursively(File root) throws IOException {
    LINKER.deleteRecursively(root.toPath());
  }

  private static void mkdirs(File dir) {
    if (!dir.exists()) {
      dir.mkdirs();
    }
  }

  private static void writeManifest(File manifestFile, List<String> classesDirs,
                                    Map<String, EntryInfo> jars,
                                    Map<String, ModuleState> modules) throws IOException {
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
      for (Map.Entry<String, EntryInfo> entry : jars.entrySet()) {
        writer.write(entry.getValue().size + "\t" + entry.getValue().mtime + "\t"
            + entry.getKey());
        writer.newLine();
      }
      writer.write(SECTION_FILES);
      writer.newLine();
      for (String dir : classesDirs) {
        ModuleState state = modules.get(dir);
        if (state == null) {
          continue;
        }
        writer.write(TAG_MODULE + "\t" + dir);
        writer.newLine();
        for (Map.Entry<String, EntryInfo> file : state.rootFiles.entrySet()) {
          writer.write(TAG_ROOT_FILE + "\t" + file.getKey() + "\t" + file.getValue().size
              + "\t" + file.getValue().mtime);
          writer.newLine();
        }
        for (Map.Entry<String, String> dirEntry : state.dirs.entrySet()) {
          writer.write(TAG_DIR + "\t" + dirEntry.getKey() + "\t" + dirEntry.getValue());
          writer.newLine();
        }
      }
    }
  }

  private static Manifest readManifest(File manifestFile) {
    if (!manifestFile.isFile()) {
      return null;
    }
    String expectedHeader =
        GiuanTomcatConstants.MANIFEST_HEADER + " " + GiuanTomcatConstants.MANIFEST_VERSION;
    try (BufferedReader reader =
             Files.newBufferedReader(manifestFile.toPath(), StandardCharsets.UTF_8)) {
      String header = reader.readLine();
      if (header == null || !header.equals(expectedHeader)) {
        return null;
      }
      List<String> classesDirs = new ArrayList<>();
      Map<String, EntryInfo> jars = new HashMap<>();
      Map<String, ModuleState> modules = new LinkedHashMap<>();
      Map<String, Map<String, EntryInfo>> moduleRootFiles = new LinkedHashMap<>();
      Map<String, Map<String, String>> moduleDirs = new LinkedHashMap<>();
      String currentModule = null;
      String section = null;
      String line;
      while ((line = reader.readLine()) != null) {
        if (SECTION_CLASSES.equals(line)) {
          section = SECTION_CLASSES;
        } else if (SECTION_JARS.equals(line)) {
          section = SECTION_JARS;
        } else if (SECTION_FILES.equals(line)) {
          section = SECTION_FILES;
        } else if (line.isEmpty()) {
          continue;
        } else if (SECTION_CLASSES.equals(section)) {
          classesDirs.add(line);
        } else if (SECTION_JARS.equals(section)) {
          String[] parts = line.split("\t", 3);
          if (parts.length == 3) {
            try {
              jars.put(parts[2],
                  new EntryInfo(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
            } catch (NumberFormatException ignored) {
              // skip malformed entry
            }
          }
        } else if (SECTION_FILES.equals(section)) {
          String[] parts = line.split("\t");
          if (parts.length < 2) {
            continue;
          }
          if (TAG_MODULE.equals(parts[0])) {
            currentModule = parts[1];
            moduleRootFiles.put(currentModule, new LinkedHashMap<>());
            moduleDirs.put(currentModule, new LinkedHashMap<>());
          } else if (TAG_ROOT_FILE.equals(parts[0]) && parts.length == 4 && currentModule != null) {
            try {
              moduleRootFiles.get(currentModule).put(parts[1],
                  new EntryInfo(Long.parseLong(parts[2]), Long.parseLong(parts[3])));
            } catch (NumberFormatException ignored) {
              // skip malformed entry
            }
          } else if (TAG_DIR.equals(parts[0]) && parts.length == 3 && currentModule != null
              && !parts[2].isEmpty()) {
            moduleDirs.get(currentModule).put(parts[1], parts[2]);
          }
        }
      }
      for (String module : moduleRootFiles.keySet()) {
        modules.put(module,
            new ModuleState(moduleRootFiles.get(module), moduleDirs.get(module)));
      }
      return new Manifest(classesDirs, jars, modules);
    } catch (IOException e) {
      return null;
    }
  }
}
