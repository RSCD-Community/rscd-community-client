/*
 * The plain experience table, for any skill: where you are, and the total and
 * remaining experience for the levels ahead. The curve is the authentic one
 * -- Calculator.xpForLevel() computes the same table the client itself is
 * built on -- so the numbers here are the numbers the game will show.
 *
 * Reading the skill and the experience happens inside compute(), not at
 * load: switch the skill dropdown and the whole table follows, and the exp
 * figure is live from your character each time an input changes.
 */
public class Experience extends Calculator {

   /* The client's stat order; the option index IS the skill id. */
   private static final String[] SKILLS = {
      "Attack", "Defense", "Strength", "Hits", "Ranged", "Prayer",
      "Magic", "Cooking", "Woodcutting", "Fletching", "Fishing",
      "Firemaking", "Crafting", "Smithing", "Mining", "Herblaw",
      "Agility", "Thieving", "Runecrafting"
   };

   Input skill = choice("Skill", SKILLS);
   Input target = number("Target level (0 = next)").def(0).range(0, 99);

   public Experience(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "The authentic experience curve, live from your character.";
   }

   public void compute(Output out) {
      int which = skill.index();
      int have = exp(which);
      int level = levelForXp(have);
      int goal = target.num();
      if (goal == 0) {
         goal = Math.min(99, level + 1);
      }

      out.text(SKILLS[which] + ": level " + level + " with " + comma(have) + " exp", GOLD);
      if (goal > level) {
         out.text(comma(xpForLevel(goal) - have) + " exp to go for level " + goal);
      } else {
         out.text("You already have level " + goal, GOOD);
      }

      out.gap();

      /* The next ten levels, plus the target if it is further out than that.
         The target's own row is the green one. */
      int last = Math.min(99, level + 10);
      Table t = out.table("Lv.", "Total exp", "To go");

      for (int l = level + 1; l <= last; l++) {
         int total = xpForLevel(l);
         t.row(l == goal).cell(l).cell(total).cell(Math.max(0, total - have));
      }

      if (goal > last) {
         int total = xpForLevel(goal);
         t.row(true).cell(goal).cell(total).cell(Math.max(0, total - have));
      }
   }
}
