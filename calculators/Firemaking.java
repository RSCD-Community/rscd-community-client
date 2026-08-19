/*
 * The firemaking calculator, in tip.it Classic's own shape (Firecalc, by
 * Silverion) -- which is not an item table like the other skill calculators
 * here. tip.it's page assumes you always burn the best log your level
 * allows, and estimates the experience per fire at each level along the way
 * as (25 + 1.75 * level) -- an approximation of the average log in reach,
 * not any one log's real rate -- then simulates lighting fires level by
 * level from where you are to your target, counting how many it takes.
 *
 * That simulation is reproduced here exactly as tip.it's own JavaScript did
 * it: one average rate per level, fires added at that rate until the next
 * level's experience total is reached, then the rate recalculated for the
 * level after.
 */
public class Firemaking extends Calculator {

   Input currentExp = number("Current exp").def(exp(FIREMAKING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(FIREMAKING)) + 1)).range(2, 99);

   public Firemaking(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "tip.it Classic's Firecalc, same average-rate simulation.";
   }

   public void compute(Output out) {
      int have = currentExp.num();
      int goal = target.num();
      int level = levelForXp(have);

      if (goal <= level) {
         out.text("You already have level " + goal + " -- "
            + comma(have) + " exp is level " + level, GOOD);
         return;
      }

      int need = xpForLevel(goal) - have;
      out.text("For level " + goal + " you need " + comma(need) + " more exp", GOLD);
      out.text("You are level " + level + " with " + comma(have) + " exp", DIM);
      out.gap();

      double simExp = have;
      long fires = 0;
      for (int lvl = level; lvl < goal; lvl++) {
         double rate = 25.0 + 1.75 * (lvl + 1);
         int nextLevelExp = xpForLevel(lvl + 1);
         while (simExp < nextLevelExp) {
            fires++;
            simExp += rate;
         }
      }

      out.text("To reach level " + goal + " light about " + comma(fires) + " fires", GOOD);
   }
}
