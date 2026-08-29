import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Label;
import java.awt.List;
import java.awt.Panel;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

/**
 * Cooking at the Zanaris bank, which needs Lost City to reach.
 *
 * Zanaris wins on the only measurement that matters: twelve steps from the
 * range to a fairy banker. Ardougne's north bank has a range sitting right
 * against its south fence and looks closer on a map, but that fence has no
 * gap -- the real walk is twenty-two steps out east and back in through the
 * north, nearly double. Do not trust eyeballed distance here; walk it.
 *
 * Pick one kind of fish or a dozen. The list is ordered by cooking level, and
 * the script always reaches for the lowest tier it still has stock of and can
 * legally cook, so a run started with shrimp, anchovies and lobster ticked
 * burns through every shrimp, then every anchovy, then the lobsters -- and the
 * lobsters wait politely if level 40 has not arrived yet, because by the time
 * the anchovies run out it will have.
 */
public final class ZanarisCook extends Script implements ActionListener {

    private static final int COOKING = 7;
    private static final int RANGE_X = 172, RANGE_Y = 3534;
    private static final int[] BANKER = { 224 };

    /* Range end first. The corners of the walk, taken from a route that
       honours the wall bits rather than the solid ones -- the client routes
       the straights between them by itself. */
    /*
     * WAY[0] is the cooking tile, and it has to be one the range can actually
     * be reached from. Three of the range's four neighbours are walkable and
     * only two of those are on its side of the wall -- pick the wrong one and
     * the client quietly reroutes every single cook, so the script marches to
     * a tile in the next room, gets dragged round to the range by the click,
     * cooks once, and marches back. Check the wall bits between the tile and
     * the object, not just that the tile is empty.
     */
    private static final int[][] WAY = {
        { 172, 3533 }, { 168, 3533 }, { 168, 3527 }, { 174, 3527 }
    };

    private static final class Fish {
        final String name;
        final int raw, cooked, burnt, lvl;
        Fish(String name, int raw, int cooked, int burnt, int lvl) {
            this.name = name; this.raw = raw;
            this.cooked = cooked; this.burnt = burnt; this.lvl = lvl;
        }
    }

    /* Ordered by cooking level, lowest first. main() walks this array in
       order, so "start at the lowest tier" is a property of the table rather
       than something the selection code has to sort out. */
    private static final Fish[] FISH = {
        new Fish("Shrimp (1)",        349, 350, 353,  1),
        new Fish("Sardine (1)",       354, 355, 360,  1),
        new Fish("Chicken (1)",       133, 132, 134,  1),
        new Fish("Beef (1)",          504, 132, 134,  1),
        new Fish("Bear meat (1)",     502, 132, 134,  1),
        new Fish("Rat meat (1)",      503, 132, 134,  1),
        new Fish("Mackerel (1)",      552, 553, 365,  1),
        new Fish("Cod (1)",           550, 551, 365,  1),
        new Fish("Herring (5)",       361, 362, 365,  5),
        new Fish("Anchovies (15)",    351, 352, 353, 15),
        new Fish("Trout (15)",        358, 359, 360, 15),
        new Fish("Pike (20)",         363, 364, 365, 20),
        new Fish("Salmon (25)",       356, 357, 360, 25),
        new Fish("Tuna (30)",         366, 367, 368, 30),
        new Fish("Lobster (40)",      372, 373, 374, 40),
        new Fish("Bass (42)",         554, 555, 368, 42),
        new Fish("Swordfish (45)",    369, 370, 371, 45),
        new Fish("Shark (80)",        545, 546, 547, 80),
        new Fish("Sea turtle (85)",  1192,1193,1248, 85),
    };

    public static void main(String[] argv) {
        new ZanarisCook(null).init(null);
    }

    private boolean[] picked;
    private Fish current;
    private int cooked_count, burnt_count;
    private long start_time, click_time, bank_time, menu_time, sleep_time;

    private Frame frame;
    private List list;

    /* Same trap S_Smelter documents: an unattended restart has nobody to
       click OK, so the choices are persisted and restored instead. */
    private static final java.io.File CONFIG =
        new java.io.File("script-bin", "ZanarisCook.cfg");

    public ZanarisCook(Extension ex) {
        super(ex);
    }

    private void saveConfig() {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < picked.length; i++) {
                if (picked[i]) {
                    if (sb.length() > 0) { sb.append(','); }
                    sb.append(i);
                }
            }
            java.io.PrintWriter out = new java.io.PrintWriter(CONFIG, "UTF-8");
            out.println(sb.toString());
            out.close();
        } catch (Exception e) {
            System.out.println("[ZanarisCook] Could not save the configuration: " + e);
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
            if (line == null || line.trim().length() == 0) { return; }
            boolean[] p = new boolean[FISH.length];
            boolean any = false;
            String[] parts = line.trim().split(",");
            for (int i = 0; i < parts.length; i++) {
                int n = Integer.parseInt(parts[i].trim());
                if (n >= 0 && n < FISH.length) { p[n] = true; any = true; }
            }
            if (!any) { return; }
            picked = p;
            for (int i = 0; i < FISH.length; i++) {
                if (p[i]) { list.select(i); } else { list.deselect(i); }
            }
            System.out.println("[ZanarisCook] Restored the saved selection.");
        } catch (Exception e) {
            System.out.println("[ZanarisCook] Saved configuration unreadable, ignoring it: " + e);
        }
    }

    @Override
    public void init(String params) {
        click_time = bank_time = menu_time = sleep_time = -1L;
        start_time = -1L;
        cooked_count = burnt_count = 0;
        current = null;
        if (frame == null) {
            list = new List(12, true);
            for (int i = 0; i < FISH.length; i++) {
                list.add(FISH[i].name);
            }
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
            frame.add(new Label("Tick everything to cook -- lowest level first."),
                BorderLayout.NORTH);
            frame.add(list, BorderLayout.CENTER);
            frame.add(buttons, BorderLayout.SOUTH);
            frame.setSize(300, 320);
        }
        loadConfig();
        if (!isUnattended() || picked == null) {
            frame.setLocationRelativeTo(null);
            frame.toFront();
            frame.requestFocus();
            frame.setVisible(true);
        }
    }

    /** The lowest-tier pick we still have raw stock of and are allowed to cook. */
    private Fish nextFromBank() {
        int lvl = getLevel(COOKING);
        for (int i = 0; i < FISH.length; i++) {
            if (!picked[i] || FISH[i].lvl > lvl) { continue; }
            if (bankCount(FISH[i].raw) > 0) { return FISH[i]; }
        }
        return null;
    }

    /** An inventory slot holding burnt remains of anything we picked, or -1. */
    private int burntIndex() {
        for (int i = 0; i < FISH.length; i++) {
            /* Anything whose burnt form IS its cooked form cannot burn, and
               dropping on that id would throw away the finished article. */
            if (!picked[i] || FISH[i].burnt == FISH[i].cooked) { continue; }
            /* Burnt ids never collide with any raw id in the table, so this
               cannot eat fish still waiting to be cooked. */
            int slot = getInventoryIndex(FISH[i].burnt);
            if (slot != -1) { return slot; }
        }
        return -1;
    }

    /** Whichever pick is sitting in the inventory right now. */
    private Fish carrying() {
        for (int i = 0; i < FISH.length; i++) {
            if (picked[i] && getInventoryCount(FISH[i].raw) > 0) { return FISH[i]; }
        }
        return null;
    }

    @Override
    public int main() {
        if (picked == null) {
            return 1000;   // window not confirmed yet
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
        if (isBanking()) {
            bank_time = -1L;
            for (int i = 0; i < FISH.length; i++) {
                if (!picked[i]) { continue; }
                int c = getInventoryCount(FISH[i].cooked);
                if (c > 0) { deposit(FISH[i].cooked, c); return random(600, 900); }
            }
            Fish held = carrying();
            if (held != null) {
                current = held;
                closeBank();
                return random(600, 800);
            }
            Fish next = nextFromBank();
            if (next == null) {
                return _end("Nothing left to cook.");
            }
            if (current != next) {
                System.out.println("[ZanarisCook] Now on " + next.name + ".");
                current = next;
            }
            int want = getEmptySlots();
            int have = bankCount(next.raw);
            if (want > have) { want = have; }
            if (want <= 0) {
                return _end("No room to withdraw " + next.name + ".");
            }
            withdraw(next.raw, want);
            return random(600, 900);
        } else if (bank_time != -1L) {
            if (System.currentTimeMillis() >= (bank_time + 8000L)) {
                bank_time = -1L;
            }
            return random(300, 400);
        }
        if (sleep_time != -1L) {
            if (System.currentTimeMillis() >= sleep_time) {
                useSleepingBag();
                sleep_time = -1L;
                return random(1500, 2500);
            }
            return 0;
        }
        if (click_time != -1L) {
            if (System.currentTimeMillis() >= click_time) {
                click_time = -1L;
            }
            return 0;
        }
        /* Burnt fish is worth nothing and a bank trip is the only other way
           to be rid of it, so it goes on the floor the moment it appears
           rather than riding along and costing a slot for the rest of the
           inventory. */
        int burnt = burntIndex();
        if (burnt != -1) {
            dropItem(burnt);
            return random(600, 900);
        }
        Fish held = carrying();
        if (held != null) {
            int walk = follow(0);
            if (walk != -1) { return walk; }
            useItemOnObject(held.raw, RANGE_X, RANGE_Y);
            /* Backstop only. A cook normally ends on the server message
               below, well inside this. */
            click_time = System.currentTimeMillis() + 4000L;
            return random(500, 700);
        }
        /* Fairy bankers wander their pen, so reach for one the moment it is
           in range rather than marching to the last waypoint first. */
        int[] banker = getNpcByIdNotTalk(BANKER);
        if (banker[0] != -1 && distanceTo(banker[1], banker[2]) <= 5) {
            talkToNpc(banker[0]);
            menu_time = System.currentTimeMillis();
            return random(1000, 2000);
        }
        int walk = follow(WAY.length - 1);
        if (walk != -1) { return walk; }
        if (banker[0] != -1) {
            talkToNpc(banker[0]);
            menu_time = System.currentTimeMillis();
            return random(1000, 2000);
        }
        return random(600, 1000);
    }

    private int nearestWaypoint() {
        int best = 0, bestd = Integer.MAX_VALUE;
        for (int i = 0; i < WAY.length; i++) {
            int d = distanceTo(WAY[i][0], WAY[i][1]);
            if (d < bestd) { bestd = d; best = i; }
        }
        return best;
    }

    /**
     * One step along WAY toward the given corner. Returns a sleep, or -1 once
     * we are standing on it.
     *
     * Walking to the tile you already stand on is a click the client throws
     * away, and a caller that keeps asking never moves again -- hence the
     * equality check rather than a distance one.
     */
    private int follow(int target) {
        if (isWalking()) { return random(400, 700); }
        int here = nearestWaypoint();
        int next = here == target ? target : (here < target ? here + 1 : here - 1);
        if (getX() == WAY[next][0] && getY() == WAY[next][1]) {
            if (next == target) { return -1; }
            next = next < target ? next + 1 : next - 1;
        }
        walkTo(WAY[next][0], WAY[next][1]);
        return random(600, 1000);
    }

    private int _end(String reason) {
        System.out.println("[ZanarisCook] " + reason);
        _printOut();
        stopScript();
        setAutoLogin(false);
        return 0;
    }

    @Override
    public void paint() {
        int y = 25;
        drawString("Zanaris Cook", 25, y, 1, 0xFFFFFF);
        y += 15;
        drawString("Runtime: " + _getRuntime(), 25, y, 1, 0xFFFFFF);
        y += 15;
        drawString("On: " + (current == null ? "-" : current.name), 25, y, 1, 0xFFFFFF);
        y += 15;
        drawString("Cooked: " + cooked_count + "  Burnt: " + burnt_count, 25, y, 1, 0xFFFFFF);
    }

    @Override
    public void onServerMessage(String str) {
        str = str.toLowerCase(Locale.ENGLISH);
        if (str.indexOf("tired") != -1) {
            sleep_time = System.currentTimeMillis() + random(800, 2500);
        } else if (str.indexOf("nicely cooked") != -1) {
            ++cooked_count;
            click_time = System.currentTimeMillis() + random(100, 200);
        } else if (str.indexOf("accidently burn") != -1) {
            ++burnt_count;
            click_time = System.currentTimeMillis() + random(100, 200);
        } else if (str.indexOf("advanced") != -1) {
            System.out.println("[ZanarisCook] Level " + getLevel(COOKING) + " cooking.");
        } else if (str.indexOf("busy") != -1) {
            menu_time = -1L;
        }
    }

    private void _printOut() {
        System.out.println("[ZanarisCook] Runtime: " + _getRuntime());
        System.out.println("[ZanarisCook] Cooked: " + cooked_count + ", burnt: " + burnt_count);
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
            int[] sel = list.getSelectedIndexes();
            if (sel == null || sel.length == 0) {
                return;   // nothing ticked; keep the window up
            }
            boolean[] p = new boolean[FISH.length];
            for (int i = 0; i < sel.length; i++) { p[sel[i]] = true; }
            picked = p;
            saveConfig();
        }
        frame.setVisible(false);
    }
}
