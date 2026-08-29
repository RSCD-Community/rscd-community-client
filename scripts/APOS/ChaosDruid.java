import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

/**
 * Chaos druids in the Taverley dungeon.
 *
 * There are four chaos druid groups in the world and three of them are shut
 * behind a lock. The Ardougne tower -- the obvious one, and the one the old
 * STS script's cousin used -- sits behind door 96 at (617,556), thieving 46.
 * Both Yanille groups are sealed rooms whose only way in is door 162,
 * thieving 82 and a lockpick. Taverley is the group anyone can reach: a
 * ladder in the village, no level on it, no key, no quest.
 *
 * The Edgeville dungeon group is unlocked too, and it is bigger -- eight
 * spawns against five -- but that room also holds black demons at combat 157,
 * Chronozon at 121, poison spiders at 64 and a dozen skeletons at 54, every
 * one aggressive. It is a death sentence for anyone who is not already strong
 * enough not to need herblaw levels. The Taverley corridor is quiet by
 * comparison: the walk passes one ghost at combat 25, and the black knights
 * and baby blue dragons are all twenty tiles or more off the path.
 *
 * It also picks up limpwurt roots, which the old script walked straight over.
 * That is the whole point of the trip: guam and tarromin are what the potions
 * are brewed from, but limpwurt is what strength potions are gated on, and at
 * 7.1% it is the rarest of the three.
 *
 * Autocast rules, learned from mudclient's own tick loop:
 *
 *   - fireAutocast() re-walks toward its target every second. Walking away
 *     while a target is still alive gets you dragged straight back, so this
 *     script only ever walks when it is out of combat.
 *   - A ground-click walk (menu 920) clears the target and sets
 *     autocastAcquireSuppressed. WalkTo and PickupItem do not go through
 *     that path, so scripted movement is safe -- but a person clicking the
 *     ground mid-fight will still switch autocast off for that fight.
 *   - Re-clicking Attack on a mob you are already fighting is pointless and
 *     re-aims autocast, so the attack only fires when out of combat.
 *
 * Nothing here reads or writes autocastEnabled. Whatever the F2 panel is set
 * to is what happens: melee, ranged or magic, the script does not care.
 */
public final class ChaosDruid extends Script implements ActionListener {

    private static final int DRUID = 270;
    private static final int SLEEPING_BAG = 1263;
    private static final int[] BANKER = { 95 };

    /** Grimy herbs, unidentified. */
    private static final int[] HERBS = {
        165, 435, 436, 437, 438, 439, 440, 441, 442, 443,
        815, 817, 819, 821, 823, 933
    };
    /** The reason for the trip. */
    private static final int LIMPWURT = 220;
    /** Runes the druids drop. They stack, so they cost one slot between them. */
    private static final int[] RUNES = { 40, 41, 42, 46, 38, 619 };

    /** Everything worth bending down for. */
    private static final int[] LOOT = build();

    /**
     * Loot that is also ammunition, and so never goes in the bank.
     *
     * Blood runes are the odd one out: the druids drop them and a mage
     * spends them, so a trip that banks everything it picked up also banks
     * the runes the next fight was going to be cast with. Runes stack, so
     * holding them back costs one inventory slot however many pile up --
     * cheaper than walking to Falador for ammunition we were carrying.
     */
    private static final int[] KEEP = { 619 };

    private static boolean keep(int id) {
        for (int i = 0; i < KEEP.length; i++) {
            if (KEEP[i] == id) { return true; }
        }
        return false;
    }

    private static int[] build() {
        int[] out = new int[HERBS.length + 1 + RUNES.length];
        int n = 0;
        for (int i = 0; i < HERBS.length; i++) { out[n++] = HERBS[i]; }
        out[n++] = LIMPWURT;
        for (int i = 0; i < RUNES.length; i++) { out[n++] = RUNES[i]; }
        return out;
    }

    /** Anything cooked that is worth eating when a druid gets lucky. */
    private static final int[] FOOD = { 373, 367, 357, 359, 352, 350, 355, 364, 362 };
    private static final int EAT_BELOW = 50;   /* percent */

    /*
     * Two planes, so two routes. The world is stacked in 944-tile bands
     * (Formulae.getHeight), which is why a dungeon y looks like 3352 rather
     * than 408 -- band three, plus the offset. Anything at or above BAND is
     * underground.
     */
    private static final int BAND = 2832;

    /** Ladder up, in the dungeon. */
    private static final int UP_X = 376, UP_Y = 3352;
    /** Ladder down, in Taverley. */
    private static final int DOWN_X = 376, DOWN_Y = 520;

    /* Fight end first, ladder last. The stair-step in the middle is a
       diagonal corridor, not noise -- walking it as one straight line clips
       the wall and the client reroutes. */
    /** How far from the fighting area a druid may be and still be worth
        taking where it stands, rather than walking the route back first. */
    private static final int ROAM = 10;

    private static final int[][] DUN = {
        { 345, 3318 }, { 347, 3318 }, { 347, 3319 }, { 348, 3319 },
        { 348, 3320 }, { 373, 3320 }, { 373, 3321 }, { 374, 3321 },
        { 374, 3322 }, { 375, 3322 }, { 375, 3323 }, { 376, 3323 },
        { 376, 3324 }, { 377, 3324 }, { 377, 3352 }
    };

    /*
     * Falador is walled, and the wall really is solid: where it jogs west
     * around y540 the corner is closed by two diagonal wall pieces at
     * (340,540) and (339,541). An earlier route crossed there, which the
     * server's own grid will happily path but the client refuses, and the
     * result was a bot pacing the inside of the wall forever. The only ways
     * in or out are the handholds and the two gates.
     *
     * So the trip is a loop, not a there-and-back: in over the wall, out
     * through the north gate.
     */

    /* Bank counter to the inside of the north gate. */
    private static final int[][] CITY = {
        { 330, 553 }, { 330, 552 }, { 327, 552 }, { 327, 547 },
        { 326, 547 }, { 326, 540 }, { 324, 540 }, { 324, 522 },
        { 317, 522 }, { 317, 494 }, { 319, 494 }, { 319, 493 },
        { 328, 493 }, { 328, 488 }, { 341, 488 }
    };

    /** The north gate itself. It is shut in the world data, so it has to be
        opened rather than walked through. */
    private static final int GATE_X = 341, GATE_Y = 487;

    /** Outside the north gate, round to the Taverley ladder. */
    private static final int[][] OUT = {
        { 342, 488 }, { 342, 501 }, { 343, 501 }, { 343, 511 },
        { 356, 511 }, { 356, 512 }, { 358, 512 }, { 358, 513 },
        { 359, 513 }, { 359, 515 }, { 360, 515 }, { 360, 516 },
        { 362, 516 }, { 362, 517 }, { 364, 517 }, { 364, 518 },
        { 365, 518 }, { 365, 519 }, { 366, 519 }, { 366, 521 },
        { 376, 521 }
    };

    /*
     * The short way west: handholds over the Falador wall, object 693 at
     * (339,555). It drops you at (338,555), five tiles from the bank counter,
     * and saves most of the northern detour.
     *
     * Two things make it a special case rather than just more waypoints.
     * It is one-way -- the agility table has oneWay true, 340,555 -> 338,555 --
     * so it is no use walking back out, and the trip east goes by the gate.
     * And it is not fatigue-exempt: at 100 the server answers "You are too
     * tired to do that, get some rest!" and you are left standing against
     * the wall with nowhere to go. Since a full-fatigue trip to the bank is
     * exactly the trip that goes for the sleeping bag, that is not a rare
     * case, so anything at 100 takes the gate both ways.
     */
    private static final int HANDHOLDS_X = 339, HANDHOLDS_Y = 555;

    /** Ladder to the foot of the handholds, east side of the wall. */
    private static final int[][] WALL = {
        { 377, 520 }, { 375, 521 }, { 375, 528 }, { 374, 528 },
        { 374, 532 }, { 373, 532 }, { 373, 540 }, { 358, 540 },
        { 358, 544 }, { 357, 544 }, { 357, 555 }, { 340, 555 }
    };

    /** Where the handholds drop you, on to the bank.
     *
     * The counter is walled on all four sides -- the gap is on the west face
     * at y553, not the south, so the last leg goes round to x327 and comes
     * back in. Stopping outside the south wall at (330,558) puts a wall
     * between you and every banker in the room. */
    private static final int[][] WEST = {
        { 338, 555 }, { 338, 558 }, { 327, 558 }, { 327, 553 },
        { 330, 553 }
    };

    /** At or west of this column you are outside the walls. */
    private static final int GATE_OUT_X = 342;

    /** South of this row the wall itself, not the gate, is the boundary. */
    private static final int WALL_Y = 542;

    /** West face of the wall in that southern stretch. */
    private static final int WALL_IN_X = 338;

    /* South of this row the handholds are the way in; north of it the gate
       is, and the gate is also the only way in at full fatigue. */
    private static final int SOUTH_Y = 510;

    public static void main(String[] argv) {
        new ChaosDruid(null).init(null);
    }

    private boolean configured;
    private boolean neverSleep;
    /* Sticky: the trip to the bank has to survive anything that
       happens on the way. Deriving it from the inventory each tick
       meant that eating one piece of food -- which frees a slot --
       turned the walk round and marched back to the druids. */
    private boolean banking;
    private int kills, looted;
    private long start_time, bank_time, menu_time, sleep_time, attack_time;

    private Frame frame;
    private Checkbox sleepBox;

    /* An unattended restart has nobody to click OK, so the answer is kept on
       disk and restored; see S_Smelter for the crash loop this avoids. */
    private static final java.io.File CONFIG =
        new java.io.File("script-bin", "ChaosDruid.cfg");

    public ChaosDruid(Extension ex) {
        super(ex);
    }

    private void saveConfig() {
        try {
            java.io.PrintWriter out = new java.io.PrintWriter(CONFIG, "UTF-8");
            out.println(neverSleep ? "nosleep" : "sleep");
            out.close();
        } catch (Exception e) {
            System.out.println("[ChaosDruid] Could not save the configuration: " + e);
        }
    }

    private void loadConfig() {
        if (!CONFIG.isFile()) { return; }
        try {
            java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    new java.io.FileInputStream(CONFIG), "UTF-8"));
            String line = in.readLine();
            in.close();
            if (line == null) { return; }
            neverSleep = line.trim().equals("nosleep");
            configured = true;
            sleepBox.setState(neverSleep);
            System.out.println("[ChaosDruid] Restored the saved configuration.");
        } catch (Exception e) {
            System.out.println("[ChaosDruid] Saved configuration unreadable, ignoring it: " + e);
        }
    }

    @Override
    public void init(String params) {
        start_time = -1L;
        bank_time = menu_time = sleep_time = attack_time = -1L;
        kills = looted = 0;
        banking = false;
        routeWay = null;
        if (frame == null) {
            sleepBox = new Checkbox("Never sleep (no sleeping bag)", false);
            Panel buttons = new Panel();
            Button b = new Button("OK");
            b.addActionListener(this);
            buttons.add(b);
            b = new Button("Cancel");
            b.addActionListener(this);
            buttons.add(b);

            frame = new Frame(getClass().getSimpleName());
            frame.addWindowListener(
                new StandardCloseHandler(frame, StandardCloseHandler.HIDE));
            frame.setIconImages(Constants.ICONS);
            frame.add(new Label("Chaos druids -- herbs, limpwurt roots, runes."),
                BorderLayout.NORTH);
            frame.add(sleepBox, BorderLayout.CENTER);
            frame.add(buttons, BorderLayout.SOUTH);
            frame.setSize(320, 150);
        }
        loadConfig();
        if (!isUnattended() || !configured) {
            frame.setLocationRelativeTo(null);
            frame.toFront();
            frame.requestFocus();
            frame.setVisible(true);
        }
    }

    @Override
    public int main() {
        if (!configured) {
            return 1000;
        }
        if (start_time == -1L) {
            start_time = System.currentTimeMillis();
        }
        if (isQuestMenu()) {
            answer(0);
            menu_time = -1L;
            bank_time = System.currentTimeMillis();
            return random(1000, 2000);
        }
        if (isSleeping()) {
            return random(800, 1200);
        }

        /* Eating is an inventory action, not a ground click, so it does not
           disturb the autocast target. Do it even mid-fight. */
        if (getHpPercent() < EAT_BELOW) {
            int food = getInventoryIndex(FOOD);
            if (food != -1) {
                useItem(food);
                return random(900, 1400);
            }
        }

        if (isBanking()) {
            bank_time = -1L;
            for (int i = 0; i < LOOT.length; i++) {
                if (keep(LOOT[i])) { continue; }
                int c = getInventoryCount(LOOT[i]);
                if (c > 0) { deposit(LOOT[i], c); return random(600, 900); }
            }
            /* Restock the bag we sleep in, unless we are not sleeping. */
            if (!neverSleep && getInventoryCount(SLEEPING_BAG) < 1) {
                if (bankCount(SLEEPING_BAG) < 1) {
                    return _end("No sleeping bag in the bank.");
                }
                withdraw(SLEEPING_BAG, 1);
                return random(600, 900);
            }
            closeBank();
            banking = false;
            return random(600, 800);
        } else if (bank_time != -1L) {
            if (System.currentTimeMillis() >= (bank_time + 8000L)) {
                bank_time = -1L;
            }
            return random(300, 400);
        }

        /* Fatigue. Sleeping is a full stop, so it waits for a gap in the
           fighting rather than cutting a fight short. With the bag switched
           off we simply keep swinging: past 100 the kills still drop loot,
           they just stop paying experience. */
        if (!neverSleep && getFatigue() > 95 && !inCombat()) {
            int bag = getInventoryIndex(SLEEPING_BAG);
            if (bag == -1) {
                banking = true;
                return goBank();
            }
            useSleepingBag();
            return random(1500, 2500);
        }

        /* In combat: hands off. Walking would be undone by autocast's own
           walk, and a second Attack click would only re-aim it. */
        if (inCombat()) {
            attack_time = -1L;
            return random(500, 900);
        }

        if (getEmptySlots() < 1) {
            banking = true;
        }
        if (banking) {
            return goBank();
        }

        /* Loot before anything else -- ground items expire. */
        int[] item = getItemById(LOOT);
        if (item[0] != -1 && distanceTo(item[1], item[2]) < 12) {
            pickupItem(item[0], item[1], item[2]);
            ++looted;
            return random(700, 1100);
        }

        /* Back to the fight, if we are not standing in it yet. */
        int walk = goFight();
        if (walk != -1) { return walk; }

        int[] druid = getNpcById(DRUID);
        if (druid[0] != -1) {
            if (attack_time != -1L
                && System.currentTimeMillis() < attack_time + 3000L) {
                return random(300, 500);
            }
            attackNpc(druid[0]);
            attack_time = System.currentTimeMillis();
            return random(900, 1400);
        }
        return random(600, 1000);
    }

    private boolean underground() {
        return getY() >= BAND;
    }

    /** Down the ladder and along the corridor to the druids. */
    /**
     * True when we are standing on the city side of the wall.
     *
     * The wall does not run down one column, which is the whole reason this
     * needs a method. In the north the gate is the boundary and the city
     * reaches out to x341; in the south the wall stands at x339, so 340 and
     * 341 are Taverley ground even though they sit west of the gate's
     * column. Judging the whole length by the gate put a bot that had just
     * missed its grip on the handholds -- standing at 340,555, still very
     * much outside -- on the city side of a wall it had not crossed, and
     * sent it walking at a bank counter five tiles away with solid stone in
     * between. There is no way round: the corner at (340,540) and (339,541)
     * is closed by diagonals the client will not path, so it paced there
     * until somebody noticed, and never tried the handholds again.
     */
    private boolean inside() {
        return getY() >= WALL_Y ? getX() <= WALL_IN_X : getX() < GATE_OUT_X;
    }

    private int goFight() {
        if (underground()) {
            /*
             * A fight ends where the druid fell, not where the route says it
             * should. Marching back to the same tile between every kill is
             * most of a trip spent going nowhere, so if there is another one
             * within sight of the fighting area we take it from where we are
             * standing and let the route wait.
             *
             * Both halves of that matter. Without the druid check we walk
             * back for no reason; without the leash on how far the fighting
             * area is behind us we would chase one druid to the next and
             * drift off down the dungeon.
             */
            int[] near = getNpcById(DRUID);
            if (near[0] != -1
                && distanceTo(near[1], near[2]) <= ROAM
                && distanceTo(DUN[0][0], DUN[0][1]) <= ROAM) {
                return -1;
            }
            return follow(DUN, 0);
        }
        if (inside()) {
            /* Still inside the walls. */
            int walk = follow(CITY, CITY.length - 1);
            if (walk != -1) { return walk; }
            /* The gate steps you through itself -- the handler teleports
               you to the far side. Do not follow it with a walk: if the
               click ever misses, that walk asks the client to reach a tile
               behind a shut gate, and the only way it can is back through
               the city and the whole way round. */
            atObject(GATE_X, GATE_Y);
            return random(1500, 2500);
        }
        int walk = follow(OUT, OUT.length - 1);
        if (walk != -1) { return walk; }
        atObject(DOWN_X, DOWN_Y);
        return random(2000, 3000);
    }

    /** Back up the ladder and across Taverley to the Falador west bank. */
    private int goBank() {
        if (underground()) {
            int walk = follow(DUN, DUN.length - 1);
            if (walk != -1) { return walk; }
            atObject(UP_X, UP_Y);
            return random(2000, 3000);
        }
        if (!inside()) {
            /*
             * Outside the walls, and which way in depends on where we are
             * standing rather than on which waypoint happens to be nearest.
             * The handholds are the southern way in and only make sense
             * walking up from the ladder; north of that we are beside the
             * gate, and the gate opens whatever the fatigue.
             *
             * Choosing by nearest waypoint instead looks reasonable and is
             * not: from the gate the closest tile on the handholds route is
             * the far end of it, sixty tiles east beside the ladder, so the
             * walk sets off away from a gate it is standing next to.
             */
            if (getY() >= SOUTH_Y && getFatigue() < 100) {
                int climb = follow(WALL, WALL.length - 1);
                if (climb != -1) { return climb; }
                atObject(HANDHOLDS_X, HANDHOLDS_Y);
                return random(1500, 2500);
            }
            int back = follow(OUT, 0);
            if (back != -1) { return back; }
            atObject(GATE_X, GATE_Y);
            return random(1500, 2500);
        }
        /* Inside. The handholds drop you in the south-west corner, the gate
           lets you in at the north -- different corners, different routes. */
        int walk = getY() >= 542
            ? follow(WEST, WEST.length - 1)
            : follow(CITY, 0);
        if (walk != -1) { return walk; }
        int[] banker = getNpcByIdNotTalk(BANKER);
        if (banker[0] != -1) {
            talkToNpc(banker[0]);
            menu_time = System.currentTimeMillis();
            return random(1000, 2000);
        }
        return random(600, 1000);
    }

    /* The route we are part-way along, and the waypoint we are heading for.
       Held in fields on purpose: route progress is a thing that happened, not
       a thing you can measure. Deriving it from geometry each tick -- pick the
       nearest waypoint, step one along from there -- reads fine and is wrong
       the moment anything moves you off the line. A fight does exactly that:
       you get dragged a couple of tiles, the waypoint behind you is now the
       nearer one, so the walk goes backwards to it, which puts the one ahead
       nearer again, and the character paces between two tiles forever. Once
       we have set out for a waypoint we keep going to it. */
    private int[][] routeWay;
    private int routeAt;

    /** Straight-line nearest waypoint. Only ever used to join a route, never
        to work out how far along one we already are. */
    private int nearestWaypoint(int[][] way) {
        int best = 0, bestd = Integer.MAX_VALUE;
        for (int i = 0; i < way.length; i++) {
            int d = distanceTo(way[i][0], way[i][1]);
            if (d < bestd) { bestd = d; best = i; }
        }
        return best;
    }

    /**
     * One step along the given route toward the given corner. Returns a
     * sleep, or -1 once we have arrived.
     *
     * A route is joined once -- when it is not the route we were already on,
     * or when something has moved us clean off it, which in practice means a
     * teleport or a death rather than a shove in a fight. After that the
     * waypoint only ever advances toward the target.
     */
    private int follow(int[][] way, int target) {
        if (way != routeWay || distanceTo(way[routeAt][0], way[routeAt][1]) > 40) {
            routeWay = way;
            routeAt = nearestWaypoint(way);
        }
        if (isWalking()) { return random(400, 700); }
        /* Arrived at the one we were heading for: take the next one along.
           Within a tile counts as arrived -- a walk that stops a step short
           against a corner would otherwise never satisfy an exact match, and
           the script would stand there clicking the same tile. */
        if (distanceTo(way[routeAt][0], way[routeAt][1]) <= 1) {
            if (routeAt == target) { return -1; }
            routeAt += routeAt < target ? 1 : -1;
        }
        walkTo(way[routeAt][0], way[routeAt][1]);
        return random(600, 1000);
    }

    private int _end(String reason) {
        System.out.println("[ChaosDruid] " + reason);
        _printOut();
        stopScript();
        setAutoLogin(false);
        return 0;
    }

    @Override
    public void paint() {
        int y = 25;
        drawString("Chaos Druid", 25, y, 1, 0xFFFFFF);
        y += 15;
        drawString("Runtime: " + _getRuntime(), 25, y, 1, 0xFFFFFF);
        y += 15;
        drawString("Kills: " + kills + "  Looted: " + looted, 25, y, 1, 0xFFFFFF);
        y += 15;
        drawString("Sleep: " + (neverSleep ? "off" : "on")
            + "  Fatigue: " + getFatigue() + "%", 25, y, 1, 0xFFFFFF);
    }

    @Override
    public void onServerMessage(String str) {
        str = str.toLowerCase(Locale.ENGLISH);
        if (str.indexOf("you have defeated") != -1) {
            ++kills;
        } else if (str.indexOf("busy") != -1) {
            menu_time = -1L;
        }
    }

    private void _printOut() {
        System.out.println("[ChaosDruid] Runtime: " + _getRuntime());
        System.out.println("[ChaosDruid] Kills: " + kills + ", looted: " + looted);
    }

    private String _getRuntime() {
        long secs = ((System.currentTimeMillis() - start_time) / 1000L);
        if (secs >= 3600) {
            return (secs / 3600) + " hours, " + ((secs % 3600) / 60) + " mins, " + (secs % 60) + " secs.";
        }
        if (secs >= 60) {
            return secs / 60 + " mins, " + (secs % 60) + " secs.";
        }
        return secs + " secs.";
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("OK")) {
            neverSleep = sleepBox.getState();
            configured = true;
            saveConfig();
        }
        frame.setVisible(false);
    }
}
