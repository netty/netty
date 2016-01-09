package io.netty.handler.codec.digests;

/**
 * interface that a message digest conforms to.
 */
public interface Digest {

  /**
   * update the message digest with a block of bytes.
   *
   * @param in
   *          the byte array containing the data.
   * @param inOff
   *          the offset into the byte array where the data starts.
   * @param len
   *          the length of the data.
   */
  public void update(byte[] in, int inOff, int len);

  /**
   * close the digest, producing the final digest value. The doFinal call leaves
   * the digest reset.
   *
   * @param out
   *          the array the digest is to be copied into.
   * @param outOff
   *          the offset into the out array the digest is to start at.
   */
  public int doFinal(byte[] out, int outOff);

  /**
   * reset the digest back to it's initial state.
   */
  public void reset();
}