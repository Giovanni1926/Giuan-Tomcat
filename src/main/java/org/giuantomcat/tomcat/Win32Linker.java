package org.giuantomcat.tomcat;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Windows filesystem links implemented through Win32/JNA.
 *
 * Supports:
 *
 *  - Directory junctions
 *  - Hard links for files
 *
 * Junction example:
 *
 *   C:\tomcat\webapps\myapp\WEB-INF\classes
 *              |
 *              +----> C:\project\target\classes
 *
 * Requires Windows.
 */
public final class Win32Linker {

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
    private static final int FILE_SHARE_READ   = 0x00000001;
    private static final int FILE_SHARE_WRITE  = 0x00000002;
    private static final int FILE_SHARE_DELETE = 0x00000004;

    /*
     * CreateFile creation disposition.
     */
    private static final int OPEN_EXISTING = 3;

    /*
     * File flags.
     */
    private static final int FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
    private static final int FILE_FLAG_BACKUP_SEMANTICS    = 0x02000000;

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

    private Win32Linker() {
    }

    /**
     * Creates a Windows directory junction.
     *
     * Example:
     *
     *   createJunction(
     *       new File("C:\\tomcat\\webapps\\myapp\\WEB-INF\\classes"),
     *       new File("C:\\project\\target\\classes")
     *   );
     *
     * The link directory must not already exist.
     *
     * @param link   directory that will become the junction
     * @param target existing directory to which the junction points
     */
    public static void createJunction(File link, File target)
            throws IOException {

        requireWindows();

        if (link == null) {
            throw new IllegalArgumentException("link cannot be null");
        }

        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }

        Path linkPath = link.getAbsoluteFile().toPath().normalize();
        Path targetPath = target.getAbsoluteFile().toPath().normalize();

        /*
         * Junction target must exist and must be a directory.
         */
        if (!Files.exists(targetPath)) {
            throw new IOException(
                    "Junction target does not exist: " + targetPath
            );
        }

        if (!Files.isDirectory(targetPath)) {
            throw new IOException(
                    "Junction target is not a directory: " + targetPath
            );
        }

        /*
         * A junction cannot be created over an existing path.
         */
        if (Files.exists(linkPath)) {
            throw new IOException(
                    "Junction path already exists: " + linkPath
            );
        }

        /*
         * Windows junctions are created on top of an existing
         * directory.
         */
        Files.createDirectory(linkPath);

        WinNT.HANDLE handle = null;

        try {
            handle = Kernel32.INSTANCE.CreateFile(
                    linkPath.toString(),

                    GENERIC_WRITE,

                    FILE_SHARE_READ
                            | FILE_SHARE_WRITE
                            | FILE_SHARE_DELETE,

                    null,

                    OPEN_EXISTING,

                    FILE_FLAG_OPEN_REPARSE_POINT
                            | FILE_FLAG_BACKUP_SEMANTICS,

                    null
            );

            if (handle == null
                    || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {

                int error = Kernel32.INSTANCE.GetLastError();

                throw new IOException(
                        "CreateFile failed (" + error + "): "
                                + linkPath
                );
            }

            /*
             * Junctions use an NT-style substitute name.
             *
             * Example:
             *
             *   C:\project\target\classes
             *
             * becomes:
             *
             *   \??\C:\project\target\classes
             */
            String substituteName =
                    "\\??\\" + targetPath;

            byte[] substituteNameBytes =
                    substituteName.getBytes(StandardCharsets.UTF_16LE);

            int substituteNameLength =
                    substituteNameBytes.length;

            /*
             * REPARSE_DATA_BUFFER layout:
             *
             * Offset  Size
             *
             * 0       4    ReparseTag
             * 4       2    ReparseDataLength
             * 6       2    Reserved
             *
             * MountPointReparseBuffer:
             *
             * 8       2    SubstituteNameOffset
             * 10      2    SubstituteNameLength
             * 12      2    PrintNameOffset
             * 14      2    PrintNameLength
             *
             * 16      ...  PathBuffer
             */

            /*
             * We only provide SubstituteName.
             *
             * PathBuffer must contain BOTH names null-terminated:
             *
             *   substituteName
             *   WCHAR '\0'
             *   printName (empty)
             *   WCHAR '\0'
             *
             * Even when the print name is empty its
             * null terminator must be present, otherwise
             * ntfs.sys rejects the data with
             * ERROR_INVALID_REPARSE_DATA (4392).
             */
            int pathBufferLength =
                    substituteNameLength + 2 + 2;

            /*
             * MountPointReparseBuffer:
             *
             * 8 bytes of fields
             * + PathBuffer
             */
            int reparseDataLength =
                    8 + pathBufferLength;

            /*
             * Full REPARSE_DATA_BUFFER:
             *
             * 8 byte header
             * + reparseDataLength
             */
            int totalBufferSize =
                    8 + reparseDataLength;

            Memory buffer =
                    new Memory(totalBufferSize);

            try {
                buffer.clear();

                /*
                 * -------------------------------------------------
                 * REPARSE_DATA_BUFFER header
                 * -------------------------------------------------
                 */

                // ReparseTag
                buffer.setInt(
                        0,
                        IO_REPARSE_TAG_MOUNT_POINT
                );

                // ReparseDataLength
                buffer.setShort(
                        4,
                        (short) reparseDataLength
                );

                // Reserved
                buffer.setShort(
                        6,
                        (short) 0
                );

                /*
                 * -------------------------------------------------
                 * MountPointReparseBuffer
                 * -------------------------------------------------
                 */

                /*
                 * SubstituteNameOffset
                 *
                 * PathBuffer starts immediately with
                 * SubstituteName.
                 */
                buffer.setShort(
                        8,
                        (short) 0
                );

                /*
                 * SubstituteNameLength
                 *
                 * IMPORTANT:
                 *
                 * This is a BYTE count, not a character count.
                 */
                buffer.setShort(
                        10,
                        (short) substituteNameLength
                );

                /*
                 * PrintNameOffset
                 *
                 * It starts after:
                 *
                 * substituteName
                 * + terminating WCHAR
                 */
                buffer.setShort(
                        12,
                        (short) (substituteNameLength + 2)
                );

                /*
                 * PrintNameLength
                 *
                 * We don't provide a print name.
                 */
                buffer.setShort(
                        14,
                        (short) 0
                );

                /*
                 * PathBuffer starts at offset 16.
                 */
                buffer.write(
                        16,
                        substituteNameBytes,
                        0,
                        substituteNameBytes.length
                );

                /*
                 * WCHAR NULL terminator for substituteName.
                 */
                buffer.setShort(
                        16 + substituteNameLength,
                        (short) 0
                );

                /*
                 * WCHAR NULL terminator for the empty print name.
                 */
                buffer.setShort(
                        16 + substituteNameLength + 2,
                        (short) 0
                );

                /*
                 * -------------------------------------------------
                 * Send REPARSE_DATA_BUFFER to Windows
                 * -------------------------------------------------
                 */

                IntByReferenceHolder bytesReturned =
                        new IntByReferenceHolder();

                boolean success =
                        Kernel32.INSTANCE.DeviceIoControl(
                                handle,

                                FSCTL_SET_REPARSE_POINT,

                                buffer,
                                totalBufferSize,

                                null,
                                0,

                                bytesReturned.reference,

                                null
                        );

                if (!success) {

                    int error =
                            Kernel32.INSTANCE.GetLastError();

                    throw new IOException(
                            "DeviceIoControl failed ("
                                    + error
                                    + "): "
                                    + getWindowsErrorDescription(error)
                                    + "\nLink: "
                                    + linkPath
                                    + "\nTarget: "
                                    + targetPath
                    );
                }

            } finally {
                buffer.clear();
            }

        } catch (IOException e) {

            /*
             * If creation fails, the directory we created above
             * is still there.
             *
             * Remove it so the caller doesn't get a leftover
             * normal directory.
             */
            try {
                Files.deleteIfExists(linkPath);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }

            throw e;

        } finally {

            if (handle != null
                    && !WinBase.INVALID_HANDLE_VALUE.equals(handle)) {

                Kernel32.INSTANCE.CloseHandle(handle);
            }
        }
    }

    /**
     * Creates a Windows hard link.
     *
     * Hard links work for files, not directories.
     *
     * @param link   new file path
     * @param target existing file
     */
    public static void createHardLink(
            File link,
            File target
    ) throws IOException {

        requireWindows();

        if (link == null) {
            throw new IllegalArgumentException("link cannot be null");
        }

        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }

        Path linkPath =
                link.getAbsoluteFile().toPath().normalize();

        Path targetPath =
                target.getAbsoluteFile().toPath().normalize();

        if (!Files.exists(targetPath)) {
            throw new IOException(
                    "Hard link target does not exist: "
                            + targetPath
            );
        }

        if (!Files.isRegularFile(targetPath)) {
            throw new IOException(
                    "Hard link target is not a regular file: "
                            + targetPath
            );
        }

        if (Files.exists(linkPath)) {
            throw new IOException(
                    "Hard link path already exists: "
                            + linkPath
            );
        }

        boolean success =
                Kernel32Extra.INSTANCE.CreateHardLinkW(
                        linkPath.toString(),
                        targetPath.toString(),
                        null
                );

        if (!success) {

            int error =
                    Kernel32.INSTANCE.GetLastError();

            throw new IOException(
                    "CreateHardLink failed ("
                            + error
                            + "): "
                            + getWindowsErrorDescription(error)
                            + "\nLink: "
                            + linkPath
                            + "\nTarget: "
                            + targetPath
            );
        }
    }

    /**
     * Deletes a junction, symbolic link or normal empty directory.
     *
     * For a junction this removes the junction itself and DOES NOT
     * delete the target directory.
     *
     * @param link path to remove
     */
    public static void deleteLink(File link)
            throws IOException {

        if (link == null) {
            throw new IllegalArgumentException("link cannot be null");
        }

        Path path =
                link.getAbsoluteFile().toPath().normalize();

        if (!Files.exists(path)) {
            return;
        }

        /*
         * Files.delete() on a junction removes the junction itself.
         * It does not recursively delete its target.
         */
        Files.delete(path);
    }

    /**
     * Basic platform validation.
     */
    private static void requireWindows() {

        String os =
                System.getProperty("os.name", "");

        if (!os.toLowerCase().contains("win")) {
            throw new UnsupportedOperationException(
                    "Win32Linker can only be used on Windows"
            );
        }
    }

    /**
     * Provides a readable description for the most relevant
     * errors used by this class.
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

    /**
     * JNA interface for CreateHardLinkW.
     */
    private interface Kernel32Extra extends StdCallLibrary {

        Kernel32Extra INSTANCE =
                Native.load(
                        "kernel32",
                        Kernel32Extra.class,
                        W32APIOptions.DEFAULT_OPTIONS
                );

        boolean CreateHardLinkW(
                String lpFileName,
                String lpExistingFileName,
                WinBase.SECURITY_ATTRIBUTES lpSecurityAttributes
        );
    }

    /**
     * Small wrapper so we don't need to import IntByReference
     * in the main implementation.
     */
    private static final class IntByReferenceHolder {

        final com.sun.jna.ptr.IntByReference reference =
                new com.sun.jna.ptr.IntByReference();
    }
}

