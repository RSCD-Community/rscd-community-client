package org.rscdaemon.client.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/*
 * Hands an rscd:// join link to the client that is already running, instead of
 * starting a second one.
 *
 * The OS scheme handler has no idea whether anything is open -- the desktop
 * entry is `run.sh %u`, so every click on a Join Now link is a fresh JVM, a
 * fresh asset download and another window. A player who clicks two worlds
 * while deciding ends up with two clients, which is not what clicking a link
 * in a browser means anywhere else.
 *
 * The rule is deliberately narrow: a running client takes the link ONLY if
 * nobody is signed in on it. Someone mid-game is not interrupted, and their
 * session is never closed to make room for a link -- the second click opens
 * its own window, exactly as it does today. Not signed in means the player is
 * sitting on the Worlds or sign-in screen, which is precisely the state where
 * "just load it here" is what they wanted.
 *
 * Mechanics: the running client listens on an ephemeral LOOPBACK port and
 * writes that port plus a random token to a file under the user's home. A new
 * process reads the file, connects, presents the token and offers the link,
 * and exits only if the answer is TAKEN. Anything else -- no file, stale file,
 * connection refused, wrong token, a client that is signed in, no answer
 * within the timeout -- falls through to a normal launch. Every failure mode
 * lands on the behaviour we already had, which is why this can be wired in
 * ahead of the game rather than around it.
 *
 * The token is not ceremony. Without it any local process could tell the
 * client to go and sit on a sign-in screen belonging to a server of its
 * choosing, which is a plausible way to collect a password on a shared
 * machine. Reading the file requires being the user already.
 */
public final class SingleInstance {

   /*
    * What the running client does with an offered link. Returning false means
    * "not now" and the caller starts its own instance; nothing is queued.
    */
   public interface Joiner {
      boolean join(String uri);
   }

   private static final String PROTOCOL = "RSCD1";
   private static final String ACCEPTED = "TAKEN";
   private static final String DECLINED = "BUSY";
   /* Long enough for a loopback round trip against a client that is busy
      drawing, short enough that a wedged instance cannot stop a launch. */
   private static final int HANDOFF_TIMEOUT = 2000;
   private static final int LINE_LIMIT = 2048;

   private static ServerSocket listener;
   private static String token;

   private SingleInstance() {
   }

   /*
    * Offer the link to a client that is already running.
    *
    * @return true if one took it, in which case this process has nothing left
    *         to do and should exit without opening a window.
    */
   public static boolean handoff(String uri) {
      if (uri == null || uri.length() == 0) {
         return false;
      }

      String[] lock = readLock();
      if (lock == null) {
         return false;
      }

      int port;
      try {
         port = Integer.parseInt(lock[0]);
      } catch (NumberFormatException e) {
         return false;
      }
      if (port <= 0 || port > 65535) {
         return false;
      }

      Socket socket = null;
      try {
         socket = new Socket();
         socket.connect(new java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), HANDOFF_TIMEOUT);
         socket.setSoTimeout(HANDOFF_TIMEOUT);

         Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
         out.write(PROTOCOL + " " + lock[1] + " JOIN " + uri + "\n");
         out.flush();

         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
         String reply = in.readLine();
         return ACCEPTED.equals(reply == null ? null : reply.trim());
      } catch (IOException e) {
         /* Refused, stale, or wedged -- all of them mean "launch normally".
            Deliberately silent: a leftover lock file from a client that was
            killed is the common case and is not worth a scary line. */
         return false;
      } finally {
         close(socket);
      }
   }

   /*
    * Become the instance that answers join links. Safe to call once, early;
    * failing to listen is not an error worth stopping a launch over, so this
    * reports rather than throws.
    */
   public static synchronized void listen(final Joiner joiner) {
      if (listener != null || joiner == null) {
         return;
      }

      try {
         listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
      } catch (IOException e) {
         System.out.println("Join links will open a new window: " + e.getMessage());
         return;
      }

      token = Long.toHexString(new SecureRandom().nextLong());
      if (!writeLock(listener.getLocalPort(), token)) {
         close(listener);
         listener = null;
         return;
      }

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
         @Override
         public void run() {
            stop();
         }
      }));

      Thread thread = new Thread(new Runnable() {
         @Override
         public void run() {
            accept(joiner);
         }
      }, "rscd-join-links");
      thread.setDaemon(true);
      thread.start();
   }

   private static void accept(Joiner joiner) {
      while (true) {
         ServerSocket current;
         synchronized (SingleInstance.class) {
            current = listener;
         }
         if (current == null || current.isClosed()) {
            return;
         }

         Socket socket = null;
         try {
            socket = current.accept();
            socket.setSoTimeout(HANDOFF_TIMEOUT);
            serve(socket, joiner);
         } catch (IOException e) {
            /* A closed listener is how stop() ends this thread. Anything else
               is one bad caller and must not take the loop down with it. */
            synchronized (SingleInstance.class) {
               if (listener == null || listener.isClosed()) {
                  return;
               }
            }
         } finally {
            close(socket);
         }
      }
   }

   private static void serve(Socket socket, Joiner joiner) throws IOException {
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      String line = in.readLine();
      Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);

      String uri = parse(line);
      /* Answer the same way for a bad token and a declined link. There is
         nothing to be gained by telling a caller which of the two it hit. */
      out.write((uri != null && joiner.join(uri) ? ACCEPTED : DECLINED) + "\n");
      out.flush();
   }

   /* "RSCD1 <token> JOIN <uri>" -- returns the uri, or null if this is not a
      well-formed request from someone holding our token. */
   private static String parse(String line) {
      if (line == null || line.length() > LINE_LIMIT || token == null) {
         return null;
      }

      String[] parts = line.trim().split(" ", 4);
      if (parts.length != 4 || !PROTOCOL.equals(parts[0]) || !"JOIN".equals(parts[2])) {
         return null;
      }
      if (!token.equals(parts[1])) {
         return null;
      }

      String uri = parts[3].trim();
      return uri.regionMatches(true, 0, "rscd://", 0, 7) ? uri : null;
   }

   /* Stop answering and take the lock file with us, so the next launch does
      not spend its timeout talking to a port nobody is on. */
   public static synchronized void stop() {
      if (listener != null) {
         close(listener);
         listener = null;
      }
      File file = lockFile();
      if (file != null) {
         file.delete();
      }
   }

   private static String[] readLock() {
      File file = lockFile();
      if (file == null || !file.isFile()) {
         return null;
      }

      BufferedReader in = null;
      try {
         in = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8));
         String port = in.readLine();
         String secret = in.readLine();
         if (port == null || secret == null) {
            return null;
         }
         return new String[]{port.trim(), secret.trim()};
      } catch (IOException e) {
         return null;
      } finally {
         if (in != null) {
            try {
               in.close();
            } catch (IOException ignored) {
            }
         }
      }
   }

   private static boolean writeLock(int port, String secret) {
      File file = lockFile();
      if (file == null) {
         return false;
      }

      /* Narrow the permissions before anything is written, not after: the
         token is the only thing standing between a local process and the
         ability to point this client at a server of its choosing. */
      try {
         file.delete();
         file.createNewFile();
         file.setReadable(false, false);
         file.setWritable(false, false);
         file.setReadable(true, true);
         file.setWritable(true, true);
      } catch (IOException e) {
         return false;
      }

      Writer out = null;
      try {
         out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
         out.write(port + "\n" + secret + "\n");
         out.flush();
         return true;
      } catch (IOException e) {
         return false;
      } finally {
         if (out != null) {
            try {
               out.close();
            } catch (IOException ignored) {
            }
         }
      }
   }

   /*
    * Under the user's home rather than beside the jar: the jar may sit in a
    * read-only system directory, and two people logged into the same machine
    * must not hand each other's clients their links.
    */
   private static File lockFile() {
      String home = System.getProperty("user.home", "");
      File dir;
      if (home.length() > 0) {
         dir = new File(home, ".rscd");
      } else {
         dir = new File(System.getProperty("java.io.tmpdir", "."),
               "rscd-" + System.getProperty("user.name", "user"));
      }
      if (!dir.isDirectory() && !dir.mkdirs()) {
         return null;
      }
      return new File(dir, "instance");
   }

   private static void close(ServerSocket socket) {
      if (socket != null) {
         try {
            socket.close();
         } catch (IOException ignored) {
         }
      }
   }

   private static void close(Socket socket) {
      if (socket != null) {
         try {
            socket.close();
         } catch (IOException ignored) {
         }
      }
   }
}
