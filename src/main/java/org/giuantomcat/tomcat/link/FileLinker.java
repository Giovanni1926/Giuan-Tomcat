package org.giuantomcat.tomcat.link;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Filesystem-linking operations that have an OS-specific implementation.
 *
 * <p>Implemented by {@link WindowsFileLinker} and {@link UnixFileLinker}; the concrete instance is
 * selected at runtime by {@link FileLinkerFactory#get()} (see {@code STYLE.md}).
 */
public interface FileLinker {

  /**
   * Creates a directory link ({@code link}) pointing at {@code target}.
   *
   * @param link   path that will become the link
   * @param target existing directory the link points to
   */
  void createDirectoryLink(Path link, Path target) throws IOException;

  /**
   * Creates a file link ({@code link}) pointing at {@code target}.
   *
   * @param link   path that will become the link
   * @param target existing file the link points to
   */
  void createFileLink(Path link, Path target) throws IOException;

  /**
   * Deletes a single link (or any existing file/directory) without following it.
   *
   * @param link path to remove
   */
  void deleteLink(Path link) throws IOException;

  /**
   * Recursively deletes a tree. Any link encountered is removed as a link, never followed.
   *
   * @param root path of the tree to delete
   */
  void deleteRecursively(Path root) throws IOException;

  /**
   * Returns {@code true} if {@code path} is a directory/file link (junction, symlink, hard link).
   *
   * @param path path to inspect
   */
  boolean isLink(Path path);

  /**
   * Returns {@code true} if {@code link} exists and resolves to {@code target}.
   *
   * <p>Used to validate the consolidated tree without trusting any recorded state: works for NTFS
   * junctions, Unix symlinks and, on Windows, also for links pointing to a file (the OS resolves
   * them). A missing link, a dangling link or a real directory obtained by copying returns
   * {@code false}, so the caller rebuilds it.
   *
   * @param link   path to inspect
   * @param target expected target directory
   */
  default boolean isDirectoryLinkTo(Path link, Path target) {
    try {
      return Files.exists(link) && Files.exists(target) && Files.isSameFile(link, target);
    } catch (IOException e) {
      return false;
    }
  }
}
