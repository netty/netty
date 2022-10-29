package io.netty.rpc.consumer.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.rpc.protocol.InvokerProtocol;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author lxcecho 909231497@qq.com
 * @since 16:27 29-10-2022
 */
public class RpcProxy {

    public static <T> T create(Class<?> clazz) {
        // clazz 传进来本身就是 interface
        MethodProxy proxy = new MethodProxy(clazz);
        Class<?>[] interfaces = clazz.isInterface() ?
                new Class[]{clazz} :
                clazz.getInterfaces();
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), interfaces, proxy);
    }

    private static class MethodProxy implements InvocationHandler {
        private Class<?> clazz;

        public MethodProxy(Class<?> clazz) {
            this.clazz = clazz;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 如果传进来是一个已实现的具体类（本次演示略过此逻辑)
            if (Object.class.equals(method.getDeclaringClass())) {
                try {
                    return method.invoke(this, args);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                // 如果传进来的是一个接口（核心)
            } else {
                return rpcInvoke(proxy, method, args);
            }
            return null;
        }

        /**
         * 实现核心的方法
         *
         * @param proxy
         * @param method
         * @param args
         * @return
         */
        private Object rpcInvoke(Object proxy, Method method, Object[] args) {
            // 传输协议封装
            InvokerProtocol protocol = InvokerProtocol.builder()
                    .className(this.clazz.getName()).methodName(method.getName())
                    .values(args).params(method.getParameterTypes())
                    .build();

            NioEventLoopGroup group = new NioEventLoopGroup();
            RpcProxyHandler rpcProxyHandler = new RpcProxyHandler();
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline()
                                        .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))
                                        .addLast("frameEncoder", new LengthFieldPrepender(4))
                                        .addLast("encoder", new ObjectEncoder())
                                        .addLast("decoder", new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)))
                                        .addLast(rpcProxyHandler);
                            }
                        });

                ChannelFuture cf = bootstrap.connect("localhost", 8090).sync();
                cf.channel().writeAndFlush(protocol).sync();
                cf.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                group.shutdownGracefully();
            }
            return rpcProxyHandler.getResponse();
        }
    }

}
