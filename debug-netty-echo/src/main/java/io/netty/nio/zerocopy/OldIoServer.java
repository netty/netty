package io.netty.nio.zerocopy;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author lxcecho 909231497@qq.com
 * @since 29.05.2021
 */
public class OldIoServer {

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(7001);
        while (true) {
            Socket socket = serverSocket.accept();
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
            try {
                byte[] bytes = new byte[4096];
                while (true) {
                    int read = dataInputStream.read(bytes, 0, bytes.length);
                    if (-1 == read) {
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
