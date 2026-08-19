/*
 * The herblaw skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every herb identification and potion with how many you would need to
 * make -- green for the ones your level can already do, red for the ones it
 * cannot yet.
 *
 * The rows and their experience rates are tip.it Classic's own herblaw
 * calculator's (Herbexp, by Silverion), kept as that community recorded
 * them. An "H " prefix is identifying the herb; the rest are the finished
 * potions.
 */
public class Herblaw extends Calculator {

   Input currentExp = number("Current exp").def(exp(HERBLAW)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(HERBLAW)) + 1)).range(2, 99);

   /* name, level required, experience per item. */
   private static final Object[][] ITEMS = {
      { "H Guam Leaf", 3, 2.5 },
      { "Attack potion", 3, 25.0 },
      { "H Marrantill", 5, 3.75 },
      { "Cure poison potion", 5, 37.5 },
      { "H Tarromin", 10, 5.0 },
      { "Strength potion", 12, 50.0 },
      { "H Harralander", 20, 6.0 },
      { "Stat Restore potion", 22, 63.0 },
      { "H Ranarr Weed", 25, 8.0 },
      { "Defense potion", 30, 75.0 },
      { "Restore Prayer potion", 38, 87.5 },
      { "H Irit Leaf", 45, 9.0 },
      { "Super attack potion", 45, 100.0 },
      { "Poison antidote", 48, 106.0 },
      { "H Avantoe", 50, 10.0 },
      { "Fishing potion", 50, 113.0 },
      { "H Kwuarm", 55, 11.25 },
      { "Super strength potion", 55, 125.0 },
      { "Weapon poison", 60, 137.5 },
      { "H Cadantine", 66, 12.5 },
      { "Super defense potion", 66, 150.0 },
      { "H Dwarf Weed", 70, 13.75 },
      { "Ranging potion", 72, 161.0 }
   };

   public Herblaw(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Herbexp.";
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
      for (int i = 0; i < ITEMS.length; i++) {
         String name = (String)ITEMS[i][0];
         int req = ((Integer)ITEMS[i][1]).intValue();
         double per = ((Double)ITEMS[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
