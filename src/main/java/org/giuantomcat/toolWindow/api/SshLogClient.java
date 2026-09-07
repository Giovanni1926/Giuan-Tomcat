package org.giuantomcat.toolWindow.api;

import com.intellij.openapi.application.ApplicationManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.giuantomcat.GiuanTomcatConstants;
import org.giuantomcat.toolWindow.model.SshAuthMethod;
import org.giuantomcat.toolWindow.model.TomcatInstance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Streams a remote Tomcat log over SSH using {@code tail -f}.
 *
 * <p>Both authentication methods (password and private key) are supported. The returned
 * {@link TailHandle} cancels the underlying channel/session/client. Callbacks are invoked on a
 * background thread.
 */
public final class SshLogClient {

  /** Cancels an active log tail. */
  public interface TailHandle {
    void close();
  }

  public TailHandle tail(TomcatInstance instance,
                         String sshPassword,
                         String sshPassphrase,
                         Consumer<String> onLine,
                         Consumer<Throwable> onError) {
    String host = instance.sshHost == null || instance.sshHost.trim().isEmpty()
        ? instance.host
        : instance.sshHost;

    SshClient client = SshClient.setUpDefaultClient();
    client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
    client.start();

    LineOutputStream output = new LineOutputStream(onLine);
    ClientSession[] sessionRef = new ClientSession[1];
    ChannelExec[] channelRef = new ChannelExec[1];

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        ConnectFuture connectFuture = client.connect(instance.sshUser, host, instance.sshPort);
        if (!connectFuture.await(GiuanTomcatConstants.SSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          throw new IOException("SSH connection timeout to " + host + ":" + instance.sshPort);
        }
        ClientSession session = connectFuture.getSession();
        sessionRef[0] = session;

        if (instance.sshAuth == SshAuthMethod.KEY) {
          FileKeyPairProvider provider = new FileKeyPairProvider(Paths.get(instance.sshKeyPath));
          if (sshPassphrase != null && !sshPassphrase.isEmpty()) {
            provider.setPasswordFinder(FilePasswordProvider.of(sshPassphrase));
          }
          session.setKeyIdentityProvider(provider);
        } else {
          session.addPasswordIdentity(sshPassword == null ? "" : sshPassword);
        }
        session.auth().verify(GiuanTomcatConstants.SSH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        String command = "tail -n " + GiuanTomcatConstants.LOG_TAIL_LINES
            + " -f " + shellQuote(instance.logFile);
        ChannelExec channel = session.createExecChannel(command);
        channelRef[0] = channel;
        channel.setIn(new ByteArrayInputStream(new byte[0]));
        channel.setOut(output);
        channel.setErr(output);
        channel.open().verify(GiuanTomcatConstants.SSH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      } catch (Throwable t) {
        if (onError != null) {
          onError.accept(t);
        }
        closeQuietly(client, sessionRef[0], channelRef[0]);
      }
    });

    return () -> closeQuietly(client, sessionRef[0], channelRef[0]);
  }

  private static void closeQuietly(SshClient client, ClientSession session, ChannelExec channel) {
    if (channel != null) {
      channel.close(true);
    }
    if (session != null) {
      session.close(true);
    }
    client.stop();
  }

  private static String shellQuote(String value) {
    String safe = value == null ? "" : value;
    return "'" + safe.replace("'", "'\\''") + "'";
  }

  /** Converts the byte stream produced by {@code tail -f} into lines and forwards them. */
  private static final class LineOutputStream extends OutputStream {

    private final Consumer<String> myConsumer;
    private final ByteArrayOutputStream myBuffer = new ByteArrayOutputStream();

    private LineOutputStream(Consumer<String> consumer) {
      myConsumer = consumer;
    }

    @Override
    public void write(int b) {
      if (b == '\n') {
        flushLine();
      } else {
        myBuffer.write(b);
      }
    }

    private void flushLine() {
      String line = myBuffer.toString(StandardCharsets.UTF_8);
      myBuffer.reset();
      if (line.endsWith("\r")) {
        line = line.substring(0, line.length() - 1);
      }
      if (myConsumer != null) {
        myConsumer.accept(line);
      }
    }

    @Override
    public void close() {
      flushLine();
    }
  }
}
