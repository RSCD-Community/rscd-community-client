/**
 * Lobster fishing at Catherby, banking in Catherby.
 *
 * The only cage spot in the game within a sane walk of a bank: object 194 at
 * (409,504), fished from (409,503), 40 steps from the bankers on a route that
 * is almost entirely one straight run east along y497. The other three
 * reachable cage spots are all Ardougne's, at 90 steps and up. Nothing on the
 * walk is aggressive at any level.
 *
 * Why the cage and not the harpoon, which is the same object's other option:
 *
 *   catch chance is 30 + (fishing level - requirement), capped at 70, and the
 *   fish is drawn at random from everything you qualify for BEFORE that roll.
 *   The harpoon draws from tuna (35) and swordfish (50), so at 50 fishing it
 *   averages 33 experience a click. The cage only ever offers lobster (40),
 *   which at 50 is 36 a click and climbs from there. The harpoon only wins
 *   once swordfish stops missing, which is a long way up.
 *
 * Needs a lobster pot. No bait, so there is nothing to run out of but space.
 */
public class CatherbyLobster extends Script {

    private static final int LOBSTER_POT = 375;
    private static final int SLEEPING_BAG = 1263;
    private static final int CAGE_SPOT = 194;
    private static final int SKILL_FISHING = 10;

    /* Catherby's bankers are 95, the same id Draynor and Falador use. 268 is
       Al Kharid's; listing both costs nothing and travels well. */
    private static final int[] BANKER = { 95, 268 };

    /** Raw lobster. The cage produces nothing else. */
    private static final int[] KEEP = { 372 };

    private static final int SPOT_X = 409, SPOT_Y = 504;
    private static final int STAND_X = 409, STAND_Y = 503;

    /* Waypoints, shore end first. The last one is beside the banker. */
    private static final int[][] WAY = {
        { 409, 500 }, { 411, 498 }, { 418, 497 }, { 425, 497 },
        { 432, 497 }, { 437, 497 }, { 439, 495 }
    };

    /* Flip off once a clean unattended stretch is on record. */
    private static final boolean DEBUG = true;

    private long start_time = -1L;
    private int caught;
    private long last_report;

    public CatherbyLobster(Extension ex) {
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
        if (getInventoryIndex(LOBSTER_POT) == -1) {
            System.out.println("[CatherbyLobster] No lobster pot. Stopping.");
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
        report(out ? "toShore" : "toBank");
        return out ? toShore() : toBank();
    }

    // ---------------------------------------------------------- outbound --

    /** Room in the pack: get to the spot and keep lowering the cage. */
    private int toShore() {
        int[] spot = getObjectById(CAGE_SPOT);
        if (spot[0] != -1 && distanceTo(spot[1], spot[2]) <= 4) {
            /* Second option on the spot -- the first is the harpoon. */
            atObject2(spot[1], spot[2]);
            return random(1200, 1800);
        }
        if (distanceTo(STAND_X, STAND_Y) <= 6) {
            if (!isWalking()) {
                walkTo(STAND_X, STAND_Y);
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

    // --------------------------------------------------------- reporting --

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
        int[] spot = getObjectById(CAGE_SPOT);
        int[] banker = getNpcByIdNotTalk(BANKER);
        System.out.println("[CatherbyLobster] (" + getX() + "," + getY() + ") "
                + where + " empty=" + getEmptySlots()
                + " walking=" + isWalking()
                + " banking=" + isBanking()
                + " spot=" + (spot[0] == -1 ? "none" : spot[1] + "," + spot[2])
                + " banker=" + (banker[0] == -1 ? "none" : banker[1] + "," + banker[2])
                + " -> way" + nearestWaypoint());
    }

    // ----------------------------------------------------------- walking --

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
           away, and a caller that keeps asking for it never moves again. */
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
            if (caught % 25 == 0) {
                System.out.println("[CatherbyLobster] " + caught + " lobster, "
                        + getLevel(SKILL_FISHING) + " fishing, "
                        + ((System.currentTimeMillis() - start_time) / 60000L) + " mins.");
            }
        }
    }
}
