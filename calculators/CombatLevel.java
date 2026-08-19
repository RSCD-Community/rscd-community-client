/*
 * The combat level calculator, after uLtRaPoWeR and Silverion's classic --
 * type seven levels, read your combat level, how it is weighted, and how far
 * the next one is.
 *
 * The formula is the server's own, from Formulae.getCombatLevel():
 *
 *    attack  = att + str
 *    defense = def + hits
 *    mage    = (prayer + magic) / 8
 *    ranged-based when attack < ranged * 1.5, then
 *       combat = defense/4 + ranged * 0.375 + mage
 *    otherwise
 *       combat = attack/4 + defense/4 + mage
 *
 * The "levels until" lines are found by simulation against that same formula
 * rather than by arithmetic on the weights, so they stay right at the seam
 * where enough ranged levels flip a melee character to ranged-based.
 */
public class CombatLevel extends Calculator {

   Input attack = number("Attack").def(baseLevel(ATTACK)).range(1, 99);
   Input defense = number("Defense").def(baseLevel(DEFENSE)).range(1, 99);
   Input strength = number("Strength").def(baseLevel(STRENGTH)).range(1, 99);
   Input hits = number("Hits").def(baseLevel(HITS)).range(1, 99);
   Input ranged = number("Ranged").def(baseLevel(RANGED)).range(1, 99);
   Input prayer = number("Prayer").def(baseLevel(PRAYER)).range(1, 99);
   Input magic = number("Magic").def(baseLevel(MAGIC)).range(1, 99);

   public CombatLevel(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "The server's own Formulae.getCombatLevel(), preloaded from your stats.";
   }

   private static int combat(int att, int def, int str, int hp, int rng, int pray, int mag) {
      double attack = att + str;
      double defense = def + hp;
      double mage = (pray + mag) / 8.0;

      if (attack < rng * 1.5) {
         return (int)(defense / 4.0 + rng * 0.375 + mage);
      }

      return (int)(attack / 4.0 + defense / 4.0 + mage);
   }

   /* How many levels of one stat reach the next combat level; 0 when even 99
      everywhere would not get there through that stat alone. */
   private int until(int which, int target) {
      int[] s = {
         attack.num(), defense.num(), strength.num(), hits.num(),
         ranged.num(), prayer.num(), magic.num()
      };

      for (int add = 1; add <= 200; add++) {
         int[] t = new int[]{s[0], s[1], s[2], s[3], s[4], s[5], s[6]};
         t[which] += add;
         if (combat(t[0], t[1], t[2], t[3], t[4], t[5], t[6]) >= target) {
            return add;
         }
      }

      return 0;
   }

   private static int least(int a, int b) {
      if (a == 0) {
         return b;
      }

      return b == 0 ? a : Math.min(a, b);
   }

   public void compute(Output out) {
      int level = combat(attack.num(), defense.num(), strength.num(), hits.num(),
         ranged.num(), prayer.num(), magic.num());
      boolean rangedBased = attack.num() + strength.num() < ranged.num() * 1.5;

      out.text("Your combat level is " + level, GOLD);
      out.text("It is " + (rangedBased ? "Ranged" : "Attack + Strength") + " based");
      out.gap();

      int next = level + 1;

      if (rangedBased) {
         int viaRanged = until(4, next);
         if (viaRanged > 0) {
            out.text(viaRanged + " more ranged levels until level " + next, GOOD);
         }

         int viaDef = least(until(1, next), until(3, next));
         if (viaDef > 0) {
            out.text(viaDef + " more defense or hit levels until level " + next, GOOD);
         }
      } else {
         int viaMelee = least(least(until(0, next), until(2, next)),
            least(until(1, next), until(3, next)));
         if (viaMelee > 0) {
            out.text(viaMelee + " more attack, defense, strength, or hit levels until level " + next, GOOD);
         }
      }

      int viaMage = least(until(5, next), until(6, next));
      if (viaMage > 0) {
         out.text(viaMage + " more magic or prayer levels until level " + next, GOOD);
      }
   }
}
