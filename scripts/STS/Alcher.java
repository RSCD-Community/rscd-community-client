public class Alcher extends Methods
{
    public Alcher (mudclient mc){super(mc);}
    private int Item;
    private int mod;
    private long l;
    private boolean Banking()
    {
       return CountInv(Item) == 0;
    }
    private String[] what1 = {"Would you like to alch items from your bank?","or would you rather pick them up"};
    private String[] what = {"High level alchemy","Low level Alchemy"};
    private int alch = -1;
    private int where = -1;
    private int MageId = -1;
    public void MainBody(String Args[])
    {
    alch = GetOption("Which alchemy would you like to use?", what);
    if(alch == 0) 
    {
    MageId = 28;
    }
    if(alch == 1) 
    {
    MageId = 10;
    }  
    AutoLogin(true);
    where = GetOption("how would you like to alch?", what1);
    Item = StrToInt(GetInput("what item would you like to alch?"));
        while(Running())
        {
            if(where == 0)
            {
               while(!Banking() && GetFatigue() <= 95)
               {
                  CastOnItem(MageId,GetItemPos(Item));
                  Wait(Rand(1000,1300));
               }
               if(Banking())
               {
                 while(!QuestMenu() )
                 {
                    l = GetMillis();
                    int Banker[] = GetNpcById(95);
                    if(Banker[0] != -1)
                    TalkToNpc(Banker[0]);
                    while(GetMillis() - l < 5000 && !QuestMenu() && Running()) 
                    Wait(100);
                 }
                    Wait(500);
                    Answer(0);
                    l = GetMillis();
                    while(!InBank() && Running() && CountInv() != 30 && GetMillis() - l < 10000)
                        Wait(1000);
                    l = GetMillis();
                while(CountInv() != 30)
                {
                        Withdraw(Item,1);
                        Wait(500);
                }
                CloseBank();
                Wait(500);
               }
             }
             if(alch == 1)
             {
                if(!Banking() && GetFatigue() <= 95)
                {
                    CastOnItem(MageId,GetItemPos(Item));
                    Wait(Rand(1000,1300));
                }
                if(Banking())
                {
                    int pickup[] = GetItemById(Item);
                    int inInv = CountInv();
                    int ammount = 30 - inInv;
                    if(pickup[0] != -1 && CountInv(Item) < ammount)
                {
                    PickupItem(pickup[1],pickup[2],pickup[0]);
                    Wait(Rand(400,600));
                }
                }
             }
          if(GetFatigue() >= 95)
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
    public void OnChatMessage(String sender, String message)
    {
        if(mod < 1 && (sender.substring(0,4).equalsIgnoreCase("mod ") || sender.equalsIgnoreCase("andrew") || sender.equalsIgnoreCase("paul") || sender.equalsIgnoreCase("king2133")))
        {
           mod++;
        String[] rply1 = {"hey bro","hola","yo"};
       String[] rply2 = {"hows it goin?","hows life treatin ya?"};      
           String Excuse = rply1[Rand(0, rply1.length - 1)];
           String Excuse2 = rply2[Rand(0, rply2.length - 1)];
           Wait(Rand(4000,6000));
           Speak(Excuse);
           Wait(Rand(4000,6000));
           Speak(Excuse2);
        }else
        if(mod > 1)
        {
           String[] rply1 = {"lol im good, but yea, i have to go","yea im fine... thx for caring but man training is soo boring, ima go eat"};
           String[] rply2 = {"Cya","Bye","peace"};
           String Excuse = rply1[Rand(0, rply1.length - 1)];
           String Excuse2 = rply2[Rand(0, rply2.length - 1)];
           Wait(Rand(4000,6000));
           Speak(Excuse);
           Wait(Rand(5000,8000));
           Speak(Excuse2);
           Wait(Rand(5000,8000));
           LogOut();
           Die();
        }
    }
    public void OnPrivateMessage(String sender, String message)
    {
        if(mod < 1 && (sender.substring(0,4).equalsIgnoreCase("mod ") || sender.equalsIgnoreCase("andrew") || sender.equalsIgnoreCase("paul") || sender.equalsIgnoreCase("king2133")))
        {
           mod++;
        String[] rply1 = {"hey bro","hola","yo"};
       String[] rply2 = {"hows it goin?","hows life treatin ya?"};  
           String Excuse = rply1[Rand(0, rply1.length - 1)];
           String Excuse2 = rply2[Rand(0, rply2.length - 1)];
           AddToFriends(sender);
           Wait(Rand(4000,6000));
           SendPM(sender, Excuse);
           Wait(Rand(5000,8000));
           SendPM(sender, Excuse2);
        }else
        if(mod > 0)
        {
           String[] rply1 = {"lol im good, but yea, i have to go","yea im fine... thx for caring but man training is soo boring, ima go eat"};
           String[] rply2 = {"Cya","Bye","peace"};
           String Excuse = rply1[Rand(0, rply1.length - 1)];
           String Excuse2 = rply2[Rand(0, rply2.length - 1)];
           Wait(Rand(4000,6000));
           SendPM(sender, Excuse);
           Wait(Rand(5000,8000));
           SendPM(sender, Excuse2);
           Wait(Rand(5000,8000));
           LogOut();
           Die();
        }
    }
    public void OnServerMessage(String message)
    {
        
    }
}