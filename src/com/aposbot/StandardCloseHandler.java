package com.aposbot;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
 * The other APOS class its scripts import, and the reason they need it.
 *
 * An AWT Frame has no close button behaviour of its own: clicking the X fires
 * windowClosing() and, if nothing is listening, does nothing at all. Every
 * script with a settings window therefore ships this:
 *
 *    frame.addWindowListener(
 *       new StandardCloseHandler( frame, StandardCloseHandler.HIDE ) );
 *
 * HIDE and not DISPOSE, in all three call sites, because the window is reopened
 * later with the same settings still in its fields.
 *
 * As with Constants, this is written from the call sites rather than copied --
 * there is no APOS source here. The three modes are the ones the name implies
 * and are the only sensible readings of "what a standard close does": hide it,
 * throw it away, or shut everything down.
 */
public class StandardCloseHandler extends WindowAdapter {
   /** Hide the window; it can be shown again with everything still in it. */
   public static final int HIDE = 0;

   /** Dispose of the window's peer. Showing it again rebuilds it. */
   public static final int DISPOSE = 1;

   /*
    * Close the whole application.
    *
    * Deliberately not System.exit(). The end-user client runs several game
    * clients in one JVM, so a script's settings window closing must not be
    * able to take the other tabs down with it -- the same reasoning as
    * Methods.Die(). This disposes the window and does nothing further; a
    * script that wanted the process gone has to say so itself.
    */
   public static final int EXIT = 2;

   private final Window window;
   private final int mode;

   public StandardCloseHandler(Frame window, int mode) {
      this.window = window;
      this.mode = mode;
   }

   public StandardCloseHandler(Window window, int mode) {
      this.window = window;
      this.mode = mode;
   }

   public void windowClosing(WindowEvent event) {
      if (this.window == null) {
         return;
      }

      if (this.mode == HIDE) {
         this.window.setVisible(false);
      } else {
         this.window.setVisible(false);
         this.window.dispose();
      }
   }
}
