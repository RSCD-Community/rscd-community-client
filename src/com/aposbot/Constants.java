package com.aposbot;

import java.awt.Image;
import java.util.Collections;
import java.util.List;

/*
 * A stand-in for the one APOS class its scripts import by name.
 *
 * Ten of the sixteen surviving APOS scripts open an AWT settings window, and
 * three of those decorate it:
 *
 *    frame.setIconImages( Constants.ICONS );
 *
 * That is the entire use. The class is here, under APOS's own package name,
 * because an import that does not resolve is a compile error and the whole
 * point of this tier is that a script written in 2016 compiles untouched.
 *
 * Nothing was copied from APOS to write it -- there is no APOS source in this
 * project. What exists here is the smallest thing the call sites need, which
 * is a list of images that Frame.setIconImages() will accept.
 */
public final class Constants {
   /*
    * The window icons a script's settings frame should wear.
    *
    * Empty, because this client has no icon of its own yet: it ships no image
    * resource and its own window is left with the platform default. An empty
    * list is what setIconImages() documents as "use the default", so the three
    * scripts that pass it get exactly what they would have got had they never
    * called it. When the client gains an icon, it goes here and those three
    * windows pick it up with no change to them.
    */
   public static final List<Image> ICONS = Collections.unmodifiableList(new java.util.ArrayList<Image>());

   private Constants() {
   }
}
