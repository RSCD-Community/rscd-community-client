import org.rscdaemon.client.*;
import java.util.Locale;

/**
 * FableMiner - the first of Fable's crew: a character who mines something,
 * stands where people walk past, and sells what it mined.
 *
 * The counter is not here. Announcing, answering, trading and refusing
 * politely are the same machine for every one of the crew, and they live in
 * MerchantScript in the client. What is left in this file is the half that
 * differs: what it sells, what it costs, and the day's work.
 *
 * The work is Varrock south-east: tin and copper until mining 15, iron from
 * there, banking at Varrock East and upgrading the pickaxe as the level
 * allows. Once the bank holds five thousand iron ore it walks to Draynor,
 * turns the lot into certificates at Giles, and carries them back as stock.
 *
 * The bank must start with the pickaxes it will grow into -- bronze through
 * rune -- and a sleeping bag. Everything else it provides for itself.
 *
 * The crew is meant to graduate: when this one's mining is high enough to be
 * worth putting on coal, a fresh character takes over the iron. That is a
 * change of rocks, ore and route, which is why those three things are kept
 * together at the top rather than scattered through the file.
 */
public final class FableMiner extends MerchantScript
{
	/* ------------------------------------------------------------- goods -- */

	/** Iron ore certificate. Five ore to Giles in Draynor makes one. */
	private static final int GOODS = 517;

	/** Coins per certificate. Shop value of the five ore inside one is 85. */
	private static final int PRICE = 500;

	/** Inventory slots in a pack. Certificates need one free unless some
	    are already carried, in which case they stack into that slot. */
	private static final int PACK_SLOTS = 30;

	public FableMiner( Extension ex )
	{
		super( ex );
	}

	@Override
	protected int[] goods()
	{
		return new int[] { GOODS };
	}

	@Override
	protected int[] goodsPer()
	{
		return new int[] { 1 };
	}

	@Override
	protected int price()
	{
		return PRICE;
	}

	@Override
	protected String goodsPlural()
	{
		return "iron ore certs";
	}

	@Override
	protected String tag()
	{
		return "FableMiner";
	}

	@Override
	protected String outOfStock()
	{
		return "Sorry m8, I'm out of stock - back once I've mined some more";
	}

	@Override
	protected boolean interested( String said )
	{
		return super.interested( said )
			|| said.indexOf( "cert" ) != -1
			|| said.indexOf( "iron" ) != -1
			|| said.indexOf( "ore" ) != -1;
	}

	@Override
	public void init( String params )
	{
		certRun   = false;
		homeRun   = false;
		exchanges = 0;
		moveTime  = -1L;
		note( "Desk open. Selling iron ore certs at " + PRICE + "gp." );
	}

	/* ------------------------------------------------------------ mining -- */

	private static final int MINING = 14;

	/** Iron needs mining 15; below that it is tin and copper. */
	private static final int IRON_LEVEL = 15;

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

	/* Rune down to bronze, and the mining level each one demands. */
	private static final int[] PICKAXES     = { 1262, 1261, 1260, 1259, 1258, 156 };
	private static final int[] PICKAXE_REQS = {   41,   31,   21,    6,    1,   1 };

	private static final int SLEEPING_BAG = 1263;

	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };

	/**
	 * Iron ore in the bank that sets off a trip to Giles.
	 *
	 * Two things pull against each other here. Draynor is a long walk, and a
	 * desk that spends its day walking is a desk that mines nothing -- so the
	 * batch wants to be large. But the desk cannot sell what it has not
	 * certified, and below the threshold it stands at Varrock telling
	 * everyone it is out of stock -- so the batch wants to be small.
	 *
	 * Twenty five hundred splits it: a couple of runs a day rather than one a
	 * week, and the walk pays for itself twice, because a merchant standing
	 * in Draynor for the length of an exchange is a merchant Draynor can buy
	 * from. The trip is stock and shopfront at once.
	 */
	private static final int CERT_BATCH = 2500;

	/** Giles takes twenty-five ore at a time, and gives five certificates. */
	private static final int PER_EXCHANGE = 25;

	/**
	 * How far off a known route still counts as being on it. Wide enough to
	 * cover the gap between waypoints, narrow enough that a death spawn or a
	 * hand-placed bot somewhere unrelated still reads as genuinely lost.
	 */
	private static final int ROUTE_SLACK = 8;

	/*
	 * Varrock East bank to the south-east mine, bank end first. Waypoints
	 * sampled off a BFS of the server's own collision grid, edge bits
	 * included, so every hop is genuinely walkable: the bank's only doorway
	 * is on its north side, so the route runs the y=509-510 corridor along
	 * the bank's north face before turning south-west, and crosses the
	 * mine's north fence through the gap west of x=79.
	 */
	private static final int[][] PATH = {
		{ 101, 512 }, { 102, 509 }, {  97, 508 }, {  91, 508 }, {  85, 508 },
		{  83, 512 }, {  83, 517 }, {  82, 521 }, {  81, 525 }, {  78, 527 },
		{  77, 531 }, {  77, 536 }, {  76, 542 }, {  74, 546 }
	};

	/*
	 * Varrock East bank to Draynor bank, sampled the same way: out of
	 * Varrock's west gate, down the road past the south gate, then the long
	 * run south to Draynor market. Giles stands at (229,631), two tiles off
	 * the second-to-last waypoint.
	 */
	private static final int[][] ROAD = {
		{ 101, 512 }, { 104, 509 }, { 110, 509 }, { 116, 509 }, { 122, 509 },
		{ 128, 509 }, { 134, 509 }, { 140, 509 }, { 146, 509 }, { 152, 509 },
		{ 157, 510 }, { 163, 510 }, { 168, 511 }, { 173, 512 }, { 179, 512 },
		{ 185, 512 }, { 191, 512 }, { 197, 512 }, { 203, 512 }, { 209, 512 },
		{ 215, 512 }, { 215, 518 }, { 215, 524 }, { 215, 530 }, { 217, 534 },
		{ 223, 534 }, { 229, 534 }, { 232, 537 }, { 232, 543 }, { 232, 549 },
		{ 232, 555 }, { 232, 561 }, { 232, 567 }, { 231, 572 }, { 230, 577 },
		{ 229, 582 }, { 229, 588 }, { 229, 594 }, { 229, 600 }, { 229, 606 },
		{ 229, 612 }, { 229, 618 }, { 229, 624 }, { 229, 630 }, { 225, 632 },
		{ 220, 633 }, { 220, 636 }
	};

	private static final int GILES     = 225;
	private static final int GILES_X   = 229;
	private static final int GILES_Y   = 632;

	/** Set at the Varrock bank when the ore pile is big enough to cert. */
	private boolean certRun;

	/**
	 * Set when Giles has nothing left to trade, and held until the mine is
	 * back in reach. Draynor alone used to decide where the script was in its
	 * cycle, which meant there was no way to be standing in Draynor and *not*
	 * be on a cert run -- the finished run fell straight back into the cert
	 * branch and re-announced itself forever. Leaving needs its own state.
	 */
	private boolean homeRun;

	/** Queued jiggle after an "I can't reach that" style message. */
	private long moveTime = -1L;

	/** Trips to Giles's counter this session, for the log. */
	private int exchanges;

	/**
	 * What it does for a living, between customers.
	 *
	 * Everything here is decided from where it is standing and what it is
	 * carrying, so a relogin in the middle of anything picks up where it left
	 * off. The one remembered thing is certRun, and losing that costs a
	 * single wasted bank trip: the next full load sees the pile again.
	 */
	@Override
	protected int work()
	{
		if ( getInventoryIndex( PICKAXES ) < 0 && ! isBanking() && ! certRun )
		{
			/* No pick and not at a bank to fetch one. Nothing to do but walk
			   back and sort it out there. */
			if ( insideBank() )
			{
				return openBank();
			}

			return walkPath( false );
		}

		if ( moveTime != -1L && System.currentTimeMillis() >= moveTime )
		{
			moveTime = -1L;
			walkTo( getX() + random( -1, 1 ), getY() + random( -1, 1 ) );
			return random( 1000, 1500 );
		}

		if ( getFatigue() > 90 )
		{
			if ( getInventoryCount( SLEEPING_BAG ) < 1 )
			{
				/* Fatigued with nothing to sleep in: the bank has the spare. */
				return insideBank() ? openBank() : walkPath( false );
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

		if ( homeRun )
		{
			/* The road home is longer than the "lost" radius below, so this
			   flag has to survive the whole walk, not just the Draynor tiles. */
			if ( distanceTo( 86, 528 ) <= 70 )
			{
				homeRun = false;
			}
			else
			{
				return walkRoad( false );
			}
		}
		else if ( certRun || inDraynor() )
		{
			return certStep();
		}

		if ( getInventoryCount() == 30 )
		{
			return insideBank() ? openBank() : walkPath( false );
		}

		if ( insideMine() )
		{
			int[] rock;

			if ( getLevel( MINING ) >= IRON_LEVEL )
			{
				rock = getObjectById( IRON_ROCKS );
			}
			else if ( getInventoryCount( COPPER_ORE ) <= getInventoryCount( TIN_ORE ) )
			{
				rock = getObjectById( COPPER_ROCKS );
			}
			else
			{
				rock = getObjectById( TIN_ROCKS );
			}

			if ( rock[0] != -1 )
			{
				atObject( rock[1], rock[2] );
				return random( 750, 950 );
			}

			return random( 1000, 2000 );   // respawns are a few seconds
		}

		if ( distanceTo( 86, 528 ) > 70 )
		{
			/* Somewhere else entirely -- dropped in by hand, or a death. The
			   road home is the Draynor one only if it is near it. */
			if ( inDraynor() )
			{
				return certStep();
			}

			/*
			 * Standing on a road it knows is not being lost. Everything else
			 * here reasons from flags, and a flag is exactly what a script
			 * does not keep: a relogin restarts the script from scratch, and
			 * the Draynor road is forty-seven waypoints of which almost none
			 * are inside the radius above or inside Draynor. So a disconnect
			 * anywhere along that stretch used to strand the bot for good --
			 * it would sit and count "lost" every six seconds, on a tile that
			 * was in its own route table the whole time, and every further
			 * relogin stranded it again.
			 *
			 * Asking the route where it is instead of asking a field survives
			 * restarts, deaths and being dropped in by hand. Walking home
			 * rather than onward is deliberate: it is the one direction that
			 * is right whichever leg it was on, since the bank end re-decides
			 * whether there is enough ore to be worth another trip.
			 */
			if ( distanceToRoute( ROAD ) <= ROUTE_SLACK )
			{
				note( "Off the flags at " + getX() + "," + getY()
					+ " but on the Draynor road - walking home." );
				return walkRoad( false );
			}

			note( "Lost at " + getX() + "," + getY() + " - not walking blind." );
			return random( 5000, 8000 );
		}

		return walkPath( true );
	}

	/* ---------------------------------------------------------- the bank -- */

	/** Walk to a banker and open the bank. */
	private int openBank()
	{
		if ( isQuestMenu() )
		{
			return leaveMenu();
		}

		int[] banker = getNpcByIdNotTalk( BANKERS );

		if ( banker[0] != -1 )
		{
			note( "At the bank carrying "
				+ oresCarried() + " ore, mining " + getLevel( MINING ) + "." );
			talkToNpc( banker[0] );
			return random( 3000, 4000 );
		}

		if ( inDraynor() )
		{
			walkTo( 220 + random( 0, 1 ), 636 );
		}
		else
		{
			walkTo( 101 + random( 0, 1 ), 512 + random( 0, 1 ) );
		}

		return random( 1000, 1500 );
	}

	/**
	 * At the counter. Ore in, the right pickaxe out, and the decision about
	 * whether the pile is big enough to be worth the walk to Draynor.
	 *
	 * Certificates are never banked: they are the shop stock, and they stack
	 * into one slot, so carrying the lot costs nothing.
	 */
	private int bankStep()
	{
		if ( inDraynor() )
		{
			return certBankStep();
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

		/* Certificates belong in the pack, not in here -- stock() counts what
		   is carried, so a cert sitting in the bank is a cert the desk will
		   swear it does not have. Anything that put them in here (a hand
		   deposit, a pack that filled up mid-run) is undone on the next visit
		   rather than left to be noticed by a customer.
		
		   This has to come AFTER the ore goes in: certificates need a slot of
		   their own unless some are already carried, and a pack that just
		   walked in from the mine has none. Withdrawing first means "you
		   don't have room for that", which returns from here without changing
		   anything, so the same step runs again forever. */
		if ( bankCount( GOODS ) > 0 && ( getInventoryCount( GOODS ) > 0
			|| getInventoryCount() < PACK_SLOTS ) )
		{
			note( "Reclaiming " + bankCount( GOODS )
				+ " banked certs -- stock lives in the pack." );
			withdraw( GOODS, bankCount( GOODS ) );
			return random( 1000, 1500 );
		}

		if ( getInventoryCount( SLEEPING_BAG ) < 1 && bankCount( SLEEPING_BAG ) > 0 )
		{
			withdraw( SLEEPING_BAG, 1 );
			return random( 1000, 1500 );
		}

		/* The best pickaxe this mining level can actually swing stays in the
		   pack; every other one carried, better or worse, goes in the bank. */
		int best = -1;

		for ( int i = 0; i < PICKAXES.length; i++ )
		{
			if ( getLevel( MINING ) >= PICKAXE_REQS[i] && getInventoryCount( PICKAXES[i] ) > 0 )
			{
				best = i;
				break;
			}
		}

		if ( best == -1 )
		{
			for ( int i = 0; i < PICKAXES.length; i++ )
			{
				if ( getLevel( MINING ) >= PICKAXE_REQS[i] && bankCount( PICKAXES[i] ) > 0 )
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
			if ( getLevel( MINING ) >= PICKAXE_REQS[i] && bankCount( PICKAXES[i] ) > 0 )
			{
				note( "Swapping up to a better pickaxe." );
				withdraw( PICKAXES[i], 1 );
				return random( 1000, 1500 );
			}
		}

		if ( ! certRun && bankCount( IRON_ORE ) >= CERT_BATCH )
		{
			note( "" + bankCount( IRON_ORE )
				+ " iron ore banked - off to Giles." );
			certRun = true;
		}

		closeBank();
		return random( 1000, 1500 );
	}

	/* -------------------------------------------------------- the cert run -- */

	/**
	 * The Draynor end. Withdraw twenty-five ore, walk the few tiles to Giles,
	 * trade them for five certificates, repeat until the pile is gone.
	 *
	 * Twenty-five is Giles's own limit -- his menu offers five to twenty-five
	 * ores and nothing larger -- so a five thousand ore pile is two hundred
	 * of these. That is the price of certificates existing at all, and it is
	 * exactly the sort of errand a bot should be running instead of a person.
	 */
	private int certStep()
	{
		if ( ! inDraynor() )
		{
			return walkRoad( true );
		}

		if ( getInventoryCount( IRON_ORE ) >= PER_EXCHANGE )
		{
			return exchangeStep();
		}

		if ( isBanking() )
		{
			return certBankStep();
		}

		return openBank();
	}

	/** At the Draynor counter: ore out, gems and spare ore in. */
	private int certBankStep()
	{
		for ( int i = 0; i < ORES_TO_BANK.length; i++ )
		{
			if ( ORES_TO_BANK[i] == IRON_ORE )
			{
				continue;
			}

			int count = getInventoryCount( ORES_TO_BANK[i] );

			if ( count > 0 )
			{
				deposit( ORES_TO_BANK[i], count );
				return random( 1000, 1500 );
			}
		}

		int carried = getInventoryCount( IRON_ORE );

		if ( carried < PER_EXCHANGE && bankCount( IRON_ORE ) >= PER_EXCHANGE - carried )
		{
			withdraw( IRON_ORE, PER_EXCHANGE - carried );
			return random( 1000, 1500 );
		}

		if ( carried < PER_EXCHANGE )
		{
			/* Less than one exchange left in the bank: put back what is in
			   hand and go home. The remainder waits for the next batch. */
			if ( carried > 0 )
			{
				deposit( IRON_ORE, carried );
				return random( 1000, 1500 );
			}

			note( "Cert run done - " + stock()
				+ " " + goodsPlural() + " in the pack." );
			certRun = false;
			homeRun = true;
			closeBank();
			return random( 1000, 1500 );
		}

		closeBank();
		return random( 1000, 1500 );
	}

	/**
	 * Back out of whatever menu is open without agreeing to anything.
	 *
	 * Giles keeps talking after a trade goes through, so the script arrives
	 * at this point with his menu still up and no ore left to sell. Clicking
	 * the first option on the way past used to answer "I have some
	 * certificates to trade in", and the two pickers behind it then took the
	 * default as well -- the run handed one certificate straight back for
	 * five ore, every single cycle, which is why a two and a half thousand
	 * ore trip finished on four hundred and ninety-nine certificates instead
	 * of five hundred. A refusal has to be chosen deliberately, never taken
	 * as whatever happens to be first.
	 */
	private int leaveMenu()
	{
		String[] options = questMenuOptions();

		for ( int i = 0; i < options.length; i++ )
		{
			String o = options[i] == null
				? "" : options[i].toLowerCase( Locale.ENGLISH );

			if ( o.indexOf( "nothing" ) != -1 || o.indexOf( "bye" ) != -1
				|| o.indexOf( "cancel" ) != -1 || o.indexOf( "no thank" ) != -1
				|| o.indexOf( "not inter" ) != -1 )
			{
				answer( i );
				return random( 1200, 1800 );
			}
		}

		/* No refusal on offer. The last option is the conventional way out of
		   an RSC menu, and it is a far better guess than the first. */
		answer( options.length - 1 );
		return random( 1200, 1800 );
	}

	/**
	 * Giles's menu, answered by what the options say rather than by counting
	 * clicks: three separate pickers in a row, and a miscount would sell the
	 * wrong ore or trade in certificates instead of ore.
	 */
	private int exchangeStep()
	{
		if ( isQuestMenu() )
		{
			String[] options = questMenuOptions();

			for ( int i = 0; i < options.length; i++ )
			{
				String o = options[i] == null ? "" : options[i].toLowerCase( Locale.ENGLISH );

				if ( o.indexOf( "ore to trade in" ) != -1 || o.equals( "iron" )
					|| o.equals( "twentyfive" ) )
				{
					if ( o.equals( "twentyfive" ) )
					{
						exchanges++;
						note( "Exchange " + exchanges
							+ ": " + PER_EXCHANGE + " ore to Giles." );
					}

					answer( i );
					return random( 1200, 1800 );
				}
			}

			/* Nothing recognised -- back out rather than click blind. */
			return leaveMenu();
		}

		int[] giles = getNpcById( GILES );

		if ( giles[0] != -1 )
		{
			talkToNpc( giles[0] );
			return random( 2000, 3000 );
		}

		walkTo( GILES_X, GILES_Y );
		return random( 1000, 1500 );
	}

	/* ------------------------------------------------------------ walking -- */

	/** Everything in the pack that came out of a rock. */
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

	private boolean inDraynor()
	{
		return ( getX() >= 212 && getX() <= 236 && getY() >= 624 && getY() <= 644 );
	}

	private int walkPath( boolean towardMine )
	{
		return walkWaypoints( PATH, towardMine );
	}

	private int walkRoad( boolean towardDraynor )
	{
		return walkWaypoints( ROAD, towardDraynor );
	}

	/**
	 * Walk a sampled route one waypoint at a time.
	 *
	 * Exact tiles only: every waypoint came out of the collision grid, but
	 * the tile one step off it may be a fence bit or a rock. Rejoining the
	 * nearest waypoint first is what makes a route survive being knocked off
	 * it -- but "nearest" is measured along the route, so a route that
	 * doubles back on itself needs its waypoints spaced wider than the gap
	 * between its two passes.
	 */
	/**
	 * Tiles to the nearest waypoint of a route. Slack of a few tiles is the
	 * point: waypoints sit six apart, so a bot walking the line between two
	 * of them is legitimately three tiles off the nearest one and is not lost
	 * in any sense worth stopping for.
	 */
	private int distanceToRoute( int[][] route )
	{
		int best = Integer.MAX_VALUE;

		for ( int i = 0; i < route.length; i++ )
		{
			int dist = distanceTo( route[i][0], route[i][1] );

			if ( dist < best )
			{
				best = dist;
			}
		}

		return best;
	}

	private int walkWaypoints( int[][] route, boolean forward )
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
			next = nearest;
		}
		else if ( forward )
		{
			next = Math.min( nearest + 1, route.length - 1 );
		}
		else
		{
			next = Math.max( nearest - 1, 0 );
		}

		walkTo( route[next][0], route[next][1] );
		return random( 1000, 1500 );
	}

	@Override
	public void onServerMessage( String message )
	{
		note( "server: " + message );

		String lower = message.toLowerCase( Locale.ENGLISH );

		if ( lower.indexOf( "standing here" ) != -1 )
		{
			/* "I can't reach that" and friends: step off the spot rather
			   than swing at the same unreachable rock for ever. */
			moveTime = System.currentTimeMillis() + random( 1500, 1800 );
		}
	}
}
