package io.netty.example.dns.doh;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.NetUtil;

import javax.net.ssl.SSLException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DoHClient {
    private final DohProviders.DohProvider dohProvider;

    public DoHClient(DohProviders.DohProvider dohProvider) {
        this.dohProvider = dohProvider;
    }

    private static void handleQueryResp(DefaultDnsResponse msg) {
        if (msg.count(DnsSection.QUESTION) > 0) {
            DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
            System.out.printf("name: %s%n", question.name());
        }
        for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A || record.type() == DnsRecordType.AAAA) {
                //just print the IP after query
                DnsRawRecord raw = (DnsRawRecord) record;
                System.out.println(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
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
                            ch.pipeline().addLast(sslCtx.newHandler(ch.alloc(), dohProvider.host(),
                                    dohProvider.port()));
//                            ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new DohRecordEncoder(dohProvider));
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


            ChannelFuture f = b.connect(dohProvider.host(), dohProvider.port()).sync();
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
        new DoHClient(DohProviders.GOOGLE).start();
    }
}
