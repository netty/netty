package io.netty.netty.tomcat.http;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;
import java.util.Map;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10:35 29-10-2022
 */
public class NettyRequest {

    private ChannelHandlerContext ctx;

    private HttpRequest req;

    public NettyRequest(ChannelHandlerContext ctx, HttpRequest req) {
        this.ctx = ctx;
        this.req = req;
    }

    public String getUrl() {
        return req.uri();
    }

    public String getMethod() {
        return req.method().name();
    }

    public Map<String, List<String>> getParameters() {
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.uri());
        return queryStringDecoder.parameters();
    }

    public String getParameters(String name) {
        Map<String, List<String>> params = getParameters();
        List<String> list = params.get(name);
        if (null == list) {
            return null;
        } else {
            return list.get(0);
        }
    }

}
