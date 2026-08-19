package org.rscdaemon.client;

import java.awt.Component;

public final class GameImageMiddleMan extends GameImage {
   public mudclient _mudclient;

   public GameImageMiddleMan(int width, int height, int k, Component component) {
      super(width, height, k, component);
   }

   @Override
   public final void drawSceneSprite(int i, int j, int k, int l, int i1, int j1, int k1) {
      if (i1 >= 50000) {
         this._mudclient.drawTeleportBubble(i, j, k, l, i1 - 50000, j1, k1);
      } else if (i1 >= 40000) {
         this._mudclient.drawGroundItem(i, j, k, l, i1 - 40000, j1, k1);
      } else if (i1 >= 20000) {
         this._mudclient.drawNpc(i, j, k, l, i1 - 20000, j1, k1);
      } else if (i1 >= 5000) {
         this._mudclient.drawPlayer(i, j, k, l, i1 - 5000, j1, k1);
      } else {
         super.spriteClip1(i, j, k, l, i1);
      }
   }
}
