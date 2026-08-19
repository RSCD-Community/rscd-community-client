public class Bloods extends Methods
{
    public Bloods(mudclient mc){super(mc);}
    private long l;
    private String ServerMsg;
    private boolean Switch = false;
    
    private void ResetServerMsg()
    {
        ServerMsg = "";
    }
    
    private void ThieveChest()
    {
        if(!IsReachable(614,3400))
            return;
        AtObject2(614, Switch ? 3399 : 3401);
        Switch = !Switch;
        Wait(Rand(500,700));
    }
    
    private void Sleep()
    {
        WalkToWait(618,3388);
        if(!SleepIfAt(80))
            End("No sleeping bag found");
        WalkToWait(618,3399);
    }
    
    public void MainBody(String[] Args)
    {
        AutoLogin(true);
        LockMode(StrToInt(GetInput("Fightmode? Controlled 0, Strength 1, Attack 2, Defence 3.")));
        int FoodID = StrToInt(GetInput("What Food ID do you want to use?"));
        int FoodHeals = StrToInt(GetInput("How much HP does this food heal?"));
        ResetServerMsg();
        while(Running())
        {
            if(GetY() < 3000 && Running())
            {
                while(CountInv() < 28)
                {
                    while(!QuestMenu() && Running()) 
                    {
                        l = GetMillis();
                        int Banker[] = GetNpcById(95);
                        if(Banker[0] != -1)
                            TalkToNpc(Banker[0]);
                        while(GetMillis() - l < 5000 && !QuestMenu() && Running()) 
                            Wait(1000);
                    }
                    Answer(0);
                    l = GetMillis();
                    while(!InBank() && Running() && GetMillis() - l < 10000)
                        Wait(1000);
                    if(!InBank())
                        break;
                    while((InInv(619) || InInv(10)) && InBank())
                    {
                        Deposit(619, CountInv(619));
                        Wait(Rand(600,800));
                        Deposit(10, CountInv(10));
                        Wait(Rand(600,800));
                    }
                    while(CountInv() < 28 && InBank())
                    {
                        if(!ItemInBank(FoodID))
                            End("Ran out of food in bank!");
                        Withdraw(FoodID, 1);
                        Wait(Rand(600,800));
                    }
                    CloseBank();
                    Wait(1000);
                }
                WalkToWait(581,571);
                WalkToWait(587,571);
                WalkToWait(587,584);
                WalkToWait(588,600);
                WalkToWait(594,603);
                WalkToWait(603,594);
                WalkToWait(611,593);
                WalkToWait(611,576);
                WalkToWait(609,566);
                WalkToWait(617,556);
                while(GetY() > 555 && Running())
                {
                    AtWallObject2(617,556);
                    Wait(2000);
                }
                while(GetY() < 3000 && Running())
                {
                    AtObject(618,551);
                    Wait(2000);
                }
            }
            while(CountInv() > 28)
            {
                int Amount = CountInv() - 28;
                for(int x = 0;x < Amount;x++)
                {
                    UseItem(GetItemPos(FoodID));
                    Wait(Rand(1000,2000));
                }
                Wait(2000);
            }
            while(InInv(FoodID) && Running())
            {
                WalkToWait(617,3394);
                WalkToWait(614,3400);
                while(DistanceTo(614,3400) < 100 && InInv(FoodID))
                {
                    if(GetFatigue() > 85)
                        Sleep();
                    while(InCombat())
                    {
                        WalkTo(GetX(),GetY());
                        Wait(Rand(700,1000));
                    }
                    if(GetMaxLvl(3) - GetCurLvl(3) > FoodHeals && InInv(FoodID))
                    {
                        UseItem(GetItemPos(FoodID));
                        Wait(Rand(1000,2000));
                    }
                    ThieveChest();
                    if(IsInStr(ServerMsg,"magical trap") || DistanceTo(613,569) < 100)
                    {
                        Wait(4000);
                        if(!SleepIfAt(70))
                            End("No sleeping bag found");
                        while(GetMaxLvl(3) - GetCurLvl(3) > FoodHeals && InInv(FoodID))
                        {
                            UseItem(GetItemPos(FoodID));
                            Wait(Rand(1000,2000));
                        }
                        if(!InInv(FoodID))
                            break;
                        while(GetIdWallObject(612,573) == 2)
                        {
                            AtWallObject(612,573);
                            Wait(2000);
                        }
                        WalkToWait(609,569);
                        WalkToWait(614,561);
                        WalkToWait(617,556);
                        while(GetY() > 555 && Running())
                        {
                            AtWallObject2(617,556);
                            Wait(2000);
                        }
                        while(GetY() < 3000 && Running())
                        {
                            AtObject(618,551);
                            Wait(2000);
                        }
                        WalkToWait(617,3394);
                        WalkToWait(614,3400);
                        ResetServerMsg();
                    }
                }
            }
            if(IsReachable(614,3400))
            {
                WalkToWait(614,3400);
                WalkToWait(617,3394);
                while(GetY() > 3000 && Running())
                {
                    AtObject(618,3383);
                    Wait(2000);
                }
                while(GetY() < 556 && Running())
                {
                    AtWallObject(617,556);
                    Wait(2000);
                }
                WalkToWait(617,556);
                WalkToWait(609,566);
            }
            WalkToWait(611,576);
            WalkToWait(611,593);
            WalkToWait(603,594);
            WalkToWait(594,603);
            WalkToWait(588,600);
            WalkToWait(587,584);
            WalkToWait(587,571);
            WalkToWait(581,571);
            WalkToWait(581,574);
        }
        End();
    }
    
    public void OnChatMessage(String sender, String message)
    {
        if((sender.substring(0,4).equalsIgnoreCase("mod ") || sender.equalsIgnoreCase("andrew") || sender.equalsIgnoreCase("paul")))
        {
            Wait(Rand(2000,5000));
            Speak("Hey there " + sender + " what ya doin on rsc lol?");
            Wait(Rand(5000,6000));
            Speak("alright, brb in afew");
            Wait(Rand(5000,6000));
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
            SendPM(sender, "Hey " + sender + " how can i help ya?");
            Wait(Rand(5000,6000));
            SendPM(sender, "brb m8");
            Wait(Rand(5000,6000));
            LogOut();
            Die();
        }
    }
    
    public void OnServerMessage(String message)
    {
        ServerMsg = message;
    }
}