import org.rscdaemon.client.*;
import java.util.Locale;

/**
 * FableGuildMiner - the crew's coal miner, and the graduation FableMiner was
 * always pointed at.
 *
 * The shape is the same as FableMiner's, on purpose: mine, bank, and once the
 * pile is worth the walk, turn it into certificates at Giles and stand
 * somewhere busy selling them. What differs is the whole geography, and that
 * is what this file is.
 *
 * The work is the Mining Guild under Falador. Coal only -- there is nothing
 * else down there worth a swing -- banking at Falador East, and Draynor for
 * the certificates because Giles is the only ore certer in the game.
 *
 * Two things about the guild shape the script, and neither is obvious from
 * the map:
 *
 * The entrance is a shed, not a doorway. The ladder sits in a twenty-nine
 * tile room north-east of the bank whose only opening is a door on its north
 * wall, at (274,563). A route plotted from the bank to the ladder comes back
 * "no route" because a door is a wall until something clicks it, so the walk
 * is deliberately two legs with a door click between them.
 *
 * The guild wants mining 60. That is the server's gate on the ladder, not
 * this script's idea, and there is no point pacing the shed below it -- so
 * below sixty it says so once and stands still rather than pretending to
 * work.
 */
public final class FableGuildMiner extends MerchantScript
{
	/* ------------------------------------------------------------- goods -- */

	/** Coal certificate. Five coal to Giles in Draynor makes one. */
	private static final int GOODS = 518;

	/**
	 * Coins per certificate.
	 *
	 * Coal is worth forty-five at a shop against iron ore's seventeen, so the
	 * five inside a certificate are two and a half times an iron one's. The
	 * iron desk sells at five hundred; this holds the same multiple over shop
	 * value rather than inventing a second pricing idea, and lands on a round
	 * two hundred and fifty a coal.
	 */
	private static final int PRICE = 1250;

	/** Inventory slots in a pack. Certificates need one free unless some
	    are already carried, in which case they stack into that slot. */
	private static final int PACK_SLOTS = 30;

	public FableGuildMiner( Extension ex )
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
		return "coal certs";
	}

	@Override
	protected String tag()
	{
		return "FableGuildMiner";
	}

	@Override
	protected String outOfStock()
	{
		return "Sorry m8, out of stock - back once I've mined some more";
	}

	@Override
	protected boolean interested( String said )
	{
		return super.interested( said )
			|| said.indexOf( "cert" ) != -1
			|| said.indexOf( "coal" ) != -1
			|| said.indexOf( "ore" ) != -1;
	}

	@Override
	public void init( String params )
	{
		certRun   = false;
		homeRun   = false;
		exchanges = 0;
		moveTime  = -1L;
		warned    = false;
		note( "Desk open. Selling coal certs at " + PRICE + "gp." );
	}

	/* ------------------------------------------------------------ mining -- */

	private static final int MINING = 14;

	/** The guild ladder is gated on this, by the server, not by this script. */
	private static final int GUILD_LEVEL = 60;

	private static final int[] COAL_ROCKS = { 110, 111 };

	private static final int COAL = 155;

	/* Coal plus the uncut gems a swing occasionally turns up
	   (diamond 157, ruby 158, emerald 159, sapphire 160). */
	private static final int[] ORES_TO_BANK = { COAL, 157, 158, 159, 160 };

	/* Rune down to bronze, and the mining level each one demands. */
	private static final int[] PICKAXES     = { 1262, 1261, 1260, 1259, 1258, 156 };
	private static final int[] PICKAXE_REQS = {   41,   31,   21,    6,    1,   1 };

	private static final int SLEEPING_BAG = 1263;

	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };

	/**
	 * Coal in the bank that sets off a trip to Giles.
	 *
	 * Same reasoning as the iron desk: large enough that the day is not spent
	 * walking, small enough that the shop is not empty for a week. Draynor is
	 * further from Falador than it is from Varrock, which argues for a bigger
	 * batch -- but coal sells for two and a half times what iron does, so the
	 * same number of certificates is worth two and a half times the wait.
	 * Those cancel out closely enough to leave the figure where it is.
	 */
	private static final int CERT_BATCH = 2500;

	/** Giles takes twenty-five ore at a time, and gives five certificates. */
	private static final int PER_EXCHANGE = 25;

	/* ---------------------------------------------------------- geography -- */

	/*
	 * Falador East bank to the tile outside the guild shed's door. Sampled
	 * off the server's own collision grid, bank end first. It runs east and
	 * then south around the block rather than straight across, because
	 * straight across is somebody's house.
	 */
	private static final int[][] PATH = {
		{ 283, 571 }, { 286, 571 }, { 287, 569 }, { 287, 566 }, { 287, 563 },
		{ 285, 562 }, { 282, 562 }, { 279, 562 }, { 276, 562 }, { 274, 562 }
	};

	/*
	 * Falador East bank to Draynor bank: out of Falador's east side, south
	 * down the long road, west along the y=610 run and down into Draynor
	 * market. Giles stands at (229,631), which is waypoint thirty-three.
	 */
	private static final int[][] ROAD = {
		{ 283, 571 }, { 286, 572 }, { 287, 575 }, { 287, 579 }, { 290, 580 },
		{ 290, 584 }, { 290, 588 }, { 290, 592 }, { 290, 596 }, { 290, 600 },
		{ 290, 604 }, { 290, 608 }, { 288, 610 }, { 284, 610 }, { 280, 610 },
		{ 276, 610 }, { 272, 610 }, { 268, 610 }, { 264, 610 }, { 260, 610 },
		{ 256, 610 }, { 252, 610 }, { 248, 610 }, { 245, 611 }, { 245, 615 },
		{ 245, 619 }, { 245, 623 }, { 245, 627 }, { 244, 630 }, { 240, 630 },
		{ 236, 630 }, { 232, 630 }, { 229, 631 }, { 226, 632 }, { 223, 633 },
		{ 220, 634 }, { 220, 636 }
	};

	/*
	 * The bank precinct: the bank door, east along the y=571 wall, then the
	 * run down the outside of the east block and back up to its north-east
	 * corner. Bank end first, same as the other two.
	 *
	 * This is not a route anything deliberately walks. It exists so that
	 * being loose in the streets around the bank is a recoverable state
	 * instead of a terminal one. The certer wanders, exchangeStep chases it,
	 * and the chase ends wherever the npc happened to be standing -- which
	 * is how this bot came to sit at 297,556 for six days: ten tiles off the
	 * mine path against a slack of eight, with eighteen perfectly walkable
	 * steps between it and the nearest flag it recognised.
	 *
	 * Note that straight-line distance is worthless here and the slack test
	 * only works because these waypoints blanket the block. The tile at
	 * 303,571 is twenty tiles from the bank door and eighty-seven steps from
	 * it, because the wall in between has no gate on this side.
	 */
	private static final int[][] PRECINCT = {
		{ 283, 571 }, { 285, 571 }, { 288, 571 }, { 291, 571 }, { 294, 571 },
		{ 296, 570 }, { 296, 567 }, { 296, 564 }, { 296, 561 }, { 296, 558 },
		{ 297, 556 }, { 297, 554 }, { 299, 553 }, { 300, 551 }, { 300, 548 },
		{ 300, 545 }
	};

	/** The shed door, its outside tile, and the tile just inside it. */
	private static final int DOOR_X   = 274;
	private static final int DOOR_Y   = 563;
	private static final int OUT_X    = 274;
	private static final int OUT_Y    = 562;
	private static final int IN_X     = 274;
	private static final int IN_Y     = 565;

	/** Down in the shed, up at the bottom of it. */
	private static final int LADDER_DOWN_X = 274;
	private static final int LADDER_DOWN_Y = 566;
	private static final int LADDER_UP_X   = 274;
	private static final int LADDER_UP_Y   = 3398;

	private static final int GILES     = 225;
	private static final int GILES_X   = 229;
	private static final int GILES_Y   = 632;

	/** Set at the Falador bank when the coal pile is big enough to cert. */
	private boolean certRun;

	/**
	 * Set when Giles has nothing left to trade, and held until Falador is
	 * back in reach. Draynor alone cannot say which way round the cycle the
	 * script is: arriving and leaving stand on the same tiles, so leaving
	 * needs a flag of its own or the finished run falls straight back into
	 * the cert branch and re-announces itself forever.
	 */
	private boolean homeRun;

	/** Said the mining-level piece once, so it is not said every pass. */
	private boolean warned;

	/** Queued jiggle after an "I can't reach that" style message. */
	private long moveTime = -1L;

	/** Trips to Giles's counter this session, for the log. */
	private int exchanges;

	/**
	 * What it does for a living, between customers.
	 *
	 * Everything is decided from where it is standing and what it is
	 * carrying, so a relogin mid-anything picks up where it left off. The
	 * remembered things are certRun and homeRun, and losing either costs one
	 * wasted trip rather than anything worse.
	 */
	@Override
	protected int work()
	{
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
				/* Draynor counts as a bank here. Falling asleep on a cert run
				   with no bag would otherwise send the script walking the
				   Falador path from sixty tiles the wrong side of it. */
				if ( inDraynor() || atBank() )
				{
					return openBank();
				}

				return certRun || homeRun ? walkRoad( ! homeRun ) : headForBank();
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
			/* The road home is far longer than any "am I near it" radius, so
			   this has to survive the whole walk rather than a few tiles. */
			if ( distanceTo( PATH[0][0], PATH[0][1] ) <= 40 )
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

		if ( inGuild() )
		{
			return guildStep();
		}

		if ( getInventoryIndex( PICKAXES ) < 0 || getInventoryCount() == 30 )
		{
			/* Nothing to mine with, or nowhere to put it. Either way the
			   answer is the bank, and the way out of the shed is the door. */
			return atBank() ? openBank() : headForBank();
		}

		if ( getLevel( MINING ) < GUILD_LEVEL )
		{
			if ( ! warned )
			{
				warned = true;
				note( "Mining " + getLevel( MINING ) + " - the guild wants "
					+ GUILD_LEVEL + ". Standing by." );
			}

			return random( 20000, 30000 );
		}

		if ( inShed() )
		{
			atObject( LADDER_DOWN_X, LADDER_DOWN_Y );
			return random( 2000, 3000 );
		}

		if ( atDoor() )
		{
			return throughDoor( IN_X, IN_Y );
		}

		/*
		 * On the Draynor road, wherever along it.
		 *
		 * certRun and homeRun are cleared by init(), and init() runs again on
		 * every relogin -- so one disconnect on the road leaves a bot that is
		 * standing on its own route with no idea it was ever on one. Asking the
		 * route where it is survives that; asking a field does not.
		 *
		 * This has to be asked before the distance test below, and that is the
		 * whole bug. The road passes within sixty tiles of the mine path around
		 * Falador's south-east corner without ever being walkable to it, so the
		 * old order called the bot "close enough", handed walkPath a waypoint
		 * forty tiles away on the far side of the city wall, and the server
		 * quietly declined to path it. No message, no movement, no state change:
		 * it stood there through seventy-three idle logouts.
		 *
		 * Walking home is right on either leg, since the bank end re-decides
		 * what to do next anyway.
		 */
		if ( distanceToRoute( PATH ) > ROUTE_SLACK )
		{
			if ( distanceToRoute( ROAD ) <= ROUTE_SLACK )
			{
				note( "Off the flags at " + getX() + "," + getY()
					+ " but on the Draynor road - walking home." );
				return walkRoad( false );
			}

			if ( distanceToRoute( PRECINCT ) <= ROUTE_SLACK )
			{
				note( "Off the flags at " + getX() + "," + getY()
					+ " but in the bank precinct - walking home." );
				return walkRoad( false );
			}

			/* Genuinely nowhere known. Say so, but say it on a timer: the
			   first time this happened the same line went into the log a
			   hundred thousand times over six days and nobody noticed,
			   because a message that repeats every five seconds reads
			   exactly like a message that is not there. One line a minute,
			   and a louder one once it is clear nothing is going to shake
			   it loose. */
			long now = System.currentTimeMillis();

			if ( lostSince == 0L )
			{
				lostSince = now;
			}

			if ( now >= lostQuiet )
			{
				long mins = ( now - lostSince ) / 60000L;

				note( "Lost at " + getX() + "," + getY()
					+ " - not walking blind. Stuck " + mins + " min." );

				if ( mins >= 10L )
				{
					note( "STUCK: " + mins + " min at " + getX() + ","
						+ getY() + " with no route home - needs a human." );
				}

				lostQuiet = now + 60000L;
			}

			return random( 5000, 8000 );
		}

		lostSince = 0L;
		return walkPath( true );
	}

	/* ------------------------------------------------------- the guild -- */

	/** Underground: swing at coal until the pack is full, then climb out. */
	private int guildStep()
	{
		if ( getInventoryCount() == 30 || getInventoryIndex( PICKAXES ) < 0 )
		{
			atObject( LADDER_UP_X, LADDER_UP_Y );
			return random( 2000, 3000 );
		}

		int[] rock = getObjectById( COAL_ROCKS );

		if ( rock[0] != -1 )
		{
			atObject( rock[1], rock[2] );
			return random( 750, 950 );
		}

		/* Seven of the guild's coal rocks ring the ladder, so an empty screen
		   here means respawns rather than a walk being needed. */
		return random( 1000, 2000 );
	}

	/**
	 * Click the shed door and step through it.
	 *
	 * A door in Classic is opened by clicking the wall it is in, and the
	 * click walks the player through -- but only from the side they are
	 * standing on, so the tile to head for afterwards is the far one and has
	 * to be passed in rather than worked out here.
	 */
	private int throughDoor( int toX, int toY )
	{
		atWallObject( DOOR_X, DOOR_Y );
		walkTo( toX, toY );
		return random( 1500, 2200 );
	}

	/** The way out of the shed, and then the walk to the counter. */
	private int headForBank()
	{
		if ( inShed() )
		{
			return throughDoor( OUT_X, OUT_Y );
		}

		return walkPath( false );
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

		/*
		 * A banker has to be within reach before there is any point talking
		 * to it. The sighting radius is about sixteen tiles and the Falador
		 * and Varrock bankers both roam a box behind their counter, so a bot
		 * loitering anywhere in the surrounding streets can see one, call
		 * itself "at the bank", and spend every tick asking an npc it cannot
		 * walk to -- the counter tile is behind a wall, the server declines
		 * the step, and nothing moves or logs a complaint. That is how the
		 * coal bot came to print "At the bank carrying 27 coal" eight times
		 * from fourteen tiles away before falling through into the lost
		 * branch. Four tiles, and otherwise walk to the bank door like any
		 * other tick. CatherbyLobster had this right first; the miners did
		 * not.
		 */
		if ( banker[0] != -1 && distanceTo( banker[1], banker[2] ) <= 4 )
		{
			note( "At the bank carrying " + oresCarried()
				+ " coal, mining " + getLevel( MINING ) + "." );
			talkToNpc( banker[0] );
			return random( 3000, 4000 );
		}

		if ( inDraynor() )
		{
			walkTo( 220 + random( 0, 1 ), 636 );
		}
		else
		{
			walkTo( 283 + random( 0, 1 ), 570 + random( 0, 1 ) );
		}

		return random( 1000, 1500 );
	}

	/**
	 * At the counter. Coal in, the right pickaxe out, and the decision about
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

		if ( ! certRun && bankCount( COAL ) >= CERT_BATCH )
		{
			note( "" + bankCount( COAL ) + " coal banked - off to Giles." );
			certRun = true;
		}

		closeBank();
		return random( 1000, 1500 );
	}

	/* -------------------------------------------------------- the cert run -- */

	/**
	 * The Draynor end. Withdraw twenty-five coal, walk the few tiles to
	 * Giles, trade them for five certificates, repeat until the pile is gone.
	 *
	 * Twenty-five is Giles's own limit -- his menu offers five to twenty-five
	 * and nothing larger -- so a twenty-five hundred pile is a hundred of
	 * these. That is the price of certificates existing at all, and exactly
	 * the sort of errand a bot should be running instead of a person.
	 */
	private int certStep()
	{
		if ( ! inDraynor() )
		{
			return walkRoad( true );
		}

		if ( getInventoryCount( COAL ) >= PER_EXCHANGE )
		{
			return exchangeStep();
		}

		if ( isBanking() )
		{
			return certBankStep();
		}

		return openBank();
	}

	/** Coins, so a sale's takings are not banked out from under the desk. */
	private static final int COINS = 10;

	/**
	 * Whether the pack is supposed to be carrying this at all.
	 *
	 * A character that arrives from Tutorial Island brings a sword, a shield,
	 * a net and a handful of runes, and nothing here ever told it to put them
	 * down. That is eight occupied slots, which is eight fewer ore -- and
	 * Giles trades twenty-five at a time, so a pack holding the starter kit
	 * can never assemble an exchange. It asks the bank for the shortfall, is
	 * told there is no room, changes nothing, and asks again. The run that
	 * turned this up had done it fifty-one thousand times.
	 *
	 * Listing what stays is safer than listing what goes: a drop, a bounty
	 * reward or a quest item nobody thought about is junk by default, and the
	 * worst that does is put something in the bank.
	 */
	private boolean keepInPack( int id )
	{
		if ( id == SLEEPING_BAG || id == GOODS || id == COINS || id == COAL )
		{
			return true;
		}

		for ( int i = 0; i < PICKAXES.length; i++ )
		{
			if ( PICKAXES[i] == id )
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Bank one thing the pack should not be holding. True if it did.
	 *
	 * One per call, because the bank screen is a click at a time and an
	 * unstacked item deposits as its own slot -- the stack size is read off
	 * the slot rather than assumed, so five mind-runes go in one click and a
	 * sword goes in one click.
	 */
	private boolean depositJunk()
	{
		for ( int slot = 0; slot < getInventoryCount(); slot++ )
		{
			int id = getInventoryId( slot );

			if ( id != -1 && ! keepInPack( id ) )
			{
				note( "Banking " + getItemNameId( id ) + " - the pack is for "
					+ "tools and stock." );
				deposit( id, getInventoryStack( slot ) );
				return true;
			}
		}

		return false;
	}

	/** At the Draynor counter: coal out, gems and spare coal in. */
	private int certBankStep()
	{
		for ( int i = 0; i < ORES_TO_BANK.length; i++ )
		{
			if ( ORES_TO_BANK[i] == COAL )
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

		if ( depositJunk() )
		{
			return random( 1000, 1500 );
		}

		int carried = getInventoryCount( COAL );
		int room    = PACK_SLOTS - getInventoryCount();

		if ( carried < PER_EXCHANGE && bankCount( COAL ) >= PER_EXCHANGE - carried )
		{
			/*
			 * Ask for no more than there is somewhere to put. A withdraw that
			 * does not fit is refused whole -- the server says "you don't have
			 * room for that" and nothing moves -- and since nothing moves, the
			 * same step runs again on the next tick with the same numbers. That
			 * is the shape of every one of these loops: a failure that changes
			 * no state is a failure that repeats forever.
			 */
			if ( room < PER_EXCHANGE - carried )
			{
				/* What is in hand is sitting in the slots the withdraw needs.
				   Putting it back costs one click and the next pass takes the
				   whole twenty-five in a single go. */
				if ( carried > 0 )
				{
					deposit( COAL, carried );
					return random( 1000, 1500 );
				}

				note( "An empty pack still has no room for an exchange "
					+ "- ending the cert run rather than retrying." );
				certRun = false;
				homeRun = true;
				closeBank();
				return random( 1000, 1500 );
			}

			withdraw( COAL, PER_EXCHANGE - carried );
			return random( 1000, 1500 );
		}

		if ( carried < PER_EXCHANGE )
		{
			/* Less than one exchange left in the bank: put back what is in
			   hand and go home. The remainder waits for the next batch. */
			if ( carried > 0 )
			{
				deposit( COAL, carried );
				return random( 1000, 1500 );
			}

			note( "Cert run done - " + stock() + " " + goodsPlural()
				+ " in the pack." );
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
	 * here with his menu still up and no coal left to sell. Clicking the
	 * first option on the way past answers "I have some certificates to trade
	 * in", and the two pickers behind it take their defaults as well -- which
	 * hands a certificate straight back for five coal, once every cycle. A
	 * refusal has to be chosen deliberately, never taken as whatever happens
	 * to be first.
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
		   an RSC menu, and a far better guess than the first. */
		answer( options.length - 1 );
		return random( 1200, 1800 );
	}

	/**
	 * Giles's menu, answered by what the options say rather than by counting
	 * clicks: three pickers in a row, and a miscount would sell the wrong ore
	 * or trade in certificates instead of ore.
	 *
	 * His coal option is spelled with a capital C where the others are
	 * lowercase, which is Jagex's own inconsistency and not worth correcting
	 * -- but it is exactly why this matches on lowercased text rather than on
	 * the label as shipped.
	 */
	private int exchangeStep()
	{
		if ( isQuestMenu() )
		{
			String[] options = questMenuOptions();

			for ( int i = 0; i < options.length; i++ )
			{
				String o = options[i] == null
					? "" : options[i].toLowerCase( Locale.ENGLISH );

				if ( o.indexOf( "ore to trade in" ) != -1 || o.equals( "coal" )
					|| o.equals( "twentyfive" ) )
				{
					if ( o.equals( "twentyfive" ) )
					{
						exchanges++;
						note( "Exchange " + exchanges + ": "
							+ PER_EXCHANGE + " coal to Giles." );
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

	/**
	 * Underground.
	 *
	 * Classic stacks its floors 944 tiles apart in y, so anything past three
	 * thousand is below ground and nothing on the surface can be mistaken for
	 * it. That is a far safer test here than the guild's own bounds, because
	 * a death drops the player somewhere else underground entirely and the
	 * script needs to know that much before it knows anything else.
	 */
	private boolean inGuild()
	{
		return getY() > 3000;
	}

	/** The entrance shed: twenty-nine tiles, one door, one ladder. */
	private boolean inShed()
	{
		return ( getX() >= 272 && getX() <= 277
			&& getY() >= 563 && getY() <= 567 );
	}

	/** Standing on the tile outside the shed door, ready to click it. */
	private boolean atDoor()
	{
		return distanceTo( OUT_X, OUT_Y ) <= 1;
	}

	private boolean atBank()
	{
		return ( getX() >= 280 && getX() <= 287
			&& getY() >= 564 && getY() <= 575 );
	}

	private boolean inDraynor()
	{
		return ( getX() >= 212 && getX() <= 236
			&& getY() >= 624 && getY() <= 644 );
	}

	/** How far off a sampled route still counts as being on it. */
	private static final int ROUTE_SLACK = 8;

	/** When the bot first found itself off every known route, and when it is
	    next allowed to complain about it. Both in wall-clock millis; both
	    survive nothing, which is fine -- a relogin that lands it somewhere it
	    recognises is exactly the case where the clock should start over. */
	private long lostSince;
	private long lostQuiet;

	/** Distance to the nearest waypoint of a route. */
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
			/* "I can't reach that" and friends: step off the spot rather than
			   swing at the same unreachable rock for ever. */
			moveTime = System.currentTimeMillis() + random( 1500, 1800 );
		}
	}
}
