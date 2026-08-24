/**
 * Mining Guild coal miner.
 *
 * Written because S_Miner's guild mode decides where it is by testing the
 * player's coordinates against hand-drawn boxes, and one tile outside a box
 * it deadlocks: a full inventory standing in the bank doorway clicks the
 * door forever instead of banking. A script that only survives from one
 * starting position is a script that dies the first time anything nudges
 * it, so this one is built the other way round. Every tick it asks where it
 * is from the world -- underground or not, which building it stands in,
 * what is within reach -- and takes the next step from there. Start it at
 * the rocks, on the ladder, inside the bank, or anywhere along the walk,
 * and it carries on.
 *
 * The route, read out of the server's own landscape and object data rather
 * than guessed at:
 *
 *   Ladder house   walls x272-278, y563-568, its ONLY opening the door on
 *                  the north wall at (274,563). Ladder 223 inside (274,566)
 *                  goes down; Ladder 5 (274,3398) comes back up.
 *   Rocks          coal 110/111 and mithril 107, a few tiles from the foot
 *                  of the ladder, around (270,3395).
 *   Falador bank   walls x280-287, y564-574, entered by the double doors on
 *                  the east wall at (287,571). Bankers stand x282-285,
 *                  y566-573.
 *
 * Both buildings are sealed except for those two doors, so the walk between
 * them runs north of both, along y561-562. That is what WAY_* below is.
 */
public class GuildMiner extends Script {

    /*
     * What to swing at. The guild holds 28 coal, 4 mithril, 4 iron, 3 tin,
     * 3 copper, one gold (264,3380) and one adamantite (276,3373) at the
     * north end. Coal by default because it is the only ore here with
     * enough rocks to keep a miner busy; the single addy and gold rocks
     * respawn far too slowly to bot on their own, though they are worth
     * adding alongside coal if you want them picked up in passing.
     *
     *   coal 110,111   mithril 107   iron 102,103   gold 112,113
     *   adamantite 108,109   tin 104,105   copper 100,101
     */
    private static final int[] ROCKS = { 110, 111 };

    /* The two doors, as wall objects: {x, y} of the wall itself. */
    private static final int HOUSE_DOOR_X = 274, HOUSE_DOOR_Y = 563;
    private static final int BANK_DOOR_X = 287, BANK_DOOR_Y = 571;
    private static final int DOOR_ID = 2, BANK_DOOR_ID = 16;

    private static final int LADDER_DOWN_X = 274, LADDER_DOWN_Y = 566;
    private static final int LADDER_UP_X = 274, LADDER_UP_Y = 3398;
    /* Where to stand when every rock in view is spent. Middle of the coal,
       within a short walk of the whole cavern including the north end. */
    private static final int PIT_X = 270, PIT_Y = 3390;

    /* Waypoints north of both buildings, house end first. */
    private static final int[][] WAY = {
        { 274, 562 }, { 276, 561 }, { 283, 561 }, { 287, 562 }, { 287, 570 }
    };

    private static final int BANKER = 95;
    private static final int SLEEPING_BAG = 1263;
    private static final int[] PICKAXES = { 1262, 1261, 1260, 1259, 1258, 156 };

    /* Everything worth carrying home: ores, bars, gems. */
    private static final int[] KEEP = {
        155, 150, 151, 152, 153, 154, 149, 383, 202, 409,
        160, 159, 158, 157, 542, 889, 890, 891,
        161, 162, 163, 164, 523, 892, 893, 894
    };

    private static final int SKILL_MINING = 14;

    private long start_time = -1L;
    private int mined;

    public GuildMiner(Extension ex) {
        super(ex);
    }

    public void init(String params) {
        start_time = -1L;
        mined = 0;
    }

    public int main() {
        if (start_time == -1L) {
            start_time = System.currentTimeMillis();
        }
        if (isSleeping()) {
            return random(1000, 2000);
        }
        if (getFatigue() > 95 && getInventoryIndex(SLEEPING_BAG) != -1) {
            useSleepingBag();
            return random(2000, 3000);
        }
        if (getInventoryIndex(PICKAXES) == -1) {
            System.out.println("[GuildMiner] No pickaxe in the inventory. Stopping.");
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
        return getEmptySlots() > 0 ? toRocks() : toBank();
    }

    // ---------------------------------------------------------- outbound --

    /** Room in the pack: get underground and swing at something. */
    private int toRocks() {
        if (isUnderground()) {
            int[] rock = getObjectById(ROCKS);
            if (rock[0] != -1) {
                atObject(rock[1], rock[2]);
                return random(1200, 1800);
            }
            /* All mined out for the moment: wait among them for a respawn
               rather than wandering off and losing the spot. */
            if (!isWalking() && distanceTo(PIT_X, PIT_Y) > 3) {
                walkTo(PIT_X, PIT_Y);
            }
            return random(1200, 2000);
        }
        if (inHouse()) {
            atObject(LADDER_DOWN_X, LADDER_DOWN_Y);
            return random(2000, 3000);
        }
        if (inBank()) {
            return leaveBank();
        }
        /* Outside: walk the north corridor to the house door, then through. */
        if (getX() == HOUSE_DOOR_X && getY() == HOUSE_DOOR_Y - 1) {
            return pass(HOUSE_DOOR_X, HOUSE_DOOR_Y, DOOR_ID,
                    HOUSE_DOOR_X, HOUSE_DOOR_Y);
        }
        return follow(0);
    }

    // ----------------------------------------------------------- inbound --

    /** Pack is full: reach a banker, from wherever this happens to be. */
    private int toBank() {
        /* Proximity beats geography every time. If a banker is standing in
           talking range there is nothing to walk to and no door to open,
           whatever the coordinates say -- this is the case that used to
           deadlock. */
        int[] banker = getNpcByIdNotTalk(BANKER);
        if (banker[0] != -1 && distanceTo(banker[1], banker[2]) <= 4) {
            talkToNpc(banker[0]);
            return random(2500, 3500);
        }
        if (isUnderground()) {
            if (distanceTo(LADDER_UP_X, LADDER_UP_Y) <= 3) {
                atObject(LADDER_UP_X, LADDER_UP_Y);
                return random(2000, 3000);
            }
            if (!isWalking()) {
                walkTo(LADDER_UP_X, LADDER_UP_Y + random(0, 1));
            }
            return random(1200, 2000);
        }
        if (inBank()) {
            if (banker[0] != -1) {
                if (!isWalking()) {
                    walkTo(banker[1], banker[2] + random(0, 1));
                }
                return random(1200, 2000);
            }
            return random(600, 1000);
        }
        if (inHouse()) {
            /* Out through the north door: stand under it, then through it. */
            if (getX() == HOUSE_DOOR_X && getY() == HOUSE_DOOR_Y) {
                return pass(HOUSE_DOOR_X, HOUSE_DOOR_Y, DOOR_ID,
                        HOUSE_DOOR_X, HOUSE_DOOR_Y - 1);
            }
            if (!isWalking()) {
                walkTo(HOUSE_DOOR_X, HOUSE_DOOR_Y);
            }
            return random(1000, 1600);
        }
        /* Outside: corridor east, then in through the bank doors. */
        if (getX() == BANK_DOOR_X && getY() == BANK_DOOR_Y) {
            return pass(BANK_DOOR_X, BANK_DOOR_Y, BANK_DOOR_ID,
                    BANK_DOOR_X - 1, BANK_DOOR_Y);
        }
        if (getX() == BANK_DOOR_X && Math.abs(getY() - BANK_DOOR_Y) <= 2) {
            if (!isWalking()) {
                walkTo(BANK_DOOR_X, BANK_DOOR_Y);
            }
            return random(800, 1400);
        }
        return follow(WAY.length - 1);
    }

    /** Step out of the bank the way we came in. */
    private int leaveBank() {
        if (getX() == BANK_DOOR_X - 1 && getY() == BANK_DOOR_Y) {
            return pass(BANK_DOOR_X, BANK_DOOR_Y, BANK_DOOR_ID,
                    BANK_DOOR_X, BANK_DOOR_Y);
        }
        if (!isWalking()) {
            walkTo(BANK_DOOR_X - 1, BANK_DOOR_Y);
        }
        return random(1000, 1600);
    }

    // ----------------------------------------------------------- walking --

    /**
     * Walk the corridor towards WAY[goal], one waypoint at a time.
     *
     * Progress is measured against the world, not remembered: whichever
     * waypoint we are nearest to is where we are, so being teleported,
     * logged back in, or simply started half way along all behave the same.
     */
    private int follow(int goal) {
        int nearest = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < WAY.length; ++i) {
            int d = distanceTo(WAY[i][0], WAY[i][1]);
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        int next = nearest;
        if (best <= 2) {
            next = nearest < goal ? nearest + 1
                 : nearest > goal ? nearest - 1 : goal;
        }
        if (!isWalking()) {
            walkTo(WAY[next][0], WAY[next][1]);
        }
        return random(1200, 2000);
    }

    /**
     * Get to the far side of a door, standing on the near side of it.
     *
     * Opening a door does NOT move you through it -- the wall stops
     * blocking and that is all. A script that clicks the door and then
     * re-reads its own position finds itself exactly where it was, still
     * on the wrong side, and clicks again; because the door is open by
     * then the click does nothing at all, and it stands in the doorway
     * until a human walks it out by hand. Opening and stepping through
     * have to be two states of one move, which is what this is: click
     * while the door is shut, walk the moment it isn't.
     *
     * (x,y) is the wall itself; (tx,ty) the tile on the other side.
     */
    private int pass(int x, int y, int id, int tx, int ty) {
        if (getWallObjectIdFromCoords(x, y) == id) {
            atWallObject(x, y);
            return random(1200, 1800);
        }
        if (!isWalking()) {
            walkTo(tx, ty);
        }
        return random(800, 1400);
    }

    // ------------------------------------------------------------ where --

    private boolean isUnderground() {
        return getY() > 3000;
    }

    /** Inside the ladder house: its walls are x272-278, y563-568. */
    private boolean inHouse() {
        return getX() > 272 && getX() < 278 && getY() > 562 && getY() < 568;
    }

    /** Inside the bank: its walls are x280-287, y564-574. */
    private boolean inBank() {
        return getX() > 280 && getX() < 287 && getY() > 564 && getY() < 574;
    }

    public void onServerMessage(String str) {
        if (str.indexOf("just mined") != -1 || str.indexOf("manage to mine") != -1) {
            ++mined;
            if (mined % 50 == 0) {
                System.out.println("[GuildMiner] " + mined + " ore, "
                        + getLevel(SKILL_MINING) + " mining, "
                        + ((System.currentTimeMillis() - start_time) / 60000L) + " mins.");
            }
        }
    }
}
