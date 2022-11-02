package io.netty.decorator;

import lombok.extern.slf4j.Slf4j;

/**
 * @author lxcecho 909231497@qq.com
 * @since 03.04.2022
 */
@Slf4j
public class ConcreteDecorator1 extends Decorator {

    public ConcreteDecorator1(Component component) {
        super(component);
    }

    @Override
    public void doSomething() {
        super.doSomething();
        this.doAnotherThing();
    }

    private void doAnotherThing() {
        log.info("功能B");
    }
}
