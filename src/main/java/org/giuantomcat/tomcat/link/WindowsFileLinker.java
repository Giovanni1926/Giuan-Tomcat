package org.giuantomcat.tomcat.link;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link FileLinker} for Windows, backed by Win32/JNA native calls.
 *
 * <p>Supports NTFS directory junctions and hard links for files, and uses {@code rmdir /s /q} for
 * recursive deletion (which removes junctions as links without following them).
 *
 * <p>Junction example:
 *
 * <pre>
 *   C:\tomcat\webapps\myapp\WEB-INF\classes
 *              |
 *              +----&gt; C:\project\target\classes
 * </pre>
 */
public final class WindowsFileLinker implements FileLinker {

  private static final String RMDIR = "rmdir /s /q \"%s\"";

  /*
   * DeviceIoControl control code used to create a mount-point
   * reparse point, which is what Windows directory junctions use.
   */
  private static final int FSCTL_SET_REPARSE_POINT = 0x000900A4;

  /*
   * Reparse tag for a mount point / junction.
   */
  private static final int IO_REPARSE_TAG_MOUNT_POINT = 0xA0000003;

  /*
   * CreateFile access flags.
   */
  private static final int GENERIC_WRITE = 0x40000000;

  /*
   * File sharing flags.
   */
  private static final int FILE_SHARE_READ = 0x00000001;
  private static final int FILE_SHARE_WRITE = 0x00000002;
  private static final int FILE_SHARE_DELETE = 0x00000004;

  /*
   * CreateFile creation disposition.
   */
  private static final int OPEN_EXISTING = 3;

  /*
   * File flags.
   */
  private static final int FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
  private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;

  /*
   * ERROR_ALREADY_EXISTS
   */
  private static final int ERROR_ALREADY_EXISTS = 183;

  /*
   * ERROR_FILE_NOT_FOUND
   */
  private static final int ERROR_FILE_NOT_FOUND = 2;

  /*
   * ERROR_PATH_NOT_FOUND
   */
  private static final int ERROR_PATH_NOT_FOUND = 3;

  /*
   * ERROR_ACCESS_DENIED
   */
  private static final int ERROR_ACCESS_DENIED = 5;

  /*
   * ERROR_NOT_SAME_DEVICE
   */
  private static final int ERROR_NOT_SAME_DEVICE = 17;

  /*
   * ERROR_INVALID_PARAMETER
   */
  private static final int ERROR_INVALID_PARAMETER = 87;

  /*
   * ERROR_INVALID_REPARSE_DATA
   *
   * This is the error you were getting with the previous
   * implementation.
   */
  private static final int ERROR_INVALID_REPARSE_DATA = 4392;

  WindowsFileLinker() {
  }

  @Override
  public void createDirectoryLink(Path link, Path target) throws IOException {
    createJunction(link.toAbsolutePath().normalize(), target.toAbsolutePath().normalize());
  }

  @Override
  public void createFileLink(Path link, Path target) throws IOException {
    createHardLink(link.toAbsolutePath().normalize(), target.toAbsolutePath().normalize());
  }

  @Override
  public void deleteLink(Path link) throws IOException {
    Path path = link.toAbsolutePath().normalize();
    if (!Files.exists(path)) {
      return;
    }
    Files.delete(path);
  }

  @Override
  public void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    runCmd(String.format(RMDIR, root.toAbsolutePath()));
  }

  @Override
  public boolean isLink(Path path) {
    try {
      return Files.isSymbolicLink(path);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Creates a Windows directory junction.
   *
   * @param link   directory that will become the junction
   * @param target existing directory to which the junction points
   */
  private void createJunction(Path link, Path target) throws IOException {
    if (!Files.exists(target)) {
      throw new IOException("Junction target does not exist: " + target);
    }
    if (!Files.isDirectory(target)) {
      throw new IOException("Junction target is not a directory: " + target);
    }
    if (Files.exists(link)) {
      throw new IOException("Junction path already exists: " + link);
    }

    Files.createDirectory(link);

    WinNT.HANDLE handle = null;
    try {
      handle = Kernel32.INSTANCE.CreateFile(
          link.toString(),
          GENERIC_WRITE,
          FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
          null,
          OPEN_EXISTING,
          FILE_FLAG_OPEN_REPARSE_POINT | FILE_FLAG_BACKUP_SEMANTICS,
          null);

      if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
        int error = Kernel32.INSTANCE.GetLastError();
        throw new IOException("CreateFile failed (" + error + "): " + link);
      }

      /*
       * Junctions use an NT-style substitute name:
       *   C:\project\target\classes -> \??\C:\project\target\classes
       */
      String substituteName = "\\??\\" + target;
      byte[] substituteNameBytes = substituteName.getBytes(StandardCharsets.UTF_16LE);
      int substituteNameLength = substituteNameBytes.length;

      /*
       * REPARSE_DATA_BUFFER layout:
       *
       * Offset  Size
       * 0       4    ReparseTag
       * 4       2    ReparseDataLength
       * 6       2    Reserved
       *
       * MountPointReparseBuffer:
       * 8       2    SubstituteNameOffset
       * 10      2    SubstituteNameLength
       * 12      2    PrintNameOffset
       * 14      2    PrintNameLength
       * 16      ...  PathBuffer
       */

      /*
       * We only provide SubstituteName. PathBuffer must contain BOTH names
       * null-terminated (substituteName + print name, even when empty) or
       * ntfs.sys rejects the data with ERROR_INVALID_REPARSE_DATA (4392).
       */
      int pathBufferLength = substituteNameLength + 2 + 2;
      int reparseDataLength = 8 + pathBufferLength;
      int totalBufferSize = 8 + reparseDataLength;

      Memory buffer = new Memory(totalBufferSize);
      try {
        buffer.clear();

        // ReparseTag
        buffer.setInt(0, IO_REPARSE_TAG_MOUNT_POINT);
        // ReparseDataLength
        buffer.setShort(4, (short) reparseDataLength);
        // Reserved
        buffer.setShort(6, (short) 0);

        // SubstituteNameOffset
        buffer.setShort(8, (short) 0);
        // SubstituteNameLength (byte count, not char count)
        buffer.setShort(10, (short) substituteNameLength);
        // PrintNameOffset
        buffer.setShort(12, (short) (substituteNameLength + 2));
        // PrintNameLength
        buffer.setShort(14, (short) 0);

        // PathBuffer starts at offset 16.
        buffer.write(16, substituteNameBytes, 0, substituteNameBytes.length);
        // WCHAR NULL terminator for substituteName.
        buffer.setShort(16 + substituteNameLength, (short) 0);
        // WCHAR NULL terminator for the empty print name.
        buffer.setShort(16 + substituteNameLength + 2, (short) 0);

        IntByReferenceHolder bytesReturned = new IntByReferenceHolder();
        boolean success = Kernel32.INSTANCE.DeviceIoControl(
            handle,
            FSCTL_SET_REPARSE_POINT,
            buffer,
            totalBufferSize,
            null,
            0,
            bytesReturned.reference,
            null);

        if (!success) {
          int error = Kernel32.INSTANCE.GetLastError();
          throw new IOException(
              "DeviceIoControl failed (" + error + "): " + getWindowsErrorDescription(error)
                  + "\nLink: " + link
                  + "\nTarget: " + target);
        }
      } finally {
        buffer.clear();
      }
    } catch (IOException e) {
      try {
        Files.deleteIfExists(link);
      } catch (IOException cleanupException) {
        e.addSuppressed(cleanupException);
      }
      throw e;
    } finally {
      if (handle != null && !WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
        Kernel32.INSTANCE.CloseHandle(handle);
      }
    }
  }

  /**
   * Creates a Windows hard link.
   *
   * @param link   new file path
   * @param target existing file
   */
  private void createHardLink(Path link, Path target) throws IOException {
    if (!Files.exists(target)) {
      throw new IOException("Hard link target does not exist: " + target);
    }
    if (!Files.isRegularFile(target)) {
      throw new IOException("Hard link target is not a regular file: " + target);
    }
    if (Files.exists(link)) {
      throw new IOException("Hard link path already exists: " + link);
    }

    boolean success = Kernel32Extra.INSTANCE.CreateHardLinkW(
        link.toString(),
        target.toString(),
        null);

    if (!success) {
      int error = Kernel32.INSTANCE.GetLastError();
      throw new IOException(
          "CreateHardLink failed (" + error + "): " + getWindowsErrorDescription(error)
              + "\nLink: " + link
              + "\nTarget: " + target);
    }
  }

  /**
   * Provides a readable description for the most relevant errors used by this class.
   */
  private static String getWindowsErrorDescription(int error) {
    switch (error) {
      case ERROR_ALREADY_EXISTS:
        return "ERROR_ALREADY_EXISTS";
      case ERROR_FILE_NOT_FOUND:
        return "ERROR_FILE_NOT_FOUND";
      case ERROR_PATH_NOT_FOUND:
        return "ERROR_PATH_NOT_FOUND";
      case ERROR_ACCESS_DENIED:
        return "ERROR_ACCESS_DENIED";
      case ERROR_NOT_SAME_DEVICE:
        return "ERROR_NOT_SAME_DEVICE (link and target on different volumes?)";
      case ERROR_INVALID_PARAMETER:
        return "ERROR_INVALID_PARAMETER";
      case ERROR_INVALID_REPARSE_DATA:
        return "ERROR_INVALID_REPARSE_DATA";
      default:
        return "Windows error " + error;
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

  /**
   * JNA interface for CreateHardLinkW.
   */
  private interface Kernel32Extra extends StdCallLibrary {

    Kernel32Extra INSTANCE =
        Native.load("kernel32", Kernel32Extra.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean CreateHardLinkW(
        String lpFileName,
        String lpExistingFileName,
        WinBase.SECURITY_ATTRIBUTES lpSecurityAttributes);
  }

  /**
   * Small wrapper so we don't need to import IntByReference in the main implementation.
   */
  private static final class IntByReferenceHolder {

    final com.sun.jna.ptr.IntByReference reference = new com.sun.jna.ptr.IntByReference();
  }
}
