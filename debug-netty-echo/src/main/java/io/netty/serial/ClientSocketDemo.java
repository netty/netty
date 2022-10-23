package io.netty.serial;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * @author lxcecho 909231497@qq.com
 * @since 14:24 23-10-2022
 */
public class ClientSocketDemo {

    public static void main(String[] args) {

        try {
            Socket socket = new Socket("localhost", 9090);
            User user = new User();
            user.setName("lxcecho");
            // 如何传递
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeObject(user);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // TODO
        }
    }

}
