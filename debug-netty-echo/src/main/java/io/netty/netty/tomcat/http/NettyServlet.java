package io.netty.netty.tomcat.http;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10:33 29-10-2022
 */
public abstract class NettyServlet {

    public void service(NettyRequest request, NettyResponse response) throws Exception {

        //由 service 方法来决定，是调用 doGet 或者调用 doPost
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            doGet(request, response);
        } else {
            doPost(request, response);
        }

    }

    public abstract void doGet(NettyRequest request, NettyResponse response) throws Exception;

    public abstract void doPost(NettyRequest request, NettyResponse response) throws Exception;

}
