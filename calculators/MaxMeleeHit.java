/*
 * The melee max-hit calculator, in the shape of the classic web ones: pick
 * your strength, weapon, amulet, gloves, potion, prayer and style, read the
 * biggest number you can hit.
 *
 * The formula is not guessed and not copied from a fansite -- it is the
 * server's own, from Formulae.maxHit():
 *
 *    max = (int)((str * prayerMult + styleBonus) * (power * 0.00175 + 0.1) + 1.05)
 *
 * where power is the sum of the WeaponPowerPoints of everything worn -- which
 * is why the amulet and gauntlets are inputs: an Amulet of strength is worth
 * 10 power, the same as a battle axe's whole head. The power values below are
 * lifted from the server's ItemWieldableDef data, so this calculator and the
 * game cannot disagree.
 *
 * The dragon axe is deliberately absent: its def carries armour points where
 * its weapon points should be, so its true power is not currently knowable.
 * When that gets fixed, add it here.
 */
public class MaxMeleeHit extends Calculator {

   Input strength = number("Strength level").def(baseLevel(STRENGTH)).range(1, 99);

   Input weapon = choice("Weapon",
      "None (fists)", 0,
      "Bronze dagger", 5,
      "Bronze mace", 5,
      "Bronze short sword", 7,
      "Bronze scimitar", 7,
      "Bronze long sword", 8,
      "Bronze battle axe", 10,
      "Bronze 2-handed sword", 11,
      "Iron dagger", 6,
      "Iron mace", 6,
      "Iron short sword", 8,
      "Iron scimitar", 10,
      "Iron long sword", 10,
      "Iron battle axe", 13,
      "Iron 2-handed sword", 15,
      "Steel dagger", 9,
      "Steel mace", 9,
      "Steel short sword", 14,
      "Steel scimitar", 15,
      "Steel long sword", 17,
      "Steel battle axe", 21,
      "Steel 2-handed sword", 23,
      "Black dagger", 9,
      "Black mace", 11,
      "Black short sword", 13,
      "Black scimitar", 15,
      "Black long sword", 16,
      "Black battle axe", 26,
      "Black 2-handed sword", 23,
      "Mithril dagger", 12,
      "Mithril mace", 12,
      "Mithril short sword", 19,
      "Mithril scimitar", 21,
      "Mithril long sword", 23,
      "Mithril battle axe", 30,
      "Mithril 2-handed sword", 32,
      "Adamantite dagger", 16,
      "Adamantite mace", 19,
      "Adamantite short sword", 25,
      "Adamantite scimitar", 29,
      "Adamantite long sword", 32,
      "Adamantite battle axe", 42,
      "Adamantite 2-handed sword", 45,
      "Rune dagger", 26,
      "Rune mace", 29,
      "Rune short sword", 41,
      "Rune scimitar", 45,
      "Rune long sword", 50,
      "Rune battle axe", 65,
      "Rune 2-handed sword", 70,
      "Rune throwing knife", 26,
      "Silverlight", 10,
      "Excalibur", 10,
      "Dragon sword", 68);

   Input amulet = choice("Amulet",
      "None", 0,
      "Amulet of strength (ruby)", 10,
      "Amulet of power (diamond)", 6,
      "Dragonstone amulet", 5);

   Input gloves = choice("Gloves",
      "None", 0,
      "Ice gloves / Gauntlets", 2);

   Input potion = choice("Potion",
      "None", 0,
      "Strength potion", 1,
      "Super strength potion", 2);

   Input prayer = choice("Prayer",
      "None", 1.00,
      "Burst of Strength", 1.05,
      "Superhuman Strength", 1.10,
      "Ultimate Strength", 1.15);

   Input style = choice("Attack style",
      "Accurate or defensive", 0,
      "Controlled (all xp)", 1,
      "Aggressive (strength xp)", 3);

   public MaxMeleeHit(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "The server's own Formulae.maxHit(), with its own weapon power table.";
   }

   public void compute(Output out) {
      int base = strength.num();

      /* The potion boost, exactly as the server applies it: ceil(max * pct)
         plus a flat bit, on top of the base level. */
      int boost = 0;
      if (potion.index() == 1) {
         boost = (int)Math.ceil(base * 0.10) + 2;
      } else if (potion.index() == 2) {
         boost = (int)Math.ceil(base * 0.15) + 4;
      }

      int power = weapon.num() + amulet.num() + gloves.num();
      double effective = (base + boost) * prayer.val() + style.val();
      int max = (int)(effective * (power * 0.00175 + 0.1) + 1.05);

      out.text("Your maximum hit is " + max, GOLD);
      out.gap();
      out.text("Strength after potion: " + (base + boost)
         + (boost > 0 ? "  (" + base + " + " + boost + ")" : ""), DIM);
      out.text("Prayer multiplier: x" + prayer.val(), DIM);
      out.text("Style bonus: +" + style.num(), DIM);
      out.text("Power points worn: " + power
         + "  (weapon " + weapon.num() + ", amulet " + amulet.num()
         + ", gloves " + gloves.num() + ")", DIM);
   }
}
