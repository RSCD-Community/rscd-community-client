/**
 * Buys empty vials from Jatix, fills them at the Falador fountain, banks them.
 *
 * Herblaw is the one skill here with no shortcut: every unfinished potion
 * wants a vial of water, and a vial is filled one click at a time. Twenty
 * eight vials is one inventory, a level up in the twenties is two hundred
 * potions, and nobody should be doing that by hand.
 *
 * The loop runs between three places:
 *
 *   Falador west bank   (332,554)   coins out, vials of water in
 *   fountain            (327,545)   four waypoints along, ON the way
 *   Jatix's Olde        (369,506)   fifty vials in stock on a five second
 *   Herbalist                       respawn -- the fastest vial supply in
 *                                   the game
 *
 * Five shops sell empty vials. Walking the landscape's own collision data
 * from each of them to the nearest bank counter rules out four: Entrana is
 * unreachable on foot, Shilo Village needs its quest, Grud's is a hundred
 * and five steps from a counter, and Tai Bwo Wannai is a hundred and forty
 * eight. Jatix is the closest, and the fountain is the only water source
 * that is actually ON the walk rather than beside it.
 *
 * THE WALL. Jatix is not simply "west of Falador" -- he is on the far side
 * of the city wall, and Falador is sealed. Flood-filling the collision grid
 * with diagonal wall pieces honoured gives 139,669 tiles on the city side
 * and 64,279 on his, joined by exactly three things: the north gate (137 at
 * 341,487), the south gate (138 at 343,581) and the handholds (693 at
 * 339,555, agility 5, one way). So this script keeps TWO routes and a gate
 * click between them, and it is not worth trying to collapse them into one:
 * a single array of waypoints has no way to say "and here you must click
 * something".
 *
 * Filling grants no experience, so there is no fatigue to manage and no
 * sleeping bag to carry. This runs until the coins run out or a requested
 * target is reached.
 *
 * Usage:  VialFiller          fill until the bank runs out of coins
 *         VialFiller 500      stop after five hundred
 *
 * Jatix stocks eye of newt beside the vials, fifty at a time, which is the
 * other half of an attack potion. Buying those too would be one more line
 * here -- it is left out on purpose, because a script that quietly spends
 * your money on a second thing is a script you have to read before you can
 * trust it.
 *
 * A warning about the neighbours: two druids roam the patch around Jatix and
 * they are aggressive. They stop bothering anyone past about combat sixty,
 * but a low level character left here overnight will not survive it.
 */
public class VialFiller extends Script
{
	/* Vial in, vial of water out. */
	private static final int VIAL = 465;
	private static final int VIAL_OF_WATER = 464;
	private static final int COINS = 10;

	/** The Falador fountain. Its footprint covers 327-328 by 545-546. */
	private static final int FOUNTAIN_X = 327;
	private static final int FOUNTAIN_Y = 545;

	/** Falador's north gate. Clicking it steps you through, both ways. */
	private static final int GATE_X = 341;
	private static final int GATE_Y = 487;

	private static final int JATIX = 230;
	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };

	/**
	 * Inside the wall: bank counter, fountain, gate. Sampled every fourth
	 * tile off the collision grid, so every one of these is a tile a player
	 * can actually stand on -- the tile beside it may be a wall bit or a
	 * market stall.
	 *
	 * Generate replacements with the server's own tools rather than by hand:
	 *   python3 tools/route.py 332 554 341 488 --step 4 --java
	 * It reads the same grid the server does and refuses to cross a diagonal.
	 */
	private static final int[][] CITY = {
		{332,554},{330,552},{327,551},{327,547},{326,545},{326,541},
		{324,539},{324,535},{324,531},{324,527},{324,523},{321,522},
		{317,522},{317,518},{317,514},{317,510},{317,506},{317,502},
		{317,498},{317,494},{320,493},{324,493},{328,493},{328,489},
		{331,488},{335,488},{339,488},{341,488}
	};

	private static final int AT_BANK = 0;
	private static final int AT_FOUNTAIN = 4;
	private static final int AT_GATE_IN = CITY.length - 1;

	/** Outside the wall: gate to Jatix's door. */
	private static final int[][] TAVERLEY = {
		{342,487},{342,491},{342,495},{344,497},{348,497},{352,497},
		{356,497},{360,497},{362,499},{364,501},{368,501},{371,502},
		{371,506},{369,506}
	};

	private static final int AT_GATE_OUT = 0;
	private static final int AT_SHOP = TAVERLEY.length - 1;

	/** One inventory of vials, leaving a slot for the coins. */
	private static final int LOAD = 28;

	/** Twenty eight vials cost about sixty coins, so this is many trips. */
	private static final int COIN_LOAD = 5000;
	private static final int COIN_FLOOR = 500;

	private int filled;
	private int target;
	private boolean done;

	/**
	 * Consecutive shop visits where the vial row was missing entirely.
	 *
	 * A shop that has sold out of a line does not show it at zero, it stops
	 * listing it, so "not there" and "none left" are the same thing seen a
	 * moment apart. Buying faster than Jatix restocks makes that the normal
	 * case rather than the exception, and treating the first one as fatal is
	 * what turned a twenty minute run into a logout. Only a row that stays
	 * missing across many visits means the shop genuinely does not deal in
	 * vials any more.
	 */
	private int vialMisses;

	/** About five minutes of waiting at four seconds a look. */
	private static final int MISSES_BEFORE_GIVING_UP = 75;

	public VialFiller( Extension ex )
	{
		super( ex );
	}

	@Override
	public void init( String params )
	{
		filled = 0;
		done = false;
		target = 0;

		if ( params != null && params.trim().length() > 0 )
		{
			try
			{
				target = Integer.parseInt( params.trim() );
			}
			catch ( NumberFormatException e )
			{
				System.out.println( "VialFiller: '" + params
					+ "' is not a number, filling until the coins run out." );
			}
		}
	}

	@Override
	public int main()
	{
		if ( done )
		{
			return 5000;
		}

		if ( isBanking() )
		{
			return bankStep();
		}

		if ( isShopOpen() )
		{
			return shopStep();
		}

		if ( isQuestMenu() )
		{
			/* Both Jatix and the bankers open on their first option. */
			answer( 0 );
			return random( 1500, 2500 );
		}

		if ( getInventoryCount( VIAL ) > 0 )
		{
			return fillStep();
		}

		if ( getInventoryCount( VIAL_OF_WATER ) > 0 )
		{
			return goBank();
		}

		/* Empty handed: money first, then the shop. */
		if ( getInventoryCount( COINS ) < COIN_FLOOR )
		{
			return goBank();
		}

		return goShop();
	}

	/**
	 * Which side of the wall are we on?
	 *
	 * This is a question about these two routes, not about Falador in
	 * general: every tile in CITY has x of 341 or less and every tile in
	 * TAVERLEY has 342 or more, because the wall runs down that seam here.
	 * It says nothing useful anywhere else on the map, which is why it is
	 * private and why the routes are named rather than numbered.
	 */
	private boolean inCity()
	{
		return getX() <= GATE_X;
	}

	/**
	 * Click the gate and let it carry us through.
	 *
	 * Deliberately no walkTo afterwards. Gates move you across themselves,
	 * and a walk on the far tile "just in case" is worse than useless: if
	 * the click missed, the client honours the walk the only way it can,
	 * which is back through the city and the whole way round.
	 */
	private int useGate( int[][] route, int approach )
	{
		if ( distanceTo( route[approach][0], route[approach][1] ) > 1 )
		{
			return walkRoute( route, approach );
		}

		atObject( GATE_X, GATE_Y );
		return random( 2000, 3000 );
	}

	/**
	 * Get back inside the wall, wherever we are now.
	 *
	 * Returns a delay to wait if it did something, or -1 for "already
	 * inside, carry on" -- every caller wants that shape, since walking to
	 * the gate is a whole tick's work on its own.
	 */
	private int goCity()
	{
		return inCity() ? -1 : useGate( TAVERLEY, AT_GATE_OUT );
	}

	/** At the fountain, one vial per click. Fill happens inside the wall. */
	private int fillStep()
	{
		int gate = goCity();

		if ( gate != -1 )
		{
			return gate;
		}

		if ( distanceTo( CITY[AT_FOUNTAIN][0], CITY[AT_FOUNTAIN][1] ) > 1 )
		{
			return walkRoute( CITY, AT_FOUNTAIN );
		}

		useItemOnObject( VIAL, FOUNTAIN_X, FOUNTAIN_Y );
		return random( 600, 900 );
	}

	/** Walk to a banker and open the bank. */
	private int goBank()
	{
		int gate = goCity();

		if ( gate != -1 )
		{
			return gate;
		}

		if ( distanceTo( CITY[AT_BANK][0], CITY[AT_BANK][1] ) > 6 )
		{
			return walkRoute( CITY, AT_BANK );
		}

		int[] banker = getNpcByIdNotTalk( BANKERS );

		if ( banker[0] != -1 )
		{
			talkToNpc( banker[0] );
			return random( 3000, 4000 );
		}

		walkTo( CITY[AT_BANK][0], CITY[AT_BANK][1] );
		return random( 1200, 1800 );
	}

	/**
	 * At the counter: water in, coins out.
	 *
	 * Closing the bank is what ends the visit. Withdraw does nothing when the
	 * inventory is already full, so a step that only ever withdraws stands at
	 * the counter forever, quietly asking for what it already has.
	 */
	private int bankStep()
	{
		int water = getInventoryCount( VIAL_OF_WATER );

		if ( water > 0 )
		{
			/* Counted here rather than at the fountain: a click with nothing
			   in hand filled nothing, and the tally should say what actually
			   went into the bank. */
			deposit( VIAL_OF_WATER, water );
			filled += water;
			System.out.println( "VialFiller: " + filled + " filled." );

			if ( target > 0 && filled >= target )
			{
				System.out.println( "VialFiller: target of " + target
					+ " reached. Stopping." );
				done = true;
				closeBank();
			}

			return random( 900, 1300 );
		}

		if ( getInventoryCount( COINS ) < COIN_FLOOR )
		{
			int purse = bankCount( COINS );

			if ( purse < COIN_FLOOR )
			{
				System.out.println( "VialFiller: out of coins, " + filled
					+ " filled in total. Stopping." );
				done = true;
				closeBank();
				return 5000;
			}

			withdraw( COINS, purse < COIN_LOAD ? purse : COIN_LOAD );
			return random( 1200, 1600 );
		}

		closeBank();
		return random( 900, 1300 );
	}

	/** Out through the gate, along to Jatix, open the shop. */
	private int goShop()
	{
		if ( inCity() )
		{
			return useGate( CITY, AT_GATE_IN );
		}

		if ( distanceTo( TAVERLEY[AT_SHOP][0], TAVERLEY[AT_SHOP][1] ) > 4 )
		{
			return walkRoute( TAVERLEY, AT_SHOP );
		}

		int[] jatix = getNpcByIdNotTalk( JATIX );

		if ( jatix[0] != -1 )
		{
			talkToNpc( jatix[0] );
			return random( 3000, 4000 );
		}

		walkTo( TAVERLEY[AT_SHOP][0], TAVERLEY[AT_SHOP][1] );
		return random( 1200, 1800 );
	}

	/**
	 * At the counter in Taverley. Buy a load and leave.
	 *
	 * Ten at a time rather than the whole load in one go: each buy is its own
	 * packet, and a burst of twenty eight against a shelf that only holds
	 * fifty is how a script ends up buying air and thinking it succeeded.
	 * The shelf refills every five seconds, so waiting costs nothing.
	 */
	private int shopStep()
	{
		int held = getInventoryCount( VIAL );

		if ( held >= LOAD || getInventoryCount() >= 29 )
		{
			closeShop();
			return random( 900, 1300 );
		}

		int slot = getShopItemById( VIAL );

		if ( slot == -1 )
		{
			vialMisses++;

			if ( vialMisses >= MISSES_BEFORE_GIVING_UP )
			{
				System.out.println( "VialFiller: Jatix is not selling vials. Stopping." );
				done = true;
				closeShop();
				return 5000;
			}

			/* Sold clean out, so the row is gone until it restocks. Stay at
			   the counter and look again. */
			return random( 3000, 4000 );
		}

		vialMisses = 0;

		int stock = getShopItemAmount( slot );

		if ( stock < 1 )
		{
			/* Sold out. It comes back in five seconds. */
			return random( 3000, 4000 );
		}

		int want = LOAD - held;

		if ( want > stock )
		{
			want = stock;
		}

		if ( want > 10 )
		{
			want = 10;
		}

		buyShopItem( slot, want );
		return random( 1200, 1800 );
	}

	/**
	 * Walk one route towards one of its named ends.
	 *
	 * Rejoining the nearest waypoint first is what makes a route survive
	 * being knocked off it. "Nearest" is straight line distance, so a route
	 * that doubled back on itself would pick the wrong end -- neither of
	 * these does, and every fourth tile is plenty of spacing to keep it that
	 * way.
	 */
	private int walkRoute( int[][] route, int destination )
	{
		int nearest = 0;
		int nearestDist = Integer.MAX_VALUE;

		for ( int i = 0; i < route.length; i++ )
		{
			int dist = distanceTo( route[i][0], route[i][1] );

			if ( dist < nearestDist )
			{
				nearestDist = dist;
				nearest = i;
			}
		}

		int next;

		if ( nearestDist > 4 )
		{
			/* Knocked off the route: get back on it before going anywhere. */
			next = nearest;
		}
		else if ( nearest < destination )
		{
			next = nearest + 1;
		}
		else if ( nearest > destination )
		{
			next = nearest - 1;
		}
		else
		{
			next = destination;
		}

		walkTo( route[next][0], route[next][1] );
		return random( 1000, 1500 );
	}
}
