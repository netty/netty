package io.netty.handler.codec.memcache.ascii;


import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheArithmeticResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheDeleteResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheErrorResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheFlushResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheRetrieveResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheStatsResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheStoreResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheTouchResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheVersionResponse;
import io.netty.util.CharsetUtil;
import io.netty.util.Version;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class AsciiMemcacheResponseEncoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new AsciiMemcacheResponseEncoder());
    }

    @Test
    public void shouldEncodeStoreOperation() {
        AsciiMemcacheStoreResponse response = new AsciiMemcacheStoreResponse(
                AsciiMemcacheStoreResponse.StorageResponse.EXISTS
        );
        writeAndAssertOutbound(response, "EXISTS");
    }

    @Test
    public void shouldEncodeErrorResponse() {
        AsciiMemcacheErrorResponse response = new AsciiMemcacheErrorResponse(
                AsciiMemcacheErrorResponse.ErrorResponse.ERROR
        );
        writeAndAssertOutbound(response, "ERROR");
    }

    @Test
    public void shouldEncodeErrorWithMessageResponse() {
        String msg = "You did something wrong, boy.";
        AsciiMemcacheErrorResponse response = new AsciiMemcacheErrorResponse(
                AsciiMemcacheErrorResponse.ErrorResponse.CLIENT_ERROR,
                msg
        );
        writeAndAssertOutbound(response, "CLIENT_ERROR " + msg);
    }

    @Test
    public void shouldEncodeRetrieveResponse() {
        AsciiMemcacheRetrieveResponse response = new AsciiMemcacheRetrieveResponse(
          "foo", 100, 0
        );
        writeAndAssertOutbound(response, "VALUE foo 0 100");
    }

    @Test
    public void shouldEncodeDeleteResponse() {
        AsciiMemcacheDeleteResponse response = new AsciiMemcacheDeleteResponse(
            AsciiMemcacheDeleteResponse.DeleteResponse.DELETED
        );
        writeAndAssertOutbound(response, "DELETED");
    }

    @Test
    public void shouldEncodeArithmeticFoundResponse() {
        AsciiMemcacheArithmeticResponse response = new AsciiMemcacheArithmeticResponse(
            10
        );
        writeAndAssertOutbound(response, "10");
    }

    @Test
    public void shouldEncodeArithmeticNotFoundResponse() {
        AsciiMemcacheArithmeticResponse response = AsciiMemcacheArithmeticResponse.NOT_FOUND;
        writeAndAssertOutbound(response, "NOT_FOUND");
    }

    @Test
    public void shouldEncodeTouchResponse() {
        AsciiMemcacheTouchResponse response = new AsciiMemcacheTouchResponse(
            AsciiMemcacheTouchResponse.TouchResponse.TOUCHED
        );
        writeAndAssertOutbound(response, "TOUCHED");
    }

    @Test
    public void shouldEncodeVersionResponse() {
        AsciiMemcacheVersionResponse response = new AsciiMemcacheVersionResponse(
            "1.0.0"
        );
        writeAndAssertOutbound(response, "VERSION 1.0.0");
    }

    @Test
    public void shouldEncodeFlushResponse() {
        AsciiMemcacheFlushResponse response = new AsciiMemcacheFlushResponse();
        writeAndAssertOutbound(response, "OK");
    }

    @Test
    public void shouldEncodeStatsResponse() {
        Map<String, String> stats = new HashMap<String, String>();
        stats.put("netty", "rocks");
        stats.put("load", "uber-webscale");
        AsciiMemcacheStatsResponse response = new AsciiMemcacheStatsResponse(stats);

        writeAndAssertOutbound(response, "STAT netty rocks\r\nSTAT load uber-webscale\r\nEND");
    }

    /**
     * Helper method to assert a string on the outbound channel side.
     *
     * Note that for convenience, the expected string does not need to contain
     * the newline characters, those will be added automatically.
     *
     * @param res the response to write.
     * @param expected the expected string written out.
     */
    private void writeAndAssertOutbound(AsciiMemcacheResponse res, String expected) {
        boolean result = channel.writeOutbound(res);
        assertThat(result, is(true));

        ByteBuf written = (ByteBuf) channel.readOutbound();
        assertThat(written.toString(CharsetUtil.UTF_8), is(expected + "\r\n"));
        written.release();
    }
}
