import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

public final class S_Certer extends Script
        implements ActionListener {

    private static final int ID_GNOMEBALL = 981;
    /** The last confirmed choice, so an unattended restart needs no window. */
    private static final java.io.File CONFIG =
        new java.io.File(System.getProperty("user.home"), ".rscd-s_certer.cfg");
    private CertOption item;
    private boolean uncert;
    private Frame frame;
    private Choice choice;
    private long bank_time;
    private long menu_time;
    /* When the amount was picked, and how many of the goods being handed in
       were held at that moment. -1 when no exchange is in flight. */
    private long trade_time = -1L;
    private int trade_held;

    public S_Certer(Extension ex) {
        super(ex);
    }

    @Override
    public void init(String params) {
        if (frame == null) {
            Frame frame = new Frame(getClass().getSimpleName());
            frame.setIconImages(Constants.ICONS);
            frame.addWindowListener(
                new StandardCloseHandler(frame, StandardCloseHandler.HIDE)
            );
            
            Panel pChoice = new Panel(new GridLayout(0, 1));
            choice = new Choice();
            choice.add("Sharks [C]");
            choice.add("Raw sharks [C]");
            choice.add("Yew logs [A]");
            choice.add("Maple logs [A]");
            choice.add("Willow logs [A]");
            choice.add("Restore prayer potions [Y]");
            choice.add("Super attack potions [Y]");
            choice.add("Super defense potions [Y]");
            choice.add("Super strength potions [Y]");
            choice.add("Dragon bones [Y]");
            choice.add("Limpwurt roots [Y]");
            choice.add("Iron bars [D/Z]");
            choice.add("Steel bars [D/Z]");
            choice.add("Mithril bars [D/Z]");
            choice.add("Gold bars [D/Z]");
            choice.add("Silver bars [D/Z]");
            choice.add("Iron ores [D/Z]");
            choice.add("Coal ores [D/Z]");
            choice.add("Mithril ores [D/Z]");
            choice.add("Gold ores [D/Z]");
            choice.add("Silver ores [D/Z]");
            choice.add("Lobsters [D]");
            choice.add("Raw lobsters [D]");
            choice.add("Swordfish [D]");
            choice.add("Raw swordfish [D]");
            pChoice.add(choice);
            pChoice.add(new Label("A: Ardougne"));
            pChoice.add(new Label("C: Catherby"));
            pChoice.add(new Label("D: Draynor"));
            pChoice.add(new Label("Z: Zanaris/Lost City"));
            pChoice.add(new Label("Y: Yanille"));
            
            Panel pButtons = new Panel();
            
            Button button = new Button("Cert");
            button.addActionListener(this);
            pButtons.add(button);
            button = new Button("Uncert");
            button.addActionListener(this);
            pButtons.add(button);
            
            frame.add(pChoice, BorderLayout.CENTER);
            frame.add(pButtons, BorderLayout.SOUTH);
            frame.setResizable(false);
            frame.pack();
            this.frame = frame;
        }
        loadConfig();
        /* A person starting the script always gets the window; an unattended
           start (login restart, the panel's Play) has nobody to click it and
           runs on the saved choice -- unless there is none, in which case the
           window is the only way to pick one and appears regardless. */
        if (!isUnattended() || item == null) {
            frame.setLocationRelativeTo(null);
            frame.toFront();
            frame.requestFocus();
            frame.setVisible(true);
        }
        bank_time = -1L;
        menu_time = -1L;
        trade_time = -1L;
    }

    private void saveConfig(int index) {
        try {
            java.io.PrintWriter out = new java.io.PrintWriter(CONFIG, "UTF-8");
            out.println(index);
            out.println(uncert ? "uncert" : "cert");
            out.close();
        } catch (Exception e) {
            System.out.println("[S_Certer] Could not save the configuration: " + e);
        }
    }

    private void loadConfig() {
        if (!CONFIG.isFile()) {
            return;
        }
        try {
            java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    new java.io.FileInputStream(CONFIG), "UTF-8"));
            int index = Integer.parseInt(in.readLine().trim());
            String mode = in.readLine().trim();
            in.close();
            if (index < 0 || index >= choice.getItemCount()) {
                return;
            }
            choice.select(index);
            uncert = mode.equals("uncert");
            applyChoice(index);
        } catch (Exception e) {
            System.out.println("[S_Certer] Could not read the configuration: " + e);
        }
    }

    @Override
    public int main() {
        /* The window does not block: init() shows it and returns, and this
           runs at once. Without a choice there is nothing to cert, and a
           null item here would throw -- which the client answers by
           restarting the script, which shows the window again, forever. */
        if (item == null) {
            return 1000;
        }
        if (isBanking()) {
            bank_time = -1L;
            if (uncert) {
                int count = getInventoryCount(item.getItemId());
                int count_c = getInventoryCount(item.getCertId());
                if (count == 0) {
                    if (count_c < 5) {
                        System.out.println(
                                "Out of certificates, stopping...");
                        stopScript();
                        setAutoLogin(false);
                        return 0;
                    }
                    closeBank();
                    return random(1000, 1500);
                }
                deposit(item.getItemId(), count);
                return random(1000, 1500);
            }
            if (hasInventoryItem(item.getItemId())) {
                closeBank();
                return random(1000, 1500);
            }
            if (bankCount(item.getItemId()) < 25) {
                System.out.println("Out of items, stopping...");
                stopScript();
                setAutoLogin(false);
                return 0;
            }
            withdraw(item.getItemId(), 25);
            return random(1000, 1500);
        } else if (bank_time != -1L) {
            if (System.currentTimeMillis() >= (bank_time + 8000L)) {
                bank_time = -1L;
            }
            return random(300, 400);
        }
        /* The exchange is not instant. After the amount is picked the certer
           echoes the choice as a spoken line, a second and a half later the
           goods arrive, and only then is the conversation over. The original
           script waited a flat 1.5 s and then looked at its pack: on a slow
           tick the ore had not landed yet, so it saw "nothing to bank" and
           talked to the certer again -- a second exchange, 25 more ore into a
           pack that now had three free slots, and 22 on the ground to vanish.
           Over a 64-hour run that was about a quarter of the ore. So wait
           for the pack to actually change, and only give up after 8 s. */
        if (trade_time != -1L) {
            int held = getInventoryCount(
                    uncert ? item.getCertId() : item.getItemId());
            if (held != trade_held) {
                trade_time = -1L;
            } else if (System.currentTimeMillis() < trade_time + 8000L) {
                return random(300, 400);
            } else {
                System.out.println("The exchange did not go through - trying again.");
                trade_time = -1L;
            }
        }
        if (menu_time != -1) {
            if (!isQuestMenu()) {
                if (System.currentTimeMillis() >= (menu_time + 8000L)) {
                    menu_time = -1L;
                }
                return random(300, 400);
            } else {
                menu_time = -1L;
            }
        }
        int ball = getInventoryIndex(ID_GNOMEBALL);
        if (ball != -1) {
            dropItem(ball);
            return random(2000, 3000);
        }
        if (getY() > 3000) {
            return doLostCity();
        }
        if (getX() < 243 && getX() > 215) {
            return doDraynor();
        }
        if (getX() < 444 && getX() > 425) {
            return doCatherby();
        }
        if (getY() > 741 && getX() < 608 && getX() > 580) {
            return doYanille();
        }
        if (getX() < 589 && getX() > 576) {
            return doArdougne();
        }
        System.out.println("Not in a supported location!");
        return 1000;
    }

    private int doDraynor() {
        if (getX() < 224) {
            if (isQuestMenu()) {
                answer(0);
                bank_time = System.currentTimeMillis();
                return random(2000, 3000);
            }
            if (shouldBank()) {
                if (getY() < 634) {
                    int[] bank_doors = getObjectById(64);
                    if (bank_doors[0] != -1) {
                        atObject(bank_doors[1], bank_doors[2]);
                        return random(1000, 1500);
                    }
                }
                int[] banker = getNpcByIdNotTalk(BANKERS);
                if (banker[0] != -1) {
                    talkToNpc(banker[0]);
                    return random(1000, 2000);
                }
                return random(100, 700);
            }
            if (getY() > 633) {
                int[] bank_doors = getObjectById(64);
                if (bank_doors[0] != -1) {
                    atObject(bank_doors[1], bank_doors[2]);
                    return random(1000, 1500);
                }
                walkTo(225 + random(0, 2), 631 + random(0, 1));
                return random(1000, 1500);
            }
        }
        if (isQuestMenu()) {
            return stdopt();
        }
        if (shouldBank()) {
            walkTo(220 - random(0, 1), 633);
            return random(1000, 1500);
        }
        int[] certer = getNpcByIdNotTalk(item.getCerterIds());
        if (certer[0] != -1) {
            talkToNpc(certer[0]);
            return random(1000, 2000);
        }
        return random(100, 700);
    }

    private int doLostCity() {
        if (getY() < 3530) {
            if (isQuestMenu()) {
                answer(0);
                bank_time = System.currentTimeMillis();
                return random(2000, 3000);
            }
            if (shouldBank()) {
                if (getX() < 172) {
                    int[] bank_gate = getObjectById(57);
                    if (bank_gate[0] != -1) {
                        atObject(bank_gate[1], bank_gate[2]);
                        return random(1000, 1500);
                    }
                }
                int[] banker = getNpcByIdNotTalk(BANKERS);
                if (banker[0] != -1) {
                    talkToNpc(banker[0]);
                    return random(1000, 2000);
                }
                return random(100, 700);
            }
            if (getX() > 171) {
                int[] bank_gate = getObjectById(57);
                if (bank_gate[0] != -1) {
                    atObject(bank_gate[1], bank_gate[2]);
                    return random(1000, 1500);
                }
            }
            walkTo(174 + random(0, 3), 3531 + random(0, 3));
            return random(1000, 1500);
        }
        if (isQuestMenu()) {
            return stdopt();
        }
        if (shouldBank()) {
            walkTo(171, 3528 - random(0, 1));
            return random(1000, 1500);
        }
        int[] certer = getNpcByIdNotTalk(item.getCerterIds());
        if (certer[0] != -1) {
            talkToNpc(certer[0]);
            return random(1000, 2000);
        }
        return random(100, 700);
    }

    private int doCatherby() {
        if (getX() > 436) {
            if (isQuestMenu()) {
                answer(0);
                bank_time = System.currentTimeMillis();
                return random(2000, 3000);
            }
            if (shouldBank()) {
                if (getY() > 496) {
                    int[] bank_doors = getObjectById(64);
                    if (bank_doors[0] != -1) {
                        atObject(bank_doors[1], bank_doors[2]);
                        return random(1000, 1500);
                    }
                }
                int[] banker = getNpcByIdNotTalk(BANKERS);
                if (banker[0] != -1) {
                    talkToNpc(banker[0]);
                    return random(1000, 2000);
                }
                return random(100, 700);
            }
            if (getY() < 497) {
                int[] bank_doors = getObjectById(64);
                if (bank_doors[0] != -1) {
                    atObject(bank_doors[1], bank_doors[2]);
                    return random(1000, 1500);
                }
            }
            walkTo(431 - random(0, 1), 489 - random(0, 3));
            return random(1000, 1500);
        }
        if (isQuestMenu()) {
            return stdopt();
        }
        if (getWallObjectIdFromCoords(427, 485) == 2) {
            atWallObject(427, 485);
            return random(1000, 1500);
        }
        if (shouldBank()) {
            walkTo(440 - random(0, 1), 497);
            return random(1000, 1500);
        }
        int[] certer = getNpcByIdNotTalk(item.getCerterIds());
        if (certer[0] != -1) {
            talkToNpc(certer[0]);
            return random(1000, 2000);
        }
        return random(100, 700);
    }

    private int doArdougne() {
        if (getY() > 571) {
            if (isQuestMenu()) {
                answer(0);
                bank_time = System.currentTimeMillis();
                return random(2000, 3000);
            }
            if (shouldBank()) {
                int[] banker = getNpcByIdNotTalk(BANKERS);
                if (banker[0] != -1) {
                    talkToNpc(banker[0]);
                    return random(1000, 2000);
                }
                return random(100, 700);
            }
            walkTo(587 - random(0, 1), 564 - random(0, 1));
            return random(1000, 1500);
        }
        if (isQuestMenu()) {
            return stdopt();
        }
        if (getWallObjectIdFromCoords(586, 563) == 2) {
            atWallObject(586, 563);
            return random(1000, 1500);
        }
        if (shouldBank()) {
            walkTo(581 - random(0, 1), 573 - random(0, 1));
            return random(1000, 1500);
        }
        int[] certer = getNpcByIdNotTalk(item.getCerterIds());
        if (certer[0] != -1) {
            talkToNpc(certer[0]);
            return random(1000, 2000);
        }
        return random(100, 700);
    }

    private int doYanille() {
        if (getX() < 592) {
            if (isQuestMenu()) {
                answer(0);
                bank_time = System.currentTimeMillis();
                return random(2000, 3000);
            }
            if (shouldBank()) {
                int[] banker = getNpcByIdNotTalk(BANKERS);
                if (banker[0] != -1) {
                    talkToNpc(banker[0]);
                    return random(1000, 2000);
                }
                return random(100, 700);
            }
            walkTo(601 + random(0, 2), 748 - random(0, 2));
            return random(1000, 1500);
        }
        if (isQuestMenu()) {
            int index = getMenuIndex(
                    uncert ? "Five Certificates Please." : "Twenty Five");
            if (index != -1) {
                answer(index);
                exchangeStarted();
                return random(1300, 1500);
            }
            return random(250, 300);
        }
        if (getWallObjectIdFromCoords(603, 746) == 2) {
            atWallObject(603, 746);
            return random(1000, 1500);
        }
        if (shouldBank()) {
            walkTo(586 + random(0, 3), 751 + random(0, 6));
            return random(1000, 1500);
        }
        int[] certer = getNpcByIdNotTalk(item.getCerterIds());
        if (certer[0] != -1) {
            int index = getInventoryIndex(
                    uncert ? item.getCertId() : item.getItemId());
            if (index != -1) {
                useOnNpc(certer[0], index);
            } else {
                System.out.println("Error: index is -1.");
            }
            return random(1000, 2000);
        }
        return random(100, 700);
    }

    // standard certification process, yanille differs from the rest
    private int stdopt() {
        int index = getMenuIndex(
                uncert ? "I have some certificates to trade in"
                : "I have some " + item.getType() + " to trade in");
        if (index != -1) {
            answer(index);
            menu_time = System.currentTimeMillis();
            return random(2500, 2700);
        }
        index = getMenuIndex(item.getItemName());
        if (index != -1) {
            answer(index);
            menu_time = System.currentTimeMillis();
            return random(1000, 1200);
        }
        index = getMenuIndex(uncert ? "five" : "Twentyfive");
        if (index != -1) {
            answer(index);
            exchangeStarted();
            return random(1500, 1700);
        }
        return random(250, 300);
    }

    /** The amount was just picked: hold off until the pack shows the result. */
    private void exchangeStarted() {
        trade_held = getInventoryCount(
                uncert ? item.getCertId() : item.getItemId());
        trade_time = System.currentTimeMillis();
    }

    private boolean shouldBank() {
        if (uncert) {
            return hasInventoryItem(item.getItemId());
        } else {
            return getInventoryCount(item.getItemId()) < 25;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        uncert = e.getActionCommand().equals("Uncert");
        int index = choice.getSelectedIndex();
        applyChoice(index);
        saveConfig(index);
        String str = new StringBuilder()
            .append(uncert ? "Uncerting " : "Certing ")
            .append(item.getItemName().toLowerCase(Locale.ENGLISH))
            .append(".")
            .toString();
        System.out.println(str);
        frame.setVisible(false);
    }

    /** Turn a row of the choice list into the item it stands for. */
    private void applyChoice(int index) {
        switch (index) {
            case 0:
                item = new CertOption(new int[] {
                    299
                }, 546, 630, "fish", "shark");
                break;
            case 1:
                item = new CertOption(new int[] {
                    299
                }, 545, 631, "fish", "Raw shark");
                break;
            case 2:
                item = new CertOption(new int[] {
                    341
                }, 635, 711, "logs", "yew logs");
                break;
            case 3:
                item = new CertOption(new int[] {
                    341
                }, 634, 712, "logs", "maple logs");
                break;
            case 4:
                item = new CertOption(new int[] {
                    341
                }, 633, 713, "logs", "willow logs");
                break;
            case 5:
                item = new CertOption(new int[] {
                    778
                }, 483, 1272, null, "Prayer Restore Potion");
                break;
            case 6:
                item = new CertOption(new int[] {
                    778
                }, 486, 1273, null, "Super Attack Potion");
                break;
            case 7:
                item = new CertOption(new int[] {
                    778
                }, 495, 1274, null, "Super Defense Potion");
                break;
            case 8:
                item = new CertOption(new int[] {
                    778
                }, 492, 1275, null, "Super Strength Potion");
                break;
            case 9:
                item = new CertOption(new int[] {
                    778
                }, 814, 1270, null, "Dragon Bones");
                break;
            case 10:
                item = new CertOption(new int[] {
                    778
                }, 220, 1271, null, "Limpwurt Root");
                break;
            case 11:
                item = new CertOption(new int[] {
                    226, 467
                }, 170, 528, "bars", "iron bar");
                break;
            case 12:
                item = new CertOption(new int[] {
                    226, 467
                }, 171, 529, "bars", "steel bar");
                break;
            case 13:
                item = new CertOption(new int[] {
                    226, 467
                }, 173, 530, "bars", "mithril bar");
                break;
            case 14:
                item = new CertOption(new int[] {
                    226, 467
                }, 172, 532, "bars", "Gold bar");
                break;
            case 15:
                item = new CertOption(new int[] {
                    226, 467
                }, 384, 531, "bars", "silver bar");
                break;
            case 16:
                item = new CertOption(new int[] {
                    225, 466
                }, 151, 517, "ore", "iron");
                break;
            case 17:
                item = new CertOption(new int[] {
                    225, 466
                }, 155, 518, "ore", "Coal");
                break;
            case 18:
                item = new CertOption(new int[] {
                    225, 466
                }, 153, 519, "ore", "mithril");
                break;
            case 19:
                item = new CertOption(new int[] {
                    225, 466
                }, 152, 521, "ore", "Gold");
                break;
            case 20:
                item = new CertOption(new int[] {
                    225, 466
                }, 383, 520, "ore", "silver");
                break;
            case 21:
                item = new CertOption(new int[] {
                    227
                }, 373, 533, "fish", "Lobster");
                break;
            case 22:
                item = new CertOption(new int[] {
                    227
                }, 372, 534, "fish", "Raw Lobster");
                break;
            case 23:
                item = new CertOption(new int[] {
                    227
                }, 370, 535, "fish", "swordfish");
                break;
            case 24:
                item = new CertOption(new int[] {
                    227
                }, 369, 536, "fish", "Raw swordfish");
                break;
        }
    }

    @Override
    public void walkTo(int x, int y) {
        if (!isWalking()) {
            super.walkTo(x, y);
        }
    }
    
    @Override
    public void talkToNpc(int i) {
        super.talkToNpc(i);
        menu_time = System.currentTimeMillis();
    }
    
    @Override
    public void useOnNpc(int npc, int slot) {
        super.useOnNpc(npc, slot);
        menu_time = System.currentTimeMillis();
    }
    
    @Override
    public void onServerMessage(String str) {
        str = str.toLowerCase(Locale.ENGLISH);
        if (str.contains("busy")) {
            menu_time = -1L;
        }
    }

    @Override
    public String toString() {
        return uncert ? "Uncerter" : "Certer";
    }
    
    private final static class CertOption {

        final int[] certer_ids;
        final int item_id;
        final int cert_id;
        final String type;
        final String name;

        CertOption(int[] certer_ids, int item_id, int cert_id,
                    String type, String option) {
            this.certer_ids = certer_ids;
            this.item_id = item_id;
            this.cert_id = cert_id;
            this.type = type;
            this.name = option;
        }

        int[] getCerterIds() {
            return certer_ids;
        }

        int getItemId() {
            return item_id;
        }

        int getCertId() {
            return cert_id;
        }

        String getType() {
            return type;
        }

        String getItemName() {
            return name;
        }
    }
}
