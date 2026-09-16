/**
 * Tree Gnome Stronghold bowstring maker: pick flax, spin it, bank the
 * strings, repeat.
 *
 * The stronghold is the tightest place in the world to do this because all
 * three things sit in one clearing -- the flax field is on the ground, and
 * both the spinning wheels and the bank are up trees a short walk apart.
 * Route read out of the server's own object data:
 *
 *   Flax field     object 313, ~90 plants worldwide and the densest patch
 *                  is here: x710-719, y490-517, right under the bank tree.
 *                  Its "pick" is command TWO -- command one is WalkTo, so
 *                  a plain atObject() walks up to the plant and stands
 *                  there admiring it. atObject2() is what picks.
 *   Spinning wheel three of them, each up its own ladder on a tree
 *                  platform. This uses the eastern one: ladder (691,515)
 *                  up to the platform, wheel at (693,1459).
 *   Gnome bank     ladder (714,500) up to the platform at (714,1444),
 *                  then south over the log bridge at (714,1448) to the
 *                  bankers (npc 540) at (713,1453) and (715,1451).
 *
 * Upstairs is y + 944, so the two platforms are told apart by x alone:
 * under 700 is the wheel, over it is the bank. Log bridges and platforms
 * are ordinary walkable scenery -- nothing to click, you just walk across.
 *
 * Like GuildMiner, this asks the world where it is every tick rather than
 * remembering a step number, so it picks up from any point: standing in
 * the flax, half way up a tree, at the wheel holding twenty flax, or in
 * the bank with a full load of strings.
 *
 * Spinning flax needs Crafting 10. Below that the server refuses every
 * attempt, so the script says so once and stops rather than clicking a
 * wheel forever.
 */
public class GnomeFlax extends Script {

    private static final int FLAX = 675, BOWSTRING = 676;
    private static final int FLAX_PLANT = 313, WHEEL = 121;
    private static final int LADDER_UP = 5, LADDER_DOWN = 6;
    private static final int BANKER = 540;
    private static final int SLEEPING_BAG = 1263;

    /* Ground level. */
    private static final int FLAX_X = 714, FLAX_Y = 505;
    private static final int WHEEL_LADDER_X = 691, WHEEL_LADDER_Y = 515;
    private static final int BANK_LADDER_X = 714, BANK_LADDER_Y = 500;

    /* Up the trees (ground + 944). */
    private static final int WHEEL_X = 693, WHEEL_Y = 1459;
    private static final int WHEEL_DOWN_X = 691, WHEEL_DOWN_Y = 1459;
    private static final int BANK_DOWN_X = 714, BANK_DOWN_Y = 1444;
    /*
     * (714,1452) was here and is a walled tile -- tools/route.py finds no
     * route to it from anywhere on the platform. It never showed because the
     * bankers start inside talking range and this is only the fallback, but
     * a walk to an unreachable square is a walk that never happens. The
     * bankers roam y1448-1458, so the fallback aims at the middle of their
     * strip on the walkable column.
     */
    private static final int BANK_X = 715, BANK_Y = 1452;

    /** Anything east of this line upstairs is the bank tree, not the wheel. */
    private static final int PLATFORM_SPLIT = 700;

    private static final int SKILL_CRAFTING = 12;

    /* Set false once this thing is trusted. While it is on, every tick
       prints what the script can see and what it decided -- which is the
       only way to tell "the branch was never reached" apart from "the
       branch ran and the click did nothing". */
    private static final boolean DEBUG = false;

    private long start_time = -1L;
    private long last_report;
    private int spun;
    private boolean too_low;
    /* Deposit-stall watchdog -- see the isBanking() branch in main(). */
    private int bank_last = -1;
    private int bank_stalls;

    public GnomeFlax(Extension ex) {
        super(ex);
    }

    public void init(String params) {
        start_time = -1L;
        last_report = 0L;
        spun = 0;
        too_low = false;
        bank_last = -1;
        bank_stalls = 0;
    }

    public int main() {
        if (start_time == -1L) {
            start_time = System.currentTimeMillis();
        }
        if (too_low) {
            return 1000;
        }
        if (isSleeping()) {
            return random(1000, 2000);
        }
        if (getFatigue() > 95) {
            if (getInventoryIndex(SLEEPING_BAG) == -1) {
                System.out.println("[GnomeFlax] Fatigued with no sleeping bag. Stopping.");
                stopScript();
                return 1000;
            }
            useSleepingBag();
            return random(2000, 3000);
        }
        /*
         * However the bank came to be open, empty it and get on.
         *
         * A BANK WINDOW THE SERVER HAS ALREADY CLOSED IS THE ONE THING THAT
         * CAN WEDGE THIS SCRIPT FOR GOOD. isBanking() reads a client flag,
         * and the flag survives a dropped connection -- a server restart
         * while the bank is open leaves the window painted with no session
         * behind it. Every deposit is then thrown away silently: the pack
         * does not change, this branch runs again, and nothing below it is
         * ever reached. Stopping and restarting the script does not help,
         * because the stale flag lives in the client, not in here.
         *
         * So the deposits have to be watched. Five ticks with the count
         * unmoved is not a slow bank, it is a dead one -- closeBank()
         * clears the flag locally whatever the server thinks, and the walk
         * back to the banker starts a real session.
         */
        if (isBanking()) {
            int strings = getInventoryCount(BOWSTRING);
            if (strings > 0) {
                if (strings != bank_last) {
                    bank_last = strings;
                    bank_stalls = 0;
                } else if (++bank_stalls > 5) {
                    log("Bank open but deposits are doing nothing - stale "
                            + "window, closing it and starting the trip over.");
                    bank_stalls = 0;
                    bank_last = -1;
                    closeBank();
                    return random(1200, 1800);
                }
                deposit(BOWSTRING, strings);
                return random(600, 1000);
            }
            bank_stalls = 0;
            bank_last = -1;
            closeBank();
            return random(800, 1200);
        }
        if (isQuestMenu()) {
            answer(0);
            return random(1500, 2500);
        }

        int flax = getInventoryCount(FLAX);
        int strings = getInventoryCount(BOWSTRING);
        report(flax, strings);
        /* Gather until there is nowhere to put the next plant, spin what
           was gathered, then bank it. Reading the pack rather than keeping
           a phase counter is what lets this resume mid-trip. */
        if (getEmptySlots() > 0 && strings == 0) {
            return pick();
        }
        if (flax > 0) {
            return toWheel();
        }
        return toBank();
    }

    /** Throttled state dump: where, holding what, and which way it will go. */
    private void report(int flax, int strings) {
        if (!DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < last_report + 4000L) {
            return;
        }
        last_report = now;
        String going = (getEmptySlots() > 0 && strings == 0) ? "PICK"
                     : flax > 0 ? "WHEEL" : "BANK";
        log("(" + getX() + "," + getY() + ")"
                + " upstairs=" + upstairs()
                + " flax=" + flax + " strings=" + strings
                + " empty=" + getEmptySlots()
                + " fatigue=" + getFatigue()
                + " walking=" + isWalking()
                + " questmenu=" + isQuestMenu()
                + " banking=" + isBanking()
                + " -> " + going);
    }

    /**
     * Both to the console and to script-bin/GnomeFlax.log.
     *
     * The console is not always reachable -- a client launched from an IDE
     * or behind a pipe sends stdout somewhere nobody can read afterwards --
     * and a bug you cannot see the state of is a bug you fix by guessing.
     */
    private void log(String line) {
        System.out.println("[GnomeFlax] " + line);
        java.io.PrintWriter out = null;
        try {
            out = new java.io.PrintWriter(new java.io.FileWriter(
                    new java.io.File("script-bin", "GnomeFlax.log"), true));
            out.println(line);
        } catch (Exception e) {
            /* logging must never be the thing that stops the script */
        } finally {
            if (out != null) out.close();
        }
    }

    // ----------------------------------------------------------- picking --

    private int pick() {
        if (upstairs()) {
            return descend();
        }
        int[] plant = getObjectById(FLAX_PLANT);
        if (plant[0] != -1) {
            atObject2(plant[1], plant[2]);   /* "pick" is command two */
            return random(900, 1400);
        }
        /* Field picked bare for the moment -- stand in it and wait for a
           respawn rather than wandering off and losing the patch. */
        if (!isWalking() && distanceTo(FLAX_X, FLAX_Y) > 3) {
            walkTo(FLAX_X, FLAX_Y);
        }
        return random(1200, 2000);
    }

    // --------------------------------------------------------- spinning --

    private int toWheel() {
        if (upstairs()) {
            if (getX() < PLATFORM_SPLIT) {
                useItemOnObject(FLAX, WHEEL_X, WHEEL_Y);
                return random(900, 1400);
            }
            return descend();          /* up the wrong tree */
        }
        return climb(WHEEL_LADDER_X, WHEEL_LADDER_Y);
    }

    // ---------------------------------------------------------- banking --

    private int toBank() {
        if (upstairs()) {
            if (getX() >= PLATFORM_SPLIT) {
                /* Proximity beats geography: if a banker is in talking
                   range there is nothing left to walk to. */
                /* Walk at the banker, not at a spot the banker is usually
                   near. They roam eleven tiles of platform; a fixed target
                   can be reached and still leave them out of range, and then
                   there is nothing left to walk towards. */
                int[] banker = getNpcByIdNotTalk(BANKER);
                if (banker[0] != -1) {
                    if (distanceTo(banker[1], banker[2]) <= 4) {
                        talkToNpc(banker[0]);
                        return random(2500, 3500);
                    }
                    return stepTowards(banker[1], banker[2]);
                }
                return stepTowards(BANK_X, BANK_Y);   /* south over the bridge */
            }
            return descend();          /* up the wrong tree */
        }
        return climb(BANK_LADDER_X, BANK_LADDER_Y);
    }

    // ---------------------------------------------------------- climbing --

    /** Down whichever tree we are actually up. */
    private int descend() {
        int x = getX() < PLATFORM_SPLIT ? WHEEL_DOWN_X : BANK_DOWN_X;
        int y = getX() < PLATFORM_SPLIT ? WHEEL_DOWN_Y : BANK_DOWN_Y;
        return climb(x, y);
    }

    /**
     * Walk at most three tiles towards somewhere, then ask again.
     *
     * The tree platforms are a narrow strip joined by ONE-TILE log bridges,
     * and asking the client to path the whole eight tiles from the ladder
     * to the bank in a single walkTo() gets no route at all -- the script
     * sits on the platform issuing a walk that never happens. Short hops
     * follow the strip because each one is close enough to be trivially
     * pathable, and re-deciding every few tiles is what lets the walk
     * recover if something nudges the player off the line.
     */
    private int stepTowards(int tx, int ty) {
        int dx = tx - getX(), dy = ty - getY();
        if (dx > 3) dx = 3; else if (dx < -3) dx = -3;
        if (dy > 3) dy = 3; else if (dy < -3) dy = -3;
        if (!isWalking()) {
            walkTo(getX() + dx, getY() + dy);
        }
        return random(900, 1500);
    }

    /**
     * Use a ladder, from wherever we happen to be standing.
     *
     * A LADDER OCCUPIES ITS OWN TILE AND YOU CANNOT STAND ON IT. Walking
     * "to" a ladder is therefore a walk to a blocked square: it fails, the
     * player does not move, and a script that waits to be within one tile
     * before clicking waits forever two tiles away -- which is exactly how
     * this one first died, parked at (714,502) beside the bank ladder at
     * (714,500) issuing the same impossible walk every tick.
     *
     * atObject() already walks the player into range and uses the object,
     * so the right move is to click it from a few tiles out and let the
     * client do the approach. The walkTo below is only for closing real
     * distance, and it deliberately aims BESIDE the ladder, never at it.
     */
    private int climb(int x, int y) {
        if (distanceTo(x, y) <= 4) {
            if (DEBUG) {
                log("climbing at (" + x + "," + y + ")");
            }
            atObject(x, y);
            return random(1800, 2600);
        }
        if (!isWalking()) {
            walkTo(x, y + 2);
        }
        return random(1200, 2000);
    }

    /** The platforms sit one level up, which is a flat +944 on y. */
    private boolean upstairs() {
        return getY() > 944 && getY() < 1888;
    }

    public void onServerMessage(String str) {
        if (str.indexOf("crafting level of 10") != -1) {
            System.out.println("[GnomeFlax] Spinning flax needs Crafting 10. Stopping.");
            too_low = true;
            stopScript();
            return;
        }
        if (str.indexOf("bow string") != -1) {
            ++spun;
            if (spun % 50 == 0) {
                System.out.println("[GnomeFlax] " + spun + " bow strings, "
                        + getLevel(SKILL_CRAFTING) + " crafting, "
                        + ((System.currentTimeMillis() - start_time) / 60000L) + " mins.");
            }
        }
    }
}
