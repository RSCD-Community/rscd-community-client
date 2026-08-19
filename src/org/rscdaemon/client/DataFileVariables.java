package org.rscdaemon.client;

/**
 * Decoder state block for DataFileDecrypter -- one instance per unpackData
 * call. This is bzlib's DState struct ported field-for-field: the nine finals
 * are the bzlib constants (MTFA_SIZE, MTFL_SIZE, MAX_ALPHA_SIZE, MAX_CODE_LEN,
 * BZ_RUNB, BZ_N_GROUPS, BZ_G_SIZE, BZ_N_ITERS, BZ_MAX_SELECTORS), then the
 * stream cursors, the bit buffer, per-block Huffman/MTF tables, and the BWT
 * inverse. bwtBuffer is bzip2's tt array; it is static so the 100k ints are
 * allocated once and shared across every archive load.
 */
class DataFileVariables {
   final int mtfArraySize = 4096;
   final int mtfSubArraySize = 16;
   final int maxAlphaSize = 258;
   final int maxCodeLength = 23;
   final int runBSymbol = 1;
   final int maxHuffmanGroups = 6;
   final int symbolsPerGroup = 50;
   final int huffmanIterations = 4;
   final int maxSelectors = 18002;
   byte[] inputBuffer;
   int inputOffset;
   int inputRemaining;
   int totalInLo32;
   int totalInHi32;
   byte[] outputBuffer;
   int outputOffset;
   int outputRemaining;
   int totalOutLo32;
   int totalOutHi32;
   byte stateOutCh;
   int stateOutLen;
   boolean blockRandomised;
   int bitBuffer;
   int bitCount;
   int blockSize100k;
   int currentBlock;
   int origPtr;
   int tPos;
   int lastDecodedByte;
   int[] bwtByteCounts = new int[256];
   int blockBytesUsed;
   int[] cumulativeByteCounts = new int[257];
   int[] cumulativeByteCountsCopy = new int[257];
   public static int[] bwtBuffer;
   int symbolsUsed;
   boolean[] symbolInUse = new boolean[256];
   boolean[] symbolRangeInUse = new boolean[16];
   byte[] symbolMap = new byte[256];
   byte[] mtfSymbols = new byte[4096];
   int[] mtfBase = new int[16];
   byte[] huffmanSelectors = new byte[18002];
   byte[] huffmanSelectorsMtf = new byte[18002];
   byte[][] huffmanCodeLengths = new byte[6][258];
   int[][] huffmanLimits = new int[6][258];
   int[][] huffmanBases = new int[6][258];
   int[][] huffmanPerms = new int[6][258];
   int[] huffmanMinLengths = new int[6];
   int blockLength;
}
