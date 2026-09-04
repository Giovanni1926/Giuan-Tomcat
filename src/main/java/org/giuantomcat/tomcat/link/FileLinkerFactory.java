package org.giuantomcat.tomcat.link;

/**
 * Selects the {@link FileLinker} implementation for the current operating system at runtime.
 */
public final class FileLinkerFactory {

  private static final FileLinker INSTANCE = create();

  private FileLinkerFactory() {
  }

  /**
   * @return the {@link FileLinker} matching the current {@code os.name}
   */
  public static FileLinker get() {
    return INSTANCE;
  }

  private static FileLinker create() {
    String os = System.getProperty("os.name", "");
    if (os.toLowerCase().contains("win")) {
      return new WindowsFileLinker();
    }
    return new UnixFileLinker();
  }
}
