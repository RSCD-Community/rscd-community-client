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

public final class TPM_WaterfallFG extends Script
{
	private static final int[] STRENGTH_POTIONS = { 
        221 , 222 , 223 , 224
    };
	
    private Frame frame = null;
    private Choice fightModeChoice;
	private Choice weaponChoice;
	private Choice amuletChoice;
	private Choice buryOrIgnoreBonesChoice;
	private Choice foodChoice;

	private int     WEAPON_ID;
	private int     AMULET_ID;
	private boolean BURY_BONES;
	private int[]   FOOD_IDS;
	private int     FOOD_LIMIT = 12;
	private int[]   LOOT_IDS;

	private boolean DEATH_SEQUENCE;
	
    private long bank_time;
    private long menu_time;
	private long move_time;
	
	private long startTime;
	private int[] initialXP;
	
	private static final HashMap<String , Integer> weaponOptions;
	private static final HashMap<String , Integer> amuletOptions;
	private static final HashMap<String , Integer> buryOrIgnoreOptions;
	private static final HashMap<String , int[]>   foodOptions;
	
	static {
        weaponOptions = new HashMap<String , Integer>();
		weaponOptions.put( "Dragon sword" , 593 );
		weaponOptions.put( "Dragon axe" , 594 );
		
		amuletOptions = new HashMap<String , Integer>();
		amuletOptions.put( "Charged Dragonstone Amulet" , 597 );
		amuletOptions.put( "Dragonstone Amulet" , 522 );
		amuletOptions.put( "Diamond Amulet of power" , 317 );
		amuletOptions.put( "Ruby Amulet of strength" , 316 );
		amuletOptions.put( "Emerald Amulet of protection" , 315 );
		amuletOptions.put( "Gnome Emerald Amulet of protection" , 744 );
		
		buryOrIgnoreOptions = new HashMap<String, Integer>();
		buryOrIgnoreOptions.put( "Bury Big Bones" , 1 );
		buryOrIgnoreOptions.put( "Ignore Big Bones" , 0 );
		
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

    public TPM_WaterfallFG( Extension ex )
	{
        super( ex );
    }

    @Override
    public void init( String params ) 
	{
		this.initialXP = new int[SKILL.length];
		
		if ( frame == null ) 
		{
			System.out.println( "[TPM_WaterfallFG] Fire Giant Farming Script by T4hpur3mag3 initialized." );
		
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
			weaponChoice = new Choice();
			Iterator<String> iterator = weaponOptions.keySet().iterator();
			while ( iterator.hasNext() )
			{
                weaponChoice.add( iterator.next() );
            }
			amuletChoice = new Choice();
			iterator = amuletOptions.keySet().iterator();
			while ( iterator.hasNext() )
			{
                amuletChoice.add( iterator.next() );
            }
			buryOrIgnoreBonesChoice = new Choice();
			iterator = buryOrIgnoreOptions.keySet().iterator();
			while ( iterator.hasNext() )
			{
                buryOrIgnoreBonesChoice.add( iterator.next() );
            }
			foodChoice = new Choice();
			iterator = foodOptions.keySet().iterator();
            while ( iterator.hasNext() )
			{  
                foodChoice.add( iterator.next() );
            }
			pChoice.add( new Label( "Default Fight Mode" ) );
			pChoice.add( fightModeChoice );
			pChoice.add( new Label( "Default Weapon" ) );
			pChoice.add( weaponChoice );
			pChoice.add( new Label( "Default Amulet" ) );
			pChoice.add( amuletChoice );
			pChoice.add( new Label( "Bury or Ignore Bones?" ) );
			pChoice.add( buryOrIgnoreBonesChoice );
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
						TPM_WaterfallFG.this.WEAPON_ID  = weaponOptions.get( weaponChoice.getSelectedItem() );
						TPM_WaterfallFG.this.AMULET_ID  = amuletOptions.get( amuletChoice.getSelectedItem() );
						TPM_WaterfallFG.this.BURY_BONES = buryOrIgnoreOptions.get( buryOrIgnoreBonesChoice.getSelectedItem() ) == 1;
						TPM_WaterfallFG.this.FOOD_IDS   = foodOptions.get( foodChoice.getSelectedItem() );
						if ( TPM_WaterfallFG.this.BURY_BONES )
						{
							TPM_WaterfallFG.this.LOOT_IDS   = new int[] { 10 , 31 , 40 , 41 , 42 , 81 , 93 , 373 , 396 , 398 , 399 , 403 , 408 , 413 , 436 , 438 , 439 , 440 , 441 , 442 , 518 , 520 , 526 , 527 , 615 , 619 , 795 , 1277 , 444 , 445 , 446 , 447 , 448 , 449 , 450 , 451 , 452 , 453 , 221 , 222 , 223 , 224 , 465 };
						}
						else
						{
							TPM_WaterfallFG.this.LOOT_IDS   = new int[] { 10 , 31 , 40 , 41 , 42 , 81 , 93 , 373 , 396 , 398 , 399 , 403 , 408 , 436 , 438 , 439 , 440 , 441 , 442 , 518 , 520 , 526 , 527 , 615 , 619 , 795 , 1277 , 444 , 445 , 446 , 447 , 448 , 449 , 450 , 451 , 452 , 453 , 221 , 222 , 223 , 224 , 465 };
						}
						System.out.println( "[TPM_WaterfallFG] The script has been configured and can now be started." );
						TPM_WaterfallFG.this.frame.setVisible( false );
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
        bank_time = -1L;
        menu_time = -1L;
		move_time = -1L;
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
		
		if ( getFightMode() != fightModeChoice.getSelectedIndex() ) 
		{
            setFightMode( fightModeChoice.getSelectedIndex() );
			
            return random( 400 , 600 );
        }
		
		if ( getHpPercent() <= 35 )
		{
			// FLEE COMBAT
			
			if ( inCombat() )
			{
				walkTo( getX() , getY() );
				
				return random( 600 , 800 );
			}
		
			if ( insideWaterfall() )
			{
				// RUN TO LOBBY
				
				if ( insideWaterfallBackRoom() )
				{	
					int[] doors = getObjectById( 64 );
					
					if ( doors[0] != -1 && doors[1] >= 656 && doors[1] <= 663 && doors[2] >= 3282 && doors[2] <= 3302 )
					{
						atObject( doors[1] , doors[2] );
						
						return random( 600 , 800 );
					}
					else
					{
						walkTo( 659 + random( 0 , 1 ) , 3290 + random( 0 , 4 ) );
						
						return random( 1000 , 2000 );
					}
				}
				else if ( insideWaterfallHallway() )
				{
					int[] doors = getObjectById( 64 );
					
					if ( doors[0] != -1 && doors[1] >= 656 && doors[1] <= 663 && doors[2] >= 3282 && doors[2] <= 3302 )
					{
						atObject( doors[1] , doors[2] );
						
						return random( 600 , 800 );
					}
					else    
					{
						walkTo( 659 + random( 0 , 1 ) , 3297 + random( 0 , 1 ) );
						
						return random( 1000 , 2000 );
					}
				}
			}
			
			// EAT FOOD
			
			int inv_count = getInventoryCount();
						
			for ( int i = 0; i < inv_count; i++ ) 
			{
				if ( getItemCommand( i ).toLowerCase( Locale.ENGLISH ).equals( "eat" ) ) 
				{
					useItem( i );
					return random( 1400 , 1600 );
				}
			}
		}
		else if ( isBanking() )
		{
			for ( int loot_id : LOOT_IDS )
			{
				if ( getInventoryCount( loot_id ) > 0 )
				{
					if ( loot_id == 795 && getLevel( 1 ) >= 60 )
					{
						if ( getInventoryCount( loot_id ) > 1 )
						{
							deposit( loot_id , getInventoryCount( loot_id ) - 1 );
							return random( 1000 , 1500 );
						}
					}
					else
					{
						deposit( loot_id , getInventoryCount( loot_id ) );
						return random( 1000 , 1500 );
					}
				}
			}
			
			// WITHDRAW SLEEPING BAG IF NOT HOLDING
			
			if ( getInventoryCount( 1263 ) == 0 )
			{
				if ( bankCount( 1263 ) == 0 )
				{
					System.out.println( "[TPM_WaterfallFG] The script has stopped, out of sleeping bags..." );
					
					stopScript(); 
					setAutoLogin( false );
					
					return 0;
				}
			
				withdraw( 1263 , 1 );
				return random( 1000 , 1500 );
			}
			
			// WITHDRAW GLARIALS AMULET IF NOT HOLDING
			
			if ( getInventoryCount( 782 ) == 0 )
			{
				if ( bankCount( 782 ) == 0 )
				{
					System.out.println( "[TPM_WaterfallFG] The script has stopped, out of glarials amulets..." );
					
					stopScript(); 
					setAutoLogin( false );
					
					return 0;
				}
			
				withdraw( 782 , 1 );
				return random( 1000 , 1500 );
			}
			
			// WITHDRAW ROPE IF NOT HOLDING
			
			if ( getInventoryCount( 237 ) < 2 )
			{
				if ( bankCount( 237 ) <= 3 )
				{
					System.out.println( "[TPM_WaterfallFG] The script has stopped, out of rope..." );
					
					stopScript(); 
					setAutoLogin( false );
					
					return 0;
				}
			
				withdraw( 237 , 2 - getInventoryCount( 237 ) );
				return random( 1000 , 1500 );
			}
			
			// WITHDRAW WEAPON IF NOT HOLDING
			
			if ( getInventoryCount( WEAPON_ID ) == 0 )
			{
				if ( bankCount( WEAPON_ID ) == 0 )
				{
					System.out.println( "[TPM_BlueDrags] The script has stopped, out of " + weaponChoice.getSelectedItem() + "..." );
					
					stopScript(); 
					setAutoLogin( false );
					
					return 0;
				}
			
				withdraw( WEAPON_ID , 1 );
				return random( 1000 , 1500 );
			}
			
			// WITHDRAW AMULET IF NOT HOLDING
			
			if ( getInventoryCount( AMULET_ID ) == 0 )
			{
				if ( bankCount( AMULET_ID ) == 0 )
				{
					System.out.println( "[TPM_BlueDrags] The script has stopped, out of " + amuletChoice.getSelectedItem() + "..." );
					
					stopScript(); 
					setAutoLogin( false );
					
					return 0;
				}
			
				withdraw( AMULET_ID , 1 );
				return random( 1000 , 1500 );
			}
			
			// WITHDRAW FOOD IF NOT HOLDING ENOUGH
			
			int CUR_FOOD_COUNT = getInventoryCount( FOOD_IDS );
			
			if ( CUR_FOOD_COUNT < FOOD_LIMIT ) 
			{
				for ( int id : FOOD_IDS ) 
				{
					int bank_count = bankCount( id );
					
					if ( bank_count <= 0 )
					{
						continue;
					}
					
					int amount = FOOD_LIMIT - CUR_FOOD_COUNT;
					
					if ( FOOD_LIMIT - CUR_FOOD_COUNT > bank_count ) 
					{
						amount = bank_count;
					}
					
					withdraw( id , amount );
					
					return random( 1000 , 2000 );
				}
				
				System.out.println( "[TPM_WaterfallFG] The script has stopped, out of " + foodChoice.getSelectedItem() + "..." );
				
				stopScript(); 
				setAutoLogin( false );
				
				return 0;
			}
			
			closeBank();
			
			return random( 1000 , 2000 );
		}  
		else if ( getInventoryCount() == 30 || ( getInventoryCount( FOOD_IDS ) == 0 && getInventoryCount( 373 ) == 0 ) || ( getInventoryCount( 237 ) == 0 && ! ( insideWaterfall() || onWaterfallLedge() ) ) || getInventoryCount( 782 ) == 0 )
		{
			if ( getInventoryCount( 413 ) > 0 && BURY_BONES )
			{
				// FLEE COMBAT
		
				if ( inCombat() )
				{
					walkTo( getX() , getY() );
					
					return random( 600 , 800 );
				}
				int count = getInventoryCount();
				
				for ( int i = 0; i < count; i++ ) 
				{
					if (getItemCommand( i ).toLowerCase( Locale.ENGLISH ).equals( "bury" )) 
					{
						useItem( i );
						
						return random( 800 , 1000 );
					}
				}
			}
		
			if ( getHpPercent() <= 75 )
			{
				// FLEE COMBAT
			
				if ( inCombat() )
				{
					walkTo( getX() , getY() );
					
					return random( 600 , 800 );
				}
				
				// EAT FOOD
		
				int inv_count = getInventoryCount();
							
				for ( int i = 0; i < inv_count; i++ ) 
				{
					if ( getItemCommand( i ).toLowerCase( Locale.ENGLISH ).equals( "eat" ) ) 
					{
						useItem( i );
						return random( 1400 , 1600 );
					}
				}
			}
		
			if ( insideArdougneBank() )
			{
				if ( isQuestMenu() ) 
				{
					answer( 0 );
					
					bank_time = System.currentTimeMillis();
					
					return random( 3000 , 4000 );
				}
				else
				{
					int[] banker = getNpcByIdNotTalk( BANKERS );
							
					if ( banker[0] != -1 ) 
					{
						talkToNpc( banker[0] );
						
						return random( 3000 , 4000 );
					}
				}
			}
			else if ( insideWaterfall() )
			{
				if ( insideWaterfallBackRoom() )
				{	
					int[] doors = getObjectById( 64 );
					
					if ( doors[0] != -1 && doors[1] >= 656 && doors[1] <= 663 && doors[2] >= 3282 && doors[2] <= 3302 )
					{
						atObject( doors[1] , doors[2] );
						
						return random( 600 , 800 );
					}
					else
					{
						walkTo( 659 + random( 0 , 1 ) , 3290 + random( 0 , 4 ) );
						
						return random( 1000 , 2000 );
					}
				}
				else if ( insideWaterfallHallway() )
				{
					int[] doors = getObjectById( 64 );
					
					if ( doors[0] != -1 && doors[1] >= 656 && doors[1] <= 663 && doors[2] >= 3282 && doors[2] <= 3302 )
					{
						atObject( doors[1] , doors[2] );
						
						return random( 600 , 800 );
					}
					else    
					{
						walkTo( 659 + random( 0 , 1 ) , 3297 + random( 0 , 1 ) );
						
						return random( 1000 , 2000 );
					}
				}
				else if ( insideWaterfallLobby() )
				{
					int[] doors = getObjectById( 471 );
			  
					if ( doors[0] != -1 )   
					{
						atObject( doors[1] , doors[2] );
						
						return random( 1200 , 3200 );  
					}
				}
			}
			else if ( onWaterfallLedge() )
			{
				int[] waterfall = getObjectById( 469 );
			  
				if ( waterfall[0] != -1 )   
				{
					atObject( waterfall[1] , waterfall[2] );
					
					return random( 1200 , 3200 );  
				}
			}
			else if ( insideAlmeraHouse() ) 
			{ 
				walkTo( 647 + random( 0 , 1 ) , 459 + random( 0 , 1 ) );
			}  
			else if ( besideWaterfall() )
			{
				walkTo( 642 + random( 0 , 1 ) , 476 + random( 0 , 1 ) );
			}
			else if ( belowWaterfall() )  
			{
				walkTo( 643 + random( 0 , 1 ) , 492 + random( 0 , 1 ) );
			}
			else if ( nearVisitorCenter() )
			{
				walkTo( 642 + random( 0 , 1 ) , 514 + random( 0 , 1 ) );
			}
			else if ( nearBridgeNorthOfArdougne() )
			{
				walkTo( 627 + random( 0 , 1 ) , 529 + random( 0 , 1 ) );
			}
			else if ( nearWarriorsNorthOfArdougne() )
			{
				walkTo( 604 + random( 0 , 1 ) , 535 + random( 0 , 1 ) );
			}
			else if ( nearBrumtyHouse() )
			{
				walkTo( 588 + random( 0 , 1 ) , 561 + random( 0 , 1 ) );
			}
			else if ( outsideArdougneBank() )
			{
				walkTo( 582 + random( 0 , 1 ) , 574 + random( 0 , 1 ) );
			}
		}  
		else
		{	
			if ( insideWaterfall() )
			{
				// EQUIP AMULET IF UNEQUIPPED
					
				int amulet_index = getInventoryIndex( AMULET_ID );
				
				if ( amulet_index != -1 && ! isItemEquipped( amulet_index ) )
				{
					wearItem( amulet_index );
					
					return random( 800 , 1200 );
				}
				
				// EQUIP DRAGON MED IF UNEQUIPPED
					
				int med_index = getInventoryIndex( 795 );
				
				if ( med_index != -1 && ! isItemEquipped( med_index ) && getLevel( 1 ) >= 60 )
				{
					wearItem( med_index );
					
					return random( 800 , 1200 );
				}
			
				if ( getHpPercent() <= 75 )
				{
					// FLEE COMBAT
				
					if ( inCombat() )
					{
						walkTo( getX() , getY() );
						
						return random( 600 , 800 );
					}
					
					// EAT FOOD
			
					int inv_count = getInventoryCount();
								
					for ( int i = 0; i < inv_count; i++ ) 
					{
						if ( getItemCommand( i ).toLowerCase( Locale.ENGLISH ).equals( "eat" ) ) 
						{
							useItem( i );
							
							return random( 1400 , 1600 );
						}
					}
				}
				else if ( insideWaterfallLobby() )
				{
					if ( getFatigue() >= 90 )
					{
						if ( inCombat() )
						{
							walkTo( getX() , getY() );  
							
							return random( 600 , 800 );
						}
						
						if ( ! isSleeping() )
						{
							useSleepingBag();
						}
					}
					else
					{
						int[] doors = getObjectById( 64 );
						
						if ( doors[0] != -1 && doors[1] >= 656 && doors[1] <= 663 && doors[2] >= 3282 && doors[2] <= 3302 )
						{
							atObject( doors[1] , doors[2] );
							
							return random( 1000 , 2000 );
						}
						else
						{
							walkTo( 659 + random( 0 , 1 ) , 3290 + random( 0 , 4 ) );
							
							return random( 1000 , 2000 );
						}
					}
				}
				else if ( insideWaterfallHallway() )
				{
					if ( getFatigue() >= 90 )
					{
						if ( inCombat() )
						{
							walkTo( getX() , getY() );  
							
							return random( 600 , 800 );
						}
						
						int[] doors = getObjectById( 64 );
						
						if ( doors[0] != -1 && doors[1] >= 656 && doors[1] <= 663 && doors[2] >= 3282 && doors[2] <= 3302 )
						{
							atObject( doors[1] , doors[2] );
							
							return random( 600 , 800 );
						}
						else    
						{
							walkTo( 659 + random( 0 , 1 ) , 3297 + random( 0 , 1 ) );
							
							return random( 1000 , 2000 );
						}
					}
					else
					{
						int[] doors = getObjectById( 64 );

						if ( doors[0] != -1 && doors[1] >= 656 && doors[1] <= 663 && doors[2] >= 3282 && doors[2] <= 3302 )
						{
							atObject( doors[1] , doors[2] );
							
							return random( 1000 , 2000 );
						}
						else   
						{     
							walkTo( 660 , 3287 + random( 0 , 4 ) );
							
							return random( 1000 , 2000 );
						}
					}
				}   
				else if ( insideWaterfallBackRoom() )
				{
					if ( ! inCombat() )
					{
						int[] item = _getItemFast( LOOT_IDS );
						
						if ( item[0] != -1 )
						{
							pickupItem( item[0] , item[1] , item[2] );
							return random( 500 , 800 );
						}
						
						if ( getInventoryCount( 413 ) > 0 && BURY_BONES )
						{
							int count = getInventoryCount();
							
							for ( int i = 0; i < count; i++ ) 
							{
								if (getItemCommand( i ).toLowerCase( Locale.ENGLISH ).equals( "bury" )) 
								{
									useItem( i );
									return random( 1200 , 1600 );
								}
							}
						}
						
						if ( getFatigue() >= 90 )
						{
							if ( inCombat() )
							{
								walkTo( getX() , getY() );  
								
								return random( 600 , 800 );
							}
							
							int[] doors = getObjectById( 64 );
						
							if ( doors[0] != -1 && doors[1] >= 656 && doors[1] <= 663 && doors[2] >= 3282 && doors[2] <= 3302 )
							{
								atObject( doors[1] , doors[2] );
								
								return random( 600 , 800 );
							}
							else
							{
								walkTo( 659 + random( 0 , 1 ) , 3290 + random( 0 , 4 ) );
								
								return random( 1000 , 2000 );
							}
						}
						
						// DRINK A STRENGTH POTION IF WE GOT ANY
		
						int maxStrengthLevel = getLevel( 2 ) + (int)Math.floor( getLevel( 2 ) * 0.10 ) + 3;
						int strengthPotion = getInventoryIndex( STRENGTH_POTIONS );
						
						if ( strengthPotion != -1 && getCurrentLevel( 2 ) <= maxStrengthLevel - 3 ) 
						{
							useItem( strengthPotion );
							return random( 700 , 900 );
						}
					
						int[] npc = _getReachableNpc( 344 );
							
						if ( npc[0] != -1 && npc[1]  >= 656 && npc[1] <= 663 && npc[2] >= 3282 && npc[2] <= 3288 )
						{
							if ( distanceTo( npc[1] , npc[2] ) > 5 ) 
							{
								_walkApprox( npc[1] , npc[2] , 1 );
							}
							
							attackNpc( npc[0] );
							
							return random( 200 , 500 );
						}
					}
					
					return 100;
				}
			}
			else
			{
				if ( insideArdougneBank() )
				{
					// EQUIP WEAPON IF UNEQUIPPED
					
					int weapon_index = getInventoryIndex( WEAPON_ID );
					
					if ( weapon_index != -1 && ! isItemEquipped( weapon_index ) )
					{
						wearItem( weapon_index );
						
						return random( 800 , 1200 );
					}
					
					// EQUIP AMULET IF UNEQUIPPED
					
					int amulet_index = getInventoryIndex( AMULET_ID );
					
					if ( amulet_index != -1 && ! isItemEquipped( amulet_index ) )
					{
						wearItem( amulet_index );
						
						return random( 800 , 1200 );
					}
					
					walkTo( 590 + random( 0 , 1 ) , 559 + random( 0 , 1 ) );
				}
				else if ( outsideArdougneBank() )
				{
					walkTo( 604 + random( 0 , 1 ) , 540 + random( 0 , 1 ) );
				}
				else if ( nearBrumtyHouse() )
				{
					walkTo( 623 + random( 0 , 1 ) , 536 + random( 0 , 1 ) );
				}
				else if ( nearWarriorsNorthOfArdougne() )
				{
					walkTo( 641 + random( 0 , 1 ) , 522 + random( 0 , 1 ) );
				}
				else if ( nearBridgeNorthOfArdougne() )
				{
					walkTo( 640 + random( 0 , 1 ) , 500 + random( 0 , 1 ) );
				}
				else if ( nearVisitorCenter() )
				{
					walkTo( 643 + random( 0 , 1 ) , 482 + random( 0 , 1 ) );
				}
				else if ( belowWaterfall() )
				{
					walkTo( 647 + random( 0 , 1 ) , 459 + random( 0 , 1 ) );
				}
				else if ( besideWaterfall() )
				{
					walkTo( 653 + random( 0 , 1 ) , 448 + random( 0 , 1 ) );
				}
				else if ( insideAlmeraHouse() ) 
				{  
					int[] raft = getObjectById( 464 );

					if ( raft[0] != -1 )
					{
						atObject( raft[1] , raft[2] );
						
						return random( 3200 , 5600 );
					}
					else
					{
						walkTo( 659 , 447 + random( 1 , 2 ) );
					}
				}
				else if ( onFirstRiverIsland() )
				{
					int   rope = getInventoryIndex( 237 );
					int[] tree = getObjectById( 462 );
			  
					if ( rope != -1 && tree[0] != -1 )
					{
						useItemOnObject( 237 , tree[1] , tree[2] );
						
						return random( 3200 , 5600 );
					}
				}
				else if ( onSecondRiverIsland() )
				{
					int   rope = getInventoryIndex( 237 );
					int[] tree = getObjectById( 463 );
			  
					if ( rope != -1 && tree[0] != -1 )  
					{
						useItemOnObject( 237 , tree[1] , tree[2] );
						
						return random( 3200 , 5600 );
					}
				}
				else if ( onThirdRiverIsland() )
				{
					int   rope = getInventoryIndex( 237 );
					int[] tree = getObjectById( 482 );
			  
					if ( rope != -1 && tree[0] != -1 )   
					{
						useItemOnObject( 237 , tree[1] , tree[2] );
						
						return random( 3200 , 5600 );  
					}
				}
				else if ( onWaterfallLedge() )
				{
					int amulet = getInventoryIndex( 782 );
					
					if ( amulet != -1 )
					{  
						if ( ! isItemEquipped( amulet ) )
						{
							wearItem( amulet );
							
							return random( 1200 , 2400 );
						}
					}
					  
					int[] doors = getObjectById( 471 );
			  
					if ( doors[0] != -1 )   
					{
						atObject( doors[1] , doors[2] );
						
						return random( 1200 , 3200 );  
					}
				}
			}
		}
        
		return random( 3300 , 5400 );
    }
	
	private void outOfBounds()
	{
		System.out.println( "[TPM_WaterfallFG] This script requires you to be standing in the waterfall, ardougne bank, or lumbridge respawn" );
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
	
	private boolean outsideArdougneBank()
	{
		return ( getX() >= 578 && getX() <= 593 && getY() >= 559 && getY() <= 571 );
	}
	
	private boolean nearBrumtyHouse()
	{
		return ( getX() >= 587 && getX() <= 607 && getY() >= 534 && getY() <= 558 );
	}
	
	private boolean nearWarriorsNorthOfArdougne()
	{
		return ( getX() >= 608 && getX() <= 633 && getY() >= 526 && getY() <= 544 );
	}
	
	private boolean nearBridgeNorthOfArdougne()
	{
		return ( getX() >= 634 && getX() <= 655 && getY() >= 507 && getY() <= 537 );
	}
	
	private boolean nearVisitorCenter()
	{
		return ( getX() >= 639 && getX() <= 656 && getY() >= 486 && getY() <= 506 );
	}
	
	private boolean belowWaterfall()
	{
		return ( getX() >= 636 && getX() <= 654 && getY() >= 471 && getY() <= 485 );
	}
	
	private boolean besideWaterfall()  
	{
		return ( getX() >= 636 && getX() <= 659 && getY() >= 452 && getY() <= 470 ) || ( getX() >= 646 && getX() <= 651 && getY() >= 447 && getY() <= 451 );
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
			int id = getGroundItemId( i );
		
            if ( id != 795 && id != 1277 && (getItemX( i ) != x || getItemY( i ) != y ) )
			{
                continue;
            }
		
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
    public void onServerMessage( String str ) 
	{
        str = str.toLowerCase( Locale.ENGLISH );
		   
        if ( str.contains( "busy" ) ) 
		{
            menu_time = -1L;
        }
		else if ( str.contains( "standing here" ) ) 
		{
            move_time = System.currentTimeMillis() + random( 1500 , 1800 );
		}
	}
	
	public void paint() 
	{
        final int orange = 0xFFD900;
        final int white  = 0xFFFFFF;
		
        int   x  = 105;
        int   y  = 40;
		int[] xp;
		
		drawString( "TPM_WaterfallFG" , x + 10 , y , 1 , orange );
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
