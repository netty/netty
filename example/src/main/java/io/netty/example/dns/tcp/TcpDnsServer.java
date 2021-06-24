package io.netty.example.dns.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.NetUtil;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class TcpDnsServer {
    private static final String QUERY_DOMAIN = "www.example.com";
    private static final int DNS_SERVER_PORT = 53;
    private static final String DNS_SERVER_HOST = "127.0.0.1";

    public static void main(String[] args) throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap().group(new NioEventLoopGroup(1), new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new TcpDnsQueryDecoder(), new TcpDnsResponseEncoder(), new SimpleChannelInboundHandler<DnsQuery>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DnsQuery msg) throws Exception {
                                DnsQuestion question = msg.recordAt(DnsSection.QUESTION);
                                System.out.println("Query domain: " + question);

                                ctx.writeAndFlush(newResponse(msg, question, 600, new byte[]{(byte) 192, (byte) 168, 1, 1}));
                            }

                            private DefaultDnsResponse newResponse(DnsQuery query, DnsQuestion question, long ttl, byte[]... addresses) {
                                DefaultDnsResponse response = new DefaultDnsResponse(query.id());
                                response.addRecord(DnsSection.QUESTION, question);

                                for (byte[] address : addresses) {
                                    DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(question.name(), DnsRecordType.A, ttl, Unpooled.wrappedBuffer(address));
                                    response.addRecord(DnsSection.ANSWER, queryAnswer);
                                }
                                return response;
                            }
                        });
                    }
                });
        Executors.newSingleThreadScheduledExecutor().schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    clientQuery();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 2000, TimeUnit.MILLISECONDS);
        bootstrap.bind(DNS_SERVER_PORT).channel().closeFuture().sync();
    }

    private static void clientQuery() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new TcpDnsQueryEncoder())
                                    .addLast(new TcpDnsResponseDecoder())
                                    .addLast(new SimpleChannelInboundHandler<DefaultDnsResponse>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, DefaultDnsResponse msg) {
                                            try {
                                                handleQueryResp(msg);
                                            } finally {
                                                ctx.close();
                                            }
                                        }
                                    });
                        }
                    });

            final Channel ch = b.connect(DNS_SERVER_HOST, DNS_SERVER_PORT).sync().channel();

            int randomID = new Random().nextInt(60000 - 1000) + 1000;
            DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                    .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
            ch.writeAndFlush(query).sync();
            boolean success = ch.closeFuture().await(10, TimeUnit.SECONDS);
            if (!success) {
                System.err.println("dns query timeout!");
                ch.close().sync();
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void handleQueryResp(DefaultDnsResponse msg) {
        if (msg.count(DnsSection.QUESTION) > 0) {
            DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
            System.out.printf("name: %s%n", question.name());
        }
        for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A) {
                //just print the IP after query
                DnsRawRecord raw = (DnsRawRecord) record;
                System.out.println(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
            }
        }
    }
}
