package io.netty.bio.tomcat.http;

import java.io.OutputStream;

/**
 * @author lxcecho 909231497@qq.com
 * @since 9:48 29-10-2022
 */
public class EchoResponse {

    private OutputStream out;

    public EchoResponse(OutputStream out) {
        this.out = out;
    }

    public void write(String s) throws Exception {
        // 用的是 HTTP 协议，输出也要遵循 HTTP 协议，给到一个状态码 200
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\n")
                .append("Content-Type: text/html;\n")
                .append("\r\n")
                .append(s);
        out.write(sb.toString().getBytes());
    }

}
