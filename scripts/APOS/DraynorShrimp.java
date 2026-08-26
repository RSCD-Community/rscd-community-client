/**
 * Net fishing on the Draynor riverbank, banking in Draynor.
 *
 * The Al Kharid spots are two steps closer to their bank and were tried
 * first, but the desert between them is scorpion country: npc 70 spawns at
 * (78,715), it is aggressive, and its combat level of 21 means it hunts
 * anyone at combat 42 or below. Draynor's only aggressive neighbours are the
 * two dark wizards east of the bank, level 14, who pacify above combat 28 --
 * so this walk is safe from the first level a fisher is likely to have.
 *
 * The layout, read out of the server's landscape rather than guessed at:
 *
 *   Spots      object 193 "fish", Net -> shrimp (level 1) and anchovies
 *              (level 15). Three of them: (221,664) fished from (221,663),
 *              (224,661) from (223,661), (224,659) from (223,659).
 *   Bank       bankers 95 at (218,635), (220,637) and (222,635). (220,637)
 *              is reached from (221,637), which is where the walk ends.
 *   Between    north up the x221 riverside track, east around the wizards'
 *              huts at y640, then north past the bank's east wall and in
 *              from the top. 41 steps.
 *
 * The dogleg at (224,633) is not decoration. The bank building is sealed on
 * the side facing the river, so walking straight at the bankers puts you
 * against a wall; the way in is round the north end.
 *
 * Needs a small net. These spots take no bait, so there is nothing to run
 * out of but inventory space.
 */
public class DraynorShrimp extends Script {

    private static final int SMALL_NET = 376;
    private static final int SLEEPING_BAG = 1263;
    private static final int NET_SPOT = 193;
    private static final int SKILL_FISHING = 10;

    /*
     * Draynor and Falador use npc 95; Al Kharid uses 268. Both are named
     * "Banker" and look identical in game, which is exactly why this is
     * worth spelling out -- asking for the wrong one finds no banker at
     * all, silently, and the script just stands in the room. Listing both
     * costs nothing and makes the script work at either bank.
     */
    private static final int[] BANKER = { 95, 268 };

    /** Raw shrimp and raw anchovies -- everything these spots produce. */
    private static final int[] KEEP = { 349, 351 };

    /* The three spots, and the bank tile each is fished from. */
    private static final int[][] SPOTS = {
        { 221, 664, 221, 663 },
        { 224, 661, 223, 661 },
        { 224, 659, 223, 659 },
    };

    /* Waypoints, river end first. The last one is the counter tile. */
    private static final int[][] WAY = {
        { 221, 662 }, { 221, 655 }, { 221, 648 }, { 221, 641 },
        { 224, 640 }, { 224, 636 }, { 224, 633 }, { 221, 634 }, { 221, 637 }
    };

    /* Flip off once a clean unattended stretch is on record. */
    private static final boolean DEBUG = true;

    private long start_time = -1L;
    private int caught;
    private long last_report;

    public DraynorShrimp(Extension ex) {
        super(ex);
    }

    public void init(String params) {
        start_time = -1L;
        caught = 0;
    }

    public int main() {
        if (start_time == -1L) {
            start_time = System.currentTimeMillis();
        }
        if (isSleeping()) {
            return random(1000, 2000);
        }
        if (getFatigue() > 90 && getInventoryIndex(SLEEPING_BAG) != -1) {
            useSleepingBag();
            return random(2000, 3000);
        }
        if (getInventoryIndex(SMALL_NET) == -1) {
            System.out.println("[DraynorShrimp] No small net. Stopping.");
            stopScript();
            return 1000;
        }
        /* Whatever opened it, empty it and move on. */
        if (isBanking()) {
            for (int i = 0; i < KEEP.length; ++i) {
                int count = getInventoryCount(KEEP[i]);
                if (count > 0) {
                    deposit(KEEP[i], count);
                    return random(600, 1000);
                }
            }
            closeBank();
            return random(800, 1200);
        }
        if (isQuestMenu()) {
            answer(0);
            return random(1500, 2500);
        }
        boolean out = getEmptySlots() > 0;
        report(out ? "toRiver" : "toBank");
        return out ? toRiver() : toBank();
    }

    // ---------------------------------------------------------- outbound --

    /** Room in the pack: get to a spot and keep swinging the net. */
    private int toRiver() {
        int[] spot = getObjectById(NET_SPOT);
        if (spot[0] != -1 && distanceTo(spot[1], spot[2]) <= 4) {
            atObject(spot[1], spot[2]);
            return random(1200, 1800);
        }
        int[] target = nearestSpot();
        if (distanceTo(target[2], target[3]) <= 6) {
            if (!isWalking()) {
                walkTo(target[2], target[3]);
            }
            return random(1000, 1600);
        }
        return follow(0);
    }

    // ----------------------------------------------------------- inbound --

    /** Pack is full: reach a banker, from wherever this happens to be. */
    private int toBank() {
        /* Proximity beats geography. A banker in talking range means there
           is nothing left to walk, whatever the coordinates say. */
        int[] banker = getNpcByIdNotTalk(BANKER);
        if (banker[0] != -1 && distanceTo(banker[1], banker[2]) <= 4) {
            talkToNpc(banker[0]);
            return random(2500, 3500);
        }
        if (banker[0] != -1) {
            if (!isWalking()) {
                walkTo(banker[1] + 1, banker[2]);
            }
            return random(1000, 1600);
        }
        return follow(WAY.length - 1);
    }

    // ---------------------------------------------------------- reporting --

    /** Throttled state dump. Reading the code guesses; this tells. */
    private void report(String where) {
        if (!DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - last_report < 4000L) {
            return;
        }
        last_report = now;
        int[] spot = getObjectById(NET_SPOT);
        int[] banker = getNpcByIdNotTalk(BANKER);
        System.out.println("[DraynorShrimp] (" + getX() + "," + getY() + ") "
                + where + " empty=" + getEmptySlots()
                + " walking=" + isWalking()
                + " banking=" + isBanking()
                + " spot=" + (spot[0] == -1 ? "none" : spot[1] + "," + spot[2])
                + " banker=" + (banker[0] == -1 ? "none" : banker[1] + "," + banker[2])
                + " -> way" + nearestWaypoint());
    }

    // ----------------------------------------------------------- walking --

    /** Whichever of the spots is closer right now. */
    private int[] nearestSpot() {
        int[] best = SPOTS[0];
        int d = distanceTo(SPOTS[0][0], SPOTS[0][1]);
        for (int i = 1; i < SPOTS.length; ++i) {
            int t = distanceTo(SPOTS[i][0], SPOTS[i][1]);
            if (t < d) {
                d = t;
                best = SPOTS[i];
            }
        }
        return best;
    }

    private int nearestWaypoint() {
        int nearest = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < WAY.length; ++i) {
            int d = distanceTo(WAY[i][0], WAY[i][1]);
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        return nearest;
    }

    /**
     * Walk towards WAY[goal], one waypoint at a time.
     *
     * Progress is measured against the world, not remembered: whichever
     * waypoint we are nearest to is where we are, so being teleported,
     * logged back in, or simply started half way along all behave the same.
     */
    private int follow(int goal) {
        int nearest = nearestWaypoint();
        int best = distanceTo(WAY[nearest][0], WAY[nearest][1]);
        int next = nearest;
        if (best <= 2) {
            next = nearest < goal ? nearest + 1
                 : nearest > goal ? nearest - 1 : goal;
        }
        /* Walking to where we already stand is a click the client throws
           away, and a caller that keeps asking for it never moves again.
           Say so instead of spinning. */
        if (next == nearest && best == 0) {
            return random(600, 1000);
        }
        if (!isWalking()) {
            walkTo(WAY[next][0], WAY[next][1]);
        }
        return random(1200, 2000);
    }

    public void onServerMessage(String str) {
        if (str.indexOf("You catch a") != -1) {
            ++caught;
            if (caught % 50 == 0) {
                System.out.println("[DraynorShrimp] " + caught + " fish, "
                        + getLevel(SKILL_FISHING) + " fishing, "
                        + ((System.currentTimeMillis() - start_time) / 60000L) + " mins.");
            }
        }
    }
}
