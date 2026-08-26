/**
 * Tree Gnome Stronghold agility course, one obstacle at a time in order.
 *
 * The course is a ring of seven obstacles and the server only pays the lap
 * bonus -- which is most of what the course is worth -- when all seven are
 * crossed in sequence. So this walks onto each obstacle's own staging tile
 * before it clicks, and never clicks anything from across the clearing.
 * That matters more than it sounds: several of these obstacles are
 * two-way, and the server picks the direction by which end of the crossing
 * the player is nearer. Click a two-way obstacle from the wrong side and it
 * politely carries you backwards, forever.
 *
 * Read out of the server's own ObjectAgility table -- stand here, click
 * there, land there:
 *
 *   0 log        stand (692,495)   click (692,495)   -> (692,499)
 *   1 net        stand (692,502)   click (692,503)   -> (692,1450)
 *   2 tower up   stand (693,1451)  click (693,1452)  -> (692,2395)
 *   3 rope swing stand (689,2395)  click (689,2395)  -> (685,2396)
 *   4 tower down stand (684,2396)  click (683,2396)  -> (684,508)
 *   5 net        stand (683,503)   click (683,502)   -> (683,501)
 *   6 pipe       stand (683,498)   click (683,497)   -> (683,494)
 *
 * and then it is a nine tile walk east from the pipe's mouth back to the
 * log to start again.
 *
 * Upper floors are y + 944 a level, so the two tree platforms are told
 * apart from the ground by y alone. On the ground the two halves of the
 * ring are told apart by x: everything at x 689 and above is the climb
 * out, everything at 688 and below is the way back down.
 *
 * Nothing here remembers a step number -- it asks the world where it is
 * every tick and works out which obstacle is next, so it picks the lap up
 * from any point on the course.
 */
public class GnomeAgility extends Script {

    private static final int LOG = 655, NET_UP = 647, TOWER_UP = 648;
    private static final int ROPE = 650, TOWER_DOWN = 649;
    private static final int NET_DOWN = 653, PIPE = 654;
    private static final int SLEEPING_BAG = 1263;

    /** Ground, east half: the log and the first net. */
    private static final int LOG_X = 692, LOG_Y = 495;
    private static final int NET_UP_X = 692, NET_UP_STAND_Y = 502, NET_UP_OBJ_Y = 503;

    /** First platform: the watch tower. */
    private static final int TOWER_UP_X = 693, TOWER_UP_STAND_Y = 1451, TOWER_UP_OBJ_Y = 1452;

    /** Second platform: rope swing east side, tower down west side. */
    private static final int ROPE_X = 689, ROPE_Y = 2395;
    private static final int TOWER_DOWN_STAND_X = 684, TOWER_DOWN_OBJ_X = 683;
    private static final int TOWER_DOWN_Y = 2396;

    /** Ground, west half: the second net and the pipe. */
    private static final int NET_DOWN_X = 683, NET_DOWN_STAND_Y = 503, NET_DOWN_OBJ_Y = 502;
    private static final int PIPE_X = 683, PIPE_STAND_Y = 498, PIPE_OBJ_Y = 497;

    /** Anything at or east of this line on the second platform is the rope side. */
    private static final int PLATFORM_SPLIT = 686;
    /** Anything at or east of this line on the ground is the climb out. */
    private static final int GROUND_SPLIT = 689;

    /* On while this is being trusted. Every few seconds it writes where the
       script is and which obstacle it decided was next, to the console and
       to script-bin/GnomeAgility.log. A course that loops back on itself
       looks identical from the outside whichever branch is at fault, and
       the log is the only thing that tells them apart. */
    private static final boolean DEBUG = true;

    private long start_time = -1L;
    private long last_report;
    private int laps;

    public GnomeAgility(Extension ex) {
        super(ex);
    }

    public void init(String params) {
        start_time = -1L;
        last_report = 0L;
        laps = 0;
    }

    public int main() {
        if (start_time == -1L) {
            start_time = System.currentTimeMillis();
        }
        if (isSleeping()) {
            return random(1000, 2000);
        }
        /* Agility is the one skill that refuses outright at full fatigue
           rather than paying nothing, so a tired run is a stuck run. */
        if (getFatigue() > 95) {
            if (getInventoryIndex(SLEEPING_BAG) == -1) {
                log("Fatigued with no sleeping bag. Stopping.");
                stopScript();
                return 1000;
            }
            useSleepingBag();
            return random(2000, 3000);
        }

        int y = getY(), x = getX();
        report();

        /* Second platform, up among the beams. */
        if (y >= 1888) {
            if (x >= PLATFORM_SPLIT) {
                return obstacle(ROPE_X, ROPE_Y, ROPE_X, ROPE_Y);
            }
            return obstacle(TOWER_DOWN_STAND_X, TOWER_DOWN_Y, TOWER_DOWN_OBJ_X, TOWER_DOWN_Y);
        }
        /* First platform, the tower's lower deck. */
        if (y >= 944) {
            return obstacle(TOWER_UP_X, TOWER_UP_STAND_Y, TOWER_UP_X, TOWER_UP_OBJ_Y);
        }
        /* Ground, west half: down off the tower, net, pipe, then home. */
        if (x < GROUND_SPLIT) {
            if (y >= NET_DOWN_STAND_Y) {
                return obstacle(NET_DOWN_X, NET_DOWN_STAND_Y, NET_DOWN_X, NET_DOWN_OBJ_Y);
            }
            if (y >= 496) {
                return obstacle(PIPE_X, PIPE_STAND_Y, PIPE_X, PIPE_OBJ_Y);
            }
            /* Out of the pipe and the lap is done. */
            return walkHome();
        }
        /* Ground, east half. The log sets you down at y 499, which is why
           the net's half starts there and not at the tile it is climbed
           from -- read it as "south of the log's far end". */
        if (y >= 499) {
            return obstacle(NET_UP_X, NET_UP_STAND_Y, NET_UP_X, NET_UP_OBJ_Y);
        }
        return obstacle(LOG_X, LOG_Y, LOG_X, LOG_Y);
    }

    /**
     * Stand on the obstacle's staging tile, then use it.
     *
     * The exact tile is the point. A two-way obstacle sends the player to
     * whichever end they are further from, so being one tile out on the
     * wrong side of the middle is what turns a course into a shuttle.
     */
    private int obstacle(int standX, int standY, int objX, int objY) {
        if (getX() != standX || getY() != standY) {
            if (!isWalking()) {
                walkTo(standX, standY);
            }
            return random(700, 1200);
        }
        atObject(objX, objY);
        return random(1800, 2800);
    }

    /** The walk back from the pipe's mouth to the top of the log. */
    private int walkHome() {
        if (getX() == LOG_X && getY() == LOG_Y) {
            laps++;
            log("lap " + laps + " done");
            return 100;
        }
        if (!isWalking()) {
            walkTo(LOG_X, LOG_Y);
        }
        return random(700, 1200);
    }

    /** Throttled state dump: where, and which obstacle it decided was next. */
    private void report() {
        if (!DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < last_report + 4000L) {
            return;
        }
        last_report = now;
        int x = getX(), y = getY();
        String next;
        if (y >= 1888) {
            next = x >= PLATFORM_SPLIT ? "ROPE" : "TOWER_DOWN";
        } else if (y >= 944) {
            next = "TOWER_UP";
        } else if (x < GROUND_SPLIT) {
            next = y >= NET_DOWN_STAND_Y ? "NET_DOWN" : y >= 496 ? "PIPE" : "HOME";
        } else {
            next = y >= 499 ? "NET_UP" : "LOG";
        }
        log("(" + x + "," + y + ")"
                + " fatigue=" + getFatigue()
                + " walking=" + isWalking()
                + " laps=" + laps
                + " -> " + next);
    }

    /**
     * Both to the console and to script-bin/GnomeAgility.log.
     *
     * The console is not always reachable -- a client launched from an IDE
     * or behind a pipe sends stdout somewhere nobody can read afterwards --
     * and a bug you cannot see the state of is a bug you fix by guessing.
     */
    private void log(String line) {
        System.out.println("[GnomeAgility] " + line);
        java.io.PrintWriter out = null;
        try {
            out = new java.io.PrintWriter(new java.io.FileWriter(
                    new java.io.File("script-bin", "GnomeAgility.log"), true));
            out.println(line);
        } catch (Exception e) {
            /* logging must never be the thing that stops the script */
        } finally {
            if (out != null) out.close();
        }
    }
}
