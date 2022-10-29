package io.netty.bio.tomcat.servlet;

import io.netty.bio.tomcat.http.EchoRequest;
import io.netty.bio.tomcat.http.EchoResponse;
import io.netty.bio.tomcat.http.EchoServlet;

/**
 * @author lxcecho 909231497@qq.com
 * @since 9:56 29-10-2022
 */
public class FirstServlet extends EchoServlet {


    @Override
    public void doGet(EchoRequest request, EchoResponse response) throws Exception {
        this.doPost(request, response);
    }

    @Override
    public void doPost(EchoRequest request, EchoResponse response) throws Exception {
        response.write("This is First Serlvet");
    }
}
