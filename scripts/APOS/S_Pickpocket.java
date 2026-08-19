import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import javax.swing.BoxLayout;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

public final class S_Pickpocket extends Script
    implements ActionListener, ItemListener {
    
    private static final int ID_GNOMEBALL = 981;
    private static final int SKILL_HITS = 3;
    private static final int SKILL_THIEV = 17;
    
    private static final int BANK_NEVER = 0;
    private static final int BANK_FOOD = 1;
    
    private static final int COINS = 10;
	private static final int BLOODS = 619;
    
    private static final HashMap<String, int[]> map_npcs;
    private static final HashMap<String, int[]> map_food;
    
    static {
        map_npcs = new HashMap<String, int[]>();
        map_npcs.put("Men (level 1)", new int[] { 11, 72, 318 });
        map_npcs.put("Farmers (level 10)", new int[] { 63, 319 });
        map_npcs.put("Warriors (level 25)", new int[] { 86, 320 });
        map_npcs.put("Rogues (level 32)", new int[] { 342 });
        map_npcs.put("Guards (level 40)", new int[] { 65, 321, 376 });
        map_npcs.put("Knights (level 55)", new int[] { 322 });
        map_npcs.put("Paladins (level 70)", new int[] { 323 });
        map_npcs.put("Gnomes (level 75)", new int[] { 593, 592, 591 });
        map_npcs.put("Heroes (level 80)", new int[] { 324 });
        
        map_food = new HashMap<String, int[]>();
        map_food.put("Shrimp", new int[] { 350 });
        map_food.put("Anchovy", new int[] { 352 });
        map_food.put("Sardine", new int[] { 355 });
        map_food.put("Salmon", new int[] { 357 });
        map_food.put("Trout", new int[] { 359 });
        map_food.put("Herring", new int[] { 362 });
        map_food.put("Pike", new int[] { 364 });
        map_food.put("Tuna", new int[] { 367 });
        map_food.put("Swordfish", new int[] { 370 });
        map_food.put("Lobster", new int[] { 373 });
        map_food.put("Shark", new int[] { 546 });
        map_food.put("Manta ray", new int[] { 1191 });
        map_food.put("Sea turtle", new int[] { 1193 });
        map_food.put("Kebab", new int[] { 210 });
        map_food.put("Cake", new int[] { 330, 333, 335 });
        map_food.put("Chocolate cake", new int[] { 332, 334, 336 });
        map_food.put("Meat pizza", new int[] { 326, 328 });
        map_food.put("Anchovy pizza", new int[] { 327, 329 });
    }
    
    private static final int[] ids_bank = {
    	COINS , BLOODS , 41, 38, 619, 152, 142, 612, 161
    };
    
    private int[] ids_npcs;
    private int[] ids_food;
    private int sleep_at;
    private long move_time;
    private long bank_time;
    private boolean init_path;
    private int eat_at;
    private int withdraw_food;
    
    private int[] bank_counts;
    
    private Frame frame;
    private Choice ch_fm;
    private Choice ch_bank;
    private Choice ch_npc;
    private Choice ch_food;
    private TextField tf_food;
    private TextField tf_eat;
    private TextField tf_sleep;
    
    private PathWalker pw;
    private PathWalker.Path to_bank;
    private PathWalker.Path from_bank;
    private PathWalker.Location bank;
    
    private long start_time;
    private long total_success;
    private long cur_success;
    private long total_fails;
    private long cur_fails;
    private int levels_gained;
    private int total_withdraw;
    
    private int start_x;
    private int start_y;
    
    private long lvl_time;

    public S_Pickpocket(Extension ex) {
        super(ex);
        pw = new PathWalker(ex);
    }
    
    public static void main(String[] argv) {
        S_Pickpocket p = new S_Pickpocket(null);
        p.init(null);
    }
    
    @Override
    public void init(String params) {
        total_withdraw  = 0;
        levels_gained = 0;
        cur_success = 0;
        cur_fails = 0;
        move_time = -1L;
        bank_time = -1L;
        start_time = -1L;
        lvl_time = -1L;
        init_path = false;
        bank_counts = new int[ids_bank.length];
        if (frame == null) {
            ch_bank = new Choice();
            ch_bank.addItemListener(this);
            ch_bank.add("Never");
            ch_bank.add("For food");
            ch_bank.add("For food or full bag");
            
            ch_fm = new Choice();
            for (String str : FIGHTMODES) {
                ch_fm.add(str);
            }
            
            Iterator<String> sit;
            
            ch_npc = new Choice();
            sit = map_npcs.keySet().iterator();
            while (sit.hasNext()) {
                ch_npc.add(sit.next());
            }

            ch_food = new Choice();
            sit = map_food.keySet().iterator();
            while (sit.hasNext()) {
                ch_food.add(sit.next());
            }
            
            tf_food = new TextField("20");
            tf_food.setEnabled(false);
            
            Panel pInput = new Panel(new GridLayout(0, 2, 2, 2));
            pInput.add(new Label("NPC:"));
            pInput.add(ch_npc);
            pInput.add(new Label("Combat style:"));
            pInput.add(ch_fm);
            pInput.add(new Label("Banking mode:"));
            pInput.add(ch_bank);
            pInput.add(new Label("Withdraw food:"));
            pInput.add(ch_food);
            pInput.add(new Label("Withdraw food count:"));
            pInput.add(tf_food);
            pInput.add(new Label("Eat at HP level:"));
            pInput.add(tf_eat = new TextField("10"));
            pInput.add(new Label("Sleep at fatigue %:"));
            pInput.add(tf_sleep = new TextField("95"));
            
            ch_food.setEnabled(false);
            
            Button button;
            Panel pButtons = new Panel();
            button = new Button("OK");
            button.addActionListener(this);
            pButtons.add(button);
            button = new Button("Cancel");
            button.addActionListener(this);
            pButtons.add(button);
            
            frame = new Frame(getClass().getSimpleName());
            frame.setLayout(new BoxLayout(frame, BoxLayout.Y_AXIS));
            frame.addWindowListener(
                new StandardCloseHandler(frame, StandardCloseHandler.HIDE)
            );
            frame.setIconImages(Constants.ICONS);
            frame.add(pInput, BorderLayout.NORTH);
            frame.add(new Label(
                "Banking is supported from most ground level locations.",
                Label.CENTER
            ), BorderLayout.CENTER);
            frame.add(new Label(
                "Start this script at the NPCs.",
                Label.CENTER
            ), BorderLayout.SOUTH);
            frame.add(pButtons, BorderLayout.SOUTH);
            frame.setResizable(false);
            frame.pack();
        }
        frame.setLocationRelativeTo(null);
        frame.toFront();
        frame.requestFocus();
        frame.setVisible(true);
    }

    @Override
    public int main() {
        if (lvl_time != -1L) {
            if (System.currentTimeMillis() >= lvl_time) {
                System.out.print("Congrats on level ");
                System.out.print(getLevel(SKILL_THIEV));
                System.out.println(" thieving!");
                lvl_time = -1L;
            }
        }
        if (start_time == -1L) {
            start_time = System.currentTimeMillis();
        }
        if (inCombat()) {
            int ideal_fm = ch_fm.getSelectedIndex();
            if (getFightMode() != ideal_fm) {
                setFightMode(ideal_fm);
            } else {
                walkTo(getX(), getY());
            }
            return random(400, 600);
        }
        int bank_type = ch_bank.getSelectedIndex();
        if (getCurrentLevel(SKILL_HITS) <= eat_at) {
            int slot = getFoodSlot();
            if (slot != -1) {
                useItem(slot);
                return random(800, 1000);
            }
            if (bank_type == BANK_NEVER) {
                System.out.println("No food!");
                return random(500, 1000);
            }
        }
        if (getFatigue() >= sleep_at) {
            useSleepingBag();
            return random(2000, 3000);
        }
        if (move_time != -1L && System.currentTimeMillis() >= move_time) {
            System.out.println("Moving for 5 min timer");
            _walkApprox(getX(), getY());
            move_time = -1L;
            return random(1500, 2500);
        }
        int ball = getInventoryIndex(ID_GNOMEBALL);
        if (ball != -1) {
            dropItem(ball);
            return random(1000, 1200);
        }
        if (bank_type != BANK_NEVER) {
            if (!init_path) {
                pw.init(null);
                start_x = getX();
                start_y = getY();
                bank = pw.getNearestBank(start_x, start_y);
                System.out.println("Nearest bank: " + bank.name);
                to_bank = pw.calcPath(start_x, start_y, bank.x, bank.y);
                if (to_bank == null) {
                    stopScript(); return 0;
                }
                from_bank = pw.calcPath(bank.x, bank.y, start_x, start_y);
                if (from_bank == null) {
                    stopScript(); return 0;
                }
                init_path = true;
            }
            if (isQuestMenu()) {
                answer(0);
                bank_time = System.currentTimeMillis();
                return random(2000, 3000);
            }
            if (isBanking()) {
                int array_sz = ids_bank.length;
                for (int i = 0; i < array_sz; ++i) {
                    int count = getInventoryCount(ids_bank[i]);
                    if (count > 0) {
                        deposit(ids_bank[i], count);
                        bank_counts[i] += count;
                        return random(1000, 2000);
                    }
                }
                int food_count = getInventoryCount(ids_food);
                if (food_count < withdraw_food) {
                    for (int id : ids_food) {
                        int bank_count = bankCount(id);
                        if (bank_count <= 0) continue;
                        int w = withdraw_food - food_count;
                        if (w > bank_count) w = bank_count;
                        total_withdraw += w;
                        withdraw(id, w);
                        return random(1000, 2000);
                    }
                    System.out.println("ERROR: Out of food!");
                    stopScript(); setAutoLogin(false);
                    return 0;
                }
                pw.setPath(from_bank);
                closeBank();
                return random(1000, 2000);
            } else if (bank_time != -1L) {
                if (System.currentTimeMillis() >= (bank_time + 8000L)) {
                    bank_time = -1L;
                }
                return random(300, 400);
            }
            if (pw.walkPath()) return 0;
            if (shouldBank()) {
                if (!isAtApproxCoords(bank.x, bank.y, 20) || startCloser()) {
                    pw.setPath(to_bank);
                    return random(600, 800);
                }
                int[] banker = getNpcByIdNotTalk(BANKERS);
                if (banker[0] != -1) {
                    if (distanceTo(banker[1], banker[2]) > 5) {
                        if (!isWalking()) {
                            _walkApprox(banker[1], banker[2]);
                        }
                        return random(1500, 2500);
                    }
                    talkToNpc(banker[0]);
                    return random(3000, 3500);
                }
                return random(100, 700);
            }
        }
        int[] gold = getItemById(COINS);
        if (gold[1] == getX() && gold[2] == getY()) {
        	pickupItem(gold[0], gold[1], gold[2]);
            return random(600, 800);
        }
		int[] bloods = getItemById(BLOODS);
        if (bloods[1] == getX() && bloods[2] == getY()) {
        	pickupItem(bloods[0], bloods[1], bloods[2]);
            return random(600, 800);
        }
        int[] npc = _getNpcReachable(ids_npcs);
        if (npc[0] != -1) {
            if (distanceTo(npc[1], npc[2]) > 5) {
                _walkApprox(npc[1], npc[2]);
                return random(1500, 2500);
            }
            thieveNpc(npc[0]);
            return random(600, 800);
        }
        return random(100, 700);
    }
    
    private int[] _getNpcReachable(int... ids) {
        int[] npc = new int[] {
            -1, -1, -1
        };
        int max_dist = Integer.MAX_VALUE;
        int count = countNpcs();
        for (int i = 0; i < count; i++) {
            if (isNpcInCombat(i)) continue;
            if (inArray(ids, getNpcId(i))) {
                int x = getNpcX(i);
                int y = getNpcY(i);
                if (!isReachable(x, y)) continue;
                int dist = distanceTo(x, y, getX(), getY());
                if (dist < max_dist) {
                    npc[0] = i;
                    npc[1] = x;
                    npc[2] = y;
                    max_dist = dist;
                }
            }
        }
        return npc;
    }

    @Override
    public void paint() {
        final int orangey = 0xFFD900;
        final int white = 0xFFFFFF;
        int x = 85;
        int y = 25;
        drawString("Stormy's Pickpocket", x, y, 1, orangey);
        y += 15;
        drawString("Runtime: " + _getRuntime(), x, y, 1, white);
        y += 15;
        drawString("Stats for current level (" +
                levels_gained + " gained):",
                x, y, 1, orangey);
        y += 15;
        x += 10;
        drawString("Successful attempts: " + cur_success, x, y, 1, white);
        y += 15;
        drawString("Failed attempts: " + cur_fails, x, y, 1, white);
        y += 15;
        drawString("Fail rate: " + (float)
                ((double) cur_fails / (double) cur_success),
                x, y, 1, white);
        if (levels_gained > 0) {
            y += 15;
            x -= 10;
            drawString("Total:", x, y, 1, orangey);
            x += 10;
            y += 15;
            drawString("Successful attempts: " + total_success,
                    x, y, 1, white);
            y += 15;
            drawString("Failed attempts: " + total_fails, x, y, 1, white);
        }
        if (ch_bank.getSelectedIndex() == BANK_NEVER) return;
        y += 15;
        x -= 10;
        drawString("Banked items:", x, y, 1, orangey);
        x += 10;
        y += 15;
        if (withdraw_food != 0) {
            drawString(total_withdraw + " food withdrawn (" +
                (total_withdraw / withdraw_food) + " trips)", 
                x, y, 1, white);
            y += 15;
        }
        int len = ids_bank.length;
        for (int i = 0; i < len; ++i) {
            int count = bank_counts[i];
            if (count <= 0) continue;
            drawString(count + " " + getItemNameId(ids_bank[i]),
                    x, y, 1, white);
            y += 15;
        }
    }
    
    private String _getRuntime() {
        long secs = ((System.currentTimeMillis() - start_time) / 1000);
        if (secs >= 3600) {
            return (secs / 3600) + " hours, " +
                    ((secs % 3600) / 60) + " mins, " +
                    (secs % 60) + " secs.";
        }
        if (secs >= 60) {
            return secs / 60 + " mins, " +
                    (secs % 60) + " secs.";
        }
        return secs + " secs.";
    }
    
    @Override
    public void onServerMessage(String str) {
    	str = str.toLowerCase(Locale.ENGLISH);
        if (str.contains("standing here")) {
            move_time = (System.currentTimeMillis() + random(1500, 1800));
        } else if (str.contains("fail")) {
            ++cur_fails;
            ++total_fails;
        } else if (str.contains("you pick")) {
            ++cur_success;
            ++total_success;
        } else if (str.contains("advanced")) {
            System.out.println("You just advanced a level.");
            System.out.print("Runtime: ");
            System.out.println(_getRuntime());
            System.out.print("Old success count: ");
            System.out.println(cur_success);
            System.out.print("Old fail count: ");
            System.out.println(cur_fails);
            System.out.print("Old fail rate: ");
            System.out.println((double) cur_fails / (double) cur_success);
            System.out.print("Fail total: ");
            System.out.println(total_fails);
            System.out.print("Success total: ");
            System.out.println(total_success);
            lvl_time = System.currentTimeMillis() + 2000L;
            cur_fails = 0;
            cur_success = 0;
            ++levels_gained;
        }
    }
    
    private boolean startCloser() {
        int dist_bank = distanceTo(bank.x, bank.y);
        int dist_start = distanceTo(start_x, start_y);
        return (dist_start < dist_bank);
    }
    
    private boolean shouldBank() {
        switch (ch_bank.getSelectedIndex()) {
            case BANK_NEVER:
                return false;
            case BANK_FOOD:
                return getFoodSlot() == -1;
            default:
                return getFoodSlot() == -1 ||
                       getInventoryCount() == MAX_INV_SIZE;
        }
    }
    
    private void _walkApprox(int x, int y) {
        int dx, dy;
        int loop = 0;
        do {
            dx = x + random(-1, 1);
            dy = y + random(-1, 1);
            if ((++loop) > 100) return;
        } while (!isReachable(dx, dy));
        walkTo(dx, dy);
    }
    
    private int getFoodSlot() {
        int count = getInventoryCount();
        for (int i = 0; i < count; i++) {
            if (getItemCommand(i).toLowerCase(Locale.ENGLISH).equals("eat")) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("OK")) {
            eat_at = Integer.parseInt(tf_eat.getText());
            sleep_at = Integer.parseInt(tf_sleep.getText());
            withdraw_food = Integer.parseInt(tf_food.getText());
            ids_npcs = map_npcs.get(ch_npc.getSelectedItem());
            ids_food = map_food.get(ch_food.getSelectedItem());
        }
        frame.setVisible(false);
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        boolean enabled = ch_bank.getSelectedIndex() != BANK_NEVER;
        ch_food.setEnabled(enabled);
        tf_food.setEnabled(enabled);
    }
}
