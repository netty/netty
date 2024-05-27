package io.netty.handler.codec.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.PrematureChannelClosureException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;

public class HttpServer implements AutoCloseable{


    public static void main(String[] args) {
        if (args.length != 0) {
            System.err.println("Usage: App");
            System.exit(1);
        }

        try (HttpServer app = new HttpServer(4212)) {
            System.out.printf("Server listening on %s\n", app.future.channel().localAddress());
            app.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // set a limit of 5MB for the decompressed size
    private static final int _5_MB = 5 * 1024 * 1024;
    private final ChannelFuture future;

    HttpServer(int port) throws InterruptedException {
        final ChannelHandler httpHandler = new HttpHandler();
        final ChannelHandler exceptionHandler = new ExceptionHandler();

        ServerBootstrap bootstrap =
                new ServerBootstrap()
                        .group(new NioEventLoopGroup(1), new NioEventLoopGroup())
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.SO_BACKLOG, 50)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(
                                new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel socketChannel) {
                                        socketChannel
                                                .pipeline()
                                                .addLast(new HttpServerCodec())
                                                .addLast(new HttpContentDecompressor())
                                                .addLast(new HttpObjectAggregator(_5_MB))
                                                .addLast(httpHandler)
                                                .addLast(exceptionHandler);
                                    }
                                });
        future = bootstrap.bind(port).sync();
    }

    private void run() throws InterruptedException {
        future.channel().closeFuture().sync();
        System.out.printf("Closed the server on %s\n", future.channel().localAddress());
    }

    @Override
    public void close() throws InterruptedException {
        future.channel().close().sync();
    }

    @ChannelHandler.Sharable
    private static final class HttpHandler extends SimpleChannelInboundHandler<FullHttpMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpMessage msg) {
            String response = String.format("Processed %d bytes\n", msg.content().readableBytes());
            FullHttpResponse httpResponse =
                    new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.OK,
                            Unpooled.wrappedBuffer(response.getBytes(StandardCharsets.UTF_8)));
            httpResponse
                    .headers()
                    .set(CONTENT_TYPE, TEXT_PLAIN)
                    .setInt(CONTENT_LENGTH, httpResponse.content().readableBytes());
            HttpUtil.setKeepAlive(httpResponse, true);
            ctx.writeAndFlush(httpResponse);
        }
    }

    @ChannelHandler.Sharable
    private static final class ExceptionHandler extends ChannelDuplexHandler {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause.getClass().equals(PrematureChannelClosureException.class)
                    && !ctx.channel().isActive()) {
                // do nothing here
                return;
            }
            FullHttpResponse response =
                    new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            Unpooled.wrappedBuffer(printStackTrace(cause)));
            response
                    .headers()
                    .set(CONTENT_TYPE, TEXT_PLAIN)
                    .setInt(CONTENT_LENGTH, response.content().readableBytes());
            HttpUtil.setKeepAlive(response, false);
            ctx.writeAndFlush(response);
            ctx.close();
        }

        private static byte[] printStackTrace(Throwable cause) {
            try (ByteArrayOutputStream str = new ByteArrayOutputStream()) {
                try (PrintStream ps = new PrintStream(str)) {
                    cause.printStackTrace(ps);
                }
                return str.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


}