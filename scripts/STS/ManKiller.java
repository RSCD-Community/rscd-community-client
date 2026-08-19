public class ManKiller extends Methods {
    boolean paused = false;
    
    public ManKiller(mudclient mc) {
        super(mc);
    }
    
     public void MainBody(String args[]) {
        boolean controlled = false;
        int fightMode = 0, max = 0, stat = 0;
        int[] herbs = { 165, 435, 436, 437, 438, 439, 440, 441, 442, 443 };
        String mode = GetInput("Please enter your FightMode(Controlled, Strength, Attack, Defense)").toLowerCase();
        if(mode.equals("controlled")) {
            fightMode = 0;
            controlled = true;
        } else
        if(mode.equals("strength")) {
            fightMode = 1;
            stat = 2;
        } else
        if(mode.equals("attack")) {
            fightMode = 2;
            stat = 0;
        } else
        if(mode.equals("defense")) {
            fightMode = 3;
            stat = 1;
        } else
            End("Invalid Fightmode Chosen, please type \"controlled, strength, attack, or defense\"");
        if(!controlled)
            max = StrToInt(GetInput("Please enter max level of chosen mode(Does not work with controlled)"));
        StartScanForMods();
        AutoLogin(true);
        SetMode(fightMode);
        Display("ManKiller by RLN");
        while(Running()) {
            while(!paused) {
                  if(GetMode() != fightMode)
                      SetMode(fightMode);
                  if(!controlled && GetMaxLvl(stat) >= max)
                    End("Set stat achieved, ending");
                if(GetCurLvl(3) < 4)
                    End("Hp has dropped below 4, ending");
                if(CountInv() == 30) {
                    ForceWalkToWait(217, 450);
                    while(!QuestMenu() && Running()) {
                        int[] bankers = GetNpcById(BANKERS);
                        if(bankers[0] != -1)
                            TalkToNpc(bankers[0]);
                        Wait(2000);
                    }
                    Answer(0);
                    while(!InBank())
                        Wait(100);
                    if(InBank())
                        for(int i = 0; i < herbs.length; i++) {
                            for(int x = 0; x <= CountInv(herbs[i]); x++) {
                                Deposit(herbs[i], x);
                                Wait(100);
                            }
                            Wait(100);
                        }
                }
                SleepIfAt(95);
                int[] men = GetNpcById(11);
                int herb[] = GetItemById(herbs);
                if(!InCombat()) {
                    if(men[0] != -1 && GetMode() == fightMode) {
                        AttackNpc(men[0]);
                        Wait(Rand(700,800));
                    } else if(herb[0] != -1 && (herb[1] > 207 && herb[1] < 220) && (herb[2] < 447 && herb[2] > 438)) {
                        PickupItem(herb[1], herb[2], herb[0]);
                        Wait(Rand(200,220));
                    } else
                    if(GetX() != 213 || GetY() != 443) {
                        ForceWalkTo(213, 443);
                        Wait(700);
                    }
                }
                Wait(Rand(10,20));
            }
        }
        End("ManKiller ended");
    }
    
    public void KeyPressed(int key) {
        if(key == 1009) {
            paused = !paused;
            if(paused)
                Display("ManKiller paused");
            else
                Display("ManKiller resumed");
        }
    }

}