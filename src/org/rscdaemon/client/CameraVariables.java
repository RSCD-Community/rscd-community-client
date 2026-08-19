package org.rscdaemon.client;

/* One framebuffer row of the polygon currently being rasterised by Camera:
   the left/right edge X in 24.8 fixed point and the shade (gouraud intensity
   or texture light level) at each edge. */
public class CameraVariables {
   int leftX;
   int rightX;
   int leftShade;
   int rightShade;
}
