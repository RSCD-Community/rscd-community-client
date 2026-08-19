import javax.swing.JOptionPane;

public class Shadows extends Script {

	long spellCastTime;

    public Shadows(Extension e) {
        super(e);
    }

    public void init(String params) {
		spellCastTime = -1L;
		
        Object[] options = {"Lobsters", "Swordfish", "Sharks"};
        String fd = (String)JOptionPane.showInputDialog(null, "What kind of food?", "Shadows", JOptionPane.PLAIN_MESSAGE, null, options, options[2]);
        Object[] optionss = {"Attack", "Strength", "Defense", "Controlled"};
        String fightm = (String)JOptionPane.showInputDialog(null, "Fightmode?", "Shadows", JOptionPane.PLAIN_MESSAGE, null, optionss, optionss[0]);

        if(fd.equals("Lobsters")) {
            food = 373;
        } else
        if(fd.equals("Swordfish")) {
            food = 370;
        } else
        if(fd.equals("Sharks")) {
            food = 546;
        }
        
        if(fightm.equals("Attack")) {
            fightMode = 2;
        } else
        if(fightm.equals("Strength")) {
            fightMode = 1;
        } else
        if(fightm.equals("Defense")) {
            fightMode = 3;
        }
    }

    public int main() {
        if(getY() >= 3368 && getY() <= 3396) {
            for(int i : drops) {
                int[] d = getItemById(i);
                if(d[0] != -1) {
                    if(inCombat()) {
                        walkTo(getX(), getY());
                        return 800;
                    }
                    if(getInventoryCount() == 30 && hasInventoryItem(food)) {
                        useItem(getInventoryIndex(food));
                        return 800;
                    }
                    pickupItem(d[0], d[1], d[2]);
                    return random(700, 800);
                }
            }
            if(hasInventoryItem(food) && getCurrentLevel(3) <= 60) {
                if(inCombat()) {
                    walkTo(getX(), getY());
                    return 800;
                }
                useItem(getInventoryIndex(food));
                return random(1000, 1500);
            }
            if(getFatigue() > 90 && getY() >= 3368 && getY() <= 3396) {
                walkTo(509, 3383);
                if(getX() == 509 && getY() == 3383 && hasInventoryItem(1263)) {
                    useSleepingBag();
                    return 1000;
                }
                return 900;
            }
            if(getFightMode() != fightMode) {
                setFightMode(fightMode);
            }
            if(getY() > 3383 && hasInventoryItem(food)) {
                walkTo(509, 3383);
                return random(1200, 1500);
            }
            if(!hasInventoryItem(food) || getInventoryCount()  == 30) {
                int[] stairs3 = getObjectById(41);
                if(inCombat()) {
                    walkTo(getX(), getY());
                    return random(700, 800);
                }
                if(getFatigue() > 90 && getY() > 3385) {
                    useSleepingBag();
                    return 1000;
                }
                if(stairs3[0] != -1) {
                    atObject(stairs3[1], stairs3[2]);
                    return random(1200, 1500);
                }
                if(stairs3[0] == -1) {
                    walkTo(519, 3378);
                    return random(1200, 1500);
                }
            }
            int[] warrior = getNpcById(787);
            if(warrior[0] != -1) {
				if ( inCombat() )
				{
					if ( spellCastTime <= System.currentTimeMillis() - 1000 )
					{
						if ( canCastSpell( 8 ) && getInventoryCount( 33 ) >= 2 && getInventoryCount( 41 ) >= 1 ) 
							mageNpc( warrior[0] , 8 );
						
						spellCastTime = System.currentTimeMillis();
					}
					
				}
				else
				{
					attackNpc( warrior[0] );
					return random(700, 900);
				}
                
            }
        }
        if(hasInventoryItem(food) && getY() >= 2421 && getY() <= 2427) {
            int[] stairs = getObjectById(42);
            if(stairs[0] != -1) {
                atObject(stairs[1], stairs[2]);
                return random(1200, 1500);
            }
        }
        if(hasInventoryItem(food) && getY() >= 1477 && getY() <= 1478) {
            int[] stairs1 = getObjectById(42);
            if(stairs1[0] != -1) {
                atObject(stairs1[1], stairs1[2]);
                return random(1500, 1800);
            }
        }
        if(hasInventoryItem(food) && getY() >= 533 && getY() <= 539) {
            int[] stairs2 = getObjectById(42);
            if(stairs2[0] != -1) {
                atObject(stairs2[1], stairs2[2]);
                return random(1500, 1800);
            }
        }
        if(!hasInventoryItem(food) && getY() >= 533 && getY() <= 539) {
            int[] stairs4 = getObjectById(41);
            if(stairs4[0] != -1) {
                atObject(stairs4[1], stairs4[2]);
                return random(1500, 1800);
            }
        }
        if(!hasInventoryItem(food) && getY() >= 1477 && getY() <= 1478) {
            int[] stairs5 = getObjectById(41);
            if(stairs5[0] != -1) {
                atObject(stairs5[1], stairs5[2]);
                return random(1500, 1800);
            }
        }
        if(!hasInventoryItem(food) && getY() >= 2421 && getY() <= 2427) {
            if(isQuestMenu()) {
               answer(0);
               return random(2000, 3000);
           }
            if(!isBanking()) {
                int banker[] = getNpcByIdNotTalk(95);
               if(banker[0] != -1) {
                   talkToNpc(banker[0]);
                    return 2000;
                }
            } else
            if(isBanking()) {
                for(int d : drops) {
                    if(hasInventoryItem(d)&&d!=33&&d!=41) {
                        deposit(d, getInventoryCount(d));
                        return random(800, 1000);
                    }
                }
                if(getInventoryCount(food) < 12) {
                    withdraw(food, 12);
                    return 1000;
                }
                closeBank();
                return 1000;
            }
        }
        return random(1000, 1200);
    }

    private int food;
    private int fightMode;

    private int[] drops = {
        1276, 1277, 526, 527, 619, 31, 32, 33, 34, 38, 41, 46, 40, 42, 370, 373
    };
}