package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
                "Cookie: _twitter_sess=BAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZF90b2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlY2JiZTJiODBlNDk3MmNiYjRiYzoMY3NyZl9pZCIlOTBlMzQ1%250AOGYzYzJiZDZkODM4MmU5M2UyNDc2NDQwMmYiCmZsYXNoSUM6J0FjdGlvbkNv%250AbnRyb2xsZXI6OkZsYXNoOjpGbGFzaEhhc2h7AAY6CkB1c2VkewA6B2lkIiU2%250ANjllMWE1ODkzNmY5ZDI2ZDA4M2E5MmZhZTFiNWY5ODoVaW5fbmV3X3VzZXJf%250AZmxvdzA6D2NyZWF0ZWRfYXRsKwic0jKmNQE6B3VhMA%253D%253D--872000bc7a385dd5cc182e52403bac9612e21aad; __utma=43838368.41298689.1329442411.1329939679.1329950853.10; __utmb=43838368.2.10.1329950853; __utmc=43838368; __utmv=43838368.lang%3A%20en; __utmz=43838368.1329939679.9.5.utmcsr=platform.twitter.com|utmccn=(referral)|utmcmd=referral|utmcct=/widgets/follow_button.1329368159.html; ab_sess_empty_timeline_176=1; pid=v1%3A1329943905918256635983; auth_token=8972b636d275e2b935b2ecbbe2b80e4972cbb4bc; auth_token_session=true; twll=l%3D1329939704; t1=1; guest_id=v1%3A132944162331520783; k=10.35.32.123.1329441623311662; lang=en; external_referer=padhuUp37zgqUmMDqxVOSYzGxCgaWDDnHLW1eft%2BcOl3GI1tIOaiIg0ro2MzGXX\n" +
                "Connection: keep-alive\n" +
                "\n").getBytes()));
        HttpMessage decode = (HttpMessage) decoderEmbedder.poll();
        assertTrue(decode.containsHeader("Host"));
        assertTrue(decode.containsHeader("Connection"));
        assertEquals("en-usTest", decode.getHeader("Accept-Language"));
        assertEquals("en-usTesting", decode.getHeader("Accept-Languages"));
        assertEquals("gzip, deflateTested", decode.getHeader("Accept-Encoding"));
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
                        "Content-Type: text/html; charset=utf-8\n" +
                        "X-Runtime: 0.33541\n" +
                        "X-MID: 295041ceca552bbcb01856ff2e95081944e0c810\n" +
                        "X-Revision: DEV\n" +
                        "Set-Cookie: dnt=; domain=.twitter.com; path=/; expires=Thu, 01-Jan-1970 00:00:00 GMT\n" +
                        "Set-Cookie: twid=u%3D668473%7CsPORk8i7LS6u1wq%2F2vP4TvAAWQc%3D; domain=.twitter.com; path=/; secure\n" +
                        "Set-Cookie: _twitter_sess=BAh7DSIKZmxhc2hJQzonQWN0aW9uQ29udHJvbGxlcjo6Rmxhc2g6OkZsYXNo%250ASGFzaHsABjoKQHVzZWR7ADoVaW5fbmV3X3VzZXJfZmxvdzA6B3VhMDoMY3Ny%250AZl9pZCIlOTBlMzQ1OGYzYzJiZDZkODM4MmU5M2UyNDc2NDQwMmY6D2NyZWF0%250AZWRfYXRsKwic0jKmNQE6CXVzZXJpAzkzCjoTcGFzc3dvcmRfdG9rZW4iLTg5%250ANzJiNjM2ZDI3NWUyYjkzNWIyZWNiYmUyYjgwZTQ5NzJjYmI0YmM6B2lk\n" +
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
                "Cookie: _twitter_sess=BAh7DToJdXNlcmkDOTMKOhNwYXNzd29yZF90b2tlbiItODk3MmI2MzZkMjc1%250AZTJiOTM1YjJlY2JiZTJiODBlNDk3MmNiYjRiYzoMY3NyZl9pZCIlOTBlMzQ1%250AOGYzYzJiZDZkODM4MmU5M2UyNDc2NDQwMmYiCmZsYXNoSUM6J0FjdGlvbkNv%250AbnRyb2xsZXI6OkZsYXNoOjpGbGFzaEhhc2h7AAY6CkB1c2VkewA6B2lkIiU2%250ANjllMWE1ODkzNmY5ZDI2ZDA4M2E5MmZhZTFiNWY5ODoVaW5fbmV3X3VzZXJf%250AZmxvdzA6D2NyZWF0ZWRfYXRsKwic0jKmNQE6B3VhMA%253D%253D--872000bc7a385dd5cc182e52403bac9612e21aad; __utma=43838368.41298689.1329442411.1329939679.1329950853.10; __utmb=43838368.2.10.1329950853; __utmc=43838368; __utmv=43838368.lang%3A%20en; __utmz=43838368.1329939679.9.5.utmcsr=platform.twitter.com|utmccn=(referral)|utmcmd=referral|utmcct=/widgets/follow_button.1329368159.html; ab_sess_empty_timeline_176=1; pid=v1%3A1329943905918256635983; auth_token=8972b636d275e2b935b2ecbbe2b80e4972cbb4bc; auth_token_session=true; twll=l%3D1329939704; t1=1; guest_id=v1%3A132944162331520783; k=10.35.32.123.1329441623311662; lang=en; external_referer=padhuUp37zgqUmMDqxVOSYzGxCgaWDDnHLW1eft%2BcOl3GI1tIOaiIg0ro2MzGXX\n" +
                "Connection: keep-alive\n" +
                "\n").getBytes()));
        decode = (HttpMessage) decoderEmbedder.poll();

        assertTrue(decode.containsHeader("Host"));
        assertTrue(decode.containsHeader("Connection"));
        assertEquals("en-us", decode.getHeader("Accept-Language"));
        assertEquals(1, decode.getHeaders("Cookie").size());

    }

    private DecoderEmbedder createEmbedder() {
        HttpMessageDecoder httpMessageDecoder = new HttpMessageDecoder() {
            @Override
            protected boolean isDecodingRequest() {
                return false;
            }

            @Override
            protected HttpMessage createMessage(String[] initialLine) throws Exception {
                return new DefaultHttpMessage(HttpVersion.HTTP_1_1);
            }
        };

        return new DecoderEmbedder(httpMessageDecoder);
    }
}
