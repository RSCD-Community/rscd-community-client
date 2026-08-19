/*
 * The smelting calculator -- the ore-to-bar half of smithing, in tip.it's
 * classic shape: your current experience (preloaded from your character,
 * editable), a target level, and then every bar with how many you would need
 * to smelt -- green for the ones your level can already do, red for the
 * ones it cannot yet.
 *
 * The bars and their experience rates are tip.it Classic's own smelting
 * calculator's (Smeltexp, by Silverion), kept as that community recorded
 * them. Smelting shares its level with Smithing -- see also Smithing.java
 * and Smithing2.java, tip.it's own two anvil calculators.
 */
public class Smelting extends Calculator {

   Input currentExp = number("Current exp").def(exp(SMITHING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(SMITHING)) + 1)).range(2, 99);

   /* name, level required, experience per bar. */
   private static final Object[][] BARS = {
      { "Bronze", 1, 6.25 },
      { "Iron", 15, 12.5 },
      { "Silver", 20, 13.5 },
      { "Steel", 30, 17.5 },
      { "Gold", 40, 22.5 },
      { "Mithril", 50, 30.0 },
      { "Adamite", 70, 37.5 },
      { "Runite", 85, 50.0 }
   };

   public Smelting(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Smeltexp.";
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
