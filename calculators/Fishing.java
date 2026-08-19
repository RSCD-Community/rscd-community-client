/*
 * The fishing skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every catch with how many you would need to land -- green for the
 * ones your level can already do, red for the ones it cannot yet.
 *
 * The catches and their experience rates are tip.it Classic's own fishing
 * calculator's (Fishexp, by Silverion), kept as that community recorded
 * them. Boots, gloves and casket are the Big Net Fishing side-catches, not
 * fish -- tip.it lists them alongside the fish and so does this.
 */
public class Fishing extends Calculator {

   Input currentExp = number("Current exp").def(exp(FISHING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(FISHING)) + 1)).range(2, 99);

   /* name, level required, experience per catch. */
   private static final Object[][] CATCHES = {
      { "boots", 1, 1.0 },
      { "gloves", 1, 1.0 },
      { "casket", 1, 10.0 },
      { "Shrimp", 1, 10.0 },
      { "Sardina", 5, 20.0 },
      { "Herring", 10, 30.0 },
      { "Anchovy", 15, 40.0 },
      { "**Mackeral", 16, 10.0 },
      { "Trout", 20, 50.0 },
      { "**Cod", 23, 15.0 },
      { "Pike", 25, 60.0 },
      { "Salmon", 30, 70.0 },
      { "Tuna", 35, 80.0 },
      { "Lobster", 40, 90.0 },
      { "Swordfish", 50, 100.0 },
      { "**Bass", 46, 50.0 },
      { "**Lava Eel", 53, 30.0 },
      { "**Shark", 76, 110.0 },
      { "**Sea Turtle", 75, 105.0 },
      { "**Manta Ray", 81, 115.0 }
   };

   public Fishing(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Fishexp.";
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
      for (int i = 0; i < CATCHES.length; i++) {
         String name = (String)CATCHES[i][0];
         int req = ((Integer)CATCHES[i][1]).intValue();
         double per = ((Double)CATCHES[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
