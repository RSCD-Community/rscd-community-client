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

	/* Ores plus the uncut gems a swing occasionally turns up
	   (diamond 157, ruby 158, emerald 159, sapphire 160). */
	private static final int[] ORES_TO_BANK = {
		COPPER_ORE, TIN_ORE, IRON_ORE, 157, 158, 159, 160
	};

	private static final int[] PICKAXES = {
		1262, 1261, 1260, 1259, 1258, 156
	};

	/* Mining level each pick demands, same order - rune 41 down to bronze. */
	private static final int[] PICKAXE_REQS = { 41, 31, 21, 6, 1, 1 };

	private static final int SLEEPING_BAG = 1263;

	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };

	/*
	 * Walking route between Varrock East bank and the south-east mine,
	 * bank end first. Waypoints sampled off a BFS of the server's
	 * collision grid (edge bits included), so every hop is genuinely
	 * walkable: the bank's only doorway is on its north side, so the
	 * route runs the y=509-510 corridor along the bank's north face,
	 * then south-west, crossing the mine's north fence through the gap
	 * west of x=79. Hops stay short so the client pathfinder never has
	 * to think.
	 */
	private static final int[][] PATH = {
		{ 101, 512 },   // inside Varrock East bank
		{ 102, 509 },   // just outside the bank doors
		{  97, 508 },   // the road into town
		{  91, 508 },
		{  85, 508 },
		{  83, 512 },   // off the road, down the lane west of the houses
		{  83, 517 },
		{  82, 521 },
		{  81, 525 },
		{  78, 527 },
		{  77, 531 },   // x77, not 78: (78,533) is a diagonal fence tile
		{  77, 536 },   // the gap in the mine's north fence
		{  76, 542 },
		{  74, 546 }    // the mine
	};

	private long startTime;
	private long moveTime;
	private int  oreCount;
	private int  initialXP;

	/*
	 * The login-restart machinery replays recorded prompt answers, but this
	 * script interviews through its own AWT window, which nothing can click
	 * for an empty chair -- so an automatic restart used to come up with
	 * mode -1 and stand at the mine doing nothing. The configuration is
	 * therefore persisted on Save and restored in init(): a resumed run
	 * picks up exactly where the saved settings left it, window untouched.
	 */
	private static final java.io.File CONFIG =
		new java.io.File( "script-bin", "VarrockMiner.cfg" );

	public VarrockMiner( Extension ex )
	{
		super( ex );
	}

	private void saveConfig()
	{
		try
		{
			java.io.PrintWriter out = new java.io.PrintWriter( CONFIG, "UTF-8" );
			out.println( mode );
			out.println( switchLevel );
			out.println( stopLevel );
			out.close();
		}
		catch ( Exception e )
		{
			System.out.println( "[VarrockMiner] Could not save the configuration: " + e );
		}
	}

	private void loadConfig()
	{
		if ( ! CONFIG.isFile() )
		{
			return;
		}

		try
		{
			java.io.BufferedReader in = new java.io.BufferedReader(
				new java.io.InputStreamReader(
					new java.io.FileInputStream( CONFIG ), "UTF-8" ) );
			int m  = Integer.parseInt( in.readLine().trim() );
			int sw = Integer.parseInt( in.readLine().trim() );
			int st = Integer.parseInt( in.readLine().trim() );
			in.close();

			if ( m < MODE_EVEN || m > MODE_TRAIN )
			{
				return;
			}

			mode        = m;
			switchLevel = sw;
			stopLevel   = st;
			System.out.println( "[VarrockMiner] Restored the saved configuration - already running; use the window to change it." );
		}
		catch ( Exception e )
		{
			System.out.println( "[VarrockMiner] Saved configuration unreadable, ignoring it: " + e );
		}
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
					VarrockMiner.this.saveConfig();

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

		loadConfig();

		/* A person starting the script always gets the window, saved settings
		   already applied underneath as the running configuration -- Save
		   overwrites them. Only an unattended start (the login restart, the
		   panel's Play replay) skips it: nobody is there to click, and the
		   restored settings are the whole point of the resume. */
		if ( ! isUnattended() )
		{
			frame.setLocationRelativeTo( null );
			frame.setVisible( true );
		}

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

		/*
		 * An empty inventory can only mean the server has not sent it yet --
		 * this run always carries at least the pickaxe and the sleeping bag.
		 * Right after a relogin main() can tick ahead of that packet, and
		 * without this wait the no-pickaxe check below would read the blank
		 * as "no pickaxe" and end the run for good.
		 */
		if ( getInventoryCount() == 0 )
		{
			return 1000;
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
			 * Deposits before withdrawals, always - the inventory arrives
			 * full, so nothing can come out of the bank until the ore has
			 * gone in.
			 */
			for ( int i = 0; i < ORES_TO_BANK.length; i++ )
			{
				int count = getInventoryCount( ORES_TO_BANK[i] );

				if ( count > 0 )
				{
					deposit( ORES_TO_BANK[i], count );
					return random( 1000, 1500 );
				}
			}

			/*
			 * Every bank run is a chance to upgrade the pick - but only to
			 * one the mining level can actually use (rune demands 41, down
			 * through mithril 21 and steel 6; the server refuses the swing
			 * otherwise). The best usable pick stays in the inventory;
			 * every other pick carried, better or worse, goes in the bank.
			 */
			int best = -1;

			for ( int i = 0; i < PICKAXES.length; i++ )
			{
				if ( getLevel( 14 ) >= PICKAXE_REQS[i] && getInventoryCount( PICKAXES[i] ) > 0 )
				{
					best = i;
					break;
				}
			}

			/*
			 * Nothing usable in hand (say, only a rune pick at mining 5):
			 * pull a usable one from the bank FIRST, or banking the dead
			 * weight below would leave the inventory pickless and trip the
			 * no-pickaxe stop at the top of main().
			 */
			if ( best == -1 )
			{
				for ( int i = 0; i < PICKAXES.length; i++ )
				{
					if ( getLevel( 14 ) >= PICKAXE_REQS[i] && bankCount( PICKAXES[i] ) > 0 )
					{
						withdraw( PICKAXES[i], 1 );
						return random( 1000, 1500 );
					}
				}
			}

			for ( int i = 0; i < PICKAXES.length; i++ )
			{
				if ( i != best && getInventoryCount( PICKAXES[i] ) > 0 )
				{
					deposit( PICKAXES[i], getInventoryCount( PICKAXES[i] ) );
					return random( 1000, 1500 );
				}
			}

			for ( int i = 0; i < best; i++ )
			{
				if ( getLevel( 14 ) >= PICKAXE_REQS[i] && bankCount( PICKAXES[i] ) > 0 )
				{
					System.out.println( "[VarrockMiner] A better pickaxe is in the bank - swapping up." );
					withdraw( PICKAXES[i], 1 );
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

		/* Exact tiles only: every waypoint is verified walkable, but the
		   tile one step off it may be a rock or a fence bit. */
		walkTo( PATH[next][0], PATH[next][1] );
		return random( 1000, 1500 );
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
