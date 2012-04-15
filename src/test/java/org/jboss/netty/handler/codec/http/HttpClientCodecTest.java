package org.jboss.netty.handler.codec.http;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.PrematureChannelClosureException;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

public class HttpClientCodecTest {

    private static final String RESPONSE = "HTTP/1.0 200 OK\r\n" + "Date: Fri, 31 Dec 1999 23:59:59 GMT\r\n" + "Content-Type: text/html\r\n" + "Content-Length: 28\r\n" + "\r\n"
            + "<html><body></body></html>\r\n";

    @Test
    public void testFailsNotOnRequestResponse() {
        HttpClientCodec codec = new HttpClientCodec();
        DecoderEmbedder<ChannelBuffer> decoder = new DecoderEmbedder<ChannelBuffer>(codec);
        EncoderEmbedder<ChannelBuffer> encoder = new EncoderEmbedder<ChannelBuffer>(codec);
        
        encoder.offer(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/"));
        decoder.offer(ChannelBuffers.copiedBuffer(RESPONSE, CharsetUtil.ISO_8859_1));

        encoder.finish();
        decoder.finish();
   
    }
    
    @Test
    public void testFailsOnMissingResponse() {
        HttpClientCodec codec = new HttpClientCodec();
        EncoderEmbedder<ChannelBuffer> encoder = new EncoderEmbedder<ChannelBuffer>(codec);
        
        encoder.offer(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/"));

        try {
            encoder.finish();
            fail();
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof PrematureChannelClosureException);
        }
        
    }
}
