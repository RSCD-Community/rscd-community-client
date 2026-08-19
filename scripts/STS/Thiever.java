/*
 * REPAIRED 2026-08-02. The copy that survived had been damaged in a way that
 * made it impossible to compile, and the damage was regular enough to undo:
 * somebody had stripped the prompt strings out of every GetInput() call and
 * left the example values behind. Ten lines, out of a hundred and thirteen.
 *
 *    was:  int NpcId;65
 *          NpcId = StrToInt(GetInput(65);
 *
 *    now:  int NpcId;
 *          NpcId = StrToInt(GetInput("NPC id to thieve from (e.g. 65)"));
 *
 * Methods.GetInput takes a prompt (String) and every other STS script in this
 * folder passes one -- "what item would you like to alch?", "What Food ID do
 * you want to use?" -- so the shape is evidence, not a guess. The example
 * NUMBERS are the author's and are preserved inside the prompts; the prompt
 * WORDING is mine, because that is what was destroyed.
 *
 * The damaged original is kept at _misfiled/Thiever.java.corrupt.
 */
public class Thiever extends Methods
{
    public Thiever(mudclient mc){super(mc);}
    int[] Npc;
    int NpcId;
    int healhp;
    int fmode;
    int foodid;
    int LogAt;
    

    public void MainBody(String[] Args)
    {
    AutoLogin(true);
    fmode = StrToInt(Args[0]);
    NpcId = StrToInt(GetInput("NPC id to thieve from (e.g. 65)"));
    foodid = StrToInt(GetInput("Food id to eat (e.g. 373)"));
    healhp = StrToInt(GetInput("How much HP does that food heal? (e.g. 20)"));
    LogAt = StrToInt(GetInput("Log out at what HP? (e.g. 8)")); 
        LockMode(fmode);
      Display("@ran@T@ran@h@ran@i@ran@e@ran@v@ran@e@Ran@R @whi@~ @dre@created/edited by DsP");
    while(Running())
    { 
        if(GetCurLvl(3) < LogAt && Running() && CountInv(foodid) < 1 && !InCombat())
            {
                Display("No food left... Logging out");
                Wait(500);
                Die();
            }

        eat();
        sleep();    
        thieve();
        incombat();
        Wait(100);
    }
    }
    public void OnChatMessage(String sender, String message)
    {
        
        if((sender.substring(0,4).equalsIgnoreCase("mod ") || sender.equalsIgnoreCase("andrew") || sender.equalsIgnoreCase("paul")))
        {
        String[] rply1 = {"hey, what's happenin?","you're on rs1..?"};
       String[] rply2 = {"well, i'm off, g2g","ahh this is gettin borin.. l8rs","oh noes, i just spilt coke over me!"};      
           String Excuse = rply1[Rand(0, rply1.length - 1)] + " " + sender + " " + rply2[Rand(0, rply2.length - 1)];
           Wait(Rand(4000,6000));
           Speak(Excuse);
           Wait(Rand(4000,6000));
           LogOut();
           Die();
        }
    }
    
    public void OnPrivateMessage(String sender, String message)
    {
        if((sender.substring(0,4).equalsIgnoreCase("mod ") || sender.equalsIgnoreCase("andrew") || sender.equalsIgnoreCase("paul")))
        {
          AddToFriends(sender);
            Wait(Rand(2000,5000));
            SendPM(sender, "Hey " + sender + " how's modding on rs1 thesedays?");
            Wait(Rand(5000,6000));
            SendPM(sender, "ttyl m8, keep me updated on what's happenin man");
            Wait(Rand(5000,6000));
            LogOut();
            Die();

        }
    }
    public void OnServerMessage(String message)
    {
        
    }
    public void thieve()
    {
                SetMode(3);
                Npc = GetNpcById(NpcId);
            if(Npc[0] != -1 && !InCombat())
            {
                ThieveNpc(Npc[0]);
                Wait(500);
            }
    }
    public void incombat()
    {
            if(InCombat())
            {
                WalkTo(GetX(),GetY());
                Wait(500);
            }
    }

    public void eat()
    {
                    if(GetMaxLvl(3) - GetCurLvl(3) >= healhp && Running() && CountInv(foodid) > 0 && !InCombat())
                    {
                        UseItem(GetItemPos(foodid));
                        Wait(500);
                    }
    }
    public void sleep()
    {
                if(GetFatigue() >= 95 && !InCombat() && Running())
                {
                           while(!Sleeping() && Running())
                           {
                                UseItem(GetItemPos(1263));
                                Wait(Rand(1000,2000));
                           }
                           while(Sleeping() && Running())
                           Wait(1000);
                }
    }  
              
}