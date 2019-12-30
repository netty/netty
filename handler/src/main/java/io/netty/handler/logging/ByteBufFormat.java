package io.netty.handler.logging;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/**
 * Used to control the format and verbosity of {@link ByteBuf} logging.
 *
 * @see LoggingHandler
 */
public enum ByteBufFormat {
    /**
     * {@link ByteBuf}s will be logged in a simple format, with no hex dump included.
     */
    SIMPLE,
    /**
     * {@link ByteBuf}s will be logged using {@link ByteBufUtil#appendPrettyHexDump(StringBuilder, ByteBuf)}.
     */
    HEX_DUMP
}