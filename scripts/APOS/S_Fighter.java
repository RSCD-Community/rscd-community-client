import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

public final class S_Fighter extends Script
        implements ActionListener {
    
    private static final int SKILL_HITS = 3;
    
    private static final int MELEE = 0;
    private static final int RANGED = 1;
    
    private Frame frame;
    private TextField tf_npcs;
    private TextField tf_eat;
    private TextField tf_range;
    private TextField tf_pickup;
    private TextField tf_sleep;
    private Choice ch_fm;
    private Choice ch_spell;
    private Checkbox cb_under;
    private Checkbox cb_bones;

    private int start_x;
    private int start_y;
    private boolean init;

    private int[] npc_ids;
    private int[] item_ids;
    private int eat_at;
    private int sleep_at;
    private int range;
    
    private PathWalker pw;
    private boolean pw_init;

    public S_Fighter(Extension ex) {
        super(ex);
        pw = new PathWalker(ex);
    }
    
    public static void main(String[] argv) {
        new S_Fighter(null).init(null);
    }

    @Override
    public void init(String params) {
        pw_init = false;
        init = false;
        if (frame == null) {
            ch_fm = new Choice();
            int len = FIGHTMODES.length;
            for (int i = 0; i < len; ++i) {
                ch_fm.add(FIGHTMODES[i]);
            }
            
            ch_spell = new Choice();
            ch_spell.add("Melee");
            ch_spell.add("Ranged");
            len = SPELL.length;
            for (int i = 0; i < len; ++i) {
                ch_spell.add(SPELL[i]);
            }
            
            Panel pInput = new Panel();
            pInput.setLayout(new GridLayout(0, 2, 0, 2));
            
            pInput.add(new Label("NPC ids (1,2,3...):"));
            pInput.add(tf_npcs = new TextField());
            
            pInput.add(new Label("Combat style:"));
            pInput.add(ch_fm);
            
            pInput.add(new Label("Walkback range:"));
            pInput.add(tf_range = new TextField("30"));
            
            pInput.add(new Label("Item ids (1,2,3...):"));
            pInput.add(tf_pickup = new TextField());
            
            pInput.add(new Label("Spell/combat type:"));
            pInput.add(ch_spell);
            
            pInput.add(new Label("Eat at HP level:"));
            pInput.add(tf_eat = new TextField("10"));
            
            pInput.add(new Label("Sleep at fatigue %:"));
            pInput.add(tf_sleep = new TextField("95"));
            
            Panel cbPanel = new Panel();
            cbPanel.setLayout(new GridLayout(0, 1));
            cbPanel.add(cb_under
                    = new Checkbox("Only pick up items directly underneath the player", true));
            cbPanel.add(cb_bones = new Checkbox("Bury bones"));
            
            Panel buttonPanel = new Panel();
            Button ok = new Button("OK");
            ok.addActionListener(this);
            buttonPanel.add(ok);
            Button cancel = new Button("Cancel");
            cancel.addActionListener(this);
            buttonPanel.add(cancel);
            
            frame = new Frame(getClass().getSimpleName());
            frame.setIconImages(Constants.ICONS);
            frame.addWindowListener(
                new StandardCloseHandler(frame, StandardCloseHandler.HIDE)
            );
            frame.add(pInput, BorderLayout.NORTH);
            frame.add(cbPanel, BorderLayout.CENTER);
            frame.add(buttonPanel, BorderLayout.SOUTH);
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
        if (!init) {
            start_x = getX();
            start_y = getY();
            init = true;
        }
        int ideal_fm = ch_fm.getSelectedIndex();
        if (getFightMode() != ideal_fm) {
            setFightMode(ideal_fm);
            return random(400, 600);
        }
        if (inCombat()) {
            pw.resetWait();
            int type = ch_spell.getSelectedIndex();
            if (type == RANGED || getCurrentLevel(SKILL_HITS) <= eat_at) {
                walkTo(getX(), getY());
                return random(400, 600);
            }
            // magic in combat
            if (type > RANGED) return attack();
            return random(250, 450);
        }
        if (getFatigue() >= sleep_at) {
            useSleepingBag();
            return random(1000, 1500);
        }
        if (getCurrentLevel(SKILL_HITS) <= eat_at) {
            int count = getInventoryCount();
            for (int i = 0; i < count; i++) {
                if (getItemCommand(i).toLowerCase(Locale.ENGLISH).equals("eat")) {
                    useItem(i);
                    return random(800, 1000);
                }
            }
            System.out.println("No food!");
            return random(500, 1000);
        }
        if (pw_init) {
            if (pw.walkPath()) return 0;
        }
        if (cb_bones.getState()) {
            int count = getInventoryCount();
            for (int i = 0; i < count; i++) {
                if (getItemCommand(i).toLowerCase(Locale.ENGLISH).equals("bury")) {
                    useItem(i);
                    return random(800, 1000);
                }
            }
        }
        if (!isAtApproxCoords(start_x, start_y, range)) {
        	if (range <= 10 && isReachable(start_x, start_y)) {
            	System.out.println("Going back");
                walkTo(start_x, start_y);
                return random(1000, 2000);
            }
            if (!pw_init) {
                pw.init(null);
                pw_init = true;
            }
            PathWalker.Path p = pw.calcPath(start_x, start_y);
            if (p != null) {
            	System.out.println("Going back");
                pw.setPath(p);
                return random(600, 800);
            } else {
                System.out.println("Error calculating path, trying to move");
                _walkApprox(getX(), getY(), 10);
                return random(1000, 2000);
            }
        }
        if (!cb_under.getState()) {
            int[] item = _getReachableItem(item_ids);
            if (shouldTake(item[0], item[1], item[2])) {
                if (distanceTo(item[1], item[2]) > 5) {
                    _walkApprox(item[1], item[2], 1);
                    return random(1000, 2000);
                }
                pickupItem(item[0], item[1], item[2]);
                return random(1000, 1200);
            }
        } else {
            int[] item = _getItemFast(item_ids);
            if (shouldTake(item[0], item[1], item[2])) {
                pickupItem(item[0], item[1], item[2]);
                return random(1000, 1200);
            }
        }
        return attack();
    }

    private boolean shouldTake(int id, int x, int y) {
        if (id == -1) return false;
        if (getInventoryCount() == MAX_INV_SIZE) {
            return isItemStackableId(id) && getInventoryIndex(id) != -1;
        }
        return true;
    }
    
    private int attack() {
        int sp_type = ch_spell.getSelectedIndex();
        if (sp_type != MELEE) {
            int[] npc = getAllNpcById(npc_ids);
            if (npc[0] != -1) {
                if (sp_type == RANGED) {
                    attackNpc(npc[0]);
                    return random(1500, 2500);
                } else {
                    int spell = sp_type - 2;
                    if (canCastSpell(spell)) {
                        mageNpc(npc[0], spell);
                    } else {
                        System.out.println("Can't cast spell!");
                        stopScript(); setAutoLogin(false);
                    }
                    return random(600, 1000);
                }
            }
        } else {
            int[] npc = _getReachableNpc(npc_ids);
            if (npc[0] != -1) {
                if (distanceTo(npc[1], npc[2]) > 5) {
                    _walkApprox(npc[1], npc[2], 1);
                    return random(1000, 2000);
                }
                attackNpc(npc[0]);
                return random(600, 1000);
            }
        }
        return random(100, 700);
    }
    
    private int[] _getReachableItem(int... ids) {
        int[] item = new int[] {
            -1, -1, -1
        };
        int count = getGroundItemCount();
        int max_dist = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int id = getGroundItemId(i);
            if (inArray(ids, id)) {
                int x = getItemX(i);
                int y = getItemY(i);
                if (!isReachable(x, y)) continue;
                if (distanceTo(x, y, start_x, start_y) > range) {
                    continue;
                }
                int dist = distanceTo(x, y, getX(), getY());
                if (dist < max_dist) {
                    item[0] = id;
                    item[1] = x;
                    item[2] = y;
                    max_dist = dist;
                }
            }
        }
        return item;
    }
    
    private int[] _getItemFast(int... ids) {
        int count = getGroundItemCount();
        int x = getX();
        int y = getY();
        for (int i = 0; i < count; ++i) {
            if (getItemX(i) != x || getItemY(i) != y) {
                continue;
            }
            int id = getGroundItemId(i);
            if (inArray(ids, id)) {
                return new int[] { id, x, y };
            }
        }
        return new int[] { -1, -1, -1 };
    }

    private int[] _getReachableNpc(int... ids) {
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
                if (distanceTo(x, y, start_x, start_y) > range) {
                    continue;
                }
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

    private void _walkApprox(int x, int y, int range) {
        int dx, dy;
        int loop = 0;
        do {
            dx = x + random(-range, range);
            dy = y + random(-range, range);
            if ((++loop) > 100) return;
        } while (!isReachable(dx, dy));
        walkTo(dx, dy);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (event.getActionCommand().equals("OK")) {
            try {
                String[] array = tf_npcs.getText().trim().split(",");
                int array_sz = array.length;
                npc_ids = new int[array_sz];
                for (int i = 0; i < array_sz; i++) {
                    npc_ids[i] = Integer.parseInt(array[i]);
                }
            } catch (Throwable t) {
                System.out.println("Couldn't parse npc ids");
                npc_ids = new int[0];
            }
            try {
                String[] array = tf_pickup.getText().trim().split(",");
                int array_sz = array.length;
                item_ids = new int[array_sz];
                for (int i = 0; i < array_sz; i++) {
                    item_ids[i] = Integer.parseInt(array[i]);
                }
            } catch (Throwable t) {
                System.out.println("Couldn't parse item ids");
                item_ids = new int[0];
            }
            try {
                eat_at = Integer.parseInt(tf_eat.getText().trim());
            } catch (Throwable t) {
                System.out.println("Couldn't parse eat at value");
            }
            try {
                range = Integer.parseInt(tf_range.getText().trim());
            } catch (Throwable t) {
                System.out.println("Couldn't parse range value");
            }
            try {
                sleep_at = Integer.parseInt(tf_sleep.getText().trim());
            } catch (Throwable t) {
                System.out.println("Couldn't parse sleep value");
            }
        }
        frame.setVisible(false);
    }
    
    @Override
    public void onServerMessage(String str) {
        if (str.contains("out of ammo")) {
            stopScript();
        }
    }
}