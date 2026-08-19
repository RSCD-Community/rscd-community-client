/*
 * The woodcutting skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every tree with how many you would need to chop -- green for the ones
 * your level can already do, red for the ones it cannot yet.
 *
 * The trees and their experience rates are tip.it Classic's own woodcutting
 * calculator's (Woodcalc, by Silverion), kept as that community recorded
 * them.
 */
public class Woodcutting extends Calculator {

   Input currentExp = number("Current exp").def(exp(WOODCUT)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(WOODCUT)) + 1)).range(2, 99);

   /* name, level required, experience per tree. */
   private static final Object[][] TREES = {
      { "Common tree", 1, 25.0 },
      { "Oak tree", 15, 37.5 },
      { "Willow tree", 30, 62.5 },
      { "Maple tree", 45, 100.0 },
      { "Yew tree", 60, 175.0 },
      { "Magic tree", 75, 250.0 }
   };

   public Woodcutting(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Woodcalc.";
   }

   public void compute(Output out) {
      int have = currentExp.num();
      int goal = target.num();
      int need = xpForLevel(goal) - have;
      int level = levelForXp(have);

      if (need <= 0) {
         out.text("You already have level " + goal + " -- "
            + comma(have) + " exp is level " + level, GOOD);
         return;
      }

      out.text("For level " + goal + " you need " + comma(need) + " more exp", GOLD);
      out.text("You are level " + level + " with " + comma(have) + " exp", DIM);
      out.gap();

      Table t = out.table("Number", "Name", "Lv.", "Exp");
      for (int i = 0; i < TREES.length; i++) {
         String name = (String)TREES[i][0];
         int req = ((Integer)TREES[i][1]).intValue();
         double per = ((Double)TREES[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
