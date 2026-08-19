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

public final class TPM_BlueDrags extends Script
{
    private Frame frame = null;
    private Choice fightModeChoice;
	private Choice weaponChoice;
	private Choice buryOrBankBonesChoice;
	private Choice foodChoice;
	private TextField foodTextField;
	
	private static final int[] PICKAXES = {
        1262, 1261, 1260, 1259, 1258, 156
    };

	private int     WEAPON_ID;
	private boolean BURY_BONES;
	private int[]   FOOD_IDS;
	private int     FOOD_LIMIT;

	private boolean DEATH_SEQUENCE;
	
    private long bank_time;
    private long menu_time;
	private long move_time;
	
	private static final HashMap<String , Integer>     weaponOptions;
	private static final HashMap<String , Integer> buryOrBankOptions;
	private static final HashMap<String , int[]>   foodOptions;
	
	static {
        weaponOptions = new HashMap<String , Integer>();
		weaponOptions.put( "Dragon sword" , 593 );
		weaponOptions.put( "Dragon axe" , 594 );
		
		buryOrBankOptions = new HashMap<String, Integer>();
		buryOrBankOptions.put( "Bury Dragon Bones" , 1 );
		buryOrBankOptions.put( "Bank Dragon Bones" , 0 );
		
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

    public TPM_BlueDrags( Extension ex )
	{
        super( ex );
    }

    @Override
    public void init( String params ) 
	{
		if ( frame == null ) 
		{
			System.out.println( "[TPM_BlueDrags] Blue Dragon Farming Script by T4hpur3mag3 initialized." );
		
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
			buryOrBankBonesChoice = new Choice();
			iterator = buryOrBankOptions.keySet().iterator();
			while ( iterator.hasNext() )
			{
                buryOrBankBonesChoice.add( iterator.next() );
            }
			foodChoice = new Choice();
			iterator = foodOptions.keySet().iterator();
            while ( iterator.hasNext() )
			{  
                foodChoice.add( iterator.next() );
            }
			foodTextField = new TextField( "5" );
			pChoice.add( new Label( "Default Fight Mode" ) );
			pChoice.add( fightModeChoice );
			pChoice.add( new Label( "Default Weapon" ) );
			pChoice.add( weaponChoice );
			pChoice.add( new Label( "Bury or Bank Bones?" ) );
			pChoice.add( buryOrBankBonesChoice );
			pChoice.add( new Label( "Default Food" ) );
			pChoice.add( foodChoice );
			pChoice.add( new Label( "How much food per run?" ) );
			pChoice.add( foodTextField );
			Panel pButtons = new Panel();
			Button button  = new Button("Save Configuration");
			
			button.addActionListener( new ActionListener() 
			{
				public void actionPerformed( ActionEvent e )
				{
					try
					{
						TPM_BlueDrags.this.WEAPON_ID  = weaponOptions.get( weaponChoice.getSelectedItem() );
						TPM_BlueDrags.this.BURY_BONES = buryOrBankOptions.get( buryOrBankBonesChoice.getSelectedItem() ) == 1;
						TPM_BlueDrags.this.FOOD_IDS   = foodOptions.get( foodChoice.getSelectedItem() );
						TPM_BlueDrags.this.FOOD_LIMIT = Integer.parseInt( foodTextField.getText() );
						System.out.println( "[TPM_BlueDrags] The script has been configured and can now be started." );
						TPM_BlueDrags.this.frame.setVisible( false );
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
    }

    @Override
    public int main() 
	{
		int[] rock = getObjectById( 210 ); 

		// MINE ANY RUNE ROCKS IF IT IS AVAILABLE AND YOU ARE HOLDING A PICKAXE
		
		if ( rock[0] != -1 && getLevel( 14 ) >= 85 && getInventoryIndex( PICKAXES ) > -1 )
		{
			atObject( rock[1] , rock[2] );
			return random( 750 , 950 );
		}
		
        if ( getFightMode() != fightModeChoice.getSelectedIndex() ) 
		{
            setFightMode( fightModeChoice.getSelectedIndex() );
			
            return random( 400 , 600 );
        }
	
		if ( isBanking() )
		{
			if ( getInventoryCount( 10 ) > 0 )
			{
				deposit( 10 , getInventoryCount( 10 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 31 ) > 0 )
			{
				deposit( 31 , getInventoryCount( 31 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 32 ) > 0 )
			{
				deposit( 32 , getInventoryCount( 32 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 40 ) > 0 )
			{
				deposit( 40 , getInventoryCount( 40 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 111 ) > 0 )
			{
				deposit( 111 , getInventoryCount( 111 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 130 ) > 0 )
			{
				deposit( 130 , getInventoryCount( 130 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 154 ) > 0 )
			{
				deposit( 154 , getInventoryCount( 154 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 436 ) > 0 )
			{
				deposit( 436 , getInventoryCount( 436 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 438 ) > 0 )
			{
				deposit( 438 , getInventoryCount( 438 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 526 ) > 0 )
			{
				deposit( 526 , getInventoryCount( 526 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 527 ) > 0 )
			{
				deposit( 527 , getInventoryCount( 527 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 795 ) > 0 )
			{
				deposit( 795 , getInventoryCount( 795 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 814 ) > 0 )
			{
				deposit( 814 , getInventoryCount( 814 ) );
				
				return random( 1000 , 1500 );
			}
			
			if ( getInventoryCount( 1277 ) > 0 )
			{
				deposit( 1277 , getInventoryCount( 1277 ) );
				
				return random( 1000 , 1500 );
			}
			
			// WITHDRAW SLEEPING BAG IF NOT HOLDING
			
			if ( getInventoryCount( 1263 ) == 0 )
			{
				if ( bankCount( 1263 ) == 0 )
				{
					System.out.println( "[TPM_BlueDrags] The script has stopped, out of sleeping bags..." );
					
					stopScript(); 
					setAutoLogin( false );
					
					return 0;
				}
			
				withdraw( 1263 , 1 );
				
				return random( 1000 , 1500 );
			}
			
			// IF INSIDE DRAYNOR BANK WITHDRAW TELE RUNES ALSO
			
			if ( insideDraynorBank() )
			{
				if ( getInventoryCount( 33 ) < 5 )
				{
					if ( bankCount( 33 ) == 0 )
					{
						System.out.println( "[TPM_BlueDrags] The script has stopped, out of cammy tele runes..." );
						
						stopScript(); 
						setAutoLogin( false );
						
						return 0;  
					}
					
					withdraw( 33 , 5 - getInventoryCount( 33 ) );
					
					return random( 1000 , 1500 );
				}
				
				if ( getInventoryCount( 42 ) == 0 )
				{
					if ( bankCount( 42 ) == 0 )
					{
						System.out.println( "[TPM_BlueDrags] The script has stopped, out of cammy tele runes..." );
						
						stopScript(); 
						setAutoLogin( false );
						
						return 0;
					}
				
					withdraw( 42 , 1 );
					
					return random( 1000 , 1500 );
				}
			}
			else
			{
				if ( getInventoryCount( 42 ) > 0 )
				{
					deposit( 42 , getInventoryCount( 42 ) );
					
					return random( 1000 , 1500 );
				}
			}
			
			// WITHDRAW ANTIFIRE SHIELD IF NOT HOLDING
			
			if ( getInventoryCount( 420 ) == 0 )
			{
				if ( bankCount( 420 ) == 0 )
				{
					System.out.println( "[TPM_BlueDrags] The script has stopped, out of antifire shields..." );
					
					stopScript(); 
					setAutoLogin( false );
					
					return 0;
				}
			
				withdraw( 420 , 1 );
				
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
				
				System.out.println( "[TPM_BlueDrags] The script has stopped, out of " + foodChoice.getSelectedItem() + "..." );
				
				stopScript(); 
				setAutoLogin( false );
				
				return 0;
			}
			
			closeBank();
		}
		if ( insideLumbridgeRespawn() && ! DEATH_SEQUENCE )
		{
			System.out.println( "[TPM_BlueDrags] The script has detected a death, starting recovery sequence" );
			
			DEATH_SEQUENCE = true;
		}
		else if ( DEATH_SEQUENCE )
		{
			if ( outsideCatherby() )
			{
				DEATH_SEQUENCE = false;
			}
			else if ( insideCamelotGardens() )
			{
				int[] gate = getObjectById( 57 );

				if ( gate[0] != -1 )
				{
					atObject( gate[1] , gate[2] );
					
					return random( 300 , 600 );
				}
				else
				{
					walkTo( 439 + random( 0 , 2 ) , 466 + random( 0 , 2 ) );
					
					return random( 300 , 600 );
				}
			}
			else if ( insideLumbridgeRespawn() )
			{
				walkTo( 128 + random( 0 , 2 ) , 641 + random( 0 , 2 ) );
			}
			else if ( nearLumbridgeGeneralStore() )
			{
				walkTo( 153 + random( 0 , 2 ) , 645 + random( 0 , 2 ) );
			}
			else if ( insideLumbridgeForestEast() )
			{
				walkTo( 178 + random( 0 , 2 ) , 637 + random( 0 , 2 ) );
			}
			else if ( insideLumbridgeForestCentral() )
			{
				walkTo( 201 + random( 0 , 2 ) , 630 + random( 0 , 2 ) );
			}
			else if ( insideLumbridgeForestWest() )
			{
				walkTo( 212 + random( 0 , 1 ) , 630 + random( 0 , 1 ) );
			}
			else if ( insideDraynor() )
			{
				if ( ! insideDraynorBank() )
				{	
					int[] bank_doors = getObjectById( 64 );

					if ( bank_doors[0] != -1 )
					{
						atObject( bank_doors[1] , bank_doors[2] );
						
						return random( 400 , 800 );
					}
					else
					{
						walkTo( 220 + random( 0 , 1 ) , 636 + random( 0 , 1 ) );
						
						return random( 400 , 800 );
					}
				}
				else
				{
					// IF WE BANKED AND HAVE THE CAMMY TELE RUNES ALREADY
				
					if ( getInventoryCount( 33 ) >= 5 && getInventoryCount( 42 ) >= 1 )
					{
						// EQUIP WEAPON IF UNEQUIPPED
					
						int weapon_index = getInventoryIndex( WEAPON_ID );
						
						if ( weapon_index != -1 && ! isItemEquipped( weapon_index ) )
						{
							wearItem( weapon_index );
							
							return random( 800 , 1200 );
						}
						
						// EQUIP ANTIFIRE SHIELD IF UNEQUIPPED
						
						int shield_index = getInventoryIndex( 420 );
						
						if ( shield_index != -1 && ! isItemEquipped( shield_index ) )
						{
							wearItem( shield_index );
							
							return random( 800 , 1200 );
						}
						
						castOnSelf( 22 );
					}
					
					// OR ARE WE TALKING TO BANKER
					
					else if ( isQuestMenu() ) 
					{
						answer( 0 );
						
						bank_time = System.currentTimeMillis();
						
						return random( 3000 , 4000 );
					}
					
					// OTHERWISE LETS TALK TO THE BANKER
					
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
			}
		}
		else if ( getInventoryCount() == 30 || ( getInventoryCount( FOOD_IDS ) == 0 && getInventoryCount( 555 ) == 0 ) || getInventoryCount( 1263 ) == 0 || getInventoryCount( WEAPON_ID ) == 0 )
		{
			int gnome_ball = getInventoryIndex( 981 );
			
			if ( gnome_ball != -1 ) 
			{
				dropItem( gnome_ball );
			}
			else if ( ! insideCatherbyBank() )
			{
				if ( insideHeroesGuildDragonCage() )
				{
					int[] gate = getObjectById( 57 );

					if ( gate[0] != -1 )
					{
						if ( inCombat() )
						{
							walkTo( 374 , 3275 );
						
							return random( 300 , 600 );
						}
					
						atObject( gate[1] , gate[2] );
						
						return random( 300 , 600 );
					}
					else
					{
						walkTo( 370 + random( 0 , 2 ) , 3275 + random( 0 , 2 ) );
						
						return random( 300 , 600 );
					}
				}
				else if ( insideHeroesGuildBasement() )
				{
					int[] stairs = getObjectById( 41 );

					if ( stairs[0] != -1 )
					{
						if ( inCombat() )
						{
							walkTo( 370 + random( 0 , 2 ) , 3275 + random( 0 , 2 ) );
							
							return random( 300 , 600 );
						}
					
						atObject( stairs[1] , stairs[2] );
						
						return random( 300 , 600 );
					}
				}
				else if ( insideHeroesGuildChurch() )
				{
					int[] ladder = getObjectById( 6 );

					if ( ladder[0] != -1 )
					{
						atObject( ladder[1] , ladder[2] );
					}
				}
				else if ( insideHeroesGuild() )
				{
					atWallObject( 372 , 441 );
				}
				else if ( outsideHeroesGuild() )
				{
					walkTo( 381 + random( 0 , 2 ) , 468 + random( 0 , 2 ) );
				}
				else if ( outsideMountainPassEast() )
				{
					if ( ! ( getX() >= 385 && getX() <= 386 && getY() == 465 ) )
					{
						walkTo( 384 + random( 1 , 2 ) , 465 );
					}
					else
					{
						int[] doors = getObjectById( 64 );

						if ( doors[0] != -1 )
						{
							atObject( doors[1] , doors[2] );
							
							return random( 400 , 800 );
						}
						else
						{ 
							int[] stairs = getObjectById( 359 );

							if ( stairs[0] != -1 )
							{ 
								atObject( stairs[1] , stairs[2] );
							}
						}
					}
				}
				else if ( insideMountainPass() )
				{
					if ( insideMountainPassEast() )
					{
						walkTo( 406 + random( 0 , 1 ) , 3293 + random( 0 , 1 ) );
					}
					else
					{
						if ( getX() < 425 )
						{
							walkTo( 426 + random( 0 , 1 ) , 3294 + random( 0 , 1 ) );
						}
						else
						{  
							int[] stairs = getObjectById( 43 );

							if ( stairs[0] != -1 )
							{
								atObject( stairs[1] , stairs[2] );
							}
						}
						// STAIRS UP
					}
				}
				else if ( outsideMountainPassWest() )
				{
					walkTo( 442 + random( 0 , 2 ) , 471 + random( 0 , 2 ) );
				}
				else if ( outsideCatherby() )
				{
					walkTo( 447 + random( 0 , 2 ) , 487 + random( 0 , 2 ) );
				}
				else if ( insideCatherby() )
				{
					if ( infrontOfCatherbyBank() )
					{
						int[] bank_doors = getObjectById( 64 );
						
						if ( bank_doors[0] != -1 ) 
						{
							atObject( bank_doors[1] , bank_doors[2] );
							
							return random( 400 , 800 );
						}
						else
						{
							walkTo( 442 + random( 0 , 1 ) , 494 + random( 0 , 1 ) );
						}
					}
					else
					{
						walkTo( 440 + random( 0 , 1 ) , 500 + random( 0 , 1 ) );
					}
				}
				else 
				{
					outOfBounds();
				}
			}
			else  
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
		}  
		else if ( getHpPercent() <= 35 ) 
		{
			if ( insideHeroesGuildDragonCage() )
			{
				int[] gate = getObjectById( 57 );

				if ( gate[0] != -1 )
				{
					if ( inCombat() )
					{
						walkTo( 374 , 3275 );
					
						return random( 300 , 600 );
					}
				
					atObject( gate[1] , gate[2] );
					
					return random( 300 , 600 );
				}
				else
				{
					walkTo( 370 + random( 0 , 2 ) , 3275 + random( 0 , 2 ) );
					
					return random( 300 , 600 );
				}
			}
			else if ( insideHeroesGuildBasement() )
			{
				int[] stairs = getObjectById( 41 );

				if ( stairs[0] != -1 )
				{
					if ( inCombat() )
					{
						walkTo( 370 + random( 0 , 2 ) , 3275 + random( 0 , 2 ) );
						
						return random( 300 , 600 );
					}
				
					atObject( stairs[1] , stairs[2] );
				}
			}
			else if ( insideHeroesGuild() )
			{
				int count = getInventoryCount();
						
				for ( int i = 0; i < count; i++ ) 
				{
					if ( getItemCommand( i ).toLowerCase( Locale.ENGLISH ).equals( "eat" ) ) 
					{
						useItem( i );
						
						return random( 1000 , 1200 );
					}
				}
			}
		}
		else if ( getCurrentLevel( 5 ) == 0 )
		{
			if ( ! insideHeroesGuildChurch() )      
			{
				if ( insideCatherbyBank() )
				{
					int[] bank_doors = getObjectById( 64 );
						
					if ( bank_doors[0] != -1 ) 
					{
						atObject( bank_doors[1] , bank_doors[2] );
						
						return random( 400 , 800 );
					}
					else
					{
						walkTo( 440 + random( 0 , 1 ) , 500 + random( 0 , 1 ) );
					}
				}
				else if ( infrontOfCatherbyBank() )
				{
					walkTo( 447 + random( 0 , 2 ) , 487 + random( 0 , 2 ) );
				}
				else if ( insideCatherby() )
				{
					walkTo( 442 + random( 0 , 2 ) , 471 + random( 0 , 2 ) );
				}
				else if ( outsideCatherby() )
				{
					walkTo( 432 + random( 0 , 2 ) , 454 + random( 0 , 2 ) );
				}
				else if ( outsideMountainPassWest() )
				{
					if ( ! ( getX() >= 426 && getX() <= 427 && getY() == 457 ) )
					{
						walkTo( 425 + random( 1 , 2 ) , 457 );
					}
					else
					{  
						int[] stairs = getObjectById( 359 );

						if ( stairs[0] != -1 )
						{
							atObject( stairs[1] , stairs[2] );
						}  
					}
				}
				else if ( insideMountainPass() )
				{
					if ( insideMountainPassWest() )
					{
						walkTo( 398 + random( 0 , 1 ) , 3293 + random( 0 , 1 ) );
					}
					else
					{
						if ( getY() < 3300 )
						{
							walkTo( 387 + random( 0 , 1 ) , 3302 + random( 0 , 1 ) );
						}
						else
						{  
							int[] stairs = getObjectById( 43 );

							if ( stairs[0] != -1 )
							{
								atObject( stairs[1] , stairs[2] );
							}
						}
					}
				}
				else if ( outsideMountainPassEast() )
				{
					walkTo( 373 + random( 0 , 2 ) , 445 + random( 0 , 2 ) );
				}
				else if ( outsideHeroesGuild() )
				{
					atWallObject( 372 , 441 );
				}
				else if ( insideHeroesGuildDragonCage() )
				{
					int[] gate = getObjectById( 57 );

					if ( gate[0] != -1 )
					{
						if ( inCombat() )
						{
							walkTo( 374 , 3275 );
						
							return random( 300 , 600 );
						}
					
						atObject( gate[1] , gate[2] );
						
						return random( 300 , 600 );
					}
					else
					{
						walkTo( 370 + random( 0 , 2 ) , 3275 + random( 0 , 2 ) );
						
						return random( 300 , 600 );
					}
				}
				else if ( insideHeroesGuildBasement() )
				{
					int[] stairs = getObjectById( 41 );

					if ( stairs[0] != -1 )
					{
						if ( inCombat() )
						{
							walkTo( 370 + random( 0 , 2 ) , 3275 + random( 0 , 2 ) );
							
							return random( 300 , 600 );
						}
					
						atObject( stairs[1] , stairs[2] );
						
						return random( 300 , 600 );
					}
				}
				else if ( insideHeroesGuild() )
				{
					int[] ladder = getObjectById( 5 );

					if ( ladder[0] != -1 )
					{
						atObject( ladder[1] , ladder[2] );
					}
				}
				else
				{
					outOfBounds();
				}
			}
			else
			{
				int[] altar = getObjectById( 19 );

				if ( altar[0] != -1 )
				{
					atObject( altar[1] , altar[2] );
				}
			}
		}
		else if ( getFatigue() > 90 )
		{
			if ( ! isSleeping() )
			{
				useSleepingBag();
			}
		}
		else
		{
			if ( ! insideHeroesGuildBasement() )
			{
				if ( insideCatherbyBank() )
				{
					int[] bank_doors = getObjectById( 64 );
						
					if ( bank_doors[0] != -1 ) 
					{
						atObject( bank_doors[1] , bank_doors[2] );
						
						return random( 400 , 800 );
					}
					else
					{
						walkTo( 440 + random( 0 , 1 ) , 500 + random( 0 , 1 ) );
					}
				}
				else if ( infrontOfCatherbyBank() )
				{
					walkTo( 447 + random( 0 , 2 ) , 487 + random( 0 , 2 ) );
				}
				else if ( insideCatherby() )
				{
					walkTo( 442 + random( 0 , 2 ) , 471 + random( 0 , 2 ) );
				}
				else if ( outsideCatherby() )
				{
					walkTo( 432 + random( 0 , 2 ) , 454 + random( 0 , 2 ) );
				}
				else if ( outsideMountainPassWest() )
				{
					if ( ! ( getX() >= 426 && getX() <= 427 && getY() == 457 ) )
					{
						walkTo( 425 + random( 1 , 2 ) , 457 );
					}
					else
					{  
						int[] stairs = getObjectById( 359 );

						if ( stairs[0] != -1 )
						{
							atObject( stairs[1] , stairs[2] );
						}  
					}
				}
				else if ( insideMountainPass() )
				{
					if ( insideMountainPassWest() )
					{
						walkTo( 398 + random( 0 , 1 ) , 3293 + random( 0 , 1 ) );
					}
					else
					{
						if ( getY() < 3300 )
						{
							walkTo( 387 + random( 0 , 1 ) , 3302 + random( 0 , 1 ) );
						}
						else
						{  
							int[] stairs = getObjectById( 43 );

							if ( stairs[0] != -1 )
							{
								atObject( stairs[1] , stairs[2] );
							}
						}
					}
				}
				else if ( outsideMountainPassEast() )
				{
					walkTo( 373 + random( 0 , 2 ) , 445 + random( 0 , 2 ) );
				}
				else if ( outsideHeroesGuild() )
				{
					atWallObject( 372 , 441 );
				}
				else if ( insideHeroesGuild() )
				{
					int[] stairs = getObjectById( 42 );

					if ( stairs[0] != -1 )
					{
						atObject( stairs[1] , stairs[2] );
					}
				}
				else if ( insideHeroesGuildChurch() )
				{
					int[] ladder = getObjectById( 6 );

					if ( ladder[0] != -1 )
					{
						atObject( ladder[1] , ladder[2] );
					}
				}
				else
				{
					outOfBounds();
				}
			}
			else
			{
				if ( ! insideHeroesGuildDragonCage() )
				{
					int[] gate = getObjectById( 57 );

					if ( gate[0] != -1 )
					{
						atObject( gate[1] , gate[2] );
						
						return random( 600 , 1200 );
					}
					else
					{
						walkTo( 375 , 3272 );  
						
						return random( 600 , 1200 );
					}
				}
				else
				{
					if ( inCombat() )
					{
						if ( ! isPrayerEnabled( 12 ) )
						{ 
							enablePrayer( 12 );
							
							return random( 300 , 500 );
						}
					}
					else
					{
						if ( isPrayerEnabled( 12 ) )
						{
							disablePrayer( 12 );
							
							return random( 300 , 500 );
						}
						
						if ( getHpPercent() <= 65 )  
						{
							int count = getInventoryCount();
							
							for ( int i = 0; i < count; i++ ) 
							{
								if ( getItemCommand( i ).toLowerCase( Locale.ENGLISH ).equals( "eat" ) ) 
								{
									useItem( i );
									
									return random( 1000 , 1200 );
								}
							}
						}
						
						int[] npc = _getReachableNpc( 202 );
						
						if ( npc[0] != -1 )// && isNpcInCombat( npc[0] ) ) 
						{
							if ( distanceTo( npc[1] , npc[2] ) > 5 ) 
							{
								_walkApprox( npc[1] , npc[2] , 1 );
							}
							
							attackNpc( npc[0] );
							
							return random( 600 , 1000 );
						}
						else
						{
							int[] item = _getItemFast( new int[] { 10 , 31 , 32 , 40 , 42 , 111 , 130 , 436 , 438 , 154 , 526 , 527 , 555 , 795 , 814 , 1277 } );
							
							if ( item[0] != -1 )
							{
								pickupItem( item[0] , item[1] , item[2] );
							
								return random( 1000 , 1200 );
							}
							
							if ( getInventoryCount( 814 ) > 0 && BURY_BONES )
							{
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
						
							if ( getX() != 375 && getY() != 3272 )
							{
								walkTo( 375 , 3272 ); 
								
								return random( 1000 , 1500 );
							}
						}
						  
						 if (move_time != -1L) 
						 {
							if ( System.currentTimeMillis() >= move_time )
							{
								walkTo( 370 + random( 0 , 1 ) , 3275 + random( 0 , 1 ) );

								move_time = -1L;
								
								return random( 1000 , 1500 );
							}
							return 0;
						}
						
						return 10;
					}
				}
			}
		} 
		
		return random( 2000 , 3000 );
    }
	
	private void outOfBounds()
	{
		System.out.println( "[TPM_BlueDrags] This script requires you to be standing in the dwarf mountain pass, the heroes guild, catherby, or the lumbridge respawn." );
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
}
