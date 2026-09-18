package org.giuantomcat.tomcat;

import com.intellij.openapi.progress.ProgressIndicator;
import org.giuantomcat.GiuanTomcatConstants;
import org.giuantomcat.tomcat.ClasspathResolver.Classpath;
import org.giuantomcat.tomcat.link.FileLinker;
import org.giuantomcat.tomcat.link.FileLinkerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * <p>Every run reconciles the <em>actual</em> filesystem state instead of trusting the previously
 * recorded one: junctions are verified to resolve to the expected source directory, hard-linked
 * files are verified to exist with the same size and timestamp as their source, and jars are
 * verified to be present and up to date. Missing, dangling or stale entries are relinked, so a
 * partially cleaned or externally damaged merged tree heals automatically on the next run. The
 * manifest is written afterwards for diagnostics only and lives inside the merged root, so removing
 * that folder removes it too.
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
  private static final String WEB_INF_CLASSES = "WEB-INF/classes";
  private static final String WEB_INF_LIB = "WEB-INF/lib";

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

  private static final class ModuleState {
    final Map<String, EntryInfo> rootFiles;
    final Set<String> dirs;

    ModuleState(Map<String, EntryInfo> rootFiles, Set<String> dirs) {
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
  }

  private enum Kind {
    AGGREGATE,
    JUNCTION
  }

  /**
   * Desired layout derived from the module states: which directories exist as real nodes in the
   * merged tree (aggregate dirs for shared packages, junctions for exclusive ones) and the
   * immediate children of every aggregate node.
   */
  private static final class Tree {
    final Map<String, Integer> counts = new HashMap<>();
    final Map<String, Kind> kinds = new HashMap<>();
    final Map<String, String> junctionOwners = new HashMap<>();
    final Map<String, List<String>> children = new HashMap<>();
  }

  /**
   * Reconciles the merged classes tree with the desired layout. The manifest is not consulted:
   * the decision to link, relink or delete an entry is always taken from the current filesystem
   * state, so nothing can stay broken after a partial cleanup.
   */
  private static final class Reconcile {

    private final File classesRoot;
    private final Map<String, ModuleState> cur;
    private final List<String> curModules;
    private final Tree curTree;
    private final ProgressIndicator indicator;

    Reconcile(File classesRoot, Map<String, ModuleState> cur, ProgressIndicator indicator) {
      this.classesRoot = classesRoot;
      this.cur = cur;
      this.curModules = new ArrayList<>(cur.keySet());
      Collections.sort(curModules);
      this.curTree = buildTree(cur);
      this.indicator = indicator;
    }

    void run() throws IOException {
      boolean rootMissing = !classesRoot.isDirectory();
      aggregate("", classesRoot, rootMissing);
    }

    private void aggregate(String rel, File dir, boolean force) throws IOException {
      boolean root = rel.isEmpty();
      boolean recreate = force || !dir.isDirectory() || isLinkedDirectory(dir);
      if (recreate) {
        rebuild(dir, rel);
      } else {
        verifyDirectFiles(dir, rel);
      }

      List<String> curChildren = sortedChildren(curTree.children.get(rel));
      Map<String, String> actualNames = listNamesByLowerCase(dir);
      for (String name : curChildren) {
        String childRel = root ? name : rel + "/" + name;
        File target = new File(dir, name);
        boolean exactCase = name.equals(actualNames.get(name.toLowerCase(Locale.ROOT)));
        if (curTree.kinds.get(childRel) == Kind.AGGREGATE) {
          aggregate(childRel, target, !exactCase);
        } else {
          ensureJunction(childRel, target, !exactCase);
        }
      }

      Set<String> expected = new HashSet<>();
      for (String name : curChildren) {
        expected.add(name.toLowerCase(Locale.ROOT));
      }
      File[] actualChildren = dir.listFiles();
      if (actualChildren == null) {
        return;
      }
      for (File child : actualChildren) {
        if (child.isFile()) {
          // Direct files are created and removed by verifyDirectFiles.
          continue;
        }
        if (expected.contains(child.getName().toLowerCase(Locale.ROOT))) {
          continue;
        }
        report("Removing " + (root ? child.getName() : rel + "/" + child.getName()));
        deleteRecursively(child);
      }
    }

    private void rebuild(File dir, String rel) throws IOException {
      if (Files.exists(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        report("Rebuilding " + (rel.isEmpty() ? "classes root" : rel));
        deleteRecursively(dir);
      }
      mkdirs(dir);
      verifyDirectFiles(dir, rel);
    }

    /**
     * Ensures the real directory {@code dir} contains a hard link (or copy) for every direct file
     * of the owning modules and nothing else, repairing missing and stale entries in place.
     */
    private void verifyDirectFiles(File dir, String rel) throws IOException {
      Map<String, File> expected = new LinkedHashMap<>();
      for (String module : ownersFor(rel)) {
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
          expected.put(child.getName().toLowerCase(Locale.ROOT), child);
        }
      }

      Map<String, File> present = listFilesByLowerCase(dir);
      for (Map.Entry<String, File> entry : present.entrySet()) {
        if (expected.containsKey(entry.getKey())) {
          continue;
        }
        report("Removing " + entry.getValue().getName());
        deleteIfExists(entry.getValue());
      }

      for (File source : expected.values()) {
        File existing = present.get(source.getName().toLowerCase(Locale.ROOT));
        if (existing != null && existing.getName().equals(source.getName())
            && sameContent(existing, source)) {
          continue;
        }
        createFileLink(new File(dir, source.getName()), source);
      }
    }

    private void ensureJunction(String rel, File target, boolean force) throws IOException {
      String owner = curTree.junctionOwners.get(rel);
      File source = sourceDir(owner, rel);
      if (!force && LINKER.isDirectoryLinkTo(target.toPath(), source.toPath())) {
        return;
      }
      report("Rebuilding " + rel);
      if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        deleteRecursively(target);
      }
      createDirectoryLink(target, source);
    }

    private List<String> ownersFor(String rel) {
      return rel.isEmpty() ? curModules : sortedOwners(cur, rel);
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
    File manifestFile = new File(mergedRoot, GiuanTomcatConstants.MERGED_MANIFEST_NAME);

    boolean hasClasses = !classpath.classesDirs.isEmpty();
    boolean hasJars = !classpath.libJars.isEmpty();

    File classesDir = new File(mergedRoot, WEB_INF_CLASSES);
    File libDir = new File(mergedRoot, WEB_INF_LIB);

    List<String> sortedClasses = new ArrayList<>(new LinkedHashSet<>(classpath.classesDirs));
    Collections.sort(sortedClasses);
    Map<String, ModuleState> currentModules = new LinkedHashMap<>();
    if (hasClasses) {
      for (String dir : sortedClasses) {
        currentModules.put(dir, scanModule(new File(dir)));
      }
      new Reconcile(classesDir, currentModules, indicator).run();
    } else {
      deleteRecursively(classesDir);
    }

    if (hasJars) {
      reconcileJars(libDir, classpath.libJars, indicator);
    } else {
      deleteRecursively(libDir);
    }

    writeManifest(manifestFile, sortedClasses, classpath.libJars, currentModules);

    return new Merged(hasClasses ? classesDir.getAbsolutePath() : null,
        hasJars ? libDir.getAbsolutePath() : null);
  }

  private static ModuleState scanModule(File root) {
    Map<String, EntryInfo> rootFiles = new LinkedHashMap<>();
    Set<String> dirs = new HashSet<>();
    scanDir(root, "", rootFiles, dirs);
    return new ModuleState(rootFiles, dirs);
  }

  private static void scanDir(File dir, String rel, Map<String, EntryInfo> rootFiles,
                              Set<String> dirs) {
    File[] children = dir.listFiles();
    if (children == null) {
      return;
    }
    List<File> subDirs = new ArrayList<>();
    for (File child : children) {
      if (LINKER.isLink(child.toPath())) {
        continue;
      }
      if (child.isDirectory()) {
        subDirs.add(child);
      } else if (rel.isEmpty() && child.isFile()) {
        EntryInfo info = entryInfo(child);
        if (info != null) {
          rootFiles.put(child.getName(), info);
        }
      }
    }
    for (File sub : subDirs) {
      String childRel = rel.isEmpty() ? sub.getName() : rel + "/" + sub.getName();
      dirs.add(childRel);
      scanDir(sub, childRel, rootFiles, dirs);
    }
  }

  private static Tree buildTree(Map<String, ModuleState> modules) {
    Tree tree = new Tree();
    Set<String> rels = new HashSet<>();
    for (ModuleState state : modules.values()) {
      for (String rel : state.dirs) {
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
          if (entry.getValue().dirs.contains(rel)) {
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
      if (entry.getValue().dirs.contains(rel)) {
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

  private static Map<String, File> listFilesByLowerCase(File dir) {
    Map<String, File> files = new LinkedHashMap<>();
    File[] children = dir.listFiles();
    if (children == null) {
      return files;
    }
    for (File child : children) {
      if (child.isFile()) {
        files.put(child.getName().toLowerCase(Locale.ROOT), child);
      }
    }
    return files;
  }

  private static Map<String, String> listNamesByLowerCase(File dir) {
    Map<String, String> names = new LinkedHashMap<>();
    File[] children = dir.listFiles();
    if (children == null) {
      return names;
    }
    for (File child : children) {
      names.putIfAbsent(child.getName().toLowerCase(Locale.ROOT), child.getName());
    }
    return names;
  }

  private static boolean sameContent(File first, File second) {
    try {
      BasicFileAttributes firstAttrs =
          Files.readAttributes(first.toPath(), BasicFileAttributes.class);
      BasicFileAttributes secondAttrs =
          Files.readAttributes(second.toPath(), BasicFileAttributes.class);
      return firstAttrs.size() == secondAttrs.size()
          && firstAttrs.lastModifiedTime().equals(secondAttrs.lastModifiedTime());
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean isLinkedDirectory(File dir) {
    Path path = dir.toPath().toAbsolutePath().normalize();
    try {
      return !path.equals(path.toRealPath());
    } catch (IOException e) {
      return true;
    }
  }

  private static void reconcileJars(File libDir, List<String> jars,
                                    ProgressIndicator indicator) throws IOException {
    mkdirs(libDir);
    Map<String, String> linkNames = jarLinkNames(jars);
    Set<String> expectedNames = new HashSet<>(linkNames.values());

    File[] existing = libDir.listFiles();
    if (existing != null) {
      for (File entry : existing) {
        if (!entry.isFile() || expectedNames.contains(entry.getName())) {
          continue;
        }
        report(indicator, "Removing jar " + entry.getName());
        deleteIfExists(entry);
      }
    }

    int total = jars.size();
    int done = 0;
    for (String path : jars) {
      File source = new File(path);
      File link = new File(libDir, linkNames.get(path));
      done++;
      if (isJarLinkValid(link, source)) {
        continue;
      }
      progress(indicator,
          "Linking jar (" + done + "/" + total + "): " + link.getName(), path, done - 1, total);
      createFileLink(link, source);
      progress(indicator,
          "Linking jar (" + done + "/" + total + "): " + link.getName(), path, done, total);
    }
  }

  private static boolean isJarLinkValid(File link, File source) {
    if (!link.isFile() || !source.isFile()) {
      return false;
    }
    return sameContent(link, source);
  }

  /**
   * Assigns a unique link file name to every jar. Jars sharing the same file name (different
   * versions or modules) would otherwise overwrite each other in the merged {@code WEB-INF/lib};
   * all but the first, deterministically, get a short path hash suffix.
   */
  private static Map<String, String> jarLinkNames(List<String> jars) {
    List<String> unique = new ArrayList<>(new LinkedHashSet<>(jars));
    Collections.sort(unique);
    Map<String, List<String>> byName = new LinkedHashMap<>();
    for (String path : unique) {
      byName.computeIfAbsent(new File(path).getName().toLowerCase(Locale.ROOT),
          k -> new ArrayList<>()).add(path);
    }
    Map<String, String> names = new LinkedHashMap<>();
    for (List<String> group : byName.values()) {
      if (group.size() == 1) {
        String path = group.get(0);
        names.put(path, new File(path).getName());
        continue;
      }
      System.out.println(LOG_PREFIX + "jar omonimi, nome disambiguato: " + group);
      boolean first = true;
      for (String path : group) {
        String basename = new File(path).getName();
        names.put(path, first ? basename : disambiguatedJarName(basename, path));
        first = false;
      }
    }
    return names;
  }

  private static String disambiguatedJarName(String basename, String path) {
    int dot = basename.lastIndexOf('.');
    String stem = dot > 0 ? basename.substring(0, dot) : basename;
    String extension = dot > 0 ? basename.substring(dot) : "";
    return stem + "-" + shortHash(path) + extension;
  }

  private static String shortHash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 non disponibile", e);
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

  private static void report(ProgressIndicator indicator, String text) {
    if (indicator == null) {
      return;
    }
    indicator.setText2(text);
    indicator.checkCanceled();
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
      try {
        Files.setLastModifiedTime(link.toPath(), Files.getLastModifiedTime(target.toPath()));
      } catch (IOException ignored) {
        // Best effort: verification will relink if the timestamps diverge.
      }
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
                                    List<String> jars,
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
      for (String jar : jars) {
        EntryInfo info = entryInfo(new File(jar));
        long size = info == null ? -1 : info.size;
        long mtime = info == null ? -1 : info.mtime;
        writer.write(size + "\t" + mtime + "\t" + jar);
        writer.newLine();
      }
      writer.write(SECTION_FILES);
      writer.newLine();
      List<String> sortedModules = new ArrayList<>(classesDirs);
      Collections.sort(sortedModules);
      for (String dir : sortedModules) {
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
        List<String> dirs = new ArrayList<>(state.dirs);
        Collections.sort(dirs);
        for (String rel : dirs) {
          writer.write(TAG_DIR + "\t" + rel);
          writer.newLine();
        }
      }
    }
  }
}
