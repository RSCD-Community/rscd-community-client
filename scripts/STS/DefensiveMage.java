/**
 * DefensiveMage -- trains defense and magic at once without ever feeding the
 * kill to the wrong school.
 *
 * The engine pays combat experience for a kill entirely by what lands the
 * final blow: a melee blow splits the pot by fight mode (defensive = 3/4 to
 * defense, 1/4 to hits), a spell collects nothing beyond its own per-cast
 * experience. So this script casts at the target while its health is high --
 * banking magic experience per cast, and banking defensive melee rounds the
 * whole time, because the target closes in and the fight runs itself -- and
 * the moment the target's known HP is within the spell's max hit plus our
 * own melee max (the fight keeps swinging while a cast is in flight, so
 * both can land back to back) it stops casting dead and lets melee finish,
 * so the kill pot always lands in defense and hits.
 *
 * The client only learns an npc's current HP from damage reports (NpcHits
 * returns -1 until something has hit it), so the first cast at a fresh spawn
 * flies blind. That is safe as long as the npc's max HP is above the spell's
 * max hit, which the script checks before it ever casts.
 *
 * Spell max hits are the server's own: calcSpellHit caps at
 * aggressiveLvl * 70/100 + 1, equipment shifts the weighting but never the
 * ceiling. The food table is the server's ItemEdibleHeals with the kebab
 * left out (it rolls dice, no fixed heal) and the zero-heal novelty items
 * dropped (eating them would fit any deficit and heal nothing, forever).
 *
 * Cutoffs: at the defense cutoff the script stops outright; at the magic
 * cutoff (or out of runes) it goes melee-only and keeps going until the
 * defense cutoff lands. Fight mode is re-pinned to defensive every pass, and
 * the built-in autocast is forced off so it cannot steal a killing blow.
 *
 * The Crafting Guild cow pasture gets special door discipline. Its fence
 * line runs between y600 and y601, so a cow at y < 601 is loose outside,
 * where the nearest-cow click rains red Xs against the rails forever. When
 * the quarry is cows and the gate at (352,600) is in range: a stray outside
 * gets the gate opened, chased down and killed out there; with no strays
 * left the script comes back through and shuts the gate behind it, and an
 * open gate with nothing loose is closed on sight. Cows only leak out while
 * the gate stands open, so kept shut the pen keeps its stock.
 */
public class DefensiveMage extends Methods {
   public DefensiveMage(mudclient mc) { super(mc); }

   private static final int STAT_DEFENSE = 1, STAT_STRENGTH = 2, STAT_HITS = 3,
         STAT_PRAYER = 5, STAT_MAGIC = 6;
   private static final int MODE_DEFENSIVE = 3;
   private static final int SLEEPING_BAG = 1263;
   private static final int BONES = 20;
   private static final int BONE_REACH = 4;  // tiles; kills happen on our tile
   private static final int FATIGUE_SLEEP_AT = 76;   // "exceeds 75%"
   private static final int MIN_HP_PERCENT = 30;     // hard floor: break off and eat below this
   private static final int REENGAGE_HP_PERCENT = 50; // don't start a fight below this

   /* The Crafting Guild cow pasture. Gate 59 is the open state ("close" is
      its second option), 60 the closed one ("open" is its first). The fence
      sits between y600 and y601: y < 601 is outside. */
   private static final int COW = 6;
   private static final int GATE_X = 352, GATE_Y = 600;
   private static final int GATE_OPEN = 59, GATE_CLOSED = 60;
   private static final int PASTURE_Y = 601;       // y >= this is inside the pen
   private static final int PASTURE_RANGE = 25;    // gate rules only near this pasture

   /* The sixteen elemental combat spells, in cast-menu order. */
   private static final String[] SPELL_NAMES = {
      "Wind strike", "Water strike", "Earth strike", "Fire strike",
      "Wind bolt", "Water bolt", "Earth bolt", "Fire bolt",
      "Wind blast", "Water blast", "Earth blast", "Fire blast",
      "Wind wave", "Water wave", "Earth wave", "Fire wave" };
   private static final int[] SPELL_ID  = { 0, 2, 4, 6, 8, 11, 14, 17, 20, 23, 27, 32, 38, 40, 44, 46 };
   private static final int[] SPELL_REQ = { 1, 5, 9, 13, 17, 23, 29, 35, 41, 47, 53, 59, 62, 65, 70, 75 };
   private static final int[] SPELL_MAX = { 1, 2, 3, 3, 4, 5, 5, 6, 7, 8, 8, 9, 10, 10, 11, 12 };

   /* Server ItemEdibleHeals, heal >= 1, sorted by heal ascending. */
   private static final int[] FOOD_ID = {
      18, 228, 765, 855, 863, 871, 873, 885, 900,
      179, 249, 319, 320, 350, 352, 749, 856, 857, 858, 859, 860, 861, 862, 864, 865, 897, 1245,
      132, 258, 262, 337, 896,
      138, 259, 261, 330, 333, 335, 355, 1061,
      257, 263, 332, 334, 336, 362,
      553,
      326, 328, 359, 551, 911, 914, 954, 957,
      327, 329, 364, 912, 913, 955, 956, 1103, 1269,
      346, 357,
      367, 422, 677, 750, 751,
      325, 901, 902, 904, 905, 906, 944, 945, 947, 948, 949,
      373, 908, 909, 951, 952,
      555,
      370, 590,
      907, 910, 950, 953,
      709, 923, 924, 1102,
      546, 1191, 1193 };
   private static final int[] FOOD_HEAL = {
      1, 1, 1, 1, 1, 1, 1, 1, 1,
      2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
      3, 3, 3, 3, 3,
      4, 4, 4, 4, 4, 4, 4, 4,
      5, 5, 5, 5, 5, 5,
      6,
      7, 7, 7, 7, 7, 7, 7, 7,
      8, 8, 8, 8, 8, 8, 8, 8, 8,
      9, 9,
      10, 10, 10, 10, 10,
      11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
      12, 12, 12, 12, 12,
      13,
      14, 14,
      15, 15, 15, 15,
      19, 19, 19, 19,
      20, 21, 23 };

   private int npcId, spell, spellMax, defCut, magCut, prayCut;
   private int enemy3Rounds;
   private int boneFailX = -1, boneFailY = -1, boneFails;
   private boolean castingDone, outOfFood, recovering, buryBones, killOnCast;
   private long lastCast;
   private long defExpStart, magExpStart, prayExpStart;

   public void MainBody(String[] args) {
      npcId = StrToInt(GetInput("Npc id to fight?"));
      if (!NpcAttackable(npcId)) {
         ShowMessage("@red@Npc " + npcId + " (" + NpcName(npcId) + ") is not attackable.");
         return;
      }
      String[] menu = new String[SPELL_NAMES.length];
      for (int i = 0; i < menu.length; i++) {
         menu[i] = SPELL_NAMES[i] + " (lvl " + SPELL_REQ[i] + ", max hit " + SPELL_MAX[i] + ")";
      }
      int pick = GetOption("Cast which spell?", menu);
      if (pick < 0) { return; }
      spell = SPELL_ID[pick];
      spellMax = SPELL_MAX[pick];
      defCut = StrToInt(GetInput("Stop the script at what defense level?"));
      magCut = StrToInt(GetInput("Stop casting at what magic level?"));
      buryBones = GetOption("Bury the bones?", new String[]{"yes, until a prayer target", "no"}) == 0;
      /* Side casts only -- the active melee partner always keeps its
         handoff line, so the kill pot still lands in defense. */
      killOnCast = GetOption("May side casts kill their npc?",
            new String[]{"no - leave them for melee (classic)", "yes - cast freely"}) == 1;
      if (buryBones) {
         prayCut = StrToInt(GetInput("Stop burying at what prayer level?"));
      }

      if (GetMaxLvl(STAT_MAGIC) < SPELL_REQ[pick]) {
         ShowMessage("@red@Your magic (" + GetMaxLvl(STAT_MAGIC) + ") can't cast " + SPELL_NAMES[pick] + " yet.");
         return;
      }
      castingDone = false;
      if (NpcMaxHits(npcId) <= spellMax) {
         /* A full-health target inside one max hit: the blind first cast
            could be the killing blow, so this pairing never casts at all. */
         ShowMessage("@yel@" + NpcName(npcId) + " only has " + NpcMaxHits(npcId)
               + "hp, inside " + SPELL_NAMES[pick] + "'s max hit -- melee only.");
         castingDone = true;
      }
      /* What the enemy can deal during the server's 3-round retreat lock,
         from its own max-hit formula (npcs carry weapon power 1 and the
         controlled-style bonus of 1). Retreat is impossible inside those
         rounds, so a fight is only ever started with more health than this. */
      int npcStr = org.rscdaemon.client.entityhandling.EntityHandler.getNpcDef(npcId).strength;
      enemy3Rounds = 3 * (int)((npcStr + 1.0) * (1.0 * 0.00175 + 0.1) + 1.05);
      if (GetMaxLvl(STAT_HITS) <= enemy3Rounds) {
         ShowMessage("@red@" + NpcName(npcId) + " can deal " + enemy3Rounds
               + " damage in the 3 rounds you cannot retreat -- your max hp of "
               + GetMaxLvl(STAT_HITS) + " can never safely start that fight.");
         return;
      }
      defExpStart = GetExp(STAT_DEFENSE);
      magExpStart = GetExp(STAT_MAGIC);
      prayExpStart = GetExp(STAT_PRAYER);
      ShowMessage("@gre@DefensiveMage: " + NpcName(npcId) + " / " + SPELL_NAMES[pick]
            + ", defense stops at " + defCut + ", magic rests at " + magCut + ".");

      while (Running()) {
         if (GetMaxLvl(STAT_DEFENSE) >= defCut) {
            report("@red@Defense cutoff " + defCut + " reached -- stopping.");
            return;
         }
         if (!castingDone && GetMaxLvl(STAT_MAGIC) >= magCut) {
            report("@yel@Magic cutoff " + magCut + " reached -- melee only from here.");
            castingDone = true;
         }
         if (!castingDone && !HasRunesForSpell(spell)) {
            report("@yel@Out of runes -- melee only from here.");
            castingDone = true;
         }
         if (GetMode() != MODE_DEFENSIVE) {
            SetMode(MODE_DEFENSIVE);
         }
         if (GetAutocast()) {
            SetAutocast(false);
         }
         eat();
         if (outOfFood) {
            if (InCombat()) {
               /* Never end the script while a fight is live -- keep working
                  on the escape until it takes, then stop. */
               retreat();
               Wait(650);
               continue;
            }
            report("@red@Below " + MIN_HP_PERCENT + "% health with no food left -- logging out.");
            /* Ending the script is not enough on an unattended run: the
               character would stand here logged in, and this pen has
               aggressive neighbours. Auto-login first, or the client undoes
               the logout and puts us right back. LogOut() honours the same
               combat/10-second rule the button does, so keep asking until
               the server lets go; the forced packet is the last resort. */
            AutoLogin(false);
            for (int tries = 0; tries < 30 && LoggedIn(); tries++) {
               LogOut();
               Wait(1000);
            }
            if (LoggedIn()) {
               ForceLogOut();
            }
            return;
         }
         if (recovering) {
            /* Touching the floor arms this; starting a fresh fight re-arms
               the server's 3-round retreat lock, so no attacking until
               health is well clear of the line -- not one point over it. */
            if (GetCurLvl(STAT_HITS) * 100 >= GetMaxLvl(STAT_HITS) * REENGAGE_HP_PERCENT) {
               recovering = false;
            } else if (!InCombat()) {
               Wait(650);
               continue;
            }
         }
         if (!InCombat() && GetFatigue() >= FATIGUE_SLEEP_AT) {
            int bag = GetItemPos(SLEEPING_BAG);
            if (bag != -1) {
               UseItem(bag);
               Wait(2500);
               continue;
            }
         }
         if (buryBones && !InCombat()) {
            /* Kill, grab the bones, bury, carry on -- until the prayer
               target. Strictly out of combat: picking up sends a walk, and
               mid-fight the server reads any walk as running away. */
            if (GetMaxLvl(STAT_PRAYER) >= prayCut) {
               /* No ShowMessage here: it blocks until somebody presses
                  Enter, and mid-run there is nobody. The end-of-run summary
                  reports the prayer xp either way. */
               buryBones = false;
            } else {
               int held = GetItemPos(BONES);
               if (held != -1) {
                  UseItem(held);
                  Wait(1200);
                  continue;
               }
               int[] ground = GetItemById(BONES);
               /* DistanceTo is a straight line and the cow pen is fenced: a
                  pile 3 tiles away on the wrong side of the rails is visible
                  but unwalkable, and clicking it forever just rains red Xs.
                  IsReachable asks the pathfinder instead, and the fail
                  counter abandons any pile that still won't come after
                  three honest tries. */
               if (ground[0] != -1 && DistanceTo(ground[1], ground[2]) <= BONE_REACH
                     && IsReachable(ground[1], ground[2])
                     && !(ground[1] == boneFailX && ground[2] == boneFailY && boneFails >= 3)) {
                  PickupItem(ground[1], ground[2], BONES);
                  Wait(1000 + 400 * DistanceTo(ground[1], ground[2]));
                  if (GetItemPos(BONES) == -1) {
                     if (ground[1] == boneFailX && ground[2] == boneFailY) {
                        boneFails++;
                     } else {
                        boneFailX = ground[1]; boneFailY = ground[2]; boneFails = 1;
                     }
                  } else {
                     boneFails = 0;
                  }
                  continue;
               }
            }
         }
         if (!InCombat() && npcId == COW && DistanceTo(GATE_X, GATE_Y) <= PASTURE_RANGE
               && tendGate()) {
            Wait(650);
            continue;
         }
         if (!InCombat() && GetCurLvl(STAT_HITS) <= enemy3Rounds) {
            /* Starting a fight arms the server's 3-round retreat lock, and
               the enemy can deal enemy3Rounds damage inside it -- engaging
               below that is betting your life on its dice. Eat passes above
               already ran; if food is short, standing here regenerates. */
            Wait(650);
            continue;
         }
         int target = findTarget();
         if (target == -1) {
            if (InCombat()) {
               /* In a fight we did not pick -- an aggressive npc jumped us.
                  Waiting it out on defensive is how you die to a hobgoblin;
                  work the escape instead, whatever health is at. */
               retreat();
            }
            Wait(650);
            continue;
         }
         int hp = NpcHits(target);
         /* The defensive melee fight keeps running while a cast is in
            flight, so between the hp read and the spell landing our own
            blow can land too. The handoff line is therefore the spell's max
            PLUS our melee max -- the worst the pair can do together. */
         int ourMax = (int)(GetCurLvl(STAT_STRENGTH)
               * (GetArmourStats(2) * 0.00175 + 0.1) + 1.05);
         if (castingDone || (hp != -1 && hp <= spellMax + ourMax)) {
            /* Close enough to die to a cast: melee owns it from here. The
               fight usually already exists (the target walked over after the
               first cast); if not, start it. The melee rounds themselves are
               dead time for magic, so they go on softening the next victim. */
            if (!InCombat()) {
               AttackNpc(target);
            } else if (!castingDone) {
               sideCast();
            }
            Wait(650);
         } else {
            /* Server enforces 1200ms between casts plus the round trip. */
            if (GetMillis() - lastCast >= 1500) {
               CastOnNpc(spell, target);
               lastCast = GetMillis();
            }
            Wait(300);
         }
      }
   }

   /**
    * The npc we are already fighting, before any other: the engaged pair
    * share a tile in the combat sprites, so the one matching npc on our own
    * tile is our opponent. Otherwise the nearest free one.
    */
   private int findTarget() {
      if (InCombat()) {
         int mx = GetX(), my = GetY();
         for (int i = 0; i < CountNpcs(); i++) {
            if (NpcId(i) == npcId && NpcInCombat(i) && NpcX(i) == mx && NpcY(i) == my) {
               return i;
            }
         }
         /* In combat but not with our npc -- the caller treats this as an
            ambush and retreats. */
         return -1;
      }
      if (npcId == COW && DistanceTo(GATE_X, GATE_Y) <= PASTURE_RANGE) {
         /* Strays first: they are the ones that despawn unfought and the
            ones the plain nearest-cow scan clicks at through the fence.
            Only once the gate is open do they turn reachable; until then
            tendGate is already working on that. */
         int stray = strayCow();
         if (stray != -1 && IsReachable(NpcX(stray), NpcY(stray))) {
            return stray;
         }
      }
      int[] found = GetNpcById(npcId);
      return found[0];
   }

   /** Nearest free cow loose outside the pasture fence, or -1. */
   private int strayCow() {
      int best = -1, bestDist = 99;
      for (int i = 0; i < CountNpcs(); i++) {
         if (NpcId(i) != COW || NpcInCombat(i) || NpcY(i) >= PASTURE_Y) {
            continue;
         }
         int d = DistanceTo(NpcX(i), NpcY(i));
         if (d < bestDist) {
            best = i;
            bestDist = d;
         }
      }
      return best;
   }

   /** Pasture door discipline, out of combat only. Returns true when it
    *  acted this pass and the main loop should come back around.
    *
    *  With a stray loose: make sure the gate is open (opening it from
    *  inside first if need be) so the stray is reachable -- the chase and
    *  the kill are the main loop's normal business once findTarget starts
    *  preferring it. With no strays: come home through the gate if we are
    *  the one outside, and shut it once we are in. An open gate with
    *  nothing loose gets closed on sight, whoever left it that way. */
   private boolean tendGate() {
      boolean inside = GetY() >= PASTURE_Y;
      if (strayCow() != -1) {
         if (inside && IsObjectAt(GATE_CLOSED, GATE_X, GATE_Y)) {
            AtObject(GATE_X, GATE_Y);      // "open"
            Wait(1500);
            return true;
         }
         return false;
      }
      if (!inside) {
         if (IsObjectAt(GATE_CLOSED, GATE_X, GATE_Y)) {
            AtObject(GATE_X, GATE_Y);      // "open" -- somebody shut us out
            Wait(1500);
            return true;
         }
         WalkToWait(GATE_X, PASTURE_Y);
         return true;
      }
      if (IsObjectAt(GATE_OPEN, GATE_X, GATE_Y)) {
         AtObject2(GATE_X, GATE_Y);        // "close"
         Wait(1500);
         return true;
      }
      return false;
   }

   /**
    * Health has a hard floor at MIN_HP_PERCENT, and the server will not let
    * you eat while fighting -- so at or below the floor in combat the script
    * breaks off first with a ground click right where it stands (the server
    * refuses retreat until the opponent has made 3 hits, so it retries every
    * pass until it takes), then eats in the same pass the moment combat
    * breaks: the biggest food carried, immediately -- wasted heal beats
    * nibbling at the line on a small hits level. Above the floor, and only
    * out of combat, it is best-fit feeding:
    * the biggest heal that fits inside the missing health, so nothing is
    * wasted -- down 5 with a 20-heal and a 3-heal in the bag eats the 3.
    * Below the floor with no food left the script retreats and stops:
    * standing still regenerates, re-attacking would not.
    */
   private void eat() {
      int missing = GetMaxLvl(STAT_HITS) - GetCurLvl(STAT_HITS);
      if (missing <= 0) {
         return;
      }
      boolean floored = GetCurLvl(STAT_HITS) * 100 <= GetMaxLvl(STAT_HITS) * MIN_HP_PERCENT;
      if (floored) {
         recovering = true;
      }
      if (InCombat() && !floored) {
         /* A fight above the floor runs its course. */
         return;
      }
      /* Below the engage line the script cannot fight anyway, so the only
         job is getting health back over it. No waiting on the client's
         combat flag either -- its combatTimer is the health bar's linger
         timer and stays up seconds after the server has freed us, and the
         server refuses (without consuming) an eat it isn't ready for. So:
         ask to flee, try to eat, small delay, repeat. The first attempt
         after the server frees us is the one that lands. */
      while (GetCurLvl(STAT_HITS) <= enemy3Rounds) {
         if (InCombat()) {
            FleeCombat();
         }
         int pos = bestFood(GetMaxLvl(STAT_HITS) - GetCurLvl(STAT_HITS));
         if (pos == -1) {
            if (!InCombat()) {
               outOfFood = true;
               return;
            }
         } else {
            UseItem(pos);
         }
         Wait(1000);
      }
      /* Above the engage line it is tidy topping-up only: the biggest heal
         that fits inside the missing health, nothing wasted. */
      if (InCombat()) {
         return;
      }
      missing = GetMaxLvl(STAT_HITS) - GetCurLvl(STAT_HITS);
      for (int i = FOOD_ID.length - 1; i >= 0; i--) {
         if (FOOD_HEAL[i] > missing) {
            continue;
         }
         int pos = GetItemPos(FOOD_ID[i]);
         if (pos != -1) {
            UseItem(pos);
            Wait(1300);
            return;
         }
      }
   }

   /** The food position to eat when healing matters more than tidiness:
    *  the biggest heal that fits inside the deficit, or failing that the
    *  smallest food carried (everything overheals, so least waste wins).
    *  -1 when no recognised food is carried at all. */
   private int bestFood(int missing) {
      for (int i = FOOD_ID.length - 1; i >= 0; i--) {
         if (FOOD_HEAL[i] > missing) {
            continue;
         }
         int pos = GetItemPos(FOOD_ID[i]);
         if (pos != -1) {
            return pos;
         }
      }
      for (int i = 0; i < FOOD_ID.length; i++) {
         int pos = GetItemPos(FOOD_ID[i]);
         if (pos != -1) {
            return pos;
         }
      }
      return -1;
   }

   /** While melee finishes the current target, the casts move to the
    *  nearest fresh npc of the same kind: full per-cast magic xp from
    *  rounds that were pure waiting, and the next fight arrives
    *  pre-softened. CastOnNpcStill sends no walk (a real walk request
    *  mid-fight reads as fleeing), so only npcs the server will take from
    *  where we stand -- within its 5-tile cast range -- are considered.
    *  Free npcs only, and never one a cast could kill: unknown hp (-1,
    *  never been hit) is full health, which the startup gate already
    *  proved is safely above the spell's max. Same 1500ms pace as the
    *  main-target casting, sharing its clock. */
   private void sideCast() {
      if (GetMillis() - lastCast < 1500) {
         return;
      }
      int best = -1, bestDist = 99;
      int mx = GetX(), my = GetY();
      for (int i = 0; i < CountNpcs(); i++) {
         if (NpcId(i) != npcId || NpcInCombat(i)) {
            continue;
         }
         /* Classic mode leaves a softened npc for melee to finish; with
            killOnCast the spell may take it, kill pot and all -- that pot
            was never defense's anyway (the melee partner is excluded above),
            and the corpse still pays: bones land inside the bury sweep. */
         if (!killOnCast) {
            int hp = NpcHits(i);
            if (hp != -1 && hp <= spellMax) {
               continue;
            }
         }
         int dx = NpcX(i) - mx, dy = NpcY(i) - my;
         if (dx < 0) { dx = -dx; }
         if (dy < 0) { dy = -dy; }
         int dist = dx > dy ? dx : dy;
         if (dist <= 4 && dist < bestDist) {
            best = i;
            bestDist = dist;
         }
      }
      if (best != -1) {
         CastOnNpcStill(spell, best);
         lastCast = GetMillis();
      }
   }

   /** A ground click right where we stand -- breaking combat needs no
    *  movement at all. The server refuses it during the opponent's first 3
    *  hits, so callers retry until InCombat() goes false. InCombat() is the
    *  client's flag and only flips when the server's next update lands, so
    *  after the click this polls tight instead of checking once -- a single
    *  stale read here was costing a full loop pass before the first bite. */
   private void retreat() {
      FleeCombat();
      for (int i = 0; i < 12 && InCombat(); i++) {
         Wait(150);
      }
   }

   private void report(String line) {
      ShowMessage(line);
      ShowMessage("@whi@Gained " + (GetExp(STAT_DEFENSE) - defExpStart) + " defense xp, "
            + (GetExp(STAT_MAGIC) - magExpStart) + " magic xp, "
            + (GetExp(STAT_PRAYER) - prayExpStart) + " prayer xp this run.");
   }
}
