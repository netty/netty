package io.netty.codec.socks;

import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SocksCmdResponseDecoderTest {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SocksCmdResponseDecoderTest.class);

    private void testSocksCmdResponseDecoderWithDifferentParams(SocksMessage.CmdStatus cmdStatus, SocksMessage.AddressType addressType){
        logger.info("Testing cmdStatus: " + cmdStatus + " addressType: " + addressType);
        SocksResponse msg = new SocksCmdResponse(cmdStatus, addressType);
        SocksCmdResponseDecoder decoder = new SocksCmdResponseDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (addressType == SocksMessage.AddressType.UNKNOWN){
            assertTrue(embedder.readInbound() instanceof UnknownSocksResponse);
        } else {
            msg = (SocksCmdResponse) embedder.readInbound();
            assertTrue(((SocksCmdResponse) msg).getCmdStatus().equals(cmdStatus));
            assertNull(embedder.readInbound());
        }
    }

    @Test
    public void testSocksCmdResponseDecoderTest(){
        for (SocksMessage.CmdStatus cmdStatus: SocksMessage.CmdStatus.values()){
            for (SocksMessage.AddressType addressType: SocksMessage.AddressType.values()){
                testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, addressType);
            }
        }
    }
}
