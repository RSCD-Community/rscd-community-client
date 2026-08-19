package org.rscdaemon.client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A 3D mesh: vertices shared between variable-sized faces. Vertex positions
 * live in three parallel array sets: vertexX/Y/Z (model space, as loaded),
 * vertexTransformedX/Y/Z (after the pending translate/rotate/scale/shear is
 * applied), and projectVertexX/Y/Z + vertexViewX/Y (camera space and screen,
 * written by project() and read directly by Camera's rasteriser).
 *
 * All trig and lighting arithmetic is fixed point: sin/cos tables are scaled
 * by 32768 (>> 15 after multiplying), normals by 65536.
 */
public class Model {
   public int vertexCount;
   public int[] projectVertexX;
   public int[] projectVertexY;
   public int[] projectVertexZ;
   public int[] vertexViewX;
   public int[] vertexViewY;
   public int[] vertexIntensity;
   public byte[] vertexAmbience;
   public int faceCount;
   public int[] faceNumVertices;
   public int[][] faceVertices;
   public int[] faceFillFront;
   public int[] faceFillBack;
   public int[] faceCameraNormalMagnitude;
   public int[] faceCameraNormalScale;
   public int[] faceIntensity;
   public int[] faceNormalX;
   public int[] faceNormalY;
   private int[] faceNormalZ;
   // Depth bias added to each face's sorted depth in Camera. Never set
   // nonzero in this client, but copy() preserves it.
   public int depth;
   // 0 = transformed arrays are current; 1 = re-apply the pending transform
   // before the next use; 2 = just copy base vertices across and skip bounds,
   // leaving the bounding box wide open so project() never frustum-culls
   // (Camera uses this for the terrain model it transforms itself).
   public int transformState = 1;
   public boolean visible = true;
   public int minX;
   public int maxX;
   public int minY;
   public int maxY;
   public int minZ;
   public int maxZ;
   public boolean aBoolean254 = true; // never read anywhere; meaning unknown
   public boolean translucent = false;
   public boolean isGiantCrystal = false;
   // Mouse-pick identity: mudclient sets this to the scene object's index
   // (doors get index + 10000) and decodes it back after picking. faceTag is
   // the per-face equivalent used for ground tiles and walls (200000+ range);
   // isLocalPlayer marks faces picking should ignore.
   public int key = -1;
   public int[] faceTag;
   public byte[] isLocalPlayer;
   private boolean autocommit = false;
   public boolean isolated = false;
   public boolean unlit = false;
   public boolean unpickable = false;
   public boolean projected = false;
   private static int[] sinCosTable256 = new int[512];
   private static int[] sinCosTable1024 = new int[2048];
   private static byte[] base64Alphabet = new byte[64];
   private static int[] base64Decode = new int[256];
   // 12345678 is the "magic" marker value used throughout the renderer: as a
   // face fill it means invisible, and as a faceIntensity it means "shade
   // this face per-vertex (gouraud) instead of flat".
   private int magic = 12345678;
   public int maxVertices;
   public int[] vertexX;
   public int[] vertexY;
   public int[] vertexZ;
   public int[] vertexTransformedX;
   public int[] vertexTransformedY;
   public int[] vertexTransformedZ;
   private int maxFaces;
   // Written but never read: records which source model/faces each face came
   // from across merges. Presumably a leftover from Jagex's map editor.
   private int[][] faceOrigins;
   private int[] faceMinX;
   private int[] faceMaxX;
   private int[] faceMinY;
   private int[] faceMaxY;
   private int[] faceMinZ;
   private int[] faceMaxZ;
   private int translateX;
   private int translateY;
   private int translateZ;
   private int rotationX;
   private int rotationY;
   private int rotationZ;
   private int scaleX;
   private int scaleY;
   private int scaleZ;
   private int shearXbyY;
   private int shearZbyY;
   private int shearXbyZ;
   private int shearYbyZ;
   private int shearZbyX;
   private int shearYbyX;
   private int transformKind;
   private int diameter = 12345678;
   private int lightDirectionX = 180;
   private int lightDirectionY = 155;
   private int lightDirectionZ = 95;
   private int lightDirectionMagnitude = 256;
   protected int lightDiffuse = 512;
   protected int lightAmbience = 32;
   private int dataIndex;

   public Model(int i, int j) {
      this.initialise(i, j);
      this.faceOrigins = new int[j][1];
      int k = 0;

      while (k < j) {
         this.faceOrigins[k][0] = k++;
      }
   }

   public Model(int i, int j, boolean flag, boolean flag1, boolean flag2, boolean flag3, boolean flag4) {
      this.autocommit = flag;
      this.isolated = flag1;
      this.unlit = flag2;
      this.unpickable = flag3;
      this.projected = flag4;
      this.initialise(i, j);
   }

   private void initialise(int i, int j) {
      this.vertexX = new int[i];
      this.vertexY = new int[i];
      this.vertexZ = new int[i];
      this.vertexIntensity = new int[i];
      this.vertexAmbience = new byte[i];
      this.faceNumVertices = new int[j];
      this.faceVertices = new int[j][];
      this.faceFillFront = new int[j];
      this.faceFillBack = new int[j];
      this.faceIntensity = new int[j];
      this.faceCameraNormalScale = new int[j];
      this.faceCameraNormalMagnitude = new int[j];
      if (!this.projected) {
         this.projectVertexX = new int[i];
         this.projectVertexY = new int[i];
         this.projectVertexZ = new int[i];
         this.vertexViewX = new int[i];
         this.vertexViewY = new int[i];
      }

      if (!this.unpickable) {
         this.isLocalPlayer = new byte[j];
         this.faceTag = new int[j];
      }

      if (this.autocommit) {
         this.vertexTransformedX = this.vertexX;
         this.vertexTransformedY = this.vertexY;
         this.vertexTransformedZ = this.vertexZ;
      } else {
         this.vertexTransformedX = new int[i];
         this.vertexTransformedY = new int[i];
         this.vertexTransformedZ = new int[i];
      }

      if (!this.unlit || !this.isolated) {
         this.faceNormalX = new int[j];
         this.faceNormalY = new int[j];
         this.faceNormalZ = new int[j];
      }

      if (!this.isolated) {
         this.faceMinX = new int[j];
         this.faceMaxX = new int[j];
         this.faceMinY = new int[j];
         this.faceMaxY = new int[j];
         this.faceMinZ = new int[j];
         this.faceMaxZ = new int[j];
      }

      this.faceCount = 0;
      this.vertexCount = 0;
      this.maxVertices = i;
      this.maxFaces = j;
      this.translateX = this.translateY = this.translateZ = 0;
      this.rotationX = this.rotationY = this.rotationZ = 0;
      this.scaleX = this.scaleY = this.scaleZ = 256;
      this.shearXbyY = this.shearZbyY = this.shearXbyZ = this.shearYbyZ = this.shearZbyX = this.shearYbyX = 256;
      this.transformKind = 0;
   }

   public void projectionPrepare() {
      this.projectVertexX = new int[this.vertexCount];
      this.projectVertexY = new int[this.vertexCount];
      this.projectVertexZ = new int[this.vertexCount];
      this.vertexViewX = new int[this.vertexCount];
      this.vertexViewY = new int[this.vertexCount];
   }

   public void clear() {
      this.faceCount = 0;
      this.vertexCount = 0;
   }

   public void reduce(int i, int j) {
      this.faceCount -= i;
      if (this.faceCount < 0) {
         this.faceCount = 0;
      }

      this.vertexCount -= j;
      if (this.vertexCount < 0) {
         this.vertexCount = 0;
      }
   }

   public Model(byte[] abyte0, int i, boolean flag) {
      int j = DataOperations.getUnsigned2Bytes(abyte0, i);
      i += 2;
      int k = DataOperations.getUnsigned2Bytes(abyte0, i);
      i += 2;
      this.initialise(j, k);
      this.faceOrigins = new int[k][1];

      for (int l = 0; l < j; l++) {
         this.vertexX[l] = DataOperations.getSigned2Bytes(abyte0, i);
         i += 2;
      }

      for (int i1 = 0; i1 < j; i1++) {
         this.vertexY[i1] = DataOperations.getSigned2Bytes(abyte0, i);
         i += 2;
      }

      for (int j1 = 0; j1 < j; j1++) {
         this.vertexZ[j1] = DataOperations.getSigned2Bytes(abyte0, i);
         i += 2;
      }

      this.vertexCount = j;

      for (int k1 = 0; k1 < k; k1++) {
         this.faceNumVertices[k1] = abyte0[i++] & 255;
      }

      for (int l1 = 0; l1 < k; l1++) {
         this.faceFillFront[l1] = DataOperations.getSigned2Bytes(abyte0, i);
         i += 2;
         if (this.faceFillFront[l1] == 32767) {
            this.faceFillFront[l1] = this.magic;
         }
      }

      for (int i2 = 0; i2 < k; i2++) {
         this.faceFillBack[i2] = DataOperations.getSigned2Bytes(abyte0, i);
         i += 2;
         if (this.faceFillBack[i2] == 32767) {
            this.faceFillBack[i2] = this.magic;
         }
      }

      for (int j2 = 0; j2 < k; j2++) {
         int k2 = abyte0[i++] & 255;
         if (k2 == 0) {
            this.faceIntensity[j2] = 0;
         } else {
            this.faceIntensity[j2] = this.magic;
         }
      }

      for (int l2 = 0; l2 < k; l2++) {
         this.faceVertices[l2] = new int[this.faceNumVertices[l2]];

         for (int i3 = 0; i3 < this.faceNumVertices[l2]; i3++) {
            if (j < 256) {
               this.faceVertices[l2][i3] = abyte0[i++] & 255;
            } else {
               this.faceVertices[l2][i3] = DataOperations.getUnsigned2Bytes(abyte0, i);
               i += 2;
            }
         }
      }

      this.faceCount = k;
      this.transformState = 1;
   }

   public Model(String path) {
      byte[] abyte0 = null;

      try {
         InputStream inputstream = DataOperations.streamFromPath(path);
         DataInputStream datainputstream = new DataInputStream(inputstream);
         abyte0 = new byte[3];
         this.dataIndex = 0;
         int i = 0;

         while (i < 3) {
            i += datainputstream.read(abyte0, i, 3 - i);
         }

         i = this.readBase64(abyte0);
         abyte0 = new byte[i];
         this.dataIndex = 0;
         int j = 0;

         while (j < i) {
            j += datainputstream.read(abyte0, j, i - j);
         }

         datainputstream.close();
      } catch (IOException var14) {
         this.vertexCount = 0;
         this.faceCount = 0;
         return;
      }

      int l = this.readBase64(abyte0);
      int i1 = this.readBase64(abyte0);
      this.initialise(l, i1);
      this.faceOrigins = new int[i1][];

      for (int j3 = 0; j3 < l; j3++) {
         int j1 = this.readBase64(abyte0);
         int k1 = this.readBase64(abyte0);
         int l1 = this.readBase64(abyte0);
         this.getOrAddVertex(j1, k1, l1);
      }

      for (int k3 = 0; k3 < i1; k3++) {
         int i2 = this.readBase64(abyte0);
         int j2 = this.readBase64(abyte0);
         int k2 = this.readBase64(abyte0);
         int l2 = this.readBase64(abyte0);
         this.lightDiffuse = this.readBase64(abyte0);
         this.lightAmbience = this.readBase64(abyte0);
         int i3 = this.readBase64(abyte0);
         int[] ai = new int[i2];

         for (int l3 = 0; l3 < i2; l3++) {
            ai[l3] = this.readBase64(abyte0);
         }

         int[] ai1 = new int[l2];

         for (int i4 = 0; i4 < l2; i4++) {
            ai1[i4] = this.readBase64(abyte0);
         }

         int j4 = this.addFace(i2, ai, j2, k2);
         this.faceOrigins[k3] = ai1;
         if (i3 == 0) {
            this.faceIntensity[j4] = 0;
         } else {
            this.faceIntensity[j4] = this.magic;
         }
      }

      this.transformState = 1;
   }

   public Model(Model[] model, int i, boolean flag, boolean flag1, boolean flag2, boolean flag3) {
      this.autocommit = flag;
      this.isolated = flag1;
      this.unlit = flag2;
      this.unpickable = flag3;
      this.merge(model, i, false);
   }

   public Model(Model[] models, int i) {
      this.merge(models, i, true);
   }

   // Combines i models into this one, deduplicating shared vertices via
   // getOrAddVertex. Each source model's pending transform is baked in first
   // (commit), so the merged copy is position-final. EngineHandle builds the
   // terrain and wall meshes this way.
   public void merge(Model[] models, int i, boolean flag) {
      int j = 0;
      int k = 0;

      for (int l = 0; l < i; l++) {
         j += models[l].faceCount;
         k += models[l].vertexCount;
      }

      this.initialise(k, j);
      if (flag) {
         this.faceOrigins = new int[j][];
      }

      for (int i1 = 0; i1 < i; i1++) {
         Model model = models[i1];
         model.commit();
         this.lightAmbience = model.lightAmbience;
         this.lightDiffuse = model.lightDiffuse;
         this.lightDirectionX = model.lightDirectionX;
         this.lightDirectionY = model.lightDirectionY;
         this.lightDirectionZ = model.lightDirectionZ;
         this.lightDirectionMagnitude = model.lightDirectionMagnitude;

         for (int j1 = 0; j1 < model.faceCount; j1++) {
            int[] ai = new int[model.faceNumVertices[j1]];
            int[] ai1 = model.faceVertices[j1];

            for (int k1 = 0; k1 < model.faceNumVertices[j1]; k1++) {
               ai[k1] = this.getOrAddVertex(model.vertexX[ai1[k1]], model.vertexY[ai1[k1]], model.vertexZ[ai1[k1]]);
            }

            int l1 = this.addFace(model.faceNumVertices[j1], ai, model.faceFillFront[j1], model.faceFillBack[j1]);
            this.faceIntensity[l1] = model.faceIntensity[j1];
            this.faceCameraNormalScale[l1] = model.faceCameraNormalScale[j1];
            this.faceCameraNormalMagnitude[l1] = model.faceCameraNormalMagnitude[j1];
            if (flag) {
               if (i > 1) {
                  this.faceOrigins[l1] = new int[model.faceOrigins[j1].length + 1];
                  this.faceOrigins[l1][0] = i1;

                  for (int i2 = 0; i2 < model.faceOrigins[j1].length; i2++) {
                     this.faceOrigins[l1][i2 + 1] = model.faceOrigins[j1][i2];
                  }
               } else {
                  this.faceOrigins[l1] = new int[model.faceOrigins[j1].length];

                  for (int j2 = 0; j2 < model.faceOrigins[j1].length; j2++) {
                     this.faceOrigins[l1][j2] = model.faceOrigins[j1][j2];
                  }
               }
            }
         }
      }

      this.transformState = 1;
   }

   public int getOrAddVertex(int i, int j, int k) {
      for (int l = 0; l < this.vertexCount; l++) {
         if (this.vertexX[l] == i && this.vertexY[l] == j && this.vertexZ[l] == k) {
            return l;
         }
      }

      if (this.vertexCount >= this.maxVertices) {
         return -1;
      } else {
         this.vertexX[this.vertexCount] = i;
         this.vertexY[this.vertexCount] = j;
         this.vertexZ[this.vertexCount] = k;
         return this.vertexCount++;
      }
   }

   public int addVertex(int i, int j, int k) {
      if (this.vertexCount >= this.maxVertices) {
         return -1;
      } else {
         this.vertexX[this.vertexCount] = i;
         this.vertexY[this.vertexCount] = j;
         this.vertexZ[this.vertexCount] = k;
         return this.vertexCount++;
      }
   }

   public int addFace(int i, int[] ai, int j, int k) {
      if (this.faceCount >= this.maxFaces) {
         return -1;
      } else {
         this.faceNumVertices[this.faceCount] = i;
         this.faceVertices[this.faceCount] = ai;
         this.faceFillFront[this.faceCount] = j;
         this.faceFillBack[this.faceCount] = k;
         this.transformState = 1;
         return this.faceCount++;
      }
   }

   // Splits this model into a grid of j1 pieces (i1 per row), assigning each
   // face to a cell by the average x/z of its vertices. EngineHandle uses this
   // to cut the merged landscape into chunks the camera can cull separately.
   public Model[] split(int i, int j, int k, int l, int i1, int j1, int k1, boolean flag) {
      this.commit();
      int[] ai = new int[j1];
      int[] ai1 = new int[j1];

      for (int l1 = 0; l1 < j1; l1++) {
         ai[l1] = 0;
         ai1[l1] = 0;
      }

      for (int i2 = 0; i2 < this.faceCount; i2++) {
         int j2 = 0;
         int k2 = 0;
         int i3 = this.faceNumVertices[i2];
         int[] ai2 = this.faceVertices[i2];

         for (int i4 = 0; i4 < i3; i4++) {
            j2 += this.vertexX[ai2[i4]];
            k2 += this.vertexZ[ai2[i4]];
         }

         int k4 = j2 / (i3 * k) + k2 / (i3 * l) * i1;
         ai[k4] += i3;
         ai1[k4]++;
      }

      Model[] models = new Model[j1];

      for (int l2 = 0; l2 < j1; l2++) {
         if (ai[l2] > k1) {
            ai[l2] = k1;
         }

         models[l2] = new Model(ai[l2], ai1[l2], true, true, true, flag, true);
         models[l2].lightDiffuse = this.lightDiffuse;
         models[l2].lightAmbience = this.lightAmbience;
      }

      for (int j3 = 0; j3 < this.faceCount; j3++) {
         int k3 = 0;
         int j4 = 0;
         int l4 = this.faceNumVertices[j3];
         int[] ai3 = this.faceVertices[j3];

         for (int i5 = 0; i5 < l4; i5++) {
            k3 += this.vertexX[ai3[i5]];
            j4 += this.vertexZ[ai3[i5]];
         }

         int j5 = k3 / (l4 * k) + j4 / (l4 * l) * i1;
         this.copyFaceTo(models[j5], ai3, l4, j3);
      }

      for (int l3 = 0; l3 < j1; l3++) {
         models[l3].projectionPrepare();
      }

      return models;
   }

   public void copyFaceTo(Model model, int[] ai, int i, int j) {
      int[] ai1 = new int[i];

      for (int k = 0; k < i; k++) {
         int l = ai1[k] = model.getOrAddVertex(this.vertexX[ai[k]], this.vertexY[ai[k]], this.vertexZ[ai[k]]);
         model.vertexIntensity[l] = this.vertexIntensity[ai[k]];
         model.vertexAmbience[l] = this.vertexAmbience[ai[k]];
      }

      int i1 = model.addFace(i, ai1, this.faceFillFront[j], this.faceFillBack[j]);
      if (!model.unpickable && !this.unpickable) {
         model.faceTag[i1] = this.faceTag[j];
      }

      model.faceIntensity[i1] = this.faceIntensity[j];
      model.faceCameraNormalScale[i1] = this.faceCameraNormalScale[j];
      model.faceCameraNormalMagnitude[i1] = this.faceCameraNormalMagnitude[j];
   }

   public void setLight(boolean flag, int i, int j, int k, int l, int i1) {
      this.lightAmbience = 256 - i * 4;
      this.lightDiffuse = (64 - j) * 16 + 128;
      if (!this.unlit) {
         for (int j1 = 0; j1 < this.faceCount; j1++) {
            if (flag) {
               this.faceIntensity[j1] = this.magic;
            } else {
               this.faceIntensity[j1] = 0;
            }
         }

         this.lightDirectionX = k;
         this.lightDirectionY = l;
         this.lightDirectionZ = i1;
         this.lightDirectionMagnitude = (int)Math.sqrt((double)(k * k + l * l + i1 * i1));
         this.light();
      }
   }

   public void setLight(int i, int j, int k, int l, int i1) {
      this.lightAmbience = 256 - i * 4;
      this.lightDiffuse = (64 - j) * 16 + 128;
      if (!this.unlit) {
         this.lightDirectionX = k;
         this.lightDirectionY = l;
         this.lightDirectionZ = i1;
         this.lightDirectionMagnitude = (int)Math.sqrt((double)(k * k + l * l + i1 * i1));
         this.light();
      }
   }

   public void setLight(int i, int j, int k) {
      if (!this.unlit) {
         this.lightDirectionX = i;
         this.lightDirectionY = j;
         this.lightDirectionZ = k;
         this.lightDirectionMagnitude = (int)Math.sqrt((double)(i * i + j * j + k * k));
         this.light();
      }
   }

   public void setVertexAmbience(int i, int j) {
      this.vertexAmbience[i] = (byte)j;
   }

   public void rotateBy(int i, int j, int k) {
      this.rotationX = this.rotationX + i & 0xFF;
      this.rotationY = this.rotationY + j & 0xFF;
      this.rotationZ = this.rotationZ + k & 0xFF;
      this.determineTransformKind();
      this.transformState = 1;
   }

   public void setRotation(int i, int j, int k) {
      this.rotationX = i & 0xFF;
      this.rotationY = j & 0xFF;
      this.rotationZ = k & 0xFF;
      this.determineTransformKind();
      this.transformState = 1;
   }

   public void translateBy(int i, int j, int k) {
      this.translateX += i;
      this.translateY += j;
      this.translateZ += k;
      this.determineTransformKind();
      this.transformState = 1;
   }

   public void setTranslation(int i, int j, int k) {
      this.translateX = i;
      this.translateY = j;
      this.translateZ = k;
      this.determineTransformKind();
      this.transformState = 1;
   }

   // Picks the cheapest transform level that covers everything pending:
   // 0 none, 1 translate only, 2 +rotate, 3 +scale, 4 +shear. applyTransform
   // runs every step at or below the chosen level.
   private void determineTransformKind() {
      if (this.shearXbyY != 256 || this.shearZbyY != 256 || this.shearXbyZ != 256 || this.shearYbyZ != 256 || this.shearZbyX != 256 || this.shearYbyX != 256) {
         this.transformKind = 4;
      } else if (this.scaleX != 256 || this.scaleY != 256 || this.scaleZ != 256) {
         this.transformKind = 3;
      } else if (this.rotationX != 0 || this.rotationY != 0 || this.rotationZ != 0) {
         this.transformKind = 2;
      } else if (this.translateX == 0 && this.translateY == 0 && this.translateZ == 0) {
         this.transformKind = 0;
      } else {
         this.transformKind = 1;
      }
   }

   private void applyTranslate(int i, int j, int k) {
      for (int l = 0; l < this.vertexCount; l++) {
         this.vertexTransformedX[l] = this.vertexTransformedX[l] + i;
         this.vertexTransformedY[l] = this.vertexTransformedY[l] + j;
         this.vertexTransformedZ[l] = this.vertexTransformedZ[l] + k;
      }
   }

   // Rotates about Z, then X, then Y. Angles are 0-255 (a full circle is 256);
   // the table holds sin in [0,255] and cos in [256,511], scaled by 32768,
   // hence the >> 15 after each multiply.
   private void applyRotation(int i, int j, int k) {
      for (int i3 = 0; i3 < this.vertexCount; i3++) {
         if (k != 0) {
            int l = sinCosTable256[k];
            int k1 = sinCosTable256[k + 256];
            int j2 = this.vertexTransformedY[i3] * l + this.vertexTransformedX[i3] * k1 >> 15;
            this.vertexTransformedY[i3] = this.vertexTransformedY[i3] * k1 - this.vertexTransformedX[i3] * l >> 15;
            this.vertexTransformedX[i3] = j2;
         }

         if (i != 0) {
            int i1 = sinCosTable256[i];
            int l1 = sinCosTable256[i + 256];
            int k2 = this.vertexTransformedY[i3] * l1 - this.vertexTransformedZ[i3] * i1 >> 15;
            this.vertexTransformedZ[i3] = this.vertexTransformedY[i3] * i1 + this.vertexTransformedZ[i3] * l1 >> 15;
            this.vertexTransformedY[i3] = k2;
         }

         if (j != 0) {
            int j1 = sinCosTable256[j];
            int i2 = sinCosTable256[j + 256];
            int l2 = this.vertexTransformedZ[i3] * j1 + this.vertexTransformedX[i3] * i2 >> 15;
            this.vertexTransformedZ[i3] = this.vertexTransformedZ[i3] * i2 - this.vertexTransformedX[i3] * j1 >> 15;
            this.vertexTransformedX[i3] = l2;
         }
      }
   }

   private void applyShear(int i, int j, int k, int l, int i1, int j1) {
      for (int k1 = 0; k1 < this.vertexCount; k1++) {
         if (i != 0) {
            this.vertexTransformedX[k1] = this.vertexTransformedX[k1] + (this.vertexTransformedY[k1] * i >> 8);
         }

         if (j != 0) {
            this.vertexTransformedZ[k1] = this.vertexTransformedZ[k1] + (this.vertexTransformedY[k1] * j >> 8);
         }

         if (k != 0) {
            this.vertexTransformedX[k1] = this.vertexTransformedX[k1] + (this.vertexTransformedZ[k1] * k >> 8);
         }

         if (l != 0) {
            this.vertexTransformedY[k1] = this.vertexTransformedY[k1] + (this.vertexTransformedZ[k1] * l >> 8);
         }

         if (i1 != 0) {
            this.vertexTransformedZ[k1] = this.vertexTransformedZ[k1] + (this.vertexTransformedX[k1] * i1 >> 8);
         }

         if (j1 != 0) {
            this.vertexTransformedY[k1] = this.vertexTransformedY[k1] + (this.vertexTransformedX[k1] * j1 >> 8);
         }
      }
   }

   private void applyScale(int i, int j, int k) {
      for (int l = 0; l < this.vertexCount; l++) {
         this.vertexTransformedX[l] = this.vertexTransformedX[l] * i >> 8;
         this.vertexTransformedY[l] = this.vertexTransformedY[l] * j >> 8;
         this.vertexTransformedZ[l] = this.vertexTransformedZ[l] * k >> 8;
      }
   }

   private void computeBounds() {
      this.minX = this.minY = this.minZ = 999999;
      this.diameter = this.maxX = this.maxY = this.maxZ = -999999;

      for (int i = 0; i < this.faceCount; i++) {
         int[] ai = this.faceVertices[i];
         int k = ai[0];
         int i1 = this.faceNumVertices[i];
         int j1;
         int k1 = j1 = this.vertexTransformedX[k];
         int l1;
         int i2 = l1 = this.vertexTransformedY[k];
         int j2;
         int k2 = j2 = this.vertexTransformedZ[k];

         for (int j = 0; j < i1; j++) {
            int l = ai[j];
            if (this.vertexTransformedX[l] < j1) {
               j1 = this.vertexTransformedX[l];
            } else if (this.vertexTransformedX[l] > k1) {
               k1 = this.vertexTransformedX[l];
            }

            if (this.vertexTransformedY[l] < l1) {
               l1 = this.vertexTransformedY[l];
            } else if (this.vertexTransformedY[l] > i2) {
               i2 = this.vertexTransformedY[l];
            }

            if (this.vertexTransformedZ[l] < j2) {
               j2 = this.vertexTransformedZ[l];
            } else if (this.vertexTransformedZ[l] > k2) {
               k2 = this.vertexTransformedZ[l];
            }
         }

         if (!this.isolated) {
            this.faceMinX[i] = j1;
            this.faceMaxX[i] = k1;
            this.faceMinY[i] = l1;
            this.faceMaxY[i] = i2;
            this.faceMinZ[i] = j2;
            this.faceMaxZ[i] = k2;
         }

         if (k1 - j1 > this.diameter) {
            this.diameter = k1 - j1;
         }

         if (i2 - l1 > this.diameter) {
            this.diameter = i2 - l1;
         }

         if (k2 - j2 > this.diameter) {
            this.diameter = k2 - j2;
         }

         if (j1 < this.minX) {
            this.minX = j1;
         }

         if (k1 > this.maxX) {
            this.maxX = k1;
         }

         if (l1 < this.minY) {
            this.minY = l1;
         }

         if (i2 > this.maxY) {
            this.maxY = i2;
         }

         if (j2 < this.minZ) {
            this.minZ = j2;
         }

         if (k2 > this.maxZ) {
            this.maxZ = k2;
         }
      }
   }

   // Re-shades from the stored face normals: flat-shaded faces get the dot
   // product of their normal with the light direction; gouraud faces (the
   // magic marker) instead average their faces' normals into each shared
   // vertex and shade per vertex.
   public void light() {
      if (!this.unlit) {
         int i = this.lightDiffuse * this.lightDirectionMagnitude >> 8;

         for (int j = 0; j < this.faceCount; j++) {
            if (this.faceIntensity[j] != this.magic) {
               this.faceIntensity[j] = (this.faceNormalX[j] * this.lightDirectionX + this.faceNormalY[j] * this.lightDirectionY + this.faceNormalZ[j] * this.lightDirectionZ)
                  / i;
            }
         }

         int[] ai = new int[this.vertexCount];
         int[] ai1 = new int[this.vertexCount];
         int[] ai2 = new int[this.vertexCount];
         int[] ai3 = new int[this.vertexCount];

         for (int k = 0; k < this.vertexCount; k++) {
            ai[k] = 0;
            ai1[k] = 0;
            ai2[k] = 0;
            ai3[k] = 0;
         }

         for (int l = 0; l < this.faceCount; l++) {
            if (this.faceIntensity[l] == this.magic) {
               for (int i1 = 0; i1 < this.faceNumVertices[l]; i1++) {
                  int k1 = this.faceVertices[l][i1];
                  ai[k1] += this.faceNormalX[l];
                  ai1[k1] += this.faceNormalY[l];
                  ai2[k1] += this.faceNormalZ[l];
                  ai3[k1]++;
               }
            }
         }

         for (int j1 = 0; j1 < this.vertexCount; j1++) {
            if (ai3[j1] > 0) {
               this.vertexIntensity[j1] = (ai[j1] * this.lightDirectionX + ai1[j1] * this.lightDirectionY + ai2[j1] * this.lightDirectionZ) / (i * ai3[j1]);
            }
         }
      }
   }

   // Face normal = cross product of the first two edges, shifted down until
   // it fits 14 bits, then normalised to 65536 fixed point. Resetting
   // faceCameraNormalScale to -1 makes Camera recompute its own view-space
   // normal for the face. Ends by relighting.
   public void computeNormals() {
      if (!this.unlit || !this.isolated) {
         for (int i = 0; i < this.faceCount; i++) {
            int[] ai = this.faceVertices[i];
            int j = this.vertexTransformedX[ai[0]];
            int k = this.vertexTransformedY[ai[0]];
            int l = this.vertexTransformedZ[ai[0]];
            int i1 = this.vertexTransformedX[ai[1]] - j;
            int j1 = this.vertexTransformedY[ai[1]] - k;
            int k1 = this.vertexTransformedZ[ai[1]] - l;
            int l1 = this.vertexTransformedX[ai[2]] - j;
            int i2 = this.vertexTransformedY[ai[2]] - k;
            int j2 = this.vertexTransformedZ[ai[2]] - l;
            int k2 = j1 * j2 - i2 * k1;
            int l2 = k1 * l1 - j2 * i1;

            int i3;
            for (i3 = i1 * i2 - l1 * j1; k2 > 8192 || l2 > 8192 || i3 > 8192 || k2 < -8192 || l2 < -8192 || i3 < -8192; i3 >>= 1) {
               k2 >>= 1;
               l2 >>= 1;
            }

            int j3 = (int)(256.0 * Math.sqrt((double)(k2 * k2 + l2 * l2 + i3 * i3)));
            if (j3 <= 0) {
               j3 = 1;
            }

            this.faceNormalX[i] = k2 * 65536 / j3;
            this.faceNormalY[i] = l2 * 65536 / j3;
            this.faceNormalZ[i] = i3 * 65535 / j3;
            this.faceCameraNormalScale[i] = -1;
         }

         this.light();
      }
   }

   public void applyTransform() {
      if (this.transformState == 2) {
         this.transformState = 0;

         for (int i = 0; i < this.vertexCount; i++) {
            this.vertexTransformedX[i] = this.vertexX[i];
            this.vertexTransformedY[i] = this.vertexY[i];
            this.vertexTransformedZ[i] = this.vertexZ[i];
         }

         this.minX = this.minY = this.minZ = -9999999;
         this.diameter = this.maxX = this.maxY = this.maxZ = 9999999;
      } else {
         if (this.transformState == 1) {
            this.transformState = 0;

            for (int j = 0; j < this.vertexCount; j++) {
               this.vertexTransformedX[j] = this.vertexX[j];
               this.vertexTransformedY[j] = this.vertexY[j];
               this.vertexTransformedZ[j] = this.vertexZ[j];
            }

            if (this.transformKind >= 2) {
               this.applyRotation(this.rotationX, this.rotationY, this.rotationZ);
            }

            if (this.transformKind >= 3) {
               this.applyScale(this.scaleX, this.scaleY, this.scaleZ);
            }

            if (this.transformKind >= 4) {
               this.applyShear(this.shearXbyY, this.shearZbyY, this.shearXbyZ, this.shearYbyZ, this.shearZbyX, this.shearYbyX);
            }

            if (this.transformKind >= 1) {
               this.applyTranslate(this.translateX, this.translateY, this.translateZ);
            }

            this.computeBounds();
            this.computeNormals();
         }
      }
   }

   // Projects every vertex for the rasteriser: camera position (i,j,k),
   // camera angles pitch/yaw/roll (l,i1,j1) in 1024ths of a circle, view
   // distance as a shift (k1) and near clip (l1). Skipped entirely — visible
   // set false — when the bounding box misses Camera's frustum planes.
   public void project(int i, int j, int k, int l, int i1, int j1, int k1, int l1) {
      this.applyTransform();
      if (this.minZ <= Camera.frustumMaxZ
         && this.maxZ >= Camera.frustumMinZ
         && this.minX <= Camera.frustumMaxX
         && this.maxX >= Camera.frustumMinX
         && this.minY <= Camera.frustumMaxY
         && this.maxY >= Camera.frustumMinY) {
         this.visible = true;
         int l2 = 0;
         int i3 = 0;
         int j3 = 0;
         int k3 = 0;
         int l3 = 0;
         int i4 = 0;
         if (j1 != 0) {
            l2 = sinCosTable1024[j1];
            i3 = sinCosTable1024[j1 + 1024];
         }

         if (i1 != 0) {
            l3 = sinCosTable1024[i1];
            i4 = sinCosTable1024[i1 + 1024];
         }

         if (l != 0) {
            j3 = sinCosTable1024[l];
            k3 = sinCosTable1024[l + 1024];
         }

         for (int j4 = 0; j4 < this.vertexCount; j4++) {
            int k4 = this.vertexTransformedX[j4] - i;
            int l4 = this.vertexTransformedY[j4] - j;
            int i5 = this.vertexTransformedZ[j4] - k;
            if (j1 != 0) {
               int i2 = l4 * l2 + k4 * i3 >> 15;
               l4 = l4 * i3 - k4 * l2 >> 15;
               k4 = i2;
            }

            if (i1 != 0) {
               int j2 = i5 * l3 + k4 * i4 >> 15;
               i5 = i5 * i4 - k4 * l3 >> 15;
               k4 = j2;
            }

            if (l != 0) {
               int k2 = l4 * k3 - i5 * j3 >> 15;
               i5 = l4 * j3 + i5 * k3 >> 15;
               l4 = k2;
            }

            if (i5 >= l1) {
               this.vertexViewX[j4] = (k4 << k1) / i5;
            } else {
               this.vertexViewX[j4] = k4 << k1;
            }

            if (i5 >= l1) {
               this.vertexViewY[j4] = (l4 << k1) / i5;
            } else {
               this.vertexViewY[j4] = l4 << k1;
            }

            this.projectVertexX[j4] = k4;
            this.projectVertexY[j4] = l4;
            this.projectVertexZ[j4] = i5;
         }
      } else {
         this.visible = false;
      }
   }

   public void commit() {
      this.applyTransform();

      for (int i = 0; i < this.vertexCount; i++) {
         this.vertexX[i] = this.vertexTransformedX[i];
         this.vertexY[i] = this.vertexTransformedY[i];
         this.vertexZ[i] = this.vertexTransformedZ[i];
      }

      this.translateX = this.translateY = this.translateZ = 0;
      this.rotationX = this.rotationY = this.rotationZ = 0;
      this.scaleX = this.scaleY = this.scaleZ = 256;
      this.shearXbyY = this.shearZbyY = this.shearXbyZ = this.shearYbyZ = this.shearZbyX = this.shearYbyX = 256;
      this.transformKind = 0;
   }

   public Model copy() {
      Model[] models = new Model[]{this};
      Model model = new Model(models, 1);
      model.depth = this.depth;
      model.isGiantCrystal = this.isGiantCrystal;
      return model;
   }

   public Model copy(boolean flag, boolean flag1, boolean flag2, boolean flag3) {
      Model[] models = new Model[]{this};
      Model model = new Model(models, 1, flag, flag1, flag2, flag3);
      model.depth = this.depth;
      return model;
   }

   public void copyPosition(Model model) {
      this.rotationX = model.rotationX;
      this.rotationY = model.rotationY;
      this.rotationZ = model.rotationZ;
      this.translateX = model.translateX;
      this.translateY = model.translateY;
      this.translateZ = model.translateZ;
      this.determineTransformKind();
      this.transformState = 1;
   }

   // The .ob3 text format stores each number as three base64 digits biased by
   // 131072; 123456 decodes to the magic marker. Newlines are skipped.
   public int readBase64(byte[] abyte0) {
      while (abyte0[this.dataIndex] == 10 || abyte0[this.dataIndex] == 13) {
         this.dataIndex++;
      }

      int i = base64Decode[abyte0[this.dataIndex++] & 255];
      int j = base64Decode[abyte0[this.dataIndex++] & 255];
      int k = base64Decode[abyte0[this.dataIndex++] & 255];
      int l = i * 4096 + j * 64 + k - 131072;
      if (l == 123456) {
         l = this.magic;
      }

      return l;
   }

   static {
      for (int i = 0; i < 256; i++) {
         sinCosTable256[i] = (int)(Math.sin((double)i * 0.02454369) * 32768.0);
         sinCosTable256[i + 256] = (int)(Math.cos((double)i * 0.02454369) * 32768.0);
      }

      for (int j = 0; j < 1024; j++) {
         sinCosTable1024[j] = (int)(Math.sin((double)j * 0.00613592315) * 32768.0);
         sinCosTable1024[j + 1024] = (int)(Math.cos((double)j * 0.00613592315) * 32768.0);
      }

      for (int k = 0; k < 10; k++) {
         base64Alphabet[k] = (byte)(48 + k);
      }

      for (int l = 0; l < 26; l++) {
         base64Alphabet[l + 10] = (byte)(65 + l);
      }

      for (int i1 = 0; i1 < 26; i1++) {
         base64Alphabet[i1 + 36] = (byte)(97 + i1);
      }

      base64Alphabet[62] = -93;
      base64Alphabet[63] = 36;
      int j1 = 0;

      while (j1 < 10) {
         base64Decode[48 + j1] = j1++;
      }

      for (int k1 = 0; k1 < 26; k1++) {
         base64Decode[65 + k1] = k1 + 10;
      }

      for (int l1 = 0; l1 < 26; l1++) {
         base64Decode[97 + l1] = l1 + 36;
      }

      base64Decode[163] = 62;
      base64Decode[36] = 63;
   }
}
