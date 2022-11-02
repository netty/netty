package io.netty.decorator;

/**
 * @author lxcecho 909231497@qq.com
 * @since 03.04.2022
 */
public class Decorator implements Component {

    private Component component;

    public Decorator(Component component) {
        this.component = component;
    }

    @Override
    public void doSomething() {
        component.doSomething();
    }
}
