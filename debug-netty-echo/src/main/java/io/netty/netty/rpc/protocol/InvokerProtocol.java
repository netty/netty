package io.netty.netty.rpc.protocol;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 自定义传输协议
 *
 * @author lxcecho 909231497@qq.com
 * @since 16:27 29-10-2022
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@Builder
public class InvokerProtocol {

    /**
     * 类名
     */
    private String className;

    /**
     * 函数名称
     */
    private String methodName;

    /**
     * 参数类型
     */
    private Class<?>[] params;

    /**
     * 参数列表
     */
    private Object[] values;

}
