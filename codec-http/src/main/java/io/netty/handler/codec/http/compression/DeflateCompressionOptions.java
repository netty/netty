package io.netty.handler.codec.http.compression;

import io.netty.util.internal.ObjectUtil;

/**
 * {@link DeflateCompressionOptions} holds {@link #compressionLevel()},
 * {@link #memLevel()} and {@link #windowBits()} for Deflate compression.
 */
public class DeflateCompressionOptions implements CompressionOptions {

    private final int compressionLevel;
    private final int windowBits;
    private final int memLevel;

    /**
     * Default implementation of {@link DeflateCompressionOptions} with
     * {@link #compressionLevel} set to 6, {@link #windowBits} set to 15
     * and {@link #memLevel} set to 8.
     */
    public static final DeflateCompressionOptions DEFAULT = new DeflateCompressionOptions(
            6, 15, 8
    );

    /**
     * Create a new {@link DeflateCompressionOptions} Instance
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     * @param windowBits       The base two logarithm of the size of the history buffer.  The
     *                         value should be in the range {@code 9} to {@code 15} inclusive.
     *                         Larger values result in better compression at the expense of
     *                         memory usage.  The default value is {@code 15}.
     * @param memLevel         How much memory should be allocated for the internal compression
     *                         state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                         memory.  Larger values result in better and faster compression
     *                         at the expense of memory usage.  The default value is {@code 8}
     */
    public DeflateCompressionOptions(int compressionLevel, int windowBits, int memLevel) {
        this.compressionLevel = ObjectUtil.checkInRange(compressionLevel, 0, 9, "compressionLevel");
        this.windowBits = ObjectUtil.checkInRange(windowBits, 9, 15, "windowBits");
        this.memLevel = ObjectUtil.checkInRange(memLevel, 1, 9, "memLevel");
    }

    public int compressionLevel() {
        return compressionLevel;
    }

    public int windowBits() {
        return windowBits;
    }

    public int memLevel() {
        return memLevel;
    }
}
