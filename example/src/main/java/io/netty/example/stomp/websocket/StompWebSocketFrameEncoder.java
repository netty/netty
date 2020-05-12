package io.netty.example.stomp.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.stomp.LastStompContentSubframe;
import io.netty.handler.codec.stomp.StompFrame;
import io.netty.handler.codec.stomp.StompHeadersSubframe;
import io.netty.handler.codec.stomp.StompSubframe;
import io.netty.handler.codec.stomp.StompSubframeEncoder;

import java.util.List;

public class StompWebSocketFrameEncoder extends StompSubframeEncoder {

    @Override
    public void encode(ChannelHandlerContext ctx, StompSubframe msg, List<Object> out) throws Exception {
        super.encode(ctx, msg, out);

        if (out.isEmpty()) {
            return;
        }

        final WebSocketFrame webSocketFrame;
        if (msg instanceof StompFrame) {
            if (out.size() == 1) {
                webSocketFrame = new TextWebSocketFrame(getFirst(out));
            } else {
                CompositeByteBuf content = ctx.alloc().compositeBuffer(out.size());
                for (Object byteBuf : out) {
                    content.addComponent(true, (ByteBuf) byteBuf);
                }
                webSocketFrame = new TextWebSocketFrame(content);
            }
        } else if (msg instanceof StompHeadersSubframe) {
            webSocketFrame = new TextWebSocketFrame(false, 0, getFirst(out));
        } else if (msg instanceof LastStompContentSubframe) {
            webSocketFrame = new ContinuationWebSocketFrame(true, 0, getFirst(out));
        } else {
            webSocketFrame = new ContinuationWebSocketFrame(false, 0, getFirst(out));
        }

        out.clear();
        out.add(webSocketFrame);
    }

    private static ByteBuf getFirst(List<Object> container) {
        return (ByteBuf) container.get(0);
    }
}
