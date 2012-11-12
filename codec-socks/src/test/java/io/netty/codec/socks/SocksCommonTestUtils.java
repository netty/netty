package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedByteChannel;

/**
 * Created by IntelliJ IDEA.
 * User: alexey
 * Date: 11/12/12
 * Time: 6:42 PM
 * To change this template use File | Settings | File Templates.
 */
class SocksCommonTestUtils {
    /**
     * A constructor to stop this class being constructed.
     */
    private SocksCommonTestUtils() {
        //NOOP
    }

    public static void writeMessageIntoEmbedder(EmbeddedByteChannel embedder, SocksMessage msg) {
        ByteBuf buf = Unpooled.buffer();
        msg.encodeAsByteBuf(buf);
        embedder.writeInbound(buf);
    }
}
