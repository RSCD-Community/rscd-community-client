/*
 * The cooking skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every cookable food with how many you would need to cook -- green for
 * the ones your level can already do, red for the ones it cannot yet.
 *
 * The foods and their experience rates are tip.it Classic's own cooking
 * calculator's (Cookexp, by Silverion), kept as that community recorded
 * them. A "**" on a name is tip.it's own mark for a food gated behind a
 * quest as well as a level.
 */
public class Cooking extends Calculator {

   Input currentExp = number("Current exp").def(exp(COOKING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(COOKING)) + 1)).range(2, 99);

   /* name, level required, experience per item. */
   private static final Object[][] FOODS = {
      { "Meat", 1, 30.0 },
      { "Shrimp", 1, 30.0 },
      { "Bread", 1, 40.0 },
      { "Sardine", 1, 40.0 },
      { "Herring", 5, 50.0 },
      { "Redberry Pie", 10, 60.0 },
      { "Anchovy", 15, 30.0 },
      { "**Mackeral", 1, 60.0 },
      { "Trout", 15, 70.0 },
      { "**Cod", 1, 75.0 },
      { "Meatpie", 20, 80.0 },
      { "Pike", 20, 80.0 },
      { "Stew", 25, 90.0 },
      { "Salmon", 25, 90.0 },
      { "Apple Pie", 30, 100.0 },
      { "Tuna", 30, 100.0 },
      { "Pizza", 35, 110.0 },
      { "Wine", 35, 110.0 },
      { "Cake", 40, 120.0 },
      { "Lobster", 40, 120.0 },
      { "Meat + Pizza", 45, 140.0 },
      { "Swordfish", 45, 140.0 },
      { "Chocolate Cake", 50, 120.0 },
      { "**Bass", 42, 130.0 },
      { "**Fire Eel", 53, 140.0 },
      { "Anchovy + Pizza", 55, 140.0 },
      { "**Shark", 80, 210.0 }
   };

   public Cooking(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Cookexp.";
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
      for (int i = 0; i < FOODS.length; i++) {
         String name = (String)FOODS[i][0];
         int req = ((Integer)FOODS[i][1]).intValue();
         double per = ((Double)FOODS[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
