package io.netty.bio.tomcat.http;


import java.io.IOException;
import java.io.InputStream;

/**
 * @author lxcecho 909231497@qq.com
 * @since 9:49 29-10-2022
 */
public class EchoRequest {

    private String method;

    private String url;

    public EchoRequest(InputStream in) {
        try {
            // 拿到 HTTP 协议内容
            String content = "";
            byte[] buff = new byte[1024];
            int len = 0;
            if ((len = in.read(buff)) > 0) {
                content = new String(buff, 0, len);
            }

            String line = content.split("\\n")[0];
            String[] arr = line.split("\\s");

            this.method = arr[0];
            this.url = arr[1].split("\\?")[0];
            System.out.println("content: "+content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUrl() {
        return this.url;
    }

    public String getMethod() {
        return this.method;
    }

}
