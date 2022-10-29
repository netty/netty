package io.netty.bio.tomcat;

import io.netty.bio.tomcat.http.EchoRequest;
import io.netty.bio.tomcat.http.EchoResponse;
import io.netty.bio.tomcat.http.EchoServlet;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author lxcecho 909231497@qq.com
 * @since 9:46 29-10-2022
 */
public class EchoTomcat {

    private int port = 8090;

    private ServerSocket serverSocket;

    private Map<String, EchoServlet> servletMaps = new HashMap<>();

    private Properties webXml = new Properties();

    // J2EE 标准
    // Servlet
    // Request
    // Response


    // 1、配置好启动端口，默认 8090  ServerSocket  IP:localhost
    // 2、配置 web.xml 自己写的 Servlet 继承 HttpServlet
    //   servlet-name
    //   servlet-class
    //   url-pattern
    // 3、读取配置，url-pattern  和 Servlet 建立一个映射关系
    //   Map servletMaps

    private void init() {
        // 加载 web.xml 文件,同时初始化 ServletMaps 对象
        try {
            String WEB_INF = this.getClass().getResource("/").getPath();
            FileInputStream fis = new FileInputStream(WEB_INF + "web.properties");

            webXml.load(fis);

            for (Object k : webXml.keySet()) {

                String key = k.toString();
                if (key.endsWith(".url")) {
                    String servletName = key.replaceAll("\\.url$", "");
                    String url = webXml.getProperty(key);
                    String className = webXml.getProperty(servletName + ".className");
                    // 单实例，多线程
                    EchoServlet obj = (EchoServlet) Class.forName(className).newInstance();
                    servletMaps.put(url, obj);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        // 1、加载配置文件，初始化 ServeltMaps
        init();

        try {
            serverSocket = new ServerSocket(this.port);

            System.out.println("Echo Tomcat 已启动，监听的端口是：" + this.port);

            // 2、等待用户请求,用一个死循环来等待用户请求
            while (true) {
                Socket socket = serverSocket.accept();
                // 4、HTTP 请求，发送的数据就是字符串，有规律的字符串（HTTP 协议）
                process(socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void process(Socket socket) throws Exception {
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        // 7、Request(InputStream) / Response(OutputStream)
        EchoRequest request = new EchoRequest(is);
        EchoResponse response = new EchoResponse(os);

        // 5、从协议内容中拿到URL，把相应的 Servlet 用反射进行实例化
        String url = request.getUrl();

        if (servletMaps.containsKey(url)) {
            // 6、调用实例化对象的 service() 方法，执行具体的逻辑 doGet/doPost 方法
            servletMaps.get(url).service(request, response);
        } else {
            response.write("404 - Not Found");
        }

        os.flush();
        os.close();

        is.close();
        socket.close();
    }

    public static void main(String[] args) {
        new EchoTomcat().start();
    }

}
