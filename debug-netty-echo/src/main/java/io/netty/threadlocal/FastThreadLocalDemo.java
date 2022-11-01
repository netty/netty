package io.netty.threadlocal;

import io.netty.util.concurrent.FastThreadLocal;

import java.util.concurrent.TimeUnit;

/**
 * @author lxcecho 909231497@qq.com
 * @since 21:58 01-11-2022
 */
public class FastThreadLocalDemo {

    final class FastThreadLocalTest extends FastThreadLocal<Object> {
        @Override
        protected Object initialValue() throws Exception {
            return new Object();
        }
    }

    private final FastThreadLocalTest fastThreadLocalTest;

    public FastThreadLocalDemo() {
        this.fastThreadLocalTest = new FastThreadLocalTest();
    }

    public static void main(String[] args) {
        FastThreadLocalDemo fastThreadLocalDemo = new FastThreadLocalDemo();
        new Thread(() -> {
            Object obj = fastThreadLocalDemo.fastThreadLocalTest.get();
            try {
                for (int i = 0; i < 10; i++) {
                    fastThreadLocalDemo.fastThreadLocalTest.set(new Object());
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            Object obj = fastThreadLocalDemo.fastThreadLocalTest.get();
            try {
                for (int i = 0; i < 10; i++) {
                    // 输出结果都是 true：说明其他线程虽然不断修改共享对象的值，但都不影响当前线程共享对象的值，即实现了线程共享对象功能
                    System.out.println(obj == fastThreadLocalDemo.fastThreadLocalTest.get());
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

}
