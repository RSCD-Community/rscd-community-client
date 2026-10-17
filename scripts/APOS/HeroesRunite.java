import java.util.Locale;

/**
 * HeroesRunite - mines the Heroes' Guild runite rock and banks the ore.
 *
 * The guild basement holds the only runite rock on the map that is not in
 * the wilderness, which is why this run exists at all. It is also a single
 * rock on a fifteen-minute respawn, so the ore rate is low by any other
 * mine's standard: the time goes on the travel and the waiting, not on the
 * swinging. Nothing here tries to hide that.
 *
 * The kit is three items and no more - a pickaxe, a sleeping bag, and a
 * dragonstone amulet. Everything else gets banked on sight, because a full
 * inventory is the signal to go home and every extra item carried is one
 * fewer ore per trip.
 *
 * The amulet decides the route home. Charged, it teleports to Edgeville and
 * the walk back is the long road past the members' gate. Uncharged, it gets
 * dipped in the fountain of heroes on the way in and is charged by the time
 * the load is full. Absent entirely - none held, none in the bank - the run
 * still works, it just walks both ways.
 *
 * The walk out depends on Fishing Contest. With the quest done the dwarves
 * allow the White Wolf Mountain tunnel, which puts Catherby bank twenty
 * minutes closer than anything else; without it the bank is Falador west and
 * the road is the long way round through the members' gate.
 *
 * All state is derived from position and inventory. There is no config
 * window and nothing to answer: a restart, a relogin or a mid-trip
 * disconnect picks the run up wherever it finds it.
 */
public final class HeroesRunite extends Script
{
	private static final int MINING       = 14;
	private static final int RUNITE_LEVEL = 85;

	/* Both runite ids, though the guild basement only ever had 210. */
	private static final int[] RUNITE_ROCKS = { 210, 211 };

	private static final int RUNITE_ORE = 409;

	/* Uncut gems a swing occasionally turns up: diamond, ruby, emerald,
	   sapphire. Worth more than the ore and banked the same way. */
	private static final int[] GEMS = { 157, 158, 159, 160 };

	private static final int[] PICKAXES = { 1262, 1261, 1260, 1259, 1258, 156 };

	/* Mining level each pick demands, same order - rune 41 down to bronze. */
	private static final int[] PICKAXE_REQS = { 41, 31, 21, 6, 1, 1 };

	private static final int SLEEPING_BAG   = 1263;
	private static final int AMULET_CHARGED = 597;
	private static final int AMULET_PLAIN   = 522;

	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };

	/* Jagex's quest order, which is what isQuestComplete indexes on. */
	private static final int HEROES_QUEST    = 19;
	private static final int FISHING_CONTEST = 25;

	/*
	 * ---------------------------------------------------------------- routes
	 *
	 * Every waypoint below was plotted against the server's own collision
	 * data rather than walked by eye, and index 0 of each leg is always the
	 * bank end. Doors and gates are deliberately NOT walked through: each
	 * leg stops on the tile beside its obstacle and the leg records the
	 * object to click, because that click is the only thing that crosses.
	 */

	/* Catherby bank to the mountain tunnel's Catherby mouth. */
	private static final int[][] CATH_LEG = {
		{440,493},{439,496},{436,497},{432,497},{431,494},{431,490},
		{431,486},{431,482},{429,480},{429,476},{429,472},{429,468},
		{429,464},{429,460},{429,456},{426,457}
	};

	/* Through the mountain, Catherby end to Taverley end. */
	private static final int[][] TUNNEL_LEG = {
		{426,3293},{423,3293},{421,3295},{417,3295},{413,3295},{409,3295},
		{406,3294},{402,3294},{398,3294},{394,3294},{390,3294},{387,3295},
		{387,3299},{385,3301}
	};

	/* The dwarves' pen above the tunnel, round to the guild door. */
	private static final int[][] PEN_LEG = {
		{385,465},{385,464},{382,464},{378,464},{374,464},{372,462},
		{372,458},{372,454},{372,450},{372,446},{372,442},{372,441}
	};

	/* Falador west bank out to the members' gate. */
	private static final int[][] FAL_LEG = {
		{331,553},{327,553},{324,552},{324,548},{324,544},{323,541},
		{323,537},{323,533},{323,529},{319,529},{317,527},{317,523},
		{318,520},{322,520},{326,520},{330,520},{334,520},{336,518},
		{337,515},{338,512},{338,508},{338,504},{338,500},{338,496},
		{338,492},{340,490},{341,487}
	};

	/* Edgeville bank out to the members' gate - the long way home. */
	private static final int[][] EDGE_LEG = {
		{217,450},{219,447},{221,450},{221,455},{222,459},{224,462},
		{224,467},{223,471},{221,474},{221,479},{221,484},{221,489},
		{221,494},{221,499},{221,504},{221,509},{221,514},{225,515},
		{230,515},{235,515},{240,515},{245,515},{246,511},{246,506},
		{250,505},{255,505},{260,505},{265,505},{270,505},{275,505},
		{280,505},{285,505},{289,506},{294,506},{299,506},{303,505},
		{306,503},{309,501},{312,499},{315,497},{317,494},{321,493},
		{326,493},{328,490},{330,487},{335,487},{340,487},{341,487}
	};

	/* Members' gate, Taverley side, up the plateau to the guild door. */
	private static final int[][] TAV_LEG = {
		{342,487},{345,487},{349,487},{353,487},{357,487},{361,487},
		{365,487},{369,487},{372,486},{372,482},{372,478},{372,474},
		{372,470},{372,466},{372,462},{372,458},{372,454},{372,450},
		{372,446},{372,442},{372,441}
	};

	/* Inside the guild: the door to the head of the basement stairs. */
	private static final int[][] GUILD_LEG = {
		{372,440},{371,438},{369,437},{368,437}
	};

	/* The basement: foot of the stairs to the runite rock. */
	private static final int[][] MINE_LEG = {
		{368,3273},{368,3276},{368,3279},{366,3280},{364,3279},{361,3279},
		{359,3278},{357,3277},{354,3277}
	};

	/*
	 * Two routes that are not legs, because neither ends at an obstacle.
	 *
	 * The amulet lands north of Edgeville rather than in the bank, and the
	 * fountain sits in the walled courtyard through the guild's inner
	 * archway - a dead end, so the only way on from it is back the way
	 * it came.
	 */
	private static final int[][] EDGE_ARRIVAL = {
		{193,435},{197,435},{199,437},{200,440},{202,442},{204,444},
		{206,446},{209,447},{213,447},{217,447},{217,450}
	};

	private static final int[][] FOUNTAIN_PATH = {
		{372,440},{374,439},{375,437},{378,437},{380,438},{381,438}
	};

	/**
	 * One stretch of walking plus the obstacle at each end of it.
	 *
	 * A leg is the unit the travel code moves in. path[0] is the bank end;
	 * homeX/homeY is the object to click when standing on path[0] and still
	 * heading home, and workX/workY is the object to click when standing on
	 * the last tile and still heading for the rock. Either can be -1 where
	 * the leg simply ends - the bank at one extreme, the rock at the other.
	 *
	 * Clicking a leg's obstacle always lands on the matching end of the
	 * neighbouring leg, which is what lets position alone say where the run
	 * has got to.
	 */
	private static final class Leg
	{
		final int[][] path;
		final int homeX, homeY;
		final boolean homeWall;
		final int workX, workY;
		final boolean workWall;

		Leg( int[][] path, int homeX, int homeY, boolean homeWall,
			int workX, int workY, boolean workWall )
		{
			this.path = path;
			this.homeX = homeX;
			this.homeY = homeY;
			this.homeWall = homeWall;
			this.workX = workX;
			this.workY = workY;
			this.workWall = workWall;
		}
	}

	/* Stairs down into the basement, stairs up out of it. */
	private static final Leg L_GUILD =
		new Leg( GUILD_LEG, 372, 441, true, 368, 438, false );
	private static final Leg L_MINE =
		new Leg( MINE_LEG, 368, 3270, false, -1, -1, false );
	private static final Leg L_TAV =
		new Leg( TAV_LEG, 341, 487, false, 372, 441, true );

	private static final Leg[] ROUTE_CATH = {
		new Leg( CATH_LEG,   -1,  -1, false, 426,  458, false ),
		new Leg( TUNNEL_LEG, 426, 3290, false, 385, 3298, false ),
		new Leg( PEN_LEG,    385, 466, false, 372,  441, true ),
		L_GUILD,
		L_MINE
	};

	private static final Leg[] ROUTE_FAL = {
		new Leg( FAL_LEG, -1, -1, false, 341, 487, false ),
		L_TAV,
		L_GUILD,
		L_MINE
	};

	private static final Leg[] ROUTE_EDGE = {
		new Leg( EDGE_LEG, -1, -1, false, 341, 487, false ),
		L_TAV,
		L_GUILD,
		L_MINE
	};

	private static final int M_EDGE = 0;
	private static final int M_CATH = 1;
	private static final int M_FAL  = 2;

	/*
	 * How far off a waypoint still counts as being on the route. The widest
	 * gap between neighbouring waypoints above is five tiles, so six leaves
	 * room for the one-tile drift a walk ends on without ever letting the
	 * run think it is on a route it has actually fallen off. Widening this
	 * is never the fix for a stuck run - a stuck run needs a route it can
	 * actually walk, and slack only hides where it lost the old one.
	 */
	private static final int ROUTE_SLACK = 6;

	private boolean noAmulet = false;
	private boolean rubbing  = false;

	private int oreCount  = 0;
	private int tripCount = 0;
	private int initialXP = 0;
	private long startTime = -1L;
	private long moveTime  = -1L;

	private int crossX = -1, crossY = -1, crossTries = 0;
	private int walkX = -1, walkY = -1, walkTries = 0;

	private long noteTime  = 0L;
	private long lostSince = -1L;
	private long lostNote  = 0L;

	public HeroesRunite( Extension ex )
	{
		super( ex );
	}

	@Override
	public void init( String params )
	{
		System.out.println( "[HeroesRunite] Heroes' Guild runite. Pickaxe, sleeping bag and a dragonstone amulet - nothing else." );
	}

	@Override
	public int main()
	{
		if ( startTime == -1L )
		{
			startTime = System.currentTimeMillis();
		}

		if ( initialXP == 0 )
		{
			initialXP = getXpForLevel( MINING );
		}

		/*
		 * An empty inventory can only mean the server has not sent it yet -
		 * this run always carries at least the pickaxe and the sleeping bag.
		 * Right after a relogin main() can tick ahead of that packet, and
		 * every check below would read the blank as "nothing in hand".
		 */
		if ( getInventoryCount() == 0 )
		{
			return 1000;
		}

		if ( getLevel( MINING ) < RUNITE_LEVEL )
		{
			System.out.println( "[HeroesRunite] Runite needs mining " + RUNITE_LEVEL
				+ " and this is " + getLevel( MINING ) + " - stopping before the walk rather than after it." );
			setAutoLogin( false );
			stopScript();
			return 1000;
		}

		/*
		 * The guild door is Heroes' Quest's, and it refuses anybody who has
		 * not finished it - which is Jagex's rule, not ours. Finding that
		 * out at the door means a walk from Catherby and back for nothing,
		 * so find it out here instead.
		 */
		if ( ! isQuestComplete( HEROES_QUEST ) )
		{
			System.out.println( "[HeroesRunite] Heroes' Quest is not finished, so the guild door stays shut - stopping." );
			setAutoLogin( false );
			stopScript();
			return 1000;
		}

		if ( isBanking() )
		{
			return doBank();
		}

		if ( isQuestMenu() )
		{
			return doMenu();
		}

		/* Anti-stuck: an "I can't reach that" message queued a jiggle. */
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
				note( "Fatigued with no sleeping bag - still mining, but not levelling." );
			}
			else if ( ! isSleeping() )
			{
				useSleepingBag();
				return random( 1000, 2000 );
			}
			else
			{
				return random( 1000, 2000 );
			}
		}

		boolean full = ( getEmptySlots() == 0 );

		if ( full || ( atBank() && needsBank() ) )
		{
			return goHome( full );
		}

		return goToWork();
	}

	/* ------------------------------------------------------------- homeward */

	private int goHome( boolean full )
	{
		if ( atBank() )
		{
			return useBank();
		}

		/*
		 * The charged amulet is the whole reason this run is worth doing at
		 * all: it skips the entire walk back. Rub it the moment the load is
		 * full, wherever that happens to be - the basement counts, and
		 * saves the climb.
		 */
		if ( full && heldAmulet() == AMULET_CHARGED && ! inEdgeArrival() )
		{
			int slot = getInventoryIndex( AMULET_CHARGED );

			if ( slot != -1 )
			{
				rubbing = true;
				useItem( slot );
				return random( 1500, 2000 );
			}
		}

		if ( inEdgeArrival() )
		{
			return walkRoute( EDGE_ARRIVAL, true );
		}

		/* Back out of the fountain courtyard before the legs can help. */
		if ( inCourtyard() )
		{
			return walkRoute( FOUNTAIN_PATH, false );
		}

		return travel( homeMode(), false );
	}

	private int useBank()
	{
		int[] banker = getNpcByIdNotTalk( BANKERS );

		/*
		 * getNpcByIdNotTalk sees about sixteen tiles, which is further than
		 * a click can reach. Without the distance gate the run stands at the
		 * door talking to a banker two rooms away and never arrives.
		 */
		if ( banker[0] != -1 && distanceTo( getNpcX( banker[0] ), getNpcY( banker[0] ) ) <= 4 )
		{
			talkToNpc( banker[0] );
			return random( 3000, 4000 );
		}

		int[] tile = bankTile();
		walkTo( tile[0], tile[1] );
		note( "At the bank, waiting on a banker." );
		return random( 1000, 1500 );
	}

	/**
	 * Deposit everything that is not the kit, then make the kit right.
	 *
	 * Deposits run first and one item per tick, because the inventory
	 * arrives full - nothing can come out of the bank until the ore has
	 * gone in.
	 */
	private int doBank()
	{
		int best = bestPickSlot();

		/*
		 * Nothing usable in hand - say, only a rune pick at mining 41 that
		 * the run has no business carrying, or no pick at all after a death.
		 * Pull a usable one out FIRST, or the deposit sweep below strips the
		 * inventory to nothing and the run has no tool.
		 */
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

		int junk = junkSlot();

		if ( junk != -1 )
		{
			int id = getInventoryId( junk );
			deposit( id, getInventoryCount( id ) );
			return random( 1000, 1500 );
		}

		if ( getInventoryCount( SLEEPING_BAG ) < 1 && bankCount( SLEEPING_BAG ) > 0 )
		{
			withdraw( SLEEPING_BAG, 1 );
			return random( 1000, 1500 );
		}

		/* Every bank run is a chance to trade up to a better pick - but only
		   to one this mining level can actually swing. */
		for ( int i = 0; i < best; i++ )
		{
			if ( getLevel( MINING ) >= PICKAXE_REQS[i] && bankCount( PICKAXES[i] ) > 0 )
			{
				System.out.println( "[HeroesRunite] A better pickaxe is in the bank - swapping up." );
				withdraw( PICKAXES[i], 1 );
				return random( 1000, 1500 );
			}
		}

		/*
		 * Charged amulet first, uncharged second - an uncharged one is a
		 * free dip at the fountain on the way in, so it is worth carrying.
		 * Neither anywhere means this is a walking run, which is slower but
		 * perfectly workable, and worth saying once rather than every trip.
		 */
		if ( heldAmulet() == -1 )
		{
			if ( bankCount( AMULET_CHARGED ) > 0 )
			{
				withdraw( AMULET_CHARGED, 1 );
				noAmulet = false;
				return random( 1000, 1500 );
			}

			if ( bankCount( AMULET_PLAIN ) > 0 )
			{
				withdraw( AMULET_PLAIN, 1 );
				noAmulet = false;
				return random( 1000, 1500 );
			}

			if ( ! noAmulet )
			{
				noAmulet = true;
				System.out.println( "[HeroesRunite] No dragonstone amulet held or banked - walking both ways from now on." );
			}
		}
		else
		{
			noAmulet = false;
		}

		if ( getInventoryCount( SLEEPING_BAG ) < 1 )
		{
			note( "No sleeping bag held or banked - mining on, but fatigue will cap the run." );
		}

		closeBank();
		tripCount++;
		return random( 1000, 1500 );
	}

	/* ------------------------------------------------------------- outbound */

	private int goToWork()
	{
		/*
		 * An uncharged amulet gets its dip on the way in, while the
		 * inventory is still empty and the detour costs nothing. The item id
		 * changing from 522 to 597 is what ends this branch, so there is no
		 * flag to get out of step with the world.
		 */
		if ( inGuild() && getInventoryCount( AMULET_PLAIN ) > 0 )
		{
			if ( getX() == 381 && getY() == 438 )
			{
				useItemOnObject( AMULET_PLAIN, 381, 439 );
				return random( 2000, 3000 );
			}

			return walkRoute( FOUNTAIN_PATH, true );
		}

		if ( inCourtyard() )
		{
			return walkRoute( FOUNTAIN_PATH, false );
		}

		if ( getX() == 354 && getY() == 3277 )
		{
			return mine();
		}

		return travel( workMode(), true );
	}

	private int mine()
	{
		int[] rock = getObjectById( RUNITE_ROCKS );

		if ( rock[0] != -1 )
		{
			atObject( rock[1], rock[2] );
			return random( 750, 950 );
		}

		/*
		 * One rock, fifteen minutes. A silent wait that long is
		 * indistinguishable from a hung script, so say so - once a minute,
		 * not once a tick.
		 */
		note( "Rock mined out - waiting on the respawn." );
		return random( 2000, 3000 );
	}

	/* ---------------------------------------------------------------- travel */

	/**
	 * Move one step along the route, in the given direction.
	 *
	 * The leg and the waypoint are found by position rather than
	 * remembered, so a relogin or a teleport mid-trip needs no recovery
	 * code: wherever the run comes back, it is already on the right leg.
	 *
	 * Exact tile matches win over near ones. Two legs do meet at tiles one
	 * apart - either side of the guild door, either side of the members'
	 * gate - and the obstacle between them is the only way across, so
	 * "whichever waypoint I am standing on" is the only answer that does
	 * not send the run walking into a wall.
	 */
	private int travel( int mode, boolean outward )
	{
		Leg[] route = route( mode );

		int bestLeg  = -1;
		int bestIdx  = 0;
		int bestDist = Integer.MAX_VALUE;

		for ( int l = 0; l < route.length; l++ )
		{
			int[][] path = route[l].path;

			for ( int i = 0; i < path.length; i++ )
			{
				int dist = distanceTo( path[i][0], path[i][1] );

				if ( dist == 0 )
				{
					bestLeg  = l;
					bestIdx  = i;
					bestDist = 0;
					l = route.length;
					break;
				}

				if ( dist < bestDist )
				{
					bestDist = dist;
					bestLeg  = l;
					bestIdx  = i;
				}
			}
		}

		if ( bestLeg == -1 )
		{
			return 1000;
		}

		Leg leg = route[bestLeg];

		if ( bestDist > ROUTE_SLACK )
		{
			reportLost( bestDist );
			walkTo( leg.path[bestIdx][0], leg.path[bestIdx][1] );
			return random( 1000, 1500 );
		}

		lostSince = -1L;

		if ( outward )
		{
			if ( bestIdx < leg.path.length - 1 )
			{
				walkTo( leg.path[bestIdx + 1][0], leg.path[bestIdx + 1][1] );
				return random( 1000, 1500 );
			}

			return cross( leg.workX, leg.workY, leg.workWall );
		}

		if ( bestIdx > 0 )
		{
			walkTo( leg.path[bestIdx - 1][0], leg.path[bestIdx - 1][1] );
			return random( 1000, 1500 );
		}

		return cross( leg.homeX, leg.homeY, leg.homeWall );
	}

	/**
	 * Click the obstacle at the end of a leg.
	 *
	 * Every one of these - the stairs, the guild door, the members' gate -
	 * answers by putting the player on the far side, which is a tile on the
	 * next leg. There is nothing to confirm and nothing to wait for beyond
	 * the move itself.
	 */
	private int cross( int x, int y, boolean wall )
	{
		/*
		 * An obstacle that does not open looks like nothing at all from
		 * here: the run is on its route, on the right tile, clicking the
		 * right thing, and the position never changes. The rejoin guard
		 * cannot see it, because nothing is lost. Count the clicks instead,
		 * and say so when the answer stops arriving.
		 */
		if ( getX() == crossX && getY() == crossY )
		{
			crossTries++;
		}
		else
		{
			crossX = getX();
			crossY = getY();
			crossTries = 1;
		}

		if ( crossTries >= 10 )
		{
			note( "STUCK: " + crossTries + " clicks on the obstacle at " + x + "," + y
				+ " from " + getX() + "," + getY() + " and still here. Needs a human." );
		}

		if ( x == -1 )
		{
			/* The bank end of leg 0, or the rock. Both are handled by the
			   caller; arriving here means a zone check disagreed with the
			   route, so stand still rather than thrash. */
			note( "At the end of the route with nothing to cross - waiting." );
			return random( 1500, 2000 );
		}

		if ( wall )
		{
			atWallObject( x, y );
		}
		else
		{
			atObject( x, y );
		}

		return random( 1500, 2000 );
	}

	/** Walk a plain route that has no obstacle at either end. */
	private int walkRoute( int[][] path, boolean forward )
	{
		int nearest = 0;
		int nearestDist = Integer.MAX_VALUE;

		for ( int i = 0; i < path.length; i++ )
		{
			int dist = distanceTo( path[i][0], path[i][1] );

			if ( dist < nearestDist )
			{
				nearestDist = dist;
				nearest = i;
			}
		}

		int next;

		if ( nearestDist > ROUTE_SLACK )
		{
			reportLost( nearestDist );
			next = nearest;
		}
		else
		{
			lostSince = -1L;
			next = forward
				? Math.min( nearest + 1, path.length - 1 )
				: Math.max( nearest - 1, 0 );
		}

		/*
		 * Same silence as the obstacle counter guards against, one layer
		 * out: a waypoint that cannot be reached from here - behind a shut
		 * door, across a wall a zone test got wrong - is walked at forever
		 * from a standstill, and every other guard is happy because the
		 * run is on its route and within slack. Watch the position instead
		 * of the intent.
		 */
		if ( getX() == walkX && getY() == walkY )
		{
			walkTries++;
		}
		else
		{
			walkX = getX();
			walkY = getY();
			walkTries = 1;
		}

		if ( walkTries >= 15 )
		{
			note( "STUCK: " + walkTries + " steps asked for from " + getX() + "," + getY()
				+ " towards " + path[next][0] + "," + path[next][1]
				+ " and the position never changed. Needs a human." );
		}

		walkTo( path[next][0], path[next][1] );
		return random( 1000, 1500 );
	}

	/* ----------------------------------------------------------------- modes */

	private static Leg[] route( int mode )
	{
		if ( mode == M_CATH )
		{
			return ROUTE_CATH;
		}

		if ( mode == M_EDGE )
		{
			return ROUTE_EDGE;
		}

		return ROUTE_FAL;
	}

	/**
	 * Which bank this load is going to.
	 *
	 * A charged amulet means Edgeville, because that is where the rub
	 * lands. Otherwise it is whichever bank the legs can actually reach on
	 * foot: Catherby through the mountain if the dwarves allow it, Falador
	 * the long way round if they do not.
	 */
	private int homeMode()
	{
		if ( heldAmulet() != -1 )
		{
			return M_EDGE;
		}

		return isQuestComplete( FISHING_CONTEST ) ? M_CATH : M_FAL;
	}

	/**
	 * Which route leads back to the rock from here.
	 *
	 * Not the same question as homeMode. The run can be standing at
	 * Catherby holding an amulet it only just withdrew, and in that case the
	 * way out is the Catherby route even though every load from here on
	 * banks at Edgeville. So let position answer it: the route with a
	 * waypoint nearest to where the run is standing is the route it is on.
	 * Ties go to the bank this load is headed for, which keeps the choice
	 * stable on the legs all three routes share.
	 */
	private int workMode()
	{
		int home = homeMode();
		int best = home;
		int bestDist = nearestOn( route( home ) );

		int[] others = { M_EDGE, M_CATH, M_FAL };

		for ( int i = 0; i < others.length; i++ )
		{
			if ( others[i] == home )
			{
				continue;
			}

			if ( others[i] == M_CATH && ! isQuestComplete( FISHING_CONTEST ) )
			{
				continue;   // the dwarves will not open the tunnel
			}

			int dist = nearestOn( route( others[i] ) );

			if ( dist < bestDist )
			{
				bestDist = dist;
				best = others[i];
			}
		}

		return best;
	}

	private int nearestOn( Leg[] route )
	{
		int best = Integer.MAX_VALUE;

		for ( int l = 0; l < route.length; l++ )
		{
			int[][] path = route[l].path;

			for ( int i = 0; i < path.length; i++ )
			{
				int dist = distanceTo( path[i][0], path[i][1] );

				if ( dist < best )
				{
					best = dist;
				}
			}
		}

		return best;
	}

	/* ------------------------------------------------------------------ kit */

	private int heldAmulet()
	{
		if ( getInventoryCount( AMULET_CHARGED ) > 0 )
		{
			return AMULET_CHARGED;
		}

		if ( getInventoryCount( AMULET_PLAIN ) > 0 )
		{
			return AMULET_PLAIN;
		}

		return -1;
	}

	/** Index into PICKAXES of the best pick held that this level can swing. */
	private int bestPickSlot()
	{
		for ( int i = 0; i < PICKAXES.length; i++ )
		{
			if ( getLevel( MINING ) >= PICKAXE_REQS[i] && getInventoryCount( PICKAXES[i] ) > 0 )
			{
				return i;
			}
		}

		return -1;
	}

	/**
	 * The first inventory slot holding something that is not the kit.
	 *
	 * The kit is one pickaxe, one sleeping bag and one amulet, and that is
	 * the whole of it. Ore, gems, a spare pick, whatever a death or a
	 * previous script left behind - it all reads as junk here and all goes
	 * in the bank, because every slot it occupies is one fewer ore per trip.
	 */
	private int junkSlot()
	{
		int best = bestPickSlot();
		int keepPick = ( best == -1 ) ? -1 : PICKAXES[best];
		int keepAmulet = heldAmulet();

		boolean bagSeen = false;
		boolean pickSeen = false;
		boolean amuletSeen = false;

		for ( int slot = 0; slot < 30; slot++ )
		{
			int id = getInventoryId( slot );

			if ( id <= 0 )
			{
				continue;
			}

			if ( id == SLEEPING_BAG && ! bagSeen )
			{
				bagSeen = true;
				continue;
			}

			if ( id == keepPick && ! pickSeen )
			{
				pickSeen = true;
				continue;
			}

			if ( id == keepAmulet && ! amuletSeen )
			{
				amuletSeen = true;
				continue;
			}

			return slot;
		}

		return -1;
	}

	/** Is anything about the kit or the load worth opening the bank for? */
	private boolean needsBank()
	{
		if ( junkSlot() != -1 )
		{
			return true;
		}

		if ( bestPickSlot() == -1 )
		{
			return true;
		}

		if ( getInventoryCount( SLEEPING_BAG ) < 1 )
		{
			return true;
		}

		return ( heldAmulet() == -1 && ! noAmulet );
	}

	/* ----------------------------------------------------------------- zones */

	private boolean atBank()
	{
		int x = getX();
		int y = getY();

		if ( x >= 435 && x <= 441 && y >= 490 && y <= 496 )
		{
			return true;   // Catherby
		}

		if ( x >= 328 && x <= 334 && y >= 550 && y <= 556 )
		{
			return true;   // Falador west
		}

		return ( x >= 212 && x <= 220 && y >= 447 && y <= 453 );   // Edgeville
	}

	/** Where to stand while waiting for a banker, at whichever bank this is. */
	private int[] bankTile()
	{
		int x = getX();

		if ( x >= 420 )
		{
			return new int[] { 440, 493 };
		}

		if ( x >= 300 )
		{
			return new int[] { 331, 553 };
		}

		return new int[] { 217, 450 };
	}

	/**
	 * North of Edgeville, where the amulet drops the player.
	 *
	 * Deliberately stops short of the bank's own zone at x 212 so the two
	 * never both claim a tile.
	 */
	private boolean inEdgeArrival()
	{
		return ( getX() >= 190 && getX() <= 211 && getY() >= 430 && getY() <= 452 );
	}

	/**
	 * Inside the guild building or its courtyard.
	 *
	 * Stops at row 440, not 441. The guild door is a direction-0 wall at
	 * 372,441, which means it stands between 372,441 and 372,440: row 441
	 * is the mountain path outside, row 440 is the first tile in. Letting
	 * this zone have 441 makes the run believe it is already inside while
	 * it is still standing on the doormat, so it walks to a tile behind a
	 * shut door, gets nowhere, and says nothing about it - the step is
	 * within route slack, so no guard fires either.
	 */
	private boolean inGuild()
	{
		return ( getX() >= 366 && getX() <= 385 && getY() >= 432 && getY() <= 440 );
	}

	/** The walled courtyard west of the guild's inner archway. */
	private boolean inCourtyard()
	{
		return ( getX() >= 378 && getX() <= 385 && getY() >= 432 && getY() <= 440 );
	}

	/* ----------------------------------------------------------------- menus */

	private int doMenu()
	{
		if ( rubbing )
		{
			rubbing = false;
			int option = getMenuIndex( "Edgeville" );
			answer( option >= 0 ? option : 0 );
			return random( 3000, 4000 );
		}

		answer( 0 );
		return random( 2000, 3000 );
	}

	/* -------------------------------------------------------------- logging */

	/**
	 * Say something at most once a minute.
	 *
	 * A line that repeats every tick reads exactly like a line that is not
	 * there - the log fills with one sentence and the thing that actually
	 * went wrong scrolls off the top.
	 */
	private void note( String message )
	{
		long now = System.currentTimeMillis();

		if ( now - noteTime < 60000L )
		{
			return;
		}

		noteTime = now;
		System.out.println( "[HeroesRunite] " + message );
	}

	/**
	 * Off the route, and for how long.
	 *
	 * The run rejoins by walking to the nearest waypoint, which handles
	 * nearly everything - a death, a stray click, a teleport. What it cannot
	 * handle is being somewhere the waypoint is unreachable from, and ten
	 * minutes of trying is the point at which that stops being a hiccup and
	 * starts needing a person.
	 */
	private void reportLost( int dist )
	{
		long now = System.currentTimeMillis();

		if ( lostSince == -1L )
		{
			lostSince = now;
		}

		if ( now - lostNote < 60000L )
		{
			return;
		}

		lostNote = now;
		long mins = ( now - lostSince ) / 60000L;

		if ( mins >= 10L )
		{
			System.out.println( "[HeroesRunite] STUCK: " + mins + " min off the route at "
				+ getX() + "," + getY() + " - nearest waypoint is " + dist + " tiles away. Needs a human." );
		}
		else
		{
			System.out.println( "[HeroesRunite] Off the route at " + getX() + "," + getY()
				+ " - rejoining, nearest waypoint " + dist + " tiles away." );
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
		else if ( str.contains( "power emanating" ) )
		{
			System.out.println( "[HeroesRunite] Amulet charged at the fountain." );
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

		int gained = getXpForLevel( MINING ) - initialXP;

		String mode;

		if ( heldAmulet() == AMULET_CHARGED )
		{
			mode = "teleport";
		}
		else if ( heldAmulet() == AMULET_PLAIN )
		{
			mode = "uncharged";
		}
		else
		{
			mode = isQuestComplete( FISHING_CONTEST ) ? "walk (tunnel)" : "walk (Falador)";
		}

		drawString( "HeroesRunite - " + mode, x, y, 1, orange );
		y += 15;
		drawString( "Runtime: " + ( secs / 3600 ) + ":" + String.format( "%02d", ( secs % 3600 ) / 60 ) + ":" + String.format( "%02d", secs % 60 ), x, y, 1, white );
		y += 15;
		drawString( "Ore: " + oreCount + " in " + tripCount + " trips", x, y, 1, white );
		y += 15;
		drawString( "XP: " + gained + " - " + (int)( ( gained * 3600L ) / secs ) + " XP/Hr", x, y, 1, white );
	}
}
