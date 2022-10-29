package io.netty.netty.tomcat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.netty.tomcat.http.NettyRequest;
import io.netty.netty.tomcat.http.NettyResponse;
import io.netty.netty.tomcat.http.NettyServlet;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Netty 就是一个同时支持多协议的网络通信框架
 *
 * @author lxcecho 909231497@qq.com
 * @since 10:24 29-10-2022
 */
public class NettyTomcat {
    // 打开 Tomcat 远么，全局搜索 ServerSocket

    private static final int port = 8090;

    private Map<String, NettyServlet> servletMapping = new HashMap<>();

    private Properties webXml = new Properties();

    private void init() {
        // 加载 web.xml 文件，同时初始化 ServletMapping 对象
        try {
            String path = this.getClass().getResource("/").getPath();
            FileInputStream fis = new FileInputStream(path + "web_netty.properties");
            webXml.load(fis);
            for (Object k : webXml.keySet()) {
                String key = k.toString();
                if (key.endsWith(".url")) {
                    String servletName = key.replaceAll("\\.url$", "");
                    String url = webXml.getProperty(key);
                    String className = webXml.getProperty(servletName + ".className");
                    NettyServlet obj = (NettyServlet) Class.forName(className).newInstance();
                    servletMapping.put(url, obj);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        init();

        // eventLoop-1-XXX

        // Netty 封装了 NIO，Reactor 模型，Boss，worker
        // Boss 线程
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        // Worker 线程
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // 1、创建对象
            // Netty 服务
            // ServetBootstrap   ServerSocketChannel
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            // 2、配置参数——链路式编程
            serverBootstrap.group(bossGroup, workerGroup)
                    // 主线程处理类,看到这样的写法，底层就是用反射
                    .channel(NioServerSocketChannel.class)
                    // 子线程处理类 , Handler
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        /**
                         * 客户端初始化处理
                         *
                         * @param ch            the {@link Channel} which was registered.
                         * @throws Exception
                         */
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            /**
                             * 无锁化串行编程
                             * Netty 对 HTTP 协议的封装，顺序有要求
                             * 责任链模式，双向链表 Inbound OutBound
                             */
                            ch.pipeline()
                                    // HttpResponseEncoder 编码器
                                    .addLast(new HttpResponseEncoder())
                                    // HttpRequestDecoder 解码器
                                    .addLast(new HttpRequestDecoder())
                                    // 业务逻辑处理
                                    .addLast(new NettyTomcatHandler());
                        }
                    })
                    // 针对主线程的配置，分配线程最大数量 128
                    .option(ChannelOption.SO_BACKLOG, 128)
                    // 针对子线程的配置，保持长连接
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // 3 启动服务器
            ChannelFuture cf = serverBootstrap.bind(port).sync();
            System.out.println("Netty Tomcat is completed on " + port);
            cf.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // close thread pool
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public class NettyTomcatHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpRequest) {
                System.out.println("hello ~~~");
                HttpRequest req = (HttpRequest) msg;

                // 转交给我们自己的 request 实现
                NettyRequest nettyRequest = new NettyRequest(ctx, req);
                // 转交给我们自己的 response 实现
                NettyResponse nettyResponse = new NettyResponse(ctx, req);

                // 实际业务处理
                String url = nettyRequest.getUrl();
                if (servletMapping.containsKey(url)) {
                    servletMapping.get(url).service(nettyRequest, nettyResponse);
                } else {
                    nettyResponse.write("404-NOT FOUND");
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
        }
    }

    public static void main(String[] args) {
        new NettyTomcat().start();
    }

}
