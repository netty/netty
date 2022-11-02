package io.netty.decorator;

import lombok.extern.slf4j.Slf4j;

/**
 * @author lxcecho 909231497@qq.com
 * @since 03.04.2022
 */
@Slf4j
public class ConcreteComponent implements Component {
    @Override
    public void doSomething() {
        log.info("功能A");
    }
}
