package io.netty.netty.rpc.provider;

import io.netty.netty.rpc.api.IRpcHelloService;

/**
 * @author lxcecho 909231497@qq.com
 * @since 16:28 29-10-2022
 */
public class RpcHelloServiceImpl implements IRpcHelloService {
    @Override
    public String hello(String name) {
        return "Hello, " + name + "!";
    }
}
