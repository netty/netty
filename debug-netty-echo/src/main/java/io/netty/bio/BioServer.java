package io.netty.bio;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 同步阻塞 IO 模型
 *
 * @author lxcecho 909231497@qq.com
 * @since 9:35 29-10-2022
 */
public class BioServer {

    ServerSocket serverSocket;

    public BioServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("BIO Server is completing on " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始监听
     *
     * @throws Exception
     */
    public void listen() throws Exception {
        // 循环监听
        while (true) {
            // 等待客户端连接，阻塞方法
            // Socket数据发送者在服务端的引用
            Socket socket = serverSocket.accept();
            System.out.println(socket.getPort());

            // 对方法数据给我了，读 Input
            InputStream is = socket.getInputStream();

            // 网络客户端把数据发送到网卡，机器所得到的数据读到了 JVM 内中
            byte[] buff = new byte[1024];
            int len = is.read(buff);
            if (len > 0) {
                String msg = new String(buff, 0, len);
                System.out.println("receive:" + msg);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new BioServer(8090).listen();
    }

}
