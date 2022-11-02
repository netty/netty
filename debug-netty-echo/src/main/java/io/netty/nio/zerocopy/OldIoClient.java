package io.netty.nio.zerocopy;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;

/**
 * @author lxcecho 909231497@qq.com
 * @since 29.05.2021
 */
public class OldIoClient {

    public static void main(String[] args) throws Exception {

        Socket socket = new Socket("localhost", 7001);
        String filename = "protoc-3.6.1-win32.zip";
        InputStream inputStream = new FileInputStream(filename);
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

        byte[] buffer = new byte[4096];
        long read;
        long total = 0;
        long startTime = System.currentTimeMillis();
        while ((read = inputStream.read(buffer)) >= 0) {
            total += read;
            dataOutputStream.write(buffer);
        }

        System.out.println("发送总字节数 " + total + "，耗时 " + (System.currentTimeMillis() - startTime));

        dataOutputStream.close();
        socket.close();
        inputStream.close();
    }

}
