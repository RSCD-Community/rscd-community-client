package org.rscdaemon.client;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/*
 * Compiles, loads and runs a script.
 *
 * STS shelled out to javac, which is why half its readme is people reporting
 * "'javac' is not recognized as an internal or external command". This uses
 * javax.tools instead, so compilation happens in-process with no PATH to get
 * wrong -- but that needs a JDK, because a JRE ships no compiler. When there
 * isn't one, an already-compiled .class next to the source is used instead, so
 * a player on a plain JRE can still run scripts someone else built.
 *
 * Scripts are the ones people wrote in 2005-2006 and are expected to compile
 * untouched. They carry no package declaration and no imports, so the source is
 * staged with "import org.rscdaemon.client.*;" prepended -- that single line is
 * the whole compatibility layer, because the base class is still called Methods
 * and this client's class is still called mudclient.
 *
 * The script gets its own thread. It is never trusted to end politely: End()
 * only sets a flag that Running() reports, so stop() also interrupts, which is
 * what wakes a script parked in Wait().
 */
public final class ScriptRunner {
   /* Prepended to script source; the reason 2006 scripts still compile. */
   private static final String PREAMBLE = "import org.rscdaemon.client.*;";
   private static final int STOP_GRACE_MS = 2000;

   private final mudclient rs;
   private final File scriptDir;
   private final File classDir;

   private Methods script;
   private Thread thread;
   private String scriptName = "";
   /* When the current script was started, for the menu's uptime line. */
   private long startedAt;
   /* What the last start() ran. Deliberately NOT cleared by stop(): a server
      restart kills the running script, and the panel's Play button brings
      this back rather than making the player re-pick it every time. */
   private String lastName = "";
   private String[] lastArgs = new String[0];
   /* True from start() until the script is stopped or returns on its own:
      this client session has a live bot that must survive every
      interruption. Strictly in-RAM on purpose -- a script belongs to the
      session that started it, and a freshly launched client must never
      start one by itself. */
   private volatile boolean sessionActive;
   /* Set by stop() before the interrupt so the thread wrapper can tell a
      stop from a crash -- an interrupt surfaces as a Throwable either way. */
   private volatile boolean stopRequested;
   /* A login just happened; the next pump() restarts the session's script
      from the top, because a script that lived through the outage is looping
      on a world that vanished under it -- stale flags, stale indexes. */
   private volatile boolean restartPending;
   /* Crash backoff: no resume before this, so a script that dies on its
      first breath retries every RESUME_BACKOFF_MS, not hot. */
   private volatile long resumeNotBefore;
   private static final int RESUME_BACKOFF_MS = 10000;
   /* What the player typed into the script's setup prompts, in order -- raw,
      as typed, GetOption answers included as their numbers. The scripts of
      2005 take no arguments; they interview the player in MainBody, so what
      has to be remembered to start one again is the interview. An automatic
      start feeds these back through ScriptPrompt and the script never knows
      nobody was there. Persisted after a marker line in last-script.txt. */
   private final List<String> answers = new ArrayList<String>();
   private static final String ANSWERS_MARKER = "--answers--";
   /* The snapshot the current automatic run is replaying; null while a run
      is interactive. Consumed only by the script's own thread. */
   private volatile String[] replay;
   private volatile int replayAt;
   /* True while the current run was started by the machinery -- the login
      restart or the panel's Play -- rather than by a person picking the
      script. Prompts answer themselves from the replay and ShowMessage
      boxes do not wait for an Enter nobody is there to press. */
   private volatile boolean autoStarted;

   /*
    * Events are handed to the script on this queue rather than on the client
    * thread, because a handler that decides to Wait() would otherwise freeze
    * the game. STS started a fresh Thread per event, which in a crowded bank
    * means a thread per chat line and no ordering guarantee at all; one
    * consumer keeps events in the order they arrived and costs nothing when
    * no script is loaded. A handler that blocks forever backs its own queue
    * up and nothing else -- the client keeps running.
    */
   private final java.util.concurrent.BlockingQueue<Runnable> events =
      new java.util.concurrent.LinkedBlockingQueue<Runnable>();
   private Thread dispatcher;

   public ScriptRunner(mudclient rs, File scriptDir) {
      this(rs, scriptDir, new File(scriptDir.getAbsoluteFile().getParentFile(), "script-bin"));
   }

   /*
    * Compiled output lives OUTSIDE the scripts folder, so scripts/ holds
    * nothing but scripts. It used to be scripts/bin, which meant the folder a
    * person edits also filled up with generated .java and .class files -- and
    * two of those generated files, the staged sources, are copies of their own
    * scripts with a line stapled on the front, which is confusing to find next
    * to the original.
    */
   public ScriptRunner(mudclient rs, File scriptDir, File classDir) {
      this.rs = rs;
      this.scriptDir = scriptDir;
      this.classDir = classDir;
   }

   public boolean isRunning() {
      return this.thread != null && this.thread.isAlive() && this.script != null && this.script.isScriptRunning();
   }

   public String getScriptName() {
      return this.scriptName;
   }

   /** The last script start() ran, "" before the first. Survives stop(),
       and -- through a file in the class directory -- client restarts. */
   public String getLastName() {
      if (this.lastName.length() == 0) {
         this.loadLast();
      }
      return this.lastName;
   }

   public String[] getLastArgs() {
      if (this.lastName.length() == 0) {
         this.loadLast();
      }
      return this.lastArgs;
   }

   private File lastFile() {
      return new File(this.classDir, "last-script.txt");
   }

   /* First line the name, one argument per following line. Both halves are
      best-effort: a bot box whose disk refuses the note should still bot.
      This is the Play button's memory and nothing more -- deliberately no
      run-state in it, so no launch of the client ever starts a script that
      a person did not ask for. */
   private void saveLast() {
      try {
         this.classDir.mkdirs();
         java.io.PrintWriter out = new java.io.PrintWriter(this.lastFile(), "UTF-8");
         try {
            out.println(this.lastName);
            for (String arg : this.lastArgs) {
               out.println(arg);
            }
            out.println(ANSWERS_MARKER);
            synchronized (this.answers) {
               for (String answer : this.answers) {
                  out.println(answer);
               }
            }
         } finally {
            out.close();
         }
      } catch (Exception e) {
      }
   }

   private void loadLast() {
      try {
         java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(
            new java.io.FileInputStream(this.lastFile()), "UTF-8"));
         try {
            String name = in.readLine();
            /* Skip the state token a short-lived format wrote in line one. */
            if (name != null && (name.equals("running") || name.equals("done") || name.equals("stopped"))) {
               name = in.readLine();
            }
            if (name == null || name.trim().length() == 0) {
               return;
            }
            java.util.List<String> args = new java.util.ArrayList<String>();
            java.util.List<String> saved = new java.util.ArrayList<String>();
            java.util.List<String> into = args;
            String line;
            while ((line = in.readLine()) != null) {
               if (line.equals(ANSWERS_MARKER)) {
                  into = saved;
                  continue;
               }
               if (line.length() > 0) {
                  into.add(line);
               }
            }
            this.lastName = name.trim();
            this.lastArgs = args.toArray(new String[0]);
            synchronized (this.answers) {
               this.answers.clear();
               this.answers.addAll(saved);
            }
         } finally {
            in.close();
         }
      } catch (Exception e) {
      }
   }

   /** A login just completed; the next pump() acts on it. */
   public void loginHappened() {
      this.restartPending = true;
   }

   /**
    * The supervision tick, called by the client while logged in. Returns the
    * name of a script that should be (re)started right now, or null.
    *
    * Only a script this session started and never told to stop is ever
    * returned. Two things bring one back: a fresh login (even with the
    * thread alive -- it lived through the outage looping on stale state, so
    * it starts over), and a crash (the wrapper keeps the session active and
    * sets the backoff). Stopping it, or the script ending itself, is final.
    */
   public String pump() {
      if (this.restartPending) {
         this.restartPending = false;
         if (this.sessionActive && this.lastName.length() > 0) {
            return this.lastName;
         }
         return null;
      }
      if (this.sessionActive && !this.isRunning() && this.lastName.length() > 0
            && System.currentTimeMillis() >= this.resumeNotBefore) {
         return this.lastName;
      }
      return null;
   }

   /** A resume attempt failed to load; hold off a backoff's worth. */
   public void resumeFailed() {
      this.resumeNotBefore = System.currentTimeMillis() + RESUME_BACKOFF_MS;
   }

   /** How long the current script has been up, or 0 when nothing is running. */
   public long runtimeMillis() {
      return this.startedAt == 0L ? 0L : System.currentTimeMillis() - this.startedAt;
   }

   /** The running script, or null. Used to dispatch events and to draw ToShow(). */
   public Methods getScript() {
      return this.script;
    }

   /*
    * Load a script by name, compiling it if there is a newer .java than .class.
    * Returns the instance, ready to start.
    */
   /*
    * The scripts folder is organised by tier:
    *
    *    scripts/STS/*.java          Methods subclass, constructor takes mudclient
    *    scripts/APOS/*.java         Script subclass, constructor takes Extension
    *    scripts/Textscript/*.txt    the labelled-block grammar
    *    scripts/SBoT/*.java         SBotScript subclass, constructor takes mudclient
    *    scripts/*.java|txt          anything loose, kept working
    *
    * The folder names are the bot names, because that is what the person with
    * the scripts already calls them.
    *
    * Sorting by folder rather than by inspecting the class is not tidiness. The
    * SBoT and APOS tiers both have a base class called `Script` -- SBoT's in
    * the default package, APOS's in org.rscdaemon.client -- and a script saying
    * `extends Script` cannot be resolved correctly without knowing which one it
    * means. Knowing the folder means knowing the classpath to compile against,
    * and the ambiguity disappears.
    */
   private static final String[] TIERS = { "STS", "APOS", "Textscript", "SBoT" };

   /**
    * Where the tier holding this script keeps its sources; null if it is loose
    * in the root.
    *
    * A name may be qualified with its tier -- "SBoT/Test" or "SBoT.Test" -- and
    * has to be when two tiers hold the same name. They do in practice: SBoT
    * ships a sample called Test.java and so does the APOS corpus. Picking the
    * first folder that matched would have run the wrong script and told nobody,
    * which is how this was found.
    */
   private File tierDir(String name) throws ScriptException {
      int slash = Math.max(name.indexOf('/'), name.indexOf('\\'));
      int dot = name.indexOf('.');
      int cut = slash >= 0 ? slash : dot;

      if (cut > 0) {
         String want = name.substring(0, cut);
         for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equalsIgnoreCase(want)) {
               return new File(this.scriptDir, TIERS[i]);
            }
         }
      }

      List<String> found = new ArrayList<String>();
      File first = null;
      for (int i = 0; i < TIERS.length; i++) {
         File dir = new File(this.scriptDir, TIERS[i]);
         if (new File(dir, name + ".java").isFile() || new File(dir, name + ".txt").isFile()) {
            found.add(TIERS[i]);
            if (first == null) {
               first = dir;
            }
         }
      }

      if (found.size() > 1) {
         StringBuilder how = new StringBuilder();
         for (int i = 0; i < found.size(); i++) {
            how.append("\n   /start ").append(found.get(i)).append('/').append(name);
         }

         throw new ScriptException("There is more than one script called " + name + " -- "
            + found + ".\n\nSay which one:" + how);
      }

      return first;
   }

   /** The bare script name, with any tier qualifier removed. */
   private static String bareName(String name) {
      int slash = Math.max(name.indexOf('/'), name.indexOf('\\'));
      int dot = name.indexOf('.');
      int cut = slash >= 0 ? slash : dot;
      return cut > 0 ? name.substring(cut + 1) : name;
   }

   /** Compiled output, kept per tier so two tiers may both hold a Miner. */
   private File binFor(File sourceDir) {
      return sourceDir.equals(this.scriptDir)
         ? this.classDir
         : new File(this.classDir, sourceDir.getName());
   }

   private static boolean isSBoT(File sourceDir) {
      return "SBoT".equals(sourceDir.getName());
   }

   public Methods load(String qualified) throws ScriptException {
      File tier = tierDir(qualified);
      String name = bareName(qualified);
      File dir = tier == null ? this.scriptDir : tier;
      File bin = binFor(dir);

      File source = new File(dir, name + ".java");
      File compiled = new File(bin, name + ".class");
      File text = new File(dir, name + ".txt");

      /*
       * The TextScript tier. A .txt is only reached when there is no Java
       * script of the same name, so a directory holding both Miner.java and
       * Miner.txt runs the Java one -- an arbitrary rule, but a fixed one, and
       * the alternative is refusing to start either.
       *
       * Parsing happens here rather than at start(), so a bad script fails
       * through the same ScriptException path a Java one does and the player
       * gets the line number before anything moves.
       */
      if (!source.isFile() && !compiled.isFile() && text.isFile()) {
         return TextScript.parse(this.rs, text);
      }

      if (source.isFile() && (!compiled.isFile() || source.lastModified() > compiled.lastModified())) {
         this.compile(source, name, dir, bin);
      } else if (!compiled.isFile()) {
         throw new ScriptException("No script called " + name + " in " + dir.getPath());
      }

      try {
         URLClassLoader loader = new URLClassLoader(
            new URL[]{bin.toURI().toURL()}, ScriptRunner.class.getClassLoader()
         );

         try {
            Class<?> type = loader.loadClass(name);
            if (!Methods.class.isAssignableFrom(type)) {
               throw new ScriptException(name + " does not extend Methods, so it is not a script.");
            }

            /*
             * Two shapes of constructor, because there were two bots. An STS
             * script takes the client:
             *
             *    public Miner(mudclient mc) { super(mc); }
             *
             * an APOS script takes the bot's handle to it:
             *
             *    public TPM_YewCutter(Extension e) { super(e); }
             *
             * The client one is tried first only because it is the older and
             * the more common; nothing distinguishes the tiers beyond this, and
             * a script declaring both would get the client one.
             */
            try {
               return (Methods)type.getConstructor(mudclient.class).newInstance(this.rs);
            } catch (NoSuchMethodException var6) {
               return (Methods)type.getConstructor(Extension.class).newInstance(new Extension(this.rs));
            }
         } finally {
            // Java 6 URLClassLoader has no close(); the loader is garbage once
            // the instance it produced is dropped.
         }
      } catch (ScriptException var7) {
         throw var7;
      } catch (NoSuchMethodException var8) {
         throw new ScriptException(
            name + " needs a constructor taking a mudclient:\n\n   public " + name + "(mudclient mc){super(mc);}\n\n"
               + "or, for an APOS script, one taking an Extension:\n\n   public " + name + "(Extension e){super(e);}"
         );
      } catch (Exception var9) {
         throw new ScriptException("Could not load " + name + ": " + var9);
      }
   }


   /*
    * SBoT scripts say `extends Script`, and mean SBoT's base class -- which in
    * SBoT lived in the default package, because that is where scripts lived.
    *
    * The API itself is real client code, org.rscdaemon.client.sbot.SBotScript.
    * What cannot be client code is the NAME: a class in the default package
    * cannot be imported, and org.rscdaemon.client.Script is already taken by
    * the APOS base class. So a three-line default-package subclass is written
    * into the tier's output directory and compiled alongside the scripts. Being
    * in the same (unnamed) package as the scripts, it wins over anything the
    * preamble's wildcard import would offer -- which is exactly the resolution
    * SBoT scripts were written expecting.
    *
    * Written every compile rather than cached: it is three lines, and a stale
    * one after a client upgrade would produce a mystifying error.
    */
   private void writeSBotBase(File bin) throws ScriptException {
      File java = new File(bin, "Script.java");

      try {
         PrintWriter out = new PrintWriter(java, "UTF-8");

         try {
            out.println("public class Script extends org.rscdaemon.client.sbot.SBotScript {");
            out.println("   public Script(org.rscdaemon.client.mudclient mc) { super(mc); }");
            out.println("}");
         } finally {
            out.close();
         }
      } catch (IOException e) {
         throw new ScriptException("Could not write the SBoT base class: " + e);
      }

      JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
      if (javac == null) {
         return;   // no JDK: a prebuilt Script.class may already be there
      }

      java.io.ByteArrayOutputStream errors = new java.io.ByteArrayOutputStream();
      int result = javac.run(null, null, errors,
         new String[] { "-nowarn", "-classpath", classpath(), "-d", bin.getPath(), java.getPath() });

      if (result != 0) {
         throw new ScriptException("Could not compile the SBoT base class:" + errors.toString());
      }
   }

   /*
    * Stage the source with the preamble and compile it. The staged copy is what
    * javac sees, so line numbers in errors are one out from the file the author
    * edits -- corrected on the way back so reported lines match their editor.
    */
   private void compile(File source, String name, File sourceDir, File bin) throws ScriptException {
      JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
      if (javac == null) {
         throw new ScriptException(
            "Cannot compile " + name + " because this is a Java runtime, not a JDK.\n\n"
               + "Install a JDK to edit scripts, or drop an already-compiled "
               + name + ".class into " + bin.getPath() + "."
         );
      }

      if (!bin.isDirectory() && !bin.mkdirs()) {
         throw new ScriptException("Could not create " + bin.getPath());
      }

      /* An SBoT script says `extends Script` and means the default-package base
         class SBoT scripts were written against. Put one there to find, before
         anything in this folder is compiled. */
      if (isSBoT(sourceDir)) {
         writeSBotBase(bin);
      }

      File staged = new File(bin, name + ".java");

      try {
         PrintWriter out = new PrintWriter(staged, "UTF-8");

         try {
            out.println(PREAMBLE);
            // 2006 files are CP1252 more often than UTF-8 -- read them as
            // latin-1 so a stray accented character is not a compile error.
            for (String line : readLines(source)) {
               out.println(line);
            }
         } finally {
            out.close();
         }
      } catch (IOException var11) {
         throw new ScriptException("Could not stage " + name + ": " + var11);
      }

      /* The tier's own output directory goes on the classpath as well as the
         client, so an SBoT script can see the Script base written above and a
         script can see a helper class compiled alongside it. */
      List<String> options = new ArrayList<String>(
         Arrays.asList("-nowarn", "-classpath", classpath() + File.pathSeparator + bin.getPath(),
            "-d", bin.getPath())
      );
      options.add(staged.getPath());

      // Capture the diagnostics rather than letting them go to stderr, which is
      // invisible to someone who launched by double-clicking.
      java.io.ByteArrayOutputStream errors = new java.io.ByteArrayOutputStream();
      int result = javac.run(null, null, errors, options.toArray(new String[options.size()]));

      if (result != 0) {
         String message;

         try {
            message = errors.toString("UTF-8");
         } catch (IOException var10) {
            message = errors.toString();
         }

         throw new ScriptException(shiftLineNumbers(name, message));
      }
   }

   private static List<String> readLines(File file) throws IOException {
      List<String> lines = new ArrayList<String>();
      java.io.BufferedReader in = new java.io.BufferedReader(
         new java.io.InputStreamReader(new java.io.FileInputStream(file), "ISO-8859-1")
      );

      try {
         String line;
         while ((line = in.readLine()) != null) {
            lines.add(line);
         }
      } finally {
         in.close();
      }

      return lines;
   }

   /*
    * Errors point at the staged file, which has one extra line at the top.
    * Subtract it so the numbers match the file the author is looking at.
    */
   private static String shiftLineNumbers(String name, String message) {
      StringBuilder fixed = new StringBuilder();

      for (String line : message.split("\n")) {
         java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^(.*" + java.util.regex.Pattern.quote(name) + "\\.java):(\\d+):(.*)$").matcher(line);
         if (m.matches()) {
            fixed.append(name).append(".java:").append(Integer.parseInt(m.group(2)) - 1).append(':').append(m.group(3));
         } else {
            fixed.append(line);
         }

         fixed.append('\n');
      }

      return fixed.toString();
   }

   /*
    * What the script is compiled against: this client, plus whatever else is on
    * the launch classpath. Reading java.class.path alone is not enough once the
    * client is running from a jar, because the manifest Class-Path entries do
    * not appear there -- so the jar's own directory is added too.
    */
   private static String classpath() {
      StringBuilder path = new StringBuilder(System.getProperty("java.class.path", "."));

      try {
         File self = new File(ScriptRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI());
         path.append(File.pathSeparator).append(self.getPath());

         File lib = new File(self.isFile() ? self.getParentFile() : self, "lib");
         File[] jars = lib.listFiles();
         if (jars != null) {
            for (File jar : jars) {
               if (jar.getName().endsWith(".jar")) {
                  path.append(File.pathSeparator).append(jar.getPath());
               }
            }
         }
      } catch (Exception var5) {
      }

      return path.toString();
   }

   /**
    * Start a loaded script because a person asked to. Any script already
    * running is stopped first. This is the fresh-setup path: the script's
    * prompts really appear, and what gets typed into them replaces the
    * recorded answers.
    */
   public void start(Methods loaded, String name, String[] args) {
      synchronized (this.answers) {
         this.answers.clear();
      }
      this.replay = null;
      this.autoStarted = false;
      this.launch(loaded, name, args);
   }

   /**
    * Start the script with nobody at the keyboard -- the login restart and
    * the panel's Play. The recorded setup answers replay into the prompts,
    * so the script interviews an empty chair and gets the same answers the
    * player gave when they set it up.
    */
   public void startAuto(Methods loaded, String name) {
      String[] canned;
      synchronized (this.answers) {
         canned = this.answers.toArray(new String[0]);
      }
      this.replay = canned;
      this.replayAt = 0;
      this.autoStarted = true;
      this.launch(loaded, name, this.getLastArgs());
   }

   /** A prompt was really answered; remember it for automatic restarts. */
   void recordAnswer(String answer) {
      synchronized (this.answers) {
         this.answers.add(answer);
      }
      this.saveLast();
   }

   /** The next replayed setup answer, or null when the prompt must really ask. */
   String nextCannedAnswer() {
      String[] r = this.replay;
      if (r != null && this.replayAt < r.length) {
         return r[this.replayAt++];
      }
      return null;
   }

   /** Whether the current run was started by the machinery, not a person. */
   boolean unattended() {
      return this.autoStarted;
   }

   private void launch(Methods loaded, String name, String[] args) {
      this.stop();
      this.stopRequested = false;
      this.script = loaded;
      this.scriptName = name;
      this.lastName = name;
      this.lastArgs = args;
      this.sessionActive = true;
      this.saveLast();
      this.startedAt = System.currentTimeMillis();

      final Methods running = loaded;
      final String[] arguments = args;
      final Thread[] self = new Thread[1];
      this.thread = new Thread(new Runnable() {
         public void run() {
            try {
               running.MainBody(arguments);
               /* Returned on its own terms: the session's bot is done, not
                  resumable. Cleared before haltScript so pump() can never
                  catch the moment in between and resume a script that meant
                  to end. */
               if (ScriptRunner.this.thread == self[0] && !ScriptRunner.this.stopRequested) {
                  ScriptRunner.this.sessionActive = false;
               }
            } catch (Throwable var2) {
               // A script bug must not take the client down with it.
               System.err.println("Script " + ScriptRunner.this.scriptName + " died:");
               var2.printStackTrace();
               /* A crash is an interruption, not a decision: the session
                  stays active and pump() resumes it after the backoff. A
                  stop's interrupt lands here too -- stop() already ended the
                  session, so it only sets the backoff, which is moot. */
               ScriptRunner.this.resumeNotBefore = System.currentTimeMillis() + RESUME_BACKOFF_MS;
            } finally {
               running.haltScript();
            }
         }
      }, "script-" + name);
      self[0] = this.thread;
      this.thread.setDaemon(true);
      this.thread.start();
   }

   /*
    * Ask the script to stop, then interrupt it. The flag alone only works at
    * the script's next Running() check, which could be a long Wait() away.
    */
   public void stop() {
      /* A stop is a decision -- the one thing that must never be undone by
         the resume machinery. Ended before the interrupt flies. */
      this.stopRequested = true;
      this.sessionActive = false;
      if (this.script != null) {
         this.script.haltScript();
      }

      /* A script parked in GetInput is waiting on the player, not on the game,
         so the halt flag alone will never reach it. Release it first or /stop
         hangs until somebody answers a box for a script they just stopped. */
      if (this.rs != null) {
         this.rs.prompt().cancel();
      }

      if (this.thread != null && this.thread.isAlive()) {
         this.thread.interrupt();

         try {
            this.thread.join(STOP_GRACE_MS);
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
         }
      }

      // Drop whatever the old script had not got round to handling, then let
      // the consumer die: the next script starts it again with a clean queue.
      this.events.clear();

      if (this.dispatcher != null) {
         this.dispatcher.interrupt();
         this.dispatcher = null;
      }

      this.thread = null;
      this.script = null;
      this.scriptName = "";
      this.startedAt = 0L;
   }

   /*
    * ---- events ----
    *
    * All six are called from the client thread and must return immediately.
    * Each is a no-op when no script is loaded, so the call sites can stay
    * unconditional.
    */

   final void fireChatMessage(final String sender, final String message) {
      final Methods target = this.script;
      if (target != null) {
         this.post(new Runnable() {
            public void run() {
               target.OnChatMessage(sender, message);
            }
         });
      }
   }

   final void firePrivateMessage(final String sender, final String message) {
      final Methods target = this.script;
      if (target != null) {
         this.post(new Runnable() {
            public void run() {
               target.OnPrivateMessage(sender, message);
            }
         });
      }
   }

   final void fireServerMessage(final String message) {
      final Methods target = this.script;
      if (target != null) {
         this.post(new Runnable() {
            public void run() {
               target.OnServerMessage(message);
            }
         });
      }
   }

   final void fireInput(final String input) {
      final Methods target = this.script;
      if (target != null) {
         this.post(new Runnable() {
            public void run() {
               target.OnInput(input);
            }
         });
      }
   }

   final void fireKeyPressed(final int key) {
      final Methods target = this.script;
      if (target != null) {
         this.post(new Runnable() {
            public void run() {
               target.KeyPressed(key);
            }
         });
      }
   }

   final void fireDebug(final String command) {
      final Methods target = this.script;
      if (target != null) {
         this.post(new Runnable() {
            public void run() {
               target.Debug(command);
            }
         });
      }
   }

   /*
    * ToShow() is the one hook that cannot be queued -- it returns the thing
    * being drawn, this frame, on the client thread. So it runs inline, and a
    * script that throws in it loses its overlay rather than the render loop.
    */
   final Stats stats() {
      Methods target = this.script;
      if (target == null) {
         return null;
      }

      try {
         return target.ToShow();
      } catch (Throwable var3) {
         return null;
      }
   }

   /*
    * The shapes the script queued during the ToShow() above. Read straight
    * after stats() and only then: the buffer belongs to that one frame.
    */
   final int[][] shapes() {
      Methods target = this.script;

      if (!(target instanceof Script)) {
         return null;
      }

      try {
         return ((Script) target).shapes();
      } catch (Throwable var3) {
         return null;
      }
   }

   private void post(Runnable event) {
      this.startDispatcher();
      this.events.add(event);
   }

   private synchronized void startDispatcher() {
      if (this.dispatcher != null && this.dispatcher.isAlive()) {
         return;
      }

      this.dispatcher = new Thread(new Runnable() {
         public void run() {
            while (true) {
               Runnable event;

               try {
                  event = ScriptRunner.this.events.take();
               } catch (InterruptedException var3) {
                  return;
               }

               try {
                  event.run();
               } catch (Throwable var2) {
                  // One bad handler must not silence every later event.
                  System.err.println("Script " + ScriptRunner.this.scriptName + " threw in an event handler:");
                  var2.printStackTrace();
               }
            }
         }
      }, "script-events");
      this.dispatcher.setDaemon(true);
      this.dispatcher.start();
   }

   /*
    * Every script in the directory, by name, for a script picker. Both tiers
    * are listed under one set of names and a name that exists as both appears
    * once, because load() will only ever run one of them.
    */
   public String[] list() {
      java.util.Set<String> names = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

      /*
       * Every tier folder, then anything loose in the root. The menu shows one
       * flat list on purpose: a player picking a script cares which script it
       * is, not which bot it was written for.
       *
       * A name held by more than one tier is listed qualified, once per tier --
       * "SBoT/Test" and "APOS/Test" rather than a single "Test" that would be
       * ambiguous to load. Both corpora really do ship a Test.
       */
      java.util.Set<String> root = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
      collect(this.scriptDir, root);

      java.util.Map<String, java.util.List<String>> byName =
         new java.util.TreeMap<String, java.util.List<String>>(String.CASE_INSENSITIVE_ORDER);

      for (int i = 0; i < TIERS.length; i++) {
         java.util.Set<String> here = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
         collect(new File(this.scriptDir, TIERS[i]), here);

         for (String n : here) {
            java.util.List<String> tiers = byName.get(n);
            if (tiers == null) {
               tiers = new ArrayList<String>();
               byName.put(n, tiers);
            }

            tiers.add(TIERS[i]);
         }
      }

      names.addAll(root);
      for (java.util.Map.Entry<String, java.util.List<String>> e : byName.entrySet()) {
         java.util.List<String> tiers = e.getValue();
         if (tiers.size() == 1 && !root.contains(e.getKey())) {
            names.add(e.getKey());
         } else {
            for (int i = 0; i < tiers.size(); i++) {
               names.add(tiers.get(i) + "/" + e.getKey());
            }
         }
      }

      return names.toArray(new String[names.size()]);
   }

   private static void collect(File dir, java.util.Set<String> names) {
      File[] files = dir.listFiles();
      if (files == null) {
         return;
      }

      for (File file : files) {
         String n = file.getName();
         /* Script.java is the generated SBoT base class, not a script. */
         if (n.equals("Script.java")) {
            continue;
         }

         if (n.endsWith(".java")) {
            names.add(n.substring(0, n.length() - 5));
         } else if (n.endsWith(".txt")) {
            names.add(n.substring(0, n.length() - 4));
         }
      }
   }

   /** Anything that stops a script being loaded or compiled. Shown to the player verbatim. */
   public static final class ScriptException extends Exception {
      private static final long serialVersionUID = 1L;

      public ScriptException(String message) {
         super(message);
      }
   }
}
