import javax.swing.JOptionPane;
public class Thieve extends Methods
{
int foodID[] = {330,333,335};  

 int food = 0; 
 int fatigue = 90;
 public Thieve(mudclient mc){super(mc);}
   int FightMode = 3;
public String fightmode;
    public void MainBody(String Args[])
    {
         fightmode = JOptionPane.showInputDialog( "Enter the fightmode you wish to use" ); 
      FightMode = Integer.parseInt( fightmode ); 


        while(Running())
        {
              
         
               if (GetFatigue() > fatigue && Running() == true)
                     {
if(CountInv(1263) == 0)
{
System.out.println("No sleeping bag - Logging out");
System.exit( 0 ); 
}                       
  while (Sleeping() == false)
                        {
                          UseItem(GetItemPos(1263));
                        Wait(2500);
                        }
                        while (Sleeping() && Running())
                        {
                           Wait(100);
                        }
                    
}
 if (GetCurLvl(3) <= (GetMaxLvl(3) - 5) && Running() && !InCombat() ) 
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
      if(!InCombat() && Running() && GetCurLvl(3) > (GetMaxLvl(3) - 5)) 
   { 
      SetMode(FightMode); 
      int[] guard = GetNpcById(321); 
      if(guard[0] != -1)
      {
      ThieveNpc(guard[0]); 
      }
      Wait(700); 
          } 
   while(InCombat() && Running()) 
   {    
      if(GetMode() != FightMode)
      {
      SetMode(FightMode); 
      }
      WalkToWait(GetX(),GetY()); 
      Wait(2000); 
   }   
 if (CountInv(330) < 1 && CountInv(333) < 1 && CountInv(335) < 1 && Running() && !InCombat()) 
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
         if(GetMode() != FightMode)
         {     
SetMode(FightMode);        
}    
WalkToWait(GetX(),GetY()); 
           
           
         } 
             
      } 
               
       } 
         
      

       }
    }
}