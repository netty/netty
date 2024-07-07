package io.netty.example.dns.doh;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.doh.DohRecordEncoder;
import io.netty.handler.codec.doh.DohResponseDecoder;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.NetUtil;
import io.netty.util.internal.SocketUtils;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DoHClient {
    private final static InetSocketAddress DOH_SRV_GOOGLE = SocketUtils.socketAddress("dns.google", 443);
    private final static InetSocketAddress DOH_SRV_CLOUDFLARE = SocketUtils.socketAddress("1.1.1.1", 443);
    private final static InetSocketAddress DOH_SRV_QUAD9 = SocketUtils.socketAddress("dns.quad9.net", 443);


    private final InetSocketAddress dohServer;

    public DoHClient(InetSocketAddress dohServer) {
        this.dohServer = dohServer;
    }

    private static void handleQueryResp(DefaultDnsResponse msg) {
        if (msg.count(DnsSection.QUESTION) > 0) {
            DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
            System.out.printf("name: %s%n", question.name());
        }
        if (msg.count(DnsSection.ADDITIONAL) > 0) {
            DnsRecord record = msg.recordAt(DnsSection.ADDITIONAL, 0);
            System.out.printf("name: %s%n", record.name());
        }
        if (msg.count(DnsSection.AUTHORITY) > 0) {
            DnsRecord record = msg.recordAt(DnsSection.AUTHORITY, 0);
            System.out.printf("name: %s%n", record.name());
        }
        for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A || record.type() == DnsRecordType.AAAA) {
                //just print the IP after query
                DnsRawRecord raw = (DnsRawRecord) record;
                System.out.println(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
            } else {
                DnsRawRecord raw = (DnsRawRecord) record;
                System.out.println(new String(ByteBufUtil.getBytes(raw.content())));
            }
        }
    }

    public void start() throws InterruptedException, SSLException {
        SslContext sslCtx = SslContextBuilder.forClient().build();
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(sslCtx.newHandler(ch.alloc(), dohServer.getHostName(),
                                    dohServer.getPort()));
//                            ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new DohRecordEncoder(dohServer));
                            ch.pipeline().addLast(new DohResponseDecoder());

                            ch.pipeline().addLast(new SimpleChannelInboundHandler<DefaultDnsResponse>() {
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


            ChannelFuture f = b.connect(dohServer.getHostName(), dohServer.getPort()).sync();
            Channel channel = f.channel();

            DefaultDnsQuestion defaultDnsQuestion = new DefaultDnsQuestion("example.com.",
                    DnsRecordType.AAAA);

            int randomID = new Random().nextInt(60000 - 1000) + 1000;
            DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                    .setRecord(DnsSection.QUESTION, defaultDnsQuestion);

            channel.writeAndFlush(query).sync();


            boolean success = channel.closeFuture().await(100, TimeUnit.SECONDS);
            if (!success) {
                System.err.println("dns query timeout!");
                channel.close().sync();
            }

        } finally {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws InterruptedException, SSLException {
        new DoHClient(DOH_SRV_GOOGLE).start();
        new DoHClient(DOH_SRV_CLOUDFLARE).start();
        new DoHClient(DOH_SRV_QUAD9).start();
    }
}
