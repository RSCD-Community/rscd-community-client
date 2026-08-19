import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

public final class TPM_CutAndFletch extends Script
{
    private long bank_time;
    private long menu_time;
	private long start_time;
	private long move_time;
	private int shafts;
	
    public TPM_CutAndFletch( Extension ex )
	{
        super( ex );
    }

    @Override
    public void init( String params ) 
	{
		move_time = -1L;
		start_time = -1L;
		shafts = 0;
    }

    @Override
    public int main() 
	{
		if ( start_time == -1L )
            start_time = System.currentTimeMillis();
  
	
		if ( isQuestMenu() )
		{
			answer( 0 );
			return random( 900 , 1200 );
		}
		
		if ( getFatigue() >= 90 )
		{	
			if ( ! isSleeping() )
			{
				useSleepingBag();
				
				return random( 1200 , 2400 );
			}
		}
		
		if ( getInventoryCount( 14 ) > 0 )
		{
			int knife = getInventoryIndex( 13 );
			int logs  = getInventoryIndex( 14 );
			  
			if ( knife != -1 && logs != -1 )
			{
				useItemWithItem(  knife , logs );
				
				return random( 800 , 1000 );
			}
			
			return random( 1000 , 1200 );
		}
		
		int[] tree = getObjectById( new int[]{ 0 , 1 } );
			  
		if ( tree[0] != -1 )
		{
			atObject( tree[1] , tree[2] );
			
			return random( 1200 , 2400 );
		}
		
		return random( 900 , 1200 );
    }
	
	private void outOfBounds()
	{
		System.out.println( "[TPM_CutAndFletch] This script requires you to be standing in the ardougne bank" );
	}
	
	private boolean insideArdougneBank()
	{
		return ( getX() >= 577 && getX() <= 585 && getY() >= 572 && getY() <= 576 );
	}
	
	private boolean nearArdougneNorthGate()
	{
		return ( getX() >= 562 && getX() <= 574 && getY() >= 554 && getY() <= 574 );
	}
	  
	private boolean nearArdougneNorthFarm()
	{  
		return ( getX() >= 550 && getX() <= 569 && getY() >= 536 && getY() <= 554 );
	}
	
	private boolean insideAlmeraHouse()
	{
		return ( getX() >= 652 && getX() <= 658 && getY() >= 446 && getY() <= 451 ) || ( getX() == 659 && getY() >= 448 && getY() <= 449 );
	}
	
	private boolean onFirstRiverIsland()
	{
		return ( getX() == 662 && getY() == 463 );
	}
	
	private boolean onSecondRiverIsland()
	{
		return ( getX() == 662 && getY() == 467 );
	}
	
	private boolean onThirdRiverIsland()
	{
		return ( getX() == 659 && getY() == 471 );
	}
	
	private boolean onWaterfallLedge()
	{
		return ( getX() >= 659 && getX() <= 660 && getY() >= 3303 && getY() <= 3305 );
	}
	
	private boolean insideWaterfall()
	{
		return ( getX() >= 656 && getX() <= 663 && getY() >= 3282 && getY() <= 3302 );
	}
	
	private boolean insideWaterfallLobby()
	{
		return ( getX() >= 657 && getX() <= 662 && getY() >= 3295 && getY() <= 3302 );
	}
	
	private boolean insideWaterfallHallway()
	{
		return ( getX() >= 659 && getX() <= 660 && getY() >= 3289 && getY() <= 3294 );
	}
	
	private boolean insideWaterfallBackRoom()
	{
		return ( getX() >= 656 && getX() <= 663 && getY() >= 3282 && getY() <= 3288 );
	}
	
	private boolean insideCamelotGardens()
	{
		return ( getX() >= 455 && getX() <= 473 && getY() >= 454 && getY() <= 462 );
	}
	
	private boolean insideLumbridgeRespawn()
	{
		return ( getX() >= 113 && getX() <= 124 && getY() >= 643 && getY() <= 654 );
	}
	
	private boolean nearLumbridgeGeneralStore()
	{
		return ( getX() >= 125 && getX() <= 141 && getY() >= 634 && getY() <= 649 );
	}
	
	private boolean insideLumbridgeForestEast()
	{
		return ( getX() >= 140 && getX() <= 171 && getY() >= 634 && getY() <= 657 );
	}
	
	private boolean insideLumbridgeForestCentral()
	{
		return ( getX() >= 172 && getX() <= 186 && getY() >= 623 && getY() <= 652 );
	}
	
	private boolean insideLumbridgeForestWest()
	{
		return ( getX() >= 187 && getX() <= 207 && getY() >= 623 && getY() <= 652 );
	}
	
	private boolean insideDraynor()
	{
		return ( getX() >= 208 && getX() <= 231 && getY() >= 618 && getY() <= 648 );
	}
	
	private boolean insideDraynorBank()
	{
		return ( getX() >= 217 && getX() <= 223 && getY() >= 634 && getY() <= 638 );
	}
	
	private boolean insideHeroesGuild()
	{
		return ( getX() >= 368 && getX() <= 382 && getY() >= 434 && getY() <= 440 );
	}
	
	private boolean insideHeroesGuildBasement()
	{
		return ( getX() >= 368 && getX() <= 376 && getY() >= 3270 && getY() <= 3278 );
	}
	
	private boolean insideHeroesGuildDragonCage()
	{
		return ( getX() >= 373 && getX() <= 376 && getY() >= 3270 && getY() <= 3275 );
	}
	
	private boolean insideHeroesGuildChurch()
	{
		return ( getX() >= 369 && getX() <= 376 && getY() >= 1379 && getY() <= 1383 );
	}
	
	private boolean outsideHeroesGuild()
	{
		return ( getX() >= 371 && getX() <= 388 && getY() >= 441 && getY() <= 459 );
	}
	
	private boolean outsideMountainPassEast()
	{
		return ( getX() >= 371 && getX() <= 389 && getY() >= 459 && getY() <= 474 );
	}
	
	private boolean insideMountainPassEast()
	{
		return ( getX() >= 385 && getX() <= 401 && getY() >= 3291 && getY() <= 3304 );
	}
	
	private boolean insideMountainPass()
	{
		return ( getX() >= 385 && getX() <= 429 && getY() >= 3291 && getY() <= 3304 );
	}
	
	private boolean insideMountainPassWest()
	{
		return ( getX() >= 402 && getX() <= 429 && getY() >= 3291 && getY() <= 3304 );
	}
	
	private boolean outsideMountainPassWest()
	{
		return ( getX() >= 422 && getX() <= 446 && getY() >= 454 && getY() <= 464 );
	}
	
	private boolean outsideCatherby()
	{
		return ( getX() >= 422 && getX() <= 452 && getY() >= 465 && getY() <= 478 );
	}
	
	private boolean insideCatherby()  
	{
		return ( getX() >= 415 && getX() <= 454 && getY() >= 478 && getY() <= 502 );
	}
	
	private boolean infrontOfCatherbyBank()
	{
		return ( getX() >= 437 && getX() <= 443 && getY() >= 497 && getY() <= 502 );
	}
	
	private boolean insideCatherbyBank()
	{
		return ( getX() >= 427 && getX() <= 443 && getY() >= 491 && getY() <= 496 );
	}
	
	private int[] _getItemFast( int... ids ) 
	{
        int count = getGroundItemCount();
        int x = getX();
        int y = getY();
		
        for ( int i = 0; i < count; ++i ) 
		{
            if (getItemX( i ) != x || getItemY( i ) != y ) 
			{
                continue;
            }
			
            int id = getGroundItemId( i );
			
            if ( inArray( ids , id ) ) 
			{
                return new int[] { id , x , y };
            }
        }
        return new int[] { -1, -1, -1 };
    }

    private int[] _getReachableNpc( int... ids ) 
	{
        int[] npc = new int[] { -1, -1, -1 };
        int max_dist = Integer.MAX_VALUE;
        int count = countNpcs();
        
		for ( int i = 0; i < count; i++ ) 
		{
            if ( isNpcInCombat( i ) ) 
			{
				continue;
            }
			
			if ( inArray( ids , getNpcId( i ) ) )
			{
                int x = getNpcX( i );
                int y = getNpcY( i );
				
                if ( ! isReachable( x , y ) ) 
				{
					continue;
                }
				
                int dist = distanceTo( x , y , getX() , getY() );
				
                if ( dist < max_dist ) 
				{
                    npc[0] = i;
                    npc[1] = x;
                    npc[2] = y;
                    max_dist = dist;
                }
            }
        }
        return npc;
    }

    private void _walkApprox( int x , int y , int range ) 
	{
        int dx, dy;
        int loop = 0;
        do 
		{
            dx = x + random( -range , range );
            dy = y + random( -range , range );
            if ( ( ++loop ) > 100 ) return;
        } 
		while ( ! isReachable( dx , dy ) );
        walkTo( dx, dy );
    }
	
    @Override
    public void walkTo( int x , int y ) 
	{
        if ( ! isWalking() ) 
		{
            super.walkTo( x , y );
        }
    }
    
    @Override
    public void talkToNpc( int i ) 
	{
        super.talkToNpc( i );
        menu_time = System.currentTimeMillis();
    }
    
    @Override
    public void useOnNpc( int npc , int slot ) 
	{
        super.useOnNpc( npc , slot );
        menu_time = System.currentTimeMillis();
    }
	
	@Override
    public void paint() 
	{
        final int orange = 0xFFD900;
        final int white = 0xFFFFFF;
        int x = 105;
        int y = 40;
        drawString("Runtime: " + _getRuntime(), x + 10, y, 1, white);
		y+=15;
		drawString("Arrow shafts: " + shafts , x + 10, y, 1, white);
    }
	
    @Override
    public void onServerMessage( String str ) 
	{
        str = str.toLowerCase( Locale.ENGLISH );
		
		if ( str.contains( "10 arrow shafts" ) )
			shafts += 10;
	}
	
	private String _getRuntime() {
        long secs = ((System.currentTimeMillis() - start_time) / 1000L);
        if (secs >= 3600) {
            return (secs / 3600) + " hours, " +
                    ((secs % 3600) / 60) + " mins, " +
                    (secs % 60) + " secs.";
        }
        if (secs >= 60) {
            return secs / 60 + " mins, " +
                    (secs % 60) + " secs.";
        }
        return secs + " secs.";
    }
}
