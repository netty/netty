package io.netty.bio.tomcat.servlet;

import io.netty.bio.tomcat.http.BIORequest;
import io.netty.bio.tomcat.http.BIOResponse;
import io.netty.bio.tomcat.http.BIOServlet;

/**
 * @author lxcecho 909231497@qq.com
 * @since 9:56 29-10-2022
 */
public class BIOFirstServlet extends BIOServlet {


    @Override
    public void doGet(BIORequest request, BIOResponse response) throws Exception {
        this.doPost(request, response);
    }

    @Override
    public void doPost(BIORequest request, BIOResponse response) throws Exception {
        response.write("This is First Serlvet");
    }
}
