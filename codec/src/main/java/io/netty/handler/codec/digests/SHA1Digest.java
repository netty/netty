package io.netty.handler.codec.digests;

/**
 * implementation of SHA-1 as outlined in "Handbook of Applied Cryptography",
 * pages 346 - 349.
 *
 * It is interesting to ponder why the, apart from the extra IV, the other
 * difference here from MD5 is the "endianness" of the word processing!
 */
public class SHA1Digest extends GeneralDigest {
  public static final int DIGEST_LENGTH = 20;

  private int H1, H2, H3, H4, H5;

  private int[] X = new int[80];
  private int xOff;

  /**
   * Standard constructor
   */
  public SHA1Digest() {
    reset();
  }

  public int getDigestSize() {
    return DIGEST_LENGTH;
  }

  protected void processWord(byte[] in, int inOff) {
    // Note: Inlined for performance
    // X[xOff] = Pack.bigEndianToInt(in, inOff);
    int n = in[inOff] << 24;
    n |= (in[++inOff] & 0xff) << 16;
    n |= (in[++inOff] & 0xff) << 8;
    n |= (in[++inOff] & 0xff);
    X[xOff] = n;

    if (++xOff == 16) {
      processBlock();
    }
  }

  protected void processLength(long bitLength) {
    if (xOff > 14) {
      processBlock();
    }

    X[14] = (int) (bitLength >>> 32);
    X[15] = (int) (bitLength & 0xffffffff);
  }

  public int doFinal(byte[] out, int outOff) {
    finish();

    Pack.intToBigEndian(H1, out, outOff);
    Pack.intToBigEndian(H2, out, outOff + 4);
    Pack.intToBigEndian(H3, out, outOff + 8);
    Pack.intToBigEndian(H4, out, outOff + 12);
    Pack.intToBigEndian(H5, out, outOff + 16);

    reset();

    return DIGEST_LENGTH;
  }

  /**
   * reset the chaining variables
   */
  public void reset() {
    super.reset();

    H1 = 0x67452301;
    H2 = 0xefcdab89;
    H3 = 0x98badcfe;
    H4 = 0x10325476;
    H5 = 0xc3d2e1f0;

    xOff = 0;
    for (int i = 0; i != X.length; i++) {
      X[i] = 0;
    }
  }

  //
  // Additive constants
  //
  private static final int Y1 = 0x5a827999;
  private static final int Y2 = 0x6ed9eba1;
  private static final int Y3 = 0x8f1bbcdc;
  private static final int Y4 = 0xca62c1d6;

  private int f(int u, int v, int w) {
    return ((u & v) | ((~u) & w));
  }

  private int h(int u, int v, int w) {
    return (u ^ v ^ w);
  }

  private int g(int u, int v, int w) {
    return ((u & v) | (u & w) | (v & w));
  }

  protected void processBlock() {
    //
    // expand 16 word block into 80 word block.
    //
    for (int i = 16; i < 80; i++) {
      int t = X[i - 3] ^ X[i - 8] ^ X[i - 14] ^ X[i - 16];
      X[i] = t << 1 | t >>> 31;
    }

    //
    // set up working variables.
    //
    int A = H1;
    int B = H2;
    int C = H3;
    int D = H4;
    int E = H5;

    //
    // round 1
    //
    int idx = 0;

    for (int j = 0; j < 4; j++) {
      // E = rotateLeft(A, 5) + f(B, C, D) + E + X[idx++] + Y1
      // B = rotateLeft(B, 30)
      E += (A << 5 | A >>> 27) + f(B, C, D) + X[idx++] + Y1;
      B = B << 30 | B >>> 2;

      D += (E << 5 | E >>> 27) + f(A, B, C) + X[idx++] + Y1;
      A = A << 30 | A >>> 2;

      C += (D << 5 | D >>> 27) + f(E, A, B) + X[idx++] + Y1;
      E = E << 30 | E >>> 2;

      B += (C << 5 | C >>> 27) + f(D, E, A) + X[idx++] + Y1;
      D = D << 30 | D >>> 2;

      A += (B << 5 | B >>> 27) + f(C, D, E) + X[idx++] + Y1;
      C = C << 30 | C >>> 2;
    }

    //
    // round 2
    //
    for (int j = 0; j < 4; j++) {
      // E = rotateLeft(A, 5) + h(B, C, D) + E + X[idx++] + Y2
      // B = rotateLeft(B, 30)
      E += (A << 5 | A >>> 27) + h(B, C, D) + X[idx++] + Y2;
      B = B << 30 | B >>> 2;

      D += (E << 5 | E >>> 27) + h(A, B, C) + X[idx++] + Y2;
      A = A << 30 | A >>> 2;

      C += (D << 5 | D >>> 27) + h(E, A, B) + X[idx++] + Y2;
      E = E << 30 | E >>> 2;

      B += (C << 5 | C >>> 27) + h(D, E, A) + X[idx++] + Y2;
      D = D << 30 | D >>> 2;

      A += (B << 5 | B >>> 27) + h(C, D, E) + X[idx++] + Y2;
      C = C << 30 | C >>> 2;
    }

    //
    // round 3
    //
    for (int j = 0; j < 4; j++) {
      // E = rotateLeft(A, 5) + g(B, C, D) + E + X[idx++] + Y3
      // B = rotateLeft(B, 30)
      E += (A << 5 | A >>> 27) + g(B, C, D) + X[idx++] + Y3;
      B = B << 30 | B >>> 2;

      D += (E << 5 | E >>> 27) + g(A, B, C) + X[idx++] + Y3;
      A = A << 30 | A >>> 2;

      C += (D << 5 | D >>> 27) + g(E, A, B) + X[idx++] + Y3;
      E = E << 30 | E >>> 2;

      B += (C << 5 | C >>> 27) + g(D, E, A) + X[idx++] + Y3;
      D = D << 30 | D >>> 2;

      A += (B << 5 | B >>> 27) + g(C, D, E) + X[idx++] + Y3;
      C = C << 30 | C >>> 2;
    }

    //
    // round 4
    //
    for (int j = 0; j <= 3; j++) {
      // E = rotateLeft(A, 5) + h(B, C, D) + E + X[idx++] + Y4
      // B = rotateLeft(B, 30)
      E += (A << 5 | A >>> 27) + h(B, C, D) + X[idx++] + Y4;
      B = B << 30 | B >>> 2;

      D += (E << 5 | E >>> 27) + h(A, B, C) + X[idx++] + Y4;
      A = A << 30 | A >>> 2;

      C += (D << 5 | D >>> 27) + h(E, A, B) + X[idx++] + Y4;
      E = E << 30 | E >>> 2;

      B += (C << 5 | C >>> 27) + h(D, E, A) + X[idx++] + Y4;
      D = D << 30 | D >>> 2;

      A += (B << 5 | B >>> 27) + h(C, D, E) + X[idx++] + Y4;
      C = C << 30 | C >>> 2;
    }

    H1 += A;
    H2 += B;
    H3 += C;
    H4 += D;
    H5 += E;

    //
    // reset start of the buffer.
    //
    xOff = 0;
    for (int i = 0; i < 16; i++) {
      X[i] = 0;
    }
  }

}
