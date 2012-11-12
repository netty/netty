package io.netty.codec.socks;

import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Test;
import sun.net.util.IPAddressUtil;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by IntelliJ IDEA.
 * User: alexey
 * Date: 11/12/12
 * Time: 6:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class SocksCmdRequestDecoderTest {

//    private void testSocksCmdResponseDecoderWithDifferentParams(SocksMessage.CmdStatus cmdStatus, SocksMessage.AddressType addressType) {
//        System.out.println("Testing SocksCmdResponseDecoderTest with cmdStatus: " + cmdStatus + " addressType: " + addressType);
//        SocksResponse msg = new SocksCmdResponse(cmdStatus, addressType);
//        SocksCmdResponseDecoder decoder = new SocksCmdResponseDecoder();
//        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
//        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
//        if (addressType == SocksMessage.AddressType.UNKNOWN) {
//            assertTrue(embedder.readInbound() instanceof UnknownSocksResponse);
//        } else {
//            msg = (SocksCmdResponse) embedder.readInbound();
//            assertTrue(((SocksCmdResponse) msg).getCmdStatus().equals(cmdStatus));
//            assertNull(embedder.readInbound());
//        }
//    }
//
//    @Test
//    public void testSocksCmdResponseDecoderTest() {
//        for (SocksMessage.CmdStatus cmdStatus : SocksMessage.CmdStatus.values()) {
//            for (SocksMessage.AddressType addressType : SocksMessage.AddressType.values()) {
//                testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, addressType);
//            }
//        }
//    }

    @Test
    public void testCmdRequestDecoderConnectIPv4() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.CONNECT;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.IPv4;
        String host = "127.0.0.1";
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderBindIPv4() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.BIND;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.IPv4;
        String host = "127.0.0.1";
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderUdpIPv4() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.UDP;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.IPv4;
        String host = "127.0.0.1";
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderConnectDomain() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.CONNECT;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.DOMAIN;
        String host = "google.com";
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderBindDomain() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.BIND;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.DOMAIN;
        String host = "google.com";
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderUdpDomain() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.UDP;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.DOMAIN;
        String host = "google.com";
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderConnectIPv6() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.CONNECT;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.IPv6;
        String host = SocksCommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1"));
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderBindIPv6() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.BIND;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.IPv6;
        String host = SocksCommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1"));
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderUdpIPv6() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.UDP;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.IPv6;
        String host = SocksCommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1"));
        int port = 80;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderUdpIPv6Port50000() {
        SocksMessage.CmdType cmdType = SocksMessage.CmdType.UDP;
        SocksMessage.AddressType addressType = SocksMessage.AddressType.IPv6;
        String host = SocksCommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1"));
        int port = 50000;
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertTrue(msg.getCmdType().equals(cmdType));
        assertTrue(msg.getAddressType().equals(addressType));
        assertTrue(msg.getHost().equals(host));
        assertTrue(msg.getPort() == port);
        assertNull(embedder.readInbound());
    }
}
