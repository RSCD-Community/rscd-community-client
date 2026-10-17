import org.rscdaemon.client.*;
import java.util.Locale;

/**
 * EssenceMiner - the runecrafter's supply line, and nothing else.
 *
 * Aubury's rune shop in south-east Varrock is a few tiles from the east
 * bank, and he teleports anyone who has finished Rune Mysteries into the
 * essence mine. So the day is a loop with almost no walking in it: ask
 * Aubury for the mine, chip twenty-seven essence off the nearest spire, step
 * through a portal back to his shop, bank, ask again.
 *
 * No altar. Essence is the slow half of runecrafting and the altars are
 * spread across the whole map, so the pile is built first and crafted later
 * by whichever script runs the altar trips. This one's only measure of
 * success is essence in the bank.
 *
 * Needs: Rune Mysteries complete, a pickaxe, a sleeping bag. Three slots go
 * to the pickaxe, the bag and a talisman left in the pack, which is why a
 * trip is twenty-seven. Every swing at a spire succeeds, so the trip time is
 * fixed and the count is what the log reports.
 */
public final class EssenceMiner extends Script
{
	private static final int MINING = 14;

	/** Aubury and where he keeps shop. The return portal drops you beside him. */
	private static final int AUBURY   = 54;
	private static final int AUBURY_X = 102;
	private static final int AUBURY_Y = 522;

	/*
	 * The shop is a closed room: one door in its east wall, on the west edge
	 * of (104,525), with (103,525) the tile just inside. walkTo never clicks
	 * a door, so a bot sent to Aubury's tile from the street stands outside
	 * forever with no message to say why. Both directions go through here.
	 */
	private static final int DOOR_X = 104;
	private static final int DOOR_Y = 525;
	private static final int INSIDE_X = 103;
	private static final int INSIDE_Y = 525;

	/** The mine: an X of walkways on a plane of its own, spires at the arms. */
	private static final int SPIRE  = 1206;
	private static final int PORTAL = 1207;
	private static final int MINE_CENTER_X = 785;
	private static final int MINE_CENTER_Y = 3075;

	/** Varrock east bank: the counter lies behind the north door. */
	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };
	private static final int BANK_X = 101;
	private static final int BANK_Y = 512;

	private static final int PURE_ESSENCE = 1305;
	private static final int RUNE_ESSENCE = 1304;
	private static final int[] ESSENCE = { PURE_ESSENCE, RUNE_ESSENCE };

	private static final int[] PICKAXES = { 1262, 1261, 1260, 1259, 1258, 156 };
	private static final int SLEEPING_BAG = 1263;

	private static final int PACK_SLOTS = 30;

	private int trips;
	private int banked;
	private long started;

	/* The one timing the server imposes: a swing at a spire is a second and
	   a half, and clicking the spire again inside that window is ignored. */
	private long swingTime;

	/** A line every twenty seconds saying where it is and what it holds. */
	private long pulseTime;

	public EssenceMiner( Extension ex )
	{
		super( ex );
	}

	@Override
	public void init( String params )
	{
		started = System.currentTimeMillis();
		log( "Essence runs through Aubury. Mining " + getLevel( MINING ) + "." );
	}

	@Override
	public int main()
	{
		if ( getInventoryCount() == 0 )
		{
			return 1000;   // inventory packet has not arrived yet
		}

		if ( System.currentTimeMillis() >= pulseTime )
		{
			pulseTime = System.currentTimeMillis() + 20000L;
			log( "pulse " + getX() + "," + getY() + " inv=" + getInventoryCount()
				+ " ess=" + getInventoryCount( ESSENCE ) + " fat=" + getFatigue()
				+ " menu=" + isQuestMenu() + " bank=" + isBanking()
				+ " mining=" + getLevel( MINING ) );
		}

		if ( isQuestMenu() )
		{
			return menuStep();
		}

		if ( getFatigue() > 90 )
		{
			if ( getInventoryCount( SLEEPING_BAG ) < 1 )
			{
				log( "Fatigued with no sleeping bag - stopping." );
				return 60000;
			}

			if ( ! isSleeping() )
			{
				useSleepingBag();
			}

			return random( 1000, 2000 );
		}

		if ( isBanking() )
		{
			return bankStep();
		}

		if ( getInventoryIndex( PICKAXES ) < 0 )
		{
			log( "No pickaxe - stopping." );
			return 60000;
		}

		if ( inMine() )
		{
			return mineStep();
		}

		if ( distanceTo( AUBURY_X, AUBURY_Y ) <= 40 )
		{
			return varrockStep();
		}

		log( "Lost at " + getX() + "," + getY() + " - not walking blind." );
		return random( 5000, 8000 );
	}

	/* --------------------------------------------------------- the mine -- */

	private boolean inMine()
	{
		return getY() >= 3040 && getY() <= 3110 && getX() >= 740 && getX() <= 830;
	}

	private int mineStep()
	{
		if ( getInventoryCount() >= PACK_SLOTS )
		{
			int[] portal = getObjectById( PORTAL );

			if ( portal[0] != -1 )
			{
				atObject( portal[1], portal[2] );
				return random( 2000, 3000 );
			}

			walkTo( MINE_CENTER_X, MINE_CENTER_Y );
			return random( 1500, 2500 );
		}

		if ( System.currentTimeMillis() < swingTime )
		{
			return random( 200, 400 );
		}

		int[] spire = getObjectById( SPIRE );

		if ( spire[0] != -1 )
		{
			atObject( spire[1], spire[2] );
			swingTime = System.currentTimeMillis() + 1500L;
			return random( 1600, 1900 );
		}

		/* The spires stand some twenty tiles out along the arms, so from the
		   landing tile one may be out of view. Walk the north-west arm. */
		walkTo( 770, 3060 );
		return random( 1500, 2500 );
	}

	/* ------------------------------------------------------------ Varrock -- */

	private int varrockStep()
	{
		boolean inside = insideShop();

		if ( getInventoryCount( ESSENCE ) > 0 )
		{
			return inside ? throughDoor( DOOR_X, DOOR_Y ) : openBank();
		}

		if ( inside )
		{
			int[] aubury = getNpcByIdNotTalk( AUBURY );

			if ( aubury[0] != -1 )
			{
				talkToNpc( aubury[0] );
				return random( 2500, 3500 );
			}

			return random( 1000, 2000 );   // he paces; wait for him to come back into view
		}

		if ( getX() == DOOR_X && getY() == DOOR_Y )
		{
			return throughDoor( INSIDE_X, INSIDE_Y );
		}

		walkTo( DOOR_X, DOOR_Y );
		return random( 1000, 1500 );
	}

	/** The room Aubury paces: the portal lands you in it, the door is the only way out. */
	private boolean insideShop()
	{
		return getX() >= 101 && getX() <= 103 && getY() >= 522 && getY() <= 525;
	}

	/**
	 * Click the door and step through it. The click opens it from either
	 * side, but the client only walks through from the side you stand on,
	 * so the far tile is passed in.
	 */
	private int throughDoor( int toX, int toY )
	{
		atWallObject( DOOR_X, DOOR_Y );
		walkTo( toX, toY );
		return random( 1500, 2200 );
	}

	/**
	 * Aubury's menu, answered by what the options say. The teleport line is
	 * the last one and only appears once Rune Mysteries is done; a bank
	 * window never shows a menu here, so anything else open is backed out of.
	 */
	private int menuStep()
	{
		String[] options = questMenuOptions();
		int out = -1;

		for ( int i = 0; i < options.length; i++ )
		{
			String o = options[i] == null ? "" : options[i].toLowerCase( Locale.ENGLISH );

			if ( o.indexOf( "essence mine" ) != -1 )
			{
				answer( i );
				return random( 2500, 3500 );
			}

			if ( o.indexOf( "nothing" ) != -1 || o.indexOf( "bye" ) != -1
				|| o.indexOf( "cancel" ) != -1 || o.indexOf( "no thank" ) != -1 )
			{
				out = i;
			}
		}

		if ( out == -1 )
		{
			log( "Aubury offers no mine trip - is Rune Mysteries finished?" );
			out = options.length - 1;
		}

		answer( out );
		return random( 1200, 1800 );
	}

	/* ----------------------------------------------------------- the bank -- */

	private int openBank()
	{
		int[] banker = getNpcByIdNotTalk( BANKERS );

		/* Within reach only: from the street outside, a banker is visible
		   behind a wall the client cannot path through, and asking it from
		   there does nothing and says nothing. */
		if ( banker[0] != -1 && distanceTo( banker[1], banker[2] ) <= 4 )
		{
			talkToNpc( banker[0] );
			return random( 3000, 4000 );
		}

		walkTo( BANK_X + random( 0, 1 ), BANK_Y + random( 0, 1 ) );
		return random( 1000, 1500 );
	}

	private int bankStep()
	{
		for ( int i = 0; i < ESSENCE.length; i++ )
		{
			int count = getInventoryCount( ESSENCE[i] );

			if ( count > 0 )
			{
				deposit( ESSENCE[i], count );
				banked += count;
				return random( 1000, 1500 );
			}
		}

		trips++;
		long hours = Math.max( 1L, ( System.currentTimeMillis() - started ) / 60000L );
		log( "Trip " + trips + " banked. " + banked + " essence this session, "
			+ bankCount( ESSENCE ) + " in the bank, "
			+ ( banked * 60L / hours ) + "/hr." );
		closeBank();
		return random( 1000, 1500 );
	}

	@Override
	public void onServerMessage( String message )
	{
		if ( message.indexOf( "@" ) == 0 && message.length() > 5 )
		{
			message = message.substring( 5 );
		}
		log( "server: " + message );
	}

	@Override
	public void paint()
	{
		drawString( "EssenceMiner  trips " + trips + "  banked " + banked, 115, 40, 1, 0xFFFFFF );
	}

	private void log( String words )
	{
		System.out.println( "[EssenceMiner] " + words );
	}
}
