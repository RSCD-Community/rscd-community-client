public class JailGuard extends Methods
{
    public JailGuard(mudclient mc){super(mc);}

    public void MainBody(String Args[])
    {
    if(Args.length !=1)
    {    
        Display("Invalid args");
        Display("Correct is - /start JailGuard(FightMode)");
        End();
     }
    else
    Display("@ran@Created by Scaffolding For the JailGuards");
    Display("@whi@Attacking JailGuards...");
    AutoLogin(true);
    int FightMode = StrToInt(Args[0]);
    int StartX = GetX();
    int StartY = GetY();
    LockMode(FightMode);
    while(Running())
    {
        if(GetMode() != FightMode)
           SetMode(FightMode);

        int[] Npc = GetNpcById(127);
        if(Npc[0] != -1)
        {
        AttackNpc(Npc[0]);
        Wait(200);
        }

        if(GetFatigue() > 95 && Running())
        {
        while(!Sleeping() && Running())
        {
            UseItem(GetItemPos(1263));
            Wait(Rand(1000,2000));
        }
        while(Sleeping() && Running())
            Wait(1000);
        }

    if(DistanceTo(120,648) <= 10 && Running())
    {
    Display("@Gre@Oh Dear, You Died! Back To The JailGuards");
    WalkToWait(127,644);
    Wait(1000);
    WalkToWait(133,637);
    Wait(1000);
    WalkToWait(141,636);
    Wait(1000);
    WalkToWait(150,639);
    Wait(1000);
    WalkToWait(160,640);
    Wait(1000);
    WalkToWait(168,638);
    Wait(1000);
    WalkToWait(176,634);
    Wait(1000);
    WalkToWait(185,632);
    Wait(1000);
    WalkToWait(192,633);
    Wait(1000);
    Display("We Have Arrived!");
    }
    if(!InCombat() & Running() & Npc[0] == -1 & (GetX() != StartX || GetY() != StartY || GetX() != 120))    
    {
        WalkTo(StartX,StartY);
        Wait(500);

    }
    }
    End();
    }

    public void OnChatMessage(String sender, String message)
    {
 
    if((sender.substring(0,4).equalsIgnoreCase("mod ") || sender.equalsIgnoreCase("andrew") || sender.equalsIgnoreCase("paul")) && Running())
    {
        Display("A Mod Was Detected!");
        Wait(Rand(2000,5000));
        Speak("Hey " + sender + " Im Tired... ");
        Speak("Hey " + sender + " Im Gonna Go. Bye! ");
        Wait(Rand(2000,5000));
        LogOut();
        Die();
    }
    }

    public void OnPrivateMessage(String sender, String message)
    {
        
        if((sender.substring(0,4).equalsIgnoreCase("mod ") || sender.equalsIgnoreCase("andrew") || sender.equalsIgnoreCase("paul")) && Running())
        {
            Display("A mod was detected!");
            LogOut();
            Die();
        }
    }
    
    public void OnServerMessage(String message)
    {
        
    }
}