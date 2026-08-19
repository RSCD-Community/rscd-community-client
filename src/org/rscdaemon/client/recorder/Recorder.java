package org.rscdaemon.client.recorder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/*
 * Writes an MJPG AVI: every frame is a JPEG, and the AVI is the container that
 * holds them in order.
 *
 * This used to be three classes over the Java Media Framework, writing a
 * QuickTime .mov. JMF is a 2 MB Sun binary under a licence that does not
 * permit redistribution, so it could not ship with an open-source client.
 * Everything here is JDK-only -- ImageIO has had a JPEG encoder since 1.4 --
 * and the AVI container is about two hundred bytes of headers.
 *
 * It also never worked: nothing in the client ever put a frame into the queue,
 * so the encoder consumed an empty list and wrote a header with no video after
 * it. The other half of the fix is mudclient.captureFrame().
 */
public class Recorder implements Runnable {
   /* Pushed into the queue to say "no more frames". A one-pixel image rather
      than null, because a BlockingQueue will not carry null. */
   public static final BufferedImage END = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

   private static final int AVIF_HASINDEX = 16;
   private static final int AVIIF_KEYFRAME = 16;

   private final int width;
   private final int height;
   private final int frameRate;
   private final BlockingQueue<BufferedImage> frames;
   private final File output;
   /* JPEG quality. 0.8 is a fair trade for a 512x346 game view: roughly 25 KB
      a frame, with the chat pane still readable. */
   private final float quality = 0.8F;

   private volatile int written;

   public Recorder(int width, int height, float frameRate, BlockingQueue<BufferedImage> frames, String output) {
      this.width = width;
      this.height = height;
      this.frameRate = Math.max(1, Math.round(frameRate));
      this.frames = frames;
      this.output = new File(output);
   }

   /** How many frames have reached the file so far. */
   public int framesWritten() {
      return this.written;
   }

   @Override
   public void run() {
      RandomAccessFile out = null;

      try {
         out = new RandomAccessFile(this.output, "rw");
         out.setLength(0L);

         /* Two sizes are not known until the last frame arrives, so they go
            down as zero and are patched at the end. */
         out.writeBytes("RIFF");
         long riffSizeAt = out.getFilePointer();
         writeInt(out, 0);
         out.writeBytes("AVI ");
         this.writeHeaderList(out);

         out.writeBytes("LIST");
         long moviSizeAt = out.getFilePointer();
         writeInt(out, 0);
         long moviStart = out.getFilePointer();
         out.writeBytes("movi");

         List<long[]> index = new ArrayList<long[]>();
         ImageWriter jpeg = ImageIO.getImageWritersByFormatName("jpeg").next();
         ImageWriteParam param = jpeg.getDefaultWriteParam();
         param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         param.setCompressionQuality(this.quality);

         try {
            while (true) {
               BufferedImage frame = this.frames.take();
               if (frame == END) {
                  break;
               }

               byte[] encoded = encode(jpeg, param, frame);
               long chunkAt = out.getFilePointer();
               out.writeBytes("00dc");
               writeInt(out, encoded.length);
               out.write(encoded);
               // Every chunk is padded to an even length.
               if ((encoded.length & 1) != 0) {
                  out.write(0);
               }

               index.add(new long[]{chunkAt - moviStart, (long)encoded.length});
               this.written++;
            }
         } finally {
            jpeg.dispose();
         }

         long moviEnd = out.getFilePointer();
         this.writeIndex(out, index);
         long fileEnd = out.getFilePointer();

         out.seek(moviSizeAt);
         writeInt(out, (int)(moviEnd - moviStart));
         out.seek(riffSizeAt);
         writeInt(out, (int)(fileEnd - riffSizeAt - 4L));
         this.patchFrameCounts(out, index.size());
      } catch (Exception var16) {
         System.out.println("Recorder failed: " + var16);
      } finally {
         if (out != null) {
            try {
               out.close();
            } catch (IOException var15) {
            }
         }
      }
   }

   private static byte[] encode(ImageWriter jpeg, ImageWriteParam param, BufferedImage frame) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(65536);
      MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes);

      try {
         jpeg.setOutput(stream);
         jpeg.write(null, new IIOImage(frame, null, null), param);
         stream.flush();
      } finally {
         stream.close();
      }

      return bytes.toByteArray();
   }

   /*
    * hdrl: the main header, then one stream header describing the video track.
    * Both are fixed size, which is what lets patchFrameCounts() seek straight
    * to the two frame counts afterwards.
    */
   private void writeHeaderList(RandomAccessFile out) throws IOException {
      out.writeBytes("LIST");
      writeInt(out, 4 + 64 + 12 + 64 + 48);
      out.writeBytes("hdrl");

      out.writeBytes("avih");
      writeInt(out, 56);
      writeInt(out, 1000000 / this.frameRate); // microseconds per frame
      writeInt(out, 0); // max bytes per second
      writeInt(out, 0); // padding granularity
      writeInt(out, AVIF_HASINDEX);
      writeInt(out, 0); // total frames, patched
      writeInt(out, 0); // initial frames
      writeInt(out, 1); // streams
      writeInt(out, 0); // suggested buffer size
      writeInt(out, this.width);
      writeInt(out, this.height);
      writeInt(out, 0);
      writeInt(out, 0);
      writeInt(out, 0);
      writeInt(out, 0);

      out.writeBytes("LIST");
      writeInt(out, 4 + 64 + 48);
      out.writeBytes("strl");

      out.writeBytes("strh");
      writeInt(out, 56);
      out.writeBytes("vids");
      out.writeBytes("MJPG");
      writeInt(out, 0); // flags
      writeShort(out, 0); // priority
      writeShort(out, 0); // language
      writeInt(out, 0); // initial frames
      writeInt(out, 1); // scale
      writeInt(out, this.frameRate); // rate; rate/scale is the frame rate
      writeInt(out, 0); // start
      writeInt(out, 0); // length in frames, patched
      writeInt(out, 0); // suggested buffer size
      writeInt(out, -1); // quality: -1 means default
      writeInt(out, 0); // sample size
      writeShort(out, 0); // rcFrame left
      writeShort(out, 0); // top
      writeShort(out, this.width); // right
      writeShort(out, this.height); // bottom

      out.writeBytes("strf");
      writeInt(out, 40);
      writeInt(out, 40); // biSize
      writeInt(out, this.width);
      writeInt(out, this.height);
      writeShort(out, 1); // biPlanes
      writeShort(out, 24); // biBitCount
      out.writeBytes("MJPG"); // biCompression
      writeInt(out, this.width * this.height * 3); // biSizeImage
      writeInt(out, 0);
      writeInt(out, 0);
      writeInt(out, 0);
      writeInt(out, 0);
   }

   /*
    * idx1: one 16-byte entry per frame. Offsets are relative to the 'movi'
    * FOURCC, which is the convention players expect.
    */
   private void writeIndex(RandomAccessFile out, List<long[]> index) throws IOException {
      out.writeBytes("idx1");
      writeInt(out, index.size() * 16);

      for (int i = 0; i < index.size(); i++) {
         long[] entry = index.get(i);
         out.writeBytes("00dc");
         writeInt(out, AVIIF_KEYFRAME);
         writeInt(out, (int)entry[0]);
         writeInt(out, (int)entry[1]);
      }
   }

   /*
    * dwTotalFrames in the main header and dwLength in the stream header, both
    * at fixed offsets: the file opens with 12 bytes of RIFF/AVI, then 12 of
    * hdrl, then the 8-byte avih chunk header.
    */
   private void patchFrameCounts(RandomAccessFile out, int count) throws IOException {
      long avih = 12L + 12L + 8L;
      out.seek(avih + 16L); // past four DWORDs, to dwTotalFrames
      writeInt(out, count);

      // past avih's body, the strl LIST header, and the strh chunk header
      long strh = avih + 56L + 12L + 8L;
      out.seek(strh + 32L); // past type, handler, flags, priority, language, initial, scale, rate, start
      writeInt(out, count);
   }

   private static void writeInt(RandomAccessFile out, int value) throws IOException {
      out.write(value & 255);
      out.write(value >> 8 & 255);
      out.write(value >> 16 & 255);
      out.write(value >> 24 & 255);
   }

   private static void writeShort(RandomAccessFile out, int value) throws IOException {
      out.write(value & 255);
      out.write(value >> 8 & 255);
   }
}
