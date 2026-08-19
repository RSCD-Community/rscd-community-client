package org.rscdaemon.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * The socket layer under PacketConstruction: reads happen synchronously on
 * the caller's thread, writes go through a 5000-byte ring buffer drained by
 * this class's own writer thread (run()), so the game loop never blocks on
 * a slow connection -- it blocks only if it outruns the network by nearly
 * the whole ring ("buffer overflow"). A write error does not throw into
 * game code; it sets the inherited error/errorText flags, which the client
 * polls and turns into the lost-connection screen.
 */
public class StreamClass extends PacketConstruction implements Runnable {
   private InputStream inputStream;
   private OutputStream outputStream;
   private Socket streamSocket;
   private boolean closingStream = false;
   private byte[] outputBuffer;
   private int dataWritten;
   private int bufferSize;
   private boolean closedStream = true;

   public StreamClass(Socket socket, GameWindow gameWindow) throws IOException {
      this.streamSocket = socket;
      this.inputStream = socket.getInputStream();
      this.outputStream = socket.getOutputStream();
      this.closedStream = false;
      gameWindow.startThread(this);
   }

   @Override
   public void closeStream() {
      super.closeStream();
      this.closingStream = true;

      try {
         if (this.inputStream != null) {
            this.inputStream.close();
         }

         if (this.outputStream != null) {
            this.outputStream.close();
         }

         if (this.streamSocket != null) {
            this.streamSocket.close();
         }
      } catch (IOException var4) {
         System.out.println("Error closing stream");
      }

      this.closedStream = true;
      synchronized (this) {
         this.notify();
      }

      this.outputBuffer = null;
   }

   @Override
   public int readInputStream() throws IOException {
      return this.closingStream ? 0 : this.inputStream.read();
   }

   @Override
   public int inputStreamAvailable() throws IOException {
      return this.closingStream ? 0 : this.inputStream.available();
   }

   @Override
   public void readInputStream(int length, int offset, byte[] abyte0) throws IOException {
      if (!this.closingStream) {
         int k = 0;

         while (k < length) {
            int l;
            if ((l = this.inputStream.read(abyte0, k + offset, length - k)) <= 0) {
               throw new IOException("EOF");
            }

            k += l;
         }
      }
   }

   @Override
   public void writeToOutputBuffer(byte[] abyte0, int i, int j) throws IOException {
      if (!this.closingStream) {
         if (this.outputBuffer == null) {
            this.outputBuffer = new byte[5000];
         }

         synchronized (this) {
            for (int k = 0; k < j; k++) {
               this.outputBuffer[this.bufferSize] = abyte0[k + i];
               this.bufferSize = (this.bufferSize + 1) % 5000;
               if (this.bufferSize == (this.dataWritten + 4900) % 5000) {
                  throw new IOException("buffer overflow");
               }
            }

            this.notify();
         }
      }
   }

   @Override
   public void run() {
      while (!this.closedStream) {
         int i;
         int j;
         synchronized (this) {
            if (this.bufferSize == this.dataWritten) {
               try {
                  this.wait();
               } catch (InterruptedException var8) {
               }
            }

            if (this.closedStream) {
               return;
            }

            j = this.dataWritten;
            if (this.bufferSize >= this.dataWritten) {
               i = this.bufferSize - this.dataWritten;
            } else {
               i = 5000 - this.dataWritten;
            }
         }

         if (i > 0) {
            try {
               this.outputStream.write(this.outputBuffer, j, i);
            } catch (IOException var7) {
               super.error = true;
               super.errorText = "Twriter:" + var7;
            }

            this.dataWritten = (this.dataWritten + i) % 5000;

            try {
               if (this.bufferSize == this.dataWritten) {
                  this.outputStream.flush();
               }
            } catch (IOException var6) {
               super.error = true;
               super.errorText = "Twriter:" + var6;
            }
         }
      }
   }
}
