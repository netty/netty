package io.netty.netty.rpc.consumer;

import io.netty.netty.rpc.api.IRpcHelloService;
import io.netty.netty.rpc.api.IRpcService;
import io.netty.netty.rpc.consumer.proxy.RpcProxy;

/**
 * @author lxcecho 909231497@qq.com
 * @since 16:26 29-10-2022
 */
public class RpcConsumer {

    public static void main(String[] args) {
        IRpcHelloService rpcHello = RpcProxy.create(IRpcHelloService.class);
        System.out.println(rpcHello.hello("lxcecho"));

        IRpcService service = RpcProxy.create(IRpcService.class);

        System.out.println("add: " + service.add(8, 2));
        System.out.println("subtract: " + service.subtract(8, 2));
        System.out.println("multiply: " + service.multiply(8, 2));
        System.out.println("divide: " + service.divide(8, 2));
    }

}
