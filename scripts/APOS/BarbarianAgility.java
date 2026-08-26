/**
 * Barbarian Outpost agility course, six obstacles in order, level 35.
 *
 * Two things make this course harder to script than the gnome one.
 *
 * The first is that it does not close into a neat ring: the way back from
 * the ledge is a plain ladder, and the last two obstacles are the low walls
 * in the outpost fence, which are boundaries rather than objects and so are
 * jumped with atWallObject rather than atObject. They carry the lap bonus
 * -- 75 of the course's 150 experience sits on the second wall alone -- so
 * a script that skips them for being awkward throws away half the course.
 *
 * The second is that the legs overlap. The rope is entered from (487,556)
 * and the second wall sets you down at (494,558), a few tiles apart in the
 * same lane, heading opposite ways. Deciding what to do next from position
 * alone -- which is how the gnome script works, and why that one can be
 * picked up mid-lap -- turns into a coin flip here, and the coin lands on
 * "swing the rope again" often enough to ruin the lap. So this one keeps a
 * step counter, seeds it from position when it starts or gets lost, and
 * advances it only when the player is standing on the tile the last
 * obstacle was supposed to deliver them to.
 *
 * Read out of the server's own tables -- walk this way, stand here, use
 * that, land there:
 *
 *   0 rope swing  via (494,556)(489,556)(489,555)(487,555)
 *                 stand (487,556)  obj  (486,556)  -> (487,559)
 *   1 log                                 stand (487,563)  obj  (487,563)  -> (492,563)
 *   2 net                                 stand (494,563)  obj  (495,563)  -> (496,1506)
 *   3 ledge                               stand (497,1506) obj  (498,1506) -> (501,1506)
 *   4 ladder down                         stand (501,1507) obj  (501,1508) -> (501,566)
 *   5 low wall    via (501,558)(498,558)  stand (497,558)  wall (497,558)  -> (496,558)
 *   6 low wall                            stand (495,558)  wall (495,558)  -> (494,558)
 *
 * The rope crosses the spiked pit, which is the 2x2 block at (486-487,
 * 557-558) directly south of it: you stand on the beam at (487,556), swing
 * over the pit, and land on (487,559) at the head of the lane that runs
 * south to the log. It does not run east-west. There is a second beam at
 * (485,556) and it is tempting to read the swing as going between the two
 * beams, but (485,556) is on the far side of the outpost fence, in the
 * parallel lane outside the course -- landing there means walking all the
 * way south to the road at y566 and back up to reach the log, which is not
 * a course at all. The pit is the thing the rope exists to cross.
 *
 * The low walls are jumped westward, 163 first and then 164, and the two
 * tiles between them are reachable no other way.
 *
 * The ladder is not an obstacle and pays nothing; it is only how you get
 * off the ledge. The walls are jumped from the east both times, because
 * the server sends you to whichever side of a wall you are not already on.
 *
 * The spiked pits either side of the rope and the net are why the walking
 * legs list waypoints instead of one long walkTo: the direct line between
 * two of these landing tiles runs through a pit, and a walk that cannot be
 * routed is a walk that quietly does nothing.
 */
public class BarbarianAgility extends Script {

    private static final int SLEEPING_BAG = 1263;
    private static final int REQUIRED_LEVEL = 35, AGILITY = 16, HITS = 3;

    /** The step whose landing spans two floors, and where the upper one starts. */
    private static final int LADDER = 4;
    private static final int UPPER_FLOOR = 944;

    /* Every step: the waypoints to walk first, the tile to stand on, the
       tile to use, whether that tile is a wall, and the tile the server
       puts you on afterwards. Order is the course order. */
    private static final int[][] WAYPOINTS = {
        {494, 556, 489, 556, 489, 555, 487, 555},
                                /* 0 rope   -- west along the lane, then in */
        {},                     /* 1 log    -- straight down the lane */
        {},                     /* 2 net    */
        {},                     /* 3 ledge  */
        {},                     /* 4 ladder */
        {501, 558, 498, 558},   /* 5 wall   -- north out of the ladder's alley */
        {}                      /* 6 wall   */
    };
    private static final int[][] STAND = {
        {487, 556}, {487, 563}, {494, 563}, {497, 1506}, {501, 1507}, {497, 558}, {495, 558}
    };
    private static final int[][] USE = {
        {486, 556}, {487, 563}, {495, 563}, {498, 1506}, {501, 1508}, {497, 558}, {495, 558}
    };
    private static final boolean[] IS_WALL = {
        false, false, false, false, false, true, true
    };
    private static final int[][] LANDING = {
        {487, 559}, {492, 563}, {496, 1506}, {501, 1506}, {501, 566}, {496, 558}, {494, 558}
    };
    private static final String[] NAMES = {
        "rope", "log", "net", "ledge", "ladder", "wall-163", "wall-164"
    };

    /** How long a step may make no progress before the position is re-read. */
    private static final long LOST_MS = 45000L;

    /** The furthest a single walk request is allowed to ask for. */
    private static final int HOP = 3;

    /* On while this is being trusted -- see script-bin/BarbarianAgility.log. */
    private static final boolean DEBUG = true;

    private int step;
    private int waypoint;
    private long last_progress;
    private long last_report;
    private int laps;
    private boolean too_low;
    private int prev_x, prev_y;
    private int last_hits;
    private boolean bailing;

    public BarbarianAgility(Extension ex) {
        super(ex);
    }

    public void init(String params) {
        step = -1;
        waypoint = 0;
        last_progress = 0L;
        last_report = 0L;
        laps = 0;
        too_low = false;
        prev_x = -1;
        prev_y = -1;
        last_hits = 999;
        bailing = false;
    }

    public int main() {
        if (too_low) {
            return 1000;
        }
        if (getLevel(AGILITY) < REQUIRED_LEVEL) {
            log("Agility " + getLevel(AGILITY) + ", the course needs "
                    + REQUIRED_LEVEL + ". Stopping.");
            too_low = true;
            stopScript();
            return 1000;
        }
        if (isSleeping()) {
            return random(1000, 2000);
        }
        /* Agility refuses outright at full fatigue rather than paying
           nothing, so a tired run is a stuck run. */
        if (getFatigue() > 90) {
            if (getInventoryIndex(SLEEPING_BAG) == -1) {
                return bail("Fatigued with no sleeping bag");
            }
            useSleepingBag();
            return random(2000, 3000);
        }

        /* This course throws people, and it does it often at the level it
           opens at. The rope, the log and the ledge each roll a fall; the
           net cannot fail and the low walls have no fall at all. The roll
           stops at required level plus 25, so agility 60 here -- read the
           table's failStopLvl of 0 as "not recorded", not as "never", or
           you will conclude the course is harmless and be wrong. At 35 it
           is a 45% chance per obstacle, at 43 about 21%, at 55 under 2%.

           A fall costs a fifth of CURRENT hits, which is the number that
           decides this threshold. Damage shrinks as hits do -- 9 off 49,
           but only 3 off 16 -- so low hits is far less dangerous here than
           it looks, and eating early just burns food to undo damage that
           was never going to compound. A third leaves room for six or
           seven falls in a row before anything is at stake. It is not
           lower than a third because the max(1, ...) floor does not shrink
           with you: from single digits the falls still come off one at a
           time, all the way down. */
        int hp = getCurrentLevel(HITS);
        if (hp <= getLevel(HITS) / 3) {
            int carried = getInventoryCount();
            for (int i = 0; i < carried; i++) {
                if ("eat".equals(getItemCommand(i).toLowerCase())) {
                    useItem(i);
                    return random(800, 1200);
                }
            }
            return bail("Hits " + hp + "/" + getLevel(HITS) + " and no food left");
        }

        long now = System.currentTimeMillis();
        /* Moving at all counts as progress: the lost timer is there for a
           step that cannot be walked, not for one that is simply long. */
        if (getX() != prev_x || getY() != prev_y) {
            prev_x = getX();
            prev_y = getY();
            last_progress = now;
        }
        if (step < 0) {
            seed("start");
        }
        /* Standing on what the last obstacle promised means it worked. */
        if (arrived()) {
            advance();
            return random(400, 800);
        }
        /* Nothing has moved for a while: the step counter and the world
           disagree, and the world wins. */
        if (now - last_progress > LOST_MS) {
            seed("lost");
        }
        report();
        return travel();
    }

    /** Walk the step's waypoints, then its staging tile, then use it. */
    private int travel() {
        int[] via = WAYPOINTS[step];
        if (waypoint < via.length / 2) {
            int wx = via[waypoint * 2], wy = via[waypoint * 2 + 1];
            if (getX() == wx && getY() == wy) {
                waypoint++;
                return random(200, 400);
            }
            return stepTowards(wx, wy);
        }
        if (!at(STAND[step])) {
            return stepTowards(STAND[step][0], STAND[step][1]);
        }
        if (IS_WALL[step]) {
            atWallObject(USE[step][0], USE[step][1]);
        } else {
            atObject(USE[step][0], USE[step][1]);
        }
        last_progress = System.currentTimeMillis();
        return random(1800, 2800);
    }

    /**
     * One short hop towards a tile, never the whole distance at once.
     *
     * A walkTo the client cannot find a route for does nothing at all --
     * no error, no movement, no way to tell it apart from a walk still in
     * progress. The outpost is full of routes it gives up on: pits, the
     * bar's walls, the fence line. Asking for three tiles at a time keeps
     * every request inside what it will actually solve, and turns a walk
     * that would have failed silently into one that gets a little closer
     * every tick.
     */
    private int stepTowards(int tx, int ty) {
        /* Never walk towards another floor. Nothing routes between them --
           only a ladder does -- so a request like this is always the step
           counter being out of date, and walking it out is how a wrong
           step turns into a wrong lap. */
        if ((ty >= UPPER_FLOOR) != (getY() >= UPPER_FLOOR)) {
            seed("wrong floor");
            return random(400, 800);
        }
        int dx = tx - getX(), dy = ty - getY();
        if (dx > HOP) dx = HOP; else if (dx < -HOP) dx = -HOP;
        if (dy > HOP) dy = HOP; else if (dy < -HOP) dy = -HOP;
        if (!isWalking()) {
            walkTo(getX() + dx, getY() + dy);
        }
        return random(700, 1200);
    }

    private void advance() {
        if (step == LANDING.length - 1) {
            laps++;
            log("lap " + laps + " done");
            step = 0;
        } else {
            step++;
        }
        waypoint = 0;
        last_progress = System.currentTimeMillis();
    }

    /**
     * Work out which step the player is on from where they are standing.
     *
     * Deliberately coarse: this only has to be right often enough to get
     * back onto the ring, and every wrong guess costs one obstacle crossed
     * out of turn, which the server treats as "not a lap" rather than as
     * cheating.
     */
    private void seed(String why) {
        int x = getX(), y = getY();
        int guess;
        if (y >= 944) {
            guess = x <= 497 ? 3 : 4;      /* on the ledge platform */
        } else if (x >= 498) {
            guess = 5;                     /* east of both low walls */
        } else if (x >= 496) {
            guess = 6;                     /* in the gap between them */
        } else if (y >= 559) {
            guess = x <= 487 ? 1 : 2;      /* south of the pit: lane, then log */
        } else {
            guess = 0;                     /* the outpost yard */
        }
        log("seed(" + why + ") at (" + x + "," + y + ") -> step "
                + guess + " " + NAMES[guess]);
        step = guess;
        waypoint = nearestWaypoint(guess);
        last_progress = System.currentTimeMillis();
    }

    /**
     * Which waypoint of a step to walk to first, given where we are.
     *
     * Always starting at the first one is what strands a script that was
     * picked up mid-leg: the first waypoint of the rope's approach is at
     * the east end of the lane, and walking to it from the middle of the
     * yard means turning round and walking into the fence. Picking the
     * nearest one instead -- or skipping straight to the staging tile when
     * that is nearer than any of them -- joins the route where the player
     * already is.
     */
    private int nearestWaypoint(int s) {
        int[] via = WAYPOINTS[s];
        int best = via.length / 2;
        int bestd = Math.abs(getX() - STAND[s][0]) + Math.abs(getY() - STAND[s][1]);
        for (int i = 0; i < via.length / 2; i++) {
            int d = Math.abs(getX() - via[i * 2]) + Math.abs(getY() - via[i * 2 + 1]);
            if (d < bestd) {
                bestd = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Get out, not just stop.
     *
     * Stopping the script leaves the player standing in the outpost with
     * whatever emptied their hits still standing next to them, which is
     * the worst of both worlds if nobody is at the keyboard. The server
     * refuses a logout in combat and for ten seconds after, so this keeps
     * asking until it is allowed, and only gives up on the script once the
     * connection is actually gone.
     */
    private int bail(String why) {
        if (!bailing) {
            bailing = true;
            log(why + ". Logging out.");
        }
        if (!isLoggedIn()) {
            stopScript();
            return 1000;
        }
        if (canLogout()) {
            logout();
        }
        return random(1500, 2500);
    }

    /**
     * Has the last obstacle delivered us?
     *
     * Every step but the ladder answers this with one tile. The ladder
     * cannot, and pretending it can is what made this script wander.
     * Upper floors sit 944 tiles further down the y axis than the ground
     * they stand on, so the ledge platform at y1506 is the same place as
     * y562 below it. Coming down the ladder therefore changes y by 944 in
     * one move, and the exact tile it lands on is not worth pinning: what
     * matters is that we are on the ground again, and the ladder is the
     * only way off that platform, so the plane alone is proof.
     *
     * Pinning it to a tile instead means the step never completes, and the
     * script spends the rest of the leg trying to walk back to a staging
     * tile 944 tiles away on a floor it is no longer standing on -- which
     * from the ground is just a long aimless shuffle south until the lost
     * timer fires. Once per lap, every lap.
     */
    private boolean arrived() {
        if (step == LADDER) {
            return getY() < UPPER_FLOOR;
        }
        return at(LANDING[step]);
    }

    private boolean at(int[] tile) {
        return getX() == tile[0] && getY() == tile[1];
    }

    /**
     * Throttled state dump: where, which step, how far along its walk.
     *
     * Hits are in here because the course takes them: a fall is a fifth of
     * them and resets the lap. The line names the tile and the obstacle,
     * which is what tells a fall apart from something else in the outpost
     * doing the hitting. A drop is worth breaking the throttle for.
     */
    private void report() {
        if (!DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        int hits = getCurrentLevel(HITS);
        if (hits < last_hits) {
            last_hits = hits;
            last_report = now;
            log("HIT: hp now " + hits + "/" + getLevel(HITS) + " at ("
                    + getX() + "," + getY() + ") step=" + step + " " + NAMES[step]);
            return;
        }
        last_hits = hits;
        if (now < last_report + 4000L) {
            return;
        }
        last_report = now;
        log("(" + getX() + "," + getY() + ")"
                + " step=" + step + " " + NAMES[step]
                + " wp=" + waypoint
                + " hp=" + getCurrentLevel(HITS) + "/" + getLevel(HITS)
                + " fatigue=" + getFatigue()
                + " walking=" + isWalking()
                + " laps=" + laps);
    }

    /**
     * Both to the console and to script-bin/BarbarianAgility.log.
     *
     * The console is not always reachable -- a client launched from an IDE
     * or behind a pipe sends stdout somewhere nobody can read afterwards --
     * and a bug you cannot see the state of is a bug you fix by guessing.
     */
    private void log(String line) {
        System.out.println("[BarbarianAgility] " + line);
        java.io.PrintWriter out = null;
        try {
            out = new java.io.PrintWriter(new java.io.FileWriter(
                    new java.io.File("script-bin", "BarbarianAgility.log"), true));
            out.println(line);
        } catch (Exception e) {
            /* logging must never be the thing that stops the script */
        } finally {
            if (out != null) out.close();
        }
    }
}
