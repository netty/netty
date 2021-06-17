package io.netty.handler.codec.http.compression;

/**
 * {@link GzipCompressionOptions} holds {@link #compressionLevel()},
 * {@link #memLevel()} and {@link #windowBits()} for Gzip compression.
 * This class is an extension of {@link DeflateCompressionOptions}
 */
public final class GzipCompressionOptions extends DeflateCompressionOptions {

    /**
     * Default implementation of {@link GzipCompressionOptions} with
     * {@link #compressionLevel()} set to 6, {@link #windowBits()} set to 15
     * and {@link #memLevel()} set to 8.
     */
    public static final GzipCompressionOptions DEFAULT = new GzipCompressionOptions(
            6, 15, 8
    );

    /**
     * Create a new {@link GzipCompressionOptions} Instance
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
    public GzipCompressionOptions(int compressionLevel, int windowBits, int memLevel) {
        super(compressionLevel, windowBits, memLevel);
    }
}
