import org.rscdaemon.client.*;
import java.util.Locale;

/**
 * PureFeathers - the crew's feather supply, and a melee pure built out of the
 * same chickens.
 *
 * The counter is not here. Announcing, answering, trading and refusing
 * politely are the same machine for every one of the crew and they live in
 * MerchantScript; what is left in this file is the half that differs.
 *
 * The work is the farm south of Falador: five chickens in a pen, three
 * feathers guaranteed off every one of them, and a bank thirty tiles up the
 * road for the sleeping bag. Feathers stack, so there is no certing leg and no
 * bank run to make stock sellable -- the whole pile rides in one slot and the
 * pen is the shop counter.
 *
 * Two things about the fighting are deliberate.
 *
 * The character is a pure, so defence must never move. Aggressive and accurate
 * are the only two modes it ever sets: controlled spreads experience across
 * attack, strength AND defence, and defensive sends all of it to defence, so
 * either one would quietly ruin the character a few hundred chickens at a
 * time. The ladder alternates between the two every five levels -- attack to
 * 5, strength to 5, attack to 10, strength to 10 -- which keeps the two stats
 * level with each other all the way to 99 instead of finishing one and then
 * grinding the other from scratch.
 *
 * And it will not win a fight it should not be in. A level-3 character with a
 * bronze sword can be killed by a chicken, and dying here costs the feathers,
 * the sword and the bag in one go. So it eats at two thirds and backs out of
 * the pen entirely below a third, which is slower than fighting through it and
 * is the only version of this that survives unattended for months.
 *
 * Setup: a sleeping bag in the pack or in the Falador bank, a bronze sword and
 * shield wielded, and some food for the first few levels. Everything after
 * that it provides for itself.
 */
public final class PureFeathers extends MerchantScript
{
	/* ------------------------------------------------------------- goods -- */

	/** Feather. Every chicken drops three, every time. */
	private static final int GOODS = 381;

	/**
	 * Coins per feather. The fishing shops sell them at two, which is the
	 * number to beat, and they hold two hundred at a time and put one back
	 * every five seconds -- so anybody fletching in bulk is not buying them
	 * there. Three is the premium for having thousands in one place.
	 */
	private static final int PRICE = 3;


	public PureFeathers( Extension ex )
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
		return "feathers";
	}

	@Override
	protected String tag()
	{
		return "PureFeathers";
	}

	@Override
	protected String outOfStock()
	{
		return "Sorry m8, I'm out of feathers - back once I've plucked some more";
	}

	@Override
	protected boolean interested( String said )
	{
		return super.interested( said )
			|| said.indexOf( "feather" ) != -1
			|| said.indexOf( "arrow" ) != -1
			|| said.indexOf( "fletch" ) != -1;
	}

	/*
	 * The pen is the counter.
	 *
	 * MerchantScript learns where its counter is from the last bank window it
	 * had open, which is right for a bot that banks every load and wrong for
	 * this one: it banks almost never, so it would stand in a field full of
	 * customers and say nothing for weeks. Where it works is where it sells.
	 */
	@Override
	protected boolean atCounter()
	{
		return insidePen();
	}

	@Override
	public void init( String params )
	{
		moveTime = -1L;
		swings   = 0;
		note( "Desk open. Selling feathers at " + PRICE + "gp."
			+ " Attack " + getLevel( ATTACK ) + ", strength " + getLevel( STRENGTH )
			+ ", defence " + getLevel( DEFENSE ) + "." );
	}

	/* ----------------------------------------------------------- the pure -- */

	private static final int ATTACK   = 0;
	private static final int DEFENSE  = 1;
	private static final int STRENGTH = 2;
	private static final int HITS     = 3;

	/** Fight modes, as setFightMode() numbers them. */
	private static final int AGGRESSIVE = 1;
	private static final int ACCURATE   = 2;

	/** Levels won in one mode before swapping to the other. */
	private static final int BAND = 5;

	/** Where both stats stop. Defence stays where it started: 1. */
	private static final int CAP = 99;

	/**
	 * The mode this level pair calls for.
	 *
	 * The band is the next multiple of five above whichever stat is behind.
	 * Attack climbs to it first and strength follows, so the two stay within
	 * five of each other the whole way up and a half-finished character is
	 * still a usable one.
	 */
	private int wantedMode()
	{
		int attack   = getLevel( ATTACK );
		int strength = getLevel( STRENGTH );
		int band     = Math.min( CAP, ( Math.min( attack, strength ) / BAND + 1 ) * BAND );

		if ( attack < band )
		{
			return ACCURATE;
		}

		if ( strength < band )
		{
			return AGGRESSIVE;
		}

		/* Both at the cap. Nothing left to train, and no reason to keep
		   flipping the mode a customer can see on every kill. */
		return ACCURATE;
	}

	/** True once there is nothing left for a chicken to teach. */
	private boolean trained()
	{
		return getLevel( ATTACK ) >= CAP && getLevel( STRENGTH ) >= CAP;
	}

	/* -------------------------------------------------------------- work -- */

	private static final int CHICKEN = 3;

	/** Used if one is in the pack; the bed is what this walks to. */
	private static final int SLEEPING_BAG = 1263;

	/*
	 * The pen south of Falador. The chickens' own roam box is 262-278 by
	 * 596-612; this is the same ground with a tile of slack, and it is both
	 * "am I at work" and "am I standing in my shop".
	 */
	private static final int PEN_MIN_X = 261;
	private static final int PEN_MAX_X = 279;
	private static final int PEN_MIN_Y = 595;
	private static final int PEN_MAX_Y = 613;

	/** Where it stands when there is nothing to hit: the middle of the pen. */
	private static final int HOME_X = 271;
	private static final int HOME_Y = 604;

	/**
	 * Where it goes to heal: the west end of the pen, by the well.
	 *
	 * Inside the fence, not out on the road -- which is the opposite of the
	 * obvious answer and the reason this constant has a comment. There is a
	 * Highwayman at (292,608), aggressive, and he is perfectly capable of
	 * killing a level-3 pure standing on the road with three hits left. The
	 * chickens, for all that they are what hurt it, attack nothing: they are
	 * the safest company on the farm. What stops it fighting while it heals is
	 * the resting flag, not the distance.
	 */
	private static final int REST_X = 265;
	private static final int REST_Y = 608;

	/**
	 * Falador west bank to the pen, bank end first. Waypoints sampled off a
	 * BFS of the server's own collision grid, five tiles apart, so every hop
	 * is genuinely walkable rather than a straight line through a fence.
	 */
	private static final int[][] PATH = {
		{ 284, 573 }, { 287, 573 }, { 287, 578 }, { 290, 580 }, { 290, 585 },
		{ 290, 590 }, { 290, 595 }, { 290, 600 }, { 289, 604 }, { 284, 604 },
		{ 279, 604 }, { 274, 604 }, { 271, 604 }
	};

	/**
	 * Lumbridge respawn to the pen.
	 *
	 * It will die here sooner or later -- the Highwayman east of the farm sees
	 * to that, and a pure with ten hits has no answer to him -- and death puts
	 * it back at (122,647) with everything but its three best items gone. Two
	 * hundred tiles of road, sampled the same way as the short route, is the
	 * difference between a bot that picks itself up and a bot that stands in
	 * Lumbridge until somebody notices.
	 */
	private static final int[][] HOME = {
		{ 122, 647 }, { 122, 643 }, { 124, 641 }, { 125, 638 }, { 129, 638 },
		{ 131, 636 }, { 133, 634 }, { 137, 634 }, { 141, 634 }, { 145, 634 },
		{ 149, 634 }, { 153, 634 }, { 157, 634 }, { 157, 630 }, { 157, 626 },
		{ 159, 624 }, { 162, 623 }, { 166, 623 }, { 170, 623 }, { 173, 622 },
		{ 177, 622 }, { 181, 622 }, { 185, 622 }, { 189, 622 }, { 193, 622 },
		{ 197, 622 }, { 201, 622 }, { 205, 622 }, { 209, 622 }, { 211, 620 },
		{ 212, 617 }, { 214, 615 }, { 217, 614 }, { 217, 610 }, { 220, 609 },
		{ 224, 609 }, { 228, 609 }, { 232, 609 }, { 236, 609 }, { 240, 609 },
		{ 244, 609 }, { 247, 608 }, { 251, 608 }, { 255, 608 }, { 259, 608 },
		{ 263, 608 }, { 267, 608 }, { 271, 608 }, { 274, 607 }, { 273, 604 },
		{ 271, 604 }
	};

	/** Slack around a waypoint before the bot counts itself lost. */
	private static final int ROUTE_SLACK = 8;

	/**
	 * Eat at this share of full health, leave the pen below the second.
	 *
	 * Shares rather than hit counts, because this character starts with ten
	 * hits and finishes with far more, and a threshold written in hits would
	 * be panic at level 3 and suicide at level 70.
	 */
	private static final int EAT_AT     = 60;
	/*
	 * Percentages, and on a character with eleven hits a percentage is a blunt
	 * instrument: the old thirty meant "retreat with three hits left", which
	 * is one unlucky chicken short of a grave. Forty-five is five hits, which
	 * is enough to walk away on.
	 */
	private static final int RETREAT_AT = 45;

	/** Food the pack might be carrying, best last so it eats the cheap first. */
	private static final int[] FOOD = {
		132, 325, 327, 329, 333, 335, 337, 339, 350, 352, 354, 546
	};

	/** Queued jiggle after an "I can't reach that" style message. */
	private long moveTime = -1L;

	/** Said the "fatigued, no bag" line already this spell of tiredness. */
	private boolean tiredNoted;

	/** Earliest the bed may be clicked again. */
	private long counterTime;

	/** Bed clicks this session, and whether they have been given up on. */
	private int bedTries;
	private boolean bedNoGood;

	/** What findRest() returns when there is nothing it can do. */
	private static final int CARRY_ON = -1;

	/**
	 * Chickens swung at this session, for the log. Not kills: nothing the
	 * server sends says a chicken died, so counting attacks is the honest
	 * number and the feather count beside it is the one that matters anyway.
	 */
	private int swings;

	/** Set while backing out of the pen to heal, cleared once healthy. */
	private boolean resting;

	/**
	 * What it does for a living, between customers.
	 *
	 * Everything is decided from where it is standing, what it is carrying and
	 * what its stats say, so a relogin in the middle of anything picks up
	 * where it left off. Nothing here is remembered across a restart because
	 * nothing here needs to be.
	 */
	@Override
	protected int work()
	{
		if ( moveTime != -1L && System.currentTimeMillis() >= moveTime )
		{
			moveTime = -1L;
			step( getX() + random( -1, 1 ), getY() + random( -1, 1 ) );
			return random( 1000, 1500 );
		}

		/*
		 * Open windows first, always.
		 *
		 * The first version of this put the fatigue check above these and
		 * spent a night at the Falador bank saying "At the bank for a
		 * sleeping bag" twenty-one thousand times: the errand walked it to
		 * the counter, and then the fatigue branch returned before anything
		 * could ever read the open bank. A branch that reaches a window has
		 * to be below the code that handles the window, not above it.
		 */
		if ( isBanking() )
		{
			return bankStep();
		}

		/* Nothing out here buys or sells through a shop, so an open shop
		   window is a misclick. Back out before it buys something. */
		if ( isShopOpen() )
		{
			closeShop();
			return random( 1000, 1500 );
		}

		if ( isQuestMenu() )
		{
			return leaveMenu();
		}

		if ( getFatigue() > 90 )
		{
			if ( getInventoryCount( SLEEPING_BAG ) > 0 )
			{
				if ( ! isSleeping() )
				{
					useSleepingBag();
				}

				return random( 1000, 2000 );
			}

			int errand = findRest();

			if ( errand != CARRY_ON )
			{
				return errand;
			}

			/*
			 * Nowhere to sleep and nowhere to walk to. It keeps plucking
			 * anyway: fatigue stops experience, it does not stop drops, and a
			 * desk with feathers and no levels is worth more than a bot asleep
			 * at a bank.
			 */
			if ( ! tiredNoted )
			{
				tiredNoted = true;
				note( "Fatigued with nowhere to rest from " + getX() + "," + getY()
					+ " - still collecting, but not levelling." );
			}
		}
		else
		{
			tiredNoted = false;
		}

		int mode = wantedMode();

		if ( getFightMode() != mode )
		{
			/*
			 * Sent whether or not it is mid-fight. StyleHandler takes the
			 * change unconditionally -- there is no "not while fighting"
			 * check on it anywhere in the server -- and waiting for the
			 * fight to end would mean waiting on inCombat(), which lingers
			 * for seconds after the last hit. Gate this on that and the
			 * ladder can miss its rung entirely on a busy pen, paying every
			 * kill into the stat it has just finished training.
			 */
			setFightMode( mode );
			note( "Fighting " + ( mode == ACCURATE ? "accurate" : "aggressive" )
				+ " - attack " + getLevel( ATTACK )
				+ ", strength " + getLevel( STRENGTH )
				+ ", defence " + getLevel( DEFENSE ) + "." );
			return random( 600, 900 );
		}

		int health = getCurrentLevel( HITS ) * 100 / Math.max( 1, getLevel( HITS ) );

		if ( health <= EAT_AT && eatSomething() )
		{
			return random( 1200, 1800 );
		}

		if ( health <= RETREAT_AT )
		{
			if ( ! resting )
			{
				note( "Hurt at " + getCurrentLevel( HITS ) + "/" + getLevel( HITS )
					+ " - out of the pen until that comes back." );
			}

			resting = true;

			/*
			 * Breaking off a fight is its own move, and the first version did
			 * not make it: a walk to the rest tile while still in combat is
			 * refused outright for the first three rounds ("You cannot retreat
			 * in the first 3 rounds of battle"), and after that a walk to a
			 * tile the fight is dragging it away from does nothing either. The
			 * retreat the game actually accepts is a click on the tile the
			 * player is already standing on. So: flee first, walk second.
			 *
			 * This is the difference between a pure with eleven hits living on
			 * a bad roll and dying on one -- three of these characters have
			 * already been buried at this farm.
			 */
			if ( inCombat() )
			{
				step( getX(), getY() );
				return random( 1200, 1800 );
			}

			/*
			 * Out of the pen, not just off the chicken: a chicken left in
			 * combat follows, and standing next to one to heal is how a pure
			 * with eleven hits dies to poultry.
			 */
			step( REST_X, REST_Y );
			return random( 3000, 4000 );
		}

		if ( resting )
		{
			if ( health < 90 )
			{
				return random( 4000, 6000 );
			}

			resting = false;
			note( "Healed up - back to work." );
		}

		if ( ! insidePen() )
		{
			return goToWork();
		}

		if ( inCombat() )
		{
			return random( 900, 1400 );
		}

		int[] feather = getItemById( GOODS );

		if ( feather[0] != -1 && insidePen( feather[1], feather[2] ) )
		{
			pickupItem( feather[0], feather[1], feather[2] );
			return random( 700, 1000 );
		}

		int[] chicken = getNpcById( CHICKEN );

		if ( chicken[0] != -1 )
		{
			if ( trained() && getInventoryCount() >= 29 )
			{
				/* Nothing left to train and nowhere to put the drops. The
				   pack never actually fills -- feathers stack -- so this only
				   happens if something else is being carried. */
				return random( 4000, 6000 );
			}

			attackNpc( chicken[0] );
			swings++;

			if ( swings % 25 == 0 )
			{
				note( swings + " attacks, " + getInventoryCount( GOODS )
					+ " feathers, attack " + getLevel( ATTACK )
					+ " strength " + getLevel( STRENGTH )
					+ " defence " + getLevel( DEFENSE ) + "." );
			}

			return random( 1200, 1800 );
		}

		/* Pen empty: they come back in half a minute. Standing still beats
		   wandering off after the last one. */
		if ( distanceTo( HOME_X, HOME_Y ) > 6 )
		{
			step( HOME_X, HOME_Y );
		}

		return random( 2000, 3000 );
	}

	/** Eat the first food in the pack. False when there is none. */
	private boolean eatSomething()
	{
		for ( int i = 0; i < FOOD.length; i++ )
		{
			int slot = getInventoryIndex( FOOD[i] );

			if ( slot != -1 )
			{
				useItem( slot );
				return true;
			}
		}

		return false;
	}

	/* ------------------------------------------------------------ walking -- */

	private boolean insidePen()
	{
		return insidePen( getX(), getY() );
	}

	private boolean insidePen( int x, int y )
	{
		return x >= PEN_MIN_X && x <= PEN_MAX_X && y >= PEN_MIN_Y && y <= PEN_MAX_Y;
	}

	/**
	 * Back to the pen from wherever it woke up.
	 *
	 * Anywhere on the route walks the route; anywhere else does nothing at
	 * all. A bot that walks blind out of a strange place -- a death spawn in
	 * Falador, a hand-placed test -- arrives somewhere stranger, and the one
	 * thing worse than a stopped bot is a lost one carrying stock.
	 */
	private int goToWork()
	{
		if ( distanceToRoute( PATH ) <= ROUTE_SLACK )
		{
			lostSince = 0L;
			return walkRoute( PATH, true );
		}

		if ( distanceToRoute( BED ) <= ROUTE_SLACK )
		{
			lostSince = 0L;
			return walkRoute( BED, false );
		}

		if ( distanceToRoute( HOME ) <= ROUTE_SLACK )
		{
			lostSince = 0L;
			return walkRoute( HOME, true );
		}

		if ( distanceToRoute( STRAY ) <= ROUTE_SLACK )
		{
			note( "Off the routes at " + getX() + "," + getY()
				+ " but on the Rimmington road - walking home." );
			lostSince = 0L;
			return walkRoute( STRAY, false );
		}

		if ( distanceToRoute( FIELD ) <= ROUTE_SLACK )
		{
			note( "Off the routes at " + getX() + "," + getY()
				+ " but in the field north of the bed road - walking home." );
			lostSince = 0L;
			return walkRoute( FIELD, false );
		}

		/* One line a minute, not one every five seconds. Last time this
		   dead-ended it printed the same sentence four thousand seven hundred
		   times and nobody saw it, because a message that repeats forever
		   reads exactly like a message that is not there. */
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
				note( "STUCK: " + mins + " min at " + getX() + "," + getY()
					+ " with no route home - needs a human." );
			}

			lostQuiet = now + 60000L;
		}

		return random( 5000, 8000 );
	}

	/** When the bot first lost every route, and when it may complain again. */
	private long lostSince;
	private long lostQuiet;

	/** The short route. Forward is toward the pen, since the pen is the end. */
	private int walkPath( boolean towardPen )
	{
		return walkRoute( PATH, towardPen );
	}

	/**
	 * One hop along a route.
	 *
	 * Both routes end at the pen, so forward always means "to work" and
	 * backward means "to the bank" -- which only the short one ever does.
	 * Stepping to the nearest waypoint first, and only then along, is what
	 * lets it join either route from the middle after a relogin.
	 */
	/**
	 * One step, with the client's own pathfinder held at arm's length.
	 *
	 * EngineHandle.getStepCount works in sector-local coordinates and indexes
	 * a 96x96 array with them unchecked, so for the tick or two after a death
	 * or a teleport -- when the new area has been set but the player's local
	 * position has not caught up -- it throws
	 * ArrayIndexOutOfBoundsException and takes the whole script down with it.
	 * Seen for real at a Lumbridge respawn: "Index -70 out of bounds for
	 * length 96". Nothing a script can read tells it the sector is mid-swap,
	 * so the only honest guard is to let the step fail and try again next
	 * tick. Fixing getStepCount itself would mean rebuilding and
	 * redeploying the client jar.
	 */
	private void step( int x, int y )
	{
		try
		{
			walkTo( x, y );
		}
		catch ( RuntimeException broken )
		{
			/* Sector mid-swap. Nothing to do but come back in a moment. */
		}
	}

	private int walkRoute( int[][] route, boolean forward )
	{
		int nearest     = 0;
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

		step( route[next][0], route[next][1] );
		return random( 1000, 1500 );
	}

	/** Tiles to the nearest waypoint of a route. */
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

	/* --------------------------------------------------------- the errand -- */

	/**
	 * Get the fatigue off, or admit there is nowhere to do it.
	 *
	 * A sleeping bag is the obvious way and the worst one available here: this
	 * character starts with nothing, dies to the Highwayman now and then, and
	 * every general store wants about forty coins for a bag it cannot earn
	 * until a customer turns up. The bed in the house west of the farm costs
	 * nothing, sets fatigue straight to zero (ObjectAction's "rest"), and sits
	 * on a road the Highwayman is not on. So the bed is the plan, and the bag
	 * is only used when one happens to be in the pack -- see the caller.
	 *
	 * Returns CARRY_ON when it is somewhere this does not know how to leave,
	 * which is not a failure: see the caller for that too.
	 */
	private int findRest()
	{
		if ( bedNoGood )
		{
			return CARRY_ON;
		}

		if ( getX() == STAND_X && getY() == STAND_Y )
		{
			if ( System.currentTimeMillis() < counterTime )
			{
				return random( 1500, 2500 );
			}

			/*
			 * The server marks the player busy and clears fatigue a moment
			 * later, so there is nothing to poll -- just don't re-click while
			 * the first click is still resolving.
			 *
			 * The three-strike guard below counts CONSECUTIVE FAILURES, and
			 * the distinction is the whole point. It used to count tries:
			 * ++bedTries after every click, latch at three, done forever.
			 * That was written when the click did nothing at all -- the walk
			 * arrived on the right tile, the server said nothing, fatigue
			 * stayed at 100 -- and as a guard against standing in a bedroom
			 * clicking furniture it was right. Then the approach was fixed
			 * and the bed started working, and the same counter promptly shot
			 * the bot in the foot: three successful rests tripped the latch
			 * and levelling stopped again, for the opposite reason.
			 *
			 * onServerMessage clears the count on "you rest on the bed", so
			 * only a real run of silent clicks can switch the errand off.
			 */
			note( "Resting at the bed." );
			counterTime = System.currentTimeMillis() + 8000L;
			atObject( BED_X, BED_Y );

			if ( ++bedTries >= 3 )
			{
				bedNoGood = true;
				note( "The bed is not taking - back to the pen, no levels until"
					+ " there is a sleeping bag." );
			}

			return random( 4000, 5000 );
		}

		/* On the door tile and still outside: click the door and step
		   through. A door in Classic is opened by clicking the wall it is in
		   and the click walks the player through, but only from the side they
		   are standing on, so the tile to head for afterwards is the far one. */
		if ( getX() == HOUSE_DOOR_X && getY() == HOUSE_DOOR_Y )
		{
			atWallObject( HOUSE_DOOR_X, HOUSE_DOOR_Y );
			walkTo( STAND_X, STAND_Y );
			return random( 1500, 2200 );
		}

		/* Inside the room but not on the tile the bed can be reached from. */
		if ( getY() <= 612 && getY() >= 606 && getX() >= 212 && getX() <= 216 )
		{
			walkTo( STAND_X, STAND_Y );
			return random( 1000, 1500 );
		}

		if ( distanceToRoute( BED ) <= ROUTE_SLACK )
		{
			lostSince = 0L;
			return walkRoute( BED, true );
		}

		return CARRY_ON;
	}

	/*
	 * The bed, the door into its room, and the tile the click is made from.
	 *
	 * This used to say the only standable tile was 213,608, to the south, and
	 * that is the bug that cost this character every level it has not got.
	 * 213,608 is OUTSIDE THE HOUSE. The landscape carries a south-edge wall
	 * bit on 213,608 and 214,608 with the matching north-edge bit on the bed
	 * row, and nextTo() in ObjectAction is a reachability test, not an
	 * adjacency one -- it traces a line to the object, hits the bedroom wall,
	 * and returns without sending so much as "nothing interesting happens".
	 * Hence the symptom nobody could place: the walk arrives on the right
	 * tile, the server says nothing at all, and fatigue stays at 100.
	 *
	 * The earlier note reasoned from route.py calling 212,609, 214,609 and
	 * 213,610 solid -- but two of those three are the bed's own footprint, so
	 * of course they are solid, and that told us nothing about which side of
	 * the wall to stand on. The test that answers it is the landscape edge
	 * bits with the object overlay taken off: 215,610 and 214,610 both read
	 * 0x00, no wall between them, so a click from 215,610 reaches the bed.
	 */
	private static final int BED_X = 213, BED_Y = 609;

	/** The bedroom door, and the tile inside it the bed is clicked from. */
	private static final int HOUSE_DOOR_X = 215, HOUSE_DOOR_Y = 613;
	private static final int STAND_X = 215, STAND_Y = 610;

	/*
	 * The pen to that house door, west along the farm road, door tile last.
	 * Sampled off the collision grid like the others; it ends at 215,613
	 * because the bed is indoors and the walk has to finish at a doorway
	 * rather than at a wall.
	 *
	 * While correcting this: the old note claimed the nearer bed at 298,579
	 * was "walled off from every side". It is not -- it has four open
	 * approach tiles of its own. It is also behind a door, so it is a fair
	 * alternative rather than a dead end; it just saves nothing once the walk
	 * has to find a doorway either way.
	 */
	private static final int[][] BED = {
		{ 271, 604 }, { 274, 605 }, { 274, 609 }, { 271, 610 }, { 267, 610 },
		{ 263, 610 }, { 259, 610 }, { 255, 610 }, { 251, 610 }, { 247, 610 },
		{ 244, 611 }, { 240, 611 }, { 236, 611 }, { 232, 611 }, { 228, 611 },
		{ 224, 611 }, { 220, 611 }, { 218, 613 }, { 216, 615 }, { 215, 613 }
	};

	/*
	 * Rimmington, and the hundred and six walkable steps back to the pen.
	 *
	 * What put the bot at 321,659 is not established -- nothing in this
	 * script walks east of the farm, it had not died (deaths=0 in the player
	 * row), and the log has no position line between the pen and Rimmington.
	 * What is certain is that it stood there through twenty-five idle logouts
	 * with a clear road home, because the nearest route it recognised was
	 * fifty tiles away. Displacement happens. Refusing to walk back from a
	 * place we can name is a choice, and this is it being unmade.
	 */
	private static final int[][] STRAY = {
		{ 271, 604 }, { 276, 604 }, { 281, 604 }, { 286, 604 }, { 291, 604 },
		{ 296, 604 }, { 301, 604 }, { 306, 604 }, { 311, 604 }, { 316, 604 },
		{ 320, 605 }, { 320, 610 }, { 320, 615 }, { 320, 620 }, { 320, 625 },
		{ 320, 630 }, { 320, 635 }, { 320, 640 }, { 321, 644 }, { 321, 649 },
		{ 321, 654 }, { 321, 659 }
	};

	/*
	 * The field north of the bed road, and the straight column down out of
	 * it. Found at 239,600 after a rest, then 241,598 and 245,594 across two
	 * relogins, stood there eleven hours: the bed road was seventeen tiles
	 * south, clear ground the whole way, and the nearest route it knew was
	 * nine tiles too far. The displacement itself is still unexplained --
	 * nothing in this script walks north of the road. The slack stays at
	 * eight; a place the bot has been found in gets a route of its own.
	 */
	private static final int[][] FIELD = {
		{ 240, 611 }, { 245, 611 }, { 245, 606 }, { 245, 600 }, { 245, 594 },
		{ 245, 588 }
	};

	/* ---------------------------------------------------------- the bank -- */

	/**
	 * At the counter, which nothing here sets out to do -- the bed replaced
	 * the sleeping-bag errand. This is the way back out if a bank window ever
	 * does open: take anything useful that is in there and walk to work.
	 *
	 * Feathers are never banked: stock() counts what is carried, so a feather
	 * in here is a feather the desk will swear it does not have.
	 */
	private int bankStep()
	{
		if ( getInventoryCount( SLEEPING_BAG ) < 1 && bankCount( SLEEPING_BAG ) > 0 )
		{
			withdraw( SLEEPING_BAG, 1 );
			return random( 1000, 1500 );
		}

		if ( bankCount( GOODS ) > 0 )
		{
			note( "Reclaiming " + bankCount( GOODS )
				+ " banked feathers -- stock lives in the pack." );
			withdraw( GOODS, bankCount( GOODS ) );
			return random( 1000, 1500 );
		}

		return walkPath( true );
	}

	/**
	 * Back out of whatever menu is open without agreeing to anything. Nothing
	 * out here asks a question worth answering, and the first option is never
	 * a safe guess.
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

		answer( options.length - 1 );
		return random( 1200, 1800 );
	}

	@Override
	public void onServerMessage( String message )
	{
		note( "server: " + message );

		String lower = message.toLowerCase( Locale.ENGLISH );

		if ( lower.indexOf( "standing here" ) != -1 )
		{
			/* "I can't reach that" and friends: step off the spot rather than
			   swing at the same unreachable chicken for ever. */
			moveTime = System.currentTimeMillis() + random( 1500, 1800 );
		}

		/* The bed answered, so the run of failures is over. Without this the
		   failure count is really a use count and three good nights' sleep
		   retire the bed permanently -- see findRest(). */
		if ( lower.indexOf( "rest on the bed" ) != -1
			|| lower.indexOf( "feeling refreshed" ) != -1 )
		{
			bedTries = 0;
			bedNoGood = false;
		}
	}
}
