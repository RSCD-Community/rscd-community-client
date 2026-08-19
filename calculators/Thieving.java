/*
 * The thieving skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every stall, chest, door and pocket in the game with how many times it
 * would take -- green for the ones your level can already do, red for the
 * ones it cannot yet.
 *
 * The targets and their experience rates are tip.it's RSC thieving
 * calculator's, kept as that community recorded them.
 */
public class Thieving extends Calculator {

   Input currentExp = number("Current exp").def(exp(THIEVING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(THIEVING)) + 1)).range(2, 99);

   /* name, level required, experience per go. */
   private static final Object[][] TARGETS = {
      { "Man", 1, 8.0 },
      { "Cake stall", 5, 16.0 },
      { "Pirate 2 chests", 5, 5.0 },
      { "Pirate 1st chest", 5, 7.5 },
      { "1 story houses", 7, 4.0 },
      { "3 chests axe house", 10, 7.5 },
      { "Farmer", 10, 14.5 },
      { "Door to Nature rune chest", 16, 15.0 },
      { "Disarm Chest", 18, 8.0 },
      { "Silk stall", 20, 24.0 },
      { "2 story houses", 21, 15.0 },
      { "Warrior", 25, 26.0 },
      { "Nature rune Chest 2nd Floor", 28, 25.0 },
      { "Door near Bakery stall", 31, 15.0 },
      { "Mansion - Door - Stairs", 31, 15.0 },
      { "Door near Church", 31, 15.0 },
      { "Rogue (Lv. 21)", 32, 35.5 },
      { "Fur Stall", 35, 36.0 },
      { "Guard", 40, 46.7 },
      { "Pirate 2nd", 43, 125.0 },
      { "Door to blood rune chest", 46, 37.0 },
      { "Hemming Chest", 47, 150.0 },
      { "Silver Stall", 50, 54.0 },
      { "Knight", 55, 84.5 },
      { "Blood rune Chest", 59, 250.0 },
      { "Door to paladine building", 61, 50.0 },
      { "Spices Stall", 65, 81.0 },
      { "Watchman", 65, 137.5 },
      { "Paladin", 70, 152.0 },
      { "Chest paladine building", 72, 500.0 },
      { "Gems Stall", 75, 16.0 },
      { "Gnome", 75, 198.0 }
   };

   public Thieving(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it's RSC thieving calculator.";
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

      Table t = out.table("Number", "Name", "Lv.", "Exp.");
      for (int i = 0; i < TARGETS.length; i++) {
         String name = (String)TARGETS[i][0];
         int req = ((Integer)TARGETS[i][1]).intValue();
         double per = ((Double)TARGETS[i][2]).doubleValue();
         long times = (long)Math.ceil(need / per);
         t.row(level >= req).cell(times).cell(name).cell(req).cell(per);
      }
   }
}
