package org.rscdaemon.client;

/*
 * A press-and-hold repeat for a scroll arrow, shared by every panel this
 * client draws itself -- ScriptPanel and WorldsPanel -- rather than through
 * Jagex's own Menu widget system.
 *
 * Menu's own list scrollbars (the message tabs, friends/ignore, quest, magic)
 * never needed this: their arrows are redrawn every tick straight off the
 * currently-held mouse button (Menu.drawTextList/drawInteractiveTextList read mouseButton, not
 * an edge), so holding one down already scrolls continuously -- that is
 * genuine vanilla behaviour, not something added here.
 *
 * ScriptPanel and WorldsPanel are not built on Menu. Both only ever see a
 * discrete click -- mudclient hands ScriptPanel the once-per-press
 * mouseButtonClick edge, and WorldsPanel's own update() does its own
 * press-edge detection -- so an arrow on either panel used to move the list
 * by exactly one row no matter how long it was held.
 *
 * This is the fix, factored out once rather than written twice: call fire()
 * every tick with whether the pointer is currently down on the arrow. It does
 * nothing on the tick the press starts (the existing discrete-click handling
 * already scrolls once for that), then fires every RATE ticks once DELAY
 * ticks have passed -- the same wait-then-repeat shape as an OS scrollbar or
 * a spinner.
 */
final class ScrollRepeat {
   private static final int DELAY = 18;
   private static final int RATE = 5;

   private boolean down;
   private int ticks;

   /** Call once a tick with whether the mouse is currently held over the arrow. */
   boolean fire(boolean heldNow) {
      if (!heldNow) {
         this.down = false;
         this.ticks = 0;
         return false;
      }

      if (!this.down) {
         // The first tick of the press: the discrete click this arrow already
         // handles fires here instead, so this does not.
         this.down = true;
         this.ticks = 0;
         return false;
      }

      this.ticks++;
      if (this.ticks < DELAY) {
         return false;
      }

      return (this.ticks - DELAY) % RATE == 0;
   }
}
