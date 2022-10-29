package io.netty.rpc.provider;

import io.netty.rpc.api.IRpcHelloService;
import io.netty.rpc.api.IRpcService;

/**
 * @author lxcecho 909231497@qq.com
 * @since 16:28 29-10-2022
 */
public class RpcServiceImpl implements IRpcService {
    @Override
    public int add(int a, int b) {
        return a + b;
    }

    @Override
    public int subtract(int a, int b) {
        return a - b;
    }

    @Override
    public int multiply(int a, int b) {
        return a * b;
    }

    @Override
    public int divide(int a, int b) {
        return a / b;
    }
}
