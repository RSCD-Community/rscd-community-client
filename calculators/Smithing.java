/*
 * The smithing calculator -- the anvil half of smithing, in tip.it's
 * classic shape: your current experience (preloaded from your character,
 * editable), a target level, and then every bar with how many you would need
 * to smith -- green for the ones your level can already do, red for the
 * ones it cannot yet.
 *
 * The bars and their experience rates are tip.it Classic's own smithing
 * calculator's (Smithexp, by Silverion, titled "Smiting Experience
 * Calculator" on the page itself), kept as that community recorded them --
 * silver and gold bars included, exactly as tip.it listed them, even though
 * neither is actually worked at the anvil. tip.it published a second,
 * revised version of this page without them; see Smithing2.java.
 */
public class Smithing extends Calculator {

   Input currentExp = number("Current exp").def(exp(SMITHING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(SMITHING)) + 1)).range(2, 99);

   /* name, level required, experience per bar. */
   private static final Object[][] BARS = {
      { "Bronze", 1, 18.75 },
      { "Iron", 15, 37.5 },
      { "Silver", 20, 13.5 },
      { "Steel", 30, 55.0 },
      { "Gold", 40, 22.5 },
      { "Mithril", 50, 80.0 },
      { "Adamite", 70, 100.0 },
      { "Runite", 85, 125.0 }
   };

   public Smithing(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Smithexp.";
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
