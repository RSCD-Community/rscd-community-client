/*
 * The fletching skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every arrow and bow with how many you would need to make -- green for
 * the ones your level can already do, red for the ones it cannot yet.
 *
 * The items and their experience rates are tip.it Classic's own fletching
 * calculator's (Fletchingexp, by Overridea), kept as that community
 * recorded them. The "10" on the arrow rows is tip.it's own batch size --
 * the experience listed is for ten arrows at once, not one.
 */
public class Fletching extends Calculator {

   Input currentExp = number("Current exp").def(exp(FLETCHING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(FLETCHING)) + 1)).range(2, 99);

   /* name, level required, experience per item (or per ten, for arrows). */
   private static final Object[][] ITEMS = {
      { "10 Bronze Arrows", 1, 30.0 },
      { "Short Bow", 5, 10.0 },
      { "Long Bow", 10, 20.0 },
      { "10 Iron Arrows", 15, 40.0 },
      { "Short Oak Bow", 20, 33.0 },
      { "Long Oak Bow", 25, 50.0 },
      { "10 Steel Arrows", 30, 65.0 },
      { "Short Willow Bow", 35, 66.5 },
      { "Long Willow Bow", 40, 83.0 },
      { "10 Mithrill Arrows", 45, 90.0 },
      { "Short Maple Bow", 50, 100.0 },
      { "Long Maple Bow", 55, 116.5 },
      { "10 Adamantite Arrows", 65, 115.0 },
      { "Short Yew Bow", 65, 133.0 },
      { "Long Yew Bow", 70, 150.0 },
      { "10 Runite Arrows", 75, 140.0 },
      { "Short Magic Bow", 80, 166.5 },
      { "Long Magic Bow", 85, 183.0 }
   };

   public Fletching(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Fletchingexp.";
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
