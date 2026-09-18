package org.giuantomcat.tomcat;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Guards a generated runtime root against concurrent runs of the same configuration.
 *
 * <p>A marker file ({@code instance.pid}) records the process that currently owns the instance: the
 * IDE pid while the run is being prepared, then the Tomcat pid once the JVM has started. Another
 * run of the same configuration is refused while that process is alive, so a new consolidation can
 * never mutate the merged tree while a previous Tomcat is still reading it. Stale markers left by a
 * crashed process are ignored automatically because the recorded pid is no longer alive.
 *
 * <p>The check-and-claim sequence is serialized with a short-lived file lock, so two IDE instances
 * starting the same configuration at the same time cannot both claim the instance.
 */
public final class TomcatRunLock {

  private static final String MARKER_NAME = "instance.pid";
  private static final String CLAIM_NAME = "instance.lock";
  private static final int CLAIM_ATTEMPTS = 20;
  private static final long CLAIM_RETRY_MILLIS = 100L;

  private final File marker;
  private long ownerPid;

  private TomcatRunLock(File marker, long ownerPid) {
    this.marker = marker;
    this.ownerPid = ownerPid;
  }

  /**
   * Claims the runtime root for the current process.
   *
   * @param runtimeRoot runtime root shared by all runs of one configuration
   * @return the acquired lock, to be {@link #release() released} when the run terminates
   * @throws IOException if the instance is already running or the lock cannot be acquired
   */
  public static TomcatRunLock acquire(File runtimeRoot) throws IOException {
    Files.createDirectories(runtimeRoot.toPath());
    Path markerFile = new File(runtimeRoot, MARKER_NAME).toPath();
    Path claimFile = new File(runtimeRoot, CLAIM_NAME).toPath();

    try (FileChannel channel = FileChannel.open(claimFile,
        StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      FileLock claim = tryClaim(channel);
      if (claim == null) {
        throw new IOException("impossibile acquisire il lock di avvio in " + claimFile
            + ": un'altra richiesta di avvio e' in corso, riprova tra qualche secondo");
      }
      try {
        long active = activePid(markerFile);
        if (active > 0) {
          throw new IOException("un'istanza di Tomcat di questa configurazione e' gia' attiva (pid "
              + active + "): fermala prima di riavviarla. Se non e' piu' attiva elimina il file "
              + markerFile);
        }
        long pid = ProcessHandle.current().pid();
        writePid(markerFile, pid);
        return new TomcatRunLock(markerFile.toFile(), pid);
      } finally {
        claim.release();
      }
    }
  }

  /**
   * Binds the lock to the started Tomcat process, so subsequent runs keep being refused until the
   * server actually terminates (even if the IDE debug session is detached).
   *
   * @param pid process id of the launched Tomcat
   */
  public void update(long pid) throws IOException {
    if (pid <= 0) {
      return;
    }
    writePid(marker.toPath(), pid);
    ownerPid = pid;
  }

  /** Removes the marker when it still belongs to this run. */
  public void release() {
    try {
      if (Files.isRegularFile(marker.toPath()) && readPid(marker.toPath()) == ownerPid) {
        Files.deleteIfExists(marker.toPath());
      }
    } catch (IOException ignored) {
      // Best effort: a stale marker is ignored at the next start because its pid is dead.
    }
  }

  private static FileLock tryClaim(FileChannel channel) throws IOException {
    for (int attempt = 0; attempt < CLAIM_ATTEMPTS; attempt++) {
      try {
        FileLock lock = channel.tryLock();
        if (lock != null) {
          return lock;
        }
      } catch (OverlappingFileLockException e) {
        // Another thread of this JVM is claiming right now.
      }
      try {
        Thread.sleep(CLAIM_RETRY_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }

  private static long activePid(Path marker) {
    long pid = readPid(marker);
    if (pid <= 0) {
      return -1;
    }
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) ? pid : -1;
  }

  private static void writePid(Path marker, long pid) throws IOException {
    Files.write(marker, (pid + "\n").getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
  }

  private static long readPid(Path marker) {
    try {
      if (!Files.isRegularFile(marker)) {
        return -1;
      }
      String text = new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim();
      return Long.parseLong(text);
    } catch (Exception e) {
      return -1;
    }
  }
}
