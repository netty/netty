package io.netty.bio;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author lxcecho 909231497@qq.com
 * @since 9:41 29-10-2022
 */
public class BIOClient {

    public static void main(String[] args) throws Exception {
        // 要和谁进行通信，服务器IP、服务器的端口
        // 一台机器的端口号是有限
        Socket socket = new Socket("localhost", 8090);

        // 输出 O  write();
        // 不管是客户端还是服务端，都有可能 write 和 read
        OutputStream os = socket.getOutputStream();

        String name = UUID.randomUUID().toString();

        System.out.println("Client sent msg: " + name);

        os.write(name.getBytes(StandardCharsets.UTF_8));
        os.close();
        socket.close();
    }
}
