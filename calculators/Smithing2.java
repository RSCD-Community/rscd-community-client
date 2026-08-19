/*
 * tip.it Classic's second smithing calculator (Smith2exp, by Silverion) --
 * the corrected version of Smithing.java, dropping silver and gold since
 * neither is actually worked at the anvil. Same shape as every calculator
 * here: current experience preloaded from your character, a target level,
 * then every bar with how many you would need to smith -- green for the
 * ones your level can already do, red for the ones it cannot yet.
 */
public class Smithing2 extends Calculator {

   Input currentExp = number("Current exp").def(exp(SMITHING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(SMITHING)) + 1)).range(2, 99);

   /* name, level required, experience per bar. */
   private static final Object[][] BARS = {
      { "Bronze", 1, 12.5 },
      { "Iron", 15, 25.0 },
      { "Steel", 30, 37.0 },
      { "Mithril", 50, 50.0 },
      { "Adamite", 70, 62.5 },
      { "Runite", 85, 75.0 }
   };

   public Smithing2(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Smith2exp.";
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
      for (int i = 0; i < BARS.length; i++) {
         String name = (String)BARS[i][0];
         int req = ((Integer)BARS[i][1]).intValue();
         double per = ((Double)BARS[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
