import java.util.Locale;

/**
 * RunitePkMiner - mines every runite rock a mage can reach and kills whatever
 * tries to stop it.
 *
 * There are exactly three runite rocks on this map worth a trip: one in the
 * Heroes' Guild basement, and two side by side in the western wilderness at
 * 256,158 and 258,156. All three sit on a fifteen-minute respawn, so the run
 * alternates - guild, wilderness, guild, wilderness - and each leg is timed
 * so the rock at the other end has had the whole of the other leg to come
 * back. Twelve ore an hour is the ceiling that arithmetic allows (four from
 * the guild, eight from the pair) and it is only reached when no respawn is
 * missed, so every wait in this script is spent standing beside the rock
 * rather than walking somewhere else.
 *
 * Where to start it: at the Edgeville bank with the kit in the bank, or
 * anywhere along the Taverley road and the guild with a pickaxe in the pack.
 * Started at the guild it mines the guild rock first and fixes the kit on
 * the way home. Started anywhere else without the full kit it heads for the
 * Edgeville bank - rubbing the amulet if it is far, walking if it is near -
 * and says so in the log before it moves. The full kit is a runite pickaxe,
 * staff of guthix, rune legs, plate body, large helm and kite, a guthix
 * cape, Claws of Guthix runes, lobsters and the dragonstone amulet.
 *
 * The wilderness half is why the kit is what it is. A dragonstone amulet is
 * the way home: rubbing it lands the run north of Edgeville, and the server
 * refuses the rub at wilderness 30 and deeper (InvActionHandler:1248), which
 * is why the retreat walks back down to level 29 before trying. An uncharged
 * amulet is therefore not a kit item but a blocker - the run will not set
 * foot north of the ditch without a charged one, because the alternative is
 * walking three hundred tiles home through hobgoblins at ten hitpoints.
 *
 * Combat is defensive and it is absolute. The run never clicks attack on
 * anybody. It casts Claws of Guthix at whatever engaged it, prays, eats, and
 * goes back to the rock when the thing is dead. A player who opens on it
 * gets the same answer plus the best defence and magic prayers the account
 * owns, and whatever they drop goes to the bank on the next trip.
 *
 * Nothing here has a config window. State is read from position, inventory
 * and the world, so a relogin or a death mid-trip is picked up wherever the
 * run finds itself.
 */
public final class RunitePkMiner extends Script
{
	/* ------------------------------------------------------------- skills */

	private static final int HITS   = 3;
	private static final int PRAYER = 5;
	private static final int MAGIC  = 6;
	private static final int MINING = 14;

	private static final int RUNITE_LEVEL = 85;
	private static final int CLAWS_LEVEL  = 60;

	/* ------------------------------------------------------------- the kit */

	private static final int STAFF     = 1217;   // Staff of guthix
	private static final int LEGS      = 402;    // Rune plate mail legs
	private static final int BODY      = 401;    // Rune plate mail body
	private static final int HELM      = 112;    // Large rune helmet
	private static final int SHIELD    = 404;    // Rune kite shield
	private static final int CAPE      = 1215;   // Guthix cape
	private static final int GAUNTLETS = 700;    // Gauntlets of cooking
	private static final int PICKAXE   = 1262;   // Rune pickaxe
	private static final int BAG       = 1263;   // Sleeping bag

	private static final int AMULET_CHARGED = 597;
	private static final int AMULET_PLAIN   = 522;

	private static final int AIR    = 33;
	private static final int BLOOD  = 619;
	private static final int FIRE   = 31;
	private static final int LOBSTER = 373;

	private static final int RUNITE_ORE = 409;

	/* Uncut gems a swing turns up now and then, banked with the ore. */
	private static final int[] GEMS = { 157, 158, 159, 160 };

	/* Worn on sight, in this order. Nothing else is wieldable kit. */
	/*
	 * Everything that goes on before the run leaves the bank.
	 *
	 * The armour is not decoration. calcGodSpells reads the *defender's*
	 * armour points twice over - once in the hit roll against magic, and
	 * again as a shift that pulls the whole damage spread down - so a
	 * caster in nothing but rune legs is both easy to hit and easy to hurt,
	 * and a level 42 loses to anything geared. Full rune and a kite take
	 * this from 49 armour points to 205.
	 *
	 * The cape is there for the other half of the same formula: magic
	 * points feed the run's own hit chance directly, and the Guthix cape
	 * is +10 against the staff's +6. It is also the authentic partner to
	 * Charge, which the staff is the cast requirement for.
	 *
	 * The shield fits because the Staff of Guthix is wieldable type 16 and
	 * not a two-hander - the 8192 bit a rune 2-handed sword carries is what
	 * would have blocked it.
	 *
	 * No gauntlets. The plate body's wieldable type is 322, which carries
	 * the gauntlet bit (256): a plate body and a pair of gauntlets unwield
	 * each other, exactly as they did in the original game. A kit that
	 * lists both is a wear() loop that puts one on, knocks the other off,
	 * and never gets to leave the bank. The cooking gauntlets cook nothing
	 * on this run anyway - the lobsters come out of the bank already done.
	 */
	/*
	 * The amulet is worn too, charged or plain: it is wieldable, the server
	 * rubs it and dips it just the same from the neck, and a worn amulet is
	 * one slot more for ore. Each change of state - the dip, the last rub -
	 * hands back a fresh unworn item, and wear() picks that up next tick.
	 */
	private static final int[] WEARABLES = {
		LEGS, BODY, HELM, SHIELD, CAPE, STAFF, AMULET_CHARGED, AMULET_PLAIN
	};

	/*
	 * Claws of Guthix is four air, two blood and one fire a cast
	 * (SpellDef 33), so these three numbers are a hundred casts exactly and
	 * run out together. Keeping them in that ratio is the whole point: a
	 * stock that empties one rune first leaves the other two as dead weight
	 * in a wilderness inventory.
	 */
	private static final int AIR_STOCK   = 400;
	private static final int BLOOD_STOCK = 200;
	private static final int FIRE_STOCK  = 100;
	/*
	 * Fourteen, not ten, and the extra four are not for eating.
	 *
	 * Worn kit sits in the pack in this game, so full rune plus a cape, an
	 * amulet, gauntlets, a pickaxe and a bag is ten slots before anything
	 * useful goes in, and three rune stacks make thirteen. Fourteen
	 * lobsters fills the pack to twenty-seven and leaves three slots for
	 * ore and whatever is on the floor after a fight - and when those three
	 * are gone the food itself is the buffer, because a lobster can be
	 * turned into an empty slot on demand. See makeRoom().
	 */
	private static final int FOOD_STOCK  = 14;

	/*
	 * The danger line, as a fraction of max hitpoints: at or below it the
	 * run stops fighting and eats instead of eating through the fight.
	 * Two fifths leaves room for a round of damage plus the tick the eat
	 * itself costs.
	 */
	private static final int HP_FLEE_NUM = 2;
	private static final int HP_FLEE_DEN = 5;

	private static final int COINS = 10;

	/*
	 * Stackables worth taking whatever one of them prices at: coins, every
	 * rune, and every arrow. Ids read off ItemDef rather than typed from
	 * memory - the runes are the fourteen that exist, the arrows are the
	 * thirteen real ones (arrow shafts, the quest "Arrow", the lit arrow
	 * and the broken arrow are not loot).
	 */
	private static final int[] STACKABLES = {
		COINS,
		/* runes */
		31, 32, 33, 34, 35, 36, 37, 38, 40, 41, 42, 46, 619, 825,
		/* arrows */
		11, 574, 638, 639, 640, 641, 642, 643, 644, 645, 646, 647, 723
	};

	/* What one lobster puts back. Nothing eats below this gap. */
	private static final int LOBSTER_HEAL = 12;

	/* Below this many casts in hand, the next bank visit tops up; at zero
	   casts the staff is a stick and the run goes home whatever it is
	   doing. */
	private static final int CAST_FLOOR = 15;

	private static final int CLAWS = 33;

	/*
	 * Charge, and it is not optional equipment.
	 *
	 * Claws of Guthix rolls 0-25 on a charged caster and 0-10 on an
	 * uncharged one (SpellHandler:683) - so without this the run answers a
	 * geared attacker at about five damage a cast and loses every fight it
	 * is in, which is exactly what it did. Level 80 magic, a god staff
	 * wielded, three air, three blood and three fire, and it holds for ten
	 * minutes: six casts an hour against a kit that carries hundreds.
	 *
	 * Only the cast *before* the ditch belongs to this script. Keeping the
	 * buff up during a fight is the client's job and it already does it:
	 * the autocast charge-keeper (mudclient, SPELL_CHARGE) recasts Charge
	 * in place of a cast that was going out anyway, with its own retry and
	 * give-up handling, and without touching which spell autocast repeats.
	 * Doing it from here as well would mean casting Charge as the last
	 * spell mid-fight, and the autocaster repeats the last spell cast -
	 * which is how a run ends up charging itself at an attacker instead of
	 * clawing him. That is also why the pre-ditch cast is safe: it happens
	 * with nothing engaged, and the first claws cast of a fight points the
	 * autocaster back at claws before it is ever armed.
	 */
	private static final int CHARGE       = 48;
	private static final int CHARGE_LEVEL = 80;

	/*
	 * Treated as nine minutes rather than ten. The buff is read off the
	 * server's own message, so the clock here starts a moment late and
	 * running it to the wire means discovering it lapsed from the damage
	 * numbers in the middle of a fight.
	 */
	private static final long CHARGE_MS = 540000L;

	/* How many Charge casts to send before deciding it is not going to take. */
	private static final int CHARGE_TRIES = 4;

	/* ----------------------------------------------------------- prayers */

	private static final int PARALYZE = 12;   // Paralyze monster, prayer 37

	/* Best first. Defence, then magic - the two the run turns on for a
	   player fight. Steel skin and Mystic might if the account has them,
	   the weaker pair if it does not. */
	private static final int[] DEF_PRAYERS  = { 9, 3, 0 };
	private static final int[] MAGE_PRAYERS = { 19, 17, 15 };

	/* Every prayer this script ever switches on, so it can switch them all
	   off again without thinking about which fight they came from. */
	private static final int[] ALL_PRAYERS = { PARALYZE, 9, 3, 0, 19, 17, 15 };

	private static final int[] BANKERS = { 95, 224, 268, 485, 540, 617 };

	private static final int HEROES_QUEST = 19;

	/* ------------------------------------------------------------- places */

	private static final int[] RUNITE_ROCKS = { 210, 211 };

	/* The guild basement rock, and the tile to swing at it from. */
	private static final int GUILD_ROCK_X = 353, GUILD_ROCK_Y = 3277;
	private static final int GUILD_MINE_X = 354, GUILD_MINE_Y = 3277;

	/*
	 * The wilderness pair. 257,157 is the only tile that touches both, so
	 * the run stands there and swings at whichever is up without moving.
	 */
	private static final int WILD_ROCK_A_X = 256, WILD_ROCK_A_Y = 158;
	private static final int WILD_ROCK_B_X = 258, WILD_ROCK_B_Y = 156;
	private static final int WILD_MINE_X   = 257, WILD_MINE_Y   = 157;

	/*
	 * Where to wait out a wilderness respawn.
	 *
	 * Four Deadly Red spiders roam x254-263, y151-162 (NpcLoc), which is the
	 * rocks' own patch - there is no waiting beside those rocks without
	 * being chewed on. 250,157 is four tiles west of the edge of that patch,
	 * on the route, and still close enough to see both rocks come back.
	 */
	private static final int WILD_WAIT_X = 250, WILD_WAIT_Y = 157;

	/* Altars. The guild's is up the ladder at 375,438; the Edgeville end
	   borrows the monastery's, which the outbound leg walks straight past. */
	private static final int GUILD_LADDER_X = 375, GUILD_LADDER_Y = 438;
	private static final int GUILD_UP_X     = 375, GUILD_UP_Y     = 437;
	private static final int GUILD_DOWN_X   = 375, GUILD_DOWN_Y   = 1382;
	private static final int GUILD_ALTAR_X  = 369, GUILD_ALTAR_Y  = 1381;
	private static final int GUILD_PRAY_X   = 371, GUILD_PRAY_Y   = 1381;

	private static final int MON_ALTAR_X = 225, MON_ALTAR_Y = 456;
	private static final int MON_PRAY_X  = 225, MON_PRAY_Y  = 457;

	/* The fountain of heroes, which is where an uncharged amulet is dipped.
	   It is in the guild's walled courtyard on the ground floor, not
	   upstairs with the altar - two different errands in one building. */
	private static final int FOUNTAIN_X = 381, FOUNTAIN_Y = 439;

	/*
	 * ---------------------------------------------------------------- routes
	 *
	 * Plotted against the server's own collision data with tools/route.py,
	 * never off a map. Index 0 of every leg is the bank end. Doors and gates
	 * are not walked through: a leg stops on the tile beside its obstacle
	 * and names the thing to click, because the click is the only crossing.
	 */

	/* Edgeville bank out to the members' gate. */
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

	/* North of Edgeville, where the amulet drops the run. */
	private static final int[][] EDGE_ARRIVAL = {
		{193,435},{197,435},{199,437},{200,440},{202,442},{204,444},
		{206,446},{209,447},{213,447},{217,450}
	};

	/*
	 * Lumbridge respawn to the Edgeville bank.
	 *
	 * A death is the one thing that puts the run somewhere no leg passes
	 * within a hundred tiles of, and the amulet it would have teleported
	 * home with is in the killer's pack. Walking at a waypoint that far off
	 * does nothing at all - the client paths inside the loaded area and
	 * silently drops anything further - so the run stood on the respawn
	 * tile asking for a step it was never going to get. This is the walk
	 * out, plotted with tools/route.py like every other leg, and it needs
	 * no obstacle: there is a way from the respawn to the bank that crosses
	 * no door or gate.
	 */
	private static final int[][] DEATH_PATH = {
		{122,647},{122,643},{124,641},{125,638},{129,638},{131,636},
		{132,633},{134,631},{134,627},{134,623},{134,619},{134,615},
		{138,615},{138,611},{138,607},{138,603},{138,599},{140,597},
		{144,597},{148,597},{149,594},{153,594},{157,594},{161,594},
		{165,594},{169,594},{173,594},{177,594},{178,591},{178,587},
		{178,583},{178,579},{178,575},{178,571},{178,567},{178,563},
		{178,559},{180,557},{180,553},{180,549},{182,547},{183,544},
		{183,540},{184,537},{188,537},{190,535},{192,533},{195,532},
		{199,532},{203,532},{205,530},{207,528},{209,526},{210,523},
		{211,520},{213,518},{213,514},{215,512},{215,508},{215,504},
		{215,500},{215,496},{215,492},{215,488},{215,484},{215,480},
		{215,476},{216,473},{220,473},{221,470},{224,469},{224,465},
		{224,461},{224,457},{223,454},{223,450},{222,447},{218,447},
		{217,450}
	};

	/* The courtyard, for the fountain dip. A dead end: the only way on is
	   back the way it came. */
	private static final int[][] FOUNTAIN_PATH = {
		{372,440},{374,439},{375,437},{378,437},{380,438},{381,438}
	};

	/* The guild door to the foot of the upstairs ladder. Shares its first
	   two tiles with the fountain path, which is harmless - both are walked
	   forwards from the same door. */
	private static final int[][] LADDER_PATH = {
		{372,440},{374,439},{375,437}
	};

	/* Upstairs, the ladder head to the altar. */
	private static final int[][] ALTAR_PATH = {
		{375,1381},{374,1381},{373,1381},{372,1381},{371,1381}
	};

	/* The monastery altar, off the outbound Edgeville leg. */
	private static final int[][] MON_PATH = {
		{224,462},{224,461},{224,460},{224,459},{224,458},{224,457},{225,457}
	};

	/*
	 * Edgeville bank to the wilderness pair.
	 *
	 * This is the whole wilderness trip in one list, and it is as safe as
	 * the landscape allows rather than as safe as anyone would like. The
	 * corridor it climbs crosses hobgoblins (x206-240, y236-268), giants
	 * (x213-229, y167-189) and, at the top, the spiders that live on the
	 * rocks themselves. There is no line north that misses all three; what
	 * there is instead is a run that kills what touches it. The walk back
	 * down this same list is where the amulet gets rubbed, at the first
	 * waypoint shallower than wilderness 30.
	 */
	private static final int[][] WILD_PATH = {
		{217,450},{217,446},{220,445},{220,441},{220,437},{220,433},
		{220,429},{220,425},{220,421},{220,417},{219,414},{219,410},
		{219,406},{219,402},{219,398},{218,395},{218,391},{216,389},
		{216,385},{216,381},{216,377},{216,373},{216,369},{216,365},
		{216,361},{216,357},{215,354},{215,350},{215,346},{215,342},
		{215,338},{215,334},{215,330},{215,326},{214,323},{214,319},
		{213,316},{213,312},{213,308},{213,304},{213,300},{213,296},
		{213,292},{213,288},{213,284},{213,280},{213,276},{213,272},
		{213,268},{212,265},{212,261},{212,257},{212,253},{212,249},
		{212,245},{212,241},{212,237},{212,233},{212,229},{212,225},
		{212,221},{212,217},{212,213},{212,209},{212,205},{211,202},
		{211,198},{211,194},{211,190},{212,187},{212,183},{212,179},
		{212,175},{212,171},{212,167},{212,163},{212,159},{214,157},
		{218,157},{222,157},{226,157},{230,157},{234,157},{238,157},
		{242,157},{246,157},{250,157},{254,157},{257,157}
	};

	/**
	 * Shallowest wilderness row the amulet still works on.
	 *
	 * Wilderness level on this strip is 1 + (427 - y) / 6, and the server
	 * refuses the rub at 30 and deeper. y 254 is level 29, y 253 is 30. One
	 * row, and the difference between a teleport home and a walk.
	 */
	private static final int RUB_ROW = 254;

	/** The ditch. North of this row is the wilderness and the amulet rules. */
	private static final int WILD_ROW = 427;

	/**
	 * One stretch of walking plus the obstacle at each end of it.
	 *
	 * path[0] is the bank end. homeX/homeY is the thing to click when
	 * standing on path[0] and still heading home; workX/workY is the thing
	 * to click when standing on the last tile and still heading out. Either
	 * can be -1 where the leg simply ends.
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

	private static final Leg[] ROUTE_GUILD = {
		new Leg( EDGE_LEG,  -1,  -1, false, 341,  487, false ),
		new Leg( TAV_LEG,  341, 487, false, 372,  441, true  ),
		new Leg( GUILD_LEG, 372, 441, true, 368,  438, false ),
		new Leg( MINE_LEG, 368, 3270, false, -1,   -1, false )
	};

	/* How far off a waypoint still counts as being on the route. The widest
	   gap above is five tiles, so six allows the one-tile drift a walk ends
	   on and nothing more. Widening this is never the fix for a stuck run. */
	private static final int ROUTE_SLACK = 6;

	/*
	 * Past this far off the route, walking at a waypoint is not a correction
	 * - it is a request the client throws away. Nothing on a normal trip is
	 * ever this lost; a death is, which is what DEATH_PATH is for. This is
	 * deliberately a second number rather than a bigger ROUTE_SLACK: slack
	 * is how much wandering counts as still being on the route, and widening
	 * that hides exactly the stalls it exists to catch.
	 */
	private static final int LONG_HAUL = 20;

	/*
	 * The far edges of the map, for sanity rather than navigation: x runs to
	 * about 950 and y to 944 per plane across five planes. Anything outside
	 * this is the client talking, not a place.
	 */
	/* 0 controlled, 1 aggressive, 2 accurate, 3 defensive - see FIGHTMODES. */
	private static final int FIGHT_DEFENSIVE = 3;

	private static final int WORLD_X = 1000;
	private static final int WORLD_Y = 4800;

	/* Loot worth a slot. Anything a player drops at or above this base
	   price goes in the bank; below it, bones and burnt food, it stays. */
	private static final int LOOT_MIN = 20;

	/* ---------------------------------------------------------------- state */

	/**
	 * Which rock this trip is for.
	 *
	 * The only thing the run remembers between ticks, and it is re-derived
	 * from position whenever position says plainly where the run is, so a
	 * relogin never ends up walking the wrong half of the cycle.
	 */
	private boolean wildTrip = false;

	/**
	 * The bank has been dealt with on this visit.
	 *
	 * Without this the run stands in Edgeville forever: arriving at the bank
	 * is the condition for opening it, and closing it does not move the run
	 * off the tile that said to open it. One window per visit, cleared the
	 * moment the run walks away.
	 */
	private boolean banked = false;

	private boolean rubbing   = false;
	private boolean prayerRun = false;   // this stop is for the altar
	private boolean clawsLocked = false; // the god-spell gate refused a cast
	private long fleeAt = 0L;            // last break-off-to-eat, for the note
	private boolean reachedWork = false; // this trip got to its rock
	private int selfPid = -1;            // our own entry in the player list
	private long chargedAt = 0L;         // last Charge, by the server's word for it
	private boolean chargeNoted = false; // said once, not every ten minutes
	private int chargeTries = 0;         // casts sent since the buff last landed
	private long chargeRetryAt = 0L;     // the server's own "try again in N seconds"
	private boolean noAmulet  = false;
	private String shunName  = null;     // player the server refused us, by name
	private long   shunUntil = 0L;       // ...and until when enemyPlayer() skips them
	private boolean noLevel   = false;   // started with "nolevel": never sleep
	private boolean lockNoted = false;   // fatigue reached 100 under nolevel
	private boolean lowRunes  = false;

	private int oreCount  = 0;
	private int tripCount = 0;
	private int killCount = 0;
	private int deathCount = 0;
	private int lastX = -1, lastY = -1;  // last tick's tile, for the respawn jump
	private int initialXP = 0;
	private long startTime = -1L;
	private long moveTime  = -1L;

	/* When the inventory first read as empty, so a real death can be told
	   from a login that has not finished arriving. */
	private long emptySince = -1L;

	/* Combat bookkeeping. attackedAt is the "You are under attack!" stamp,
	   which is the only unambiguous word that the fight is ours. */
	private long attackedAt = 0L;
	private int  fightKind  = 0;         // 0 none, 1 npc, 2 player
	private int  fightPid   = -1;
	private int  lootX = -1, lootY = -1;
	private long lootUntil = 0L;

	private int crossX = -1, crossY = -1, crossTries = 0;
	private int walkX = -1, walkY = -1, walkTries = 0;

	private long noteTime  = 0L;
	private long lostSince = -1L;
	private long lostNote  = 0L;
	private long pulseAt   = 0L;   // last DEBUG state line
	private int  wearLast  = -1;   // item wear() tried most recently
	private int  wearTries = 0;
	private int  wearSkip  = -1;   // item wear() has given up on
	private static final int WEAR_TRIES = 6;

	/* A state line every twenty seconds; the quiet stalls are the ones
	   that need it, since a loop that never notes never shows in the log. */
	private static final boolean DEBUG = false;

	public RunitePkMiner( Extension ex )
	{
		super( ex );
	}

	/**
	 * Start as RunitePkMiner(nolevel) to freeze every level this run would
	 * otherwise raise.
	 *
	 * Fatigue is the game's own experience lock: at 100% the server still
	 * hands over the ore and still lets Claws of Guthix land, it just
	 * refuses the exp ("You are too tired to gain experience") - mining,
	 * magic, defence and hits alike. A pure that wants to keep its combat
	 * level where it is therefore wants to be tired all the time, so this
	 * mode never sleeps and leaves the bag in the bank. Fatigue climbs one
	 * point per exp award, so a rested account takes its first hundred
	 * awards before the lock closes; start tired if even that matters.
	 */
	@Override
	public void init( String params )
	{
		noLevel = params != null && params.toLowerCase().contains( "nolevel" );

		System.out.println( "[RunitePkMiner] Guild rock, wilderness pair, Claws of Guthix for anything that objects." );
		SetAutocast( true );
		System.out.println( "[RunitePkMiner] Start at the Edgeville bank, or at the guild with a pickaxe. From anywhere else with the kit incomplete it goes to the bank first - by amulet when far, on foot when near." );

		if ( noLevel )
		{
			System.out.println( "[RunitePkMiner] nolevel: staying at 100% fatigue, so no skill - mining included - gains exp. The bag stays in the bank." );
		}
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
		 * Sit out a tick on an impossible position.
		 *
		 * Around the bank window the client has handed this script
		 * coordinates like 2717,2768 for a single tick - nowhere on the
		 * map, and gone again by the next one. Reading it as a position
		 * makes every distance in the script nonsense: the nearest waypoint
		 * comes out two thousand tiles away, which reads as lost, and being
		 * that lost now means walking the route home from a death. One
		 * garbage tick is not worth a trip to Lumbridge.
		 */
		if ( getX() < 0 || getX() > WORLD_X || getY() < 0 || getY() > WORLD_Y )
		{
			return 600;
		}

		/* A jump onto the respawn tile is a death; nothing else lands there
		   from that far in one tick. */
		if ( lastX >= 0 && atRespawn( getX(), getY() ) && ! atRespawn( lastX, lastY )
			&& distanceTo( lastX, lastY ) > 40 )
		{
			onDeath();
		}

		lastX = getX();
		lastY = getY();

		/*
		 * Defensive, always, and checked every tick rather than set once.
		 *
		 * This is a 1 attack, 1 strength pure and those two stats are not
		 * allowed to move. The run never swings a weapon on purpose - it
		 * casts - but it does not have to: anything that attacks it gets
		 * retaliated against, and the server splits that exp by fight mode
		 * (FightEvent:135). Controlled, which is the default, feeds attack
		 * and strength as well as defence and would quietly end the build
		 * over a few hundred hobgoblins. Defensive sends the lot to
		 * defence. Hitpoints still rise either way; there is no mode that
		 * stops that and nothing to be done about it.
		 *
		 * Checked rather than assumed because the mode lives on the
		 * account, so anyone who logs this character in by hand and clicks
		 * a different style leaves it that way.
		 */
		if ( DEBUG && System.currentTimeMillis() - pulseAt > 20000L )
		{
			pulseAt = System.currentTimeMillis();
			System.out.println( "[RunitePkMiner] pulse " + getX() + "," + getY()
				+ " hp=" + getCurrentLevel( HITS ) + " fat=" + getFatigue()
				+ " free=" + getEmptySlots() + " bank=" + isBanking() + " menu=" + isQuestMenu()
				+ " combat=" + inCombat() + " banked=" + banked + " wild=" + wildTrip
				+ " reached=" + reachedWork + " am=" + heldAmulet() + " sleep=" + isSleeping()
				+ " unworn=" + unwornId() + " banker=" + bankerDist() + " kit=" + kitComplete() );
		}

		if ( getFightMode() != FIGHT_DEFENSIVE )
		{
			System.out.println( "[RunitePkMiner] Fight mode was " + FIGHTMODES[ getFightMode() ]
				+ " - setting Defensive. This build does not take attack or strength exp." );
			setFightMode( FIGHT_DEFENSIVE );
			return 600;
		}

		/*
		 * An empty inventory is nearly always the server not having sent it
		 * yet - main() ticks ahead of that packet after a relogin, and every
		 * check below would read the blank as "the kit is gone" and march to
		 * the bank. But it is not always: a death in the wilderness takes
		 * the lot, and a run that treats a real empty pack as a packet it is
		 * still waiting for stands on the respawn tile until somebody
		 * notices. Fifteen seconds is longer than any login takes and
		 * shorter than anybody's patience.
		 */
		if ( getInventoryCount() == 0 )
		{
			long now = System.currentTimeMillis();

			if ( emptySince == -1L )
			{
				emptySince = now;
			}

			if ( now - emptySince < 15000L )
			{
				return 1000;
			}

			note( "Nothing in the pack at all - this is a death, not a slow login. Going to the bank for a new kit." );
		}
		else
		{
			emptySince = -1L;
		}

		if ( ! requirementsMet() )
		{
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

		/*
		 * Combat first, above everything, because everything else assumes
		 * the run is free to walk. A fight it ignores for one tick is a
		 * fight it walks away from mid-cast.
		 */
		int fight = fight();

		if ( fight > 0 )
		{
			return fight;
		}

		int loot = loot();

		if ( loot > 0 )
		{
			return loot;
		}

		/* Nothing on us: drop the prayers rather than burn points standing
		   at a rock. */
		idlePrayers();

		if ( eat() )
		{
			return random( 1200, 1600 );
		}

		/* Anti-stuck: an "I can't reach that" message queued a jiggle. */
		if ( moveTime != -1L && System.currentTimeMillis() >= moveTime )
		{
			moveTime = -1L;
			walkTo( getX() + random( -1, 1 ), getY() + random( -1, 1 ) );
			return random( 1000, 1500 );
		}

		int rest = rest();

		if ( rest > 0 )
		{
			return rest;
		}

		/*
		 * Dress outside the bank window, not inside it. Anything new only
		 * ever arrives at the bank, but the wield goes through while the
		 * window is open about as often as it does not, and a staff that
		 * silently stayed in the pack is a run that walks into the
		 * wilderness unable to cast.
		 */
		if ( wear() )
		{
			return random( 800, 1200 );
		}

		return work();
	}

	/* ------------------------------------------------------- requirements */

	/**
	 * The three things no amount of walking fixes.
	 *
	 * Checked every tick rather than once at startup, because a level can
	 * arrive mid-session and a quest cannot un-complete: the cost is three
	 * array reads and it means a script started on the wrong account stops
	 * at the first tick instead of at the guild door.
	 */
	private boolean requirementsMet()
	{
		if ( getLevel( MINING ) < RUNITE_LEVEL )
		{
			System.out.println( "[RunitePkMiner] Runite needs mining " + RUNITE_LEVEL
				+ " and this is " + getLevel( MINING ) + " - stopping before the walk rather than after it." );
			setAutoLogin( false );
			stopScript();
			return false;
		}

		if ( getLevel( MAGIC ) < CLAWS_LEVEL )
		{
			System.out.println( "[RunitePkMiner] Claws of Guthix needs magic " + CLAWS_LEVEL
				+ " and this is " + getLevel( MAGIC ) + ". Without it there is nothing to fight back with - stopping." );
			setAutoLogin( false );
			stopScript();
			return false;
		}

		/*
		 * The guild door is Heroes' Quest's and it refuses anybody who has
		 * not finished it. Finding that out at the door costs a walk from
		 * Edgeville and back for nothing.
		 */
		if ( ! isQuestComplete( HEROES_QUEST ) )
		{
			System.out.println( "[RunitePkMiner] Heroes' Quest is not finished, so the guild door stays shut - stopping." );
			setAutoLogin( false );
			stopScript();
			return false;
		}

		return true;
	}

	/* ------------------------------------------------------------- combat */

	/**
	 * Answer whatever started it, and only whatever started it.
	 *
	 * This run never opens a fight. The test for "ours" is the server's own
	 * "You are under attack!" line plus our combat flag - not proximity, and
	 * not somebody else's health bar, because two players scrapping beside
	 * the rock are in combat too and are none of our business.
	 *
	 * Returns 0 when there is no fight, otherwise the tick to wait.
	 */
	private int fight()
	{
		learnSelf();

		boolean engaged = inCombat()
			|| System.currentTimeMillis() - attackedAt < 8000L;

		if ( ! engaged )
		{
			if ( fightKind != 0 )
			{
				endFight();
			}

			return 0;
		}

		/*
		 * The danger tier, and it has to come before anything that picks a
		 * target: below the line the only two things worth a tick are
		 * breaking combat and eating, and both want to happen on every one
		 * of them rather than behind a target search.
		 *
		 * It does not stop casting to do it. The built-in autocast closes
		 * distance before each cast - fireAutocast() calls walkToTile() -
		 * so a retreat with autocast on gives ground back a tile at a time.
		 * That is the lesser problem by a wide margin: a retreat from
		 * something that follows almost never lands, and a run that stops
		 * dealing damage at forty percent just dies slower.
		 */
		if ( hurt() )
		{
			/*
			 * Aim first, then run. The flee is a walk and an eat, and the
			 * built-in autocast is what keeps hitting through it - but
			 * autocast only fires at a target it has been given, and the
			 * give happens in fightPlayer()/fightNpc(), below this line.
			 * Two deaths at the wilderness pair had the same shape: a
			 * re-open below the flee line, forty seconds of "cannot
			 * retreat" and "cannot do that whilst fighting", and three
			 * casts in all of it, with the other side parked on 30 hp.
			 * The fight had no target because the hurt branch never
			 * handed it one.
			 */
			aimAt( enemyPlayer(), enemyNpc() );
			return runAndEat();
		}

		/*
		 * No eating here. The server refuses food to anyone in combat
		 * ("You cannot do that whilst fighting!" - the original game's rule,
		 * and the reason RSC fights were won by retreating to eat), so an
		 * eat attempted mid-fight is a tick spent not casting. That was a
		 * run standing at 38 of 49 retrying a lobster while a level 90 took
		 * it to 6. Above the danger line the answer to being hit is to hit
		 * back; the top-up happens the moment the fight ends, from main().
		 */
		int player = enemyPlayer();

		if ( player != -1 )
		{
			return fightPlayer( player );
		}

		int npc = enemyNpc();

		if ( npc != -1 )
		{
			return fightNpc( npc );
		}

		/*
		 * Engaged, and nothing in reach to answer. Either the thing died
		 * this tick or the combat flag is the health-bar linger rather than
		 * a fight - inCombat() is true for a few seconds after the last
		 * blow. Hold still either way: walking off mid-linger is how a run
		 * ends up dragging a half-dead hobgoblin up the corridor.
		 */
		return random( 600, 900 );
	}

	/**
	 * Hand autocast a target for a fight it does not have one for yet. The
	 * cast itself is what sets the target in the client, so this is one
	 * Claws at whoever is new - a player first, the way fight() orders
	 * them - and nothing when the current target is still the right one.
	 */
	private void aimAt( int player, int npc )
	{
		if ( player != -1 )
		{
			if ( fightKind != 2 || fightPid != getPlayerPID( player ) )
			{
				openOn( player );
				magePlayer( player, CLAWS );
				SetAutocast( true );
			}
		}
		else if ( npc != -1 && fightKind != 1 )
		{
			fightKind = 1;
			fightPid  = -1;
			mageNpc( npc, CLAWS );
			SetAutocast( true );
		}
	}

	/** Bookkeeping for a player fight that has just started. */
	private void openOn( int index )
	{
		fightKind = 2;
		fightPid  = getPlayerPID( index );
		System.out.println( "[RunitePkMiner] " + getPlayerName( index ) + " (level "
			+ getPlayerCombatLevel( index ) + ") opened at " + getX() + "," + getY() + " - answering." );
	}

	private int fightPlayer( int index )
	{
		if ( fightKind != 2 || fightPid != getPlayerPID( index ) )
		{
			openOn( index );
		}

		/* Remember where this is happening: if they go down, the drop is
		   here and the fight is over before there is time to ask where. */
		lootX = getPlayerX( index );
		lootY = getPlayerY( index );

		/*
		 * Defence and magic, the best of each the account owns. Paralyze is
		 * deliberately not in this list - it does not hold a player, and the
		 * points it burns are points the two prayers that do work want.
		 */
		wantPrayer( DEF_PRAYERS );
		wantPrayer( MAGE_PRAYERS );

		if ( casts() < 1 )
		{
			return panicOut( "out of runes in a player fight" );
		}

		magePlayer( index, CLAWS );
		SetAutocast( true );
		return random( 900, 1200 );
	}

	private int fightNpc( int index )
	{
		if ( fightKind != 1 )
		{
			fightKind = 1;
			fightPid  = -1;
		}

		lootX = -1;
		lootY = -1;

		/*
		 * Paralyze monster holds an npc still, which on a monster that has
		 * to stand next to us to do anything is the whole fight. Only worth
		 * switching on while there are points to spend on it - below a
		 * quarter of the pool the altar is the better use of what is left.
		 */
		if ( prayerFraction() > 0.25D )
		{
			wantPrayer( new int[] { PARALYZE } );
		}
		else
		{
			dropPrayer( PARALYZE );
		}

		if ( casts() < 1 )
		{
			return panicOut( "out of runes in a monster fight" );
		}

		mageNpc( index, CLAWS );
		SetAutocast( true );
		return random( 900, 1200 );
	}

	/**
	 * The player we are fighting, or -1.
	 *
	 * Adjacent, in combat, and not us. In Classic a fight pins both sides to
	 * touching tiles, so one tile is the whole search - anything further out
	 * is somebody else's problem by definition.
	 */
	private int enemyPlayer()
	{
		int count = countPlayers();

		for ( int i = 0; i < count; i++ )
		{
			if ( getPlayerPID( i ) == selfPid )
			{
				continue;
			}

			int dist = distanceTo( getPlayerX( i ), getPlayerY( i ) );

			/*
			 * Distance zero is allowed once we can tell ourselves apart by
			 * pid: two players can stand on one tile, and one who does it
			 * while hitting us is the most engaged target there is.
			 */
			if ( dist > 2 || ( selfPid == -1 && dist < 1 ) )
			{
				continue;
			}

			if ( ! isPlayerInCombat( i ) )
			{
				continue;
			}

			/*
			 * Someone the server has already refused us. Being in combat a
			 * tile away does not make them ours: on the rub row a hobgoblin
			 * opened on the run while a level 90 stood next to it fighting
			 * its own, and the run spent its answer on the player - ten
			 * refusals of "must move to at least level 34 wilderness" -
			 * while the thing actually hitting it went unanswered.
			 */
			if ( System.currentTimeMillis() < shunUntil
				&& shunName != null && shunName.equalsIgnoreCase( getPlayerName( i ) ) )
			{
				continue;
			}

			return i;
		}

		return -1;
	}

	/**
	 * Work out which entry in the player list is us, once.
	 *
	 * This exists because of a fight the run had with itself. Telling the
	 * local player apart by position - distance zero is us - looks obvious
	 * and holds right up until the run is mid-step: getX()/getY() report
	 * the tile being walked onto while the list entry is still a tile
	 * behind, so the run shows up in its own scan at distance one, the
	 * combat flag from the fight it just left is still set, and it opens on
	 * itself. It spent two hundred blood runes casting Claws of Guthix at
	 * its own back on the walk home from a death.
	 *
	 * A pid does not move. Learning it needs one tick standing still with
	 * exactly one entry on this tile, which is the login tick in practice -
	 * and insisting on exactly one is what stops a player sharing the tile
	 * from being learned as us, which would be the same bug pointing the
	 * other way and permanently.
	 */
	private void learnSelf()
	{
		if ( selfPid != -1 || isWalking() )
		{
			return;
		}

		int count = countPlayers();
		int found = -1;

		for ( int i = 0; i < count; i++ )
		{
			if ( distanceTo( getPlayerX( i ), getPlayerY( i ) ) == 0 )
			{
				if ( found != -1 )
				{
					return;   // two of us on the tile: wait for a clearer tick
				}

				found = i;
			}
		}

		if ( found != -1 )
		{
			selfPid = getPlayerPID( found );
		}
	}

	/** The npc we are fighting, or -1. Same one-tile rule. */
	private int enemyNpc()
	{
		int count = countNpcs();
		int best = -1;
		int bestDist = Integer.MAX_VALUE;

		for ( int i = 0; i < count; i++ )
		{
			int dist = distanceTo( getNpcX( i ), getNpcY( i ) );

			if ( dist > 2 )
			{
				continue;
			}

			if ( ! isNpcInCombat( i ) && ! isNpcHpBarVisible( i ) )
			{
				continue;
			}

			if ( dist < bestDist )
			{
				bestDist = dist;
				best = i;
			}
		}

		return best;
	}

	/**
	 * The fight is over.
	 *
	 * A player fight that ends opens the loot window: whatever they were
	 * carrying is on the floor where they stood, and it is on the floor for
	 * everybody, so the next twenty seconds are worth more than the next
	 * swing at a rock.
	 */
	private void endFight()
	{
		if ( fightKind == 2 && lootX != -1 )
		{
			killCount++;
			lootUntil = System.currentTimeMillis() + 20000L;
			System.out.println( "[RunitePkMiner] Player fight over at " + lootX + "," + lootY
				+ " - checking the floor." );
		}

		fightKind = 0;
		fightPid  = -1;

		/*
		 * Nothing has engaged for eight seconds, so there is no fight left
		 * to carry: drop the target, not the switch. An autocaster still
		 * aimed at a player who fled alive would walk after them, and this
		 * run answers attacks rather than starting them. The switch itself
		 * stays on for the whole run, so the settings panel tells the truth
		 * about what the client is doing.
		 */
		ClearAutocastTarget();
	}

	/**
	 * Pick up what a player fight left behind.
	 *
	 * Price is the filter, not a list of ids: anybody who dies here might
	 * have been carrying anything, and a whitelist written today is a
	 * whitelist that misses the one drop worth having. Bones and burnt food
	 * come in under the floor and stay where they are.
	 */
	private int loot()
	{
		if ( System.currentTimeMillis() > lootUntil )
		{
			return 0;
		}

		if ( getEmptySlots() < 1 )
		{
			/* A lobster is cheaper than leaving a platebody on the floor
			   for whoever walks past next. */
			if ( makeRoom() )
			{
				return random( 1200, 1600 );
			}

			lootUntil = 0L;
			note( "Loot on the floor, no room for it and nothing left to eat - banking first." );
			return 0;
		}

		/*
		 * Best first, not first found. The pack has a handful of free slots
		 * and a dead player can leave a dozen things, so taking them in
		 * array order means filling up on their bronze arrows while the
		 * rune platebody sits there. Anything left behind is a reason for
		 * the next person to follow this bot around and collect what it
		 * could not be bothered with.
		 */
		int count = getGroundItemCount();
		int bestId = -1, bestX = -1, bestY = -1;
		long bestWorth = -1L;

		for ( int i = 0; i < count; i++ )
		{
			int id = getGroundItemId( i );

			if ( id <= 0 )
			{
				continue;
			}

			int x = getItemX( i );
			int y = getItemY( i );

			if ( distanceTo( x, y ) > 4 )
			{
				continue;
			}

			long worth = worthTaking( id );

			if ( worth > bestWorth )
			{
				bestWorth = worth;
				bestId = id;
				bestX = x;
				bestY = y;
			}
		}

		if ( bestId == -1 || bestWorth < 1L )
		{
			lootUntil = 0L;
			return 0;
		}

		/*
		 * Keep the window open while there is still something on the floor.
		 * The twenty seconds are there so the run does not stand over an
		 * empty tile forever, not to make it walk away from a pile it is
		 * halfway through.
		 */
		lootUntil = System.currentTimeMillis() + 8000L;
		pickupItem( bestId, bestX, bestY );
		return random( 800, 1100 );
	}

	/* ------------------------------------------------------------ prayers */

	/** Switch on the best of a list the account can actually use. */
	private void wantPrayer( int[] choices )
	{
		if ( getCurrentLevel( PRAYER ) < 1 )
		{
			return;
		}

		for ( int i = 0; i < choices.length; i++ )
		{
			if ( getLevel( PRAYER ) < getPrayerLevel( choices[i] ) )
			{
				continue;
			}

			if ( ! isPrayerEnabled( choices[i] ) )
			{
				enablePrayer( choices[i] );
			}

			/* The best one it owns, and only that one: two prayers from the
			   same column do not stack, they just drain twice as fast. */
			for ( int j = i + 1; j < choices.length; j++ )
			{
				dropPrayer( choices[j] );
			}

			return;
		}
	}

	private void dropPrayer( int prayer )
	{
		if ( isPrayerEnabled( prayer ) )
		{
			disablePrayer( prayer );
		}
	}

	/** Out of combat, nothing should be lit. */
	private void idlePrayers()
	{
		for ( int i = 0; i < ALL_PRAYERS.length; i++ )
		{
			dropPrayer( ALL_PRAYERS[i] );
		}
	}

	private double prayerFraction()
	{
		int max = getLevel( PRAYER );

		if ( max < 1 )
		{
			return 0.0D;
		}

		return (double) getCurrentLevel( PRAYER ) / (double) max;
	}

	/* --------------------------------------------------------------- food */

	/**
	 * Eat at two thirds, not at death's door.
	 *
	 * A lobster is twelve hitpoints and the eat takes a tick the fight does
	 * not pause for, so the margin has to cover a round of whatever is
	 * hitting us. Returns true when a lobster went in.
	 *
	 * Two tiers, and the difference between them matters. Down to two
	 * thirds the run keeps fighting and just tops itself up: disengaging
	 * from a fight it is winning only invites the next thing along. Below
	 * the danger line it stops trading hits first and eats second, because
	 * a lobster is twelve hitpoints against an opponent who may be landing
	 * more than that per round, and eating through that is a slower death
	 * rather than a recovery.
	 */
	/**
	 * Turn a lobster into an empty slot.
	 *
	 * Deliberately wasteful and only called where a slot is worth more than
	 * the healing: standing on loot with a full pack, or beside a rock that
	 * is ready. Eating at full health throws twelve hitpoints away, and a
	 * runite ore or somebody's rune platebody is worth more than twelve
	 * hitpoints the run was not going to use anyway.
	 */
	private boolean makeRoom()
	{
		if ( getEmptySlots() > 0 )
		{
			return false;
		}

		int slot = getInventoryIndex( LOBSTER );

		if ( slot == -1 )
		{
			return false;
		}

		useItem( slot );
		return true;
	}

	private boolean eat()
	{
		int max = getLevel( HITS );
		int cur = getCurrentLevel( HITS );

		if ( max < 1 || cur > ( max * 2 ) / 3 )
		{
			return false;
		}

		int slot = getInventoryIndex( LOBSTER );

		if ( slot == -1 )
		{
			return false;
		}

		/*
		 * Only eat when the whole lobster is used. Twelve hitpoints into a
		 * gap smaller than twelve throws the remainder away, and the food
		 * count is what decides how long a trip can last - there are ten
		 * lobsters in the kit and no way to get more until the bank.
		 */
		if ( max - cur < LOBSTER_HEAL )
		{
			return false;
		}

		useItem( slot );
		return true;
	}

	/**
	 * What a ground item is worth taking for, or 0 to leave it.
	 *
	 * Base price does nearly all of this on its own: rune and adamantite
	 * armour, rune and dragon weapons and every gem amulet price in the
	 * thousands and clear the floor without being named. Naming them would
	 * only mean a list that misses whatever gets added next.
	 *
	 * The exception is stackables. A ground item carries no visible stack
	 * size - there is no API for it - so 500 coins and one coin both price
	 * at 1, and 1000 air runes price at 4. Those are exactly the piles a
	 * dead player leaves, so they are taken on identity rather than price
	 * and sorted below real gear, which is the right order when there are
	 * only a few slots free.
	 *
	 * Quest amulets are deliberately absent: they are untradeable, so they
	 * never reach the floor in the first place.
	 */
	private long worthTaking( int id )
	{
		int price = getItemBasePriceId( id );

		if ( price >= LOOT_MIN )
		{
			return (long) price;
		}

		for ( int i = 0; i < STACKABLES.length; i++ )
		{
			if ( STACKABLES[i] == id )
			{
				/* Below any real piece of gear, above anything ignored. */
				return 1L;
			}
		}

		return 0L;
	}


	/** At or below the danger line: stop trading hits and get out. */
	private boolean hurt()
	{
		int max = getLevel( HITS );

		return max > 0 && getCurrentLevel( HITS ) <= ( max * HP_FLEE_NUM ) / HP_FLEE_DEN;
	}

	/**
	 * Run, and eat on the way.
	 *
	 * FleeCombat() goes out every tick rather than once: the server refuses
	 * to let anybody leave in the first three rounds of a fight, so a single
	 * request is a request that was probably declined. Running also costs a
	 * melee attacker its rhythm - it has to re-close every time - which is
	 * most of what makes this survivable at all.
	 *
	 * The eat rides along in the same tick. A lobster only goes in when the
	 * full twelve hitpoints are usable, so a long chase does not burn the
	 * whole trip's food putting two points back at a time.
	 */
	private int runAndEat()
	{
		if ( fleeAt == 0L || System.currentTimeMillis() - fleeAt > 6000L )
		{
			fleeAt = System.currentTimeMillis();
			System.out.println( "[RunitePkMiner] Down to " + getCurrentLevel( HITS )
				+ " of " + getLevel( HITS ) + " - breaking off and eating, "
				+ getInventoryCount( LOBSTER ) + " lobsters left." );
		}

		/*
		 * Autocast stays on through the retreat, deliberately. Turning it
		 * off was a death sentence: this branch runs on every tick from
		 * forty percent down, so the run spent the whole back half of a
		 * fight with its staff idle, eating and asking to retreat while
		 * the other side kept swinging. A chaser follows, so the retreat
		 * rarely lands and the only thing that ends the fight is damage.
		 * Breaking combat and getting food in is the job here; the casting
		 * carries on by itself.
		 */
		SetAutocast( true );
		FleeCombat();
		eat();

		return random( 600, 900 );
	}

	/**
	 * Death bookkeeping. Not driven by a server line: the server sends
	 * nothing on death at all - "Oh dear! You are dead..." is painted by
	 * the client - so main() calls this when the position jumps to the
	 * Lumbridge respawn from somewhere else, which is the only way to
	 * arrive there from forty tiles away in one tick.
	 */
	private void onDeath()
	{
		deathCount++;
		System.out.println( "[RunitePkMiner] Died - respawning, and the kit is in the bank or on the floor where it happened." );
		fightKind = 0;
		lootUntil = 0L;
		idlePrayers();
	}

	/* The Lumbridge respawn, with a tile of slack each way. */
	private static boolean atRespawn( int x, int y )
	{
		return x >= 118 && x <= 126 && y >= 643 && y <= 652;
	}

	/* --------------------------------------------------------------- rest */

	/**
	 * Sleep off the fatigue, but never in the wilderness.
	 *
	 * The server refuses the bag at wilderness 1 and deeper
	 * (InvActionHandler:1364), so a run that gets fatigued up north has to
	 * carry it home - and a sleep is the one thing that cannot be
	 * interrupted to answer a fight, which is reason enough not to want one
	 * there anyway.
	 */
	private int rest()
	{
		if ( noLevel )
		{
			if ( ! lockNoted && getFatigue() >= 100 )
			{
				lockNoted = true;
				note( "Fatigue at 100% - every level is frozen from here." );
			}

			return 0;
		}

		if ( getFatigue() <= 90 )
		{
			return 0;
		}

		if ( inWild() )
		{
			note( "Fatigued in the wilderness, where the bag is refused - mining on, but not levelling." );
			return 0;
		}

		if ( getInventoryCount( BAG ) < 1 )
		{
			note( "Fatigued with no sleeping bag - still mining, but not levelling." );
			return 0;
		}

		if ( ! isSleeping() )
		{
			useSleepingBag();
		}

		return random( 1000, 2000 );
	}

	/* --------------------------------------------------------------- work */

	/**
	 * The cycle, in the order it happens.
	 *
	 * Guild trip, bank, wilderness trip, bank, repeat. Which half of that
	 * the run is on is a single boolean, and every branch below reads
	 * position first so the boolean only ever decides what to do next rather
	 * than where the run is.
	 */
	private int work()
	{
		if ( ! atBank() )
		{
			banked = false;
		}
		else
		{
			if ( ! banked )
			{
				return useBank();
			}

			/*
			 * Standing at the bank, the window already been and gone, and
			 * the kit still wrong - so the bank does not have it. Walking
			 * back out to a rock without a pickaxe or without a cast is a
			 * loop with extra steps, and so is queueing for the banker
			 * again, so say which item it is and stop.
			 */
			if ( ! kitComplete() )
			{
				System.out.println( "[RunitePkMiner] " + missing()
					+ " - the bank does not have one either. Stopping rather than walking out without it." );
				setAutoLogin( false );
				stopScript();
				return 1000;
			}
		}

		/*
		 * Kit gone anywhere else: nothing else matters until the bank
		 * fixes it - with one exception. The guild half needs nothing but
		 * the pickaxe (the rock is behind a quest door, nobody fights you
		 * there), so a run started at the guild gate with the wilderness
		 * kit still in the bank does that trip first and sorts the kit out
		 * on the way home, which is where it was going anyway. Teleporting
		 * away from a rock you are standing next to, to fetch armour for a
		 * different trip, is how two amulet charges went on nothing.
		 */
		if ( ! kitComplete() )
		{
			if ( nearGuild() && getInventoryCount( PICKAXE ) > 0 )
			{
				wildTrip = false;
				return guild();
			}

			return panicOut( "the kit is incomplete" );
		}

		if ( isBankFull() )
		{
			return goHome();
		}

		if ( inEdgeArrival() )
		{
			return walkRoute( EDGE_ARRIVAL, true );
		}

		/*
		 * The wilderness half, and the one rule it is built around: never
		 * cross the ditch without a charged amulet. Uncharged means the
		 * guild first, where the fountain is - which is the same trip the
		 * run would have done anyway, just out of turn.
		 */
		/*
		 * No claws, no wilderness. The gate is only discoverable by being
		 * refused, so this can flip mid-run - and when it does the run
		 * drops to guild-only rather than walking north defenceless.
		 */
		if ( wildTrip && clawsLocked )
		{
			wildTrip = false;
		}

		if ( wildTrip && heldAmulet() != AMULET_CHARGED )
		{
			note( "Amulet is not charged - taking the guild trip first and dipping it at the fountain." );
			wildTrip = false;
		}

		if ( inWild() )
		{
			return wilderness();
		}

		if ( wildTrip )
		{
			/*
			 * Charged before the ditch, not after it. Two different things
			 * have to be up to go north and both are checked here: the
			 * amulet, which is the way back out, and Charge, which is the
			 * difference between answering an attacker and annoying one.
			 */
			int chargeTick = topUpCharge();

			if ( chargeTick > 0 )
			{
				return chargeTick;
			}

			return travelWild();
		}

		return guild();
	}

	/** Everything that happens north of the ditch. */
	private int wilderness()
	{
		/*
		 * The trip is over when the ore is in hand or the runes are out, and
		 * the way out is one row of the map: walk back down the same list to
		 * wilderness 29 and rub.
		 */
		if ( retreating() )
		{
			if ( getY() >= RUB_ROW )
			{
				return rub();
			}

			return walkRoute( WILD_PATH, false );
		}

		if ( atWildRocks() )
		{
			reachedWork = true;
			return mineWild();
		}

		if ( waitingWild() )
		{
			reachedWork = true;

			/* Both rocks out and we are standing clear of the spiders. */
			note( "Both wilderness rocks are out - waiting west of the spiders for the respawn." );
			return random( 2500, 3500 );
		}

		return walkRoute( WILD_PATH, true );
	}

	/**
	 * Is this trip done with?
	 *
	 * Either rock in hand is enough to go, because the walk home is long
	 * enough that a second rock is worth less than a second trip, and a
	 * full inventory or an empty rune pouch end it whatever has been mined.
	 */
	private boolean retreating()
	{
		if ( casts() < 1 )
		{
			return true;
		}

		if ( getEmptySlots() < 1 )
		{
			return true;
		}

		return ( getInventoryCount( RUNITE_ORE ) >= 2 || bothWildRocksMined() );
	}

	private boolean bothWildRocksMined()
	{
		return getInventoryCount( RUNITE_ORE ) >= 1
			&& rockAt( WILD_ROCK_A_X, WILD_ROCK_A_Y ) == -1
			&& rockAt( WILD_ROCK_B_X, WILD_ROCK_B_Y ) == -1;
	}

	private int mineWild()
	{
		if ( makeRoom() )
		{
			return random( 1200, 1600 );
		}

		int a = rockAt( WILD_ROCK_A_X, WILD_ROCK_A_Y );
		int b = rockAt( WILD_ROCK_B_X, WILD_ROCK_B_Y );

		if ( a != -1 )
		{
			atObject( WILD_ROCK_A_X, WILD_ROCK_A_Y );
			return random( 750, 950 );
		}

		if ( b != -1 )
		{
			atObject( WILD_ROCK_B_X, WILD_ROCK_B_Y );
			return random( 750, 950 );
		}

		/*
		 * Both out. Step west out of the spiders' patch rather than stand on
		 * the rocks waiting to be bitten - the rocks are still in view from
		 * there, so nothing is missed.
		 */
		return walkTile( WILD_WAIT_X, WILD_WAIT_Y );
	}

	/** Standing at the wait tile with nothing yet to mine. */
	private boolean waitingWild()
	{
		if ( getX() != WILD_WAIT_X || getY() != WILD_WAIT_Y )
		{
			return false;
		}

		return rockAt( WILD_ROCK_A_X, WILD_ROCK_A_Y ) == -1
			&& rockAt( WILD_ROCK_B_X, WILD_ROCK_B_Y ) == -1;
	}

	/** Everything that happens on the Taverley side. */
	private int guild()
	{
		/* Upstairs, which is only ever the altar errand. */
		if ( inGuildUpstairs() )
		{
			if ( prayerLow() )
			{
				if ( getX() == GUILD_PRAY_X && getY() == GUILD_PRAY_Y )
				{
					atObject( GUILD_ALTAR_X, GUILD_ALTAR_Y );
					return random( 1500, 2000 );
				}

				return walkRoute( ALTAR_PATH, true );
			}

			if ( getX() == ALTAR_PATH[0][0] && getY() == ALTAR_PATH[0][1] )
			{
				prayerRun = false;
				atObject( GUILD_DOWN_X, GUILD_DOWN_Y );
				return random( 1500, 2000 );
			}

			return walkRoute( ALTAR_PATH, false );
		}

		if ( inCourtyard() )
		{
			/* The dip itself, then straight back out - the courtyard is a
			   dead end and the fountain is the only thing in it. */
			if ( getInventoryCount( AMULET_PLAIN ) > 0 )
			{
				if ( getX() == FOUNTAIN_PATH[FOUNTAIN_PATH.length - 1][0]
					&& getY() == FOUNTAIN_PATH[FOUNTAIN_PATH.length - 1][1] )
				{
					useItemOnObject( AMULET_PLAIN, FOUNTAIN_X, FOUNTAIN_Y );
					return random( 2000, 3000 );
				}

				return walkRoute( FOUNTAIN_PATH, true );
			}

			return walkRoute( FOUNTAIN_PATH, false );
		}

		if ( inGuild() )
		{
			/*
			 * Order matters on the ground floor. The dip comes before the
			 * climb because an uncharged amulet is what the whole wilderness
			 * half is waiting on, and both before the stairs down, because
			 * carrying either errand into the basement means climbing back
			 * up for it.
			 */
			if ( getInventoryCount( AMULET_PLAIN ) > 0 )
			{
				return walkRoute( FOUNTAIN_PATH, true );
			}

			if ( prayerLow() || prayerRun )
			{
				prayerRun = true;

				if ( getX() == GUILD_UP_X && getY() == GUILD_UP_Y )
				{
					atObject( GUILD_LADDER_X, GUILD_LADDER_Y );
					return random( 1500, 2000 );
				}

				return walkRoute( LADDER_PATH, true );
			}
		}

		/* In the basement, at the rock. */
		if ( getX() == GUILD_MINE_X && getY() == GUILD_MINE_Y )
		{
			reachedWork = true;

			if ( guildDone() )
			{
				return goHome();
			}

			return mineGuild();
		}

		if ( guildDone() )
		{
			return goHome();
		}

		return travel( ROUTE_GUILD, true );
	}

	/**
	 * Has the guild half got what it came for?
	 *
	 * One ore. The rock is a quarter-hour respawn and the wilderness pair is
	 * waiting, so a second ore here costs more than it is worth.
	 */
	private boolean guildDone()
	{
		if ( wildTrip )
		{
			return true;
		}

		return getInventoryCount( RUNITE_ORE ) >= 1 || getEmptySlots() < 1 || casts() < 1;
	}

	private int mineGuild()
	{
		if ( makeRoom() )
		{
			return random( 1200, 1600 );
		}

		if ( rockAt( GUILD_ROCK_X, GUILD_ROCK_Y ) != -1 )
		{
			atObject( GUILD_ROCK_X, GUILD_ROCK_Y );
			return random( 750, 950 );
		}

		/*
		 * Fifteen minutes beside one rock, with nothing in the basement that
		 * wants to fight - so this is the one wait the run can spend
		 * standing exactly where the work is. A silent wait that long is
		 * indistinguishable from a hung script, so say so once a minute.
		 */
		note( "Guild rock mined out - waiting on the respawn." );
		return random( 2000, 3000 );
	}

	/* ------------------------------------------------------------ homeward */

	/**
	 * Back to Edgeville, by whichever means the inventory allows.
	 *
	 * A charged amulet skips the entire walk, and it is charged on every
	 * guild trip precisely so that it is charged here. Without one the legs
	 * walk it, which is slow but works.
	 */
	private int goHome()
	{
		if ( atBank() )
		{
			return banked ? random( 1500, 2000 ) : useBank();
		}

		if ( inEdgeArrival() )
		{
			return walkRoute( EDGE_ARRIVAL, true );
		}

		if ( inWild() )
		{
			if ( getY() >= RUB_ROW )
			{
				return rub();
			}

			return walkRoute( WILD_PATH, false );
		}

		if ( heldAmulet() == AMULET_CHARGED && ! inGuildUpstairs() && ! nearEdgeville() )
		{
			return rub();
		}

		if ( inGuildUpstairs() )
		{
			return walkRoute( ALTAR_PATH, false );
		}

		if ( inCourtyard() )
		{
			return walkRoute( FOUNTAIN_PATH, false );
		}

		return travel( ROUTE_GUILD, false );
	}

	private int rub()
	{
		int slot = getInventoryIndex( AMULET_CHARGED );

		if ( slot == -1 )
		{
			return 0;
		}

		rubbing = true;
		useItem( slot );
		return random( 1500, 2000 );
	}

	/**
	 * Abandon whatever this was and get to the bank.
	 *
	 * The protocol for a kit that has gone wrong - no runes, no pickaxe, no
	 * amulet - is the same wherever it is noticed: home by amulet if there
	 * is one, on foot if there is not, and no mining on the way.
	 */
	private int panicOut( String why )
	{
		note( "Heading for the bank: " + why + "." );

		if ( atBank() )
		{
			return banked ? random( 1500, 2000 ) : useBank();
		}

		if ( inWild() && getY() < RUB_ROW )
		{
			return walkRoute( WILD_PATH, false );
		}

		/*
		 * The amulet is for distance. Rubbed from the field north of
		 * Edgeville it lands you in the same field, one charge lighter, and
		 * the next tick rubs again: that is how a charged amulet turned
		 * plain without the run ever leaving Edgeville. Within walking
		 * distance of the bank, walk.
		 */
		if ( heldAmulet() == AMULET_CHARGED && ! nearEdgeville() )
		{
			return rub();
		}

		return goHome();
	}

	/* --------------------------------------------------------------- bank */

	private int useBank()
	{
		int[] banker = getNpcByIdNotTalk( BANKERS );

		/*
		 * getNpcByIdNotTalk sees about sixteen tiles, further than a click
		 * reaches. Without the distance gate the run stands at the door
		 * talking to a banker two rooms away and never arrives.
		 */
		if ( banker[0] != -1 && distanceTo( getNpcX( banker[0] ), getNpcY( banker[0] ) ) <= 4 )
		{
			talkToNpc( banker[0] );
			return random( 3000, 4000 );
		}

		walkTo( EDGE_LEG[0][0], EDGE_LEG[0][1] );
		note( "At the bank, waiting on a banker." );
		return random( 1000, 1500 );
	}

	/**
	 * Deposit the load, then make the kit right, one item a tick.
	 *
	 * Deposits run first because the inventory arrives full: nothing comes
	 * out of the bank until the ore and the loot have gone in.
	 */
	private int doBank()
	{
		int junk = junkSlot();

		if ( junk != -1 )
		{
			int id = getInventoryId( junk );
			deposit( id, getInventoryCount( id ) );
			return random( 1000, 1500 );
		}

		/* Wearables and tools, one at a time, in the order a wilderness trip
		   needs them. */
		int[] singles = { LEGS, BODY, HELM, SHIELD, CAPE, STAFF, PICKAXE, BAG };

		for ( int i = 0; i < singles.length; i++ )
		{
			if ( singles[i] == BAG && noLevel )
			{
				continue;
			}

			if ( getInventoryCount( singles[i] ) < 1 && bankCount( singles[i] ) > 0 )
			{
				withdraw( singles[i], 1 );
				return random( 1000, 1500 );
			}
		}

		/*
		 * The amulet, charged for preference. An uncharged one is still
		 * worth carrying - the guild trip dips it on the way past - but the
		 * wilderness half will not start on one, so a charged amulet in the
		 * bank beats a plain one in hand.
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
				System.out.println( "[RunitePkMiner] No dragonstone amulet held or banked. "
					+ "The guild trip still runs; the wilderness half will not, because the amulet is the way out of it." );
			}
		}
		else
		{
			noAmulet = false;
		}

		int[] stock = { AIR, BLOOD, FIRE, LOBSTER };
		int[] want  = { AIR_STOCK, BLOOD_STOCK, FIRE_STOCK, FOOD_STOCK };

		for ( int i = 0; i < stock.length; i++ )
		{
			int have = getInventoryCount( stock[i] );

			if ( have >= want[i] )
			{
				continue;
			}

			int short_ = want[i] - have;
			int banked = bankCount( stock[i] );

			if ( banked < 1 )
			{
				continue;
			}

			withdraw( stock[i], Math.min( short_, banked ) );
			return random( 1000, 1500 );
		}

		/* Say the bad news once rather than every trip. */
		if ( casts() < CAST_FLOOR )
		{
			if ( ! lowRunes )
			{
				lowRunes = true;
				System.out.println( "[RunitePkMiner] Runes are running out - " + casts()
					+ " casts left and the bank has no more. The wilderness half is not safe on that." );
			}
		}
		else
		{
			lowRunes = false;
		}

		/*
		 * Everything is in. Turn the cycle over once per visit, not once per
		 * tick: isBanking() stays true for a tick or two after closeBank(),
		 * so this branch runs again with nothing left to do, and a flip
		 * sitting outside the guard flips straight back - which reads as a
		 * run that does the wilderness trip twice and never goes to the
		 * guild at all.
		 */
		if ( ! banked )
		{
			banked = true;
			tripCount++;

			/*
			 * Turn the cycle over only when the trip actually got to the
			 * work. The two halves are supposed to alternate evenly, and a
			 * bank visit is not the same thing as a finished trip: a death
			 * recovery, an empty pack or a panic out of the wilderness all
			 * come back here having mined nothing, and flipping on those
			 * hands the next turn to the other half and quietly skips this
			 * one. That is how the guild half went unrun for three cycles.
			 * Not reaching the rock means this half gets another go.
			 *
			 * The order is otherwise strict: guild, wilderness, guild, and
			 * so on, with no half ever skipped for being slow. A rock that
			 * has not come back is waited out where it stands rather than
			 * swapped away from, because a strict cycle paces itself
			 * against the two respawn timers and settles into walking off
			 * them. Jumping to whichever rock happens to be up looks
			 * faster for one trip and then spends the rest of the hour
			 * arriving at both of them early.
			 */
			if ( reachedWork )
			{
				reachedWork = false;
				wildTrip = ! wildTrip;
			}
		}

		closeBank();
		return random( 1000, 1500 );
	}

	/**
	 * The first slot holding something that is not kit.
	 *
	 * Ore, gems, whatever came off a player, whatever a death left behind -
	 * it all reads as junk here and it all goes in, because every slot it
	 * holds is one fewer ore next trip.
	 */
	private int junkSlot()
	{
		int keepAmulet = heldAmulet();

		boolean[] seen = new boolean[ WEARABLES.length ];
		boolean pickSeen = false;
		boolean bagSeen = false;
		boolean amuletSeen = false;

		for ( int slot = 0; slot < 30; slot++ )
		{
			int id = getInventoryId( slot );

			if ( id <= 0 )
			{
				continue;
			}

			if ( id == AIR || id == BLOOD || id == FIRE || id == LOBSTER )
			{
				continue;
			}

			if ( id == PICKAXE && ! pickSeen )
			{
				pickSeen = true;
				continue;
			}

			if ( id == BAG && ! bagSeen && ! noLevel )
			{
				bagSeen = true;
				continue;
			}

			if ( id == keepAmulet && ! amuletSeen )
			{
				amuletSeen = true;
				continue;
			}

			boolean worn = false;

			for ( int w = 0; w < WEARABLES.length; w++ )
			{
				if ( id == WEARABLES[w] && ! seen[w] )
				{
					seen[w] = true;
					worn = true;
					break;
				}
			}

			if ( worn )
			{
				continue;
			}

			return slot;
		}

		return -1;
	}

	/** Is the bank the next stop regardless of what else is going on? */
	private boolean isBankFull()
	{
		return getEmptySlots() < 1;
	}

	/**
	 * The kit, minus the food.
	 *
	 * Lobsters are not on this list on purpose: running out of food is a
	 * reason to be careful, not a reason to abandon a rock that is up. Runes
	 * are, because without a cast there is nothing to fight back with and
	 * the wilderness stops being a mine.
	 */
	private boolean kitComplete()
	{
		if ( getInventoryCount( PICKAXE ) < 1 )
		{
			return false;
		}

		if ( getInventoryCount( STAFF ) < 1 || getInventoryCount( LEGS ) < 1 )
		{
			return false;
		}

		/*
		 * The armour counts as base kit, not as a nicety: going north
		 * without it is the difference between answering a geared attacker
		 * and feeding one. The cape is in here for the same reason - it is
		 * ten magic points of hit chance.
		 */
		if ( getInventoryCount( BODY ) < 1 || getInventoryCount( HELM ) < 1
			|| getInventoryCount( SHIELD ) < 1 || getInventoryCount( CAPE ) < 1 )
		{
			return false;
		}

		if ( casts() < 1 )
		{
			return false;
		}

		return ! ( heldAmulet() == -1 && ! noAmulet );
	}

	/** Which kit item is absent, in the words the log wants. */
	private String missing()
	{
		if ( getInventoryCount( PICKAXE ) < 1 )
		{
			return "No runite pickaxe";
		}

		if ( getInventoryCount( STAFF ) < 1 )
		{
			return "No staff of guthix";
		}

		if ( getInventoryCount( LEGS ) < 1 )
		{
			return "No rune legs";
		}

		if ( getInventoryCount( BODY ) < 1 )
		{
			return "No rune plate mail body";
		}

		if ( getInventoryCount( HELM ) < 1 )
		{
			return "No large rune helmet";
		}

		if ( getInventoryCount( SHIELD ) < 1 )
		{
			return "No rune kite shield";
		}

		if ( getInventoryCount( CAPE ) < 1 )
		{
			return "No guthix cape";
		}

		if ( casts() < 1 )
		{
			return "No runes for Claws of Guthix";
		}

		if ( heldAmulet() == -1 )
		{
			return "No dragonstone amulet";
		}

		return "Kit incomplete";
	}

	/* ---------------------------------------------------------------- kit */

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

	/** How many Claws of Guthix the pouch can still pay for. */
	/** Is Charge still up? */
	private boolean charged()
	{
		return chargedAt != 0L && System.currentTimeMillis() - chargedAt < CHARGE_MS;
	}

	/**
	 * Put Charge up, or say why it cannot be.
	 *
	 * Returns a tick delay when it cast and 0 when there was nothing to do,
	 * so a caller can treat it as one step of whatever it was doing.
	 */
	private int topUpCharge()
	{
		if ( charged() || getLevel( MAGIC ) < CHARGE_LEVEL )
		{
			chargeTries = 0;
			return 0;
		}

		if ( getInventoryCount( AIR ) < 3 || getInventoryCount( BLOOD ) < 3
			|| getInventoryCount( FIRE ) < 3 )
		{
			return 0;
		}

		/*
		 * Give up rather than stand here.
		 *
		 * This is not caution for its own sake: the first version of this
		 * sent the self-cast packet, the server drops that for a ground
		 * spell without a word, and so the run stood beside the Edgeville
		 * bank asking for Charge every second and a half for twenty
		 * minutes with a full kit and nothing in the log. Anything that
		 * waits on an acknowledgement has to be able to stop waiting.
		 * Claws without Charge still hits 0-10, which is worse than going
		 * out charged and better than never going out.
		 */
		if ( chargeTries >= CHARGE_TRIES )
		{
			if ( chargeTries == CHARGE_TRIES )
			{
				chargeTries++;
				System.out.println( "[RunitePkMiner] Charge is not taking after "
					+ CHARGE_TRIES + " casts - going north on an uncharged staff, capped at 18 a cast instead of 25. Worth a look." );
			}

			return 0;
		}

		/*
		 * A cast can simply fail - "The spell fails, you may try again in
		 * 20 seconds" - and the server then refuses every cast for those
		 * twenty seconds. Sending more inside the window spends tries on
		 * nothing: that was four casts gone in four seconds and a trip
		 * north uncharged. Wait the server's own number out instead.
		 */
		if ( System.currentTimeMillis() < chargeRetryAt )
		{
			return 1000;
		}

		if ( ! chargeNoted )
		{
			chargeNoted = true;
			System.out.println( "[RunitePkMiner] Putting Charge up - Claws of Guthix caps at 25 behind it instead of 18." );
		}

		chargeTries++;

		/*
		 * Charge is a ground spell (SpellDef type 6), not a self spell, and
		 * the distinction is invisible until it is wrong: CastOnSelf sends
		 * packet 206 with no target and the server's self-cast path has no
		 * branch for a type 6, so it is dropped in silence. There is no
		 * castOnGround() on the script API - only castOnGroundItem() - so
		 * this reaches past it to the inherited method that sends 232.
		 */
		CastOnGround( CHARGE );
		return random( 1200, 1600 );
	}

	private int casts()
	{
		int air   = getInventoryCount( AIR ) / 4;
		int blood = getInventoryCount( BLOOD ) / 2;
		int fire  = getInventoryCount( FIRE );

		return Math.min( air, Math.min( blood, fire ) );
	}

	/** Dress whatever came out of the bank unworn. */
	private int unwornId()
	{
		for ( int i = 0; i < WEARABLES.length; i++ )
		{
			int slot = getInventoryIndex( WEARABLES[i] );

			if ( slot != -1 && ! isItemEquipped( slot ) )
			{
				return WEARABLES[i];
			}
		}

		return -1;
	}

	private int bankerDist()
	{
		int[] banker = getNpcByIdNotTalk( BANKERS );
		return banker[0] == -1 ? -1 : distanceTo( getNpcX( banker[0] ), getNpcY( banker[0] ) );
	}

	/**
	 * Put on whatever is in the pack and not worn, one item a tick.
	 *
	 * With a give-up: an item the server keeps refusing - a quest gate on
	 * an account that has not done the quest, two pieces that unwield each
	 * other - is left alone after WEAR_TRIES goes, with one line saying so.
	 * Without that, a refused wield is a loop that never notes anything,
	 * and the run stands at the bank until someone looks.
	 */
	private boolean wear()
	{
		for ( int i = 0; i < WEARABLES.length; i++ )
		{
			int slot = getInventoryIndex( WEARABLES[i] );

			if ( slot == -1 || isItemEquipped( slot ) || WEARABLES[i] == wearSkip )
			{
				continue;
			}

			if ( WEARABLES[i] != wearLast )
			{
				wearLast  = WEARABLES[i];
				wearTries = 0;
			}

			if ( ++wearTries > WEAR_TRIES )
			{
				wearSkip = WEARABLES[i];
				note( "Item " + WEARABLES[i] + " will not stay worn after " + WEAR_TRIES
					+ " tries - carrying on without it." );
				continue;
			}

			wearItem( slot );
			return true;
		}

		return false;
	}

	private boolean prayerLow()
	{
		return prayerFraction() < 0.5D && getLevel( PRAYER ) > 0;
	}

	/* ------------------------------------------------------------- travel */

	/**
	 * Move one step along a route of legs, in the given direction.
	 *
	 * The leg and the waypoint are found by position rather than remembered,
	 * so a relogin or a teleport mid-trip needs no recovery code: wherever
	 * the run comes back, it is already on the right leg.
	 *
	 * Exact tile matches win over near ones. Two legs do meet at tiles one
	 * apart - either side of the guild door, either side of the members'
	 * gate - and the obstacle between them is the only way across, so
	 * "whichever waypoint I am standing on" is the only answer that does not
	 * send the run walking into a wall.
	 */
	private int travel( Leg[] route, boolean outward )
	{
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

			if ( bestDist > LONG_HAUL )
			{
				return walkRoute( DEATH_PATH, true );
			}

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
	 * Walk the Edgeville leg as far as the monastery turn-off, then the
	 * altar, then back out.
	 *
	 * The monastery sits six tiles off the outbound leg and its altar is the
	 * only prayer refill on this side of the map, which is why the Edgeville
	 * end of the cycle borrows it rather than having one of its own.
	 */
	private int travelWild()
	{
		if ( prayerLow() && nearMonastery() )
		{
			if ( getX() == MON_PRAY_X && getY() == MON_PRAY_Y )
			{
				atObject( MON_ALTAR_X, MON_ALTAR_Y );
				return random( 1500, 2000 );
			}

			return walkRoute( MON_PATH, true );
		}

		if ( onMonPath() )
		{
			return walkRoute( MON_PATH, false );
		}

		return walkRoute( WILD_PATH, true );
	}

	/**
	 * Click the obstacle at the end of a leg.
	 *
	 * Every one of these answers by putting the player on the far side,
	 * which is a tile on the next leg. There is nothing to confirm and
	 * nothing to wait for beyond the move itself.
	 */
	private int cross( int x, int y, boolean wall )
	{
		/*
		 * An obstacle that does not open looks like nothing at all from
		 * here: the run is on its route, on the right tile, clicking the
		 * right thing, and the position never changes. The rejoin guard
		 * cannot see it, because nothing is lost. Count the clicks instead.
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
			/*
			 * A leg with no obstacle at its end lands here for one tick
			 * every single time it completes: the walk is finished and the
			 * state machine has not picked the next goal yet. Saying so out
			 * loud on that tick buries the one case that matters, which is
			 * the same tile reported over and over. Only speak up once it
			 * has actually repeated.
			 */
			if ( crossTries >= 3 )
			{
				note( "At the end of the route with nothing to cross - waiting." );
			}

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

			/*
			 * Too far for a step to be honoured at all, and this is not
			 * already the walk home: get back within reach first and let
			 * the trip pick itself up at the bank.
			 */
			if ( nearestDist > LONG_HAUL && path != DEATH_PATH )
			{
				return walkRoute( DEATH_PATH, true );
			}

			next = nearest;
		}
		else
		{
			lostSince = -1L;
			next = forward
				? Math.min( nearest + 1, path.length - 1 )
				: Math.max( nearest - 1, 0 );
		}

		return walkTile( path[next][0], path[next][1] );
	}

	/**
	 * Ask for one step and watch whether it happens.
	 *
	 * Same silence the obstacle counter guards against, one layer out: a
	 * waypoint that cannot be reached from here - behind a shut door, across
	 * a wall a zone test got wrong - is walked at forever from a standstill,
	 * and every other guard is happy because the run is on its route and
	 * within slack. Watch the position instead of the intent.
	 */
	private int walkTile( int x, int y )
	{
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
				+ " towards " + x + "," + y + " and the position never changed. Needs a human." );
		}

		walkTo( x, y );
		return random( 1000, 1500 );
	}

	/* --------------------------------------------------------------- zones */

	private boolean atBank()
	{
		return getX() >= 212 && getX() <= 220 && getY() >= 447 && getY() <= 453;
	}

	/**
	 * North of Edgeville, where the amulet drops the run.
	 *
	 * Stops short of the bank's own zone at x 212 so the two never both
	 * claim a tile.
	 */
	private boolean inEdgeArrival()
	{
		return getX() >= 190 && getX() <= 211 && getY() >= 430 && getY() <= 452;
	}

	/**
	 * North of the ditch.
	 *
	 * Rows, not a zone: everything this script does up there is decided by
	 * how deep the row is, and x only comes into it to keep the dungeon
	 * planes and the far side of the map out.
	 */
	private boolean inWild()
	{
		return getY() < WILD_ROW && getY() >= 140 && getX() >= 195 && getX() <= 300;
	}

	private boolean atWildRocks()
	{
		return getX() >= 254 && getX() <= 262 && getY() >= 152 && getY() <= 162;
	}

	/**
	 * Inside the guild building or its courtyard.
	 *
	 * Stops at row 440, not 441. The guild door is a direction-0 wall at
	 * 372,441, which means it stands between 372,441 and 372,440: row 441 is
	 * the mountain path outside, row 440 is the first tile in. Letting this
	 * zone have 441 makes the run believe it is already inside while it is
	 * still on the doormat, so it walks to a tile behind a shut door, gets
	 * nowhere, and says nothing about it.
	 */
	private boolean inGuild()
	{
		return getX() >= 366 && getX() <= 385 && getY() >= 432 && getY() <= 440;
	}

	/** The walled courtyard west of the guild's inner archway. */
	private boolean inCourtyard()
	{
		return getX() >= 378 && getX() <= 385 && getY() >= 432 && getY() <= 440;
	}

	/**
	 * Close enough to the guild that the trip is already most of the way
	 * done: the Taverley approach, the door, the ground floor and the mine.
	 * Measured from the guild door, wide enough to take in a run started on
	 * the road outside.
	 */
	private boolean nearGuild()
	{
		return ! inWild() && ( distanceTo( 372, 441 ) <= 40 || inGuild() || inCourtyard()
			|| inGuildUpstairs() || ( getY() >= 3200 && getY() <= 3300 ) );
	}

	/** Edgeville and the field the amulet drops the run in: bank by foot from here. */
	private boolean nearEdgeville()
	{
		return inEdgeArrival() || atBank()
			|| ( distanceTo( EDGE_LEG[0][0], EDGE_LEG[0][1] ) <= 40 && ! inWild() );
	}

	/** The guild's upper floor, which holds the altar and nothing else. */
	private boolean inGuildUpstairs()
	{
		return getX() >= 364 && getX() <= 382 && getY() >= 1374 && getY() <= 1388;
	}

	/** Close enough to the monastery turn-off for the detour to be cheap. */
	private boolean nearMonastery()
	{
		return getY() >= 452 && getY() <= 470 && getX() >= 218 && getX() <= 232;
	}

	private boolean onMonPath()
	{
		for ( int i = 1; i < MON_PATH.length; i++ )
		{
			if ( getX() == MON_PATH[i][0] && getY() == MON_PATH[i][1] )
			{
				return true;
			}
		}

		return false;
	}

	/** Index of a rock still standing on that tile, or -1. */
	private int rockAt( int x, int y )
	{
		int id = getObjectIdFromCoords( x, y );

		for ( int i = 0; i < RUNITE_ROCKS.length; i++ )
		{
			if ( id == RUNITE_ROCKS[i] )
			{
				return id;
			}
		}

		return -1;
	}

	/* ---------------------------------------------------------------- menus */

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
	/** The first number in a server line, or 20 when there is none. */
	/**
	 * The player named in a wilderness-gap refusal: "You must move to at
	 * least level 34 wilderness to attack Bot2!" or "Bot2 is not in high
	 * enough wilderness for you to attack!".
	 */
	private static String attackRefusedName( String line )
	{
		int at = line.indexOf( "to attack " );

		if ( at >= 0 )
		{
			String rest = line.substring( at + 10 );
			int end = rest.indexOf( '!' );
			return end > 0 ? rest.substring( 0, end ) : rest;
		}

		int is = line.indexOf( " is not in high enough" );

		if ( is > 0 )
		{
			String head = line.substring( 0, is );
			int sp = head.lastIndexOf( '@' );
			return sp >= 0 ? head.substring( sp + 1 ).trim() : head.trim();
		}

		return null;
	}

	private static int secondsIn( String line )
	{
		int value = -1;

		for ( int i = 0; i < line.length(); i++ )
		{
			char c = line.charAt( i );

			if ( c >= '0' && c <= '9' )
			{
				value = ( value < 0 ? 0 : value * 10 ) + ( c - '0' );
			}
			else if ( value >= 0 )
			{
				break;
			}
		}

		return value < 0 ? 20 : value;
	}

	private void note( String message )
	{
		long now = System.currentTimeMillis();

		if ( now - noteTime < 60000L )
		{
			return;
		}

		noteTime = now;
		System.out.println( "[RunitePkMiner] " + message );
	}

	/**
	 * Off the route, and for how long.
	 *
	 * The run rejoins by walking to the nearest waypoint, which handles
	 * nearly everything - a death, a stray click, a teleport. What it cannot
	 * handle is being somewhere the waypoint is unreachable from, and ten
	 * minutes of trying is where that stops being a hiccup.
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
			System.out.println( "[RunitePkMiner] STUCK: " + mins + " min off the route at "
				+ getX() + "," + getY() + " - nearest waypoint is " + dist + " tiles away. Needs a human." );
		}
		else
		{
			System.out.println( "[RunitePkMiner] Off the route at " + getX() + "," + getY()
				+ " - rejoining, nearest waypoint " + dist + " tiles away." );
		}
	}

	@Override
	public void onServerMessage( String str )
	{
		String low = str.toLowerCase( Locale.ENGLISH );

		if ( DEBUG )
		{
			System.out.println( "[RunitePkMiner] server< " + str );
		}

		if ( low.contains( "under attack" ) )
		{
			/*
			 * The only line the server sends that means "this fight is
			 * yours". Everything the combat code does hangs off it, because
			 * proximity cannot tell our fight from the one next to it.
			 */
			attackedAt = System.currentTimeMillis();
		}
		else if ( low.contains( "charged with magical power" ) )
		{
			chargedAt = System.currentTimeMillis();
			chargeTries = 0;
		}
		else if ( low.contains( "the spell fails" ) || low.contains( "must wait another" ) )
		{
			/* Only Charge is cast from outside a fight, so a refusal
			   while there is no fight is a refusal of Charge. */
			if ( ! inCombat() )
			{
				chargeRetryAt = System.currentTimeMillis() + secondsIn( low ) * 1000L + 500L;

				if ( low.contains( "must wait another" ) && chargeTries > 0 )
				{
					chargeTries--;   // never reached the server; not a try
				}
			}
		}
		else if ( low.contains( "must move to at least level" ) || low.contains( "is not in high enough wilderness" ) )
		{
			/*
			 * The wilderness level gap, and it cuts both ways - they cannot
			 * attack us either - so whatever hit us was not this player.
			 * Shun them long enough for the real attacker to be found.
			 */
			shunName  = attackRefusedName( str );
			shunUntil = System.currentTimeMillis() + 30000L;
			fightKind = 0;
		}
		else if ( low.contains( "manage to obtain some" ) )
		{
			oreCount++;
		}
		else if ( low.contains( "standing here" ) )
		{
			moveTime = System.currentTimeMillis() + random( 1500, 1800 );
		}
		else if ( low.contains( "inside the mage arena" ) )
		{
			/*
			 * The god-spell gate. Claws of Guthix only casts outside the
			 * arena once this character has 100 in-arena casts of it on
			 * record (SpellHandler.checkGodSpellGate), and that unlock is
			 * per god and permanent. Without it every cast is refused and
			 * the run has no weapon at all - which matters most in the one
			 * place it cannot afford to be unarmed.
			 *
			 * So this is not a warning, it is a route change: the wilderness
			 * half is off the table until the spell is earned, and the run
			 * keeps to the guild rock, where nothing attacks and nothing
			 * takes the kit on death.
			 */
			if ( ! clawsLocked )
			{
				clawsLocked = true;
				System.out.println( "[RunitePkMiner] Claws of Guthix is not unlocked on this character - 100 casts inside the Mage Arena are needed first." );
				System.out.println( "[RunitePkMiner] Staying on the guild rock until then. The wilderness without a weapon is just a donation." );
			}
		}
		else if ( low.contains( "power emanating" ) )
		{
			System.out.println( "[RunitePkMiner] Amulet charged at the fountain." );
		}
		else if ( low.contains( "you have been killed" ) || low.contains( "oh dear" ) )
		{
			onDeath();
		}
		else if ( low.contains( "you can't reach that" ) )
		{
			moveTime = System.currentTimeMillis() + random( 1500, 1800 );
		}
	}

	@Override
	public void paint()
	{
		final int orange = 0xFFD900;
		final int white  = 0xFFFFFF;
		final int red    = 0xFF3030;

		int x = 315;
		int y = 20;

		long secs = ( ( System.currentTimeMillis() - ( startTime > 0L ? startTime : System.currentTimeMillis() ) ) / 1000L ) + 1;

		int gained = getXpForLevel( MINING ) - initialXP;

		String mode;

		if ( inWild() )
		{
			mode = "wilderness";
		}
		else if ( wildTrip )
		{
			mode = "to wilderness";
		}
		else
		{
			mode = "guild";
		}

		drawString( "RunitePkMiner - " + mode, x, y, 1, orange );
		y += 15;
		drawString( "Runtime: " + ( secs / 3600 ) + ":" + String.format( "%02d", ( secs % 3600 ) / 60 )
			+ ":" + String.format( "%02d", secs % 60 ), x, y, 1, white );
		y += 15;
		drawString( "Ore: " + oreCount + " in " + tripCount + " trips - "
			+ (int) ( ( oreCount * 3600L ) / secs ) + "/hr", x, y, 1, white );
		y += 15;
		drawString( "XP: " + gained + " - " + (int) ( ( gained * 3600L ) / secs ) + " XP/Hr", x, y, 1, white );
		y += 15;
		drawString( "Casts: " + casts() + "  Food: " + getInventoryCount( LOBSTER ), x, y, 1, white );
		y += 15;
		drawString( "Kills: " + killCount + "  Deaths: " + deathCount, x, y, 1,
			deathCount > 0 ? red : white );
	}
}
