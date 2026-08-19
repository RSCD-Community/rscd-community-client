package org.rscdaemon.client;

import org.rscdaemon.client.entityhandling.EntityHandler;
import org.rscdaemon.client.entityhandling.defs.DoorDef;
import org.rscdaemon.client.entityhandling.defs.GameObjectDef;
import org.rscdaemon.client.entityhandling.defs.ItemDef;
import org.rscdaemon.client.entityhandling.defs.NPCDef;
import org.rscdaemon.client.entityhandling.defs.PrayerDef;
import org.rscdaemon.client.entityhandling.defs.SpellDef;

/*
 * The third scripting tier: APOS.
 *
 * STS and TextScript are the same lineage -- one is a script *for* the other --
 * and both were written for the 2005 bot this client's Methods class comes
 * from. APOS is not related to either. It was a separate, later bot with its
 * own base class, its own naming convention and, more importantly, its own
 * shape of script, and enough people wrote for it that a pile of APOS scripts
 * is the other half of what a returning player is likely to still have on disk.
 *
 * The difference that matters is the lifecycle. An STS script is a *thread*:
 *
 *    public void MainBody(String[] Args) {
 *       while (Running()) { ...do the whole job...; Wait(600); }
 *    }
 *
 * An APOS script is a *state machine*, driven from outside:
 *
 *    public void init(String params) { ...set up... }
 *    public int  main()   { ...one decision...; return 600; }   // ms to wait
 *    public void paint()  { drawString("Logs: " + n, 115, 40, 1, 0xFFFFFF); }
 *
 * main() is called over and over and returns how long to leave it alone for.
 * That is the whole framework, and it is why an APOS script never loops and
 * never sleeps: doing either would stall the bot that owns the loop.
 *
 * This class is that bot. MainBody() -- the STS entry point, so ScriptRunner
 * needs no special case -- calls init() once and then main() forever, waiting
 * whatever main() asked for. Everything else here is the APOS method surface
 * translated onto Methods, which already talks to this client.
 *
 * Three conventions of the original are kept exactly, because scripts depend
 * on them:
 *
 *   - lowerCamelCase throughout, against STS's PascalCase. They are different
 *     APIs that happen to share a client; no attempt is made to unify them.
 *   - "index" for a live NPC or player slot, and the *inventory slot* for
 *     items -- getInventoryIndex(id) is how a script turns an id into one.
 *     getObjectById() and friends return {id, x, y}; getNpcById() returns
 *     {index, x, y}. Position 0 is -1 when nothing was found.
 *   - nothing is final. TPM_BuyRope and TPM_StealNatures both override
 *     talkToNpc() and useOnNpc() and call super from inside, to fold a wait
 *     into every call, and a final method would break both.
 *
 * What could not be honoured is noted at the method. There are four such
 * places, all of them things this client does not have rather than things the
 * original did not do.
 */
public class Script extends Methods {
   /* RSC's inventory is 30 slots; mudclient.inventoryItems is oversized. */
   private static final int INVENTORY_SIZE = 30;

   /* The sleeping bag, for useSleepingBag(). */
   private static final int SLEEPING_BAG = 1263;

   /* The smallest wait between two main() calls, for a script that returns 0. */
   private static final int MIN_TICK = 1;

   /*
    * BANKERS is not declared here. Both bots published a constant of that name
    * holding the same six npc ids, and Methods already has it, so an APOS
    * script sees it by inheritance. Redeclaring it would hide the field with a
    * copy of itself and give the client two lists to keep in step.
    *
    * SKILL and FIGHTMODES have no STS equivalent and are declared below.
    */

   /*
    * Skill names, in the order the client's stat arrays use, which is the order
    * getLevel()/getCurrentLevel()/getXpForLevel() are indexed by. Seven of the
    * sixteen scripts walk this array to build an XP/hr table, so its length is
    * load-bearing: it must be 19 and it must line up with mudclient.skillArray.
    */
   public final String[] SKILL = new String[]{
      "Attack", "Defense", "Strength", "Hits", "Ranged", "Prayer",
      "Magic", "Cooking", "Woodcut", "Fletching", "Fishing", "Firemaking",
      "Crafting", "Smithing", "Mining", "Herblaw", "Agility", "Thieving", "Runecrafting"
   };

   /*
    * Fight modes, indexed the way setFightMode() takes them. Scripts put this
    * straight into an AWT Choice and hand the selected index back, so the order
    * is the protocol's, not a display preference.
    */
   public final String[] FIGHTMODES = new String[]{
      "Controlled", "Aggressive", "Accurate", "Defensive"
   };

   /*
    * APOS's own constants, values taken from its javadoc's constant-values
    * page rather than guessed. The direction numbers are RSC's sprite facings,
    * which is what Mob.currentSprite holds, so getPlayerDirection() can return
    * the field untranslated.
    */
   public static final int MAX_INV_SIZE = INVENTORY_SIZE;

   public static final int DIR_NORTH = 0;
   public static final int DIR_NORTHWEST = 1;
   public static final int DIR_WEST = 2;
   public static final int DIR_SOUTHWEST = 3;
   public static final int DIR_SOUTH = 4;
   public static final int DIR_SOUTHEAST = 5;
   public static final int DIR_EAST = 6;
   public static final int DIR_NORTHEAST = 7;

   /*
    * Methods already publishes this list as SPELLS; APOS scripts spell it
    * SPELL. Aliased to the same array rather than copied so the two names can
    * never drift apart.
    */
   public final String[] SPELL = this.SPELLS;

   /* ---- paint buffer ----

      drawString() collects; ToShow() hands the collection to the renderer. The
      client calls ToShow() on its own thread, so the buffer is swapped under a
      lock rather than shared: a script that draws from main() as well as from
      paint() gets a torn frame at worst, never an exception. */
   private final Object paintLock = new Object();
   private java.util.List<String> paintText = new java.util.ArrayList<String>();
   private java.util.List<int[]> paintWhere = new java.util.ArrayList<int[]>();

   /*
    * autohop()'s flag. Stored and readable, and that is all it can be -- see
    * the method.
    */
   private boolean autohop;

   /*
    * APOS's client flags. Each is stored here because a script can read back
    * what it set; whether the client acts on it is noted at the setter.
    */
   private boolean rendering = true;

   /*
    * Whether the overlay is drawn.
    *
    * ONE flag, deliberately, shared with PaintListener: setPaintOverlay(),
    * PaintListener.toggle() and PaintListener.setEnabled() are three names for
    * this switch, not three switches.
    *
    * It was briefly two here, on the reasoning that the user's keypress and
    * the script's own setting are separate intentions, and that a script
    * writing the flag each pass would override the keypress. APOS does not
    * work that way: its Script class carries no paint field at all, so
    * setPaintOverlay can only be delegating to the listener's own state.
    * B_Smithy settles it -- it saves the flag with isPaintOverlay(), clears it
    * with setPaintOverlay(false) to take a clean screenshot, then restores it
    * with BOTH PaintListener.toggle() and setPaintOverlay(true). That sequence
    * is only harmless if the two are the same switch; with two flags the
    * toggle turns the listener off and the HUD never comes back.
    */
   private volatile boolean paintOverlay = true;
   private boolean skipLines;
   private boolean trickMode;
   private boolean keysEnabled = true;

   public Script(Extension e) {
      super(e == null ? null : e.rs);
   }

   /*
    * ScriptRunner constructs scripts with a mudclient when it can and an
    * Extension when it cannot. Both exist so that a script written for either
    * bot loads through the same path.
    */
   public Script(mudclient mc) {
      super(mc);
   }

   /* ================= the APOS lifecycle ================= */

   /** Called once, before the first main(), with the arguments the script was started with. */
   public void init(String params) {
   }

   /**
    * One step of the script. Returns how many milliseconds to wait before the
    * next call; 0 or less means "immediately".
    */
   public int main() {
      return 1000;
   }

   /** Called once per frame to draw the script's overlay, via drawString(). */
   public void paint() {
   }

   /** Called when a server message arrives. */
   public void onServerMessage(String message) {
   }

   /**
    * Called when someone speaks near you. The two booleans were APOS's
    * "sender is a moderator" and "sender is an administrator"; this client is
    * not told either, so both arrive false. Nothing in the surviving corpus
    * reads them -- TPM_RefBot names them paramBoolean1 and paramBoolean2.
    */
   public void onChatMessage(String message, String player, boolean moderator, boolean administrator) {
   }

   /** Called when a private message arrives. The booleans are as onChatMessage. */
   public void onPrivateMessage(String message, String player, boolean moderator, boolean administrator) {
   }

   /** Called when another player sends you a trade request. */
   public void onTradeRequest(String player) {
   }

   /* ================= the driver ================= */

   /*
    * The STS entry point, so that from ScriptRunner's point of view an APOS
    * script is just another Methods. init() runs once; main() runs until the
    * script stops itself, the player stops it, or it throws.
    *
    * A throw out of main() ends the script rather than being swallowed. APOS
    * did the same, and the alternative -- carrying on -- means a script with a
    * null dereference in one branch loops on the exception forever with the
    * character standing still.
    */
   public final void MainBody(String[] Args) {
      CURRENT.set(this);

      try {
         this.runBody(Args);
      } finally {
         CURRENT.remove();
      }
   }

   /*
    * Which script is running on this thread.
    *
    * APOS's PaintListener.toggle() is static and takes no arguments, so it has
    * to find its script somehow. A plain static field would be wrong here --
    * the launcher runs a client per tab, so there can be more than one script
    * at once -- but each runs on its own thread, which makes the thread the
    * right key.
    */
   private static final ThreadLocal<Script> CURRENT = new ThreadLocal<Script>();

   static Script current() {
      return CURRENT.get();
   }

   private void runBody(String[] Args) {
      try {
         this.init(join(Args));
      } catch (Throwable var4) {
         this.report("init", var4);
         return;
      }

      while (this.Running()) {
         int delay;

         try {
            delay = this.main();
         } catch (Throwable var3) {
            this.report("main", var3);
            return;
         }

         if (!this.Running()) {
            return;
         }

         this.Wait(delay > MIN_TICK ? delay : MIN_TICK);
      }
   }

   /*
    * APOS took its parameters as one string, this client takes them as the
    * comma-separated list inside /start Name(a,b,c). Joined back with commas,
    * so a script that splits on them gets what its author typed and a script
    * that ignores them -- which is all sixteen of these -- is unaffected.
    */
   private static String join(String[] args) {
      if (args == null || args.length == 0) {
         return "";
      }

      StringBuilder joined = new StringBuilder();

      for (int i = 0; i < args.length; i++) {
         if (i > 0) {
            joined.append(',');
         }

         joined.append(args[i]);
      }

      return joined.toString();
   }

   private void report(String where, Throwable cause) {
      this.Display("@red@Script threw in " + where + "(): " + cause);
      cause.printStackTrace();
      this.End();
   }

   /* ================= events ================= */

   /*
    * The STS hooks, translated. Argument order is the reason these exist:
    * OnChatMessage is (sender, message) and onChatMessage is (message, player).
    * Getting that backwards is silent -- both are strings -- so it is done
    * once, here.
    */

   public final void OnChatMessage(String sender, String message) {
      this.onChatMessage(message, sender, false, false);
   }

   public final void OnPrivateMessage(String sender, String message) {
      this.onPrivateMessage(message, sender, false, false);
   }

   public final void OnServerMessage(String message) {
      this.onServerMessage(message);

      /*
       * APOS had a trade-request hook because its client raised one. This one
       * does not: a trade request arrives as an ordinary server message,
       * "@gry@ Name wishes to trade with you" (see the server's
       * TradeHandler). So the name is cut back out of it. Colour tags are
       * stripped by taking everything after the last '@'.
       */
      int wishes = message == null ? -1 : message.indexOf(" wishes to trade");

      if (wishes != -1) {
         String who = message.substring(0, wishes);
         int tag = who.lastIndexOf('@');
         this.onTradeRequest(tag == -1 ? who.trim() : who.substring(tag + 1).trim());
      }
   }

   /*
    * paint() runs here, inline on the client thread, because ToShow() returns
    * the thing being drawn this frame. A script that throws in paint() loses
    * its overlay for that frame and nothing else.
    */
   public final Stats ToShow() {
      synchronized (this.paintLock) {
         this.paintText = new java.util.ArrayList<String>();
         this.paintWhere = new java.util.ArrayList<int[]>();
         this.paintOps = new java.util.ArrayList<int[]>();
      }

      /* Either switch being off means "do not call paint() and draw nothing",
         so both short-circuit here rather than at the drawing end: nobody
         should be paying for an overlay that is switched off. */
      if (!this.paintOverlay) {
         return null;
      }

      try {
         this.paint();
      } catch (Throwable var8) {
         return null;
      }

      synchronized (this.paintLock) {
         int lines = this.paintText.size();

         if (lines == 0) {
            return null;
         }

         String[] text = new String[lines];
         int[] x = new int[lines];
         int[] y = new int[lines];
         int[] fonts = new int[lines];
         int[] colours = new int[lines];

         for (int i = 0; i < lines; i++) {
            int[] where = this.paintWhere.get(i);
            text[i] = this.paintText.get(i);
            x[i] = where[0];
            y[i] = where[1];
            fonts[i] = where[2];
            colours[i] = where[3];
         }

         return new Stats(text, x, y, fonts, colours);
      }
   }

   /**
    * Draw one line over the game view. Only meaningful from inside paint():
    * the buffer is emptied at the start of every frame, so a line drawn from
    * main() survives only until the next one is rendered.
    */
   public void drawString(String text, int x, int y, int font, int colour) {
      if (text == null) {
         return;
      }

      synchronized (this.paintLock) {
         this.paintText.add(text);
         this.paintWhere.add(new int[]{x, y, font, colour});
      }
   }

   /* ================= the script's own controls ================= */

   /** Stop this script. */
   public void stopScript() {
      this.End();
   }

   /** Wait, in milliseconds. Returns as soon as the script is stopped. */
   public void sleep(int ms) {
      this.Wait(ms);
   }

   /**
    * A random number between the two bounds, both included. APOS's own was
    * exclusive at the top, but every call in the corpus is a jitter or a
    * coin-flip -- random(1,500), random(0,1) -- where one extra value at the
    * end changes nothing, and matching Methods.Rand() keeps one definition of
    * randomness in the client instead of two.
    */
   public int random(int low, int high) {
      return this.Rand(low, high);
   }

   public boolean inArray(int[] array, int value) {
      return this.InArray(array, value);
   }

   public boolean inArray(String[] array, String value) {
      return this.InArray(array, value);
   }

   /* ================= you ================= */

   public int getX() {
      return this.GetX();
   }

   public int getY() {
      return this.GetY();
   }

   public int getFatigue() {
      return this.GetFatigue();
   }

   /** Your current hits as a percentage of your base level. */
   public int getHpPercent() {
      return this.HitsPercent();
   }

   public boolean inCombat() {
      return this.InCombat();
   }

   public boolean isWalking() {
      return this.IsWalking();
   }

   /** Base level in the given skill, 0-17; see SKILL. */
   public int getLevel(int skill) {
      return this.GetMaxLvl(skill);
   }

   /** Current level in the given skill, which is the one a boost or a drain moves. */
   public int getCurrentLevel(int skill) {
      return this.GetCurLvl(skill);
   }

   /**
    * Experience in the given skill.
    *
    * The name says "xp for level" and the method returns nothing of the sort.
    * That is not a mistake here: every use of it in the corpus is
    *
    *    initialXP[i] = getXpForLevel(i);        // at init
    *    gained = getXpForLevel(i) - initialXP[i];
    *
    * across all 18 skills, to build an XP/hr readout. Seven scripts do it and
    * none of them ever passes a level. So it is current experience, and the
    * name is the original's.
    */
   public int getXpForLevel(int skill) {
      return (int)this.GetExp(skill);
   }

   public boolean isSleeping() {
      return this.Sleeping();
   }

   /**
    * Use the sleeping bag. Does nothing when none is carried.
    *
    * This works: the server locks you, and 1.5 seconds later fatigue is zero.
    * What it does not do is put a sleep screen in front of you with a word to
    * type, and it never will -- see Methods.Sleeping() for why. So a script
    * guarded by "if (getFatigue() > 95 && !isSleeping())" fires, and may fire
    * twice inside that second and a half, which costs nothing.
    */
   public void useSleepingBag() {
      int slot = this.GetItemPos(SLEEPING_BAG);

      if (slot != -1) {
         this.UseItem(slot);
      }
   }

   public boolean isPrayerEnabled(int prayer) {
      return this.IsPrayerOn(prayer);
   }

   public void enablePrayer(int prayer) {
      this.PrayerOn(prayer);
   }

   public void disablePrayer(int prayer) {
      this.PrayerOff(prayer);
   }

   /** 0 controlled, 1 aggressive, 2 accurate, 3 defensive; see FIGHTMODES. */
   public void setFightMode(int mode) {
      this.SetMode(mode);
   }

   public int getFightMode() {
      return this.GetMode();
   }

   public void setAutoLogin(boolean on) {
      this.AutoLogin(on);
   }

   /**
    * Whether to hop worlds by itself.
    *
    * APOS hopped when it saw a player it had been told to avoid. Nothing in
    * this client hops on its own -- there is no watch list and no trigger --
    * so the flag is stored and never acted on. Kept because five scripts turn
    * it off at startup and would not compile without it, and stored rather
    * than dropped so that a bot layer added later has the setting to read.
    */
   public void autohop(boolean on) {
      this.autohop = on;
   }

   public boolean isAutohop() {
      return this.autohop;
   }

   public int getWorld() {
      return this.GetWorld();
   }

   public void hop(int world) {
      this.HopServer(world);
   }

   /* ================= movement ================= */

   public void walkTo(int x, int y) {
      this.WalkTo(x, y);
   }

   public boolean isReachable(int x, int y) {
      return this.IsReachable(x, y);
   }

   /** Distance from you to the given tile, as the crow flies. */
   public int distanceTo(int x, int y) {
      return this.DistanceTo(x, y);
   }

   public int distanceTo(int x1, int y1, int x2, int y2) {
      return this.DistanceTo(x1, y1, x2, y2);
   }

   /* ================= inventory ================= */

   public int getInventoryCount() {
      return this.CountInv();
   }

   /**
    * How many of any of these are carried, added together.
    *
    * Varargs, as APOS declared it, rather than the int/int[] pair this used to
    * have. Varargs accepts both call forms, so nothing that compiled before
    * stops compiling, and the corpus scripts that pass three ids inline now
    * work instead of failing to resolve.
    */
   public int getInventoryCount(int... ids) {
      return ids == null || ids.length == 0 ? 0 : this.CountInv(ids);
   }

   /*
    * The slot of the first of these that is carried, or -1.
    *
    * "First" is by the order of the list, not by inventory position, which is
    * what makes it useful: TPM_BlueDrags passes PICKAXES best-first and
    * TPM_WaterfallFG passes STRENGTH_POTIONS four-dose-first, and both expect
    * to be handed the best one they have rather than whichever happens to sit
    * nearest the top of the bag.
    */
   public int getInventoryIndex(int... ids) {
      if (ids == null) {
         return -1;
      }

      for (int i = 0; i < ids.length; i++) {
         int slot = this.GetItemPos(ids[i]);

         if (slot != -1) {
            return slot;
         }
      }

      return -1;
   }

   /** Use the item in the given slot -- eat it, bury it, light it. */
   public void useItem(int slot) {
      this.UseItem(slot);
   }

   public void useItemWithItem(int slot1, int slot2) {
      this.UseItemWithItem(slot1, slot2);
   }

   public void dropItem(int slot) {
      this.DropItem(slot);
   }

   public void wearItem(int slot) {
      this.WearItem(slot);
   }

   public boolean isItemEquipped(int slot) {
      return this.WearingItem(slot);
   }

   /**
    * The command written on the item in the given slot -- "Eat", "Bury",
    * "Light". Scripts lowercase it and compare, which is how TPM_CutAndBurn
    * tells a log from food without a table of item ids.
    */
   public String getItemCommand(int slot) {
      int id = this.InvItemId(slot);

      if (id == -1) {
         return "";
      }

      String command = EntityHandler.getItemDef(id).getCommand();
      return command == null ? "" : command;
   }

   /* ================= ground items ================= */

   public int getGroundItemCount() {
      return this.CountItems();
   }

   public int getGroundItemId(int index) {
      return this.ItemId(index);
   }

   public int getItemX(int index) {
      return this.ItemX(index);
   }

   public int getItemY(int index) {
      return this.ItemY(index);
   }

   /** {id, x, y} of the nearest one of these, or {-1, -1, -1}. */
   public int[] getItemById(int... ids) {
      return ids == null || ids.length == 0
         ? new int[]{-1, -1, -1} : this.GetItemById(ids);
   }

   /** Pick up the given item at the given tile. Note the id comes first. */
   public void pickupItem(int id, int x, int y) {
      this.PickupItem(x, y, id);
   }

   /** Use the item in the given inventory slot on a specific item on the floor. */
   public void useItemOnGroundItem(int slot, int id, int x, int y) {
      this.UseItemOnGItem(slot, x, y, id);
   }

   /* ================= objects ================= */

   /** {id, x, y} of the nearest one of these, or {-1, -1, -1}. */
   public int[] getObjectById(int... ids) {
      return ids == null || ids.length == 0
         ? new int[]{-1, -1, -1} : this.GetObjectById(ids);
   }

   /** The object's first command -- open a door, chop a tree, climb a ladder. */
   public void atObject(int x, int y) {
      this.AtObject(x, y);
   }

   /** The object's second command, where it has one. */
   public void atObject2(int x, int y) {
      this.AtObject2(x, y);
   }

   /*
    * Use an item on an object.
    *
    * The argument is an ITEM ID, not a slot. This was previously guessed at --
    * the corpus has call sites that read either way, so the client accepted
    * both and used "is this a usable slot?" to decide. APOS's own javadoc
    * settles it: useItemOnObject takes an id, and the slot form is a separate
    * method, useSlotOnObject. The guess was wrong for any genuine item id
    * below 30, which it would have silently treated as a slot.
    */
   public void useItemOnObject(int itemId, int objectX, int objectY) {
      int slot = this.GetItemPos(itemId);

      if (slot != -1) {
         this.UseOnObject(slot, objectX, objectY);
      }
   }

   /** Use an item on the nearest object of the given id. */
   public void useItemOnObject(int itemId, int objectId) {
      int[] object = this.GetObjectById(objectId);

      if (object[0] != -1) {
         this.useItemOnObject(itemId, object[1], object[2]);
      }
   }

   /** Use the item in the given inventory slot on the object at these tiles. */
   public void useSlotOnObject(int slot, int objectX, int objectY) {
      this.UseOnObject(slot, objectX, objectY);
   }

   /* ================= wall objects ================= */

   public int[] getWallObjectById(int... ids) {
      return ids == null || ids.length == 0
         ? new int[]{-1, -1, -1} : this.GetWallObjectById(ids);
   }

   public void atWallObject(int x, int y) {
      this.AtWallObject(x, y);
   }

   public void atWallObject2(int x, int y) {
      this.AtWallObject2(x, y);
   }

   /* ================= npcs ================= */

   public int countNpcs() {
      return this.CountNpcs();
   }

   /** {index, x, y} of the nearest one, skipping any already in combat. */
   public int[] getNpcById(int... ids) {
      return ids == null || ids.length == 0
         ? new int[]{-1, -1, -1} : this.GetNpcById(ids);
   }

   /** As getNpcById, and also skipping any already in conversation. */
   public int[] getNpcByIdNotTalk(int... ids) {
      return ids == null || ids.length == 0
         ? new int[]{-1, -1, -1} : this.GetNpcByIdNotTalk(ids);
   }

   public int getNpcId(int index) {
      return this.NpcId(index);
   }

   public int getNpcX(int index) {
      return this.NpcX(index);
   }

   public int getNpcY(int index) {
      return this.NpcY(index);
   }

   public boolean isNpcInCombat(int index) {
      return this.NpcInCombat(index);
   }

   public void attackNpc(int index) {
      this.AttackNpc(index);
   }

   public void talkToNpc(int index) {
      this.TalkToNpc(index);
   }

   /** Use the item in the given slot on the given NPC. NPC first, slot second. */
   public void useOnNpc(int index, int slot) {
      this.UseOnNpc(slot, index);
   }

   /* ================= the quest menu ================= */

   public boolean isQuestMenu() {
      return this.QuestMenu();
   }

   public void answer(int option) {
      this.Answer(option);
   }

   /**
    * The position of the given option on the menu, or -1.
    *
    * Exact match first, because that is what the original did and what a
    * script that quotes an option verbatim expects. Then a trimmed,
    * case-insensitive pass, because the two calls in the corpus quote lines
    * from a 2006 server -- "Okay, please sell me some Rope" -- and one
    * changed capital would otherwise send the script down a branch it never
    * intended.
    */
   public int getMenuIndex(String option) {
      if (option == null) {
         return -1;
      }

      int count = this.CountQuestMenu();

      for (int i = 0; i < count; i++) {
         if (option.equals(this.GetQuestOption(i))) {
            return i;
         }
      }

      for (int i = 0; i < count; i++) {
         if (option.trim().equalsIgnoreCase(this.GetQuestOption(i).trim())) {
            return i;
         }
      }

      return -1;
   }

   /* ================= magic ================= */

   public void castOnSelf(int spell) {
      this.CastOnSelf(spell);
   }

   /* ================= the bank ================= */

   public boolean isBanking() {
      return this.InBank();
   }

   public void closeBank() {
      this.CloseBank();
   }

   /** The number of distinct items in the bank. */
   public int bankCount() {
      return this.BankCount();
   }

   /**
    * How many of these items are in the bank, added together.
    *
    * The no-argument bankCount() above is kept as its own overload rather than
    * folded in here: APOS spells that one getBankSize(), and Java picks the
    * exact match over the varargs, so both readings keep working.
    */
   public int bankCount(int... ids) {
      int total = 0;

      if (ids != null) {
         for (int i = 0; i < ids.length; i++) {
            total += this.CountInBank(ids[i]);
         }
      }

      return total;
   }

   public void withdraw(int id, int amount) {
      this.Withdraw(id, amount);
   }

   public void deposit(int id, int amount) {
      this.Deposit(id, amount);
   }

   /* ================= other players ================= */

   /** {index, x, y} of the named player, or {-1, -1, -1}. */
   public int[] getPlayerByName(String name) {
      return this.GetPlayerByName(name);
   }

   /**
    * {index, x, y} of the player with the given server id, or {-1, -1, -1}.
    *
    * A pid is the number the server gave that player for this session; an
    * index is where they happen to sit in the client's own list this tick.
    * The index changes as people walk in and out of view, the pid does not,
    * which is why TPM_RefBot stores pids and resolves them again every time.
    */
   public int[] getPlayerByPid(int pid) {
      if (this.rs.playerArray == null) {
         return new int[]{-1, -1, -1};
      }

      for (int i = 0; i < this.rs.playerCount; i++) {
         Mob player = this.rs.playerArray[i];

         if (player != null && player.serverIndex == pid) {
            return new int[]{i, this.PlayerX(i), this.PlayerY(i)};
         }
      }

      return new int[]{-1, -1, -1};
   }

   /** The server id of the player at the given index, or -1. */
   public int getPlayerPID(int index) {
      Mob player = this.mob(index);
      return player == null ? -1 : player.serverIndex;
   }

   public String getPlayerName(int index) {
      return this.PlayerName(index);
   }

   public int getPlayerX(int index) {
      return this.PlayerX(index);
   }

   public int getPlayerY(int index) {
      return this.PlayerY(index);
   }

   public int getPlayerCombatLevel(int index) {
      Mob player = this.mob(index);
      return player == null ? -1 : player.level;
   }

   public boolean isPlayerInCombat(int index) {
      return this.PlayerInCombat(index);
   }

   private Mob mob(int index) {
      return this.rs.playerArray != null && index >= 0 && index < this.rs.playerCount
         ? this.rs.playerArray[index]
         : null;
   }

   /* ================= friends and messages ================= */

   public boolean isFriend(String name) {
      return this.IsOnFriendList(name);
   }

   public void addFriend(String name) {
      this.AddToFriends(name);
   }

   /** Note the order: the message first, then who it goes to. */
   public void sendPrivateMessage(String message, String player) {
      this.SendPM(player, message);
   }

   /* ================= trading ================= */

   /** Ask the player with the given server id to trade. */
   public void sendTradeRequest(int pid) {
      int[] player = this.getPlayerByPid(pid);

      if (player[0] != -1) {
         this.TradePlayer(player[0]);
      }
   }

   /** True on the first trade screen, where items are put up. */
   public boolean isInTradeOffer() {
      return this.InTradeScreen1();
   }

   /** True on the second trade screen, where both sides confirm. */
   public boolean isInTradeConfirm() {
      return this.InTradeScreen2();
   }

   /** Accept the first trade screen. */
   public void acceptTrade() {
      this.AcceptTrade1();
   }

   /** Accept the second trade screen, which completes the trade. */
   public void confirmTrade() {
      this.AcceptTrade2();
   }

   public void declineTrade() {
      this.DeclineTrade();
   }

   /** Add the item in the given inventory slot to your side of the offer. */
   public void offerItemTrade(int slot, int amount) {
      int id = this.InvItemId(slot);

      if (id != -1) {
         this.AddToOffer(id, amount);
      }
   }

   /** How many separate stacks you have put up. */
   public int getLocalTradeItemCount() {
      return this.rs.tradeMyItemCount;
   }

   /** How many separate stacks the other side has put up. */
   public int getRemoteTradeItemCount() {
      return this.rs.tradeOtherItemCount;
   }

   /** True when the other side has offered at least that much of that item. */
   public boolean hasOtherTraded(int id, int amount) {
      return this.IsOffered(id, amount);
   }

   /* =========================================================================
    * The rest of the APOS surface.
    *
    * Written against APOS's published javadoc (Script.html, 245 entries), not
    * against its bytecode: nothing here is decompiled from bot.jar. The doc
    * gives the contract -- parameter meanings, what a lookup returns when it
    * finds nothing -- and the implementations below are this client's own.
    *
    * Where the doc and the corpus disagreed, the doc won; see useItemOnObject.
    * ========================================================================= */

   /* ---------------- client state ---------------- */

   /** Log out, politely: refused in combat and for ten seconds after. */
   public void logout() {
      this.LogOut();
   }

   /** True when a logout request would actually be honoured. */
   public boolean canLogout() {
      return this.CanLogOut();
   }

   public boolean isLoggedIn() {
      return this.LoggedIn();
   }

   public boolean isAutoLogin() {
      return this.IsAutoLogin();
   }

   /** True while our own speech bubble is still up. */
   public boolean isTalking() {
      return this.rs != null && this.rs.ourPlayer != null
         && this.rs.ourPlayer.lastMessageTimeout > 0;
   }

   public boolean isHpBarShowing() {
      return this.HealthBarShowing();
   }

   /**
    * True while an action is in progress.
    *
    * RSC has no "skilling" flag on the wire. What it has is the client's own
    * action lock -- the same counter that greys the interface while you chop
    * or mine -- which is what this reads.
    */
   public boolean isSkilling() {
      return this.rs != null && this.rs.lastWalkTimeout > 0 && !this.InCombat();
   }

   /**
    * Fatigue as a fraction of a percent.
    *
    * The server sends fatigue as a 0..750 counter and the interface rounds it
    * to a percentage; this exposes the unrounded value, which is the whole
    * reason APOS separated it from getFatigue().
    */
   public double getAccurateFatigue() {
      return this.rs == null ? 0.0D : this.rs.fatigue * 100.0D / 750.0D;
   }

   public double getAccurateXpForLevel(int skill) {
      return this.getXpForLevel(skill);
   }

   /* ---------------- inventory ---------------- */

   public int getEmptySlots() {
      return MAX_INV_SIZE - this.CountInv();
   }

   public boolean hasInventoryItem(int id) {
      return this.InInv(id);
   }

   /**
    * Unequip the item in the given slot.
    *
    * APOS's name for this is removeItem and it means "take it off", not "throw
    * it away" -- dropItem is the one that throws it away. Reading the name the
    * other way round would have had scripts binning their armour.
    */
   public void removeItem(int slot) {
      this.RemoveItem(slot);
   }

   public int getInventoryId(int slot) {
      return this.InvItemId(slot);
   }

   /** The size of the stack in the given slot, or 0. */
   public int getInventoryStack(int slot) {
      if (this.rs == null || slot < 0 || slot >= this.rs.inventoryCount) {
         return 0;
      }

      return this.rs.inventoryItemsCount[slot];
   }

   public String getItemName(int slot) {
      return getItemNameId(this.InvItemId(slot));
   }

   public String getItemDescription(int slot) {
      return getItemDescriptionId(this.InvItemId(slot));
   }

   public String getItemCommandId(int id) {
      ItemDef def = id < 0 ? null : EntityHandler.getItemDef(id);
      return def == null ? "" : def.getCommand();
   }

   public boolean isItemTradable(int slot) {
      return isItemTradableId(this.InvItemId(slot));
   }

   public boolean isItemStackable(int slot) {
      return isItemStackableId(this.InvItemId(slot));
   }

   public int getItemBasePrice(int slot) {
      return getItemBasePriceId(this.InvItemId(slot));
   }

   public void castOnItem(int spell, int slot) {
      this.CastOnItem(spell, slot);
   }

   /* ---------------- item definitions ---------------- */

   public static String getItemNameId(int id) {
      ItemDef def = id < 0 ? null : EntityHandler.getItemDef(id);
      return def == null ? "" : def.getName();
   }

   public static String getItemDescriptionId(int id) {
      ItemDef def = id < 0 ? null : EntityHandler.getItemDef(id);
      return def == null ? "" : def.getDescription();
   }

   public static boolean isItemStackableId(int id) {
      ItemDef def = id < 0 ? null : EntityHandler.getItemDef(id);
      return def != null && def.isStackable();
   }

   /**
    * Whether the item may be traded.
    *
    * The client's item definitions carry no untradable flag -- RSC enforced
    * that server-side -- so this can only answer for items the client knows
    * exist. Everything real is tradable as far as the cache is concerned.
    */
   public static boolean isItemTradableId(int id) {
      return id >= 0 && EntityHandler.getItemDef(id) != null;
   }

   public static int getItemBasePriceId(int id) {
      ItemDef def = id < 0 ? null : EntityHandler.getItemDef(id);
      return def == null ? 0 : def.getBasePrice();
   }

   /* ---------------- ground items ---------------- */

   public boolean isItemAt(int id, int x, int y) {
      return this.IsItemAt(x, y, id);
   }

   public void castOnGroundItem(int spell, int itemId, int itemX, int itemY) {
      this.CastOnGItem(spell, itemId, itemX, itemY);
   }

   /* ---------------- objects ---------------- */

   public int getObjectCount() {
      return this.CountObjects();
   }

   public int getObjectId(int index) {
      return this.ObjectId(index);
   }

   public int getObjectX(int index) {
      return this.ObjectX(index);
   }

   public int getObjectY(int index) {
      return this.ObjectY(index);
   }

   /** The id of the object on that tile, or -1. */
   public int getObjectIdFromCoords(int x, int y) {
      return this.GetIdObject(x, y);
   }

   public boolean isObjectAt(int x, int y) {
      return this.GetIdObject(x, y) != -1;
   }

   public static String getObjectName(int id) {
      GameObjectDef def = id < 0 ? null : EntityHandler.getObjectDef(id);
      return def == null ? "" : def.getName();
   }

   public static String getObjectDesc(int id) {
      GameObjectDef def = id < 0 ? null : EntityHandler.getObjectDef(id);
      return def == null ? "" : def.getDescription();
   }

   /* ---------------- wall objects ---------------- */

   /** The id of the boundary on that tile, or -1. */
   public int getWallObjectIdFromCoords(int x, int y) {
      return this.GetIdWallObject(x, y);
   }

   public int getWallObjectCount() {
      return this.CountWallObjects();
   }

   public int getWallObjectId(int index) {
      return this.WallObjectId(index);
   }

   public int getWallObjectX(int index) {
      return this.WallObjectX(index);
   }

   public int getWallObjectY(int index) {
      return this.WallObjectY(index);
   }

   public static String getWallObjectName(int id) {
      DoorDef def = id < 0 ? null : EntityHandler.getDoorDef(id);
      return def == null ? "" : def.getName();
   }

   public static String getWallObjectDesc(int id) {
      DoorDef def = id < 0 ? null : EntityHandler.getDoorDef(id);
      return def == null ? "" : def.getDescription();
   }

   public void useItemOnWallObject(int slot, int x, int y) {
      this.UseOnWallObject(slot, x, y);
   }

   /* ---------------- npcs ---------------- */

   /** As getNpcById, but not skipping npcs that are in combat or talking. */
   public int[] getAllNpcById(int... ids) {
      return ids == null || ids.length == 0
         ? new int[]{-1, -1, -1} : this.GetAllNpcById(ids);
   }

   /**
    * The nearest npc of that id within radius tiles of a fixed point.
    *
    * The centre is the given tile, not the player: this is how a script pins
    * itself to one spawn instead of wandering after whatever drifts closest.
    */
   public final int[] getNpcInRadius(int id, int startX, int startY, int radius) {
      return this.getNpcInExtendedRadius(id, startX, startY, radius, radius);
   }

   /** As getNpcInRadius with a separate horizontal and vertical reach. */
   public int[] getNpcInExtendedRadius(int id, int startX, int startY, int latitude, int longitude) {
      int count = this.CountNpcs();
      int[] best = new int[]{-1, -1, -1};
      int bestDistance = Integer.MAX_VALUE;

      for (int i = 0; i < count; i++) {
         if (this.NpcId(i) != id) {
            continue;
         }

         int x = this.NpcX(i);
         int y = this.NpcY(i);

         if (Math.abs(x - startX) > latitude || Math.abs(y - startY) > longitude) {
            continue;
         }

         int distance = this.DistanceTo(this.GetX(), this.GetY(), x, y);

         if (distance < bestDistance) {
            bestDistance = distance;
            best = new int[]{i, x, y};
         }
      }

      return best;
   }

   public void thieveNpc(int index) {
      this.ThieveNpc(index);
   }

   public void mageNpc(int index, int spell) {
      this.CastOnNpc(spell, index);
   }

   public boolean isNpcTalking(int index) {
      return this.IsNpcTalking(index);
   }

   public boolean isNpcHpBarVisible(int index) {
      return this.NpcHealthBarShowing(index);
   }

   public String getNpcName(int index) {
      return getNpcNameId(this.NpcId(index));
   }

   public String getNpcDescription(int index) {
      return getNpcDescriptionId(this.NpcId(index));
   }

   public int getNpcCombatLevel(int index) {
      return getNpcCombatLevelId(this.NpcId(index));
   }

   public static String getNpcNameId(int id) {
      NPCDef def = id < 0 ? null : EntityHandler.getNpcDef(id);
      return def == null ? "" : def.getName();
   }

   public static String getNpcDescriptionId(int id) {
      NPCDef def = id < 0 ? null : EntityHandler.getNpcDef(id);
      return def == null ? "" : def.getDescription();
   }

   /**
    * An npc's combat level, worked out from its stats.
    *
    * RSC scored the melee half and the defensive half separately; npcs have no
    * prayer, ranged or magic levels in the cache, so those terms drop out and
    * what is left is this.
    */
   public static int getNpcCombatLevelId(int id) {
      NPCDef def = id < 0 ? null : EntityHandler.getNpcDef(id);

      if (def == null) {
         return 0;
      }

      double base = (def.defense + def.hits) * 0.25D;
      double melee = (def.attack + def.strength) * 0.325D;
      return (int) (base + melee);
   }

   /* ---------------- players ---------------- */

   public int countPlayers() {
      return this.CountPlayers();
   }

   /** Which way the player is facing, as one of the DIR_ constants. */
   public int getPlayerDirection(int index) {
      Mob mob = this.player(index);
      return mob == null ? -1 : mob.currentSprite;
   }

   public boolean isPlayerHpBarVisible(int index) {
      Mob mob = this.player(index);
      return mob != null && mob.combatTimer > 0;
   }

   public boolean isPlayerTalking(int index) {
      Mob mob = this.player(index);
      return mob != null && mob.lastMessageTimeout > 0;
   }

   public boolean isPlayerWalking(int index) {
      Mob mob = this.player(index);
      return mob != null && mob.stepCount > 0;
   }

   /* ---------------- quest menu ---------------- */

   public int questMenuCount() {
      return this.CountQuestMenu();
   }

   public String getQuestMenuOption(int i) {
      return this.GetQuestOption(i);
   }

   /** Every option currently on the dialogue menu, in order. */
   public String[] questMenuOptions() {
      int count = this.CountQuestMenu();
      String[] options = new String[Math.max(count, 0)];

      for (int i = 0; i < options.length; i++) {
         String option = this.GetQuestOption(i);
         options[i] = option == null ? "" : option;
      }

      return options;
   }

   /* ---------------- quests ---------------- */

   public boolean isQuestComplete(int i) {
      return this.QuestDone(i);
   }

   /* ---------------- bank ---------------- */

   /** The number of distinct items in the bank. */
   public int getBankSize() {
      return this.BankCount();
   }

   public int getBankId(int i) {
      return this.BankItemId(i);
   }

   public boolean hasBankItem(int id) {
      return this.ItemInBank(id);
   }

   /* ---------------- shops ---------------- */

   public boolean isShopOpen() {
      return this.InShop();
   }

   /** The position of that item in the shop window, or -1. */
   public int getShopItemById(int id) {
      if (this.rs == null || this.rs.shopItems == null) {
         return -1;
      }

      for (int i = 0; i < this.rs.shopItems.length; i++) {
         if (this.rs.shopItems[i] == id) {
            return i;
         }
      }

      return -1;
   }

   public int getShopItemId(int i) {
      return this.rs != null && this.rs.shopItems != null
         && i >= 0 && i < this.rs.shopItems.length ? this.rs.shopItems[i] : -1;
   }

   /** How many of that shop position the shop currently holds. */
   public int getShopItemAmount(int i) {
      return this.rs != null && this.rs.shopItemCount != null
         && i >= 0 && i < this.rs.shopItemCount.length ? this.rs.shopItemCount[i] : 0;
   }

   /*
    * Buy and sell take a shop POSITION, which is what APOS documents, while
    * the client's own BuyShopItem/SellShopItem take an item id and move one at
    * a time. Translating here rather than changing those keeps the STS tier's
    * meaning of those names intact.
    */
   public void buyShopItem(int i, int amount) {
      int id = this.getShopItemId(i);

      if (id != -1) {
         for (int n = 0; n < amount; n++) {
            this.BuyShopItem(id);
         }
      }
   }

   public void sellShopItem(int i, int amount) {
      int id = this.getShopItemId(i);

      if (id != -1) {
         for (int n = 0; n < amount; n++) {
            this.SellShopItem(id);
         }
      }
   }

   public void closeShop() {
      this.CloseShop();
   }

   /* ---------------- trade ---------------- */

   public boolean hasLocalAcceptedTrade() {
      return this.MyTrade1Accepted();
   }

   public boolean hasRemoteAcceptedTrade() {
      return this.OpponentTradeAccepted();
   }

   /* ---------------- position ---------------- */

   /** True when we are standing within radius tiles of that spot. */
   public boolean isAtApproxCoords(int x, int y, int radius) {
      return Math.abs(this.GetX() - x) <= radius && Math.abs(this.GetY() - y) <= radius;
   }

   /* ---------------- spells and prayers ---------------- */

   /** True when we have both the magic level and the runes for it. */
   public boolean canCastSpell(int spell) {
      SpellDef def = spell < 0 ? null : EntityHandler.getSpellDef(spell);
      return def != null
         && this.GetMaxLvl(6) >= def.getReqLevel()
         && this.HasRunesForSpell(spell);
   }

   public static boolean isCombatSpell(int spell) {
      SpellDef def = spell < 0 ? null : EntityHandler.getSpellDef(spell);
      return def != null && def.getSpellType() == 2;
   }

   public boolean isCastableOnInv(int spell) {
      return this.IsCastableOnItem(spell);
   }

   public boolean isCastableOnGroundItem(int spell) {
      return this.IsCastableOnGItem(spell);
   }

   public boolean isCastableOnSelf(int spell) {
      return this.IsCastableOnSelf(spell);
   }

   public static int getPrayerCount() {
      return EntityHandler.prayerCount();
   }

   public static String getPrayerName(int i) {
      PrayerDef def = i < 0 ? null : EntityHandler.getPrayerDef(i);
      return def == null ? "" : def.getName();
   }

   public static int getPrayerLevel(int i) {
      PrayerDef def = i < 0 ? null : EntityHandler.getPrayerDef(i);
      return def == null ? 0 : def.getReqLevel();
   }

   /* ---------------- friends and ignores ---------------- */

   public void removeFriend(String name) {
      this.RemoveFromFriends(name);
   }

   public void addIgnore(String name) {
      this.AddToIgnore(name);
   }

   public void removeIgnore(String name) {
      this.RemoveFromIgnore(name);
   }

   public int getFriendCount() {
      String[] list = this.GetFriendList();
      return list == null ? 0 : list.length;
   }

   public String getFriendName(int i) {
      String[] list = this.GetFriendList();
      return list != null && i >= 0 && i < list.length ? list[i] : "";
   }

   public int getIgnoredCount() {
      String[] list = this.GetIgnoreList();
      return list == null ? 0 : list.length;
   }

   public String getIgnoredName(int i) {
      String[] list = this.GetIgnoreList();
      return list != null && i >= 0 && i < list.length ? list[i] : "";
   }

   public boolean isIgnored(String name) {
      return this.IsOnIgnoreList(name);
   }

   /* ---------------- client flags ---------------- */

   /** Print a line into the game's message pane. */
   public void writeLine(String str) {
      this.Display(str);
   }

   public void takeScreenshot(String file) {
      this.SaveScreenShot(file);
   }

   /**
    * Turn the 3D view off to save cycles.
    *
    * SetGfx is the client's own switch for this and it does exactly what the
    * doc warns about -- the buffer stops being updated, so the window keeps
    * whatever was last drawn until rendering comes back on.
    */
   public void setRendering(boolean flag) {
      this.rendering = flag;
      this.SetGfx(flag);
   }

   public boolean isRendering() {
      return this.rendering;
   }

   /**
    * Whether paint() is called and its overlay drawn.
    *
    * Read by the client each frame before it asks for the overlay, so turning
    * it off stops the script's own painting without stopping the script.
    */
   public void setPaintOverlay(boolean flag) {
      this.paintOverlay = flag;
   }

   public boolean isPaintOverlay() {
      return this.paintOverlay;
   }

   /*
    * Line skipping was a half-resolution mode in some bots. This client
    * renders every line and has no such mode, so the flag is stored and
    * reported honestly rather than pretending to take effect: a script that
    * sets it still runs, and one that reads it back gets what it set.
    */
   public void setSkipLines(boolean flag) {
      this.skipLines = flag;
   }

   public boolean isSkipLines() {
      return this.skipLines;
   }

   /*
    * Fatigue tricking. Stored only: the sleeping this client does on a
    * script's behalf is whatever the script asks for through useSleepingBag,
    * so there is no built-in sleep policy for this flag to modify. Kept so the
    * scripts that set it compile and behave as they would have against a bot
    * with the trick switched off.
    */
   public void setTrickMode(boolean flag) {
      this.trickMode = flag;
   }

   /** @deprecated APOS deprecated this in favour of setTrickMode. */
   @Deprecated
   public boolean isTricking() {
      return this.trickMode;
   }

   /** @deprecated Use getFatigue(); this is the same number. */
   @Deprecated
   public int getSleepingFatigue() {
      return this.GetFatigue();
   }

   /* ---------------- keys and typing ---------------- */

   /** Called when a key is pressed, unless the script has disabled keys. */
   public void onKeyPress(int keycode) {
   }

   /** Stop delivering key presses to this script. */
   public void disableKeys() {
      this.keysEnabled = false;
   }

   /*
    * The client's key hook is KeyPressed; APOS's is onKeyPress. Bridged here
    * so a script can override either one and still be called.
    */
   @Override
   public void KeyPressed(int key) {
      if (this.keysEnabled) {
         this.onKeyPress(key);
      }
   }

   /** Put a line of text into the chat box without sending it. */
   public void setTypeLine(String str) {
      if (this.rs != null) {
         this.rs.inputText = str == null ? "" : str;
      }
   }

   /**
    * Advance a dialogue that is waiting on a click to continue.
    *
    * Returns true if there was one to advance.
    */
   public boolean next() {
      if (this.IsPopup()) {
         this.ClosePopup();
         return true;
      }

      return false;
   }


   /* ---------------- actions against another player ---------------- */

   /*
    * The four player-targeted actions. Nothing in Methods did any of these --
    * the STS-era class covers npcs and objects thoroughly and simply stops at
    * players -- so the opcodes come from the server's own handler table:
    * AttackHandler 57, SpellHandler 55, InvUseOnPlayer 16, FollowRequest 68.
    *
    * Each walks to the target first, the same way AttackNpc does, because the
    * server checks range before it does anything else.
    */

   public void attackPlayer(int index) {
      Mob mob = this.player(index);

      if (mob != null) {
         this.walkToMob(mob);
         this.rs.streamClass.createPacket(57);
         this.rs.streamClass.add2ByteInt(mob.serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   public void magePlayer(int index, int spell) {
      Mob mob = this.player(index);

      if (mob != null) {
         this.walkToMob(mob);
         this.rs.streamClass.createPacket(55);
         this.rs.streamClass.add2ByteInt(spell);
         this.rs.streamClass.add2ByteInt(mob.serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   public void useItemWithPlayer(int index, int slot) {
      Mob mob = this.player(index);

      if (mob != null) {
         this.walkToMob(mob);
         this.rs.streamClass.createPacket(16);
         this.rs.streamClass.add2ByteInt(mob.serverIndex);
         this.rs.streamClass.add2ByteInt(slot);
         this.rs.streamClass.formatPacket();
      }
   }

   /*
    * Takes a server index rather than a local one, which is what APOS
    * documents and what the packet carries, so it is passed straight through.
    */
   public void followPlayer(int serverIndex) {
      if (this.rs != null) {
         this.rs.streamClass.createPacket(68);
         this.rs.streamClass.add2ByteInt(serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   /* ---------------- overlay drawing ---------------- */

   /*
    * APOS let a script draw shapes as well as text. drawString() went through
    * Stats because that is STS's contract and the client renders it; shapes
    * have no such contract, so they are queued here as commands and replayed
    * by the client against GameImage, which already has a primitive for every
    * one of them.
    *
    * Same buffer discipline as the text: filled during paint(), read on the
    * client thread straight afterwards, swapped rather than shared.
    */
   static final int OP_HLINE = 0;
   static final int OP_VLINE = 1;
   static final int OP_BOX_OUTLINE = 2;
   static final int OP_BOX_FILL = 3;
   static final int OP_BOX_ALPHA = 4;
   static final int OP_CIRCLE = 5;
   static final int OP_PIXEL = 6;

   private java.util.List<int[]> paintOps = new java.util.ArrayList<int[]>();

   private void op(int[] command) {
      synchronized (this.paintLock) {
         this.paintOps.add(command);
      }
   }

   /** The shapes queued by the last paint(). Client-side; not for scripts. */
   final int[][] shapes() {
      synchronized (this.paintLock) {
         return this.paintOps.toArray(new int[this.paintOps.size()][]);
      }
   }

   public void drawHLine(int x, int y, int length, int colour) {
      this.op(new int[]{OP_HLINE, x, y, length, colour});
   }

   public void drawVLine(int x, int y, int length, int colour) {
      this.op(new int[]{OP_VLINE, x, y, length, colour});
   }

   public void drawBoxOutline(int x, int y, int width, int height, int colour) {
      this.op(new int[]{OP_BOX_OUTLINE, x, y, width, height, colour});
   }

   public void drawBoxFill(int x, int y, int width, int height, int colour) {
      this.op(new int[]{OP_BOX_FILL, x, y, width, height, colour});
   }

   /** trans is 0 (invisible) to 255 (solid), as APOS documents it. */
   public void drawBoxAlphaFill(int x, int y, int width, int height, int trans, int colour) {
      this.op(new int[]{OP_BOX_ALPHA, x, y, width, height, trans, colour});
   }

   public void drawCircleFill(int x, int y, int radius, int colour, int trans) {
      this.op(new int[]{OP_CIRCLE, x, y, radius, colour, trans});
   }

   public void setPixel(int x, int y, int colour) {
      this.op(new int[]{OP_PIXEL, x, y, colour});
   }

   /*
    * The one APOS drawing call with no GameImage equivalent. An AWT Image
    * cannot be blitted into the client's int buffer without being decoded
    * first, and no script in the corpus calls it, so rather than build a
    * decoder nothing uses, this is a no-op that keeps such a script compiling
    * and running. Documented rather than silently absent.
    */
   public void drawImage(java.awt.Image image, int startX, int startY) {
   }

}
