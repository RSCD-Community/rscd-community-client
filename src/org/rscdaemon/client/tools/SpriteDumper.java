package org.rscdaemon.client.tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

import org.rscdaemon.client.GameImage;
import org.rscdaemon.client.entityhandling.EntityHandler;
import org.rscdaemon.client.entityhandling.defs.NPCDef;
import org.rscdaemon.client.util.Assets;

/**
 * Renders every NPC's sprite composite to a PNG, for the website Beastiary.
 *
 * Reuses the client's own data loaders and GameImage.spriteClip4, so the
 * output is exactly what the game draws: the same 12-layer composition,
 * direction frames, hair/top/bottom/skin colour overlays and horizontal
 * flips as mudclient.drawNpc, minus the 3D projection.
 *
 * Usage: SpriteDumper <cache_data dir> <output dir> [direction 0-7] [npc id]
 *
 * With no npc id, dumps every NPC as npc<id>.png. With one, dumps that NPC
 * in all 8 directions as npc<id>_d<dir>.png (for picking the display angle).
 */
public class SpriteDumper {

    /* mudclient's npcAnimationArray: layer draw order per direction. */
    private static final int[][] LAYER_ORDER = {
        {11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4},
        {11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4},
        {11, 3, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4},
        {3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
        {3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
        {4, 3, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
        {11, 4, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3},
        {11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4, 3}
    };

    private static final int CANVAS = 512;

    private static final String[] DATA_FILES = {
        "NPCs.xml.data", "ItemDef.xml.data", "Textures.xml.data",
        "Animations.xml.data", "SpellDef.xml.data", "Prayers.xml.data",
        "Tiles.xml.data", "Doors.xml.data", "Elevation.xml.data",
        "Objects.xml.data", "Sprites.xml.data"
    };

    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        File outDir = new File(args[1]);
        int direction = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int onlyId = args.length > 3 ? Integer.parseInt(args[3]) : -1;
        outDir.mkdirs();

        for (String name : DATA_FILES) {
            Assets.put(name, Files.readAllBytes(new File(cacheDir, name).toPath()));
        }
        EntityHandler.load();

        GameImage gi = new GameImage(CANVAS, CANVAS, 20000, null);
        loadEntitySprites(gi);

        if (onlyId >= 0) {
            for (int dir = 0; dir < 8; dir++) {
                dump(gi, onlyId, dir, new File(outDir, "npc" + onlyId + "_d" + dir + ".png"));
            }
            return;
        }
        int written = 0;
        for (int id = 0; id < EntityHandler.npcCount(); id++) {
            if (dump(gi, id, direction, new File(outDir, "npc" + id + ".png"))) {
                written++;
            }
        }
        System.out.println("Wrote " + written + " of " + EntityHandler.npcCount() + " npcs");
    }

    /* mudclient's entity-sprite unpack loop, verbatim. */
    private static void loadEntitySprites(GameImage gi) {
        int animationNumber = 0;
        label:
        for (int i = 0; i < EntityHandler.animationCount(); i++) {
            String s = EntityHandler.getAnimationDef(i).getName();
            for (int j = 0; j < i; j++) {
                if (EntityHandler.getAnimationDef(j).getName().equalsIgnoreCase(s)) {
                    EntityHandler.getAnimationDef(i).number = EntityHandler.getAnimationDef(j).getNumber();
                    continue label;
                }
            }
            loadSprites(gi, animationNumber, 15);
            if (EntityHandler.getAnimationDef(i).hasA()) {
                loadSprites(gi, animationNumber + 15, 3);
            }
            if (EntityHandler.getAnimationDef(i).hasF()) {
                loadSprites(gi, animationNumber + 18, 9);
            }
            EntityHandler.getAnimationDef(i).number = animationNumber;
            animationNumber += 27;
        }
    }

    private static void loadSprites(GameImage gi, int id, int amount) {
        for (int i = id; i < id + amount; i++) {
            gi.loadSprite(i, "entity");
        }
    }

    /**
     * mudclient.drawNpc's standing-pose composition for one direction,
     * drawn at natural size (camera1 x camera2, scale 100).
     */
    private static boolean dump(GameImage gi, int id, int direction, File out) throws Exception {
        NPCDef def = EntityHandler.getNpcDef(id);
        int k = def.getCamera1();
        int l = def.getCamera2();
        if (k <= 0 || l <= 0 || k > CANVAS || l > CANVAS) {
            return false;
        }

        java.util.Arrays.fill(gi.imagePixelArray, 0);
        gi.resetDimensions();

        int x = (CANVAS - k) / 2;
        int y = (CANVAS - l) / 2;

        int l1 = direction & 7;
        boolean flag = false;
        int i2 = l1;
        if (l1 == 5) {
            i2 = 3;
            flag = true;
        } else if (l1 == 6) {
            i2 = 2;
            flag = true;
        } else if (l1 == 7) {
            i2 = 1;
            flag = true;
        }
        int j2 = i2 * 3; // standing: walk frame 0

        boolean drew = false;
        for (int k2 = 0; k2 < 12; k2++) {
            int l2 = LAYER_ORDER[l1][k2];
            int k3 = def.getSprite(l2);
            if (k3 < 0) {
                continue;
            }
            int k4 = j2;
            if (flag && i2 >= 1 && i2 <= 3 && EntityHandler.getAnimationDef(k3).hasF()) {
                k4 = j2 + 15;
            }
            if (i2 == 5 && !EntityHandler.getAnimationDef(k3).hasA()) {
                continue;
            }
            int l4 = k4 + EntityHandler.getAnimationDef(k3).getNumber();
            if (gi.sprites[l4] == null) {
                continue;
            }
            int i5 = k * gi.sprites[l4].getSomething1()
                / gi.sprites[EntityHandler.getAnimationDef(k3).getNumber()].getSomething1();
            int i4 = -(i5 - k) / 2;
            int colour = EntityHandler.getAnimationDef(k3).getCharColour();
            int skinColour = 0;
            if (colour == 1) {
                colour = def.getHairColour();
                skinColour = def.getSkinColour();
            } else if (colour == 2) {
                colour = def.getTopColour();
                skinColour = def.getSkinColour();
            } else if (colour == 3) {
                colour = def.getBottomColour();
                skinColour = def.getSkinColour();
            }
            gi.spriteClip4(x + i4, y, i5, l, l4, colour, skinColour, 0, flag);
            drew = true;
        }
        if (!drew) {
            return false;
        }

        // crop to the drawn pixels; 0 (the cleared canvas) is transparent
        int minX = CANVAS, minY = CANVAS, maxX = -1, maxY = -1;
        for (int py = 0; py < CANVAS; py++) {
            for (int px = 0; px < CANVAS; px++) {
                if (gi.imagePixelArray[py * CANVAS + px] != 0) {
                    if (px < minX) minX = px;
                    if (px > maxX) maxX = px;
                    if (py < minY) minY = py;
                    if (py > maxY) maxY = py;
                }
            }
        }
        if (maxX < 0) {
            return false;
        }

        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int rgb = gi.imagePixelArray[(minY + py) * CANVAS + minX + px];
                if (rgb != 0) {
                    img.setRGB(px, py, 0xFF000000 | rgb);
                }
            }
        }
        ImageIO.write(img, "png", out);
        return true;
    }
}
