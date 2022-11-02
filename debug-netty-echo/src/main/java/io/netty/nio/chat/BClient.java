package io.netty.nio.chat;

/**
 * @author lxcecho 909231497@qq.com
 * @since 17.09.2021
 */
public class BClient {
    public static void main(String[] args) {
        try {
            new ChatClient().startClient("zake");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
