package io.netty.codec.socks;

import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by IntelliJ IDEA.
 * User: alexey
 * Date: 11/12/12
 * Time: 6:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class SocksAuthRequestDecoderTest {
    @Test
    public void testAuthRequestDecoder() {
        String username = "test";
        String password = "test";
        SocksAuthRequest msg = new SocksAuthRequest(username, password);
        SocksAuthRequestDecoder decoder = new SocksAuthRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksAuthRequest) embedder.readInbound();
        assertTrue(msg.getUsername().equals(username));
        assertTrue(msg.getUsername().equals(password));
        assertNull(embedder.readInbound());
    }
}
