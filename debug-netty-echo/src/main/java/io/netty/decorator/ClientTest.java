package io.netty.decorator;

import org.junit.Test;

/**
 * @author lxcecho 909231497@qq.com
 * @since 03.04.2022
 */
public class ClientTest {


    @Test
    public void testDecorator() {
        Component component = new ConcreteDecorator1(new ConcreteDecorator1(new ConcreteComponent()));
        component.doSomething();
    }

}
