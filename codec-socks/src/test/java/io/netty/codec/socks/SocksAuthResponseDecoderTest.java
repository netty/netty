package io.netty.codec.socks;

import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by IntelliJ IDEA.
 * User: alexey
 * Date: 11/12/12
 * Time: 6:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class SocksAuthResponseDecoderTest {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SocksAuthResponseDecoderTest.class);
    private void testSocksAuthResponseDecoderWithDifferentParams(SocksMessage.AuthStatus authStatus){
        logger.info("Testing SocksAuthResponseDecoder with authStatus: "+ authStatus);
        SocksAuthResponse msg = new SocksAuthResponse(authStatus);
        SocksAuthResponseDecoder decoder = new SocksAuthResponseDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksAuthResponse) embedder.readInbound();
        assertTrue(msg.getAuthStatus().equals(authStatus));
        assertNull(embedder.readInbound());
    }

    @Test
    public void testSocksCmdResponseDecoderTest(){
        for (SocksMessage.AuthStatus authStatus: SocksMessage.AuthStatus.values()){
                testSocksAuthResponseDecoderWithDifferentParams(authStatus);
        }
    }
}
