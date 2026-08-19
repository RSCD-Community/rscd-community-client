package org.rscdaemon.client;
import org.rscdaemon.client.util.Config;

import java.io.BufferedInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import org.rscdaemon.client.util.Assets;
import org.rscdaemon.client.util.MemoryArchive;
import org.rscdaemon.client.entityhandling.EntityHandler;
import org.rscdaemon.client.model.Sector;
import org.rscdaemon.client.util.DataConversions;

public class EngineHandle {
   private MemoryArchive tileArchive;
   public int[][] objectDirs;
   public int[] selectedX;
   public int[] selectedY;
   /*
    * Collision bits per tile: 1/2/4/8 are walls on the four tile edges (a
    * wall sets one bit on its own tile and the opposing bit on the
    * neighbour), 16 and 32 the two diagonal directions, 64 a solid object or
    * blocked overlay, 128 a water-type overlay.
    */
   public int[][] walkableValue;
   public boolean playerIsAlive;
   /* Scratch model the whole landscape is assembled in, then split 8x8 into
      the terrainModels/wallModels/roofModels pieces the camera culls. */
   public Model landscapeModel;
   private GameImage gameImage;
   private Camera camera;
   private boolean requiresClean;
   /* The loaded 96x96 area is four 48x48 sectors: 0 is x<48,y<48, 1 is
      x>=48, 2 is y>=48, 3 is both -- hence the repeated 48-split below. */
   private Sector[] sectors;
   Model[][] wallModels;
   /* Elevation per tile corner. While roofs are built, a wall marks its two
      corners by adding 80000 + its height so the roof sits on the wall top;
      the flag is stripped again at the end of buildLandscape. */
   int[][] elevationMap;
   Model[] terrainModels;
   private int[] groundTextureArray;
   Model[][] roofModels;

   /* Breadth-first flood fill over the collision map. tmpTiles records the
      direction each tile was entered from, then the trail is walked
      backwards from the goal to emit one waypoint per turn. */
   public int getStepCount(int walkSectionX, int walkSectionY, int x1, int y1, int x2, int y2, int[] walkSectionXArray, int[] walkSectionYArray, boolean flag) {
      int[][] tmpTiles = new int[96][96];

      for (int k1 = 0; k1 < 96; k1++) {
         for (int l1 = 0; l1 < 96; l1++) {
            tmpTiles[k1][l1] = 0;
         }
      }

      int i2 = 0;
      int j2 = 0;
      int k2 = walkSectionX;
      int l2 = walkSectionY;
      tmpTiles[walkSectionX][walkSectionY] = 99;
      walkSectionXArray[i2] = walkSectionX;
      walkSectionYArray[i2++] = walkSectionY;
      int i3 = walkSectionXArray.length;
      boolean flag1 = false;

      while (j2 != i2) {
         k2 = walkSectionXArray[j2];
         l2 = walkSectionYArray[j2];
         j2 = (j2 + 1) % i3;
         if (k2 >= x1 && k2 <= x2 && l2 >= y1 && l2 <= y2) {
            flag1 = true;
            break;
         }

         if (flag) {
            if (k2 > 0 && k2 - 1 >= x1 && k2 - 1 <= x2 && l2 >= y1 && l2 <= y2 && (this.walkableValue[k2 - 1][l2] & 8) == 0) {
               flag1 = true;
               break;
            }

            if (k2 < 95 && k2 + 1 >= x1 && k2 + 1 <= x2 && l2 >= y1 && l2 <= y2 && (this.walkableValue[k2 + 1][l2] & 2) == 0) {
               flag1 = true;
               break;
            }

            if (l2 > 0 && k2 >= x1 && k2 <= x2 && l2 - 1 >= y1 && l2 - 1 <= y2 && (this.walkableValue[k2][l2 - 1] & 4) == 0) {
               flag1 = true;
               break;
            }

            if (l2 < 95 && k2 >= x1 && k2 <= x2 && l2 + 1 >= y1 && l2 + 1 <= y2 && (this.walkableValue[k2][l2 + 1] & 1) == 0) {
               flag1 = true;
               break;
            }
         }

         if (k2 > 0 && tmpTiles[k2 - 1][l2] == 0 && (this.walkableValue[k2 - 1][l2] & 120) == 0) {
            walkSectionXArray[i2] = k2 - 1;
            walkSectionYArray[i2] = l2;
            i2 = (i2 + 1) % i3;
            tmpTiles[k2 - 1][l2] = 2;
         }

         if (k2 < 95 && tmpTiles[k2 + 1][l2] == 0 && (this.walkableValue[k2 + 1][l2] & 114) == 0) {
            walkSectionXArray[i2] = k2 + 1;
            walkSectionYArray[i2] = l2;
            i2 = (i2 + 1) % i3;
            tmpTiles[k2 + 1][l2] = 8;
         }

         if (l2 > 0 && tmpTiles[k2][l2 - 1] == 0 && (this.walkableValue[k2][l2 - 1] & 116) == 0) {
            walkSectionXArray[i2] = k2;
            walkSectionYArray[i2] = l2 - 1;
            i2 = (i2 + 1) % i3;
            tmpTiles[k2][l2 - 1] = 1;
         }

         if (l2 < 95 && tmpTiles[k2][l2 + 1] == 0 && (this.walkableValue[k2][l2 + 1] & 113) == 0) {
            walkSectionXArray[i2] = k2;
            walkSectionYArray[i2] = l2 + 1;
            i2 = (i2 + 1) % i3;
            tmpTiles[k2][l2 + 1] = 4;
         }

         if (k2 > 0
            && l2 > 0
            && (this.walkableValue[k2][l2 - 1] & 116) == 0
            && (this.walkableValue[k2 - 1][l2] & 120) == 0
            && (this.walkableValue[k2 - 1][l2 - 1] & 124) == 0
            && tmpTiles[k2 - 1][l2 - 1] == 0) {
            walkSectionXArray[i2] = k2 - 1;
            walkSectionYArray[i2] = l2 - 1;
            i2 = (i2 + 1) % i3;
            tmpTiles[k2 - 1][l2 - 1] = 3;
         }

         if (k2 < 95
            && l2 > 0
            && (this.walkableValue[k2][l2 - 1] & 116) == 0
            && (this.walkableValue[k2 + 1][l2] & 114) == 0
            && (this.walkableValue[k2 + 1][l2 - 1] & 118) == 0
            && tmpTiles[k2 + 1][l2 - 1] == 0) {
            walkSectionXArray[i2] = k2 + 1;
            walkSectionYArray[i2] = l2 - 1;
            i2 = (i2 + 1) % i3;
            tmpTiles[k2 + 1][l2 - 1] = 9;
         }

         if (k2 > 0
            && l2 < 95
            && (this.walkableValue[k2][l2 + 1] & 113) == 0
            && (this.walkableValue[k2 - 1][l2] & 120) == 0
            && (this.walkableValue[k2 - 1][l2 + 1] & 121) == 0
            && tmpTiles[k2 - 1][l2 + 1] == 0) {
            walkSectionXArray[i2] = k2 - 1;
            walkSectionYArray[i2] = l2 + 1;
            i2 = (i2 + 1) % i3;
            tmpTiles[k2 - 1][l2 + 1] = 6;
         }

         if (k2 < 95
            && l2 < 95
            && (this.walkableValue[k2][l2 + 1] & 113) == 0
            && (this.walkableValue[k2 + 1][l2] & 114) == 0
            && (this.walkableValue[k2 + 1][l2 + 1] & 115) == 0
            && tmpTiles[k2 + 1][l2 + 1] == 0) {
            walkSectionXArray[i2] = k2 + 1;
            walkSectionYArray[i2] = l2 + 1;
            i2 = (i2 + 1) % i3;
            tmpTiles[k2 + 1][l2 + 1] = 12;
         }
      }

      if (!flag1) {
         return -1;
      } else {
         j2 = 0;
         walkSectionXArray[j2] = k2;
         walkSectionYArray[j2++] = l2;

         int k3;
         for (int j3 = k3 = tmpTiles[k2][l2]; k2 != walkSectionX || l2 != walkSectionY; j3 = tmpTiles[k2][l2]) {
            if (j3 != k3) {
               k3 = j3;
               walkSectionXArray[j2] = k2;
               walkSectionYArray[j2++] = l2;
            }

            if ((j3 & 2) != 0) {
               k2++;
            } else if ((j3 & 8) != 0) {
               k2--;
            }

            if ((j3 & 1) != 0) {
               l2++;
            } else if ((j3 & 4) != 0) {
               l2--;
            }
         }

         return j2;
      }
   }

   public int getGroundElevation(int i, int j) {
      if (i >= 0 && i < 96 && j >= 0 && j < 96) {
         byte byte0 = 0;
         if (i >= 48 && j < 48) {
            byte0 = 1;
            i -= 48;
         } else if (i < 48 && j >= 48) {
            byte0 = 2;
            j -= 48;
         } else if (i >= 48 && j >= 48) {
            byte0 = 3;
            i -= 48;
            j -= 48;
         }

         return (this.sectors[byte0].getTile(i, j).groundElevation & 0xFF) * 3;
      } else {
         return 0;
      }
   }

   public void updateObject(int x, int y, int k, int l) {
      if (x >= 0 && y >= 0 && x < 95 && y < 95) {
         if (EntityHandler.getObjectDef(k).getType() == 1 || EntityHandler.getObjectDef(k).getType() == 2) {
            int i1;
            int j1;
            if (l != 0 && l != 4) {
               j1 = EntityHandler.getObjectDef(k).getWidth();
               i1 = EntityHandler.getObjectDef(k).getHeight();
            } else {
               i1 = EntityHandler.getObjectDef(k).getWidth();
               j1 = EntityHandler.getObjectDef(k).getHeight();
            }

            for (int k1 = x; k1 < x + i1; k1++) {
               for (int l1 = y; l1 < y + j1; l1++) {
                  if (EntityHandler.getObjectDef(k).getType() == 1) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] & 65471;
                  } else if (l == 0) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] & 65533;
                     if (k1 > 0) {
                        this.andMinusWalkable(k1 - 1, l1, 8);
                     }
                  } else if (l == 2) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] & 65531;
                     if (l1 < 95) {
                        this.andMinusWalkable(k1, l1 + 1, 1);
                     }
                  } else if (l == 4) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] & 65527;
                     if (k1 < 95) {
                        this.andMinusWalkable(k1 + 1, l1, 2);
                     }
                  } else if (l == 6) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] & 65534;
                     if (l1 > 0) {
                        this.andMinusWalkable(k1, l1 - 1, 4);
                     }
                  }
               }
            }

            this.updateShadows(x, y, i1, j1);
         }
      }
   }

   public int getAveragedElevation(int i, int j) {
      int k = i >> 7;
      int l = j >> 7;
      int i1 = i & 127;
      int j1 = j & 127;
      if (k >= 0 && l >= 0 && k < 95 && l < 95) {
         int k1;
         int l1;
         int i2;
         if (i1 <= 128 - j1) {
            k1 = this.getGroundElevation(k, l);
            l1 = this.getGroundElevation(k + 1, l) - k1;
            i2 = this.getGroundElevation(k, l + 1) - k1;
         } else {
            k1 = this.getGroundElevation(k + 1, l + 1);
            l1 = this.getGroundElevation(k, l + 1) - k1;
            i2 = this.getGroundElevation(k + 1, l) - k1;
            i1 = 128 - i1;
            j1 = 128 - j1;
         }

         return k1 + l1 * i1 / 128 + i2 * j1 / 128;
      } else {
         return 0;
      }
   }

   /* Overlay 250 is a placeholder written at sector seams; it resolves to
      water (2), or to 9 on an edge tile whose neighbour across the seam
      isn't water. */
   public void resolveSectorEdgeOverlays() {
      for (int i = 0; i < 96; i++) {
         for (int j = 0; j < 96; j++) {
            if (this.getGroundTexturesOverlay(i, j) == 250) {
               if (i == 47 && this.getGroundTexturesOverlay(i + 1, j) != 250 && this.getGroundTexturesOverlay(i + 1, j) != 2) {
                  this.setGroundTexturesOverlay(i, j, 9);
               } else if (j == 47 && this.getGroundTexturesOverlay(i, j + 1) != 250 && this.getGroundTexturesOverlay(i, j + 1) != 2) {
                  this.setGroundTexturesOverlay(i, j, 9);
               } else {
                  this.setGroundTexturesOverlay(i, j, 2);
               }
            }
         }
      }
   }

   public void loadArea(int i, int j, int k) {
      this.garbageCollect();
      int l = (i + 24) / 48;
      int i1 = (j + 24) / 48;
      this.buildLandscape(i, j, k, true);
      if (k == 0) {
         this.buildLandscape(i, j, 1, false);
         this.buildLandscape(i, j, 2, false);
         this.loadSection(l - 1, i1 - 1, k, 0);
         this.loadSection(l, i1 - 1, k, 1);
         this.loadSection(l - 1, i1, k, 2);
         this.loadSection(l, i1, k, 3);
         this.resolveSectorEdgeOverlays();
      }
   }

   public void setVertexAmbience(int i, int j, int k, int l, int i1) {
      Model model = this.terrainModels[i + j * 8];

      for (int j1 = 0; j1 < model.vertexCount; j1++) {
         if (model.vertexX[j1] == k * 128 && model.vertexZ[j1] == l * 128) {
            model.setVertexAmbience(j1, i1);
            return;
         }
      }
   }

   public void raiseWallElevation(int i, int j, int k, int l, int i1) {
      int j1 = EntityHandler.getDoorDef(i).getModelVar1();
      if (this.elevationMap[j][k] < 80000) {
         this.elevationMap[j][k] = this.elevationMap[j][k] + 80000 + j1;
      }

      if (this.elevationMap[l][i1] < 80000) {
         this.elevationMap[l][i1] = this.elevationMap[l][i1] + 80000 + j1;
      }
   }

   public void setGroundTexturesOverlay(int i, int j, int k) {
      if (i >= 0 && i < 96 && j >= 0 && j < 96) {
         byte byte0 = 0;
         if (i >= 48 && j < 48) {
            byte0 = 1;
            i -= 48;
         } else if (i < 48 && j >= 48) {
            byte0 = 2;
            j -= 48;
         } else if (i >= 48 && j >= 48) {
            byte0 = 3;
            i -= 48;
            j -= 48;
         }

         this.sectors[byte0].getTile(i, j).groundOverlay = (byte)k;
      }
   }

   public void orWalkable(int i, int j, int k) {
      this.walkableValue[i][j] = this.walkableValue[i][j] | k;
   }

   public void garbageCollect() {
      if (this.requiresClean) {
         this.camera.cleanupModels();
      }

      for (int i = 0; i < 64; i++) {
         this.terrainModels[i] = null;

         for (int j = 0; j < 4; j++) {
            this.wallModels[j][i] = null;
         }

         for (int k = 0; k < 4; k++) {
            this.roofModels[k][i] = null;
         }
      }

      System.gc();
   }

   public void updateShadows(int i, int j, int k, int l) {
      if (i >= 1 && j >= 1 && i + k < 96 && j + l < 96) {
         for (int i1 = i; i1 <= i + k; i1++) {
            for (int j1 = j; j1 <= j + l; j1++) {
               if ((this.getWalkableValue(i1, j1) & 99) == 0
                  && (this.getWalkableValue(i1 - 1, j1) & 89) == 0
                  && (this.getWalkableValue(i1, j1 - 1) & 86) == 0
                  && (this.getWalkableValue(i1 - 1, j1 - 1) & 108) == 0) {
                  this.setTileAmbience(i1, j1, 0);
               } else {
                  this.setTileAmbience(i1, j1, 35);
               }
            }
         }
      }
   }

   public void registerDoorCollision(int i, int j, int k, int l) {
      if (i >= 0 && j >= 0 && i < 95 && j < 95) {
         if (EntityHandler.getDoorDef(l).getDoorType() == 1) {
            if (k == 0) {
               this.walkableValue[i][j] = this.walkableValue[i][j] | 1;
               if (j > 0) {
                  this.orWalkable(i, j - 1, 4);
               }
            } else if (k == 1) {
               this.walkableValue[i][j] = this.walkableValue[i][j] | 2;
               if (i > 0) {
                  this.orWalkable(i - 1, j, 8);
               }
            } else if (k == 2) {
               this.walkableValue[i][j] = this.walkableValue[i][j] | 16;
            } else if (k == 3) {
               this.walkableValue[i][j] = this.walkableValue[i][j] | 32;
            }

            this.updateShadows(i, j, 1, 1);
         }
      }
   }

   public void buildLandscape(int i, int j, int k, boolean flag) {
      int l = (i + 24) / 48;
      int i1 = (j + 24) / 48;
      this.loadSection(l - 1, i1 - 1, k, 0);
      this.loadSection(l, i1 - 1, k, 1);
      this.loadSection(l - 1, i1, k, 2);
      this.loadSection(l, i1, k, 3);
      this.resolveSectorEdgeOverlays();
      if (this.landscapeModel == null) {
         this.landscapeModel = new Model(18688, 18688, true, true, false, false, true);
      }

      if (flag) {
         this.gameImage.clearScreen();

         for (int j1 = 0; j1 < 96; j1++) {
            for (int l1 = 0; l1 < 96; l1++) {
               this.walkableValue[j1][l1] = 0;
            }
         }

         Model model = this.landscapeModel;
         model.clear();

         for (int j2 = 0; j2 < 96; j2++) {
            for (int i3 = 0; i3 < 96; i3++) {
               int i4 = -this.getGroundElevation(j2, i3);
               if (this.getGroundTexturesOverlay(j2, i3) > 0 && EntityHandler.getTileDef(this.getGroundTexturesOverlay(j2, i3) - 1).getUnknown() == 4) {
                  i4 = 0;
               }

               if (this.getGroundTexturesOverlay(j2 - 1, i3) > 0 && EntityHandler.getTileDef(this.getGroundTexturesOverlay(j2 - 1, i3) - 1).getUnknown() == 4) {
                  i4 = 0;
               }

               if (this.getGroundTexturesOverlay(j2, i3 - 1) > 0 && EntityHandler.getTileDef(this.getGroundTexturesOverlay(j2, i3 - 1) - 1).getUnknown() == 4) {
                  i4 = 0;
               }

               if (this.getGroundTexturesOverlay(j2 - 1, i3 - 1) > 0
                  && EntityHandler.getTileDef(this.getGroundTexturesOverlay(j2 - 1, i3 - 1) - 1).getUnknown() == 4) {
                  i4 = 0;
               }

               int j5 = model.getOrAddVertex(j2 * 128, i4, i3 * 128);
               int j7 = (int)(Math.random() * 10.0) - 5;
               model.setVertexAmbience(j5, j7);
            }
         }

         for (int j3 = 0; j3 < 95; j3++) {
            for (int j4 = 0; j4 < 95; j4++) {
               int k5 = this.getGroundTexture(j3, j4);
               int k7 = this.groundTextureArray[k5];
               int i10 = k7;
               int k12 = k7;
               int l14 = 0;
               if (k == 1 || k == 2) {
                  k7 = 12345678;
                  i10 = 12345678;
                  k12 = 12345678;
               }

               if (this.getGroundTexturesOverlay(j3, j4) > 0) {
                  int l16 = this.getGroundTexturesOverlay(j3, j4);
                  int l5 = EntityHandler.getTileDef(l16 - 1).getUnknown();
                  int i19 = this.getOverlayBlendType(j3, j4);
                  k7 = i10 = EntityHandler.getTileDef(l16 - 1).getColour();
                  if (l5 == 4) {
                     k7 = 1;
                     i10 = 1;
                     if (l16 == 12) {
                        k7 = 31;
                        i10 = 31;
                     }
                  }

                  if (l5 == 5) {
                     if (this.getDiagonalWalls(j3, j4) > 0 && this.getDiagonalWalls(j3, j4) < 24000) {
                        if (this.getOverlayIfRequired(j3 - 1, j4, k12) != 12345678 && this.getOverlayIfRequired(j3, j4 - 1, k12) != 12345678) {
                           k7 = this.getOverlayIfRequired(j3 - 1, j4, k12);
                           l14 = 0;
                        } else if (this.getOverlayIfRequired(j3 + 1, j4, k12) != 12345678 && this.getOverlayIfRequired(j3, j4 + 1, k12) != 12345678) {
                           i10 = this.getOverlayIfRequired(j3 + 1, j4, k12);
                           l14 = 0;
                        } else if (this.getOverlayIfRequired(j3 + 1, j4, k12) != 12345678 && this.getOverlayIfRequired(j3, j4 - 1, k12) != 12345678) {
                           i10 = this.getOverlayIfRequired(j3 + 1, j4, k12);
                           l14 = 1;
                        } else if (this.getOverlayIfRequired(j3 - 1, j4, k12) != 12345678 && this.getOverlayIfRequired(j3, j4 + 1, k12) != 12345678) {
                           k7 = this.getOverlayIfRequired(j3 - 1, j4, k12);
                           l14 = 1;
                        }
                     }
                  } else if (l5 != 2 || this.getDiagonalWalls(j3, j4) > 0 && this.getDiagonalWalls(j3, j4) < 24000) {
                     if (this.getOverlayBlendType(j3 - 1, j4) != i19 && this.getOverlayBlendType(j3, j4 - 1) != i19) {
                        k7 = k12;
                        l14 = 0;
                     } else if (this.getOverlayBlendType(j3 + 1, j4) != i19 && this.getOverlayBlendType(j3, j4 + 1) != i19) {
                        i10 = k12;
                        l14 = 0;
                     } else if (this.getOverlayBlendType(j3 + 1, j4) != i19 && this.getOverlayBlendType(j3, j4 - 1) != i19) {
                        i10 = k12;
                        l14 = 1;
                     } else if (this.getOverlayBlendType(j3 - 1, j4) != i19 && this.getOverlayBlendType(j3, j4 + 1) != i19) {
                        k7 = k12;
                        l14 = 1;
                     }
                  }

                  if (EntityHandler.getTileDef(l16 - 1).getObjectType() != 0) {
                     this.walkableValue[j3][j4] = this.walkableValue[j3][j4] | 64;
                  }

                  if (EntityHandler.getTileDef(l16 - 1).getUnknown() == 2) {
                     this.walkableValue[j3][j4] = this.walkableValue[j3][j4] | 128;
                  }
               }

               this.drawMinimapTile(j3, j4, l14, k7, i10);
               int i17 = this.getGroundElevation(j3 + 1, j4 + 1)
                  - this.getGroundElevation(j3 + 1, j4)
                  + this.getGroundElevation(j3, j4 + 1)
                  - this.getGroundElevation(j3, j4);
               if (k7 != i10 || i17 != 0) {
                  int[] ai = new int[3];
                  int[] ai7 = new int[3];
                  if (l14 == 0) {
                     if (k7 != 12345678) {
                        ai[0] = j4 + j3 * 96 + 96;
                        ai[1] = j4 + j3 * 96;
                        ai[2] = j4 + j3 * 96 + 1;
                        int l21 = model.addFace(3, ai, 12345678, k7);
                        this.selectedX[l21] = j3;
                        this.selectedY[l21] = j4;
                        model.faceTag[l21] = 200000 + l21;
                     }

                     if (i10 != 12345678) {
                        ai7[0] = j4 + j3 * 96 + 1;
                        ai7[1] = j4 + j3 * 96 + 96 + 1;
                        ai7[2] = j4 + j3 * 96 + 96;
                        int i22 = model.addFace(3, ai7, 12345678, i10);
                        this.selectedX[i22] = j3;
                        this.selectedY[i22] = j4;
                        model.faceTag[i22] = 200000 + i22;
                     }
                  } else {
                     if (k7 != 12345678) {
                        ai[0] = j4 + j3 * 96 + 1;
                        ai[1] = j4 + j3 * 96 + 96 + 1;
                        ai[2] = j4 + j3 * 96;
                        int j22 = model.addFace(3, ai, 12345678, k7);
                        this.selectedX[j22] = j3;
                        this.selectedY[j22] = j4;
                        model.faceTag[j22] = 200000 + j22;
                     }

                     if (i10 != 12345678) {
                        ai7[0] = j4 + j3 * 96 + 96;
                        ai7[1] = j4 + j3 * 96;
                        ai7[2] = j4 + j3 * 96 + 96 + 1;
                        int k22 = model.addFace(3, ai7, 12345678, i10);
                        this.selectedX[k22] = j3;
                        this.selectedY[k22] = j4;
                        model.faceTag[k22] = 200000 + k22;
                     }
                  }
               } else if (k7 != 12345678) {
                  int[] ai1 = new int[]{j4 + j3 * 96 + 96, j4 + j3 * 96, j4 + j3 * 96 + 1, j4 + j3 * 96 + 96 + 1};
                  int l19 = model.addFace(4, ai1, 12345678, k7);
                  this.selectedX[l19] = j3;
                  this.selectedY[l19] = j4;
                  model.faceTag[l19] = 200000 + l19;
               }
            }
         }

         for (int k4 = 1; k4 < 95; k4++) {
            for (int i6 = 1; i6 < 95; i6++) {
               if (this.getGroundTexturesOverlay(k4, i6) > 0 && EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4, i6) - 1).getUnknown() == 4) {
                  int l7 = EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4, i6) - 1).getColour();
                  int j10 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6), i6 * 128);
                  int l12 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6), i6 * 128);
                  int i15 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6 + 1), (i6 + 1) * 128);
                  int j17 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6 + 1), (i6 + 1) * 128);
                  int[] ai2 = new int[]{j10, l12, i15, j17};
                  int i20 = model.addFace(4, ai2, l7, 12345678);
                  this.selectedX[i20] = k4;
                  this.selectedY[i20] = i6;
                  model.faceTag[i20] = 200000 + i20;
                  this.drawMinimapTile(k4, i6, 0, l7, l7);
               } else if (this.getGroundTexturesOverlay(k4, i6) == 0 || EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4, i6) - 1).getUnknown() != 3) {
                  if (this.getGroundTexturesOverlay(k4, i6 + 1) > 0
                     && EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4, i6 + 1) - 1).getUnknown() == 4) {
                     int i8 = EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4, i6 + 1) - 1).getColour();
                     int k10 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6), i6 * 128);
                     int i13 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6), i6 * 128);
                     int j15 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6 + 1), (i6 + 1) * 128);
                     int k17 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6 + 1), (i6 + 1) * 128);
                     int[] ai3 = new int[]{k10, i13, j15, k17};
                     int j20 = model.addFace(4, ai3, i8, 12345678);
                     this.selectedX[j20] = k4;
                     this.selectedY[j20] = i6;
                     model.faceTag[j20] = 200000 + j20;
                     this.drawMinimapTile(k4, i6, 0, i8, i8);
                  }

                  if (this.getGroundTexturesOverlay(k4, i6 - 1) > 0
                     && EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4, i6 - 1) - 1).getUnknown() == 4) {
                     int j8 = EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4, i6 - 1) - 1).getColour();
                     int l10 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6), i6 * 128);
                     int j13 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6), i6 * 128);
                     int k15 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6 + 1), (i6 + 1) * 128);
                     int l17 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6 + 1), (i6 + 1) * 128);
                     int[] ai4 = new int[]{l10, j13, k15, l17};
                     int k20 = model.addFace(4, ai4, j8, 12345678);
                     this.selectedX[k20] = k4;
                     this.selectedY[k20] = i6;
                     model.faceTag[k20] = 200000 + k20;
                     this.drawMinimapTile(k4, i6, 0, j8, j8);
                  }

                  if (this.getGroundTexturesOverlay(k4 + 1, i6) > 0
                     && EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4 + 1, i6) - 1).getUnknown() == 4) {
                     int k8 = EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4 + 1, i6) - 1).getColour();
                     int i11 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6), i6 * 128);
                     int k13 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6), i6 * 128);
                     int l15 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6 + 1), (i6 + 1) * 128);
                     int i18 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6 + 1), (i6 + 1) * 128);
                     int[] ai5 = new int[]{i11, k13, l15, i18};
                     int l20 = model.addFace(4, ai5, k8, 12345678);
                     this.selectedX[l20] = k4;
                     this.selectedY[l20] = i6;
                     model.faceTag[l20] = 200000 + l20;
                     this.drawMinimapTile(k4, i6, 0, k8, k8);
                  }

                  if (this.getGroundTexturesOverlay(k4 - 1, i6) > 0
                     && EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4 - 1, i6) - 1).getUnknown() == 4) {
                     int l8 = EntityHandler.getTileDef(this.getGroundTexturesOverlay(k4 - 1, i6) - 1).getColour();
                     int j11 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6), i6 * 128);
                     int l13 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6), i6 * 128);
                     int i16 = model.getOrAddVertex((k4 + 1) * 128, -this.getGroundElevation(k4 + 1, i6 + 1), (i6 + 1) * 128);
                     int j18 = model.getOrAddVertex(k4 * 128, -this.getGroundElevation(k4, i6 + 1), (i6 + 1) * 128);
                     int[] ai6 = new int[]{j11, l13, i16, j18};
                     int i21 = model.addFace(4, ai6, l8, 12345678);
                     this.selectedX[i21] = k4;
                     this.selectedY[i21] = i6;
                     model.faceTag[i21] = 200000 + i21;
                     this.drawMinimapTile(k4, i6, 0, l8, l8);
                  }
               }
            }
         }

         model.setLight(true, 40, 48, -50, -10, -50);
         this.terrainModels = this.landscapeModel.split(0, 0, 1536, 1536, 8, 64, 233, false);

         for (int j6 = 0; j6 < 64; j6++) {
            this.camera.addModel(this.terrainModels[j6]);
         }

         for (int i9 = 0; i9 < 96; i9++) {
            for (int k11 = 0; k11 < 96; k11++) {
               this.elevationMap[i9][k11] = this.getGroundElevation(i9, k11);
            }
         }
      }

      this.landscapeModel.clear();
      int k1 = 6316128;

      for (int i2 = 0; i2 < 95; i2++) {
         for (int k2 = 0; k2 < 95; k2++) {
            int k3 = this.getVerticalWall(i2, k2);
            if (k3 > 0 && EntityHandler.getDoorDef(k3 - 1).getUnknown() == 0) {
               this.buildWall(this.landscapeModel, k3 - 1, i2, k2, i2 + 1, k2);
               if (flag && EntityHandler.getDoorDef(k3 - 1).getDoorType() != 0) {
                  this.walkableValue[i2][k2] = this.walkableValue[i2][k2] | 1;
                  if (k2 > 0) {
                     this.orWalkable(i2, k2 - 1, 4);
                  }
               }

               if (flag) {
                  this.gameImage.drawLineX(i2 * 3, k2 * 3, 3, k1);
               }
            }

            k3 = this.getHorizontalWall(i2, k2);
            if (k3 > 0 && EntityHandler.getDoorDef(k3 - 1).getUnknown() == 0) {
               this.buildWall(this.landscapeModel, k3 - 1, i2, k2, i2, k2 + 1);
               if (flag && EntityHandler.getDoorDef(k3 - 1).getDoorType() != 0) {
                  this.walkableValue[i2][k2] = this.walkableValue[i2][k2] | 2;
                  if (i2 > 0) {
                     this.orWalkable(i2 - 1, k2, 8);
                  }
               }

               if (flag) {
                  this.gameImage.drawLineY(i2 * 3, k2 * 3, 3, k1);
               }
            }

            k3 = this.getDiagonalWalls(i2, k2);
            if (k3 > 0 && k3 < 12000 && EntityHandler.getDoorDef(k3 - 1).getUnknown() == 0) {
               this.buildWall(this.landscapeModel, k3 - 1, i2, k2, i2 + 1, k2 + 1);
               if (flag && EntityHandler.getDoorDef(k3 - 1).getDoorType() != 0) {
                  this.walkableValue[i2][k2] = this.walkableValue[i2][k2] | 32;
               }

               if (flag) {
                  this.gameImage.setPixelColour(i2 * 3, k2 * 3, k1);
                  this.gameImage.setPixelColour(i2 * 3 + 1, k2 * 3 + 1, k1);
                  this.gameImage.setPixelColour(i2 * 3 + 2, k2 * 3 + 2, k1);
               }
            }

            if (k3 > 12000 && k3 < 24000 && EntityHandler.getDoorDef(k3 - 12001).getUnknown() == 0) {
               this.buildWall(this.landscapeModel, k3 - 12001, i2 + 1, k2, i2, k2 + 1);
               if (flag && EntityHandler.getDoorDef(k3 - 12001).getDoorType() != 0) {
                  this.walkableValue[i2][k2] = this.walkableValue[i2][k2] | 16;
               }

               if (flag) {
                  this.gameImage.setPixelColour(i2 * 3 + 2, k2 * 3, k1);
                  this.gameImage.setPixelColour(i2 * 3 + 1, k2 * 3 + 1, k1);
                  this.gameImage.setPixelColour(i2 * 3, k2 * 3 + 2, k1);
               }
            }
         }
      }

      if (flag) {
         this.gameImage.storeSpriteHoriz(1999, 0, 0, 285, 285);
      }

      this.landscapeModel.setLight(false, 60, 24, -50, -10, -50);
      this.wallModels[k] = this.landscapeModel.split(0, 0, 1536, 1536, 8, 64, 338, true);

      for (int l2 = 0; l2 < 64; l2++) {
         this.camera.addModel(this.wallModels[k][l2]);
      }

      for (int l3 = 0; l3 < 95; l3++) {
         for (int l4 = 0; l4 < 95; l4++) {
            int k6 = this.getVerticalWall(l3, l4);
            if (k6 > 0) {
               this.raiseWallElevation(k6 - 1, l3, l4, l3 + 1, l4);
            }

            k6 = this.getHorizontalWall(l3, l4);
            if (k6 > 0) {
               this.raiseWallElevation(k6 - 1, l3, l4, l3, l4 + 1);
            }

            k6 = this.getDiagonalWalls(l3, l4);
            if (k6 > 0 && k6 < 12000) {
               this.raiseWallElevation(k6 - 1, l3, l4, l3 + 1, l4 + 1);
            }

            if (k6 > 12000 && k6 < 24000) {
               this.raiseWallElevation(k6 - 12001, l3 + 1, l4, l3, l4 + 1);
            }
         }
      }

      for (int i5 = 1; i5 < 95; i5++) {
         for (int l6 = 1; l6 < 95; l6++) {
            int j9 = this.getRoofTexture(i5, l6);
            if (j9 > 0) {
               int j16 = i5 + 1;
               int j19 = i5 + 1;
               int j21 = l6 + 1;
               int j23 = l6 + 1;
               int l23 = 0;
               int j24 = this.elevationMap[i5][l6];
               int l24 = this.elevationMap[j16][l6];
               int j25 = this.elevationMap[j19][j21];
               int l25 = this.elevationMap[i5][j23];
               if (j24 > 80000) {
                  j24 -= 80000;
               }

               if (l24 > 80000) {
                  l24 -= 80000;
               }

               if (j25 > 80000) {
                  j25 -= 80000;
               }

               if (l25 > 80000) {
                  l25 -= 80000;
               }

               if (j24 > l23) {
                  l23 = j24;
               }

               if (l24 > l23) {
                  l23 = l24;
               }

               if (j25 > l23) {
                  l23 = j25;
               }

               if (l25 > l23) {
                  l23 = l25;
               }

               if (l23 >= 80000) {
                  l23 -= 80000;
               }

               if (j24 < 80000) {
                  this.elevationMap[i5][l6] = l23;
               } else {
                  this.elevationMap[i5][l6] = this.elevationMap[i5][l6] - 80000;
               }

               if (l24 < 80000) {
                  this.elevationMap[j16][l6] = l23;
               } else {
                  this.elevationMap[j16][l6] = this.elevationMap[j16][l6] - 80000;
               }

               if (j25 < 80000) {
                  this.elevationMap[j19][j21] = l23;
               } else {
                  this.elevationMap[j19][j21] = this.elevationMap[j19][j21] - 80000;
               }

               if (l25 < 80000) {
                  this.elevationMap[i5][j23] = l23;
               } else {
                  this.elevationMap[i5][j23] = this.elevationMap[i5][j23] - 80000;
               }
            }
         }
      }

      this.landscapeModel.clear();

      for (int i7 = 1; i7 < 95; i7++) {
         for (int k9 = 1; k9 < 95; k9++) {
            int i12 = this.getRoofTexture(i7, k9);
            if (i12 > 0) {
               int l18 = i7 + 1;
               int k21 = i7 + 1;
               int i23 = k9 + 1;
               int i24 = k9 + 1;
               int k24 = i7 * 128;
               int i25 = k9 * 128;
               int k25 = k24 + 128;
               int i26 = i25 + 128;
               int j26 = k24;
               int k26 = i25;
               int l26 = k25;
               int i27 = i26;
               int j27 = this.elevationMap[i7][k9];
               int k27 = this.elevationMap[l18][k9];
               int l27 = this.elevationMap[k21][i23];
               int i28 = this.elevationMap[i7][i24];
               int j28 = EntityHandler.getElevationDef(i12 - 1).getUnknown1();
               if (this.isFullyRoofed(i7, k9) && j27 < 80000) {
                  j27 += j28 + 80000;
                  this.elevationMap[i7][k9] = j27;
               }

               if (this.isFullyRoofed(l18, k9) && k27 < 80000) {
                  k27 += j28 + 80000;
                  this.elevationMap[l18][k9] = k27;
               }

               if (this.isFullyRoofed(k21, i23) && l27 < 80000) {
                  l27 += j28 + 80000;
                  this.elevationMap[k21][i23] = l27;
               }

               if (this.isFullyRoofed(i7, i24) && i28 < 80000) {
                  i28 += j28 + 80000;
                  this.elevationMap[i7][i24] = i28;
               }

               if (j27 >= 80000) {
                  j27 -= 80000;
               }

               if (k27 >= 80000) {
                  k27 -= 80000;
               }

               if (l27 >= 80000) {
                  l27 -= 80000;
               }

               if (i28 >= 80000) {
                  i28 -= 80000;
               }

               byte byte0 = 16;
               if (!this.hasNeighbouringRoof(i7 - 1, k9)) {
                  k24 -= byte0;
               }

               if (!this.hasNeighbouringRoof(i7 + 1, k9)) {
                  k24 += byte0;
               }

               if (!this.hasNeighbouringRoof(i7, k9 - 1)) {
                  i25 -= byte0;
               }

               if (!this.hasNeighbouringRoof(i7, k9 + 1)) {
                  i25 += byte0;
               }

               if (!this.hasNeighbouringRoof(l18 - 1, k9)) {
                  k25 -= byte0;
               }

               if (!this.hasNeighbouringRoof(l18 + 1, k9)) {
                  k25 += byte0;
               }

               if (!this.hasNeighbouringRoof(l18, k9 - 1)) {
                  k26 -= byte0;
               }

               if (!this.hasNeighbouringRoof(l18, k9 + 1)) {
                  k26 += byte0;
               }

               if (!this.hasNeighbouringRoof(k21 - 1, i23)) {
                  l26 -= byte0;
               }

               if (!this.hasNeighbouringRoof(k21 + 1, i23)) {
                  l26 += byte0;
               }

               if (!this.hasNeighbouringRoof(k21, i23 - 1)) {
                  i26 -= byte0;
               }

               if (!this.hasNeighbouringRoof(k21, i23 + 1)) {
                  i26 += byte0;
               }

               if (!this.hasNeighbouringRoof(i7 - 1, i24)) {
                  j26 -= byte0;
               }

               if (!this.hasNeighbouringRoof(i7 + 1, i24)) {
                  j26 += byte0;
               }

               if (!this.hasNeighbouringRoof(i7, i24 - 1)) {
                  i27 -= byte0;
               }

               if (!this.hasNeighbouringRoof(i7, i24 + 1)) {
                  i27 += byte0;
               }

               i12 = EntityHandler.getElevationDef(i12 - 1).getUnknown2();
               j27 = -j27;
               k27 = -k27;
               l27 = -l27;
               i28 = -i28;
               if (this.getDiagonalWalls(i7, k9) > 12000 && this.getDiagonalWalls(i7, k9) < 24000 && this.getRoofTexture(i7 - 1, k9 - 1) == 0) {
                  int[] ai8 = new int[]{this.landscapeModel.getOrAddVertex(l26, l27, i26), this.landscapeModel.getOrAddVertex(j26, i28, i27), this.landscapeModel.getOrAddVertex(k25, k27, k26)};
                  this.landscapeModel.addFace(3, ai8, i12, 12345678);
               } else if (this.getDiagonalWalls(i7, k9) > 12000 && this.getDiagonalWalls(i7, k9) < 24000 && this.getRoofTexture(i7 + 1, k9 + 1) == 0) {
                  int[] ai9 = new int[]{this.landscapeModel.getOrAddVertex(k24, j27, i25), this.landscapeModel.getOrAddVertex(k25, k27, k26), this.landscapeModel.getOrAddVertex(j26, i28, i27)};
                  this.landscapeModel.addFace(3, ai9, i12, 12345678);
               } else if (this.getDiagonalWalls(i7, k9) > 0 && this.getDiagonalWalls(i7, k9) < 12000 && this.getRoofTexture(i7 + 1, k9 - 1) == 0) {
                  int[] ai10 = new int[]{this.landscapeModel.getOrAddVertex(j26, i28, i27), this.landscapeModel.getOrAddVertex(k24, j27, i25), this.landscapeModel.getOrAddVertex(l26, l27, i26)};
                  this.landscapeModel.addFace(3, ai10, i12, 12345678);
               } else if (this.getDiagonalWalls(i7, k9) > 0 && this.getDiagonalWalls(i7, k9) < 12000 && this.getRoofTexture(i7 - 1, k9 + 1) == 0) {
                  int[] ai11 = new int[]{this.landscapeModel.getOrAddVertex(k25, k27, k26), this.landscapeModel.getOrAddVertex(l26, l27, i26), this.landscapeModel.getOrAddVertex(k24, j27, i25)};
                  this.landscapeModel.addFace(3, ai11, i12, 12345678);
               } else if (j27 == k27 && l27 == i28) {
                  int[] ai12 = new int[]{
                     this.landscapeModel.getOrAddVertex(k24, j27, i25),
                     this.landscapeModel.getOrAddVertex(k25, k27, k26),
                     this.landscapeModel.getOrAddVertex(l26, l27, i26),
                     this.landscapeModel.getOrAddVertex(j26, i28, i27)
                  };
                  this.landscapeModel.addFace(4, ai12, i12, 12345678);
               } else if (j27 == i28 && k27 == l27) {
                  int[] ai13 = new int[]{
                     this.landscapeModel.getOrAddVertex(j26, i28, i27),
                     this.landscapeModel.getOrAddVertex(k24, j27, i25),
                     this.landscapeModel.getOrAddVertex(k25, k27, k26),
                     this.landscapeModel.getOrAddVertex(l26, l27, i26)
                  };
                  this.landscapeModel.addFace(4, ai13, i12, 12345678);
               } else {
                  boolean flag1 = true;
                  if (this.getRoofTexture(i7 - 1, k9 - 1) > 0) {
                     flag1 = false;
                  }

                  if (this.getRoofTexture(i7 + 1, k9 + 1) > 0) {
                     flag1 = false;
                  }

                  if (!flag1) {
                     int[] ai14 = new int[]{this.landscapeModel.getOrAddVertex(k25, k27, k26), this.landscapeModel.getOrAddVertex(l26, l27, i26), this.landscapeModel.getOrAddVertex(k24, j27, i25)};
                     this.landscapeModel.addFace(3, ai14, i12, 12345678);
                     int[] ai16 = new int[]{this.landscapeModel.getOrAddVertex(j26, i28, i27), this.landscapeModel.getOrAddVertex(k24, j27, i25), this.landscapeModel.getOrAddVertex(l26, l27, i26)};
                     this.landscapeModel.addFace(3, ai16, i12, 12345678);
                  } else {
                     int[] ai15 = new int[]{this.landscapeModel.getOrAddVertex(k24, j27, i25), this.landscapeModel.getOrAddVertex(k25, k27, k26), this.landscapeModel.getOrAddVertex(j26, i28, i27)};
                     this.landscapeModel.addFace(3, ai15, i12, 12345678);
                     int[] ai17 = new int[]{this.landscapeModel.getOrAddVertex(l26, l27, i26), this.landscapeModel.getOrAddVertex(j26, i28, i27), this.landscapeModel.getOrAddVertex(k25, k27, k26)};
                     this.landscapeModel.addFace(3, ai17, i12, 12345678);
                  }
               }
            }
         }
      }

      this.landscapeModel.setLight(true, 50, 50, -50, -10, -50);
      this.roofModels[k] = this.landscapeModel.split(0, 0, 1536, 1536, 8, 64, 169, true);

      for (int l9 = 0; l9 < 64; l9++) {
         this.camera.addModel(this.roofModels[k][l9]);
      }

      if (this.roofModels[k][0] == null) {
         throw new RuntimeException("null roof!");
      } else {
         for (int j12 = 0; j12 < 96; j12++) {
            for (int k14 = 0; k14 < 96; k14++) {
               if (this.elevationMap[j12][k14] >= 80000) {
                  this.elevationMap[j12][k14] = this.elevationMap[j12][k14] - 80000;
               }
            }
         }
      }
   }

   public int getRoofTexture(int i, int j) {
      if (i >= 0 && i < 96 && j >= 0 && j < 96) {
         byte byte0 = 0;
         if (i >= 48 && j < 48) {
            byte0 = 1;
            i -= 48;
         } else if (i < 48 && j >= 48) {
            byte0 = 2;
            j -= 48;
         } else if (i >= 48 && j >= 48) {
            byte0 = 3;
            i -= 48;
            j -= 48;
         }

         return this.sectors[byte0].getTile(i, j).roofTexture;
      } else {
         return 0;
      }
   }

   public void andMinusWalkable(int i, int j, int k) {
      this.walkableValue[i][j] = this.walkableValue[i][j] & 65535 - k;
   }

   public void registerObjectCollision(int i, int j, int k, int l) {
      if (i >= 0 && j >= 0 && i < 95 && j < 95) {
         if (EntityHandler.getObjectDef(k).getType() == 1 || EntityHandler.getObjectDef(k).getType() == 2) {
            int i1;
            int j1;
            if (l != 0 && l != 4) {
               j1 = EntityHandler.getObjectDef(k).getWidth();
               i1 = EntityHandler.getObjectDef(k).getHeight();
            } else {
               i1 = EntityHandler.getObjectDef(k).getWidth();
               j1 = EntityHandler.getObjectDef(k).getHeight();
            }

            for (int k1 = i; k1 < i + i1; k1++) {
               for (int l1 = j; l1 < j + j1; l1++) {
                  if (EntityHandler.getObjectDef(k).getType() == 1) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] | 64;
                  } else if (l == 0) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] | 2;
                     if (k1 > 0) {
                        this.orWalkable(k1 - 1, l1, 8);
                     }
                  } else if (l == 2) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] | 4;
                     if (l1 < 95) {
                        this.orWalkable(k1, l1 + 1, 1);
                     }
                  } else if (l == 4) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] | 8;
                     if (k1 < 95) {
                        this.orWalkable(k1 + 1, l1, 2);
                     }
                  } else if (l == 6) {
                     this.walkableValue[k1][l1] = this.walkableValue[k1][l1] | 1;
                     if (l1 > 0) {
                        this.orWalkable(k1, l1 - 1, 4);
                     }
                  }
               }
            }

            this.updateShadows(i, j, i1, j1);
         }
      }
   }

   public void drawMinimapTile(int i, int j, int k, int l, int i1) {
      int j1 = i * 3;
      int k1 = j * 3;
      int l1 = this.camera.getFillColor(l);
      int i2 = this.camera.getFillColor(i1);
      l1 = l1 >> 1 & 8355711;
      i2 = i2 >> 1 & 8355711;
      if (k == 0) {
         this.gameImage.drawLineX(j1, k1, 3, l1);
         this.gameImage.drawLineX(j1, k1 + 1, 2, l1);
         this.gameImage.drawLineX(j1, k1 + 2, 1, l1);
         this.gameImage.drawLineX(j1 + 2, k1 + 1, 1, i2);
         this.gameImage.drawLineX(j1 + 1, k1 + 2, 2, i2);
      } else if (k == 1) {
         this.gameImage.drawLineX(j1, k1, 3, i2);
         this.gameImage.drawLineX(j1 + 1, k1 + 1, 2, i2);
         this.gameImage.drawLineX(j1 + 2, k1 + 2, 1, i2);
         this.gameImage.drawLineX(j1, k1 + 1, 1, l1);
         this.gameImage.drawLineX(j1, k1 + 2, 2, l1);
      }
   }

   public void updateDoor(int x, int y, int dir, int type) {
      if (x >= 0 && y >= 0 && x < 95 && y < 95) {
         if (EntityHandler.getDoorDef(type).getDoorType() == 1) {
            if (dir == 0) {
               this.walkableValue[x][y] = this.walkableValue[x][y] & 65534;
               if (y > 0) {
                  this.andMinusWalkable(x, y - 1, 4);
               }
            } else if (dir == 1) {
               this.walkableValue[x][y] = this.walkableValue[x][y] & 65533;
               if (x > 0) {
                  this.andMinusWalkable(x - 1, y, 8);
               }
            } else if (dir == 2) {
               this.walkableValue[x][y] = this.walkableValue[x][y] & 65519;
            } else if (dir == 3) {
               this.walkableValue[x][y] = this.walkableValue[x][y] & 65503;
            }

            this.updateShadows(x, y, 1, 1);
         }
      }
   }

   public int getVerticalWall(int i, int j) {
      if (i >= 0 && i < 96 && j >= 0 && j < 96) {
         byte byte0 = 0;
         if (i >= 48 && j < 48) {
            byte0 = 1;
            i -= 48;
         } else if (i < 48 && j >= 48) {
            byte0 = 2;
            j -= 48;
         } else if (i >= 48 && j >= 48) {
            byte0 = 3;
            i -= 48;
            j -= 48;
         }

         return this.sectors[byte0].getTile(i, j).verticalWall & 0xFF;
      } else {
         return 0;
      }
   }

   public boolean hasNeighbouringRoof(int i, int j) {
      return this.getRoofTexture(i, j) > 0 || this.getRoofTexture(i - 1, j) > 0 || this.getRoofTexture(i - 1, j - 1) > 0 || this.getRoofTexture(i, j - 1) > 0;
   }

   public int getGroundTexturesOverlay(int i, int j) {
      if (i >= 0 && i < 96 && j >= 0 && j < 96) {
         byte byte0 = 0;
         if (i >= 48 && j < 48) {
            byte0 = 1;
            i -= 48;
         } else if (i < 48 && j >= 48) {
            byte0 = 2;
            j -= 48;
         } else if (i >= 48 && j >= 48) {
            byte0 = 3;
            i -= 48;
            j -= 48;
         }

         return this.sectors[byte0].getTile(i, j).groundOverlay & 0xFF;
      } else {
         return 0;
      }
   }

   public void setTileAmbience(int i, int j, int k) {
      int l = i / 12;
      int i1 = j / 12;
      int j1 = (i - 1) / 12;
      int k1 = (j - 1) / 12;
      this.setVertexAmbience(l, i1, i, j, k);
      if (l != j1) {
         this.setVertexAmbience(j1, i1, i, j, k);
      }

      if (i1 != k1) {
         this.setVertexAmbience(l, k1, i, j, k);
      }

      if (l != j1 && i1 != k1) {
         this.setVertexAmbience(j1, k1, i, j, k);
      }
   }

   /* diagonalWalls encodes three things: 1..12000 a diagonal wall (id+1),
      12001..24000 the mirrored diagonal (id+12001), and 48001..60000 a
      map-embedded object (id+48001) that placeMapObjects turns into a real
      model. */
   public int getDiagonalWalls(int i, int j) {
      if (i >= 0 && i < 96 && j >= 0 && j < 96) {
         byte byte0 = 0;
         if (i >= 48 && j < 48) {
            byte0 = 1;
            i -= 48;
         } else if (i < 48 && j >= 48) {
            byte0 = 2;
            j -= 48;
         } else if (i >= 48 && j >= 48) {
            byte0 = 3;
            i -= 48;
            j -= 48;
         }

         return this.sectors[byte0].getTile(i, j).diagonalWalls;
      } else {
         return 0;
      }
   }

   public void buildWall(Model model, int i, int j, int k, int l, int i1) {
      this.setTileAmbience(j, k, 40);
      this.setTileAmbience(l, i1, 40);
      int j1 = EntityHandler.getDoorDef(i).getModelVar1();
      int k1 = EntityHandler.getDoorDef(i).getModelVar2();
      int l1 = EntityHandler.getDoorDef(i).getModelVar3();
      int i2 = j * 128;
      int j2 = k * 128;
      int k2 = l * 128;
      int l2 = i1 * 128;
      int i3 = model.getOrAddVertex(i2, -this.elevationMap[j][k], j2);
      int j3 = model.getOrAddVertex(i2, -this.elevationMap[j][k] - j1, j2);
      int k3 = model.getOrAddVertex(k2, -this.elevationMap[l][i1] - j1, l2);
      int l3 = model.getOrAddVertex(k2, -this.elevationMap[l][i1], l2);
      int i4 = model.addFace(4, new int[]{i3, j3, k3, l3}, k1, l1);
      if (EntityHandler.getDoorDef(i).getUnknown() == 5) {
         model.faceTag[i4] = 30000 + i;
      } else {
         model.faceTag[i4] = 0;
      }
   }

   public int getOverlayIfRequired(int x, int y, int underlay) {
      int texture = this.getGroundTexturesOverlay(x, y);
      return texture == 0 ? underlay : EntityHandler.getTileDef(texture - 1).getColour();
   }

   public int getGroundTexture(int i, int j) {
      if (i >= 0 && i < 96 && j >= 0 && j < 96) {
         byte byte0 = 0;
         if (i >= 48 && j < 48) {
            byte0 = 1;
            i -= 48;
         } else if (i < 48 && j >= 48) {
            byte0 = 2;
            j -= 48;
         } else if (i >= 48 && j >= 48) {
            byte0 = 3;
            i -= 48;
            j -= 48;
         }

         return this.sectors[byte0].getTile(i, j).groundTexture & 0xFF;
      } else {
         return 0;
      }
   }

   public boolean isFullyRoofed(int i, int j) {
      return this.getRoofTexture(i, j) > 0 && this.getRoofTexture(i - 1, j) > 0 && this.getRoofTexture(i - 1, j - 1) > 0 && this.getRoofTexture(i, j - 1) > 0;
   }

   public int getWalkableValue(int i, int j) {
      return i >= 0 && j >= 0 && i < 96 && j < 96 ? this.walkableValue[i][j] : 0;
   }

   public int getHorizontalWall(int i, int j) {
      if (i >= 0 && i < 96 && j >= 0 && j < 96) {
         byte byte0 = 0;
         if (i >= 48 && j < 48) {
            byte0 = 1;
            i -= 48;
         } else if (i < 48 && j >= 48) {
            byte0 = 2;
            j -= 48;
         } else if (i >= 48 && j >= 48) {
            byte0 = 3;
            i -= 48;
            j -= 48;
         }

         return this.sectors[byte0].getTile(i, j).horizontalWall & 0xFF;
      } else {
         return 0;
      }
   }

   public int getOverlayBlendType(int x, int y) {
      int texture = this.getGroundTexturesOverlay(x, y);
      if (texture == 0) {
         return -1;
      } else {
         return EntityHandler.getTileDef(texture - 1).getUnknown() != 2 ? 0 : 1;
      }
   }

   public void placeMapObjects(Model[] models) {
      for (int i = 0; i < 94; i++) {
         for (int j = 0; j < 94; j++) {
            if (this.getDiagonalWalls(i, j) > 48000 && this.getDiagonalWalls(i, j) < 60000) {
               int k = this.getDiagonalWalls(i, j) - 48001;
               int l = this.objectDirs[i][j];
               int i1;
               int j1;
               if (l != 0 && l != 4) {
                  j1 = EntityHandler.getObjectDef(k).getWidth();
                  i1 = EntityHandler.getObjectDef(k).getHeight();
               } else {
                  i1 = EntityHandler.getObjectDef(k).getWidth();
                  j1 = EntityHandler.getObjectDef(k).getHeight();
               }

               this.registerObjectCollision(i, j, k, l);
               Model model = models[EntityHandler.getObjectDef(k).modelID].copy(false, true, false, false);
               int k1 = (i + i + i1) * 128 / 2;
               int i2 = (j + j + j1) * 128 / 2;
               model.translateBy(k1, -this.getAveragedElevation(k1, i2), i2);
               model.setRotation(0, l * 32, 0);
               this.camera.addModel(model);
               model.setLight(48, 48, -50, -10, -50);
               if (i1 > 1 || j1 > 1) {
                  for (int k2 = i; k2 < i + i1; k2++) {
                     for (int l2 = j; l2 < j + j1; l2++) {
                        if ((k2 > i || l2 > j) && this.getDiagonalWalls(k2, l2) - 48001 == k) {
                           int l1 = k2;
                           int j2 = l2;
                           byte byte0 = 0;
                           if (k2 >= 48 && l2 < 48) {
                              byte0 = 1;
                              l1 = k2 - 48;
                           } else if (k2 < 48 && l2 >= 48) {
                              byte0 = 2;
                              j2 = l2 - 48;
                           } else if (k2 >= 48 && l2 >= 48) {
                              byte0 = 3;
                              l1 = k2 - 48;
                              j2 = l2 - 48;
                           }

                           this.sectors[byte0].getTile(l1, j2).diagonalWalls = 0;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public void registerObjectDir(int x, int y, int dir) {
      if (x >= 0 && x < 96 && y >= 0 && y < 96) {
         this.objectDirs[x][y] = dir;
      }
   }

   public EngineHandle(Camera camera, GameImage gameImage) {
      this.camera = camera;
      this.gameImage = gameImage;
      this.objectDirs = new int[96][96];
      this.selectedX = new int[18432];
      this.selectedY = new int[18432];
      this.wallModels = new Model[4][64];
      this.roofModels = new Model[4][64];
      this.elevationMap = new int[96][96];
      this.requiresClean = true;
      this.terrainModels = new Model[64];
      this.groundTextureArray = new int[256];
      this.walkableValue = new int[96][96];
      this.playerIsAlive = false;
      this.sectors = new Sector[4];

      try {
         // Was a ZipFile over Landscape.xml.data on disk. Kept packed
         // in memory instead and inflated a sector at a time: 914 KB resident
         // rather than the 39 MB all 1764 sectors take unpacked.
         this.tileArchive = new MemoryArchive(Assets.get("Landscape.xml.data"));
      } catch (Exception var4) {
         var4.printStackTrace();
         System.exit(1);
      }

      for (int i = 0; i < 64; i++) {
         this.groundTextureArray[i] = Camera.rgbToFill(255 - i * 4, 255 - (int)((double)i * 1.75), 255 - i * 4);
      }

      for (int j = 0; j < 64; j++) {
         this.groundTextureArray[j + 64] = Camera.rgbToFill(j * 3, 144, 0);
      }

      for (int k = 0; k < 64; k++) {
         this.groundTextureArray[k + 128] = Camera.rgbToFill(192 - (int)((double)k * 1.5), 144 - (int)((double)k * 1.5), 0);
      }

      for (int l = 0; l < 64; l++) {
         this.groundTextureArray[l + 192] = Camera.rgbToFill(96 - (int)((double)l * 1.5), 48 + (int)((double)l * 1.5), 0);
      }
   }

   public void loadSection(int sectionX, int sectionY, int height, int sector) {
      Sector s = null;

      try {
         String filename = "h" + height + "x" + sectionX + "y" + sectionY;
         byte[] entry = this.tileArchive.get(filename);
         if (entry == null) {
            s = new Sector();
            if (height == 0 || height == 3) {
               for (int i = 0; i < 2304; i++) {
                  s.getTile(i).groundOverlay = (byte)(height == 0 ? -6 : 8);
               }
            }
         } else {
            s = Sector.unpack(ByteBuffer.wrap(entry));
         }
      } catch (Exception var9) {
         var9.printStackTrace();
         System.exit(1);
      }

      this.sectors[sector] = s;
   }
}
