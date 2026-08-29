import org.rscdaemon.client.*;
import org.rscdaemon.client.*;
import org.rscdaemon.client.*;
import org.rscdaemon.client.*;
import org.rscdaemon.client.*;
import org.rscdaemon.client.*;
import org.rscdaemon.client.*;
import org.rscdaemon.client.*;

/**
 * CakeThiever - Ardougne baker's stall, worked the way a person would work it.
 *
 * The stall is the best low Thieving in the game and the market is crawling
 * with guards, so the whole craft of it is standing where nobody is looking.
 * Since the server learned about line of sight that is a real question with a
 * real answer, and this script asks it before every single steal rather than
 * grabbing blind and eating the consequences.
 *
 * Two ways to run it. Banking mode fills up and walks the cakes to the south
 * Ardougne bank. Power mode drops them where it stands, which is faster xp and
 * the reason the mode exists -- it is not "let the cakes fall on the floor by
 * themselves", the server refuses a steal you have no room for, so the drop is
 * this script's job and it does it before the bag is full rather than after.
 *
 * There is no logout in here anywhere. The bot shifts a tile now and then
 * instead, because the server's idle timer measures walking rather than doing
 * and a thief who never steps is a thief who gets sent to the login screen
 * twenty minutes in.
 */
public class CakeThiever extends Script {

   /* ------------------------------------------------------------- place -- */

   /** The baker's stall, and what it becomes for five seconds after a theft. */
   private static final int STALL = 322, EMPTY_STALL = 341;

   /**
    * Every market stall, ours and the neighbours', full or picked clean.
    *
    * All of them are solid scenery and all of them block a look, so all of
    * them are cover. The silk and fur stalls are as good to hide behind as
    * the one being robbed.
    */
   private static final int[] SOLID = {
      322, 323, 324, 325, 326, 327, 341, 973, 999, 1183
   };

   /** The tile to work from: north-west of the stall, with the stall itself
       between us and most of the market. */
   private static final int HOME_X = 543, HOME_Y = 600;

   /** South Ardougne bank, and the man behind the desk. */
   private static final int BANKER = 95;
   private static final int[] TO_BANK = { 547, 608, 551, 612 };

   /* ------------------------------------------------------------- goods -- */

   /** A whole cake, then what is left of one after two bites. */
   private static final int CAKE = 330, PARTIAL = 333, SLICE = 335;

   /* ---------------------------------------------------------- watchers -- */

   /**
    * Everyone in the market who will do something about a theft.
    *
    * The baker does not fight -- he shouts, and the shout fetches somebody who
    * does -- but he is listed with the rest because being seen by him costs
    * exactly as much as being seen by a hero.
    */
   private static final int[] WATCHERS = { 321, 322, 323, 324, 325, 65, 100 };

   /**
    * How far a watcher can see. Matches the server exactly: seven tiles
    * straight down a row or a column, three off to the side.
    *
    * The asymmetry is the whole reason this script beats standing still and
    * hoping. Directly in front of a guard is dangerous from a long way off;
    * one step off his line is safe from very close.
    */
   private static final int SEE_RANGE = 3, SEE_RANGE_DIRECT = 7;

   /* ----------------------------------------------------------- options -- */

   /** Drop the cakes where we stand instead of walking them to the bank. */
   private boolean power;

   /** Hits to start eating at. */
   private int eatAt = 20;

   /** How many items in the pack before we stop stealing and clear it. */
   private int bagLimit = 28;


   /* ------------------------------------------------------------- state -- */

   /** How many turns to wait out a watcher before moving instead. */
   private long moveTime;
   private int stolen;
   private int passes;
   private int burned;
   private int fumbles;
   private String lastTrace = "";
   private int lastCulprit = -2;
   private int stallX = -1, stallY = -1;

   public CakeThiever(Extension ex) {
      super(ex);
   }

   /**
    * Ask the four questions the old text script asked.
    *
    * Interviewing beats arguments for this one. These get recorded and replayed
    * when the client logs itself back in, so answering once holds for the whole
    * session -- and a script that reads its settings off the command line loses
    * them the moment it has to restart.
    *
    * Passing "power" as an argument still skips straight to power mode, for
    * starting it without being at the keyboard.
    */
   @Override
   public void init(String params) {
      setAutoLogin(true);

      if (params != null && params.toLowerCase().indexOf("power") != -1) {
         power = true;
      } else {
         power = GetOption("Power thieve? (drop the cakes instead of banking)",
            new String[] { "No - bank them", "Yes - drop them" }) == 1;
      }

      int mode = GetOption("What fight mode will you be using?", FIGHTMODES);

      if (mode >= 0) {
         setFightMode(mode);
      }

      eatAt = number("What hits do you want to eat at?", eatAt, 1, 99);
      if (!power) {
         bagLimit = number("Bank when the pack reaches how many items?",
            bagLimit, 2, MAX_INV_SIZE);
      }

      stampMove();
      System.out.println("[CakeThiever] " + (power ? "power" : "banking")
         + " mode, eat at " + eatAt
         + (power ? "" : ", bank at " + bagLimit + " items")
         + ". Stealing only when nobody is looking.");
   }

   /** One numeric answer, with the default kept if it is left blank or spoiled. */
   private int number(String question, int fallback, int low, int high) {
      String answer = GetInput(question);

      if (answer == null) {
         return fallback;
      }

      try {
         int n = Integer.parseInt(answer.trim());
         return n < low ? low : (n > high ? high : n);
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   /* ================================================================ run == */

   @Override
   public int main() {
      if (!isLoggedIn()) {
         return 2000;
      }

      if (isQuestMenu()) {
         answer(0);
         return random(1200, 1800);
      }

      if (inCombat()) {
         /* Nothing announces a guard; he just swings. Being in a fight at the
            stall is therefore the loudest sighting there is. */
         burned++;
         System.out.println("[CakeThiever] Spotted at " + getX() + "," + getY()
            + " - attacked (" + burned + " in a row).");
         return flee();
      }

      if (getCurrentLevel(3) <= eatAt) {
         return eat();
      }

      if (isBanking()) {
         return bank();
      }

      /* The idle timer counts steps, not thefts. One shuffle every few minutes
         is cheaper than being logged out and walked back. */
      if (System.currentTimeMillis() >= moveTime) {
         stampMove();
         walkTo(getX() + random(-1, 1), getY() + random(-1, 1));
         return random(1200, 1800);
      }

      if (full()) {
         trace("pack full");
         /* Power mode does not drop and does not bank -- it just keeps
            stealing. A full pack means the cakes land at your feet on their
            own, which is how the stalls have always been trained and why not
            one period power-thieving script has a drop in it. The pack ends up
            holding a load of cake, which is exactly what you want to be
            standing in a market full of guards holding. */
         if (!power) {
            return goBank();
         }
      }

      if (distanceTo(HOME_X, HOME_Y) > 4) {
         trace("walking home: " + getX() + "," + getY() + " is "
            + distanceTo(HOME_X, HOME_Y) + " from " + HOME_X + "," + HOME_Y);
         walkTo(HOME_X, HOME_Y);
         return random(1200, 1800);
      }

      /* Caught twice from the same tile is the server telling us this spot is
         no good. That is evidence, unlike our own guessing, so it is the only
         thing allowed to move us. */
      if (burned >= 2) {
         burned = 0;
         return retreat();
      }

      return steal();
   }

   /* ============================================================== steal == */

   /**
    * Take one cake, if and only if the coast is clear.
    *
    * Waiting is the normal state of this script, not a failure of it. A guard
    * on his round is in view for a few seconds at a time and the stall
    * restocks in five, so standing still until he turns the corner costs
    * almost nothing and getting hit by a hero costs the trip home.
    */
   private int steal() {
      int[] stall = getObjectById(STALL);

      if (stall[0] == -1) {
         trace("no stall in view (empty="
            + (getObjectById(EMPTY_STALL)[0] != -1) + ")");
         /* Either it is empty and coming back, or we have drifted out of view
            of it. Both are cured by standing where it will be. */
         if (getObjectById(EMPTY_STALL)[0] != -1) {
            return random(600, 900);
         }
         walkTo(HOME_X, HOME_Y);
         return random(1200, 1800);
      }

      stallX = stall[1];
      stallY = stall[2];
      trace("stall in view at " + stall[1] + "," + stall[2]
         + ", me at " + getX() + "," + getY());

      int watcher = watching();

      if (watcher == -1) {
         lastCulprit = -2;
      }

      if (watcher != -1) {
         passes++;

         /* There is deliberately no "waited long enough, steal anyway" escape
            here. Impatience is the one thing that must never win an argument
            with a guard standing in plain view: if the check is right we hand
            the thief to a hero, and if the check is wrong we have lost nothing
            but a few seconds. A stall that stays watched is a stall we do not
            rob today. */

         /* Say who and why the moment we start waiting, and again whenever
            the culprit changes. A bot that just counts is useless to debug;
            this check has no wall data, so when it disagrees with what a
            player can plainly see, the npc's id and position are the only
            things that show which way it is wrong. */
         if (watcher != lastCulprit) {
            lastCulprit = watcher;
            System.out.println("[CakeThiever] Holding: " + getNpcName(watcher)
               + " (id " + NpcId(watcher) + ") at " + NpcX(watcher) + ","
               + NpcY(watcher) + " vs me at " + getX() + "," + getY()
               + " - dx " + Math.abs(NpcX(watcher) - getX())
               + " dy " + Math.abs(NpcY(watcher) - getY()) + ".");
         }

         return random(500, 800);
      }

      return take();
   }

   /**
    * Rob the stall.
    *
    * "steal from" is the object's SECOND command -- the first is WalkTo, which
    * is exactly as useless as it sounds. Getting this wrong makes a bot that
    * strolls up to the stall forever and never robs it.
    */
   private int take() {
      int[] stall = getObjectById(STALL);

      if (stall[0] == -1) {
         return random(600, 900);
      }

      trace("stealing from stall at " + stall[1] + "," + stall[2]
         + " (me " + getX() + "," + getY() + ")");
      trace("stealing (" + stolen + " taken, " + fumbles + " fumbled)");
      atObject2(stall[1], stall[2]);
      return random(900, 1400);
   }

   /**
    * The index of somebody who can see us, or -1 if nobody can.
    *
    * This is the client's half of the server's line-of-sight test. It cannot
    * read walls -- no script can -- so it knows about exactly one obstruction,
    * the stall, and is deliberately pessimistic about everything else: a
    * watcher within three tiles counts as watching unless the stall is
    * provably in the way. Being wrong in that direction costs a pause. Being
    * wrong the other way costs a beating.
    */
   private int watching() {
      int count = CountNpcs();

      for (int i = 0; i < count; i++) {
         if (!isWatcher(NpcId(i))) {
            continue;
         }

         int wx = NpcX(i);
         int wy = NpcY(i);

         /*
          * Deliberately NOT the server's catch rule.
          *
          * The server lets a guard notice you from seven tiles straight down a
          * row, but it only asks that question after a theft has already gone
          * wrong. Holding a steal on it is a different and much worse bet: in
          * an open market somebody is nearly always sharing your row or column
          * with nothing in between, so the seven-tile reach is true almost
          * continuously and the bot stands at the stall doing nothing forever.
          *
          * What we want to know here is narrower -- is anyone looking right at
          * me -- so this stays at close range and leans on the stall for cover.
          * Being wrong costs one theft; the server still decides the outcome.
          */
         if (Math.max(Math.abs(wx - getX()), Math.abs(wy - getY())) > SEE_RANGE) {
            continue;
         }

         if (!sees(wx, wy)) {
            continue;
         }

         return i;
      }

      return -1;
   }

   /**
    * Step somewhere better after actually being caught.
    *
    * The hard rule here, learned the expensive way: a candidate tile is only
    * acceptable if it is no closer to EVERY watcher than where we already
    * stand. Scoring tiles on how well the stall shields them is not enough,
    * because the shield is a guess built from the one obstruction a script can
    * see, and a guess that is allowed to walk will eventually walk into a
    * hero. Distance is not a guess. If nothing is strictly safer, stand still
    * -- staying put next to a stall beats striding across a market.
    */
   private int retreat() {
      if (stallX == -1) {
         return random(600, 900);
      }

      int here = nearestWatcher(getX(), getY());
      int bestX = -1, bestY = -1, bestScore = here;

      for (int dx = -2; dx <= 2; dx++) {
         for (int dy = -2; dy <= 2; dy++) {
            int tx = stallX + dx;
            int ty = stallY + dy;

            if ((tx == getX() && ty == getY()) || solid(tx, ty)) {
               continue;
            }

            if (Math.max(Math.abs(tx - stallX), Math.abs(ty - stallY)) > 1
               && distanceTo(tx, ty) > 3) {
               /* Stay within reach of the stall; a safer tile we cannot rob
                  from is not a safer tile, it is a different script. */
               continue;
            }

            int score = nearestWatcher(tx, ty);

            /* Strictly further from the closest watcher, and not into anyone's
               line as far as we can tell. Both, or we do not move. */
            if (score > bestScore && !seenFrom(tx, ty)) {
               bestScore = score;
               bestX = tx;
               bestY = ty;
            }
         }
      }

      if (bestX == -1) {
         System.out.println("[CakeThiever] Caught, but nowhere safer to stand"
            + " - holding position.");
         return random(1500, 2500);
      }

      System.out.println("[CakeThiever] Caught at " + getX() + "," + getY()
         + " - backing off to " + bestX + "," + bestY
         + " (nearest watcher " + here + " -> " + bestScore + ").");
      walkTo(bestX, bestY);
      return random(1500, 2200);
   }

   /** Chebyshev distance to the closest watcher, or a big number if none. */
   private int nearestWatcher(int tx, int ty) {
      int count = CountNpcs();
      int best = 99;

      for (int i = 0; i < count; i++) {
         if (!isWatcher(NpcId(i))) {
            continue;
         }

         int d = Math.max(Math.abs(NpcX(i) - tx), Math.abs(NpcY(i) - ty));

         if (d < best) {
            best = d;
         }
      }

      return best;
   }

   /** Whether any watcher could see a thief standing on that tile. */
   private boolean seenFrom(int tx, int ty) {
      int count = CountNpcs();

      for (int i = 0; i < count; i++) {
         if (!isWatcher(NpcId(i))) {
            continue;
         }

         int wx = NpcX(i);
         int wy = NpcY(i);
         int dx = Math.abs(wx - tx);
         int dy = Math.abs(wy - ty);
         int reach = (dx == 0 || dy == 0) ? SEE_RANGE_DIRECT : SEE_RANGE;

         if (Math.max(dx, dy) <= reach && sees(wx, wy, tx, ty)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Say what the script just decided, but only when the decision changes.
    *
    * A bot that logs every pass drowns the one line that matters; a bot that
    * logs nothing cannot be told apart from a bot that is wedged. Printing on
    * change gives a readable running commentary of the state machine.
    */
   private void trace(String what) {
      if (!what.equals(lastTrace)) {
         lastTrace = what;
         System.out.println("[CakeThiever] " + what);
      }
   }

   private boolean isWatcher(int id) {
      for (int i = 0; i < WATCHERS.length; i++) {
         if (WATCHERS[i] == id) {
            return true;
         }
      }
      return false;
   }

   /**
    * Walk the line from a watcher to us and see whether a stall is in the way.
    *
    * This is the server's own test, reproduced with the one obstruction a
    * script can actually see. It steps a tile at a time toward us, and where
    * the line runs true diagonal it stops dead if either corner is filled --
    * because a stall is a roofed box standing in the seam, not a post you can
    * look past. Get this wrong in the cautious direction and the bot stands
    * at the stall waiting for a baker who could never see it in the first
    * place, which is a lot worse than it sounds: the baker never moves.
    */
   private boolean sees(int wx, int wy) {
      return sees(wx, wy, getX(), getY());
   }

   private boolean sees(int wx, int wy, int px, int py) {
      int x = wx;
      int y = wy;
      int steps = 0;

      while ((x != px || y != py) && steps++ < 32) {
         int dx = Integer.signum(px - x);
         int dy = Integer.signum(py - y);

         if (dx != 0 && dy != 0 && (solid(x + dx, y) || solid(x, y + dy))) {
            return false;
         }

         x += dx;
         y += dy;

         if ((x != px || y != py) && solid(x, y)) {
            return false;
         }
      }

      return true;
   }

   /** Whether a tile is filled by a stall. */
   private boolean solid(int x, int y) {
      int count = getObjectCount();

      for (int i = 0; i < count; i++) {
         if (getObjectX(i) != x || getObjectY(i) != y) {
            continue;
         }

         int id = getObjectId(i);

         for (int j = 0; j < SOLID.length; j++) {
            if (SOLID[j] == id) {
               return true;
            }
         }
      }

      return false;
   }

   /* =============================================================== bag === */

   private int cakes() {
      return getInventoryCount(CAKE, PARTIAL, SLICE);
   }

   private boolean full() {
      return getInventoryCount() >= bagLimit;
   }

   /* ============================================================== bank === */

   private int goBank() {
      for (int i = 0; i < TO_BANK.length; i += 2) {
         if (distanceTo(TO_BANK[i], TO_BANK[i + 1]) > 3) {
            walkTo(TO_BANK[i], TO_BANK[i + 1]);
            return random(1200, 1800);
         }
      }

      int[] banker = getNpcByIdNotTalk(BANKER);

      if (banker[0] != -1
         && distanceTo(getNpcX(banker[0]), getNpcY(banker[0])) <= 4) {
         talkToNpc(banker[0]);
         return random(3000, 4000);
      }

      walkTo(TO_BANK[TO_BANK.length - 2], TO_BANK[TO_BANK.length - 1]);
      return random(1200, 1800);
   }

   private int bank() {
      if (cakes() > 0) {
         deposit(CAKE, 1000);
         deposit(PARTIAL, 1000);
         deposit(SLICE, 1000);
         return random(900, 1400);
      }

      closeBank();
      System.out.println("[CakeThiever] Banked. " + stolen + " stolen, "
         + passes + " waits, thieving " + getLevel(17) + ".");
      return random(1200, 1800);
   }

   /* ============================================================ trouble == */

   /**
    * Somebody caught us. Walk off, do not fight.
    *
    * A guard will out-hit a thief who is here for the cakes, and the stall is
    * still there in a minute. Retreat is always the right answer.
    */
   private int flee() {
      walkTo(HOME_X - 3, HOME_Y + 3);
      return random(2000, 3000);
   }

   private int eat() {
      int slot = getInventoryIndex(CAKE, PARTIAL, SLICE);

      if (slot < 0) {
         /* Nothing to eat and taking damage. Get clear of the market rather
            than stand in it hoping. */
         return flee();
      }

      useItem(slot);
      return random(900, 1400);
   }

   private void stampMove() {
      moveTime = System.currentTimeMillis() + random(240000, 420000);
   }

   /**
    * The server is the oracle, not our guess about it.
    *
    * A failed steal is the one unambiguous statement that somebody really
    * could see us -- no approximation, no missing wall data. That, and only
    * that, is worth moving over.
    */
   @Override
   public void onServerMessage(String message) {
      String said = message.toLowerCase();

      if (said.indexOf("you steal") != -1) {
         stolen++;
         return;
      }

      /*
       * A fumble is NOT evidence of anything.
       *
       * The server rolls for the grab and asks who is watching as two separate
       * questions, so "you fail to steal" means the hand slipped and nothing
       * more -- it happens in an empty market as readily as a guarded one.
       * Counting fumbles as sightings, which this used to do, makes the bot
       * abandon a perfectly good tile every couple of thefts.
       */
      if (said.indexOf("fail to steal") != -1) {
         fumbles++;
      }
   }

   /**
    * Being seen has exactly one tell: somebody starts a fight.
    *
    * There is no message for it, because the guard does not announce himself
    * -- he just walks over and swings. So the sighting is inferred where it
    * actually shows, in main(), and the stallholder's shout is caught here
    * because that one IS spoken aloud.
    */
   @Override
   public void onChatMessage(String message, String sender, boolean mod, boolean admin) {
      if (message != null && message.toLowerCase().indexOf("guards") != -1) {
         burned++;
         System.out.println("[CakeThiever] Spotted at " + getX() + "," + getY()
            + " - " + sender + " shouted (" + burned + " in a row).");
      }
   }

   @Override
   /**
    * Put the state machine on the screen, not just down stdout.
    *
    * A client launched from a desktop has nowhere to print -- its stdout is a
    * socket nobody reads -- so a bot that only logs is a bot nobody can debug.
    * The last decision and the current culprit go on the overlay, where the
    * one person actually watching the market can see them.
    */
   public void paint() {
      int y = 21;
      drawString("CakeThiever - " + (power ? "power" : "banking"), 6, y, 1, 0xFFFFFF);
      y += 14;
      drawString("Stolen: " + stolen + "   fumbled: " + fumbles
         + "   held: " + passes, 6, y, 1, 0xFFFFFF);
      y += 14;
      drawString("Thieving: " + getLevel(17) + "   spotted: " + burned, 6, y, 1, 0xFFFFFF);
      y += 14;
      drawString(lastTrace.length() == 0 ? "starting up" : lastTrace,
         6, y, 1, 0xFFFF00);

      if (lastCulprit >= 0 && lastCulprit < CountNpcs()) {
         y += 14;
         drawString("held by " + getNpcName(lastCulprit)
            + " at " + NpcX(lastCulprit) + "," + NpcY(lastCulprit)
            + " (me " + getX() + "," + getY() + ")", 6, y, 1, 0xFF6060);
      }
   }
}
