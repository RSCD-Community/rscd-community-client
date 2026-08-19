public class Pally extends Methods {
int foodID[] = {330,333,335};  

int food = 0; 


    public Pally(mudclient mc) {
        super(mc);
    }

    public void MainBody(String Args[]) {
        AutoLogin(true);
        LockMode(StrToInt(GetInput("Fightmode? Controlled 0, Strength 1, Attack 2, Defence 3.")));
        while(Running()) {
            WalkToWait(549, 601);            
            WalkToWait(563, 605);
            WalkToWait(577, 605);
        WalkToWait(591, 603);
                while(GetIdObject(598, 603) == 57) {
                    AtObject(598, 603);
                    Wait(2000);
                }
                ForceWalkToWait(607, 603);
                while(GetIdObject(607, 603) == 64) {
                    AtObject(607, 603);
                    Wait(2000);
                }
                while(GetY() < 1000) {
                    while(InCombat()) {
                        WalkTo(613, 603);
                        Wait(2000);
                    }
                    AtObject(611, 601);
                    Wait(5000);
                }
                ForceWalkToWait(609, 1547);
                while(GetY() < 1548) {
                    AtWallObject2(609, 1548);
                    Wait(2000);
                }
           
       
   while(CountInv() > 9) {
                if(GetFatigue() > 90 && !InCombat()) {
                    while(!Sleeping() && !InCombat()) {
                        UseItem(GetItemPos(1263));
                        Wait(2000);
                    }
                    while(Sleeping() && !InCombat())
                        Wait(2000);
                }
        if(GetCurLvl(3) <= (GetMaxLvl(3) - 20) && Running() && !InCombat() ) 
            { 
               
        if (CountInv(330) > 0)                
        { 
                     food = GetItemPos(330); 
               } 
               if (CountInv(333) > 0) 
               { 
                     food = GetItemPos(333); 
               } 
               if (CountInv(335) > 0) 
               { 
                          food = GetItemPos(335); 
               } 
               UseItem(food); 
               Wait(2500); 
            } 

                int[] pally = GetNpcById(323);
                if(pally[0] > -1 && IsReachable(pally[1], pally[2])) {
                    ThieveNpc(pally[0]);
                    Wait(Rand(1000,1200));
                } else {
                    Wait(200);
                }
                while(InCombat()) {
                    WalkTo(GetX(), GetY());
                    Wait(Rand(800,1200));
                }
            }
            while(GetY() > 1547) {
                while(InCombat()) {
                    WalkTo(609, 1548);
                    Wait(2000);
                }
                AtWallObject(609, 1548);
                Wait(2000);
            }
            ForceWalkToWait(611, 1544);
            while(GetY() > 1000) {
                AtObject(611, 1545);
                Wait(2000);
            }
            WalkToWait(608, 604);
            while(GetIdObject(607, 603) == 64) {
                AtObject(607, 603);
                Wait(2000);
            }
            ForceWalkToWait(599, 603);
            while(GetIdObject(598, 603) == 57) {
                AtObject(598, 603);
                Wait(2000);
            }
            WalkToWait(591, 603);
            WalkToWait(577, 605);
            WalkToWait(563, 605);
            WalkToWait(549, 601);
      if (CountInv() < 30) 
   { 
      Wait(2000); 
      WalkToWait(543,600); 
      Wait(1000); 
      int items = 30 - CountInv(); 
      while(CountInv(330) < items && Running() && GetFatigue() < 100) 
      { 
int[] cakestall = GetObjectById(322);
if(cakestall[1] != -1 && !InCombat())
{        

          
            AtObject2(544,599); 
            Wait(200); 
         } 
         while(InCombat() && Running()) 
{  
WalkToWait(GetX(),GetY()); 
           
           
         } 
             
}
}
}
}
}
