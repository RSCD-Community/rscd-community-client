package org.rscdaemon.client;

import java.util.ArrayList;
import java.util.List;

/*
 * The base class a calculator extends.
 *
 * A calculator is the third kind of thing the community can drop next to the
 * jar, after scripts and textscripts: a .java file in calculators/ that
 * declares what it wants to ask (the inputs) and computes what it wants to
 * say (the outputs). The client renders both sides -- inputs down the left,
 * outputs on the right -- through Skin, so every calculator anyone writes
 * looks like it shipped with the client. All the calculation logic lives in
 * the calculator; the client is just the renderer.
 *
 * The shape is deliberately the tip.it / web-calculator shape everybody
 * already knows: a skill calculator preloads your experience from the
 * character you are logged in as, you override anything you like, and the
 * answer updates as you type. compute(Output) is re-run on every change.
 *
 * It extends Methods on purpose. That is the whole scripting API -- the
 * inventory, the bank, npcs in view, everything a script can read -- so a
 * calculator is free to answer questions no web page can, like how many yew
 * logs are actually in your bank. It also means CalculatorPanel can load one
 * through the same ScriptRunner machinery scripts use: same compiler, same
 * no-JDK .class fallback, same error reporting.
 *
 * What a calculator looks like (calculators/MaxMeleeHit.java holds the full
 * version of this):
 *
 *    public class MaxMeleeHit extends Calculator {
 *       Input strength = number("Strength level").def(baseLevel(STRENGTH)).range(1, 99);
 *       Input weapon   = choice("Weapon", "None", 0, "Rune 2-handed Sword", 70);
 *
 *       public MaxMeleeHit(mudclient mc) { super(mc); }
 *
 *       public void compute(Output out) {
 *          double eff = strength.num();
 *          out.text("Your maximum hit is "
 *             + (int)(eff * (weapon.val() * 0.00175 + 0.1) + 1.05), GOLD);
 *       }
 *    }
 *
 * Inputs are declared as field initializers, so they register themselves in
 * the order they are written and that is the order they are drawn in. A
 * calculator that wants to build inputs in a loop can do the same calls from
 * an instance initializer block -- nothing about the registry cares where
 * the call came from.
 */
public abstract class Calculator extends Methods {

   /* The skill indices, in the client's own stat-array order. */
   public static final int ATTACK = 0;
   public static final int DEFENSE = 1;
   public static final int STRENGTH = 2;
   public static final int HITS = 3;
   public static final int RANGED = 4;
   public static final int PRAYER = 5;
   public static final int MAGIC = 6;
   public static final int COOKING = 7;
   public static final int WOODCUT = 8;
   public static final int FLETCHING = 9;
   public static final int FISHING = 10;
   public static final int FIREMAKING = 11;
   public static final int CRAFTING = 12;
   public static final int SMITHING = 13;
   public static final int MINING = 14;
   public static final int HERBLAW = 15;
   public static final int AGILITY = 16;
   public static final int THIEVING = 17;

   /* Text colours for Output.text(String, int), from the client's own theme
      so nobody has to invent a green that clashes with everything else. */
   public static final int PLAIN = Skin.TEXT;
   public static final int GOLD = Skin.GOLD_HI;
   public static final int GOOD = Skin.RUNE;
   public static final int BAD = Skin.EMBER_HI;
   public static final int DIM = Skin.TEXT_DIM;

   /*
    * The authentic experience table, computed the same way mudclient computes
    * its own copy at load (the k + 300*2^(k/7) accumulation, masked and
    * quartered). Computed here rather than read from the client so it exists
    * before login finishes and never depends on load order. XP_FOR[n] is the
    * total experience at which level n is reached; XP_FOR[1] is 0.
    */
   private static final int[] XP_FOR = new int[100];
   static {
      int total = 0;
      for (int j = 0; j < 99; j++) {
         int k = j + 1;
         total += (int)((double)k + 300.0 * Math.pow(2.0, (double)k / 7.0));
         if (j + 2 <= 99) {
            XP_FOR[j + 2] = (total & 268435452) / 4;
         }
      }
   }

   /* Declaration order, which is display order. */
   private final List<Input> inputs = new ArrayList<Input>();

   public Calculator(mudclient mc) {
      super(mc);
   }

   /*
    * Recomputed on every input change, and whenever the panel opens this
    * calculator. Read the inputs, do the work, write the outputs. Throwing is
    * survivable -- the panel catches it and shows the message where the
    * outputs would have been -- but slow is not: this runs on the render
    * thread, so a compute() that takes seconds freezes the game view.
    */
   public abstract void compute(Output out);

   /** One line under the calculator's name in the list. Override or don't. */
   public String about() {
      return "";
   }

   List<Input> inputList() {
      return this.inputs;
   }

   /* ---- input factories ---- */

   /** A typed number. Defaults to 0 until .def() says otherwise. */
   protected Input number(String label) {
      Input in = new Input(Input.NUMBER, label);
      this.inputs.add(in);
      return in;
   }

   /**
    * A dropdown. Arguments alternate label, value: choice("Prayer",
    * "None", 1.0, "Burst of Strength", 1.05). The value reads back through
    * .val(); the selected label through .name(); the position through
    * .index(), for calculators whose options mean more than one number.
    */
   protected Input choice(String label, Object... namesAndValues) {
      Input in = new Input(Input.CHOICE, label);
      for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
         in.addOption(String.valueOf(namesAndValues[i]), ((Number)namesAndValues[i + 1]).doubleValue());
      }
      this.inputs.add(in);
      return in;
   }

   /** A dropdown of names alone; .val() and .index() are the position. */
   protected Input choice(String label, String[] names) {
      Input in = new Input(Input.CHOICE, label);
      for (int i = 0; i < names.length; i++) {
         in.addOption(names[i], i);
      }
      this.inputs.add(in);
      return in;
   }

   /** An on/off row. Reads back through .on(). */
   protected Input toggle(String label) {
      Input in = new Input(Input.TOGGLE, label);
      this.inputs.add(in);
      return in;
   }

   /* ---- the player, for .def() preloads and for live reads ---- */

   /** The base (unpotioned, undrained) level of a skill. */
   protected int baseLevel(int skill) {
      return this.rs.playerStatBase[skill];
   }

   /** The current (boosted or drained) level of a skill. */
   protected int curLevel(int skill) {
      return this.rs.playerStatCurrent[skill];
   }

   /** Total experience in a skill. */
   protected int exp(int skill) {
      return this.rs.playerStatExperience[skill];
   }

   protected String skillName(int skill) {
      return this.rs.skillArray[skill];
   }

   /* ---- the experience curve ---- */

   /** Total experience at which `level` is reached. Level 1 is 0. */
   public static int xpForLevel(int level) {
      if (level <= 1) {
         return 0;
      }
      return XP_FOR[Math.min(level, 99)];
   }

   /** The level `xp` total experience puts a skill at. */
   public static int levelForXp(int xp) {
      for (int level = 99; level >= 2; level--) {
         if (xp >= XP_FOR[level]) {
            return level;
         }
      }
      return 1;
   }

   /** 90872 -> "90,872", because nobody proofreads seven digits without it. */
   public static String comma(long n) {
      String s = String.valueOf(Math.abs(n));
      StringBuilder out = new StringBuilder(n < 0 ? "-" : "");
      int lead = s.length() % 3;
      for (int i = 0; i < s.length(); i++) {
         if (i > 0 && (i - lead) % 3 == 0) {
            out.append(',');
         }
         out.append(s.charAt(i));
      }
      return out.toString();
   }

   /*
    * ---- Input ----
    *
    * One declared field on the calculator, one widget on the panel. The
    * author-facing readers (num, val, on, name, index) are public because a
    * calculator compiles in the default package; the mutable state is
    * package-private because only CalculatorPanel edits it.
    */
   public static final class Input {
      static final int NUMBER = 0;
      static final int CHOICE = 1;
      static final int TOGGLE = 2;

      final int kind;
      final String label;

      /* NUMBER: what the player has typed, parsed lazily by val(). */
      String text = "";
      /* CHOICE / TOGGLE state. */
      int selected;
      boolean checked;

      private final List<String> names = new ArrayList<String>();
      private final List<Double> values = new ArrayList<Double>();
      private double fallback;
      private double min = -Double.MAX_VALUE;
      private double max = Double.MAX_VALUE;

      Input(int kind, String label) {
         this.kind = kind;
         this.label = label;
      }

      void addOption(String name, double value) {
         this.names.add(name);
         this.values.add(Double.valueOf(value));
      }

      int optionCount() {
         return this.names.size();
      }

      String optionName(int i) {
         return this.names.get(i);
      }

      /* -- author-facing, fluent declaration -- */

      /** Starting value. For a NUMBER this is also what blank falls back to. */
      public Input def(double value) {
         if (this.kind == NUMBER) {
            this.fallback = value;
            this.text = format(value);
         } else if (this.kind == CHOICE) {
            this.selected = Math.max(0, Math.min((int)value, this.names.size() - 1));
         } else {
            this.checked = value != 0.0;
         }
         return this;
      }

      /** Clamp for typed numbers. Applied when the value is read, not typed. */
      public Input range(double min, double max) {
         this.min = min;
         this.max = max;
         return this;
      }

      /* -- author-facing, reading -- */

      /** The value, clamped. A CHOICE answers its selected option's value. */
      public double val() {
         if (this.kind == CHOICE) {
            return this.values.isEmpty() ? 0.0 : this.values.get(this.selected).doubleValue();
         }
         if (this.kind == TOGGLE) {
            return this.checked ? 1.0 : 0.0;
         }
         double v;
         try {
            v = this.text.length() == 0 ? this.fallback : Double.parseDouble(this.text);
         } catch (NumberFormatException e) {
            v = this.fallback;
         }
         return Math.max(this.min, Math.min(this.max, v));
      }

      /** val() as an int, truncated the way the game's own formulas truncate. */
      public int num() {
         return (int)this.val();
      }

      public boolean on() {
         return this.kind == TOGGLE ? this.checked : this.val() != 0.0;
      }

      /** The selected option's label; "" for a NUMBER. */
      public String name() {
         return this.kind == CHOICE && !this.names.isEmpty() ? this.names.get(this.selected) : "";
      }

      public int index() {
         return this.selected;
      }

      /** What the widget shows right now. */
      String display() {
         if (this.kind == CHOICE) {
            return this.name();
         }
         if (this.kind == TOGGLE) {
            return this.checked ? "ON" : "OFF";
         }
         return this.text;
      }

      private static String format(double v) {
         return v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long)v) : String.valueOf(v);
      }
   }

   /*
    * ---- Output ----
    *
    * What compute() writes into, in order, and what the right-hand pane draws,
    * in order. Three shapes cover the calculators people actually make --
    * a headline answer, explanatory lines, and the classic green/red action
    * table -- and a calculator needing a fourth can put anything it computes
    * into text rows.
    */
   public static final class Output {
      static final int TEXT = 0;
      static final int HEADING = 1;
      static final int GAP = 2;
      static final int TABLE = 3;

      static final class Item {
         final int kind;
         final String text;
         final int colour;
         final Table table;

         Item(int kind, String text, int colour, Table table) {
            this.kind = kind;
            this.text = text;
            this.colour = colour;
            this.table = table;
         }
      }

      final List<Item> items = new ArrayList<Item>();

      public void text(String s) {
         this.text(s, PLAIN);
      }

      public void text(String s, int colour) {
         this.items.add(new Item(TEXT, s, colour, null));
      }

      /** A gold section header with the theme's rule under it. */
      public void heading(String s) {
         this.items.add(new Item(HEADING, s, GOLD, null));
      }

      public void gap() {
         this.items.add(new Item(GAP, "", 0, null));
      }

      /** Start a table. Add rows to what this returns. */
      public Table table(String... headers) {
         Table t = new Table(headers);
         this.items.add(new Item(TABLE, "", 0, t));
         return t;
      }
   }

   /*
    * A table in the tip.it mould: fixed headers, then rows that are either
    * green (you can do this now) or red (you cannot yet). Fluent:
    *
    *    Table t = out.table("Number", "Name", "Lv.", "Exp");
    *    t.row(a.level <= mine).cell(count).cell(a.name).cell(a.level).cell(a.xp);
    *
    * Number cells right-align themselves; everything else lands left.
    */
   public static final class Table {
      final String[] headers;
      final List<String[]> rows = new ArrayList<String[]>();
      final List<boolean[]> numeric = new ArrayList<boolean[]>();
      final List<Boolean> ok = new ArrayList<Boolean>();
      /* Whether the row asked to be judged at all -- a plain row() is drawn
         in normal ink, not green. */
      final List<Boolean> judged = new ArrayList<Boolean>();

      private int col;

      Table(String[] headers) {
         this.headers = headers;
      }

      /** A neutral row -- no green, no red. */
      public Table row() {
         return this.rowState(true, false);
      }

      /** A judged row: true is can-do green, false is not-yet red. */
      public Table row(boolean canDo) {
         return this.rowState(canDo, true);
      }

      private Table rowState(boolean canDo, boolean judgedRow) {
         this.rows.add(new String[this.headers.length]);
         this.numeric.add(new boolean[this.headers.length]);
         this.ok.add(Boolean.valueOf(canDo));
         this.judged.add(Boolean.valueOf(judgedRow));
         this.col = 0;
         return this;
      }

      public Table cell(Object value) {
         if (this.rows.isEmpty() || this.col >= this.headers.length) {
            return this;
         }
         String[] row = this.rows.get(this.rows.size() - 1);
         boolean[] num = this.numeric.get(this.numeric.size() - 1);
         if (value instanceof Double || value instanceof Float) {
            double d = ((Number)value).doubleValue();
            row[this.col] = d == Math.floor(d) ? String.valueOf((long)d) : String.valueOf(d);
            num[this.col] = true;
         } else if (value instanceof Number) {
            row[this.col] = comma(((Number)value).longValue());
            num[this.col] = true;
         } else {
            row[this.col] = String.valueOf(value);
         }
         this.col++;
         return this;
      }
   }
}
