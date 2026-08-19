import java.text.*;
import java.sql.*;
import java.net.*;
import java.awt.*;
import java.util.*;
public class Paladin extends Script {

  public Paladin(Extension e) {
     super(e);
  }

  public void init(String params) {
     foodId = 373;
     sleeping = true;
     if (getY() > 1000)
        currentStep = THIEVING;
     else
        currentStep = BANKING;
     String[] split = params.split(",");
     for (String s : split) {
        if (s.equalsIgnoreCase("nosleeping")) {
           sleeping = false;
        } else if (s.startsWith("foodid=")) {
           foodId = Integer.parseInt(s.substring(7));
        } else if (s.startsWith("fightmode=")) {
           fightMode = Integer.parseInt(s.substring(10));
        } else if (s.startsWith("hideweapon=")) {
			hideWeapon = ( s.equalsIgnoreCase("hideweapon=true") );
		}
     }
  }

  public int main() {
     try {
        if (getFightMode() != fightMode) {
           setFightMode(fightMode);
           return 100;
        }
        if (inCombat()) {
           walkTo(getX(), getY());
           return random(500, 1500);
        }
        if (sleeping && getFatigue() > 92) {
           useSleepingBag();
           return 2000;
        }
        switch (currentStep) {
        case WALKING_TO_BANK: {
           int[] door = getObjectById(new int[] { 57, 64 });
           if (distanceTo(door[1], door[2]) < 5) {
              atObject(door[1], door[2]);
              return random(100, 300);
           }
           if (sleeping) {
              if (walkStep == 0) {
                 if (getY() > 2000) {
                    walkStep++;
                    return 0;
                 }
                 atObject(611, 1551);
                 return random(1000, 2000);
              }
              if (walkStep == 1) {
                 if (atCoords(610, 2489)) {
                    walkStep++;
                    return 0;
                 }
                 walkTo(610, 2489);
                 return random(1000, 2000);
              }
              if (walkStep == 2) {
                 if (getY() <= 700) {
                    walkStep++;
                    return 0;
                 }
                 atObject2(610, 2487);
                 return random(1000, 2000);
              }
              if (walkStep - 3 < xCoords2.length) {
                 if (atCoords(xCoords2[walkStep - 3],
                       yCoords2[walkStep - 3])) {
                    walkStep++;
                    return 0;
                 }
                 walkTo(xCoords2[walkStep - 3], yCoords2[walkStep - 3]);
                 return random(500, 1000);
              }
           } else {
              if (walkStep == 0) {
                 if (atCoords(609, 1547)) {
                    walkStep++;
                    return 0;
                 }
                 atWallObject(609, 1548);
                 return random(500, 1000);
              }
              if (walkStep == 1) {
                 if (getY() < 1000) {
                    walkStep++;
                    return 0;
                 }
                 int[] stairs = getObjectById(44);
                 atObject(stairs[1], stairs[2]);
                 return random(1000, 2000);
              }
              if (xCoords.length - walkStep - 2 > -1) {
                 if (atCoords(xCoords[xCoords.length - walkStep - 2],
                       yCoords[yCoords.length - walkStep - 2])) {
                    walkStep++;
                    return 0;
                 }
                 walkTo(xCoords[xCoords.length - walkStep - 2],
                       yCoords[yCoords.length - walkStep - 2]);
                 return random(500, 1000);
              }
           }
           currentStep = BANKING;
           break;
        }
        case WALKING_TO_PALLYS: {
			if ( hideWeapon )
			{
				int weapon_index = getInventoryIndex( WEAPON_ID );
				
				if ( weapon_index != -1 && ! isItemEquipped( weapon_index ) )
				{
					wearItem( weapon_index );
					
					return random( 800 , 1200 );
				}
			}
			
           if (getLevel(3) - getCurrentLevel(3) > 20) {
              int index = getInventoryIndex(foodId);
              if (index == -1) {
                 currentStep = WALKING_TO_BANK;
                 walkStep = 0;
                 return 0;
              } else {
                 useItem(getInventoryIndex(foodId));
                 return random(1000, 2000);
              }
           }
           int[] door = getObjectById(new int[] { 57, 64 });
           if (distanceTo(door[1], door[2]) < 5) {
              atObject(door[1], door[2]);
              return random(100, 300);
           }
           if (walkStep < xCoords.length) {
              if (atCoords(xCoords[walkStep], yCoords[walkStep])) {
                 walkStep++;
                 return 0;
              }
              walkTo(xCoords[walkStep], yCoords[walkStep]);
              return random(500, 1000);
           }
           if (walkStep == xCoords.length) {
              if (getY() > 1000) {
                 walkStep++;
                 return 0;
              }
              atObject(611, 601);
              return random(1000, 2000);
           }
           if (getY() == 1548) {
              currentStep = THIEVING;
              return 0;
           }
           atWallObject2(609, 1548);
           return random(500, 1500);
        }
        case THIEVING: {
			if ( hideWeapon )
			{
				int weapon_index = getInventoryIndex( WEAPON_ID );
				
				if ( weapon_index != -1 && isItemEquipped( weapon_index ) )
				{
					removeItem( weapon_index );
					
					return random( 800 , 1200 );
				}
			}
			
           if (getLevel(3) - getCurrentLevel(3) > 20) {
              int index = getInventoryIndex(foodId);
              if (index == -1) {
                 currentStep = WALKING_TO_BANK;
                 walkStep = 0;
                 return 0;
              } else {
                 useItem(getInventoryIndex(foodId));
                 return random(1000, 2000);
              }
           }
           int[] paladin = getNpcById(323);
           if (paladin[0] > -1) {
              thieveNpc(paladin[0]);
              return random(300, 750);
           }
           break;
        }
        case BANKING: {
           if (getInventoryCount( foodId ) > 0 ) {
              currentStep = WALKING_TO_PALLYS;
              walkStep = 0;
              closeBank();
              return 100;
           }
           if (isBanking()) {
              deposit(545, getInventoryCount(545));
              sleep(50 + random(10, 50));
			  deposit(10, getInventoryCount(10));
              sleep(50 + random(10, 50));
			  deposit(619, getInventoryCount(619));
              sleep(50 + random(10, 50));
			  deposit(41, getInventoryCount(41));
              sleep(50 + random(10, 50));
              deposit(427, getInventoryCount(427));
              sleep(50 + random(10, 50));
              deposit(160, getInventoryCount(160));
              sleep(50 + random(10, 50));
              deposit(154, getInventoryCount(154));
              sleep(50 + random(10, 50));
              withdraw(foodId, 30 - getInventoryCount() - 2 );
              return 1200;
           }
           if (isQuestMenu()) {
              answer(0);
              return 2000;
           }
           int[] banker = getNpcById(BANKERS);
           talkToNpc(banker[0]);
           return random(2000, 3000);
        }
        }
     } catch (Exception e) {
     }
     return 0;
  }

  private boolean atCoords(int x, int y) {
     return getX() == x && getY() == y;
  }

  final int WALKING_TO_BANK = 0x01;
  final int WALKING_TO_PALLYS = 0x02;
  final int THIEVING = 0x03;
  final int BANKING = 0x04;

  int currentStep;
  int walkStep;

  boolean sleeping;
  int fightMode;
  int foodId;

  boolean hideWeapon = false;
  int[] WEAPON_ID = new int[] { 594 , 593 };
		
  int[] xCoords = new int[] { 551, 550, 556, 562, 569, 575, 582, 588, 594,
        598, 603, 606, 613 };
  int[] yCoords = new int[] { 612, 608, 607, 606, 606, 606, 605, 604, 603,
        603, 603, 603, 602 };
  int[] xCoords2 = new int[] { 526, 531, 535, 539, 545, 550 };
  int[] yCoords2 = new int[] { 610, 615, 615, 615, 615, 613 };
  
	@Override
    public void onServerMessage( String message ) 
	{
        message = message.toLowerCase( Locale.ENGLISH );
		 
		if ( message.contains( "looted" ) )
			autohop( false );
	}
}