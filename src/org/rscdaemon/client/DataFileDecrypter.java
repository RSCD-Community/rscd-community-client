package org.rscdaemon.client;

/**
 * Despite the name, no cryptography: this is a bzip2 DECOMPRESSOR. The
 * game's .jag/.mem archives hold bzip2 streams with the four-byte "BZh1"
 * magic stripped off, and unpackData inflates one entry into the caller's
 * buffer. The internals are the standard bzip2 pipeline, now renamed to the
 * reference bzlib terms: decompressStream reads block headers, Huffman
 * tables and the MTF/RLE2 symbol stream and inverts the BWT; unRunLength
 * then undoes the byte-level run-length coding into the output buffer.
 */
public class DataFileDecrypter {
   public static int unpackData(byte[] out, int outSize, byte[] input, int inSize, int inOffset) {
      DataFileVariables dataFileVariables = new DataFileVariables();
      dataFileVariables.inputBuffer = input;
      dataFileVariables.inputOffset = inOffset;
      dataFileVariables.outputBuffer = out;
      dataFileVariables.outputOffset = 0;
      dataFileVariables.inputRemaining = inSize;
      dataFileVariables.outputRemaining = outSize;
      dataFileVariables.bitCount = 0;
      dataFileVariables.bitBuffer = 0;
      dataFileVariables.totalInLo32 = 0;
      dataFileVariables.totalInHi32 = 0;
      dataFileVariables.totalOutLo32 = 0;
      dataFileVariables.totalOutHi32 = 0;
      dataFileVariables.currentBlock = 0;
      decompressStream(dataFileVariables);
      return outSize - dataFileVariables.outputRemaining;
   }

   // Final output stage (bzip2's unRLE_obuf_to_output): walks the inverted
   // BWT by chasing tPos links through bwtBuffer, and undoes the byte-level
   // run-length coding (4 identical bytes are followed by a repeat count)
   // while streaming into the output buffer. State is saved back into
   // dataFileVariables so a block can span multiple calls if the output
   // buffer fills up.
   private static void unRunLength(DataFileVariables dataFileVariables) {
      byte byte4 = dataFileVariables.stateOutCh;
      int i = dataFileVariables.stateOutLen;
      int j = dataFileVariables.blockBytesUsed;
      int k = dataFileVariables.lastDecodedByte;
      int[] ai = DataFileVariables.bwtBuffer;
      int l = dataFileVariables.tPos;
      byte[] abyte0 = dataFileVariables.outputBuffer;
      int i1 = dataFileVariables.outputOffset;
      int j1 = dataFileVariables.outputRemaining;
      int k1 = j1;
      int l1 = dataFileVariables.blockLength + 1;

      label68:
      while (true) {
         if (i > 0) {
            while (true) {
               if (j1 == 0) {
                  break label68;
               }

               if (i == 1) {
                  if (j1 == 0) {
                     i = 1;
                     break label68;
                  }

                  abyte0[i1] = byte4;
                  i1++;
                  j1--;
                  break;
               }

               abyte0[i1] = byte4;
               i--;
               i1++;
               j1--;
            }
         }

         boolean flag = true;

         while (flag) {
            flag = false;
            if (j == l1) {
               i = 0;
               break label68;
            }

            byte4 = (byte)k;
            l = ai[l];
            byte byte0 = (byte)(l & 0xFF);
            l >>= 8;
            j++;
            if (byte0 != k) {
               k = byte0;
               if (j1 == 0) {
                  i = 1;
                  break label68;
               }

               abyte0[i1] = byte4;
               i1++;
               j1--;
               flag = true;
            } else if (j == l1) {
               if (j1 == 0) {
                  i = 1;
                  break label68;
               }

               abyte0[i1] = byte4;
               i1++;
               j1--;
               flag = true;
            }
         }

         i = 2;
         l = ai[l];
         byte byte1 = (byte)(l & 0xFF);
         l >>= 8;
         if (++j != l1) {
            if (byte1 != k) {
               k = byte1;
            } else {
               i = 3;
               l = ai[l];
               byte byte2 = (byte)(l & 0xFF);
               l >>= 8;
               if (++j != l1) {
                  if (byte2 != k) {
                     k = byte2;
                  } else {
                     l = ai[l];
                     byte byte3 = (byte)(l & 0xFF);
                     l >>= 8;
                     j++;
                     i = (byte3 & 255) + 4;
                     l = ai[l];
                     k = (byte)(l & 0xFF);
                     l >>= 8;
                     j++;
                  }
               }
            }
         }
      }

      int i2 = dataFileVariables.totalOutLo32;
      dataFileVariables.totalOutLo32 += k1 - j1;
      if (dataFileVariables.totalOutLo32 < i2) {
         dataFileVariables.totalOutHi32++;
      }

      dataFileVariables.stateOutCh = byte4;
      dataFileVariables.stateOutLen = i;
      dataFileVariables.blockBytesUsed = j;
      dataFileVariables.lastDecodedByte = k;
      DataFileVariables.bwtBuffer = ai;
      dataFileVariables.tPos = l;
      dataFileVariables.outputBuffer = abyte0;
      dataFileVariables.outputOffset = i1;
      dataFileVariables.outputRemaining = j1;
   }

   private static void decompressStream(DataFileVariables dataFileVariables) {
      int k8 = 0;
      int[] ai = null;
      int[] ai1 = null;
      int[] ai2 = null;
      dataFileVariables.blockSize100k = 1;
      if (DataFileVariables.bwtBuffer == null) {
         DataFileVariables.bwtBuffer = new int[dataFileVariables.blockSize100k * 100000];
      }

      boolean flag19 = true;

      // one pass per compressed block. 0x17 is the first byte of the stream
      // footer magic (0x177245385090); a block header starts 0x314159265359.
      while (flag19) {
         byte byte0 = readByte(dataFileVariables);
         if (byte0 == 23) {
            return;
         }

         // the rest of the block magic, then the 32-bit block CRC -- read
         // only to advance the bit stream, never verified.
         byte0 = readByte(dataFileVariables);
         byte0 = readByte(dataFileVariables);
         byte0 = readByte(dataFileVariables);
         byte0 = readByte(dataFileVariables);
         byte0 = readByte(dataFileVariables);
         dataFileVariables.currentBlock++;
         byte0 = readByte(dataFileVariables);
         byte0 = readByte(dataFileVariables);
         byte0 = readByte(dataFileVariables);
         byte0 = readByte(dataFileVariables);
         byte0 = readBit(dataFileVariables);
         if (byte0 != 0) {
            dataFileVariables.blockRandomised = true;
         } else {
            dataFileVariables.blockRandomised = false;
         }

         if (dataFileVariables.blockRandomised) {
            System.out.println("PANIC! RANDOMISED BLOCK!");
         }

         // 24-bit origPtr: the row of the sorted block where the BWT
         // inverse must start.
         dataFileVariables.origPtr = 0;
         byte0 = readByte(dataFileVariables);
         dataFileVariables.origPtr = dataFileVariables.origPtr << 8 | byte0 & 255;
         byte0 = readByte(dataFileVariables);
         dataFileVariables.origPtr = dataFileVariables.origPtr << 8 | byte0 & 255;
         byte0 = readByte(dataFileVariables);
         dataFileVariables.origPtr = dataFileVariables.origPtr << 8 | byte0 & 255;

         // symbol bitmap: 16 range bits, then a 16-bit map per used range,
         // saying which of the 256 byte values occur in this block.
         for (int j = 0; j < 16; j++) {
            byte byte1 = readBit(dataFileVariables);
            if (byte1 == 1) {
               dataFileVariables.symbolRangeInUse[j] = true;
            } else {
               dataFileVariables.symbolRangeInUse[j] = false;
            }
         }

         for (int k = 0; k < 256; k++) {
            dataFileVariables.symbolInUse[k] = false;
         }

         for (int l = 0; l < 16; l++) {
            if (dataFileVariables.symbolRangeInUse[l]) {
               for (int i3 = 0; i3 < 16; i3++) {
                  byte byte2 = readBit(dataFileVariables);
                  if (byte2 == 1) {
                     dataFileVariables.symbolInUse[l * 16 + i3] = true;
                  }
               }
            }
         }

         buildSymbolMap(dataFileVariables);
         // i4 = alphabet size (used symbols plus RUNA/RUNB and EOB),
         // j4 = huffman group count, k4 = selector count.
         int i4 = dataFileVariables.symbolsUsed + 2;
         int j4 = readBits(3, dataFileVariables);
         int k4 = readBits(15, dataFileVariables);

         // selectors: unary-coded, then MTF-decoded against the small group
         // list below to give which huffman table each 50-symbol run uses.
         for (int i1 = 0; i1 < k4; i1++) {
            int j3 = 0;

            while (true) {
               byte byte3 = readBit(dataFileVariables);
               if (byte3 == 0) {
                  dataFileVariables.huffmanSelectorsMtf[i1] = (byte)j3;
                  break;
               }

               j3++;
            }
         }

         byte[] abyte0 = new byte[6];
         byte byte16 = (byte)0;

         while (byte16 < j4) {
            abyte0[byte16] = byte16++;
         }

         for (int j1 = 0; j1 < k4; j1++) {
            byte byte17 = dataFileVariables.huffmanSelectorsMtf[j1];

            byte byte15;
            for (byte15 = abyte0[byte17]; byte17 > 0; byte17--) {
               abyte0[byte17] = abyte0[byte17 - 1];
            }

            abyte0[0] = byte15;
            dataFileVariables.huffmanSelectors[j1] = byte15;
         }

         // per-group code lengths: a 5-bit starting length, then delta-coded
         // (a 1 bit means adjust up/down, a 0 bit moves to the next symbol).
         for (int k3 = 0; k3 < j4; k3++) {
            int l6 = readBits(5, dataFileVariables);

            for (int k1 = 0; k1 < i4; k1++) {
               while (true) {
                  byte byte4 = readBit(dataFileVariables);
                  if (byte4 == 0) {
                     dataFileVariables.huffmanCodeLengths[k3][k1] = (byte)l6;
                     break;
                  }

                  byte4 = readBit(dataFileVariables);
                  if (byte4 == 0) {
                     l6++;
                  } else {
                     l6--;
                  }
               }
            }
         }

         // find each group's min/max code length and build its canonical
         // huffman limit/base/perm decode tables.
         for (int l3 = 0; l3 < j4; l3++) {
            byte byte8 = 32;
            int i = 0;

            for (int l1 = 0; l1 < i4; l1++) {
               if (dataFileVariables.huffmanCodeLengths[l3][l1] > i) {
                  i = dataFileVariables.huffmanCodeLengths[l3][l1];
               }

               if (dataFileVariables.huffmanCodeLengths[l3][l1] < byte8) {
                  byte8 = dataFileVariables.huffmanCodeLengths[l3][l1];
               }
            }

            buildHuffmanDecodeTables(
               dataFileVariables.huffmanLimits[l3],
               dataFileVariables.huffmanBases[l3],
               dataFileVariables.huffmanPerms[l3],
               dataFileVariables.huffmanCodeLengths[l3],
               byte8,
               i,
               i4
            );
            dataFileVariables.huffmanMinLengths[l3] = byte8;
         }

         // End-of-block symbol. It must stay an int: symbolsUsed is the number of
         // symbols actually used by the block (up to 256), so symbolsUsed + 1
         // reaches 257 and wraps to 1 if narrowed to a byte -- the loop below
         // then terminates on the wrong symbol and the whole block decodes to
         // garbage. This was decompiled as a reuse of the byte16 loop counter.
         int l4 = dataFileVariables.symbolsUsed + 1;
         int i5 = -1;
         int j5 = 0;

         for (int i2 = 0; i2 <= 255; i2++) {
            dataFileVariables.bwtByteCounts[i2] = 0;
         }

         // reset the sliding MTF structure: mtfSymbols is 16 cells of 16
         // bytes, indexed through mtfBase, so most MTF moves only shuffle
         // one cell instead of the whole 256-entry list.
         int j9 = 4095;

         for (int l8 = 15; l8 >= 0; l8--) {
            for (int i9 = 15; i9 >= 0; i9--) {
               dataFileVariables.mtfSymbols[j9] = (byte)(l8 * 16 + i9);
               j9--;
            }

            dataFileVariables.mtfBase[l8] = j9 + 1;
         }

         // main MTF/RLE2 decode loop. i5/j5 walk the selectors in 50-symbol
         // runs; k8/ai/ai1/ai2 are the current group's minLen/limit/base/perm;
         // k5 is the decoded symbol and i6 counts bytes into bwtBuffer.
         int i6 = 0;
         if (j5 == 0) {
            i5++;
            j5 = 50;
            byte byte12 = dataFileVariables.huffmanSelectors[i5];
            k8 = dataFileVariables.huffmanMinLengths[byte12];
            ai = dataFileVariables.huffmanLimits[byte12];
            ai2 = dataFileVariables.huffmanPerms[byte12];
            ai1 = dataFileVariables.huffmanBases[byte12];
         }

         j5--;
         int i7 = k8;
         int l7 = readBits(k8, dataFileVariables);

         while (l7 > ai[i7]) {
            i7++;
            byte byte9 = readBit(dataFileVariables);
            l7 = l7 << 1 | byte9;
         }

         int k5 = ai2[l7 - ai1[i7]];

         while (k5 != l4) {
            if (k5 != 0 && k5 != 1) {
               // literal symbol: pull MTF position k5 - 1 to the front of
               // the sliding list; byte6 is the byte it stood for.
               int j11 = k5 - 1;
               byte byte6;
               if (j11 < 16) {
                  int j10 = dataFileVariables.mtfBase[0];

                  for (byte6 = dataFileVariables.mtfSymbols[j10 + j11]; j11 > 3; j11 -= 4) {
                     int k11 = j10 + j11;
                     dataFileVariables.mtfSymbols[k11] = dataFileVariables.mtfSymbols[k11 - 1];
                     dataFileVariables.mtfSymbols[k11 - 1] = dataFileVariables.mtfSymbols[k11 - 2];
                     dataFileVariables.mtfSymbols[k11 - 2] = dataFileVariables.mtfSymbols[k11 - 3];
                     dataFileVariables.mtfSymbols[k11 - 3] = dataFileVariables.mtfSymbols[k11 - 4];
                  }

                  while (j11 > 0) {
                     dataFileVariables.mtfSymbols[j10 + j11] = dataFileVariables.mtfSymbols[j10 + j11 - 1];
                     j11--;
                  }

                  dataFileVariables.mtfSymbols[j10] = byte6;
               } else {
                  int l10 = j11 / 16;
                  int i11 = j11 % 16;
                  int k10 = dataFileVariables.mtfBase[l10] + i11;

                  for (byte6 = dataFileVariables.mtfSymbols[k10]; k10 > dataFileVariables.mtfBase[l10]; k10--) {
                     dataFileVariables.mtfSymbols[k10] = dataFileVariables.mtfSymbols[k10 - 1];
                  }

                  dataFileVariables.mtfBase[l10]++;

                  while (l10 > 0) {
                     dataFileVariables.mtfBase[l10]--;
                     dataFileVariables.mtfSymbols[dataFileVariables.mtfBase[l10]] = dataFileVariables.mtfSymbols[dataFileVariables.mtfBase[l10
                           - 1]
                        + 16
                        - 1];
                     l10--;
                  }

                  dataFileVariables.mtfBase[0]--;
                  dataFileVariables.mtfSymbols[dataFileVariables.mtfBase[0]] = byte6;
                  if (dataFileVariables.mtfBase[0] == 0) {
                     int i10 = 4095;

                     for (int k9 = 15; k9 >= 0; k9--) {
                        for (int l9 = 15; l9 >= 0; l9--) {
                           dataFileVariables.mtfSymbols[i10] = dataFileVariables.mtfSymbols[dataFileVariables.mtfBase[k9] + l9];
                           i10--;
                        }

                        dataFileVariables.mtfBase[k9] = i10 + 1;
                     }
                  }
               }

               dataFileVariables.bwtByteCounts[dataFileVariables.symbolMap[byte6 & 255] & 255]++;
               DataFileVariables.bwtBuffer[i6] = dataFileVariables.symbolMap[byte6 & 255] & 255;
               i6++;
               if (j5 == 0) {
                  i5++;
                  j5 = 50;
                  byte byte14 = dataFileVariables.huffmanSelectors[i5];
                  k8 = dataFileVariables.huffmanMinLengths[byte14];
                  ai = dataFileVariables.huffmanLimits[byte14];
                  ai2 = dataFileVariables.huffmanPerms[byte14];
                  ai1 = dataFileVariables.huffmanBases[byte14];
               }

               j5--;
               int k7 = k8;
               int j8 = readBits(k8, dataFileVariables);

               while (j8 > ai[k7]) {
                  k7++;
                  byte byte11 = readBit(dataFileVariables);
                  j8 = j8 << 1 | byte11;
               }

               k5 = ai2[j8 - ai1[k7]];
            } else {
               // RUNA/RUNB: a zero-run length in bijective base 2, expanded
               // as repeats of whatever byte is at the MTF front.
               int j6 = -1;
               int k6 = 1;

               do {
                  if (k5 == 0) {
                     j6 += k6;
                  } else if (k5 == 1) {
                     j6 += 2 * k6;
                  }

                  k6 *= 2;
                  if (j5 == 0) {
                     i5++;
                     j5 = 50;
                     byte byte13 = dataFileVariables.huffmanSelectors[i5];
                     k8 = dataFileVariables.huffmanMinLengths[byte13];
                     ai = dataFileVariables.huffmanLimits[byte13];
                     ai2 = dataFileVariables.huffmanPerms[byte13];
                     ai1 = dataFileVariables.huffmanBases[byte13];
                  }

                  j5--;
                  int j7 = k8;
                  int i8 = readBits(k8, dataFileVariables);

                  while (i8 > ai[j7]) {
                     j7++;
                     byte byte10 = readBit(dataFileVariables);
                     i8 = i8 << 1 | byte10;
                  }

                  k5 = ai2[i8 - ai1[j7]];
               } while (k5 == 0 || k5 == 1);

               j6++;
               byte byte5 = dataFileVariables.symbolMap[dataFileVariables.mtfSymbols[dataFileVariables.mtfBase[0]] & 255];

               for (dataFileVariables.bwtByteCounts[byte5 & 255] = dataFileVariables.bwtByteCounts[byte5 & 255] + j6; j6 > 0; j6--) {
                  DataFileVariables.bwtBuffer[i6] = byte5 & 255;
                  i6++;
               }
            }
         }

         // BWT inverse: build the cumulative counts (cftab), then pack each
         // entry's successor index into the top 24 bits of bwtBuffer so the
         // output stage can walk the block in original order.
         dataFileVariables.stateOutLen = 0;
         dataFileVariables.stateOutCh = 0;
         dataFileVariables.cumulativeByteCounts[0] = 0;

         for (int j2 = 1; j2 <= 256; j2++) {
            dataFileVariables.cumulativeByteCounts[j2] = dataFileVariables.bwtByteCounts[j2 - 1];
         }

         for (int k2 = 1; k2 <= 256; k2++) {
            dataFileVariables.cumulativeByteCounts[k2] = dataFileVariables.cumulativeByteCounts[k2] + dataFileVariables.cumulativeByteCounts[k2 - 1];
         }

         for (int l2 = 0; l2 < i6; l2++) {
            byte byte7 = (byte)(DataFileVariables.bwtBuffer[l2] & 0xFF);
            DataFileVariables.bwtBuffer[dataFileVariables.cumulativeByteCounts[byte7 & 255]] = DataFileVariables.bwtBuffer[dataFileVariables.cumulativeByteCounts[byte7
                  & 255]]
               | l2 << 8;
            dataFileVariables.cumulativeByteCounts[byte7 & 255]++;
         }

         // prime the walk at origPtr and hand off to the RLE output stage.
         dataFileVariables.tPos = DataFileVariables.bwtBuffer[dataFileVariables.origPtr] >> 8;
         dataFileVariables.blockBytesUsed = 0;
         dataFileVariables.tPos = DataFileVariables.bwtBuffer[dataFileVariables.tPos];
         dataFileVariables.lastDecodedByte = (byte)(dataFileVariables.tPos & 0xFF);
         dataFileVariables.tPos >>= 8;
         dataFileVariables.blockBytesUsed++;
         dataFileVariables.blockLength = i6;
         unRunLength(dataFileVariables);
         // block fully drained and no run pending -> look for another block;
         // otherwise the output buffer filled and we stop here.
         if (dataFileVariables.blockBytesUsed == dataFileVariables.blockLength + 1 && dataFileVariables.stateOutLen == 0) {
            flag19 = true;
         } else {
            flag19 = false;
         }
      }
   }

   private static byte readByte(DataFileVariables dataFileVariables) {
      return (byte)readBits(8, dataFileVariables);
   }

   private static byte readBit(DataFileVariables dataFileVariables) {
      return (byte)readBits(1, dataFileVariables);
   }

   // big-endian bit reader (bzlib's bsR): tops up bitBuffer/bitCount from
   // the input a byte at a time, keeping the 64-bit total-in counters.
   private static int readBits(int i, DataFileVariables dataFileVariables) {
      while (dataFileVariables.bitCount < i) {
         dataFileVariables.bitBuffer = dataFileVariables.bitBuffer << 8 | dataFileVariables.inputBuffer[dataFileVariables.inputOffset] & 255;
         dataFileVariables.bitCount += 8;
         dataFileVariables.inputOffset++;
         dataFileVariables.inputRemaining--;
         dataFileVariables.totalInLo32++;
         if (dataFileVariables.totalInLo32 == 0) {
            dataFileVariables.totalInHi32++;
         }
      }

      int k = dataFileVariables.bitBuffer >> dataFileVariables.bitCount - i & (1 << i) - 1;
      dataFileVariables.bitCount -= i;
      return k;
   }

   private static void buildSymbolMap(DataFileVariables dataFileVariables) {
      dataFileVariables.symbolsUsed = 0;

      for (int i = 0; i < 256; i++) {
         if (dataFileVariables.symbolInUse[i]) {
            dataFileVariables.symbolMap[dataFileVariables.symbolsUsed] = (byte)i;
            dataFileVariables.symbolsUsed++;
         }
      }
   }

   // bzlib's hbCreateDecodeTables: from the per-symbol code lengths build
   // the canonical huffman tables -- perm (symbols ordered by code length),
   // limit (largest code of each length) and base (offset into perm per
   // length), which is all the decode loops need.
   private static void buildHuffmanDecodeTables(int[] limit, int[] base, int[] perm, byte[] lengths, int minLen, int maxLen, int alphaSize) {
      int l = 0;

      for (int i1 = minLen; i1 <= maxLen; i1++) {
         for (int l2 = 0; l2 < alphaSize; l2++) {
            if (lengths[l2] == i1) {
               perm[l] = l2;
               l++;
            }
         }
      }

      for (int j1 = 0; j1 < 23; j1++) {
         base[j1] = 0;
      }

      for (int k1 = 0; k1 < alphaSize; k1++) {
         base[lengths[k1] + 1]++;
      }

      for (int l1 = 1; l1 < 23; l1++) {
         base[l1] += base[l1 - 1];
      }

      for (int i2 = 0; i2 < 23; i2++) {
         limit[i2] = 0;
      }

      int i3 = 0;

      for (int j2 = minLen; j2 <= maxLen; j2++) {
         i3 += base[j2 + 1] - base[j2];
         limit[j2] = i3 - 1;
         i3 <<= 1;
      }

      for (int k2 = minLen + 1; k2 <= maxLen; k2++) {
         base[k2] = (limit[k2 - 1] + 1 << 1) - base[k2];
      }
   }
}
