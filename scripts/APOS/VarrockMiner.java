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
import java.util.Locale;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

/**
 * VarrockMiner - mines the Varrock south-east mine and banks at Varrock East.
 *
 * Two modes, picked in the config window before starting:
 *   Tin & Copper - fills the inventory with an even split of tin and copper
 *                  ore (for bronze smelting), sleeping off fatigue as it goes,
 *                  then banks it all at Varrock East and walks back.
 *   Iron         - fills the inventory with iron ore (mining 15+), sleeping
 *                  off fatigue, then banks and walks back.
 *   Train        - the two above chained into a progression: tin and copper
 *                  until mining reaches the switch level, iron from there,
 *                  and at the stop level it banks whatever it is carrying
 *                  and stops with autologin off - the handoff to whatever
 *                  comes next (quests, the coal run) instead of mining
 *                  forever. Both levels are typed into the config window.
 *
 * Requires a pickaxe and a sleeping bag in the inventory. All state is
 * derived from position, so it survives restarts and relogins mid-run.
 */
public final class VarrockMiner extends Script
{
	private Frame frame = null;
	private Choice modeChoice;
	private TextField switchField;
	private TextField stopField;

	private static final int MODE_EVEN  = 0;
	private static final int MODE_IRON  = 1;
	private static final int MODE_TRAIN = 2;
	private int mode = -1;

	private int switchLevel;
	private int stopLevel;

	private static final int[] COPPER_ROCKS = { 100, 101 };
	private static final int[] TIN_ROCKS    = { 104, 105 };
	private static final int[] IRON_ROCKS   = { 102, 103 };

	private static final int COPPER_ORE = 150;
	private static final int TIN_ORE    = 202;
	private static final int IRON_ORE   = 151;

	private static final int[] ORES_TO_BANK = { COPPER_ORE, TIN_ORE, IRON_ORE };

	private static final int[] PICKAXES = {
		1262, 1261, 1260, 1259, 1258, 156
	};

	private static final int SLEEPING_BAG = 1263;

	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };

	/*
	 * Walking route between Varrock East bank and the south-east mine,
	 * bank end first. Every tile verified walkable against the server's
	 * collision grid; hops are short enough for the client pathfinder.
	 */
	private static final int[][] PATH = {
		{ 101, 512 },   // inside Varrock East bank
		{  97, 516 },
		{  92, 521 },
		{  86, 528 },
		{  81, 533 },
		{  75, 540 },
		{  74, 546 }    // the mine
	};

	private long startTime;
	private long moveTime;
	private int  oreCount;
	private int  initialXP;

	public VarrockMiner( Extension ex )
	{
		super( ex );
	}

	@Override
	public void init( String params )
	{
		if ( frame == null )
		{
			System.out.println( "[VarrockMiner] Varrock SE mine script initialized." );

			Frame frame = new Frame( getClass().getSimpleName() );
			frame.setIconImages( Constants.ICONS );
			frame.addWindowListener(
				new StandardCloseHandler( frame, StandardCloseHandler.HIDE )
			);

			Panel pChoice = new Panel( new GridLayout( 0, 1 ) );
			modeChoice = new Choice();
			modeChoice.add( "Tin & Copper (even split)" );
			modeChoice.add( "Iron" );
			modeChoice.add( "Train to a level" );
			pChoice.add( new Label( "What to mine?" ) );
			pChoice.add( modeChoice );
			switchField = new TextField( "25" );
			stopField   = new TextField( "55" );
			pChoice.add( new Label( "Train: switch to iron at mining level" ) );
			pChoice.add( switchField );
			pChoice.add( new Label( "Train: bank up and stop at mining level" ) );
			pChoice.add( stopField );

			Panel pButtons = new Panel();
			Button button = new Button( "Save Configuration" );

			button.addActionListener( new ActionListener()
			{
				public void actionPerformed( ActionEvent e )
				{
					int picked = modeChoice.getSelectedIndex();

					if ( picked == MODE_TRAIN )
					{
						int sw, st;

						try
						{
							sw = Integer.parseInt( switchField.getText().trim() );
							st = Integer.parseInt( stopField.getText().trim() );
						}
						catch ( NumberFormatException nfe )
						{
							System.out.println( "[VarrockMiner] Train levels must be numbers - not saved." );
							return;
						}

						if ( sw < 15 || st <= sw || st > 99 )
						{
							System.out.println( "[VarrockMiner] Train levels make no sense (switch needs 15+, stop needs to be above it) - not saved." );
							return;
						}

						VarrockMiner.this.switchLevel = sw;
						VarrockMiner.this.stopLevel   = st;
					}

					VarrockMiner.this.mode = picked;

					if ( VarrockMiner.this.mode == MODE_IRON && getLevel( 14 ) < 15 )
					{
						System.out.println( "[VarrockMiner] WARNING: iron needs mining 15; the server will refuse every swing." );
					}

					System.out.println( "[VarrockMiner] The script has been configured and can now be started." );
					VarrockMiner.this.frame.setVisible( false );
				}
			});

			pButtons.add( button );
			frame.add( pChoice, BorderLayout.CENTER );
			frame.add( pButtons, BorderLayout.SOUTH );
			frame.setResizable( false );
			frame.pack();
			this.frame = frame;
		}

		frame.setLocationRelativeTo( null );
		frame.setVisible( true );

		startTime = -1L;
		moveTime  = -1L;
		oreCount  = 0;
		initialXP = 0;
	}

	@Override
	public int main()
	{
		if ( mode == -1 )
		{
			return 1000;   // config window not saved yet
		}

		if ( startTime == -1L )
		{
			startTime = System.currentTimeMillis();
		}

		if ( initialXP == 0 )
		{
			initialXP = getXpForLevel( 14 );
		}

		if ( getInventoryIndex( PICKAXES ) < 0 )
		{
			System.out.println( "[VarrockMiner] No pickaxe in the inventory - stopping." );
			setAutoLogin( false );
			stopScript();
			return 1000;
		}

		if ( getInventoryCount( SLEEPING_BAG ) < 1 )
		{
			System.out.println( "[VarrockMiner] No sleeping bag in the inventory - stopping." );
			setAutoLogin( false );
			stopScript();
			return 1000;
		}

		// Anti-stuck: "I can't reach that" style standing message queued a jiggle.
		if ( moveTime != -1L && System.currentTimeMillis() >= moveTime )
		{
			moveTime = -1L;
			walkTo( getX() + random( -1, 1 ), getY() + random( -1, 1 ) );
			return random( 1000, 1500 );
		}

		if ( isBanking() )
		{
			/*
			 * Every bank run is a chance to upgrade the pick: PICKAXES is
			 * ordered best first, and the server gates none of them by level
			 * (Formulae.getOre is bonus-only - bronze +0 up to rune +12), so
			 * a better pick in the bank is always worth swapping to. Withdraw
			 * the upgrade first, then the old pick goes back as a deposit on
			 * the next pass.
			 */
			int best = -1;

			for ( int i = 0; i < PICKAXES.length; i++ )
			{
				if ( getInventoryCount( PICKAXES[i] ) > 0 )
				{
					best = i;
					break;
				}
			}

			for ( int i = 0; i < best; i++ )
			{
				if ( bankCount( PICKAXES[i] ) > 0 )
				{
					System.out.println( "[VarrockMiner] A better pickaxe is in the bank - swapping up." );
					withdraw( PICKAXES[i], 1 );
					return random( 1000, 1500 );
				}
			}

			if ( best != -1 )
			{
				for ( int i = PICKAXES.length - 1; i > best; i-- )
				{
					if ( getInventoryCount( PICKAXES[i] ) > 0 )
					{
						deposit( PICKAXES[i], getInventoryCount( PICKAXES[i] ) );
						return random( 1000, 1500 );
					}
				}
			}

			for ( int i = 0; i < ORES_TO_BANK.length; i++ )
			{
				int count = getInventoryCount( ORES_TO_BANK[i] );

				if ( count > 0 )
				{
					deposit( ORES_TO_BANK[i], count );
					return random( 1000, 1500 );
				}
			}

			closeBank();
			return random( 1000, 1500 );
		}

		if ( getFatigue() > 90 )
		{
			if ( ! isSleeping() )
			{
				useSleepingBag();
			}
			return random( 1000, 2000 );
		}

		/*
		 * Training done: one last bank run for whatever is being carried,
		 * then stop for real - autologin off, so a disconnect can't quietly
		 * resurrect the run past its target.
		 */
		boolean trainDone = ( mode == MODE_TRAIN && getLevel( 14 ) >= stopLevel );

		if ( trainDone && oresCarried() == 0 )
		{
			System.out.println( "[VarrockMiner] Mining " + stopLevel + " reached and the last load is banked - stopping." );
			setAutoLogin( false );
			stopScript();
			return 1000;
		}

		if ( getInventoryCount() == 30 || trainDone )
		{
			if ( insideBank() )
			{
				if ( isQuestMenu() )
				{
					answer( 0 );
					return random( 3000, 4000 );
				}

				int[] banker = getNpcByIdNotTalk( BANKERS );

				if ( banker[0] != -1 )
				{
					talkToNpc( banker[0] );
					return random( 3000, 4000 );
				}

				walkTo( 101 + random( 0, 1 ), 512 + random( 0, 1 ) );
				return random( 1000, 1500 );
			}

			return walkPath( false );   // toward the bank
		}

		if ( insideMine() )
		{
			int[] rock;

			/* In train mode the switch level decides which half of the
			   progression this is; the modes stay what they always were. */
			boolean iron = ( mode == MODE_IRON
				|| ( mode == MODE_TRAIN && getLevel( 14 ) >= switchLevel ) );

			if ( iron )
			{
				rock = getObjectById( IRON_ROCKS );
			}
			else
			{
				// Keep the split even: always chase the ore we hold less of.
				if ( getInventoryCount( COPPER_ORE ) <= getInventoryCount( TIN_ORE ) )
				{
					rock = getObjectById( COPPER_ROCKS );
				}
				else
				{
					rock = getObjectById( TIN_ROCKS );
				}
			}

			if ( rock[0] != -1 )
			{
				atObject( rock[1], rock[2] );
				return random( 750, 950 );
			}

			// Everything depleted - respawns are 3-5s, just wait it out.
			return random( 1000, 2000 );
		}

		if ( distanceTo( 86, 528 ) > 70 )
		{
			System.out.println( "[VarrockMiner] This script requires you to be near the Varrock south-east mine or Varrock East bank." );
			return random( 2000, 3000 );
		}

		return walkPath( true );   // toward the mine
	}

	private int oresCarried()
	{
		int total = 0;

		for ( int i = 0; i < ORES_TO_BANK.length; i++ )
		{
			total += getInventoryCount( ORES_TO_BANK[i] );
		}

		return total;
	}

	private boolean insideMine()
	{
		return ( getX() >= 66 && getX() <= 82 && getY() >= 540 && getY() <= 552 );
	}

	private boolean insideBank()
	{
		return ( getX() >= 96 && getX() <= 107 && getY() >= 508 && getY() <= 516 );
	}

	private int walkPath( boolean towardMine )
	{
		int nearest = 0;
		int nearestDist = Integer.MAX_VALUE;

		for ( int i = 0; i < PATH.length; i++ )
		{
			int dist = distanceTo( PATH[i][0], PATH[i][1] );

			if ( dist < nearestDist )
			{
				nearestDist = dist;
				nearest = i;
			}
		}

		int next;

		if ( nearestDist > 4 )
		{
			next = nearest;   // rejoin the path before advancing along it
		}
		else if ( towardMine )
		{
			next = Math.min( nearest + 1, PATH.length - 1 );
		}
		else
		{
			next = Math.max( nearest - 1, 0 );
		}

		walkTo( PATH[next][0] + random( 0, 1 ), PATH[next][1] + random( 0, 1 ) );
		return random( 1000, 1500 );
	}

	@Override
	public void walkTo( int x, int y )
	{
		if ( ! isWalking() )
		{
			super.walkTo( x, y );
		}
	}

	@Override
	public void onServerMessage( String str )
	{
		str = str.toLowerCase( Locale.ENGLISH );

		if ( str.contains( "manage to obtain some" ) )
		{
			oreCount++;
		}
		else if ( str.contains( "standing here" ) )
		{
			moveTime = System.currentTimeMillis() + random( 1500, 1800 );
		}
	}

	@Override
	public void paint()
	{
		final int orange = 0xFFD900;
		final int white  = 0xFFFFFF;

		int x = 315;
		int y = 20;

		long secs = ( ( System.currentTimeMillis() - ( startTime > 0L ? startTime : System.currentTimeMillis() ) ) / 1000L ) + 1;

		int gained = getXpForLevel( 14 ) - initialXP;

		drawString( "VarrockMiner", x, y, 1, orange );
		y += 15;
		drawString( "Runtime: " + ( secs / 3600 ) + ":" + String.format( "%02d", ( secs % 3600 ) / 60 ) + ":" + String.format( "%02d", secs % 60 ), x, y, 1, white );
		y += 15;
		drawString( "Ores: " + oreCount + " - " + (int)( ( oreCount * 3600L ) / secs ) + " Ores/Hr", x, y, 1, white );
		y += 15;
		drawString( "XP: " + gained + " - " + (int)( ( gained * 3600L ) / secs ) + " XP/Hr", x, y, 1, white );
	}
}
