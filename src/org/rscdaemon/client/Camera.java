package org.rscdaemon.client;

/* The software 3D renderer. Owns the registered Models plus a synthetic
   "sprite model" of billboarded 2-vertex faces (players, npcs, ground items,
   teleport bubbles), transforms and projects them through Model.project,
   collects the surviving faces into CameraModel records, depth-sorts those
   with a painter's-algorithm pass plus an overlap-correction pass, and
   scan-converts each face into the framebuffer with flat, gouraud or
   perspective-textured scanline fills.

   Angles everywhere are binary: a full turn is 1024 units, and
   sinCosTable1024 holds sin(a) in [0..1023] and cos(a) in [1024..2047],
   both scaled by 32768 (so `x * table[a] >> 15` is x*sin(a)). */
public class Camera {
   /* Cache of 256-step dark-to-colour ramps for flat (negative) fills, keyed
      by fill id; gouraud shading indexes into the ramp. */
   int gradientRampCount = 50;
   int[] gradientRampFill = new int[this.gradientRampCount];
   int[][] gradientRamps = new int[this.gradientRampCount][256];
   int[] currentGradientRamp;
   public int lastVisibleFaceCount;
   public int clipNear = 5;
   public int clipFar3d = 1000;
   public int clipFar2d = 1000;
   public int fogZFalloff = 20;
   public int fogZDistance = 10;
   public static int[] sinCosTable1024 = new int[2048];
   private static int[] sinCosTable256 = new int[512];
   public boolean halfResGradient = false;
   public double unusedDouble = 1.1;
   public int unusedInt = 1;
   private boolean mousePickRequested = false;
   private int mouseX;
   private int mouseY;
   private int mousePickedCount;
   private int mousePickedMax = 100;
   private Model[] mousePickedModels = new Model[this.mousePickedMax];
   private int[] mousePickedFaces = new int[this.mousePickedMax];
   private int width = 512;
   private int halfWidth = 256;
   private int halfHeight = 192;
   private int halfWidth2 = 256;
   private int halfHeight2 = 256;
   private int viewDistance = 8;
   private int normalMagnitudeScale = 4;
   /* Camera position in world units; the pitch/yaw/roll fields hold the
      inverse rotation (1024 - angle) that Model.project applies. */
   private int cameraX;
   private int cameraY;
   private int cameraZ;
   private int cameraPitch;
   private int cameraYaw;
   private int cameraRoll;
   public int modelCount;
   public int maxModelCount;
   public Model[] modelArray;
   private int[] modelIntArray;
   private int visibleFaceCount;
   private CameraModel[] visibleFaces;
   private int spriteCount;
   private int[] spriteId;
   private int[] spriteX;
   private int[] spriteY;
   private int[] spriteZ;
   private int[] spriteWidth;
   private int[] spriteHeight;
   private int[] spriteTranslateX;
   public Model spriteModel;
   int textureCount;
   byte[][] texturePixelIndices;
   int[][] texturePalettes;
   int[] textureDimension;
   long[] textureLastUsed;
   int[][] texturePixels;
   boolean[] textureBackTransparent;
   private static long textureUseCounter;
   int[][] texturePixelPool64;
   int[][] texturePixelPool128;
   private static byte[] unusedScratchBuffer;
   GameImage gameImage;
   public int[] framebufferPixels;
   CameraVariables[] scanlines;
   int polygonMinY;
   int polygonMaxY;
   int[] planeX = new int[40];
   int[] planeY = new int[40];
   int[] vertexShade = new int[40];
   int[] faceVertexX = new int[40];
   int[] faceVertexY = new int[40];
   int[] faceVertexZ = new int[40];
   boolean f1Toggle = false;
   /* World-space AABB of the view frustum, rebuilt each frame by
      extendFrustum(); Model.project() rejects whole models against it before
      transforming any vertices. */
   static int frustumMinX;
   static int frustumMaxX;
   static int frustumMinY;
   static int frustumMaxY;
   static int frustumMinZ;
   static int frustumMaxZ;
   int newStart;
   int newEnd;

   public Camera(GameImage gameImage, int maxModels, int maxCameraModels, int k) {
      this.gameImage = gameImage;
      this.halfWidth = gameImage.menuDefaultWidth / 2;
      this.halfHeight = gameImage.menuDefaultHeight / 2;
      this.framebufferPixels = gameImage.imagePixelArray;
      this.modelCount = 0;
      this.maxModelCount = maxModels;
      this.modelArray = new Model[this.maxModelCount];
      this.modelIntArray = new int[this.maxModelCount];
      this.visibleFaceCount = 0;
      this.visibleFaces = new CameraModel[maxCameraModels];

      for (int l = 0; l < maxCameraModels; l++) {
         this.visibleFaces[l] = new CameraModel();
      }

      this.spriteCount = 0;
      this.spriteModel = new Model(k * 2, k);
      this.spriteId = new int[k];
      this.spriteWidth = new int[k];
      this.spriteHeight = new int[k];
      this.spriteX = new int[k];
      this.spriteY = new int[k];
      this.spriteZ = new int[k];
      this.spriteTranslateX = new int[k];
      if (unusedScratchBuffer == null) {
         unusedScratchBuffer = new byte[17691];
      }

      this.cameraX = 0;
      this.cameraY = 0;
      this.cameraZ = 0;
      this.cameraPitch = 0;
      this.cameraYaw = 0;
      this.cameraRoll = 0;

      for (int i1 = 0; i1 < 256; i1++) {
         sinCosTable256[i1] = (int)(Math.sin((double)i1 * 0.02454369) * 32768.0);
         sinCosTable256[i1 + 256] = (int)(Math.cos((double)i1 * 0.02454369) * 32768.0);
      }

      for (int j1 = 0; j1 < 1024; j1++) {
         sinCosTable1024[j1] = (int)(Math.sin((double)j1 * 0.00613592315) * 32768.0);
         sinCosTable1024[j1 + 1024] = (int)(Math.cos((double)j1 * 0.00613592315) * 32768.0);
      }
   }

   public void addModel(Model model) {
      if (model == null) {
         System.out.println("Warning tried to add null object!");
      }

      if (this.modelCount < this.maxModelCount) {
         this.modelIntArray[this.modelCount] = 0;
         this.modelArray[this.modelCount++] = model;
      }
   }

   public void removeModel(Model model) {
      for (int i = 0; i < this.modelCount; i++) {
         if (this.modelArray[i] == model) {
            this.modelCount--;

            for (int j = i; j < this.modelCount; j++) {
               this.modelArray[j] = this.modelArray[j + 1];
               this.modelIntArray[j] = this.modelIntArray[j + 1];
            }
         }
      }
   }

   public void cleanupModels() {
      this.clearSprites();

      for (int i = 0; i < this.modelCount; i++) {
         this.modelArray[i] = null;
      }

      this.modelCount = 0;
   }

   public void clearSprites() {
      this.spriteCount = 0;
      this.spriteModel.clear();
   }

   public void reduceSprites(int i) {
      this.spriteCount -= i;
      this.spriteModel.reduce(i, i * 2);
      if (this.spriteCount < 0) {
         this.spriteCount = 0;
      }
   }

   /* Registers a billboard: a 2-vertex face (foot point and head point) in the
      sprite model. The id encodes the entity kind by band: >=50000 teleport
      bubble, >=40000 ground item, >=20000 npc, >=5000 player. */
   public int addSprite(int id, int x, int y, int z, int width, int height, int tag) {
      this.spriteId[this.spriteCount] = id;
      this.spriteX[this.spriteCount] = x;
      this.spriteY[this.spriteCount] = y;
      this.spriteZ[this.spriteCount] = z;
      this.spriteWidth[this.spriteCount] = width;
      this.spriteHeight[this.spriteCount] = height;
      this.spriteTranslateX[this.spriteCount] = 0;
      int l1 = this.spriteModel.addVertex(x, y, z);
      int i2 = this.spriteModel.addVertex(x, y - height, z);
      int[] ai = new int[]{l1, i2};
      this.spriteModel.addFace(2, ai, 0, 0);
      this.spriteModel.faceTag[this.spriteCount] = tag;
      this.spriteModel.isLocalPlayer[this.spriteCount++] = 0;
      return this.spriteCount - 1;
   }

   public void setOurPlayer(int i) {
      this.spriteModel.isLocalPlayer[i] = 1;
   }

   public void setSpriteTranslateX(int i, int j) {
      this.spriteTranslateX[i] = j;
   }

   public void updateMouseCoords(int x, int y) {
      this.mouseX = x - this.halfWidth2;
      this.mouseY = y;
      this.mousePickedCount = 0;
      this.mousePickRequested = true;
   }

   public int getMousePickedCount() {
      return this.mousePickedCount;
   }

   public int[] getMousePickedFaces() {
      return this.mousePickedFaces;
   }

   public Model[] getMousePickedModels() {
      return this.mousePickedModels;
   }

   public void setCameraSize(int halfWindowWidth, int halfWindowHeight, int halfWindowWidth2, int halfWindowHeight2, int windowWidth, int camSizeInt) {
      this.halfWidth = halfWindowWidth2;
      this.halfHeight = halfWindowHeight2;
      this.halfWidth2 = halfWindowWidth;
      this.halfHeight2 = halfWindowHeight;
      this.width = windowWidth;
      this.viewDistance = camSizeInt;
      /* The constructor cached the framebuffer's pixel array; after a window
         resize GameImage holds a new one, so read it again here. */
      this.framebufferPixels = this.gameImage.imagePixelArray;
      this.scanlines = new CameraVariables[halfWindowHeight2 + halfWindowHeight];

      for (int k1 = 0; k1 < halfWindowHeight2 + halfWindowHeight; k1++) {
         this.scanlines[k1] = new CameraVariables();
      }
   }

   /* Quicksort of the visible-face records by average projected depth,
      farthest first: the base painter's-algorithm order. */
   private void sortByDepth(CameraModel[] visibleFaces, int i, int j) {
      if (i < j) {
         int k = i - 1;
         int l = j + 1;
         int i1 = (i + j) / 2;
         CameraModel cameraModel = visibleFaces[i1];
         visibleFaces[i1] = visibleFaces[i];
         visibleFaces[i] = cameraModel;
         int j1 = cameraModel.depth;

         while (k < l) {
            while (visibleFaces[--l].depth < j1) {
            }

            while (visibleFaces[++k].depth > j1) {
            }

            if (k < l) {
               CameraModel cameraModel_1 = visibleFaces[k];
               visibleFaces[k] = visibleFaces[l];
               visibleFaces[l] = cameraModel_1;
            }
         }

         this.sortByDepth(visibleFaces, i, l);
         this.sortByDepth(visibleFaces, l + 1, j);
      }
   }

   /* Depth sorting alone mis-orders faces whose depth ranges interleave.
      For each face this looks up to `i` slots ahead for a face whose screen
      rect overlaps and which the plane-side heuristic says should be drawn
      first, and moves it back via reorderFaces. lastSwapIndex stops the same
      pair from being swapped back and forth forever. */
   public void overlapCorrectionPass(int i, CameraModel[] visibleFaces, int j) {
      for (int k = 0; k <= j; k++) {
         visibleFaces[k].ordered = false;
         visibleFaces[k].index = k;
         visibleFaces[k].lastSwapIndex = -1;
      }

      int l = 0;

      while (true) {
         while (!visibleFaces[l].ordered) {
            if (l == j) {
               return;
            }

            CameraModel cameraModel = visibleFaces[l];
            cameraModel.ordered = true;
            int i1 = l;
            int j1 = l + i;
            if (j1 >= j) {
               j1 = j - 1;
            }

            for (int k1 = j1; k1 >= i1 + 1; k1--) {
               CameraModel cameraModel_1 = visibleFaces[k1];
               if (cameraModel.minScreenX < cameraModel_1.maxScreenX
                  && cameraModel_1.minScreenX < cameraModel.maxScreenX
                  && cameraModel.minScreenY < cameraModel_1.maxScreenY
                  && cameraModel_1.minScreenY < cameraModel.maxScreenY
                  && cameraModel.index != cameraModel_1.lastSwapIndex
                  && !this.facesAreSeparated(cameraModel, cameraModel_1)
                  && this.faceOrderHeuristic(cameraModel_1, cameraModel)) {
                  this.reorderFaces(visibleFaces, i1, k1);
                  if (visibleFaces[k1] != cameraModel_1) {
                     k1++;
                  }

                  i1 = this.newStart;
                  cameraModel_1.lastSwapIndex = cameraModel.index;
               }
            }
         }

         l++;
      }
   }

   public boolean reorderFaces(CameraModel[] visibleFaces, int i, int j) {
      while (true) {
         CameraModel cameraModel = visibleFaces[i];

         for (int k = i + 1; k <= j; k++) {
            CameraModel cameraModel_1 = visibleFaces[k];
            if (!this.facesAreSeparated(cameraModel_1, cameraModel)) {
               break;
            }

            visibleFaces[i] = cameraModel_1;
            visibleFaces[k] = cameraModel;
            i = k;
            if (k == j) {
               this.newStart = k;
               this.newEnd = k - 1;
               return true;
            }
         }

         CameraModel cameraModel_2 = visibleFaces[j];

         for (int l = j - 1; l >= i; l--) {
            CameraModel cameraModel_3 = visibleFaces[l];
            if (!this.facesAreSeparated(cameraModel_2, cameraModel_3)) {
               break;
            }

            visibleFaces[j] = cameraModel_3;
            visibleFaces[l] = cameraModel_2;
            j = l;
            if (i == l) {
               this.newStart = l + 1;
               this.newEnd = l;
               return true;
            }
         }

         if (i + 1 >= j) {
            this.newStart = i;
            this.newEnd = j;
            return false;
         }

         if (!this.reorderFaces(visibleFaces, i + 1, j)) {
            this.newStart = i;
            return false;
         }

         j = this.newEnd;
      }
   }

   /* Takes a view-space frustum corner (x, y, z), rotates it by the inverse
      camera rotation into world space, and grows the frustum AABB to hold
      it. finishCamera() feeds it the four far-plane corners and the four
      near-plane corners. */
   public void extendFrustum(int i, int j, int k) {
      int l = -this.cameraPitch + 1024 & 1023;
      int i1 = -this.cameraYaw + 1024 & 1023;
      int j1 = -this.cameraRoll + 1024 & 1023;
      if (j1 != 0) {
         int k1 = sinCosTable1024[j1];
         int j2 = sinCosTable1024[j1 + 1024];
         int i3 = j * k1 + i * j2 >> 15;
         j = j * j2 - i * k1 >> 15;
         i = i3;
      }

      if (l != 0) {
         int l1 = sinCosTable1024[l];
         int k2 = sinCosTable1024[l + 1024];
         int j3 = j * k2 - k * l1 >> 15;
         k = j * l1 + k * k2 >> 15;
         j = j3;
      }

      if (i1 != 0) {
         int i2 = sinCosTable1024[i1];
         int l2 = sinCosTable1024[i1 + 1024];
         int k3 = k * i2 + i * l2 >> 15;
         k = k * l2 - i * i2 >> 15;
         i = k3;
      }

      if (i < frustumMinX) {
         frustumMinX = i;
      }

      if (i > frustumMaxX) {
         frustumMaxX = i;
      }

      if (j < frustumMinY) {
         frustumMinY = j;
      }

      if (j > frustumMaxY) {
         frustumMaxY = j;
      }

      if (k < frustumMinZ) {
         frustumMinZ = k;
      }

      if (k > frustumMaxZ) {
         frustumMaxZ = k;
      }
   }

   /* Renders the frame: rebuilds the frustum AABB, projects every model,
      collects each surviving face (on-screen, inside the near/far band,
      not fully transparent) into a CameraModel record, depth-sorts the
      records, then draws them back to front. Faces that cross the near
      plane get their offending vertices clipped against it here; sprite
      faces are billboarded and handed to GameImageMiddleMan.drawSceneSprite.
      Vertex intensities past fogZDistance are pushed toward black by
      (z - fogZDistance) / fogZFalloff, which is RSC's distance fog. If a
      mouse pick was requested, every face whose span covers the mouse pixel
      is recorded into mousePickedModels/mousePickedFaces on the way. */
   public void finishCamera() {
      this.f1Toggle = this.gameImage.f1Toggle;
      int i3 = this.halfWidth * this.clipFar3d >> this.viewDistance;
      int j3 = this.halfHeight * this.clipFar3d >> this.viewDistance;
      frustumMinX = 0;
      frustumMaxX = 0;
      frustumMinY = 0;
      frustumMaxY = 0;
      frustumMinZ = 0;
      frustumMaxZ = 0;
      this.extendFrustum(-i3, -j3, this.clipFar3d);
      this.extendFrustum(-i3, j3, this.clipFar3d);
      this.extendFrustum(i3, -j3, this.clipFar3d);
      this.extendFrustum(i3, j3, this.clipFar3d);
      this.extendFrustum(-this.halfWidth, -this.halfHeight, 0);
      this.extendFrustum(-this.halfWidth, this.halfHeight, 0);
      this.extendFrustum(this.halfWidth, -this.halfHeight, 0);
      this.extendFrustum(this.halfWidth, this.halfHeight, 0);
      frustumMinX = frustumMinX + this.cameraX;
      frustumMaxX = frustumMaxX + this.cameraX;
      frustumMinY = frustumMinY + this.cameraY;
      frustumMaxY = frustumMaxY + this.cameraY;
      frustumMinZ = frustumMinZ + this.cameraZ;
      frustumMaxZ = frustumMaxZ + this.cameraZ;
      this.modelArray[this.modelCount] = this.spriteModel;
      this.spriteModel.transformState = 2;

      for (int i = 0; i < this.modelCount; i++) {
         this.modelArray[i]
            .project(this.cameraX, this.cameraY, this.cameraZ, this.cameraPitch, this.cameraYaw, this.cameraRoll, this.viewDistance, this.clipNear);
      }

      this.modelArray[this.modelCount]
         .project(this.cameraX, this.cameraY, this.cameraZ, this.cameraPitch, this.cameraYaw, this.cameraRoll, this.viewDistance, this.clipNear);
      this.visibleFaceCount = 0;

      for (int k3 = 0; k3 < this.modelCount; k3++) {
         Model model = this.modelArray[k3];
         if (model.visible) {
            for (int j = 0; j < model.faceCount; j++) {
               int l3 = model.faceNumVertices[j];
               int[] ai1 = model.faceVertices[j];
               boolean flag = false;

               for (int k4 = 0; k4 < l3; k4++) {
                  int i1 = model.projectVertexZ[ai1[k4]];
                  if (i1 > this.clipNear && i1 < this.clipFar3d) {
                     flag = true;
                     break;
                  }
               }

               if (flag) {
                  int l1 = 0;

                  for (int k5 = 0; k5 < l3; k5++) {
                     int j1 = model.vertexViewX[ai1[k5]];
                     if (j1 > -this.halfWidth) {
                        l1 |= 1;
                     }

                     if (j1 < this.halfWidth) {
                        l1 |= 2;
                     }

                     if (l1 == 3) {
                        break;
                     }
                  }

                  if (l1 == 3) {
                     int i2 = 0;

                     for (int l6 = 0; l6 < l3; l6++) {
                        int k1 = model.vertexViewY[ai1[l6]];
                        if (k1 > -this.halfHeight) {
                           i2 |= 1;
                        }

                        if (k1 < this.halfHeight) {
                           i2 |= 2;
                        }

                        if (i2 == 3) {
                           break;
                        }
                     }

                     if (i2 == 3) {
                        CameraModel cameraModel_1 = this.visibleFaces[this.visibleFaceCount];
                        cameraModel_1.model = model;
                        cameraModel_1.faceIndex = j;
                        this.initFaceRecord(this.visibleFaceCount);
                        int l8;
                        if (cameraModel_1.visibility < 0) {
                           l8 = model.faceFillFront[j];
                        } else {
                           l8 = model.faceFillBack[j];
                        }

                        if (l8 != 12345678) {
                           int j2 = 0;

                           for (int l9 = 0; l9 < l3; l9++) {
                              j2 += model.projectVertexZ[ai1[l9]];
                           }

                           cameraModel_1.depth = j2 / l3 + model.depth;
                           cameraModel_1.faceFill = l8;
                           this.visibleFaceCount++;
                        }
                     }
                  }
               }
            }
         }
      }

      Model model_1 = this.spriteModel;
      if (model_1.visible) {
         for (int k = 0; k < model_1.faceCount; k++) {
            int[] ai = model_1.faceVertices[k];
            int j4 = ai[0];
            int l4 = model_1.vertexViewX[j4];
            int l5 = model_1.vertexViewY[j4];
            int i7 = model_1.projectVertexZ[j4];
            if (i7 > this.clipNear && i7 < this.clipFar2d) {
               int i8 = (this.spriteWidth[k] << this.viewDistance) / i7;
               int i9 = (this.spriteHeight[k] << this.viewDistance) / i7;
               if (l4 - i8 / 2 <= this.halfWidth && l4 + i8 / 2 >= -this.halfWidth && l5 - i9 <= this.halfHeight && l5 >= -this.halfHeight) {
                  CameraModel cameraModel_2 = this.visibleFaces[this.visibleFaceCount];
                  cameraModel_2.model = model_1;
                  cameraModel_2.faceIndex = k;
                  this.initSpriteRecord(this.visibleFaceCount);
                  cameraModel_2.depth = (i7 + model_1.projectVertexZ[ai[1]]) / 2;
                  this.visibleFaceCount++;
               }
            }
         }
      }

      if (this.visibleFaceCount != 0) {
         this.lastVisibleFaceCount = this.visibleFaceCount;
         this.sortByDepth(this.visibleFaces, 0, this.visibleFaceCount - 1);
         this.overlapCorrectionPass(100, this.visibleFaces, this.visibleFaceCount);

         for (int i4 = 0; i4 < this.visibleFaceCount; i4++) {
            CameraModel cameraModel = this.visibleFaces[i4];
            Model model_2 = cameraModel.model;
            int l = cameraModel.faceIndex;
            if (model_2 == this.spriteModel) {
               int[] ai2 = model_2.faceVertices[l];
               int i6 = ai2[0];
               int j7 = model_2.vertexViewX[i6];
               int j8 = model_2.vertexViewY[i6];
               int j9 = model_2.projectVertexZ[i6];
               int i10 = (this.spriteWidth[l] << this.viewDistance) / j9;
               int k10 = (this.spriteHeight[l] << this.viewDistance) / j9;
               int i11 = j8 - model_2.vertexViewY[ai2[1]];
               int j11 = (model_2.vertexViewX[ai2[1]] - j7) * i11 / k10;
               j11 = model_2.vertexViewX[ai2[1]] - j7;
               int l11 = j7 - i10 / 2;
               int j12 = this.halfHeight2 + j8 - k10;
               this.gameImage.drawSceneSprite(l11 + this.halfWidth2, j12, i10, k10, this.spriteId[l], j11, (256 << this.viewDistance) / j9);
               if (this.mousePickRequested && this.mousePickedCount < this.mousePickedMax) {
                  l11 += (this.spriteTranslateX[l] << this.viewDistance) / j9;
                  if (this.mouseY >= j12
                     && this.mouseY <= j12 + k10
                     && this.mouseX >= l11
                     && this.mouseX <= l11 + i10
                     && !model_2.unpickable
                     && model_2.isLocalPlayer[l] == 0) {
                     this.mousePickedModels[this.mousePickedCount] = model_2;
                     this.mousePickedFaces[this.mousePickedCount] = l;
                     this.mousePickedCount++;
                  }
               }
            } else {
               int k8 = 0;
               int j10 = 0;
               int l10 = model_2.faceNumVertices[l];
               int[] ai3 = model_2.faceVertices[l];
               if (model_2.faceIntensity[l] != 12345678) {
                  if (cameraModel.visibility < 0) {
                     j10 = model_2.lightAmbience - model_2.faceIntensity[l];
                  } else {
                     j10 = model_2.lightAmbience + model_2.faceIntensity[l];
                  }
               }

               for (int k11 = 0; k11 < l10; k11++) {
                  int k2 = ai3[k11];
                  this.faceVertexX[k11] = model_2.projectVertexX[k2];
                  this.faceVertexY[k11] = model_2.projectVertexY[k2];
                  this.faceVertexZ[k11] = model_2.projectVertexZ[k2];
                  if (model_2.faceIntensity[l] == 12345678) {
                     if (cameraModel.visibility < 0) {
                        j10 = model_2.lightAmbience - model_2.vertexIntensity[k2] + model_2.vertexAmbience[k2];
                     } else {
                        j10 = model_2.lightAmbience + model_2.vertexIntensity[k2] + model_2.vertexAmbience[k2];
                     }
                  }

                  if (model_2.projectVertexZ[k2] >= this.clipNear) {
                     this.planeX[k8] = model_2.vertexViewX[k2];
                     this.planeY[k8] = model_2.vertexViewY[k2];
                     this.vertexShade[k8] = j10;
                     if (model_2.projectVertexZ[k2] > this.fogZDistance) {
                        this.vertexShade[k8] = this.vertexShade[k8] + (model_2.projectVertexZ[k2] - this.fogZDistance) / this.fogZFalloff;
                     }

                     k8++;
                  } else {
                     int k9;
                     if (k11 == 0) {
                        k9 = ai3[l10 - 1];
                     } else {
                        k9 = ai3[k11 - 1];
                     }

                     if (model_2.projectVertexZ[k9] >= this.clipNear) {
                        int k7 = model_2.projectVertexZ[k2] - model_2.projectVertexZ[k9];
                        int i5 = model_2.projectVertexX[k2]
                           - (model_2.projectVertexX[k2] - model_2.projectVertexX[k9]) * (model_2.projectVertexZ[k2] - this.clipNear) / k7;
                        int j6 = model_2.projectVertexY[k2]
                           - (model_2.projectVertexY[k2] - model_2.projectVertexY[k9]) * (model_2.projectVertexZ[k2] - this.clipNear) / k7;
                        this.planeX[k8] = (i5 << this.viewDistance) / this.clipNear;
                        this.planeY[k8] = (j6 << this.viewDistance) / this.clipNear;
                        this.vertexShade[k8] = j10;
                        k8++;
                     }

                     if (k11 == l10 - 1) {
                        k9 = ai3[0];
                     } else {
                        k9 = ai3[k11 + 1];
                     }

                     if (model_2.projectVertexZ[k9] >= this.clipNear) {
                        int l7 = model_2.projectVertexZ[k2] - model_2.projectVertexZ[k9];
                        int j5 = model_2.projectVertexX[k2]
                           - (model_2.projectVertexX[k2] - model_2.projectVertexX[k9]) * (model_2.projectVertexZ[k2] - this.clipNear) / l7;
                        int k6 = model_2.projectVertexY[k2]
                           - (model_2.projectVertexY[k2] - model_2.projectVertexY[k9]) * (model_2.projectVertexZ[k2] - this.clipNear) / l7;
                        this.planeX[k8] = (j5 << this.viewDistance) / this.clipNear;
                        this.planeY[k8] = (k6 << this.viewDistance) / this.clipNear;
                        this.vertexShade[k8] = j10;
                        k8++;
                     }
                  }
               }

               for (int i12 = 0; i12 < l10; i12++) {
                  if (this.vertexShade[i12] < 0) {
                     this.vertexShade[i12] = 0;
                  } else if (this.vertexShade[i12] > 255) {
                     this.vertexShade[i12] = 255;
                  }

                  if (cameraModel.faceFill >= 0) {
                     if (this.textureDimension[cameraModel.faceFill] == 1) {
                        this.vertexShade[i12] = this.vertexShade[i12] << 9;
                     } else {
                        this.vertexShade[i12] = this.vertexShade[i12] << 6;
                     }
                  }
               }

               this.generateScanlines(0, 0, 0, 0, k8, this.planeX, this.planeY, this.vertexShade, model_2, l);
               if (this.polygonMaxY > this.polygonMinY) {
                  this.rasterizeFace(0, 0, l10, this.faceVertexX, this.faceVertexY, this.faceVertexZ, cameraModel.faceFill, model_2);
               }
            }
         }

         this.mousePickRequested = false;
      }
   }

   /* Scan-converts one polygon (screen coords in ai/ai1, per-vertex shade in
      ai2) into the per-row scanlines[] table: leftX/rightX edge positions in
      24.8 fixed point plus the shade value at each edge. Triangles and quads
      are unrolled; the general path walks every edge. Sets polygonMinY /
      polygonMaxY to the covered row range, and also answers the mouse pick
      by checking whether the pick pixel falls inside the row span. */
   private void generateScanlines(int i, int j, int k, int l, int i1, int[] ai, int[] ai1, int[] ai2, Model model, int j1) {
      if (i1 == 3) {
         int k1 = ai1[0] + this.halfHeight2;
         int k2 = ai1[1] + this.halfHeight2;
         int k3 = ai1[2] + this.halfHeight2;
         int k4 = ai[0];
         int l5 = ai[1];
         int j7 = ai[2];
         int l8 = ai2[0];
         int j10 = ai2[1];
         int j11 = ai2[2];
         int j12 = this.halfHeight2 + this.halfHeight - 1;
         int l12 = 0;
         int j13 = 0;
         int l13 = 0;
         int j14 = 0;
         int l14 = 12345678;
         int j15 = -12345678;
         if (k3 != k1) {
            j13 = (j7 - k4 << 8) / (k3 - k1);
            j14 = (j11 - l8 << 8) / (k3 - k1);
            if (k1 < k3) {
               l12 = k4 << 8;
               l13 = l8 << 8;
               l14 = k1;
               j15 = k3;
            } else {
               l12 = j7 << 8;
               l13 = j11 << 8;
               l14 = k3;
               j15 = k1;
            }

            if (l14 < 0) {
               l12 -= j13 * l14;
               l13 -= j14 * l14;
               l14 = 0;
            }

            if (j15 > j12) {
               j15 = j12;
            }
         }

         int l15 = 0;
         int j16 = 0;
         int l16 = 0;
         int j17 = 0;
         int l17 = 12345678;
         int j18 = -12345678;
         if (k2 != k1) {
            j16 = (l5 - k4 << 8) / (k2 - k1);
            j17 = (j10 - l8 << 8) / (k2 - k1);
            if (k1 < k2) {
               l15 = k4 << 8;
               l16 = l8 << 8;
               l17 = k1;
               j18 = k2;
            } else {
               l15 = l5 << 8;
               l16 = j10 << 8;
               l17 = k2;
               j18 = k1;
            }

            if (l17 < 0) {
               l15 -= j16 * l17;
               l16 -= j17 * l17;
               l17 = 0;
            }

            if (j18 > j12) {
               j18 = j12;
            }
         }

         int l18 = 0;
         int j19 = 0;
         int l19 = 0;
         int j20 = 0;
         int l20 = 12345678;
         int j21 = -12345678;
         if (k3 != k2) {
            j19 = (j7 - l5 << 8) / (k3 - k2);
            j20 = (j11 - j10 << 8) / (k3 - k2);
            if (k2 < k3) {
               l18 = l5 << 8;
               l19 = j10 << 8;
               l20 = k2;
               j21 = k3;
            } else {
               l18 = j7 << 8;
               l19 = j11 << 8;
               l20 = k3;
               j21 = k2;
            }

            if (l20 < 0) {
               l18 -= j19 * l20;
               l19 -= j20 * l20;
               l20 = 0;
            }

            if (j21 > j12) {
               j21 = j12;
            }
         }

         this.polygonMinY = l14;
         if (l17 < this.polygonMinY) {
            this.polygonMinY = l17;
         }

         if (l20 < this.polygonMinY) {
            this.polygonMinY = l20;
         }

         this.polygonMaxY = j15;
         if (j18 > this.polygonMaxY) {
            this.polygonMaxY = j18;
         }

         if (j21 > this.polygonMaxY) {
            this.polygonMaxY = j21;
         }

         int l21 = 0;

         for (int var54 = this.polygonMinY; var54 < this.polygonMaxY; var54++) {
            if (var54 >= l14 && var54 < j15) {
               j = l12;
               i = l12;
               l21 = l13;
               l = l13;
               l12 += j13;
               l13 += j14;
            } else {
               i = 655360;
               j = -655360;
            }

            if (var54 >= l17 && var54 < j18) {
               if (l15 < i) {
                  i = l15;
                  l = l16;
               }

               if (l15 > j) {
                  j = l15;
                  l21 = l16;
               }

               l15 += j16;
               l16 += j17;
            }

            if (var54 >= l20 && var54 < j21) {
               if (l18 < i) {
                  i = l18;
                  l = l19;
               }

               if (l18 > j) {
                  j = l18;
                  l21 = l19;
               }

               l18 += j19;
               l19 += j20;
            }

            CameraVariables cameraVariables_6 = this.scanlines[var54];
            cameraVariables_6.leftX = i;
            cameraVariables_6.rightX = j;
            cameraVariables_6.leftShade = l;
            cameraVariables_6.rightShade = l21;
         }

         if (this.polygonMinY < this.halfHeight2 - this.halfHeight) {
            this.polygonMinY = this.halfHeight2 - this.halfHeight;
         }
      } else if (i1 == 4) {
         int l1 = ai1[0] + this.halfHeight2;
         int l2 = ai1[1] + this.halfHeight2;
         int l3 = ai1[2] + this.halfHeight2;
         int l4 = ai1[3] + this.halfHeight2;
         int i6 = ai[0];
         int k7 = ai[1];
         int i9 = ai[2];
         int k10 = ai[3];
         int k11 = ai2[0];
         int k12 = ai2[1];
         int i13 = ai2[2];
         int k13 = ai2[3];
         int i14 = this.halfHeight2 + this.halfHeight - 1;
         int k14 = 0;
         int i15 = 0;
         int k15 = 0;
         int i16 = 0;
         int k16 = 12345678;
         int i17 = -12345678;
         if (l4 != l1) {
            i15 = (k10 - i6 << 8) / (l4 - l1);
            i16 = (k13 - k11 << 8) / (l4 - l1);
            if (l1 < l4) {
               k14 = i6 << 8;
               k15 = k11 << 8;
               k16 = l1;
               i17 = l4;
            } else {
               k14 = k10 << 8;
               k15 = k13 << 8;
               k16 = l4;
               i17 = l1;
            }

            if (k16 < 0) {
               k14 -= i15 * k16;
               k15 -= i16 * k16;
               k16 = 0;
            }

            if (i17 > i14) {
               i17 = i14;
            }
         }

         int k17 = 0;
         int i18 = 0;
         int k18 = 0;
         int i19 = 0;
         int k19 = 12345678;
         int i20 = -12345678;
         if (l2 != l1) {
            i18 = (k7 - i6 << 8) / (l2 - l1);
            i19 = (k12 - k11 << 8) / (l2 - l1);
            if (l1 < l2) {
               k17 = i6 << 8;
               k18 = k11 << 8;
               k19 = l1;
               i20 = l2;
            } else {
               k17 = k7 << 8;
               k18 = k12 << 8;
               k19 = l2;
               i20 = l1;
            }

            if (k19 < 0) {
               k17 -= i18 * k19;
               k18 -= i19 * k19;
               k19 = 0;
            }

            if (i20 > i14) {
               i20 = i14;
            }
         }

         int k20 = 0;
         int i21 = 0;
         int k21 = 0;
         int i22 = 0;
         int j22 = 12345678;
         int k22 = -12345678;
         if (l3 != l2) {
            i21 = (i9 - k7 << 8) / (l3 - l2);
            i22 = (i13 - k12 << 8) / (l3 - l2);
            if (l2 < l3) {
               k20 = k7 << 8;
               k21 = k12 << 8;
               j22 = l2;
               k22 = l3;
            } else {
               k20 = i9 << 8;
               k21 = i13 << 8;
               j22 = l3;
               k22 = l2;
            }

            if (j22 < 0) {
               k20 -= i21 * j22;
               k21 -= i22 * j22;
               j22 = 0;
            }

            if (k22 > i14) {
               k22 = i14;
            }
         }

         int l22 = 0;
         int i23 = 0;
         int j23 = 0;
         int k23 = 0;
         int l23 = 12345678;
         int i24 = -12345678;
         if (l4 != l3) {
            i23 = (k10 - i9 << 8) / (l4 - l3);
            k23 = (k13 - i13 << 8) / (l4 - l3);
            if (l3 < l4) {
               l22 = i9 << 8;
               j23 = i13 << 8;
               l23 = l3;
               i24 = l4;
            } else {
               l22 = k10 << 8;
               j23 = k13 << 8;
               l23 = l4;
               i24 = l3;
            }

            if (l23 < 0) {
               l22 -= i23 * l23;
               j23 -= k23 * l23;
               l23 = 0;
            }

            if (i24 > i14) {
               i24 = i14;
            }
         }

         this.polygonMinY = k16;
         if (k19 < this.polygonMinY) {
            this.polygonMinY = k19;
         }

         if (j22 < this.polygonMinY) {
            this.polygonMinY = j22;
         }

         if (l23 < this.polygonMinY) {
            this.polygonMinY = l23;
         }

         this.polygonMaxY = i17;
         if (i20 > this.polygonMaxY) {
            this.polygonMaxY = i20;
         }

         if (k22 > this.polygonMaxY) {
            this.polygonMaxY = k22;
         }

         if (i24 > this.polygonMaxY) {
            this.polygonMaxY = i24;
         }

         int j24 = 0;

         for (int var55 = this.polygonMinY; var55 < this.polygonMaxY; var55++) {
            if (var55 >= k16 && var55 < i17) {
               j = k14;
               i = k14;
               j24 = k15;
               l = k15;
               k14 += i15;
               k15 += i16;
            } else {
               i = 655360;
               j = -655360;
            }

            if (var55 >= k19 && var55 < i20) {
               if (k17 < i) {
                  i = k17;
                  l = k18;
               }

               if (k17 > j) {
                  j = k17;
                  j24 = k18;
               }

               k17 += i18;
               k18 += i19;
            }

            if (var55 >= j22 && var55 < k22) {
               if (k20 < i) {
                  i = k20;
                  l = k21;
               }

               if (k20 > j) {
                  j = k20;
                  j24 = k21;
               }

               k20 += i21;
               k21 += i22;
            }

            if (var55 >= l23 && var55 < i24) {
               if (l22 < i) {
                  i = l22;
                  l = j23;
               }

               if (l22 > j) {
                  j = l22;
                  j24 = j23;
               }

               l22 += i23;
               j23 += k23;
            }

            CameraVariables cameraVariables_7 = this.scanlines[var55];
            cameraVariables_7.leftX = i;
            cameraVariables_7.rightX = j;
            cameraVariables_7.leftShade = l;
            cameraVariables_7.rightShade = j24;
         }

         if (this.polygonMinY < this.halfHeight2 - this.halfHeight) {
            this.polygonMinY = this.halfHeight2 - this.halfHeight;
         }
      } else {
         this.polygonMaxY = this.polygonMinY = ai1[0] += this.halfHeight2;

         for (int var56 = 1; var56 < i1; var56++) {
            int i2;
            if ((i2 = ai1[var56] += this.halfHeight2) < this.polygonMinY) {
               this.polygonMinY = i2;
            } else if (i2 > this.polygonMaxY) {
               this.polygonMaxY = i2;
            }
         }

         if (this.polygonMinY < this.halfHeight2 - this.halfHeight) {
            this.polygonMinY = this.halfHeight2 - this.halfHeight;
         }

         if (this.polygonMaxY >= this.halfHeight2 + this.halfHeight) {
            this.polygonMaxY = this.halfHeight2 + this.halfHeight - 1;
         }

         if (this.polygonMinY >= this.polygonMaxY) {
            return;
         }

         for (int var57 = this.polygonMinY; var57 < this.polygonMaxY; var57++) {
            CameraVariables scanlines = this.scanlines[var57];
            scanlines.leftX = 655360;
            scanlines.rightX = -655360;
         }

         int j2 = i1 - 1;
         int i3 = ai1[0];
         int i4 = ai1[j2];
         if (i3 < i4) {
            int i5 = ai[0] << 8;
            int j6 = (ai[j2] - ai[0] << 8) / (i4 - i3);
            int l7 = ai2[0] << 8;
            int j9 = (ai2[j2] - ai2[0] << 8) / (i4 - i3);
            if (i3 < 0) {
               i5 -= j6 * i3;
               l7 -= j9 * i3;
               i3 = 0;
            }

            if (i4 > this.polygonMaxY) {
               i4 = this.polygonMaxY;
            }

            for (int var59 = i3; var59 <= i4; var59++) {
               CameraVariables cameraVariables_2 = this.scanlines[var59];
               cameraVariables_2.leftX = cameraVariables_2.rightX = i5;
               cameraVariables_2.leftShade = cameraVariables_2.rightShade = l7;
               i5 += j6;
               l7 += j9;
            }
         } else if (i3 > i4) {
            int j5 = ai[j2] << 8;
            int k6 = (ai[0] - ai[j2] << 8) / (i3 - i4);
            int i8 = ai2[j2] << 8;
            int k9 = (ai2[0] - ai2[j2] << 8) / (i3 - i4);
            if (i4 < 0) {
               j5 -= k6 * i4;
               i8 -= k9 * i4;
               i4 = 0;
            }

            if (i3 > this.polygonMaxY) {
               i3 = this.polygonMaxY;
            }

            for (int var58 = i4; var58 <= i3; var58++) {
               CameraVariables cameraVariables_3 = this.scanlines[var58];
               cameraVariables_3.leftX = cameraVariables_3.rightX = j5;
               cameraVariables_3.leftShade = cameraVariables_3.rightShade = i8;
               j5 += k6;
               i8 += k9;
            }
         }

         for (int var60 = 0; var60 < j2; var60++) {
            int k5 = var60 + 1;
            int j3 = ai1[var60];
            int j4 = ai1[k5];
            if (j3 < j4) {
               int l6 = ai[var60] << 8;
               int j8 = (ai[k5] - ai[var60] << 8) / (j4 - j3);
               int l9 = ai2[var60] << 8;
               int l10 = (ai2[k5] - ai2[var60] << 8) / (j4 - j3);
               if (j3 < 0) {
                  l6 -= j8 * j3;
                  l9 -= l10 * j3;
                  j3 = 0;
               }

               if (j4 > this.polygonMaxY) {
                  j4 = this.polygonMaxY;
               }

               for (int l11 = j3; l11 <= j4; l11++) {
                  CameraVariables cameraVariables_4 = this.scanlines[l11];
                  if (l6 < cameraVariables_4.leftX) {
                     cameraVariables_4.leftX = l6;
                     cameraVariables_4.leftShade = l9;
                  }

                  if (l6 > cameraVariables_4.rightX) {
                     cameraVariables_4.rightX = l6;
                     cameraVariables_4.rightShade = l9;
                  }

                  l6 += j8;
                  l9 += l10;
               }
            } else if (j3 > j4) {
               int i7 = ai[k5] << 8;
               int k8 = (ai[var60] - ai[k5] << 8) / (j3 - j4);
               int i10 = ai2[k5] << 8;
               int i11 = (ai2[var60] - ai2[k5] << 8) / (j3 - j4);
               if (j4 < 0) {
                  i7 -= k8 * j4;
                  i10 -= i11 * j4;
                  j4 = 0;
               }

               if (j3 > this.polygonMaxY) {
                  j3 = this.polygonMaxY;
               }

               for (int i12 = j4; i12 <= j3; i12++) {
                  CameraVariables cameraVariables_5 = this.scanlines[i12];
                  if (i7 < cameraVariables_5.leftX) {
                     cameraVariables_5.leftX = i7;
                     cameraVariables_5.leftShade = i10;
                  }

                  if (i7 > cameraVariables_5.rightX) {
                     cameraVariables_5.rightX = i7;
                     cameraVariables_5.rightShade = i10;
                  }

                  i7 += k8;
                  i10 += i11;
               }
            }
         }

         if (this.polygonMinY < this.halfHeight2 - this.halfHeight) {
            this.polygonMinY = this.halfHeight2 - this.halfHeight;
         }
      }

      if (this.mousePickRequested && this.mousePickedCount < this.mousePickedMax && this.mouseY >= this.polygonMinY && this.mouseY < this.polygonMaxY) {
         CameraVariables cameraVariables_1 = this.scanlines[this.mouseY];
         if (this.mouseX >= cameraVariables_1.leftX >> 8
            && this.mouseX <= cameraVariables_1.rightX >> 8
            && cameraVariables_1.leftX <= cameraVariables_1.rightX
            && !model.unpickable
            && model.isLocalPlayer[j1] == 0) {
            this.mousePickedModels[this.mousePickedCount] = model;
            this.mousePickedFaces[this.mousePickedCount] = j1;
            this.mousePickedCount++;
         }
      }
   }

   /* Fills the rows built by generateScanlines. fill `l` >= 0 is a texture
      id; a negative fill encodes a flat 15-bit colour (see rgbToFill) drawn
      with a gouraud ramp; -2 means invisible. For textures it derives the
      perspective mapping directly from the projected-space triangle
      (ai/ai1/ai2 hold projected X/Y/Z): the three cross-product vector pairs
      give u/z, v/z and 1/z as linear functions of the screen position, so
      the per-pixel texel index comes out of two divides per 16-pixel span.
      When f1Toggle (interlace) is on, every other row is skipped and the
      row steps are doubled. */
   private void rasterizeFace(int i, int j, int k, int[] ai, int[] ai1, int[] ai2, int l, Model model) {
      if (l != -2) {
         if (l >= 0) {
            if (l >= this.textureCount) {
               l = 0;
            }

            this.prepareTexture(l);
            int i1 = ai[0];
            int k1 = ai1[0];
            int j2 = ai2[0];
            int i3 = i1 - ai[1];
            int k3 = k1 - ai1[1];
            int i4 = j2 - ai2[1];
            k--;
            int i6 = ai[k] - i1;
            int j7 = ai1[k] - k1;
            int k8 = ai2[k] - j2;
            if (this.textureDimension[l] == 1) {
               int l9 = i6 * k1 - j7 * i1 << 12;
               int k10 = j7 * j2 - k8 * k1 << 5 - this.viewDistance + 7 + 4;
               int i11 = k8 * i1 - i6 * j2 << 5 - this.viewDistance + 7;
               int k11 = i3 * k1 - k3 * i1 << 12;
               int i12 = k3 * j2 - i4 * k1 << 5 - this.viewDistance + 7 + 4;
               int k12 = i4 * i1 - i3 * j2 << 5 - this.viewDistance + 7;
               int i13 = k3 * i6 - i3 * j7 << 5;
               int k13 = i4 * j7 - k3 * k8 << 5 - this.viewDistance + 4;
               int i14 = i3 * k8 - i4 * i6 >> this.viewDistance - 5;
               int k14 = k10 >> 4;
               int i15 = i12 >> 4;
               int k15 = k13 >> 4;
               int i16 = this.polygonMinY - this.halfHeight2;
               int k16 = this.width;
               int i17 = this.halfWidth2 + this.polygonMinY * k16;
               byte byte1 = 1;
               l9 += i11 * i16;
               k11 += k12 * i16;
               i13 += i14 * i16;
               if (this.f1Toggle) {
                  if ((this.polygonMinY & 1) == 1) {
                     this.polygonMinY++;
                     l9 += i11;
                     k11 += k12;
                     i13 += i14;
                     i17 += k16;
                  }

                  i11 <<= 1;
                  k12 <<= 1;
                  i14 <<= 1;
                  k16 <<= 1;
                  byte1 = 2;
               }

               if (model.translucent) {
                  for (int var48 = this.polygonMinY; var48 < this.polygonMaxY; var48 += byte1) {
                     CameraVariables cameraVariables_3 = this.scanlines[var48];
                     j = cameraVariables_3.leftX >> 8;
                     int k17 = cameraVariables_3.rightX >> 8;
                     int k20 = k17 - j;
                     if (k20 <= 0) {
                        l9 += i11;
                        k11 += k12;
                        i13 += i14;
                        i17 += k16;
                     } else {
                        int i22 = cameraVariables_3.leftShade;
                        int k23 = (cameraVariables_3.rightShade - i22) / k20;
                        if (j < -this.halfWidth) {
                           i22 += (-this.halfWidth - j) * k23;
                           j = -this.halfWidth;
                           k20 = k17 - j;
                        }

                        if (k17 > this.halfWidth) {
                           int l17 = this.halfWidth;
                           k20 = l17 - j;
                        }

                        textureScanline128Translucent(
                           this.framebufferPixels,
                           this.texturePixels[l],
                           0,
                           0,
                           l9 + k14 * j,
                           k11 + i15 * j,
                           i13 + k15 * j,
                           k10,
                           i12,
                           k13,
                           k20,
                           i17 + j,
                           i22,
                           k23 << 2
                        );
                        l9 += i11;
                        k11 += k12;
                        i13 += i14;
                        i17 += k16;
                     }
                  }
               } else if (!this.textureBackTransparent[l]) {
                  for (int var47 = this.polygonMinY; var47 < this.polygonMaxY; var47 += byte1) {
                     CameraVariables cameraVariables_4 = this.scanlines[var47];
                     j = cameraVariables_4.leftX >> 8;
                     int i18 = cameraVariables_4.rightX >> 8;
                     int l20 = i18 - j;
                     if (l20 <= 0) {
                        l9 += i11;
                        k11 += k12;
                        i13 += i14;
                        i17 += k16;
                     } else {
                        int j22 = cameraVariables_4.leftShade;
                        int l23 = (cameraVariables_4.rightShade - j22) / l20;
                        if (j < -this.halfWidth) {
                           j22 += (-this.halfWidth - j) * l23;
                           j = -this.halfWidth;
                           l20 = i18 - j;
                        }

                        if (i18 > this.halfWidth) {
                           int j18 = this.halfWidth;
                           l20 = j18 - j;
                        }

                        textureScanline128(
                           this.framebufferPixels,
                           this.texturePixels[l],
                           0,
                           0,
                           l9 + k14 * j,
                           k11 + i15 * j,
                           i13 + k15 * j,
                           k10,
                           i12,
                           k13,
                           l20,
                           i17 + j,
                           j22,
                           l23 << 2
                        );
                        l9 += i11;
                        k11 += k12;
                        i13 += i14;
                        i17 += k16;
                     }
                  }
               } else {
                  for (int var46 = this.polygonMinY; var46 < this.polygonMaxY; var46 += byte1) {
                     CameraVariables cameraVariables_5 = this.scanlines[var46];
                     j = cameraVariables_5.leftX >> 8;
                     int k18 = cameraVariables_5.rightX >> 8;
                     int i21 = k18 - j;
                     if (i21 <= 0) {
                        l9 += i11;
                        k11 += k12;
                        i13 += i14;
                        i17 += k16;
                     } else {
                        int k22 = cameraVariables_5.leftShade;
                        int i24 = (cameraVariables_5.rightShade - k22) / i21;
                        if (j < -this.halfWidth) {
                           k22 += (-this.halfWidth - j) * i24;
                           j = -this.halfWidth;
                           i21 = k18 - j;
                        }

                        if (k18 > this.halfWidth) {
                           int l18 = this.halfWidth;
                           i21 = l18 - j;
                        }

                        textureScanline128Transparent(
                           this.framebufferPixels,
                           0,
                           0,
                           0,
                           this.texturePixels[l],
                           l9 + k14 * j,
                           k11 + i15 * j,
                           i13 + k15 * j,
                           k10,
                           i12,
                           k13,
                           i21,
                           i17 + j,
                           k22,
                           i24
                        );
                        l9 += i11;
                        k11 += k12;
                        i13 += i14;
                        i17 += k16;
                     }
                  }
               }
            } else {
               int i10 = i6 * k1 - j7 * i1 << 11;
               int l10 = j7 * j2 - k8 * k1 << 5 - this.viewDistance + 6 + 4;
               int j11 = k8 * i1 - i6 * j2 << 5 - this.viewDistance + 6;
               int l11 = i3 * k1 - k3 * i1 << 11;
               int j12 = k3 * j2 - i4 * k1 << 5 - this.viewDistance + 6 + 4;
               int l12 = i4 * i1 - i3 * j2 << 5 - this.viewDistance + 6;
               int j13 = k3 * i6 - i3 * j7 << 5;
               int l13 = i4 * j7 - k3 * k8 << 5 - this.viewDistance + 4;
               int j14 = i3 * k8 - i4 * i6 >> this.viewDistance - 5;
               int l14 = l10 >> 4;
               int j15 = j12 >> 4;
               int l15 = l13 >> 4;
               int j16 = this.polygonMinY - this.halfHeight2;
               int l16 = this.width;
               int j17 = this.halfWidth2 + this.polygonMinY * l16;
               byte byte2 = 1;
               i10 += j11 * j16;
               l11 += l12 * j16;
               j13 += j14 * j16;
               if (this.f1Toggle) {
                  if ((this.polygonMinY & 1) == 1) {
                     this.polygonMinY++;
                     i10 += j11;
                     l11 += l12;
                     j13 += j14;
                     j17 += l16;
                  }

                  j11 <<= 1;
                  l12 <<= 1;
                  j14 <<= 1;
                  l16 <<= 1;
                  byte2 = 2;
               }

               if (model.translucent) {
                  for (int var45 = this.polygonMinY; var45 < this.polygonMaxY; var45 += byte2) {
                     CameraVariables cameraVariables_6 = this.scanlines[var45];
                     j = cameraVariables_6.leftX >> 8;
                     int i19 = cameraVariables_6.rightX >> 8;
                     int j21 = i19 - j;
                     if (j21 <= 0) {
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                     } else {
                        int l22 = cameraVariables_6.leftShade;
                        int j24 = (cameraVariables_6.rightShade - l22) / j21;
                        if (j < -this.halfWidth) {
                           l22 += (-this.halfWidth - j) * j24;
                           j = -this.halfWidth;
                           j21 = i19 - j;
                        }

                        if (i19 > this.halfWidth) {
                           int j19 = this.halfWidth;
                           j21 = j19 - j;
                        }

                        textureScanline64Translucent(
                           this.framebufferPixels,
                           this.texturePixels[l],
                           0,
                           0,
                           i10 + l14 * j,
                           l11 + j15 * j,
                           j13 + l15 * j,
                           l10,
                           j12,
                           l13,
                           j21,
                           j17 + j,
                           l22,
                           j24
                        );
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                     }
                  }
               } else if (!this.textureBackTransparent[l]) {
                  for (int var44 = this.polygonMinY; var44 < this.polygonMaxY; var44 += byte2) {
                     CameraVariables cameraVariables_7 = this.scanlines[var44];
                     j = cameraVariables_7.leftX >> 8;
                     int k19 = cameraVariables_7.rightX >> 8;
                     int k21 = k19 - j;
                     if (k21 <= 0) {
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                     } else {
                        int i23 = cameraVariables_7.leftShade;
                        int k24 = (cameraVariables_7.rightShade - i23) / k21;
                        if (j < -this.halfWidth) {
                           i23 += (-this.halfWidth - j) * k24;
                           j = -this.halfWidth;
                           k21 = k19 - j;
                        }

                        if (k19 > this.halfWidth) {
                           int l19 = this.halfWidth;
                           k21 = l19 - j;
                        }

                        textureScanline64(
                           this.framebufferPixels,
                           this.texturePixels[l],
                           0,
                           0,
                           i10 + l14 * j,
                           l11 + j15 * j,
                           j13 + l15 * j,
                           l10,
                           j12,
                           l13,
                           k21,
                           j17 + j,
                           i23,
                           k24
                        );
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                     }
                  }
               } else {
                  for (int var43 = this.polygonMinY; var43 < this.polygonMaxY; var43 += byte2) {
                     CameraVariables cameraVariables_8 = this.scanlines[var43];
                     j = cameraVariables_8.leftX >> 8;
                     int i20 = cameraVariables_8.rightX >> 8;
                     int l21 = i20 - j;
                     if (l21 <= 0) {
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                     } else {
                        int j23 = cameraVariables_8.leftShade;
                        int l24 = (cameraVariables_8.rightShade - j23) / l21;
                        if (j < -this.halfWidth) {
                           j23 += (-this.halfWidth - j) * l24;
                           j = -this.halfWidth;
                           l21 = i20 - j;
                        }

                        if (i20 > this.halfWidth) {
                           int j20 = this.halfWidth;
                           l21 = j20 - j;
                        }

                        textureScanline64Transparent(
                           this.framebufferPixels,
                           0,
                           0,
                           0,
                           this.texturePixels[l],
                           i10 + l14 * j,
                           l11 + j15 * j,
                           j13 + l15 * j,
                           l10,
                           j12,
                           l13,
                           l21,
                           j17 + j,
                           j23,
                           l24
                        );
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                     }
                  }
               }
            }
         } else {
            for (int j1 = 0; j1 < this.gradientRampCount; j1++) {
               if (this.gradientRampFill[j1] == l) {
                  this.currentGradientRamp = this.gradientRamps[j1];
                  break;
               }

               if (j1 == this.gradientRampCount - 1) {
                  int l1 = (int)(Math.random() * (double)this.gradientRampCount);
                  this.gradientRampFill[l1] = l;
                  l = -1 - l;
                  int k2 = (l >> 10 & 31) * 8;
                  int j3 = (l >> 5 & 31) * 8;
                  int l3 = (l & 31) * 8;

                  for (int j4 = 0; j4 < 256; j4++) {
                     int j6 = j4 * j4;
                     int k7 = k2 * j6 / 65536;
                     int l8 = j3 * j6 / 65536;
                     int j10 = l3 * j6 / 65536;
                     this.gradientRamps[l1][255 - j4] = (k7 << 16) + (l8 << 8) + j10;
                  }

                  this.currentGradientRamp = this.gradientRamps[l1];
               }
            }

            int i2 = this.width;
            int l2 = this.halfWidth2 + this.polygonMinY * i2;
            byte byte0 = 1;
            if (this.f1Toggle) {
               if ((this.polygonMinY & 1) == 1) {
                  this.polygonMinY++;
                  l2 += i2;
               }

               i2 <<= 1;
               byte0 = 2;
            }

            if (model.isGiantCrystal) {
               for (int var42 = this.polygonMinY; var42 < this.polygonMaxY; var42 += byte0) {
                  CameraVariables scanlines = this.scanlines[var42];
                  j = scanlines.leftX >> 8;
                  int k4 = scanlines.rightX >> 8;
                  int k6 = k4 - j;
                  if (k6 <= 0) {
                     l2 += i2;
                  } else {
                     int l7 = scanlines.leftShade;
                     int i9 = (scanlines.rightShade - l7) / k6;
                     if (j < -this.halfWidth) {
                        l7 += (-this.halfWidth - j) * i9;
                        j = -this.halfWidth;
                        k6 = k4 - j;
                     }

                     if (k4 > this.halfWidth) {
                        int l4 = this.halfWidth;
                        k6 = l4 - j;
                     }

                     gradientScanlineTranslucent(this.framebufferPixels, -k6, l2 + j, 0, this.currentGradientRamp, l7, i9);
                     l2 += i2;
                  }
               }
            } else if (this.halfResGradient) {
               for (int var41 = this.polygonMinY; var41 < this.polygonMaxY; var41 += byte0) {
                  CameraVariables cameraVariables_1 = this.scanlines[var41];
                  j = cameraVariables_1.leftX >> 8;
                  int i5 = cameraVariables_1.rightX >> 8;
                  int l6 = i5 - j;
                  if (l6 <= 0) {
                     l2 += i2;
                  } else {
                     int i8 = cameraVariables_1.leftShade;
                     int j9 = (cameraVariables_1.rightShade - i8) / l6;
                     if (j < -this.halfWidth) {
                        i8 += (-this.halfWidth - j) * j9;
                        j = -this.halfWidth;
                        l6 = i5 - j;
                     }

                     if (i5 > this.halfWidth) {
                        int j5 = this.halfWidth;
                        l6 = j5 - j;
                     }

                     gradientScanlineHalfRes(this.framebufferPixels, -l6, l2 + j, 0, this.currentGradientRamp, i8, j9);
                     l2 += i2;
                  }
               }
            } else {
               for (int var40 = this.polygonMinY; var40 < this.polygonMaxY; var40 += byte0) {
                  CameraVariables cameraVariables_2 = this.scanlines[var40];
                  j = cameraVariables_2.leftX >> 8;
                  int k5 = cameraVariables_2.rightX >> 8;
                  int i7 = k5 - j;
                  if (i7 <= 0) {
                     l2 += i2;
                  } else {
                     int j8 = cameraVariables_2.leftShade;
                     int k9 = (cameraVariables_2.rightShade - j8) / i7;
                     if (j < -this.halfWidth) {
                        j8 += (-this.halfWidth - j) * k9;
                        j = -this.halfWidth;
                        i7 = k5 - j;
                     }

                     if (k5 > this.halfWidth) {
                        int l5 = this.halfWidth;
                        i7 = l5 - j;
                     }

                     gradientScanline(this.framebufferPixels, -i7, l2 + j, 0, this.currentGradientRamp, j8, k9);
                     l2 += i2;
                  }
               }
            }
         }
      }
   }

   /* The textured scanline inner loops. Perspective correction is done
      piecewise: u/z, v/z, 1/z are stepped linearly and real u,v recomputed
      by division every 16 pixels, with texel steps interpolated in between.
      The texture atlas rows hold 4 pre-darkened copies of the texture
      (see setTexturePixels); the shade term k2/l2 carries the copy offset in
      its high bits and the >>> shift in bits 23+, so lighting is a table
      pick, not a multiply. 128- and 64-pixel textures get separate copies of
      the loop (masks 16256/0x3F80 vs 4032/0xFC0), each in opaque,
      translucent (50/50 blend via (pixel >> 1 & 0x7F7F7F)) and transparent
      (skip colour 0) variants. */
   private static void textureScanline128(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2) {
      if (i2 > 0) {
         int i3 = 0;
         int j3 = 0;
         int i4 = 0;
         if (i1 != 0) {
            i = k / i1 << 7;
            j = l / i1 << 7;
         }

         if (i < 0) {
            i = 0;
         } else if (i > 16256) {
            i = 16256;
         }

         k += j1;
         l += k1;
         i1 += l1;
         if (i1 != 0) {
            i3 = k / i1 << 7;
            j3 = l / i1 << 7;
         }

         if (i3 < 0) {
            i3 = 0;
         } else if (i3 > 16256) {
            i3 = 16256;
         }

         int k3 = i3 - i >> 4;
         int l3 = j3 - j >> 4;

         for (int j4 = i2 >> 4; j4 > 0; j4--) {
            i += k2 & 6291456;
            int var75 = k2 >> 23;
            k2 += l2;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var75;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var75;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var75;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var75;
            i += k3;
            j += l3;
            i = (i & 16383) + (k2 & 6291456);
            int var76 = k2 >> 23;
            k2 += l2;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var76;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var76;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var76;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var76;
            i += k3;
            j += l3;
            i = (i & 16383) + (k2 & 6291456);
            int var77 = k2 >> 23;
            k2 += l2;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var77;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var77;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var77;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> var77;
            i += k3;
            j += l3;
            i = (i & 16383) + (k2 & 6291456);
            i4 = k2 >> 23;
            k2 += l2;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> i4;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> i4;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> i4;
            i += k3;
            j += l3;
            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> i4;
            i = i3;
            j = j3;
            k += j1;
            l += k1;
            i1 += l1;
            if (i1 != 0) {
               i3 = k / i1 << 7;
               j3 = l / i1 << 7;
            }

            if (i3 < 0) {
               i3 = 0;
            } else if (i3 > 16256) {
               i3 = 16256;
            }

            k3 = i3 - i >> 4;
            l3 = j3 - j >> 4;
         }

         for (int k4 = 0; k4 < (i2 & 15); k4++) {
            if ((k4 & 3) == 0) {
               i = (i & 16383) + (k2 & 6291456);
               i4 = k2 >> 23;
               k2 += l2;
            }

            ai[j2++] = ai1[(j & 16256) + (i >> 7)] >>> i4;
            i += k3;
            j += l3;
         }
      }
   }

   private static void textureScanline128Translucent(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2) {
      if (i2 > 0) {
         int i3 = 0;
         int j3 = 0;
         int i4 = 0;
         if (i1 != 0) {
            i = k / i1 << 7;
            j = l / i1 << 7;
         }

         if (i < 0) {
            i = 0;
         } else if (i > 16256) {
            i = 16256;
         }

         k += j1;
         l += k1;
         i1 += l1;
         if (i1 != 0) {
            i3 = k / i1 << 7;
            j3 = l / i1 << 7;
         }

         if (i3 < 0) {
            i3 = 0;
         } else if (i3 > 16256) {
            i3 = 16256;
         }

         int k3 = i3 - i >> 4;
         int l3 = j3 - j >> 4;

         for (int j4 = i2 >> 4; j4 > 0; j4--) {
            i += k2 & 6291456;
            int var75 = k2 >> 23;
            k2 += l2;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var75) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var75) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var75) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var75) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            i = (i & 16383) + (k2 & 6291456);
            int var76 = k2 >> 23;
            k2 += l2;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var76) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var76) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var76) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var76) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            i = (i & 16383) + (k2 & 6291456);
            int var77 = k2 >> 23;
            k2 += l2;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var77) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var77) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var77) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> var77) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            i = (i & 16383) + (k2 & 6291456);
            i4 = k2 >> 23;
            k2 += l2;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> i4) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> i4) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> i4) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> i4) + (ai[j2] >> 1 & 8355711);
            i = i3;
            j = j3;
            k += j1;
            l += k1;
            i1 += l1;
            if (i1 != 0) {
               i3 = k / i1 << 7;
               j3 = l / i1 << 7;
            }

            if (i3 < 0) {
               i3 = 0;
            } else if (i3 > 16256) {
               i3 = 16256;
            }

            k3 = i3 - i >> 4;
            l3 = j3 - j >> 4;
         }

         for (int k4 = 0; k4 < (i2 & 15); k4++) {
            if ((k4 & 3) == 0) {
               i = (i & 16383) + (k2 & 6291456);
               i4 = k2 >> 23;
               k2 += l2;
            }

            ai[j2++] = (ai1[(j & 16256) + (i >> 7)] >>> i4) + (ai[j2] >> 1 & 8355711);
            i += k3;
            j += l3;
         }
      }
   }

   private static void textureScanline128Transparent(int[] ai, int i, int j, int k, int[] ai1, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2, int i3) {
      if (j2 > 0) {
         int j3 = 0;
         int k3 = 0;
         i3 <<= 2;
         if (j1 != 0) {
            j3 = l / j1 << 7;
            k3 = i1 / j1 << 7;
         }

         if (j3 < 0) {
            j3 = 0;
         } else if (j3 > 16256) {
            j3 = 16256;
         }

         for (int j4 = j2; j4 > 0; j4 -= 16) {
            l += k1;
            i1 += l1;
            j1 += i2;
            j = j3;
            k = k3;
            if (j1 != 0) {
               j3 = l / j1 << 7;
               k3 = i1 / j1 << 7;
            }

            if (j3 < 0) {
               j3 = 0;
            } else if (j3 > 16256) {
               j3 = 16256;
            }

            int l3 = j3 - j >> 4;
            int i4 = k3 - k >> 4;
            int k4 = l2 >> 23;
            j += l2 & 6291456;
            l2 += i3;
            if (j4 < 16) {
               for (int l4 = 0; l4 < j4; l4++) {
                  if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                     ai[k2] = i;
                  }

                  k2++;
                  j += l3;
                  k += i4;
                  if ((l4 & 3) == 3) {
                     j = (j & 16383) + (l2 & 6291456);
                     k4 = l2 >> 23;
                     l2 += i3;
                  }
               }
            } else {
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               j = (j & 16383) + (l2 & 6291456);
               k4 = l2 >> 23;
               l2 += i3;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               j = (j & 16383) + (l2 & 6291456);
               k4 = l2 >> 23;
               l2 += i3;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               j = (j & 16383) + (l2 & 6291456);
               k4 = l2 >> 23;
               l2 += i3;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 16256) + (j >> 7)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
            }
         }
      }
   }

   private static void textureScanline64(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2) {
      if (i2 > 0) {
         int i3 = 0;
         int j3 = 0;
         l2 <<= 2;
         if (i1 != 0) {
            i3 = k / i1 << 6;
            j3 = l / i1 << 6;
         }

         if (i3 < 0) {
            i3 = 0;
         } else if (i3 > 4032) {
            i3 = 4032;
         }

         for (int i4 = i2; i4 > 0; i4 -= 16) {
            k += j1;
            l += k1;
            i1 += l1;
            i = i3;
            j = j3;
            if (i1 != 0) {
               i3 = k / i1 << 6;
               j3 = l / i1 << 6;
            }

            if (i3 < 0) {
               i3 = 0;
            } else if (i3 > 4032) {
               i3 = 4032;
            }

            int k3 = i3 - i >> 4;
            int l3 = j3 - j >> 4;
            int j4 = k2 >> 20;
            i += k2 & 786432;
            k2 += l2;
            if (i4 < 16) {
               for (int k4 = 0; k4 < i4; k4++) {
                  ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
                  i += k3;
                  j += l3;
                  if ((k4 & 3) == 3) {
                     i = (i & 4095) + (k2 & 786432);
                     j4 = k2 >> 20;
                     k2 += l2;
                  }
               }
            } else {
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               i = (i & 4095) + (k2 & 786432);
               j4 = k2 >> 20;
               k2 += l2;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               i = (i & 4095) + (k2 & 786432);
               j4 = k2 >> 20;
               k2 += l2;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               i = (i & 4095) + (k2 & 786432);
               j4 = k2 >> 20;
               k2 += l2;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
               i += k3;
               j += l3;
               ai[j2++] = ai1[(j & 4032) + (i >> 6)] >>> j4;
            }
         }
      }
   }

   private static void textureScanline64Translucent(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2) {
      if (i2 > 0) {
         int i3 = 0;
         int j3 = 0;
         l2 <<= 2;
         if (i1 != 0) {
            i3 = k / i1 << 6;
            j3 = l / i1 << 6;
         }

         if (i3 < 0) {
            i3 = 0;
         } else if (i3 > 4032) {
            i3 = 4032;
         }

         for (int i4 = i2; i4 > 0; i4 -= 16) {
            k += j1;
            l += k1;
            i1 += l1;
            i = i3;
            j = j3;
            if (i1 != 0) {
               i3 = k / i1 << 6;
               j3 = l / i1 << 6;
            }

            if (i3 < 0) {
               i3 = 0;
            } else if (i3 > 4032) {
               i3 = 4032;
            }

            int k3 = i3 - i >> 4;
            int l3 = j3 - j >> 4;
            int j4 = k2 >> 20;
            i += k2 & 786432;
            k2 += l2;
            if (i4 < 16) {
               for (int k4 = 0; k4 < i4; k4++) {
                  ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
                  i += k3;
                  j += l3;
                  if ((k4 & 3) == 3) {
                     i = (i & 4095) + (k2 & 786432);
                     j4 = k2 >> 20;
                     k2 += l2;
                  }
               }
            } else {
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               i = (i & 4095) + (k2 & 786432);
               j4 = k2 >> 20;
               k2 += l2;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               i = (i & 4095) + (k2 & 786432);
               j4 = k2 >> 20;
               k2 += l2;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               i = (i & 4095) + (k2 & 786432);
               j4 = k2 >> 20;
               k2 += l2;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
               i += k3;
               j += l3;
               ai[j2++] = (ai1[(j & 4032) + (i >> 6)] >>> j4) + (ai[j2] >> 1 & 8355711);
            }
         }
      }
   }

   private static void textureScanline64Transparent(int[] ai, int i, int j, int k, int[] ai1, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2, int i3) {
      if (j2 > 0) {
         int j3 = 0;
         int k3 = 0;
         i3 <<= 2;
         if (j1 != 0) {
            j3 = l / j1 << 6;
            k3 = i1 / j1 << 6;
         }

         if (j3 < 0) {
            j3 = 0;
         } else if (j3 > 4032) {
            j3 = 4032;
         }

         for (int j4 = j2; j4 > 0; j4 -= 16) {
            l += k1;
            i1 += l1;
            j1 += i2;
            j = j3;
            k = k3;
            if (j1 != 0) {
               j3 = l / j1 << 6;
               k3 = i1 / j1 << 6;
            }

            if (j3 < 0) {
               j3 = 0;
            } else if (j3 > 4032) {
               j3 = 4032;
            }

            int l3 = j3 - j >> 4;
            int i4 = k3 - k >> 4;
            int k4 = l2 >> 20;
            j += l2 & 786432;
            l2 += i3;
            if (j4 < 16) {
               for (int l4 = 0; l4 < j4; l4++) {
                  if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                     ai[k2] = i;
                  }

                  k2++;
                  j += l3;
                  k += i4;
                  if ((l4 & 3) == 3) {
                     j = (j & 4095) + (l2 & 786432);
                     k4 = l2 >> 20;
                     l2 += i3;
                  }
               }
            } else {
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               j = (j & 4095) + (l2 & 786432);
               k4 = l2 >> 20;
               l2 += i3;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               j = (j & 4095) + (l2 & 786432);
               k4 = l2 >> 20;
               l2 += i3;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               j = (j & 4095) + (l2 & 786432);
               k4 = l2 >> 20;
               l2 += i3;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
               j += l3;
               k += i4;
               if ((i = ai1[(k & 4032) + (j >> 6)] >>> k4) != 0) {
                  ai[k2] = i;
               }

               k2++;
            }
         }
      }
   }

   /* Gouraud scanline that advances the ramp every 2 pixels (half-res
      shading). Only used when halfResGradient is set, which nothing in this
      client currently does. */
   private static void gradientScanlineHalfRes(int[] ai, int i, int j, int k, int[] ai1, int l, int i1) {
      if (i < 0) {
         i1 <<= 1;
         k = ai1[l >> 8 & 0xFF];
         l += i1;
         int j1 = i / 8;

         for (int k1 = j1; k1 < 0; k1++) {
            ai[j++] = k;
            ai[j++] = k;
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k;
            ai[j++] = k;
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k;
            ai[j++] = k;
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k;
            ai[j++] = k;
            k = ai1[l >> 8 & 0xFF];
            l += i1;
         }

         j1 = -(i % 8);

         for (int l1 = 0; l1 < j1; l1++) {
            ai[j++] = k;
            if ((l1 & 1) == 1) {
               k = ai1[l >> 8 & 0xFF];
               l += i1;
            }
         }
      }
   }

   private static void gradientScanlineTranslucent(int[] ai, int i, int j, int k, int[] ai1, int l, int i1) {
      if (i < 0) {
         i1 <<= 2;
         k = ai1[l >> 8 & 0xFF];
         l += i1;
         int j1 = i / 16;

         for (int k1 = j1; k1 < 0; k1++) {
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            k = ai1[l >> 8 & 0xFF];
            l += i1;
         }

         j1 = -(i % 16);

         for (int l1 = 0; l1 < j1; l1++) {
            ai[j++] = k + (ai[j] >> 1 & 8355711);
            if ((l1 & 3) == 3) {
               k = ai1[l >> 8 & 0xFF];
               l += i1;
               l += i1;
            }
         }
      }
   }

   private static void gradientScanline(int[] ai, int i, int j, int k, int[] ai1, int l, int i1) {
      if (i < 0) {
         i1 <<= 2;
         k = ai1[l >> 8 & 0xFF];
         l += i1;
         int j1 = i / 16;

         for (int k1 = j1; k1 < 0; k1++) {
            ai[j++] = k;
            ai[j++] = k;
            ai[j++] = k;
            ai[j++] = k;
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k;
            ai[j++] = k;
            ai[j++] = k;
            ai[j++] = k;
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k;
            ai[j++] = k;
            ai[j++] = k;
            ai[j++] = k;
            k = ai1[l >> 8 & 0xFF];
            l += i1;
            ai[j++] = k;
            ai[j++] = k;
            ai[j++] = k;
            ai[j++] = k;
            k = ai1[l >> 8 & 0xFF];
            l += i1;
         }

         j1 = -(i % 16);

         for (int l1 = 0; l1 < j1; l1++) {
            ai[j++] = k;
            if ((l1 & 3) == 3) {
               k = ai1[l >> 8 & 0xFF];
               l += i1;
            }
         }
      }
   }

   /* Places the camera `distance` units back from the focus point along the
      view direction given by the (binary, 1024 = full turn) pitch/yaw/roll:
      the vector (0, 0, distance) is rotated by the three angles and
      subtracted from the focus. mudclient passes pitch 912, i.e. the classic
      slightly-above-horizontal RSC camera tilt. */
   public void setCamera(int focusX, int focusY, int focusZ, int pitch, int yaw, int roll, int distance) {
      pitch &= 1023;
      yaw &= 1023;
      roll &= 1023;
      this.cameraPitch = 1024 - pitch & 1023;
      this.cameraYaw = 1024 - yaw & 1023;
      this.cameraRoll = 1024 - roll & 1023;
      int offsetX = 0;
      int offsetY = 0;
      int offsetZ = distance;
      if (pitch != 0) {
         int k2 = sinCosTable1024[pitch];
         int j3 = sinCosTable1024[pitch + 1024];
         int i4 = offsetY * j3 - distance * k2 >> 15;
         offsetZ = offsetY * k2 + distance * j3 >> 15;
         offsetY = i4;
      }

      if (yaw != 0) {
         int l2 = sinCosTable1024[yaw];
         int k3 = sinCosTable1024[yaw + 1024];
         int j4 = offsetZ * l2 + offsetX * k3 >> 15;
         offsetZ = offsetZ * k3 - offsetX * l2 >> 15;
         offsetX = j4;
      }

      if (roll != 0) {
         int i3 = sinCosTable1024[roll];
         int l3 = sinCosTable1024[roll + 1024];
         int k4 = offsetY * i3 + offsetX * l3 >> 15;
         offsetY = offsetY * l3 - offsetX * i3 >> 15;
         offsetX = k4;
      }

      this.cameraX = focusX - offsetX;
      this.cameraY = focusY - offsetY;
      this.cameraZ = focusZ - offsetZ;
   }

   /* Fills in the CameraModel record for a 3D face: face normal (in
      projected space, via the first three vertices), the `visibility`
      plane-side value (dot of normal with a vertex position, sign = which
      side faces the camera), and the screen/depth bounding box used by the
      sort passes. faceCameraNormalScale -1 means the normal hasn't been
      scaled yet: the components are shifted down until they fit in +/-25000
      and the shift plus |normal| * normalMagnitudeScale are cached on the
      model. */
   private void initFaceRecord(int i) {
      CameraModel cameraModel = this.visibleFaces[i];
      Model model = cameraModel.model;
      int j = cameraModel.faceIndex;
      int[] ai = model.faceVertices[j];
      int k = model.faceNumVertices[j];
      int l = model.faceCameraNormalScale[j];
      int j1 = model.projectVertexX[ai[0]];
      int k1 = model.projectVertexY[ai[0]];
      int l1 = model.projectVertexZ[ai[0]];
      int i2 = model.projectVertexX[ai[1]] - j1;
      int j2 = model.projectVertexY[ai[1]] - k1;
      int k2 = model.projectVertexZ[ai[1]] - l1;
      int l2 = model.projectVertexX[ai[2]] - j1;
      int i3 = model.projectVertexY[ai[2]] - k1;
      int j3 = model.projectVertexZ[ai[2]] - l1;
      int k3 = j2 * j3 - i3 * k2;
      int l3 = k2 * l2 - j3 * i2;
      int i4 = i2 * i3 - l2 * j2;
      if (l == -1) {
         for (l = 0; k3 > 25000 || l3 > 25000 || i4 > 25000 || k3 < -25000 || l3 < -25000 || i4 < -25000; i4 >>= 1) {
            l++;
            k3 >>= 1;
            l3 >>= 1;
         }

         model.faceCameraNormalScale[j] = l;
         model.faceCameraNormalMagnitude[j] = (int)((double)this.normalMagnitudeScale * Math.sqrt((double)(k3 * k3 + l3 * l3 + i4 * i4)));
      } else {
         k3 >>= l;
         l3 >>= l;
         i4 >>= l;
      }

      cameraModel.visibility = j1 * k3 + k1 * l3 + l1 * i4;
      cameraModel.normalX = k3;
      cameraModel.normalY = l3;
      cameraModel.normalZ = i4;
      int j4 = model.projectVertexZ[ai[0]];
      int k4 = j4;
      int l4 = model.vertexViewX[ai[0]];
      int i5 = l4;
      int j5 = model.vertexViewY[ai[0]];
      int k5 = j5;

      for (int l5 = 1; l5 < k; l5++) {
         int i1 = model.projectVertexZ[ai[l5]];
         if (i1 > k4) {
            k4 = i1;
         } else if (i1 < j4) {
            j4 = i1;
         }

         i1 = model.vertexViewX[ai[l5]];
         if (i1 > i5) {
            i5 = i1;
         } else if (i1 < l4) {
            l4 = i1;
         }

         i1 = model.vertexViewY[ai[l5]];
         if (i1 > k5) {
            k5 = i1;
         } else if (i1 < j5) {
            j5 = i1;
         }
      }

      cameraModel.minDepth = j4;
      cameraModel.maxDepth = k4;
      cameraModel.minScreenX = l4;
      cameraModel.maxScreenX = i5;
      cameraModel.minScreenY = j5;
      cameraModel.maxScreenY = k5;
   }

   /* Sprite-face variant of initFaceRecord: the normal is fixed to +Z and
      the screen box is widened by 20 pixels each side to keep the depth sort
      stable for thin billboards. */
   private void initSpriteRecord(int i) {
      CameraModel cameraModel = this.visibleFaces[i];
      Model model = cameraModel.model;
      int j = cameraModel.faceIndex;
      int[] ai = model.faceVertices[j];
      int l = 0;
      int i1 = 0;
      int j1 = 1;
      int k1 = model.projectVertexX[ai[0]];
      int l1 = model.projectVertexY[ai[0]];
      int i2 = model.projectVertexZ[ai[0]];
      model.faceCameraNormalMagnitude[j] = 1;
      model.faceCameraNormalScale[j] = 0;
      cameraModel.visibility = k1 * l + l1 * i1 + i2 * j1;
      cameraModel.normalX = l;
      cameraModel.normalY = i1;
      cameraModel.normalZ = j1;
      int j2 = model.projectVertexZ[ai[0]];
      int k2 = j2;
      int l2 = model.vertexViewX[ai[0]];
      int i3 = l2;
      if (model.vertexViewX[ai[1]] < l2) {
         l2 = model.vertexViewX[ai[1]];
      } else {
         i3 = model.vertexViewX[ai[1]];
      }

      int j3 = model.vertexViewY[ai[1]];
      int k3 = model.vertexViewY[ai[0]];
      int k = model.projectVertexZ[ai[1]];
      if (k > j2) {
         k2 = k;
      } else if (k < j2) {
         j2 = k;
      }

      k = model.vertexViewX[ai[1]];
      if (k > i3) {
         i3 = k;
      } else if (k < l2) {
         l2 = k;
      }

      k = model.vertexViewY[ai[1]];
      if (k > k3) {
         k3 = k;
      } else if (k < j3) {
         j3 = k;
      }

      cameraModel.minDepth = j2;
      cameraModel.maxDepth = k2;
      cameraModel.minScreenX = l2 - 20;
      cameraModel.maxScreenX = i3 + 20;
      cameraModel.minScreenY = j3;
      cameraModel.maxScreenY = k3;
   }

   /* True if the two faces cannot occlude each other: disjoint screen
      rects, disjoint depth ranges, one entirely on the far side of the
      other's plane, or (final resort) their screen polygons don't
      intersect. */
   private boolean facesAreSeparated(CameraModel cameraModel, CameraModel cameraModel_1) {
      if (cameraModel.minScreenX >= cameraModel_1.maxScreenX) {
         return true;
      } else if (cameraModel_1.minScreenX >= cameraModel.maxScreenX) {
         return true;
      } else if (cameraModel.minScreenY >= cameraModel_1.maxScreenY) {
         return true;
      } else if (cameraModel_1.minScreenY >= cameraModel.maxScreenY) {
         return true;
      } else if (cameraModel.minDepth >= cameraModel_1.maxDepth) {
         return true;
      } else if (cameraModel_1.minDepth > cameraModel.maxDepth) {
         return false;
      } else {
         Model model = cameraModel.model;
         Model model_1 = cameraModel_1.model;
         int i = cameraModel.faceIndex;
         int j = cameraModel_1.faceIndex;
         int[] ai = model.faceVertices[i];
         int[] ai1 = model_1.faceVertices[j];
         int k = model.faceNumVertices[i];
         int l = model_1.faceNumVertices[j];
         int k2 = model_1.projectVertexX[ai1[0]];
         int l2 = model_1.projectVertexY[ai1[0]];
         int i3 = model_1.projectVertexZ[ai1[0]];
         int j3 = cameraModel_1.normalX;
         int k3 = cameraModel_1.normalY;
         int l3 = cameraModel_1.normalZ;
         int i4 = model_1.faceCameraNormalMagnitude[j];
         int j4 = cameraModel_1.visibility;
         boolean flag = false;

         for (int k4 = 0; k4 < k; k4++) {
            int i1 = ai[k4];
            int i2 = (k2 - model.projectVertexX[i1]) * j3 + (l2 - model.projectVertexY[i1]) * k3 + (i3 - model.projectVertexZ[i1]) * l3;
            if (i2 < -i4 && j4 < 0 || i2 > i4 && j4 > 0) {
               flag = true;
               break;
            }
         }

         if (!flag) {
            return true;
         } else {
            k2 = model.projectVertexX[ai[0]];
            l2 = model.projectVertexY[ai[0]];
            i3 = model.projectVertexZ[ai[0]];
            j3 = cameraModel.normalX;
            k3 = cameraModel.normalY;
            l3 = cameraModel.normalZ;
            i4 = model.faceCameraNormalMagnitude[i];
            j4 = cameraModel.visibility;
            flag = false;

            for (int l4 = 0; l4 < l; l4++) {
               int j1 = ai1[l4];
               int j2 = (k2 - model_1.projectVertexX[j1]) * j3 + (l2 - model_1.projectVertexY[j1]) * k3 + (i3 - model_1.projectVertexZ[j1]) * l3;
               if (j2 < -i4 && j4 > 0 || j2 > i4 && j4 < 0) {
                  flag = true;
                  break;
               }
            }

            if (!flag) {
               return true;
            } else {
               int[] ai2;
               int[] ai3;
               if (k == 2) {
                  ai2 = new int[4];
                  ai3 = new int[4];
                  int i5 = ai[0];
                  int k1 = ai[1];
                  ai2[0] = model.vertexViewX[i5] - 20;
                  ai2[1] = model.vertexViewX[k1] - 20;
                  ai2[2] = model.vertexViewX[k1] + 20;
                  ai2[3] = model.vertexViewX[i5] + 20;
                  ai3[0] = ai3[3] = model.vertexViewY[i5];
                  ai3[1] = ai3[2] = model.vertexViewY[k1];
               } else {
                  ai2 = new int[k];
                  ai3 = new int[k];

                  for (int j5 = 0; j5 < k; j5++) {
                     int i6 = ai[j5];
                     ai2[j5] = model.vertexViewX[i6];
                     ai3[j5] = model.vertexViewY[i6];
                  }
               }

               int[] ai4;
               int[] ai5;
               if (l == 2) {
                  ai4 = new int[4];
                  ai5 = new int[4];
                  int k5 = ai1[0];
                  int l1 = ai1[1];
                  ai4[0] = model_1.vertexViewX[k5] - 20;
                  ai4[1] = model_1.vertexViewX[l1] - 20;
                  ai4[2] = model_1.vertexViewX[l1] + 20;
                  ai4[3] = model_1.vertexViewX[k5] + 20;
                  ai5[0] = ai5[3] = model_1.vertexViewY[k5];
                  ai5[1] = ai5[2] = model_1.vertexViewY[l1];
               } else {
                  ai4 = new int[l];
                  ai5 = new int[l];

                  for (int l5 = 0; l5 < l; l5++) {
                     int j6 = ai1[l5];
                     ai4[l5] = model_1.vertexViewX[j6];
                     ai5[l5] = model_1.vertexViewY[j6];
                  }
               }

               return !this.polygonsIntersect(ai2, ai3, ai4, ai5);
            }
         }
      }
   }

   /* Plane-side test used by the overlap pass: true if drawing
      `cameraModel` before `cameraModel_1` is consistent, i.e. neither has
      vertices poking through the camera-facing side of the other's plane. */
   private boolean faceOrderHeuristic(CameraModel cameraModel, CameraModel cameraModel_1) {
      Model model = cameraModel.model;
      Model model_1 = cameraModel_1.model;
      int i = cameraModel.faceIndex;
      int j = cameraModel_1.faceIndex;
      int[] ai = model.faceVertices[i];
      int[] ai1 = model_1.faceVertices[j];
      int k = model.faceNumVertices[i];
      int l = model_1.faceNumVertices[j];
      int i2 = model_1.projectVertexX[ai1[0]];
      int j2 = model_1.projectVertexY[ai1[0]];
      int k2 = model_1.projectVertexZ[ai1[0]];
      int l2 = cameraModel_1.normalX;
      int i3 = cameraModel_1.normalY;
      int j3 = cameraModel_1.normalZ;
      int k3 = model_1.faceCameraNormalMagnitude[j];
      int l3 = cameraModel_1.visibility;
      boolean flag = false;

      for (int i4 = 0; i4 < k; i4++) {
         int i1 = ai[i4];
         int k1 = (i2 - model.projectVertexX[i1]) * l2 + (j2 - model.projectVertexY[i1]) * i3 + (k2 - model.projectVertexZ[i1]) * j3;
         if (k1 < -k3 && l3 < 0 || k1 > k3 && l3 > 0) {
            flag = true;
            break;
         }
      }

      if (!flag) {
         return true;
      } else {
         i2 = model.projectVertexX[ai[0]];
         j2 = model.projectVertexY[ai[0]];
         k2 = model.projectVertexZ[ai[0]];
         l2 = cameraModel.normalX;
         i3 = cameraModel.normalY;
         j3 = cameraModel.normalZ;
         k3 = model.faceCameraNormalMagnitude[i];
         l3 = cameraModel.visibility;
         flag = false;

         for (int j4 = 0; j4 < l; j4++) {
            int j1 = ai1[j4];
            int l1 = (i2 - model_1.projectVertexX[j1]) * l2 + (j2 - model_1.projectVertexY[j1]) * i3 + (k2 - model_1.projectVertexZ[j1]) * j3;
            if (l1 < -k3 && l3 > 0 || l1 > k3 && l3 < 0) {
               flag = true;
               break;
            }
         }

         return !flag;
      }
   }

   public void allocateTextures(int i, int j, int k) {
      this.textureCount = i;
      this.texturePixelIndices = new byte[i][];
      this.texturePalettes = new int[i][];
      this.textureDimension = new int[i];
      this.textureLastUsed = new long[i];
      this.textureBackTransparent = new boolean[i];
      this.texturePixels = new int[i][];
      textureUseCounter = 0L;
      this.texturePixelPool64 = new int[j][];
      this.texturePixelPool128 = new int[k][];
   }

   public void defineTexture(int i, byte[] abyte0, int[] ai, int j) {
      this.texturePixelIndices[i] = abyte0;
      this.texturePalettes[i] = ai;
      this.textureDimension[i] = j;
      this.textureLastUsed[i] = 0L;
      this.textureBackTransparent[i] = false;
      this.texturePixels[i] = null;
      this.prepareTexture(i);
   }

   /* Makes sure texture i has rendered pixels, evicting the least-recently
      used texture of the same size when its pool (texturePixelPool64/128) is
      exhausted. textureLastUsed/textureUseCounter implement the LRU clock. */
   public void prepareTexture(int i) {
      if (i >= 0) {
         this.textureLastUsed[i] = textureUseCounter++;
         if (this.texturePixels[i] == null) {
            if (this.textureDimension[i] == 0) {
               for (int j = 0; j < this.texturePixelPool64.length; j++) {
                  if (this.texturePixelPool64[j] == null) {
                     this.texturePixelPool64[j] = new int[16384];
                     this.texturePixels[i] = this.texturePixelPool64[j];
                     this.setTexturePixels(i);
                     return;
                  }
               }

               long l = 1073741824L;
               int i1 = 0;

               for (int k1 = 0; k1 < this.textureCount; k1++) {
                  if (k1 != i && this.textureDimension[k1] == 0 && this.texturePixels[k1] != null && this.textureLastUsed[k1] < l) {
                     l = this.textureLastUsed[k1];
                     i1 = k1;
                  }
               }

               this.texturePixels[i] = this.texturePixels[i1];
               this.texturePixels[i1] = null;
               this.setTexturePixels(i);
            } else {
               for (int k = 0; k < this.texturePixelPool128.length; k++) {
                  if (this.texturePixelPool128[k] == null) {
                     this.texturePixelPool128[k] = new int[65536];
                     this.texturePixels[i] = this.texturePixelPool128[k];
                     this.setTexturePixels(i);
                     return;
                  }
               }

               long l1 = 1073741824L;
               int j1 = 0;

               for (int i2 = 0; i2 < this.textureCount; i2++) {
                  if (i2 != i && this.textureDimension[i2] == 1 && this.texturePixels[i2] != null && this.textureLastUsed[i2] < l1) {
                     l1 = this.textureLastUsed[i2];
                     j1 = i2;
                  }
               }

               this.texturePixels[i] = this.texturePixels[j1];
               this.texturePixels[j1] = null;
               this.setTexturePixels(i);
            }
         }
      }
   }

   /* Renders a texture's palette+indices into RGB, then appends three
      darkened copies (7/8, 3/4, 5/8 brightness) so the scanline loops can
      light by indexing. Colour 0 is reserved for "transparent", so black
      becomes 1; the magic magenta 16253183 becomes transparent and flags
      textureBackTransparent. */
   private void setTexturePixels(int i) {
      int c = this.textureDimension[i] == 0 ? 64 : 128;
      int[] ai = this.texturePixels[i];
      int j = 0;

      for (int k = 0; k < c; k++) {
         for (int l = 0; l < c; l++) {
            int index = this.texturePixelIndices[i][l + k * c] & 255;
            int j1 = this.texturePalettes[i][index];
            j1 &= 16316671;
            if (j1 == 0) {
               j1 = 1;
            } else if (j1 == 16253183) {
               j1 = 0;
               this.textureBackTransparent[i] = true;
            }

            ai[j++] = j1;
         }
      }

      for (int i1 = 0; i1 < j; i1++) {
         int k1 = ai[i1];
         ai[j + i1] = k1 - (k1 >>> 3) & 16316671;
         ai[j * 2 + i1] = k1 - (k1 >>> 2) & 16316671;
         ai[j * 3 + i1] = k1 - (k1 >>> 2) - (k1 >>> 3) & 16316671;
      }
   }

   /* Rotates a texture's rows down by one and rebuilds the darkened
      copies; mudclient calls this on texture 17 to animate water. */
   public void scrollTexture(int i) {
      if (this.texturePixels[i] != null) {
         int[] ai = this.texturePixels[i];

         for (int j = 0; j < 64; j++) {
            int k = j + 4032;
            int l = ai[k];

            for (int j1 = 0; j1 < 63; j1++) {
               ai[k] = ai[k - 64];
               k -= 64;
            }

            this.texturePixels[i][k] = l;
         }

         char c = 4096;

         for (int i1 = 0; i1 < c; i1++) {
            int k1 = ai[i1];
            ai[c + i1] = k1 - (k1 >>> 3) & 16316671;
            ai[c * 2 + i1] = k1 - (k1 >>> 2) & 16316671;
            ai[c * 3 + i1] = k1 - (k1 >>> 2) - (k1 >>> 3) & 16316671;
         }
      }
   }

   /* Representative RGB for a face fill: texture fills sample texel 0,
      negative fills decode the packed 5-bit-per-channel flat colour. */
   public int getFillColor(int i) {
      if (i == 12345678) {
         return 0;
      } else {
         this.prepareTexture(i);
         if (i >= 0) {
            return this.texturePixels[i][0];
         } else if (i < 0) {
            i = -(i + 1);
            int j = i >> 10 & 31;
            int k = i >> 5 & 31;
            int l = i & 31;
            return (j << 19) + (k << 11) + (l << 3);
         } else {
            return 0;
         }
      }
   }

   public void setLight(int i, int j, int k) {
      if (i == 0 && j == 0 && k == 0) {
         i = 32;
      }

      for (int l = 0; l < this.modelCount; l++) {
         this.modelArray[l].setLight(i, j, k);
      }
   }

   public void setLight(int i, int j, int k, int l, int i1) {
      if (k == 0 && l == 0 && i1 == 0) {
         k = 32;
      }

      for (int j1 = 0; j1 < this.modelCount; j1++) {
         this.modelArray[j1].setLight(i, j, k, l, i1);
      }
   }

   /* Packs an RGB colour (0-255 each) into the negative "flat fill"
      encoding: -1 - (r/8 << 10 | g/8 << 5 | b/8). */
   public static int rgbToFill(int i, int j, int k) {
      return -1 - i / 8 * 1024 - j / 8 * 32 - k / 8;
   }

   public int interpolateX(int i, int j, int k, int l, int i1) {
      return l == j ? i : i + (k - i) * (i1 - j) / (l - j);
   }

   public boolean edgeSpansCross(int i, int j, int k, int l, boolean flag) {
      if ((!flag || i > k) && i >= k) {
         if (i < l) {
            return true;
         } else if (j < k) {
            return true;
         } else {
            return j < l ? true : flag;
         }
      } else if (i > l) {
         return true;
      } else if (j > k) {
         return true;
      } else {
         return j > l ? true : !flag;
      }
   }

   public boolean edgeSpanCrosses(int i, int j, int k, boolean flag) {
      if ((!flag || i > k) && i >= k) {
         return j < k ? true : flag;
      } else {
         return j > k ? true : !flag;
      }
   }

   /* Exact 2D polygon-polygon intersection test on the projected outlines,
      used as the last resort by facesAreSeparated. It advances both
      polygons' left/right edge chains down the Y overlap in lockstep and
      reports whether the X spans ever cross (interpolateX / edgeSpansCross /
      edgeSpanCrosses are its helpers). */
   public boolean polygonsIntersect(int[] ai, int[] ai1, int[] ai2, int[] ai3) {
      int i = ai.length;
      int j = ai2.length;
      byte byte0 = 0;
      int i20;
      int k20 = i20 = ai1[0];
      int k = 0;
      int j20;
      int l20 = j20 = ai3[0];
      int i1 = 0;

      for (int i21 = 1; i21 < i; i21++) {
         if (ai1[i21] < i20) {
            i20 = ai1[i21];
            k = i21;
         } else if (ai1[i21] > k20) {
            k20 = ai1[i21];
         }
      }

      for (int j21 = 1; j21 < j; j21++) {
         if (ai3[j21] < j20) {
            j20 = ai3[j21];
            i1 = j21;
         } else if (ai3[j21] > l20) {
            l20 = ai3[j21];
         }
      }

      if (j20 >= k20) {
         return false;
      } else if (i20 >= l20) {
         return false;
      } else {
         int j1;
         boolean flag;
         int l;
         if (ai1[k] < ai3[i1]) {
            l = k;

            while (ai1[l] < ai3[i1]) {
               l = (l + 1) % i;
            }

            while (ai1[k] < ai3[i1]) {
               k = (k - 1 + i) % i;
            }

            int k1 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
            int k6 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[i1]);
            int l10 = ai2[i1];
            flag = k1 < l10 | k6 < l10;
            if (this.edgeSpanCrosses(k1, k6, l10, flag)) {
               return true;
            }

            j1 = (i1 + 1) % j;
            i1 = (i1 - 1 + j) % j;
            if (k == l) {
               byte0 = 1;
            }
         } else {
            j1 = i1;

            while (ai3[j1] < ai1[k]) {
               j1 = (j1 + 1) % j;
            }

            while (ai3[i1] < ai1[k]) {
               i1 = (i1 - 1 + j) % j;
            }

            int l1 = ai[k];
            int i11 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
            int l15 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai1[k]);
            flag = l1 < i11 | l1 < l15;
            if (this.edgeSpanCrosses(i11, l15, l1, !flag)) {
               return true;
            }

            l = (k + 1) % i;
            k = (k - 1 + i) % i;
            if (i1 == j1) {
               byte0 = 2;
            }
         }

         while (byte0 == 0) {
            if (ai1[k] < ai1[l]) {
               if (ai1[k] < ai3[i1]) {
                  if (ai1[k] < ai3[j1]) {
                     int i2 = ai[k];
                     int l6 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai1[k]);
                     int j11 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
                     int i16 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai1[k]);
                     if (this.edgeSpansCross(i2, l6, j11, i16, flag)) {
                        return true;
                     }

                     k = (k - 1 + i) % i;
                     if (k == l) {
                        byte0 = 1;
                     }
                  } else {
                     int j2 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                     int i7 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[j1]);
                     int k11 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                     int j16 = ai2[j1];
                     if (this.edgeSpansCross(j2, i7, k11, j16, flag)) {
                        return true;
                     }

                     j1 = (j1 + 1) % j;
                     if (i1 == j1) {
                        byte0 = 2;
                     }
                  }
               } else if (ai3[i1] < ai3[j1]) {
                  int k2 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
                  int j7 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[i1]);
                  int l11 = ai2[i1];
                  int k16 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai3[i1]);
                  if (this.edgeSpansCross(k2, j7, l11, k16, flag)) {
                     return true;
                  }

                  i1 = (i1 - 1 + j) % j;
                  if (i1 == j1) {
                     byte0 = 2;
                  }
               } else {
                  int l2 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                  int k7 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[j1]);
                  int i12 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                  int l16 = ai2[j1];
                  if (this.edgeSpansCross(l2, k7, i12, l16, flag)) {
                     return true;
                  }

                  j1 = (j1 + 1) % j;
                  if (i1 == j1) {
                     byte0 = 2;
                  }
               }
            } else if (ai1[l] < ai3[i1]) {
               if (ai1[l] < ai3[j1]) {
                  int i3 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai1[l]);
                  int l7 = ai[l];
                  int j12 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[l]);
                  int i17 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai1[l]);
                  if (this.edgeSpansCross(i3, l7, j12, i17, flag)) {
                     return true;
                  }

                  l = (l + 1) % i;
                  if (k == l) {
                     byte0 = 1;
                  }
               } else {
                  int j3 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                  int i8 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[j1]);
                  int k12 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                  int j17 = ai2[j1];
                  if (this.edgeSpansCross(j3, i8, k12, j17, flag)) {
                     return true;
                  }

                  j1 = (j1 + 1) % j;
                  if (i1 == j1) {
                     byte0 = 2;
                  }
               }
            } else if (ai3[i1] < ai3[j1]) {
               int k3 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
               int j8 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[i1]);
               int l12 = ai2[i1];
               int k17 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai3[i1]);
               if (this.edgeSpansCross(k3, j8, l12, k17, flag)) {
                  return true;
               }

               i1 = (i1 - 1 + j) % j;
               if (i1 == j1) {
                  byte0 = 2;
               }
            } else {
               int l3 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
               int k8 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[j1]);
               int i13 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
               int l17 = ai2[j1];
               if (this.edgeSpansCross(l3, k8, i13, l17, flag)) {
                  return true;
               }

               j1 = (j1 + 1) % j;
               if (i1 == j1) {
                  byte0 = 2;
               }
            }
         }

         while (byte0 == 1) {
            if (ai1[k] < ai3[i1]) {
               if (ai1[k] < ai3[j1]) {
                  int i4 = ai[k];
                  int j13 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
                  int i18 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai1[k]);
                  return this.edgeSpanCrosses(j13, i18, i4, !flag);
               }

               int j4 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
               int l8 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[j1]);
               int k13 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
               int j18 = ai2[j1];
               if (this.edgeSpansCross(j4, l8, k13, j18, flag)) {
                  return true;
               }

               j1 = (j1 + 1) % j;
               if (i1 == j1) {
                  byte0 = 0;
               }
            } else if (ai3[i1] < ai3[j1]) {
               int k4 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
               int i9 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[i1]);
               int l13 = ai2[i1];
               int k18 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai3[i1]);
               if (this.edgeSpansCross(k4, i9, l13, k18, flag)) {
                  return true;
               }

               i1 = (i1 - 1 + j) % j;
               if (i1 == j1) {
                  byte0 = 0;
               }
            } else {
               int l4 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
               int j9 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[j1]);
               int i14 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
               int l18 = ai2[j1];
               if (this.edgeSpansCross(l4, j9, i14, l18, flag)) {
                  return true;
               }

               j1 = (j1 + 1) % j;
               if (i1 == j1) {
                  byte0 = 0;
               }
            }
         }

         while (byte0 == 2) {
            if (ai3[i1] < ai1[k]) {
               if (ai3[i1] < ai1[l]) {
                  int i5 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
                  int k9 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[i1]);
                  int j14 = ai2[i1];
                  return this.edgeSpanCrosses(i5, k9, j14, flag);
               }

               int j5 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai1[l]);
               int l9 = ai[l];
               int k14 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[l]);
               int i19 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai1[l]);
               if (this.edgeSpansCross(j5, l9, k14, i19, flag)) {
                  return true;
               }

               l = (l + 1) % i;
               if (k == l) {
                  byte0 = 0;
               }
            } else if (ai1[k] < ai1[l]) {
               int k5 = ai[k];
               int i10 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai1[k]);
               int l14 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
               int j19 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai1[k]);
               if (this.edgeSpansCross(k5, i10, l14, j19, flag)) {
                  return true;
               }

               k = (k - 1 + i) % i;
               if (k == l) {
                  byte0 = 0;
               }
            } else {
               int l5 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai1[l]);
               int j10 = ai[l];
               int i15 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[l]);
               int k19 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai1[l]);
               if (this.edgeSpansCross(l5, j10, i15, k19, flag)) {
                  return true;
               }

               l = (l + 1) % i;
               if (k == l) {
                  byte0 = 0;
               }
            }
         }

         if (ai1[k] < ai3[i1]) {
            int i6 = ai[k];
            int j15 = this.interpolateX(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
            int l19 = this.interpolateX(ai2[(j1 - 1 + j) % j], ai3[(j1 - 1 + j) % j], ai2[j1], ai3[j1], ai1[k]);
            return this.edgeSpanCrosses(j15, l19, i6, !flag);
         } else {
            int j6 = this.interpolateX(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
            int k10 = this.interpolateX(ai[(l - 1 + i) % i], ai1[(l - 1 + i) % i], ai[l], ai1[l], ai3[i1]);
            int k15 = ai2[i1];
            return this.edgeSpanCrosses(j6, k10, k15, flag);
         }
      }
   }
}
