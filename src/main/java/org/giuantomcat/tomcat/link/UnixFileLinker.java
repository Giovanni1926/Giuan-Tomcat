package org.giuantomcat.tomcat.link;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * {@link FileLinker} for Unix-like systems: uses symbolic links for directories, hard links for
 * files and {@link Files#walkFileTree} for recursive deletion.
 */
public final class UnixFileLinker implements FileLinker {

  UnixFileLinker() {
  }

  @Override
  public void createDirectoryLink(Path link, Path target) throws IOException {
    Files.createSymbolicLink(link, target);
  }

  @Override
  public void createFileLink(Path link, Path target) throws IOException {
    Files.createLink(link, target);
  }

  @Override
  public void deleteLink(Path link) throws IOException {
    Files.deleteIfExists(link);
  }

  @Override
  public void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    Files.walkFileTree(root, new RecursiveDeleteVisitor());
  }

  @Override
  public boolean isLink(Path path) {
    try {
      return Files.isSymbolicLink(path);
    } catch (Exception e) {
      return false;
    }
  }
}
