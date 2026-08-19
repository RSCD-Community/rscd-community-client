import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

/**
 * VarrockSmither - works the four-anvil smithy beside Varrock West bank,
 * running the bronze-then-iron progression that VarrockMiner's train mode
 * gathers the ore for.
 *
 * Bars come from the bank; smelting is a separate errand (S_Smelter - Varrock
 * has no furnace). Each bank visit picks the best bar the account can work:
 * iron once smithing is 15 and the bank still holds iron bars, bronze
 * otherwise, and a clean stop with autologin off when neither is left.
 *
 * Any of the 21 anvil items can be chosen in the config window. Until the
 * smithing level reaches the chosen item's requirement the script falls back
 * to daggers, which every level can make. Experience is per bar consumed, so
 * the fallback costs nothing - the item choice is about what piles up in the
 * bank (arrowheads, in this house, are fletching futures).
 *
 * The anvil menu paths are taken from the server's own InvUseOnObject menu
 * tree, not inherited from 2005 - every index below answers exactly the menu
 * the server sends.
 */
public final class VarrockSmither extends Script
{
	private Frame frame = null;
	private Choice itemChoice;

	private static final int HAMMER       = 168;
	private static final int SLEEPING_BAG = 1263;
	private static final int ANVIL        = 50;

	private static final int BRONZE_BAR = 169;
	private static final int IRON_BAR   = 170;

	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };

	/* The smithy and the bank, a door apiece apart. */
	private static final int[] SMITHY      = { 147, 511 };
	private static final int[] BANK        = { 150, 505 };
	private static final int[] BANK_DOOR   = { 64, 150, 507 };
	private static final int[] SMITHY_DOOR = { 2, 146, 510 };

	private static final String[] ITEM_NAMES = {
		"Dagger", "Throwing Knife", "Short Sword", "Long Sword (2 bars)",
		"Scimitar (2 bars)", "2-handed Sword (3 bars)", "Hatchet", "Pickaxe",
		"Battle Axe (3 bars)", "Mace", "Medium Helmet", "Large Helmet (2 bars)",
		"Square Shield (2 bars)", "Kite Shield (3 bars)",
		"Chain Mail Body (3 bars)", "Plate Mail Body (5 bars)",
		"Plate Mail Legs (3 bars)", "Plated Skirt (3 bars)",
		"Arrow Heads x10", "Arrow Heads x50 (5 bars)", "Dart Tips"
	};

	/*
	 * Answer paths through the anvil menus, one per item above, read straight
	 * out of the server's InvUseOnObject handler. Top menu: 0 weapon,
	 * 1 armour, 2 missile heads.
	 */
	private static final int[][] MENU_PATHS = {
		{ 0, 0 },       // dagger
		{ 0, 1 },       // throwing knife
		{ 0, 2, 0 },    // short sword
		{ 0, 2, 1 },    // long sword
		{ 0, 2, 2 },    // scimitar
		{ 0, 2, 3 },    // 2-handed sword
		{ 0, 3, 0 },    // hatchet
		{ 0, 3, 1 },    // pickaxe
		{ 0, 3, 2 },    // battle axe
		{ 0, 4 },       // mace
		{ 1, 0, 0 },    // medium helmet
		{ 1, 0, 1 },    // large helmet
		{ 1, 1, 0 },    // square shield
		{ 1, 1, 1 },    // kite shield
		{ 1, 2, 0 },    // chain mail body
		{ 1, 2, 1 },    // plate mail body
		{ 1, 2, 2 },    // plate mail legs
		{ 1, 2, 3 },    // plated skirt
		{ 2, 0 },       // arrow heads x10
		{ 2, 1 },       // arrow heads x50
		{ 2, 2 }        // dart tips
	};

	/* Smithing level per item, from ItemSmithingDef - bronze row, iron row. */
	private static final int[] BRONZE_LEVELS = {
		1, 7, 4, 6, 5, 14, 1, 1, 10, 2, 3, 7, 8, 12, 11, 18, 16, 16, 5, 18, 4
	};
	private static final int[] IRON_LEVELS = {
		15, 22, 19, 21, 20, 29, 16, 16, 25, 17, 18, 22, 23, 27, 25, 33, 31, 31, 20, 33, 19
	};

	private static final int[] BARS_NEEDED = {
		1, 1, 1, 2, 2, 3, 1, 1, 3, 1, 1, 2, 2, 3, 3, 5, 3, 3, 1, 5, 1
	};

	private static final int ITEM_DAGGER      = 0;
	private static final int ITEM_ARROWHEADS  = 18;

	private int preferred = -1;

	/* The bar this run of the inventory is working, decided at the bank. */
	private int activeBar = BRONZE_BAR;

	private long startTime;
	private long menuTime;
	private long bankTime;
	private int  menuStep;
	private int  hammered;
	private int  initialXP;

	public VarrockSmither( Extension ex )
	{
		super( ex );
	}

	@Override
	public void init( String params )
	{
		if ( frame == null )
		{
			System.out.println( "[VarrockSmither] Varrock West anvil script initialized." );

			Frame frame = new Frame( getClass().getSimpleName() );
			frame.setIconImages( Constants.ICONS );
			frame.addWindowListener(
				new StandardCloseHandler( frame, StandardCloseHandler.HIDE )
			);

			Panel pChoice = new Panel( new GridLayout( 0, 1 ) );
			itemChoice = new Choice();

			for ( int i = 0; i < ITEM_NAMES.length; i++ )
			{
				itemChoice.add( ITEM_NAMES[i] );
			}

			itemChoice.select( ITEM_ARROWHEADS );
			pChoice.add( new Label( "What to smith? (daggers until its level is reached)" ) );
			pChoice.add( itemChoice );

			Panel pButtons = new Panel();
			Button button = new Button( "Save Configuration" );

			button.addActionListener( new ActionListener()
			{
				public void actionPerformed( ActionEvent e )
				{
					VarrockSmither.this.preferred = itemChoice.getSelectedIndex();
					System.out.println( "[VarrockSmither] The script has been configured and can now be started." );
					VarrockSmither.this.frame.setVisible( false );
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
		menuTime  = -1L;
		bankTime  = -1L;
		menuStep  = 0;
		hammered  = 0;
		initialXP = 0;
	}

	@Override
	public int main()
	{
		if ( preferred == -1 )
		{
			return 1000;   // config window not saved yet
		}

		if ( startTime == -1L )
		{
			startTime = System.currentTimeMillis();
		}

		if ( initialXP == 0 )
		{
			initialXP = getXpForLevel( 13 );
		}

		if ( getFatigue() > 90 )
		{
			if ( ! isSleeping() )
			{
				useSleepingBag();
			}
			return random( 1000, 2000 );
		}

		if ( isWalking() )
		{
			return random( 300, 500 );
		}

		int item = itemFor( activeBar );

		if ( item != -1 && getInventoryCount( activeBar ) >= BARS_NEEDED[item] )
		{
			return smith( item );
		}

		return bank();
	}

	/*
	 * The preferred item if the level allows it on this bar, daggers if not.
	 * -1 means this bar cannot be worked at all yet (iron below 15).
	 */
	private int itemFor( int bar )
	{
		int[] levels = ( bar == IRON_BAR ) ? IRON_LEVELS : BRONZE_LEVELS;
		int lvl = getLevel( 13 );

		if ( lvl >= levels[preferred] )
		{
			return preferred;
		}

		return ( lvl >= levels[ITEM_DAGGER] ) ? ITEM_DAGGER : -1;
	}

	private int smith( int item )
	{
		if ( ! isReachable( SMITHY[0], SMITHY[1] ) )
		{
			return openDoors();
		}

		if ( isQuestMenu() )
		{
			if ( menuStep < MENU_PATHS[item].length )
			{
				answer( MENU_PATHS[item][menuStep] );
				menuStep++;
			}
			menuTime = System.currentTimeMillis();
			return random( 600, 900 );
		}

		/* A menu chain that stalled - the server dropped it, start over. */
		if ( menuTime != -1L && System.currentTimeMillis() < menuTime + 5000L )
		{
			return random( 300, 500 );
		}

		menuTime = -1L;
		menuStep = 0;
		useItemOnObject( activeBar, ANVIL );
		menuTime = System.currentTimeMillis();
		return random( 600, 900 );
	}

	private int bank()
	{
		if ( ! isReachable( BANK[0], BANK[1] ) )
		{
			return openDoors();
		}

		if ( ! isBanking() )
		{
			if ( isQuestMenu() )
			{
				answer( 0 );
				return random( 2000, 3000 );
			}

			if ( bankTime != -1L && System.currentTimeMillis() < bankTime + 15000L )
			{
				return random( 300, 500 );
			}

			bankTime = -1L;
			int[] banker = getNpcByIdNotTalk( BANKERS );

			if ( banker[0] != -1 )
			{
				talkToNpc( banker[0] );
				bankTime = System.currentTimeMillis();
				return random( 2000, 3000 );
			}

			walkTo( BANK[0], BANK[1] );
			return random( 1000, 1500 );
		}

		bankTime = -1L;

		if ( ! ensureFromBank( HAMMER, "hammer" )
			|| ! ensureFromBank( SLEEPING_BAG, "sleeping bag" ) )
		{
			return random( 1000, 2000 );
		}

		/*
		 * The progression decision, made fresh every visit: iron the moment
		 * smithing is 15 and the bank still holds iron bars, bronze while it
		 * does not, and a clean stop when there is nothing left to work.
		 */
		int nextBar;

		if ( getLevel( 13 ) >= 15 && bankCount( IRON_BAR ) > 0 )
		{
			nextBar = IRON_BAR;
		}
		else if ( bankCount( BRONZE_BAR ) > 0 )
		{
			nextBar = BRONZE_BAR;
		}
		else
		{
			System.out.println( "[VarrockSmither] No workable bars left in the bank - stopping." );
			setAutoLogin( false );
			stopScript();
			return 1000;
		}

		if ( nextBar != activeBar )
		{
			System.out.println( "[VarrockSmither] Switching to "
				+ ( nextBar == IRON_BAR ? "iron" : "bronze" ) + " bars." );
			activeBar = nextBar;
		}

		/* Everything that is not the kit or the working bar goes back. */
		for ( int i = 0; i < getInventoryCount(); i++ )
		{
			int id = getInventoryId( i );

			if ( id != HAMMER && id != SLEEPING_BAG && id != activeBar )
			{
				deposit( id, getInventoryCount( id ) );
				return random( 1000, 1500 );
			}
		}

		int space = getEmptySlots();

		if ( space > 0 )
		{
			withdraw( activeBar, Math.min( space, bankCount( activeBar ) ) );
			return random( 1000, 1500 );
		}

		closeBank();
		return random( 1000, 1500 );
	}

	/* True when the item is in the inventory, pulling it from the bank if it
	   has to; stops the script when it is in neither place. */
	private boolean ensureFromBank( int id, String name )
	{
		if ( getInventoryCount( id ) > 0 )
		{
			return true;
		}

		if ( bankCount( id ) > 0 )
		{
			withdraw( id, 1 );
			return false;
		}

		System.out.println( "[VarrockSmither] No " + name + " in the inventory or bank - stopping." );
		setAutoLogin( false );
		stopScript();
		return false;
	}

	private int openDoors()
	{
		int bankDoor = getObjectIdFromCoords( BANK_DOOR[1], BANK_DOOR[2] );
		boolean canReach = getY() < 507
			? isReachable( BANK_DOOR[1], BANK_DOOR[2] - 1 )
			: isReachable( BANK_DOOR[1], BANK_DOOR[2] );

		if ( bankDoor == BANK_DOOR[0] && canReach )
		{
			atObject( BANK_DOOR[1], BANK_DOOR[2] );
			return random( 800, 1200 );
		}

		canReach = getY() < 510
			? isReachable( SMITHY_DOOR[1], SMITHY_DOOR[2] - 1 )
			: isReachable( SMITHY_DOOR[1], SMITHY_DOOR[2] );
		int smithyDoor = getWallObjectIdFromCoords( SMITHY_DOOR[1], SMITHY_DOOR[2] );

		if ( smithyDoor == SMITHY_DOOR[0] && canReach )
		{
			atWallObject( SMITHY_DOOR[1], SMITHY_DOOR[2] );
			return random( 800, 1200 );
		}

		walkTo( BANK[0], BANK[1] );
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

		if ( str.contains( "hammer the metal" ) )
		{
			hammered++;
			menuTime = -1L;
			menuStep = 0;
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

		int gained = getXpForLevel( 13 ) - initialXP;

		drawString( "VarrockSmither", x, y, 1, orange );
		y += 15;
		drawString( "Runtime: " + ( secs / 3600 ) + ":" + String.format( "%02d", ( secs % 3600 ) / 60 ) + ":" + String.format( "%02d", secs % 60 ), x, y, 1, white );
		y += 15;
		drawString( "Bar: " + ( activeBar == IRON_BAR ? "iron" : "bronze" ) + " - Smiths: " + hammered, x, y, 1, white );
		y += 15;
		drawString( "XP: " + gained + " - " + (int)( ( gained * 3600L ) / secs ) + " XP/Hr", x, y, 1, white );
	}
}
