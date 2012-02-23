package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.junit.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

public class HttpMessageDecoderTest {
    @Test
    public void testReadHeaders() throws Exception {
        DecoderEmbedder decoderEmbedder = createEmbedder();
        decoderEmbedder.offer(ChannelBuffers.copiedBuffer(("GET / HTTP/1.1\n" +
                "Accept-Language: en-us\r\n" +
                "\tTest\n" +
                "Host: twitter.com\n" +
                "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_3) AppleWebKit/534.53.11 (KHTML, like Gecko) Version/5.1.3 Safari/534.53.10\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\n" +
                "Accept-Languages: en-us\r\n" +
                " Testing\n" +
                "Accept-Encoding: gzip, deflate\n" +
                "\tTested\n" +
                "Cookie: _twitter_sess=BAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJl\n" +
                "Connection: keep-alive\n" +
                "\n").getBytes()));
        HttpMessage decode = (HttpMessage) decoderEmbedder.poll();
        assertTrue(decode.containsHeader("Host"));
        assertTrue(decode.containsHeader("Connection"));
        assertEquals("en-us Test", decode.getHeader("Accept-Language"));
        assertEquals("en-us Testing", decode.getHeader("Accept-Languages"));
        assertEquals("gzip, deflate Tested", decode.getHeader("Accept-Encoding"));
        assertEquals(1, decode.getHeaders("Cookie").size());

        ChannelBuffer complete = ChannelBuffers.wrappedBuffer((
                "HTTP/1.1 200 OK\n" +
                        "Date: Wed, 22 Feb 2012 22:47:51 GMT\n" +
                        "Status: 200 OK\n" +
                        "Cache-Control: no-cache, no-store, must-revalidate, pre-check=0, post-check=0\n" +
                        "Pragma: no-cache\n" +
                        "ETag: \"f8ab7d2555ac27734157e5c1281f9b98\"\n" +
                        "X-Frame-Options: SAMEORIGIN\n" +
                        "X-Transaction: a760febf03ca44bb\n" +
                        "Expires: Tue, 31 Mar 1981 05:00:00 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Last-Modified: Wed, 22 Feb 2012 22:47:50 GMT\n" +
                        "Content-Type: text/html; charset=utf-8\n" +
                        "X-Runtime: 0.33541\n" +
                        "X-MID: 295041ceca552bbcb01856ff2e95081944e0c810\n" +
                        "X-Revision: DEV\n" +
                        "Set-Cookie: dnt=; domain=.twitter.com; path=/; expires=Thu, 01-Jan-1970 00:00:00 GMT\n" +
                        "Set-Cookie: twid=u%3D668473%%2F2vP4TvAAWQc%3D; domain=.twitter.com; path=/; secure\n" +
                        "Set-Cookie: _twitter_sess=BAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJl\n" +
                        "X-XSS-Protection: 1; mode=block\n" +
                        "Vary: Accept-Encoding\n" +
                        "Content-Encoding: gzip\n" +
                        "Content-Length: 1\n" +
                        "Server: tfe\n" +
                        "\n ").getBytes());

        int length = complete.readableBytes();
        int split = new Random().nextInt(length);
        decoderEmbedder = createEmbedder();
        decoderEmbedder.offer(complete.slice(0, split));
        decoderEmbedder.offer(complete.slice(split, length - split));
        complete.readerIndex(0);

        decode = (HttpMessage) decoderEmbedder.poll();

        assertTrue(decode.containsHeader("Status"));
        assertEquals("1", decode.getHeader("Content-Length"));
        assertEquals(3, decode.getHeaders("Set-Cookie").size());

        decoderEmbedder = createEmbedder();
        decoderEmbedder.offer(ChannelBuffers.copiedBuffer(("GET / HTTP/1.1\n" +
                "Host: twitter.com\n" +
                "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_3) AppleWebKit/534.53.11 (KHTML, like Gecko) Version/5.1.3 Safari/534.53.10\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\n" +
                "Accept-Language: en-us\n" +
                "Accept-Encoding: gzip, deflate\n" +
                "Cookie: _twitter_sess=BAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJl\n" +
                "Connection: keep-alive\n" +
                "\n").getBytes()));
        decode = (HttpMessage) decoderEmbedder.poll();

        assertTrue(decode.containsHeader("Host"));
        assertTrue(decode.containsHeader("Connection"));
        assertEquals("en-us", decode.getHeader("Accept-Language"));
        assertEquals(1, decode.getHeaders("Cookie").size());
        decode.removeHeader("en-us");
        assertFalse(decode.containsHeader("en-us"));
    }

    @Test
    public void microBenchmark() {
        long start = System.currentTimeMillis();
        int iterations = 200000;
        for (int i = 0; i < iterations; i++) {
            DecoderEmbedder decoderEmbedder = createEmbedder();
            decoderEmbedder.offer(ChannelBuffers.copiedBuffer(("GET / HTTP/1.1\n" +
                    "Accept-Language: en-us\r\n" +
                    "\tTest\n" +
                    "Host: twitter.com\n" +
                    "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_3) AppleWebKit/534.53.11 (KHTML, like Gecko) Version/5.1.3 Safari/534.53.10\n" +
                    "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\n" +
                    "Accept-Languages: en-us\r\n" +
                    " Testing\n" +
                    "Accept-Encoding: gzip, deflate\n" +
                    "\tTested\n" +
                    "Cookie: _twitter_sess=BAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlBAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZFb2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJl\n" +
                    "Connection: keep-alive\n" +
                    "\n").getBytes()));
            HttpMessage headerBlockVersion = (HttpMessage) decoderEmbedder.poll();
            getHeaders(headerBlockVersion);
        }
        long newdiff = System.currentTimeMillis() - start;
        System.out.println(iterations * 1000 / newdiff + " header blocks per second");
    }

    private void getHeaders(HttpMessage headerBlockVersion) {
        headerBlockVersion.getHeader("Accept-Language");
        headerBlockVersion.getHeader("Host");
        headerBlockVersion.getHeader("User-Agent");
        headerBlockVersion.getHeader("Accept");
        headerBlockVersion.getHeader("Accept-Languages");
        headerBlockVersion.getHeader("Cookie");
        headerBlockVersion.getHeader("Connection");
    }


    private DecoderEmbedder createEmbedder() {
        HttpMessageDecoder httpMessageDecoder = new HttpMessageDecoder() {
            @Override
            protected boolean isDecodingRequest() {
                return false;
            }

            @Override
            protected DefaultHttpMessage createMessage(String[] initialLine) throws Exception {
                return new DefaultHttpMessage(HttpVersion.HTTP_1_1);
            }
        };

        return new DecoderEmbedder(httpMessageDecoder);
    }
}
