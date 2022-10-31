package io.netty.netty.rpc.api;

/**
 * @author lxcecho 909231497@qq.com
 * @since 16:24 29-10-2022
 */
public interface IRpcHelloService {
    /**
     * 确认服务可用
     *
     * @param name
     * @return
     */
    String hello(String name);
}
