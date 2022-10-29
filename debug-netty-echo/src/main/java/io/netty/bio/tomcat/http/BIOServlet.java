package io.netty.bio.tomcat.http;

/**
 * @author lxcecho 909231497@qq.com
 * @since 9:47 29-10-2022
 */
public abstract class BIOServlet {

    /**
     * 由 service() 方法决定是调用  doGet 还是 doPost 方法
     *
     * @param request
     * @param response
     * @throws Exception
     */
    public void service(BIORequest request, BIOResponse response) throws Exception {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            doGet(request, response);
        } else {
            doPost(request, response);
        }
    }

    public abstract void doGet(BIORequest request, BIOResponse response) throws Exception;

    public abstract void doPost(BIORequest request, BIOResponse response) throws Exception;

}
