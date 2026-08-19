package org.rscdaemon.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * The second scripting tier: TextScript.
 *
 * TextScript v1.6 was a script *for* STS -- one compiled class, TwistedKlown's,
 * that shipped inside the bot and read a .txt file of labelled blocks. It is
 * the reason the Textscripts folder in a 2006 STS install is full of files by
 * people who never wrote a line of Java: no JDK, no compile step, no class
 * declaration, just commands one per line. Eleven of them survive in STS203C
 * and every one of them is expected to run here untouched.
 *
 *    [main]
 *    autologin(true)
 *    display(@ran@Anything Picker)
 *    goto([pickup])
 *
 *    [pickup]
 *    pickupitembyid(441)
 *    wait(350)
 *    goto([pickup])
 *
 * That is Picker.txt, complete. The grammar is:
 *
 *   [label]        a jump target. Not a scope and not a function -- execution
 *                  runs straight off the bottom of one block into the next,
 *                  which several of the surviving scripts rely on. Bomb.txt
 *                  declares [walkback] three times; the last one wins as a
 *                  target and the other two are simply run through.
 *   command(args)  one per line, case-insensitive, arguments comma-separated
 *   |anything      a comment
 *   #name#         a variable, set by the setvar family
 *   $name$         a read-only value from the game, e.g. $xcoord$, $fatigue$
 *
 * Execution starts at line one, not at [main] -- Cakes.txt has eight commands
 * before its first label -- and ends when a command runs off the bottom of the
 * file or calls end().
 *
 * The command set is the original's, recovered from TextScript.class's own
 * string table rather than guessed from the surviving scripts: 53 conditionals
 * and 61 actions, all present, none renamed. Where the original's one-line
 * behaviour was ambiguous the reading taken is noted at the command.
 *
 * The one convention worth stating because it differs from the Java tier: in
 * TextScript, items are named by *item id*, never by inventory position.
 * useitem(330) eats a cake; there is no slot 330. Every item command here
 * resolves the id to a slot and does nothing when it is not carried.
 */
public final class TextScript extends Methods {
   /* RSC's inventory, which is what gotoifinvfull is asking about. The client's
      array is 35 long for reasons of its own; the game gives you 30. */
   private static final int INVENTORY_SIZE = 30;

   /* The command name given to a line that is not one. Not a legal name -- no
      command has a space in it -- so a script can never collide with it. */
   private static final String UNKNOWN = "not a command";

   private final String name;
   private final Instruction[] code;
   private final Map<String, Integer> labels;
   private final List<String> warnings;
   private final Map<String, String> vars = new HashMap<String, String>();

   private String[] arguments = new String[0];

   /* The four message slots the gotoiflast and reset families read. Written
      from the event thread, read from the script thread. */
   private volatile String lastChat = "";
   private volatile String lastPrivate = "";
   private volatile String lastServer = "";
   private volatile String lastTrade = "";

   /* One parsed line: the command, its unsplit argument text, and where it came
      from in the file so an error can point at it. */
   private static final class Instruction {
      final String command;
      final String args;
      final int line;

      Instruction(String command, String args, int line) {
         this.command = command;
         this.args = args;
         this.line = line;
      }
   }

   /* Anything the script did wrong at run time. Carries no line number of its
      own -- the interpreter knows which instruction it was executing. */
   private static final class ScriptError extends RuntimeException {
      private static final long serialVersionUID = 1L;

      ScriptError(String message) {
         super(message);
      }
   }

   private TextScript(mudclient mc, String name, Instruction[] code, Map<String, Integer> labels,
                      List<String> warnings) {
      super(mc);
      this.name = name;
      this.code = code;
      this.labels = labels;
      this.warnings = warnings;
   }

   /*
    * ---- parsing ----
    *
    * Done at load time rather than at run time, so a typo is reported by the
    * same path as a Java compile error -- before the character starts walking
    * somewhere on the strength of a script that will die halfway through.
    */
   static TextScript parse(mudclient mc, File file) throws ScriptRunner.ScriptException {
      String name = file.getName();
      if (name.toLowerCase().endsWith(".txt")) {
         name = name.substring(0, name.length() - 4);
      }

      List<Instruction> code = new ArrayList<Instruction>();
      Map<String, Integer> labels = new HashMap<String, Integer>();
      List<String> problems = new ArrayList<String>();
      List<String> warnings = new ArrayList<String>();

      List<String> lines;

      try {
         lines = read(file);
      } catch (IOException var12) {
         throw new ScriptRunner.ScriptException("Could not read " + file.getPath() + ": " + var12);
      }

      for (int i = 0; i < lines.size(); i++) {
         String line = lines.get(i).trim();
         int number = i + 1;

         if (line.length() == 0 || line.charAt(0) == '|') {
            continue;
         }

         if (line.charAt(0) == '[') {
            int close = line.indexOf(']');
            if (close == -1) {
               problems.add("Line " + number + ": [" + line + " has no closing bracket");
               continue;
            }

            /* Labels are matched without case, because Wheat-early.txt writes
               [Main] and Bomb.txt writes GoTo([main]). A repeat is not an
               error: the later one wins, which is what the Hashtable the
               original used did. */
            labels.put(line.substring(1, close).trim().toLowerCase(), Integer.valueOf(code.size()));
            continue;
         }

         String command = line;
         String args = "";
         int open = line.indexOf('(');

         if (open != -1) {
            int close = line.lastIndexOf(')');
            command = line.substring(0, open).trim();
            args = line.substring(open + 1, close == -1 ? line.length() : close).trim();
         }

         command = command.toLowerCase();

         /*
          * A line that is not a command is not a reason to refuse the script.
          * Edge.txt ends with eighty dashes drawn under its last block, and it
          * ran for years because execution reached end() first and never got
          * that far. So an unrecognised line is kept in place as an
          * instruction that fails if it is ever reached, and named up front so
          * a real typo is still visible before the script starts.
          */
         if (!COMMANDS.contains(command)) {
            warnings.add("line " + number + ": " + line);
            code.add(new Instruction(UNKNOWN, line, number));
            continue;
         }

         code.add(new Instruction(command, args, number));
      }

      if (!problems.isEmpty()) {
         StringBuilder message = new StringBuilder(name + ".txt could not be read:\n");

         for (String problem : problems) {
            message.append('\n').append(problem);
         }

         throw new ScriptRunner.ScriptException(message.toString());
      }

      if (code.isEmpty()) {
         throw new ScriptRunner.ScriptException(name + ".txt has no commands in it.");
      }

      /* Every goto target is checked now for the same reason: "Label not found"
         is the original's most common failure and it always arrived an hour
         into a run. */
      for (Instruction instruction : code) {
         if (!instruction.command.startsWith("goto")) {
            continue;
         }

         String target = label(instruction.args);
         if (target != null && !labels.containsKey(target)) {
            problems.add("Line " + instruction.line + ": there is no [" + target + "] to jump to");
         }
      }

      if (!problems.isEmpty()) {
         StringBuilder message = new StringBuilder(name + ".txt could not be read:\n");

         for (String problem : problems) {
            message.append('\n').append(problem);
         }

         throw new ScriptRunner.ScriptException(message.toString());
      }

      return new TextScript(mc, name, code.toArray(new Instruction[code.size()]), labels, warnings);
   }

   /* 2006 text files are CP1252 far more often than UTF-8, for the same reason
      the Java tier reads its sources as latin-1: a stray accented character in
      somebody's display() line must not stop the script loading. */
   private static List<String> read(File file) throws IOException {
      List<String> lines = new ArrayList<String>();
      BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));

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

   /* The bracketed target out of a goto's arguments -- always the last one,
      because every conditional in the language ends with it. */
   private static String label(String args) {
      int comma = args.lastIndexOf(',');
      String last = (comma == -1 ? args : args.substring(comma + 1)).trim();

      if (last.startsWith("[") && last.endsWith("]")) {
         last = last.substring(1, last.length() - 1).trim();
      }

      return last.length() == 0 ? null : last.toLowerCase();
   }

   /*
    * ---- running ----
    */
   @Override
   public void MainBody(String[] Args) {
      this.arguments = Args == null ? new String[0] : Args;

      for (String warning : this.warnings) {
         this.Display("@gry@" + this.name + ": ignoring " + warning);
      }

      int pc = 0;

      while (this.Running() && pc >= 0 && pc < this.code.length) {
         Instruction instruction = this.code[pc];

         try {
            pc = this.execute(instruction, pc);
         } catch (ScriptError var5) {
            this.Display("@red@" + this.name + " line " + instruction.line + ": " + var5.getMessage());
            this.End();
            return;
         } catch (RuntimeException var6) {
            this.Display("@red@" + this.name + " line " + instruction.line + ": " + var6);
            this.End();
            return;
         }
      }

      if (this.Running()) {
         this.End("Script finished...");
      }
   }

   /* Returns the next program counter. */
   private int execute(Instruction instruction, int pc) {
      String command = instruction.command;
      String raw = instruction.args;
      int next = pc + 1;

      if (command == UNKNOWN) {
         throw new ScriptError("there is no command called " + raw);
      }

      /* ---- flow ---- */

      if (command.equals("goto")) {
         return this.jump(raw);
      }

      if (command.equals("end")) {
         if (raw.length() == 0) {
            this.End();
         } else {
            this.End(this.text(raw));
         }

         return -1;
      }

      if (command.equals("die")) {
         this.Die();
         return -1;
      }

      if (command.equals("wait")) {
         this.Wait(this.number(raw));
         return next;
      }

      if (command.equals("sleep")) {
         /* sleep(n) is SleepIfAt(n) -- the fatigue to lie down at, not a
            duration. Every surviving script that uses it passes 90-95. */
         this.SleepIfAt(this.number(raw));
         return next;
      }

      /* ---- variables ---- */

      if (command.equals("setvar")) {
         String[] a = this.pair(raw);
         this.vars.put(variable(a[0]), this.expand(a[1]));
         return next;
      }

      if (command.equals("setvarbygetinput")) {
         String[] a = this.pair(raw);
         String answer = this.GetInput(this.expand(a[1]));
         this.vars.put(variable(a[0]), answer == null ? "" : answer);
         return next;
      }

      if (command.equals("setvarbyarg")) {
         /* The n'th argument of /start Name(a,b,c), one-based, which is how
            the original's own help worded it. Missing is empty, not an error:
            scripts test for it with gotoifequals(#v#,,[...]). */
         String[] a = this.pair(raw);
         int index = this.number(a[1]) - 1;
         this.vars.put(variable(a[0]), index >= 0 && index < this.arguments.length ? this.arguments[index] : "");
         return next;
      }

      if (command.equals("setmycoords")) {
         this.vars.put("x", String.valueOf(this.GetX()));
         this.vars.put("y", String.valueOf(this.GetY()));
         return next;
      }

      /* ---- output ---- */

      if (command.equals("display") || command.equals("sysmsg")) {
         this.Display(this.text(raw));
         return next;
      }

      if (command.equals("showmessage")) {
         this.ShowMessage(this.text(raw));
         return next;
      }

      if (command.equals("speak")) {
         /* Buyer.txt has a bare speak() between its advert lines. The original
            sent an empty line; so does this. */
         this.Speak(this.text(raw));
         return next;
      }

      if (command.equals("takepic")) {
         this.SaveScreenShot(this.text(raw));
         return next;
      }

      /* ---- client settings ---- */

      if (command.equals("autologin")) {
         this.AutoLogin(bool(this.text(raw)));
         return next;
      }

      if (command.equals("chatfilter")) {
         this.ChatFilterOn(bool(this.text(raw)));
         return next;
      }

      if (command.equals("startscanformods")) {
         this.StartScanForMods();
         return next;
      }

      if (command.equals("stopscanformods")) {
         this.StopScanForMods();
         return next;
      }

      if (command.equals("closepopup")) {
         this.ClosePopup();
         return next;
      }

      /* ---- server ---- */

      if (command.equals("logout")) {
         this.LogOut();
         return next;
      }

      if (command.equals("hopserv")) {
         /* With a world number it hops to that one, without it to the next.
            hopservbyid is the same call; the original had both names and
            scripts use whichever they were taught. */
         if (raw.length() == 0) {
            this.HopServer();
         } else {
            this.HopServer(this.number(raw));
         }

         return next;
      }

      if (command.equals("hopservbyid")) {
         this.HopServer(this.number(raw));
         return next;
      }

      /* ---- movement ---- */

      if (command.equals("walkto")) {
         int[] a = this.numbers(raw, 2);
         this.WalkTo(a[0], a[1]);
         return next;
      }

      if (command.equals("walktowait")) {
         int[] a = this.numbers(raw, 2);
         this.WalkToWait(a[0], a[1]);
         return next;
      }

      if (command.equals("forcewalkto")) {
         int[] a = this.numbers(raw, 2);
         this.ForceWalkTo(a[0], a[1]);
         return next;
      }

      if (command.equals("forcewalktowait")) {
         int[] a = this.numbers(raw, 2);
         this.ForceWalkToWait(a[0], a[1]);
         return next;
      }

      if (command.equals("walktorandomly")) {
         /* Neither the help nor any surviving script pins this one down, so
            both readings are honoured: three arguments is a centre and a
            radius, four is a box. Either way it picks one tile and walks to
            it, which is the only behaviour the name can mean. */
         int[] a = this.numbers(raw, raw.indexOf(',') == raw.lastIndexOf(',') ? 3 : 4);
         int x;
         int y;

         if (a.length == 3) {
            x = this.Rand(a[0] - a[2], a[0] + a[2]);
            y = this.Rand(a[1] - a[2], a[1] + a[2]);
         } else {
            x = this.Rand(Math.min(a[0], a[2]), Math.max(a[0], a[2]));
            y = this.Rand(Math.min(a[1], a[3]), Math.max(a[1], a[3]));
         }

         this.WalkTo(x, y);
         return next;
      }

      /* ---- objects ---- */

      if (command.equals("atobjectbyid") || command.equals("atobjectbyid2")) {
         int[] found = this.GetObjectById(this.number(raw));
         if (found[0] != -1) {
            if (command.endsWith("2")) {
               this.AtObject2(found[1], found[2]);
            } else {
               this.AtObject(found[1], found[2]);
            }
         }

         return next;
      }

      if (command.equals("atobjectbycoords") || command.equals("atobjectbycoords2")) {
         int[] a = this.numbers(raw, 2);
         if (command.endsWith("2")) {
            this.AtObject2(a[0], a[1]);
         } else {
            this.AtObject(a[0], a[1]);
         }

         return next;
      }

      if (command.equals("useonobject")) {
         int[] a = this.numbers(raw, 3);
         int slot = this.GetItemPos(a[0]);
         if (slot != -1) {
            this.UseOnObject(slot, a[1], a[2]);
         }

         return next;
      }

      if (command.equals("atwallobjectbyid") || command.equals("atwallobjectbyid2")) {
         int[] found = this.GetWallObjectById(this.number(raw));
         if (found[0] != -1) {
            if (command.endsWith("2")) {
               this.AtWallObject2(found[1], found[2]);
            } else {
               this.AtWallObject(found[1], found[2]);
            }
         }

         return next;
      }

      if (command.equals("atwallobjectbycoords") || command.equals("atwallobjectbycoords2")) {
         int[] a = this.numbers(raw, 2);
         if (command.endsWith("2")) {
            this.AtWallObject2(a[0], a[1]);
         } else {
            this.AtWallObject(a[0], a[1]);
         }

         return next;
      }

      /* ---- items ---- */

      if (command.equals("useitem") || command.equals("useitem2")) {
         int slot = this.GetItemPos(this.number(raw));
         if (slot != -1) {
            if (command.endsWith("2")) {
               this.UseItem2(slot);
            } else {
               this.UseItem(slot);
            }
         }

         return next;
      }

      if (command.equals("useitemwithitem")) {
         int[] a = this.numbers(raw, 2);
         int first = this.GetItemPos(a[0]);
         int second = this.GetItemPos(a[1]);
         if (first != -1 && second != -1) {
            this.UseItemWithItem(first, second);
         }

         return next;
      }

      if (command.equals("useitemwithgrounditem")) {
         /* Two arguments is "use this carried item on the nearest one of that
            id on the floor"; four names the tile outright. */
         int[] a = this.numbers(raw, raw.indexOf(',') == raw.lastIndexOf(',') ? 2 : 4);
         int slot = this.GetItemPos(a[0]);
         if (slot == -1) {
            return next;
         }

         if (a.length == 2) {
            int[] found = this.GetItemById(a[1]);
            if (found[0] != -1) {
               this.UseItemOnGItem(slot, found[1], found[2], found[0]);
            }
         } else {
            this.UseItemOnGItem(slot, a[1], a[2], a[3]);
         }

         return next;
      }

      if (command.equals("dropitem")) {
         int slot = this.GetItemPos(this.number(raw));
         if (slot != -1) {
            this.DropItem(slot);
         }

         return next;
      }

      if (command.equals("wearitem")) {
         int slot = this.GetItemPos(this.number(raw));
         if (slot != -1) {
            this.WearItem(slot);
         }

         return next;
      }

      if (command.equals("removeitem")) {
         int slot = this.GetItemPos(this.number(raw));
         if (slot != -1) {
            this.RemoveItem(slot);
         }

         return next;
      }

      if (command.equals("pickupitembyid")) {
         this.PickupItemById(this.number(raw));
         return next;
      }

      if (command.equals("pickupitemifreachable")) {
         int id = this.number(raw);
         int[] found = this.GetItemById(id);
         if (found[0] != -1 && this.IsReachable(found[1], found[2])) {
            this.PickupItem(found[1], found[2], found[0]);
         }

         return next;
      }

      if (command.equals("pickitemifwithin")) {
         int[] a = this.numbers(raw, 2);
         int[] found = this.GetItemById(a[0]);
         if (found[0] != -1 && this.DistanceTo(found[1], found[2]) <= a[1]) {
            this.PickupItem(found[1], found[2], found[0]);
         }

         return next;
      }

      /* ---- npcs ---- */

      if (command.equals("talktonpc")) {
         /* GetNpcById, not GetAllNpcById: talking to a banker someone else is
            already fighting achieves nothing, and the original drew the same
            distinction. */
         int[] found = this.GetNpcById(this.number(raw));
         if (found[0] != -1) {
            this.TalkToNpc(found[0]);
         }

         return next;
      }

      if (command.equals("attacknpcbyid")) {
         int[] found = this.GetNpcById(this.number(raw));
         if (found[0] != -1) {
            this.AttackNpc(found[0]);
         }

         return next;
      }

      if (command.equals("attackallnpcbyid")) {
         /* The "all" is not "attack all of them" -- it is GetAllNpcById, which
            does not skip one already in combat. Used for stealing a kill. */
         int[] found = this.GetAllNpcById(this.number(raw));
         if (found[0] != -1) {
            this.AttackNpc(found[0]);
         }

         return next;
      }

      if (command.equals("attacknpcifwithin")) {
         int[] a = this.numbers(raw, 2);
         int[] found = this.GetNpcById(a[0]);
         if (found[0] != -1 && this.DistanceTo(found[1], found[2]) <= a[1]) {
            this.AttackNpc(found[0]);
         }

         return next;
      }

      if (command.equals("thievenpc")) {
         int[] found = this.GetNpcByIdNotTalk(this.number(raw));
         if (found[0] != -1) {
            this.ThieveNpc(found[0]);
         }

         return next;
      }

      if (command.equals("answer")) {
         /* Bomb.txt answers 1000. Answer() ignores an option that is not on
            the menu, which is why that script has worked for twenty years. */
         this.Answer(this.number(raw));
         return next;
      }

      /* ---- bank and shop ---- */

      if (command.equals("deposit")) {
         int[] a = this.numbers(raw, 2);
         this.Deposit(a[0], a[1]);
         return next;
      }

      if (command.equals("withdraw")) {
         int[] a = this.numbers(raw, 2);
         this.Withdraw(a[0], a[1]);
         return next;
      }

      if (command.equals("closebank")) {
         this.CloseBank();
         return next;
      }

      if (command.equals("closeshop")) {
         this.CloseShop();
         return next;
      }

      if (command.equals("buyitem")) {
         this.BuyShopItem(this.number(raw));
         return next;
      }

      if (command.equals("sellitem")) {
         this.SellShopItem(this.number(raw));
         return next;
      }

      /* ---- trade ---- */

      if (command.equals("offer")) {
         int[] a = this.numbers(raw, 2);
         this.AddToOffer(a[0], a[1]);
         return next;
      }

      if (command.equals("accept1")) {
         this.AcceptTrade1();
         return next;
      }

      if (command.equals("accept2")) {
         this.AcceptTrade2();
         return next;
      }

      if (command.equals("declinetrade")) {
         this.DeclineTrade();
         return next;
      }

      if (command.equals("tradewith")) {
         int[] found = this.GetPlayerByName(this.text(raw));
         if (found[0] != -1) {
            this.TradePlayer(found[0]);
         }

         return next;
      }

      /* ---- combat, prayer and spells ---- */

      if (command.equals("setfightmode")) {
         this.SetMode(this.number(raw));
         return next;
      }

      if (command.equals("lockmode")) {
         this.LockMode(this.number(raw));
         return next;
      }

      if (command.equals("unlockmode")) {
         this.UnlockMode();
         return next;
      }

      if (command.equals("prayeron")) {
         this.PrayerOn(this.number(raw));
         return next;
      }

      if (command.equals("prayeroff")) {
         this.PrayerOff(this.number(raw));
         return next;
      }

      if (command.equals("castonself")) {
         this.CastOnSelf(this.number(raw));
         return next;
      }

      if (command.equals("castonnpc")) {
         int[] a = this.numbers(raw, 2);
         int[] found = this.GetNpcById(a[1]);
         if (found[0] != -1) {
            this.CastOnNpc(a[0], found[0]);
         }

         return next;
      }

      if (command.equals("castonitem")) {
         int[] a = this.numbers(raw, 2);
         int slot = this.GetItemPos(a[1]);
         if (slot != -1) {
            this.CastOnItem(a[0], slot);
         }

         return next;
      }

      if (command.equals("castongrounditem")) {
         int[] a = this.numbers(raw, 2);
         int[] found = this.GetItemById(a[1]);
         if (found[0] != -1) {
            this.CastOnGItem(a[0], found[0], found[1], found[2]);
         }

         return next;
      }

      /* ---- the message slots ---- */

      if (command.equals("resetlastchatmsg")) {
         this.lastChat = "";
         return next;
      }

      if (command.equals("resetlastprivatemsg")) {
         this.lastPrivate = "";
         return next;
      }

      if (command.equals("resetlastservermsg")) {
         this.lastServer = "";
         return next;
      }

      if (command.equals("resetlasttrade")) {
         this.lastTrade = "";
         return next;
      }

      /* ---- conditionals ---- */

      if (command.startsWith("gotoif")) {
         return this.test(command, raw) ? this.jump(raw) : next;
      }

      throw new ScriptError("there is no command called " + command);
   }

   /*
    * Every gotoif in the language. The label is always the last argument, so
    * each of these reads only the ones before it.
    */
   private boolean test(String command, String raw) {
      String[] a = this.before(raw);

      if (command.equals("gotoifatcoords")) {
         return this.GetX() == number(a, 0) && this.GetY() == number(a, 1);
      }

      if (command.equals("gotoifnotatcoords")) {
         return this.GetX() != number(a, 0) || this.GetY() != number(a, 1);
      }

      if (command.equals("gotoifincoords")) {
         return this.InArea(this.GetX(), this.GetY(), number(a, 0), number(a, 1), number(a, 2), number(a, 3));
      }

      if (command.equals("gotoifnotincoords")) {
         return !this.InArea(this.GetX(), this.GetY(), number(a, 0), number(a, 1), number(a, 2), number(a, 3));
      }

      if (command.equals("gotoifatxcoord")) {
         return this.GetX() == number(a, 0);
      }

      if (command.equals("gotoifatycoord")) {
         return this.GetY() == number(a, 0);
      }

      if (command.equals("gotoifxcoordisover")) {
         return this.GetX() > number(a, 0);
      }

      if (command.equals("gotoifxcoordisunder")) {
         return this.GetX() < number(a, 0);
      }

      if (command.equals("gotoifycoordisover")) {
         return this.GetY() > number(a, 0);
      }

      if (command.equals("gotoifycoordisunder")) {
         return this.GetY() < number(a, 0);
      }

      if (command.equals("gotoifdistancetoisover")) {
         return this.DistanceTo(number(a, 0), number(a, 1)) > number(a, 2);
      }

      if (command.equals("gotoifdistancetoisunder")) {
         return this.DistanceTo(number(a, 0), number(a, 1)) < number(a, 2);
      }

      if (command.equals("gotoifdistancetoequals")) {
         return this.DistanceTo(number(a, 0), number(a, 1)) == number(a, 2);
      }

      if (command.equals("gotoifisreachable")) {
         return this.IsReachable(number(a, 0), number(a, 1));
      }

      if (command.equals("gotoifnotreachable")) {
         return !this.IsReachable(number(a, 0), number(a, 1));
      }

      if (command.equals("gotoifinvfull")) {
         return this.CountInv() >= INVENTORY_SIZE;
      }

      /* The count of one item id. Both spellings are the original's: Wheat.txt
         uses gotoifitemcountinvis and Coal.txt uses gotoifcountinvitemis, in
         the same release, for the same thing. */
      if (command.equals("gotoifitemcountinvis") || command.equals("gotoifcountinvitemis")) {
         return this.CountInv(number(a, 0)) == number(a, 1);
      }

      if (command.equals("gotoifitemcountinvover")) {
         return this.CountInv(number(a, 0)) > number(a, 1);
      }

      if (command.equals("gotoifitemcountinvunder")) {
         return this.CountInv(number(a, 0)) < number(a, 1);
      }

      /* And these three are the count of everything, not of an id. */
      if (command.equals("gotoifcountinvis")) {
         return this.CountInv() == number(a, 0);
      }

      if (command.equals("gotoifcountinvover")) {
         return this.CountInv() > number(a, 0);
      }

      if (command.equals("gotoifcountinvunder")) {
         return this.CountInv() < number(a, 0);
      }

      if (command.equals("gotoifequals")) {
         return compare(a, 0, 1) == 0;
      }

      if (command.equals("gotoifover")) {
         return compare(a, 0, 1) > 0;
      }

      if (command.equals("gotoifunder")) {
         return compare(a, 0, 1) < 0;
      }

      if (command.equals("gotoifcurstatequals")) {
         return this.GetCurLvl(stat(a, 0)) == number(a, 1);
      }

      if (command.equals("gotoifcurstatover")) {
         return this.GetCurLvl(stat(a, 0)) > number(a, 1);
      }

      if (command.equals("gotoifcurstatunder")) {
         return this.GetCurLvl(stat(a, 0)) < number(a, 1);
      }

      if (command.equals("gotoifmaxstatequals")) {
         return this.GetMaxLvl(stat(a, 0)) == number(a, 1);
      }

      if (command.equals("gotoifmaxstatover")) {
         return this.GetMaxLvl(stat(a, 0)) > number(a, 1);
      }

      if (command.equals("gotoifmaxstatunder")) {
         return this.GetMaxLvl(stat(a, 0)) < number(a, 1);
      }

      if (command.equals("gotoifincombat")) {
         return this.InCombat();
      }

      if (command.equals("gotoifnotincombat")) {
         return !this.InCombat();
      }

      if (command.equals("gotoifsleeping")) {
         return this.Sleeping();
      }

      /* gotoifnotisleeping is spelt exactly that way in TextScript.class and
         so it is spelt exactly that way here. gotoifnotsleeping is the only
         name in this file that the original did not have, and it exists
         because everybody typed it by accident. */
      if (command.equals("gotoifnotisleeping") || command.equals("gotoifnotsleeping")) {
         return !this.Sleeping();
      }

      if (command.equals("gotoifloggedin")) {
         return this.LoggedIn();
      }

      if (command.equals("gotoifloggedout")) {
         return !this.LoggedIn();
      }

      if (command.equals("gotoifquestmenu")) {
         return this.QuestMenu();
      }

      if (command.equals("gotoifnpcnear")) {
         return this.GetNpcById(number(a, 0))[0] != -1;
      }

      if (command.equals("gotoifnonpcnear")) {
         return this.GetNpcById(number(a, 0))[0] == -1;
      }

      if (command.equals("gotoifitemisnear")) {
         return this.GetItemById(number(a, 0))[0] != -1;
      }

      /* gotoifnoitem is the original's third name for the same question. */
      if (command.equals("gotoifitemisnotnear") || command.equals("gotoifnoitem")) {
         return this.GetItemById(number(a, 0))[0] == -1;
      }

      if (command.equals("gotoifinshop")) {
         return this.InShop();
      }

      if (command.equals("gotoifnotinshop")) {
         return !this.InShop();
      }

      /* The four message tests. Substring, not equality, and without case --
         these exist for mod detection and for catching "You have run out of"
         in a server line, and neither works on a whole-string match. */
      if (command.equals("gotoiflastchatmsg")) {
         return contains(this.lastChat, text(a, 0));
      }

      if (command.equals("gotoifnotlastchatmsg")) {
         return !contains(this.lastChat, text(a, 0));
      }

      if (command.equals("gotoiflastprivatemsg")) {
         return contains(this.lastPrivate, text(a, 0));
      }

      if (command.equals("gotoifnotlastprivatemsg")) {
         return !contains(this.lastPrivate, text(a, 0));
      }

      if (command.equals("gotoiflastservermsg")) {
         return contains(this.lastServer, text(a, 0));
      }

      if (command.equals("gotoifnotlastservermsg")) {
         return !contains(this.lastServer, text(a, 0));
      }

      if (command.equals("gotoiflasttrade")) {
         return contains(this.lastTrade, text(a, 0));
      }

      if (command.equals("gotoifnotlasttrade")) {
         return !contains(this.lastTrade, text(a, 0));
      }

      throw new ScriptError("there is no command called " + command);
   }

   private int jump(String raw) {
      String target = label(raw);
      Integer at = target == null ? null : this.labels.get(target);

      if (at == null) {
         throw new ScriptError("there is no [" + target + "] to jump to");
      }

      return at.intValue();
   }

   /*
    * ---- events ----
    *
    * The four slots the gotoiflast* family reads, plus the mod scanner the
    * original wired into the same two hooks. Trade is a server message here
    * rather than a hook of its own, because that is how the server announces
    * it: "@gry@ <name> wishes to trade with you".
    */
   @Override
   public void OnChatMessage(String sender, String message) {
      this.lastChat = sender + ": " + message;
   }

   @Override
   public void OnPrivateMessage(String sender, String message) {
      this.lastPrivate = sender + ": " + message;
   }

   @Override
   public void OnServerMessage(String message) {
      this.lastServer = message;

      int wishes = message.indexOf(" wishes to trade");
      if (wishes != -1) {
         String who = message.substring(0, wishes);
         int tag = who.lastIndexOf('@');
         this.lastTrade = tag == -1 ? who.trim() : who.substring(tag + 1).trim();
      }
   }

   /*
    * ---- arguments ----
    */

   /* Everything before the trailing label, expanded. */
   private String[] before(String raw) {
      int comma = raw.lastIndexOf(',');
      String head = comma == -1 ? "" : raw.substring(0, comma);
      return this.split(head);
   }

   private String[] split(String raw) {
      if (raw.trim().length() == 0) {
         return new String[0];
      }

      String[] parts = raw.split(",", -1);

      for (int i = 0; i < parts.length; i++) {
         parts[i] = this.expand(parts[i].trim());
      }

      return parts;
   }

   /* Split on the first comma only, for the setvar family: the second half is
      a prompt or a value and may well contain commas of its own. Neither half
      is expanded here -- the first is a variable name and must not be. */
   private String[] pair(String raw) {
      int comma = raw.indexOf(',');

      if (comma == -1) {
         throw new ScriptError("expected two arguments, got \"" + raw + "\"");
      }

      return new String[]{raw.substring(0, comma).trim(), raw.substring(comma + 1).trim()};
   }

   /* One whole argument, commas and all -- for display, speak and the rest. */
   private String text(String raw) {
      return this.expand(raw);
   }

   private int number(String raw) {
      return parse(this.expand(raw.trim()));
   }

   private int[] numbers(String raw, int expected) {
      String[] parts = this.split(raw);

      if (parts.length < expected) {
         throw new ScriptError("expected " + expected + " arguments, got " + parts.length);
      }

      int[] values = new int[expected];

      for (int i = 0; i < expected; i++) {
         values[i] = parse(parts[i]);
      }

      return values;
   }

   private static String text(String[] a, int i) {
      return i < a.length ? a[i] : "";
   }

   private static int number(String[] a, int i) {
      if (i >= a.length) {
         throw new ScriptError("expected " + (i + 1) + " arguments, got " + a.length);
      }

      return parse(a[i]);
   }

   private static int stat(String[] a, int i) {
      int stat = number(a, i);

      if (stat < 0 || stat > 17) {
         throw new ScriptError(stat + " is not a stat -- they are 0 to 18, attack first and runecrafting last");
      }

      return stat;
   }

   private static int parse(String s) {
      try {
         return Integer.parseInt(s.trim());
      } catch (NumberFormatException var2) {
         throw new ScriptError("\"" + s + "\" is not a number");
      }
   }

   /* Numeric when both sides are numbers, textual when either is not, which is
      what lets gotoifequals(#power#,2,[power]) and gotoifequals(#name#,bob,[l])
      both be written. */
   private static int compare(String[] a, int i, int j) {
      String left = text(a, i);
      String right = text(a, j);

      try {
         int l = Integer.parseInt(left.trim());
         int r = Integer.parseInt(right.trim());
         return l < r ? -1 : (l > r ? 1 : 0);
      } catch (NumberFormatException var6) {
         return left.compareToIgnoreCase(right);
      }
   }

   private static boolean contains(String haystack, String needle) {
      return needle.length() != 0 && haystack.toLowerCase().indexOf(needle.toLowerCase()) != -1;
   }

   private static boolean bool(String s) {
      s = s.trim();
      return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("yes");
   }

   /* #name# on either side is optional -- setvar(#x#,..) and setvar(x,..) name
      the same variable, because half the surviving scripts write it each way. */
   private static String variable(String s) {
      s = s.trim();

      if (s.length() >= 2 && s.charAt(0) == '#' && s.charAt(s.length() - 1) == '#') {
         s = s.substring(1, s.length() - 1);
      }

      if (s.length() == 0) {
         throw new ScriptError("a variable needs a name");
      }

      return s.toLowerCase();
   }

   /*
    * ---- substitution ----
    *
    * #name# is a variable the script set; $name$ is read off the game now. An
    * unset #name# is left standing rather than blanked, because that is what
    * makes a missing variable visible in a display() line instead of silently
    * turning into an empty string. An unknown $name$ is an error -- there is a
    * fixed list of them and a typo in one is never intentional.
    */
   private String expand(String s) {
      if (s.indexOf('#') == -1 && s.indexOf('$') == -1) {
         return s;
      }

      StringBuilder out = new StringBuilder(s.length());
      int i = 0;

      while (i < s.length()) {
         char c = s.charAt(i);

         if (c != '#' && c != '$') {
            out.append(c);
            i++;
            continue;
         }

         int close = s.indexOf(c, i + 1);
         if (close == -1) {
            out.append(c);
            i++;
            continue;
         }

         String key = s.substring(i + 1, close).trim().toLowerCase();

         if (c == '#') {
            String value = this.vars.get(key);
            out.append(value == null ? s.substring(i, close + 1) : value);
         } else {
            out.append(this.builtin(key));
         }

         i = close + 1;
      }

      return out.toString();
   }

   private String builtin(String key) {
      if (key.equals("xcoord")) {
         return String.valueOf(this.GetX());
      }

      if (key.equals("ycoord")) {
         return String.valueOf(this.GetY());
      }

      if (key.equals("fatigue")) {
         return String.valueOf(this.GetFatigue());
      }

      if (key.equals("countinv")) {
         return String.valueOf(this.CountInv());
      }

      if (key.equals("curfightmode")) {
         return String.valueOf(this.GetMode());
      }

      if (key.equals("lastchat")) {
         return this.lastChat;
      }

      if (key.equals("lastprivate")) {
         return this.lastPrivate;
      }

      if (key.equals("lastserver")) {
         return this.lastServer;
      }

      if (key.equals("lasttrade")) {
         return this.lastTrade;
      }

      if (key.startsWith("cur") && key.endsWith("lvl")) {
         return String.valueOf(this.GetCurLvl(skill(key.substring(3, key.length() - 3))));
      }

      if (key.startsWith("max") && key.endsWith("lvl")) {
         return String.valueOf(this.GetMaxLvl(skill(key.substring(3, key.length() - 3))));
      }

      throw new ScriptError("there is no $" + key + "$");
   }

   /*
    * The nineteen skill names as $curXXXlvl$ spells them. They are not the
    * client's own names: TextScript said range, pray, mage, woodcut and
    * defense where the stat panel says Ranged, Prayer, Magic, Woodcutting and
    * Defense, so both spellings answer.
    */
   private static int skill(String name) {
      if (name.equals("attack")) {
         return 0;
      } else if (name.equals("defense") || name.equals("defence")) {
         return 1;
      } else if (name.equals("strength")) {
         return 2;
      } else if (name.equals("hits") || name.equals("hp")) {
         return 3;
      } else if (name.equals("range") || name.equals("ranged")) {
         return 4;
      } else if (name.equals("pray") || name.equals("prayer")) {
         return 5;
      } else if (name.equals("mage") || name.equals("magic")) {
         return 6;
      } else if (name.equals("cooking")) {
         return 7;
      } else if (name.equals("woodcut") || name.equals("woodcutting")) {
         return 8;
      } else if (name.equals("fletching")) {
         return 9;
      } else if (name.equals("fishing")) {
         return 10;
      } else if (name.equals("firemaking")) {
         return 11;
      } else if (name.equals("crafting")) {
         return 12;
      } else if (name.equals("smithing")) {
         return 13;
      } else if (name.equals("mining")) {
         return 14;
      } else if (name.equals("herblaw") || name.equals("herblore")) {
         return 15;
      } else if (name.equals("agility")) {
         return 16;
      } else if (name.equals("thieving")) {
         return 17;
      } else if (name.equals("runecrafting") || name.equals("runecraft")) {
         return 18;
      } else {
         throw new ScriptError("there is no skill called " + name);
      }
   }

   /*
    * ---- the command table ----
    *
    * Every name TextScript.class knew, read out of its own string table, plus
    * the two aliases noted at their handlers. This exists so a typo is caught
    * when the script is loaded rather than an hour into a run, and it is the
    * list execute() and test() must be kept level with.
    */
   private static final String[] NAMES = {
      /* flow, variables and output */
      "goto", "end", "die", "wait", "sleep",
      "setvar", "setvarbyarg", "setvarbygetinput", "setmycoords",
      "display", "sysmsg", "showmessage", "speak", "takepic",
      /* client and server */
      "autologin", "chatfilter", "startscanformods", "stopscanformods", "closepopup",
      "logout", "hopserv", "hopservbyid",
      /* movement */
      "walkto", "walktowait", "forcewalkto", "forcewalktowait", "walktorandomly",
      /* objects */
      "atobjectbyid", "atobjectbyid2", "atobjectbycoords", "atobjectbycoords2", "useonobject",
      "atwallobjectbyid", "atwallobjectbyid2", "atwallobjectbycoords", "atwallobjectbycoords2",
      /* items */
      "useitem", "useitem2", "useitemwithitem", "useitemwithgrounditem",
      "dropitem", "wearitem", "removeitem",
      "pickupitembyid", "pickupitemifreachable", "pickitemifwithin",
      /* npcs */
      "talktonpc", "attacknpcbyid", "attackallnpcbyid", "attacknpcifwithin", "thievenpc", "answer",
      /* bank, shop and trade */
      "deposit", "withdraw", "closebank", "closeshop", "buyitem", "sellitem",
      "offer", "accept1", "accept2", "declinetrade", "tradewith",
      /* combat, prayer and spells */
      "setfightmode", "lockmode", "unlockmode", "prayeron", "prayeroff",
      "castonself", "castonnpc", "castonitem", "castongrounditem",
      /* the message slots */
      "resetlastchatmsg", "resetlastprivatemsg", "resetlastservermsg", "resetlasttrade",
      /* conditionals */
      "gotoifatcoords", "gotoifnotatcoords", "gotoifincoords", "gotoifnotincoords",
      "gotoifatxcoord", "gotoifatycoord",
      "gotoifxcoordisover", "gotoifxcoordisunder", "gotoifycoordisover", "gotoifycoordisunder",
      "gotoifdistancetoisover", "gotoifdistancetoisunder", "gotoifdistancetoequals",
      "gotoifisreachable", "gotoifnotreachable",
      "gotoifinvfull",
      "gotoifitemcountinvis", "gotoifcountinvitemis", "gotoifitemcountinvover", "gotoifitemcountinvunder",
      "gotoifcountinvis", "gotoifcountinvover", "gotoifcountinvunder",
      "gotoifequals", "gotoifover", "gotoifunder",
      "gotoifcurstatequals", "gotoifcurstatover", "gotoifcurstatunder",
      "gotoifmaxstatequals", "gotoifmaxstatover", "gotoifmaxstatunder",
      "gotoifincombat", "gotoifnotincombat",
      "gotoifsleeping", "gotoifnotisleeping", "gotoifnotsleeping",
      "gotoifloggedin", "gotoifloggedout", "gotoifquestmenu",
      "gotoifnpcnear", "gotoifnonpcnear",
      "gotoifitemisnear", "gotoifitemisnotnear", "gotoifnoitem",
      "gotoifinshop", "gotoifnotinshop",
      "gotoiflastchatmsg", "gotoifnotlastchatmsg",
      "gotoiflastprivatemsg", "gotoifnotlastprivatemsg",
      "gotoiflastservermsg", "gotoifnotlastservermsg",
      "gotoiflasttrade", "gotoifnotlasttrade"
   };

   private static final Set<String> COMMANDS = new HashSet<String>();

   static {
      for (int i = 0; i < NAMES.length; i++) {
         COMMANDS.add(NAMES[i]);
      }
   }
}
