# Calculators

Drop a `.java` file in this folder and it appears on the Calculators screen
(F2 menu -> Calculators). No forking the client, no build step of your own:
the client compiles it when you pick it, exactly the way scripts work. On a
plain JRE (no compiler), an already-compiled `.class` of the same name in the
sibling `calculators-bin/` folder works instead.

This file is a `.md` on purpose -- the picker lists only `.java` and `.txt`.

## The shape

A calculator is one class that extends `Calculator`. You declare inputs as
fields; the client draws them down the left in the order you declared them.
You do all your calculation in `compute()`; the client draws whatever you
wrote into `out` on the right, and calls `compute()` again every time the
player changes an input. The calculator does all the calculation logic --
the client is just the renderer.

```java
public class MyCalc extends Calculator {

   Input level  = number("Strength level").def(baseLevel(STRENGTH)).range(1, 99);
   Input weapon = choice("Weapon", "None", 0, "Rune 2-handed Sword", 70);
   Input potted = toggle("Super strength");

   public MyCalc(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "One line shown under the calculator while it is open.";
   }

   public void compute(Output out) {
      double power = weapon.val();
      out.text("Your maximum hit is " + level.num(), GOLD);
   }
}
```

No `package` line, no imports -- the client prepends
`import org.rscdaemon.client.*;` for you, the same as it does for scripts.

## Inputs

| factory | widget | notes |
|---|---|---|
| `number(label)` | typed field | digits, `.` and `-`; blank falls back to `.def()` |
| `choice(label, name, value, name, value, ...)` | dropdown | labels alternate with their values |
| `choice(label, String[] names)` | dropdown | value is the position in the array |
| `toggle(label)` | on/off | |

Fluent on any input: `.def(value)` for the starting state, `.range(min, max)`
to clamp a typed number.

Reading one back: `.num()` (int), `.val()` (double), `.on()` (boolean),
`.name()` (the selected option's label), `.index()` (its position).

Preloading from the player: `.def(baseLevel(STRENGTH))` starts a field at the
logged-in character's level, and the player can type over it -- the tip.it
way round. Skill constants `ATTACK` through `THIEVING` are on `Calculator`,
in the client's own stat order.

## The player, live

`baseLevel(skill)`, `curLevel(skill)`, `exp(skill)`, `skillName(skill)` read
the logged-in character. Read them inside `compute()` and the output follows
the character; read them only in `.def()` and they are just starting values.

And because `Calculator` extends `Methods`, the whole scripting API is
available too -- the inventory, the bank, npcs in sight. A calculator that
counts the yew logs actually in your bank is a perfectly good calculator.

## The experience curve

`xpForLevel(level)` and `levelForXp(xp)` are the authentic table, computed
with the client's own formula. `comma(n)` formats big numbers.

## Output

Written top to bottom, drawn top to bottom:

```java
out.text("plain line");
out.text("coloured line", GOLD);   // PLAIN, GOLD, GOOD, BAD, DIM
out.heading("A SECTION");
out.gap();

Table t = out.table("Number", "Name", "Lv.", "Exp.");
t.row(level >= 40).cell(866).cell("Guard").cell(40).cell(46.7);
```

`row(boolean)` is the tip.it judgment: `true` draws the row green (you can do
this now), `false` red (not yet). A plain `row()` is neutral. Number cells
right-align themselves.

`compute()` runs on the render thread -- keep it fast. If it throws, the
error is shown where the output would have been, so a broken calculator
tells on itself instead of showing stale numbers.

## Escape and keys

While a calculator is open the keyboard belongs to it -- typing a level does
not also type into chat. Escape backs out one step at a time (dropdown,
field, calculator, picker, script menu); F2 drops straight back to the game.
