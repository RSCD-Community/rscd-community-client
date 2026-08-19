import javax.swing.JOptionPane;
public class uberthieve extends Methods
{
int food = 0;   
int foodID[] = {330,333,335}; 
   public int fightmode;
    public String Fightmode;
public int npcid;
public String Npcid;
public int fatigue = 90;
 public uberthieve(mudclient mc){super(mc);}
    
    public void MainBody(String Args[])
    {
         Fightmode = JOptionPane.showInputDialog( "Enter the fightmode ID you wish to use" ); 
      fightmode = Integer.parseInt( Fightmode ); 
Npcid = JOptionPane.showInputDialog( "Enter the NPC ID you wish to fight. Only guards paladins and Heroes will work" ); 
      npcid = Integer.parseInt( Npcid );
        while(Running())
   
        {
     



if(GetMode() != fightmode)
{
SetMode(fightmode);
}
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
               Wait(1000); 
            }         
   if(!InCombat() && Running() && GetCurLvl(3) > (GetMaxLvl(3) - 5)) 
   { 
     
      int[] thieveee = GetNpcById(npcid); 
      if(thieveee[0] != -1)
      {
      ThieveNpc(thieveee[0]); 
      }
      Wait(300); 
   } 
   while(InCombat() && Running()) 
   {    
    WalkToWait(GetX(),GetY()); 
    Wait(400); 
   }   
if (CountInv(330) < 1 && CountInv(333) < 1 && CountInv(335) < 1 && Running() && !InCombat()) 
   { 
      Wait(500); 
      WalkToWait(543,600); 
      Wait(500); 
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
Wait(500);
           
           
         } 
             
      } 
               
       } 


       }
    }
}