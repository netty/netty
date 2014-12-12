package io.netty.example.http.router;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.router.BadClientSilencer;
import io.netty.handler.codec.http.router.Router;
import io.netty.handler.codec.http.router.RouterHandler;

public class HttpRouterServerInitializer extends ChannelInitializer<SocketChannel> {
    private final RouterHandler     handler;
    private final BadClientSilencer badClientSilencer = new BadClientSilencer();

    public HttpRouterServerInitializer(Router router) {
        handler = new RouterHandler(router);
    }

    public void initChannel(SocketChannel ch) {
        ch.pipeline()
          .addLast(new HttpServerCodec())
          .addLast(handler.name(), handler)
          .addLast(badClientSilencer);
    }
}
