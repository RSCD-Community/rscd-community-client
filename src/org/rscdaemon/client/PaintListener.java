package org.rscdaemon.client;

/*
 * APOS's switch for the script overlay.
 *
 * bot.jar shipped four top-level helper classes beside Script -- Extension,
 * PaintListener, ScriptListener and SleepListener -- and none of them appear in
 * the published javadoc, which documents Script and nothing else. The signatures
 * below were read off the class itself rather than guessed:
 *
 *    public static void toggle();
 *    public static boolean isEnabled();
 *    public static void setEnabled(boolean);
 *
 * The corpus uses one of them, B_Smithy's PaintListener.toggle(), bound to a
 * key so somebody watching a bot can get the HUD out of the way.
 *
 * These are the SAME switch as Script.setPaintOverlay, not a second one. APOS's
 * Script class holds no paint field at all, so its setPaintOverlay can only be
 * delegating here; and B_Smithy restores its HUD after a screenshot by calling
 * PaintListener.toggle() AND setPaintOverlay(true) one after the other, which
 * is only harmless if both land on the same state. Split them and that script's
 * overlay disappears for good the first time it takes a screenshot.
 *
 * NOT reproduced: the real class also has render_solid and render_textures
 * (low-graphics switches with no equivalent here), paint() and resetXp(), and
 * the sibling ScriptListener and SleepListener classes. Nothing in the corpus
 * touches any of them, and a field that exists but does nothing is worse than
 * one that is honestly absent -- a script setting render_textures would compile
 * and be silently ignored. If something turns up that needs them, they get
 * added with real behaviour behind them.
 *
 * These are static and take no arguments, which is why Script keeps a
 * thread-scoped record of what is running: see Script.current(). A static field
 * holding "the" script would break the launcher's one-client-per-tab
 * arrangement, where two scripts can be painting at once.
 */
public final class PaintListener {

   private PaintListener() {
   }

   /** Turn the overlay off, or back on. */
   public static void toggle() {
      Script script = Script.current();

      if (script != null) {
         script.setPaintOverlay(!script.isPaintOverlay());
      }
   }

   public static boolean isEnabled() {
      Script script = Script.current();
      return script != null && script.isPaintOverlay();
   }

   public static void setEnabled(boolean flag) {
      Script script = Script.current();

      if (script != null) {
         script.setPaintOverlay(flag);
      }
   }
}
