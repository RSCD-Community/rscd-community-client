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

public final class TPM_JungleSpiders extends Script
{	
    private Frame frame = null;
    private Choice fightModeChoice;
	private Choice foodChoice;

	private int[]   FOOD_IDS;
	private int[]   LOOT_IDS;
	
	private long moveTime;
	private long startTime;
	private int moveX , moveY;
	
	private int[] initialXP;
	
	private static final int GNOME_BALL  = 981;
	
	private static final HashMap<String , int[]>   foodOptions;
	
	static {
	    foodOptions = new HashMap<String , int[]>();
        foodOptions.put( "Shrimp" , new int[] { 350 } );
        foodOptions.put( "Anchovy" , new int[] { 352 } );
        foodOptions.put( "Sardine" , new int[] { 355 } );
        foodOptions.put( "Salmon" , new int[] { 357 } );
        foodOptions.put( "Trout" , new int[] { 359 } );
        foodOptions.put( "Herring" , new int[] { 362 } );
        foodOptions.put( "Pike" , new int[] { 364 } );
        foodOptions.put( "Tuna" , new int[] { 367 } );
        foodOptions.put( "Swordfish" , new int[] { 370 } );
        foodOptions.put( "Lobster" , new int[] { 373 } );
        foodOptions.put( "Shark" , new int[] { 546 } );
        foodOptions.put( "Manta ray" , new int[] { 1191 } );
        foodOptions.put( "Sea turtle" , new int[] { 1193 } );
        foodOptions.put( "Kebab" , new int[] { 210 } );
        foodOptions.put( "Cake" , new int[] { 330, 333, 335 } );
        foodOptions.put( "Chocolate cake" , new int[] { 332, 334, 336 } );
        foodOptions.put( "Meat pizza" , new int[] { 326, 328 } );
        foodOptions.put( "Anchovy pizza" , new int[] { 327, 329 } );
    }

    public TPM_JungleSpiders( Extension ex )
	{
        super( ex );
    }

    @Override
    public void init( String params ) 
	{
		this.initialXP = new int[SKILL.length];
		
		if ( frame == null ) 
		{
			System.out.println( "[TPM_JungleSpiders] Jungle spider farming script, banks Yanille by T4hpur3mag3 initialized." );
		
			Frame frame = new Frame( getClass().getSimpleName() );
            frame.setIconImages( Constants.ICONS );
            frame.addWindowListener(
                new StandardCloseHandler( frame, StandardCloseHandler.HIDE )
            );
			
			Panel pChoice = new Panel( new GridLayout( 0 , 1 ) );
			fightModeChoice = new Choice();
			for ( int i = 0; i < FIGHTMODES.length; ++i ) 
			{
				fightModeChoice.add( FIGHTMODES[i] );
			}
			foodChoice = new Choice();
			Iterator<String> iterator = foodOptions.keySet().iterator();
            while ( iterator.hasNext() )
			{  
                foodChoice.add( iterator.next() );
            }
			pChoice.add( new Label( "Default Fight Mode" ) );
			pChoice.add( fightModeChoice );
			pChoice.add( new Label( "Default Food" ) );
			pChoice.add( foodChoice );
			Panel pButtons = new Panel();
			Button button  = new Button("Save Configuration");
			
			button.addActionListener( new ActionListener() 
			{
				public void actionPerformed( ActionEvent e )
				{
					try
					{
						TPM_JungleSpiders.this.FOOD_IDS = foodOptions.get( foodChoice.getSelectedItem() );
						TPM_JungleSpiders.this.LOOT_IDS = new int[] {};
						System.out.println( "[TPM_JungleSpiders] The script has been configured and can now be started." );
						TPM_JungleSpiders.this.frame.setVisible( false );
					}
					catch( Exception ex )
					{
					
					}
				}
			});
			
			pButtons.add( button );
			frame.add( pChoice , BorderLayout.CENTER );
            frame.add( pButtons , BorderLayout.SOUTH );
            frame.setResizable( false );
            frame.pack();
            this.frame = frame;
        }
		
        frame.setLocationRelativeTo( null );
        frame.setVisible( true );
		this.moveTime = -1L;
		this.startTime = -1L;
    }

    @Override
    public int main() 
	{
		if ( this.startTime == -1L )
            this.startTime = System.currentTimeMillis();
		
		if ( this.initialXP[3] == 0 ) 
		{
            for ( int i = 0; i < SKILL.length; i++ ) 
                this.initialXP[i] = getXpForLevel( i );
            return 500;
        }
		
		if ( this.moveTime != -1L ) 
		{
			if ( getX() == this.moveX && getY() == this.moveY )
				walkTo( getX() + random( 0 , 2 ) - random( 0 , 4 ) , getY() + random( 0 , 2 ) - random( 0 , 4 ) );
			else
				this.moveTime = -1L;
			
			return random( 1000 , 1500 );
		}
		
		if ( getFightMode() != fightModeChoice.getSelectedIndex() ) 
		{
            setFightMode( fightModeChoice.getSelectedIndex() );
            return random( 400 , 600 );
        }
		
		if ( getHpPercent() <= 66 )
		{
			if ( inCombat() )
			{
				walkTo( getX() , getY() );
				return random( 600 , 800 );
			}
		
			int inventoryCount = getInventoryCount();		
			for ( int i = 0; i < inventoryCount; i++ ) 
			{
				if ( getItemCommand( i ).toLowerCase( Locale.ENGLISH ).equals( "eat" ) ) 
				{
					useItem( i );
					return random( 1400 , 1600 );
				}
			}
		}
		
		if( isQuestMenu() )  
		{ 
			answer( 0 );
			return 5500;
		}
		
		if ( isBanking() )
		{
			for ( int lootID : LOOT_IDS )
			{
				if ( getInventoryCount( lootID ) > 0 )
				{
					deposit( lootID , getInventoryCount( lootID ) );
					return random( 1000 , 1500 );
				}
			}
			
			if ( getInventoryCount( 1263 ) == 0 )
			{
				if ( bankCount( 1263 ) == 0 )
				{
					System.out.println( "[TPM_JungleSpiders] The script has stopped, out of sleeping bags..." );
					stopScript(); 
					setAutoLogin( false );
					return 0;
				}
				withdraw( 1263 , 1 );
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount() < 30 ) 
			{
				for ( int foodID : FOOD_IDS ) 
				{
					int bankCount = bankCount( foodID );
					if ( bankCount <= 0 )
						continue;
					int amountToWithdraw = 30 - getInventoryCount();
					if ( amountToWithdraw > bankCount ) 
						amountToWithdraw = bankCount;
					withdraw( foodID , amountToWithdraw );
					return random( 1000 , 2000 );
				}
				System.out.println( "[TPM_JungleSpiders] The script has stopped, out of " + foodChoice.getSelectedItem() + "..." );
				stopScript(); 
				setAutoLogin( false );
				return 0;
			}
			
			closeBank();
			return random( 1000 , 2000 );
		}  
		
		if ( getFatigue() > 90 )
        {
            useSleepingBag();
            return random( 1000 , 2000 );
        }
		
		int gnomeBall = getInventoryIndex( GNOME_BALL );
        if ( gnomeBall != -1 ) 
		{
            dropItem( gnomeBall );
            return random( 1200 , 2000 );
        }
		
		if ( getInventoryCount( FOOD_IDS ) == 0 )
		{
			if ( getX() > 584 )
			{
				int[] banker = getNpcByIdNotTalk( BANKERS );	
				if ( banker[0] != -1 ) 
				{
					talkToNpc( banker[0] );
					return random( 3000 , 4000 );
				}
			}
			else if ( getX() > 579 && getX() <= 584 )
			{
				int[] bankDoors = getObjectById( 64 );
				if ( bankDoors[0] != -1 ) 
				{
					atObject( bankDoors[1] , bankDoors[2] );
					return random( 1000 , 1500 );
				}
				walkTo( 586 , 752 );
				return random( 1000 , 1500 );
			}
			else if ( getX() > 568 && getX() <= 579 )
			{
				walkTo( 581 , 751 );
				return random( 1000 , 1200 );
			}
			else if ( getX() > 557 && getX() <= 568 )
			{
				walkTo( 569 , 747 );
				return random( 1000 , 1200 );
			}
			else if ( getX() > 543 && getX() <= 557 )
			{
				walkTo( 558 , 746 );
				return random( 1000 , 1200 );
			}  
			else
			{
				walkTo( 544 , 747 );
			}
		}
		else
		{
			if ( getX() > 584 )
			{
				int[] bankDoors = getObjectById( 64 );
				if ( bankDoors[0] != -1 ) 
				{
					atObject( bankDoors[1] , bankDoors[2] );
					return random( 1000 , 1500 );
				}
				
				walkTo( 581 - random( 0 , 1 ) + random( 0 , 2 ) , 749 );
				return random( 1000 , 1200 );
			}
			if ( getX() > 579 && getX() <= 584 )
			{
				walkTo( 573 - random( 0 , 4 ) + random( 0 , 4 ) , 744 - random( 0 , 4 ) + random( 0 , 4 ) );
				return random( 1000 , 1200 );
			}
			else if ( getX() > 568 && getX() <= 579 )
			{
				walkTo( 568 , 747 );
				return random( 1000 , 1200 );
			}
			else if ( getX() > 557 && getX() <= 568 )
			{
				walkTo( 557 , 747 );
				return random( 1000 , 1200 );
			}
			else if ( getX() > 543 && getX() <= 557 )
			{
				walkTo( 543 , 747 );
				return random( 1000 , 1200 );
			}
			else
			{
				int[] npc = _getReachableNpc( 521 );
							
				if ( npc[0] != -1 )
				{
					if ( distanceTo( npc[1] , npc[2] ) > 5 ) 
					{
						_walkApprox( npc[1] , npc[2] , 1 );
						return random( 200 , 500 );
					}
					if ( ! inCombat() )
					{
						attackNpc( npc[0] );
						return random( 200 , 500 );
					}
				}
			}
		}
		
		return 10;
    }
	
    @Override
    public void onServerMessage( String str ) 
	{
        str = str.toLowerCase( Locale.ENGLISH );
		   
        if ( str.contains( "standing" ) ) 
		{
			this.moveX = getX();
			this.moveY = getY();
			this.moveTime = System.currentTimeMillis();
		}
	}
	
	public void paint() 
	{
        final int orange = 0xFFD900;
        final int white  = 0xFFFFFF;
		
        int   x  = 105;
        int   y  = 40;
		int[] xp;
		
		drawString( "TPM_JungleSpiders" , x + 10 , y , 1 , orange );
		y += 15;
        drawString( "Runtime: " + _getRuntime() , x + 10 , y , 1 , white );
		xp = getXpStatistics( 0 );
		if ( xp[2] > 0 )
		{
			y += 15;
			drawString( "Attack XP: " + xp[2] + " - " + xp[3] + " XP/Hr" , x + 10 , y , 1 , white );
		}
		xp = getXpStatistics( 2 );
		if ( xp[2] > 0 )
		{
			y += 15;
			drawString( "Strength XP: " + xp[2] + " - " + xp[3] + " XP/Hr" , x + 10 , y , 1 , white );
		}	
		xp = getXpStatistics( 1 );
		if ( xp[2] > 0 )
		{
			y += 15;
			drawString( "Defense XP: " + xp[2] + " - " + xp[3] + " XP/Hr" , x + 10 , y , 1 , white );
		}
		xp = getXpStatistics( 3 );
		if ( xp[2] > 0 )
		{
			y += 15;
			drawString( "HP XP: " + xp[2] + " - " + xp[3] + " XP/Hr" , x + 10 , y , 1 , white );
		}
		xp = getXpStatistics( 4 );
		if ( xp[2] > 0 )
		{
			y += 15;
			drawString( "Ranged XP: " + xp[2] + " - " + xp[3] + " XP/Hr" , x + 10 , y , 1 , white );
		}
		xp = getXpStatistics( 5 );
		if ( xp[2] > 0 )
		{
			y += 15;
			drawString( "Prayer XP: " + xp[2] + " - " + xp[3] + " XP/Hr" , x + 10 , y , 1 , white );
		}
		xp = getXpStatistics( 6 );
		if ( xp[2] > 0 )
		{
			y += 15;
			drawString( "Magic XP: " + xp[2] + " - " + xp[3] + " XP/Hr" , x + 10 , y , 1 , white );
		}
    }
	
	private int[] getXpStatistics( int skill ) 
	{
        long time = ( ( System.currentTimeMillis() - ( this.startTime > 0L ? this.startTime : 1L ) ) / 1000L );
        if (time < 1L) 
            time = 1L;
        int startXP    = this.initialXP[skill];
        int currentXP  = getXpForLevel( skill );
        int[] intArray = new int[4];
        intArray[0] = currentXP;
        intArray[1] = startXP;
        intArray[2] = intArray[0] - intArray[1];
        intArray[3] = (int)( ( ( ( currentXP - startXP ) * 60L ) * 60L ) / time );
        return intArray;
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
	
	private String _getRuntime() {
        long secs = ((System.currentTimeMillis() - startTime) / 1000L);
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
