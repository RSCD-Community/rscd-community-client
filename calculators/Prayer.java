/*
 * The prayer skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every bone with how many you would need to bury -- green for the
 * ones your level can already do, red for the ones it cannot yet.
 *
 * The bones and their experience rates are tip.it Classic's own prayer
 * calculator's (Prayercalc, by Silverion), kept as that community recorded
 * them. No bone in RSC needs a level to bury, so every row is green from 1.
 */
public class Prayer extends Calculator {

   Input currentExp = number("Current exp").def(exp(PRAYER)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(PRAYER)) + 1)).range(2, 99);

   /* name, level required, experience per bone. */
   private static final Object[][] BONES = {
      { "Bones", 1, 3.75 },
      { "Bat Bones", 1, 4.5 },
      { "Big Bones", 1, 12.5 },
      { "Dragon Bones", 1, 60.0 }
   };

   public Prayer(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Prayercalc.";
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
      for (int i = 0; i < BONES.length; i++) {
         String name = (String)BONES[i][0];
         int req = ((Integer)BONES[i][1]).intValue();
         double per = ((Double)BONES[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
