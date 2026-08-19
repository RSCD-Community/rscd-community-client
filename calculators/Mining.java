/*
 * The mining skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every ore with how many you would need to mine -- green for the ones
 * your level can already do, red for the ones it cannot yet.
 *
 * The ores and their experience rates are tip.it Classic's own mining
 * calculator's (Mineexp, by Silverion), kept as that community recorded
 * them.
 */
public class Mining extends Calculator {

   Input currentExp = number("Current exp").def(exp(MINING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(MINING)) + 1)).range(2, 99);

   /* name, level required, experience per ore. */
   private static final Object[][] ORES = {
      { "Clay", 1, 5.0 },
      { "Copper", 1, 17.5 },
      { "Tin", 1, 17.5 },
      { "Iron", 15, 35.0 },
      { "Silver", 20, 40.0 },
      { "Coal", 30, 50.0 },
      { "Gem Rock", 40, 50.0 },
      { "Gold", 40, 65.0 },
      { "Mithril", 55, 80.0 },
      { "Adamantite", 70, 95.0 },
      { "Runite", 85, 125.0 }
   };

   public Mining(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Mineexp.";
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
      for (int i = 0; i < ORES.length; i++) {
         String name = (String)ORES[i][0];
         int req = ((Integer)ORES[i][1]).intValue();
         double per = ((Double)ORES[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
