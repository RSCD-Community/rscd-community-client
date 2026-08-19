public class BarbCoal extends Methods
{
    public BarbCoal(mudclient mudclient){super(mudclient);}
    private long l;

    public void MainBody(String args[])
    {
        AutoLogin(true);
        StartScanForMods();
        while(Running())
        {
            Mine();
            WalkToBank();
            BankTime();
            WalkToCoal();
        }
    }

    
    
    
  public void Mine()
  {
     while(Running() && CountInv() != 30)
     {
      SleepIfAt(80);      
      int[] Mine = GetObjectById(110);
      if(Mine[0] != -1 && !Sleeping() && Running())
        {
       AtObject(Mine[1],Mine[2]);
       Wait(750);
        }
     }
  }
  
  
  
  public void WalkToBank()
  {
   WalkToWait(222,492);
   WalkToWait(222,479);
   WalkToWait(225,466);
   WalkToWait(221,454);
   ForceWalkToWait(218,449);
  }
  
  
  
  public void BankTime()
  {
   while(!QuestMenu() && Running())
        {
          int[] Npc = GetNpcById(95);

          if(Npc[0] != -1)
          TalkToNpc(Npc[0]);
          Wait(1000);
        }
                            
      Answer(0);
      l = GetMillis();
      while (!InBank() && GetMillis() - l < 25000) 
        Wait(100);
        l = GetMillis(); 
      while(InBank() && GetMillis() - l < 25000)
           {
             while(CountInv(155) > 0 && Running())
               {
                 Deposit(155,1);
                 Wait(200);
               }
               while (CountInv(157) > 0 && Running()) 
                        { 
                         Deposit(157,1); 
                         Wait(100); 
                        }
                while (CountInv(158) > 0 && Running()) 
                    { 
                    Deposit(158,1); 
                    Wait(100); 
                    }
                while (CountInv(159) > 0 && Running()) 
                    { 
                    Deposit(159,1); 
                    Wait(100); 
                    }
                while (CountInv(160) > 0 && Running()) 
                    { 
                    Deposit(160,1); 
                    Wait(100); 
                    }
            CloseBank();
        }
   }
   
   
   
   public void WalkToCoal()
   {
    ForceWalkToWait(218,449);
    WalkToWait(222,454);
    WalkToWait(225,466);
    WalkToWait(222,479);
    WalkToWait(222,492);
    WalkToWait(225,499);
    ForceWalkToWait(226,505);
   }
      
}