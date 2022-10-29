package io.netty.rpc.api;

/**
 * @author lxcecho 909231497@qq.com
 * @since 16:23 29-10-2022
 */
public interface IRpcService {

    /**
     * 加
     *
     * @param a
     * @param b
     * @return
     */
    int add(int a, int b);

    /**
     * 减
     *
     * @param a
     * @param b
     * @return
     */
    int subtract(int a, int b);

    /**
     * 乘加减乘除
     *
     * @param a
     * @param b
     * @return
     */
    int multiply(int a, int b);

    /**
     * 除
     *
     * @param a
     * @param b
     * @return
     */
    int divide(int a, int b);

}
