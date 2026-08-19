package org.rscdaemon.client;

/*
 * What a script wants painted over the game view, returned from Methods.ToShow().
 *
 * Three parallel arrays: the text, and where each line goes. A script builds one
 * per frame, so this stays a dumb value holder -- no drawing happens here.
 *
 * STS declared the constructor package-private, which worked only because every
 * class in that build shared the default package. Scripts here are compiled
 * outside org.rscdaemon.client, so it has to be public or ToShow() is
 * unimplementable.
 */
public class Stats {
   /** The text of each line. */
   public String[] a;
   /** Screen x of each line. */
   public int[] b;
   /** Screen y of each line. */
   public int[] c;

   /*
    * Font and colour per line, added for the APOS tier.
    *
    * STS's overlay was one size and one colour, so its Stats had three arrays
    * and its scripts baked the styling into the text with @red@ tags. APOS's
    * drawString() takes a font and an RGB colour per line and its scripts pass
    * them, so the information has to survive to the renderer.
    *
    * Added rather than folded into the existing arrays: a 2006 script calls
    * the three-argument constructor, that constructor still exists and still
    * means what it meant, and these two stay null for it. Null is the signal
    * to draw the way STS drew -- see mudclient.drawScriptOverlay().
    */
   /** Font index of each line, or null to use the overlay default. */
   public int[] fonts;
   /** RGB colour of each line, or null to use the overlay default. */
   public int[] colours;

   /*
    * Mismatched lengths leave the object empty rather than throwing, because
    * this is built from script code every frame and a script bug should not be
    * able to kill the render loop. STS behaved the same way.
    */
   public Stats(String[] messages, int[] xCoords, int[] yCoords) {
      if (messages == null || xCoords == null || yCoords == null) {
         return;
      }

      if (messages.length != xCoords.length || xCoords.length != yCoords.length) {
         return;
      }

      this.a = messages;
      this.b = xCoords;
      this.c = yCoords;
   }

   /*
    * As above, with styling. A short or missing font/colour array is dropped
    * rather than rejected: losing the styling of a frame is a smaller failure
    * than losing the text, and the same reasoning as the length check above.
    */
   public Stats(String[] messages, int[] xCoords, int[] yCoords, int[] fonts, int[] colours) {
      this(messages, xCoords, yCoords);

      if (this.a == null) {
         return;
      }

      if (fonts != null && fonts.length >= this.a.length) {
         this.fonts = fonts;
      }

      if (colours != null && colours.length >= this.a.length) {
         this.colours = colours;
      }
   }
}
