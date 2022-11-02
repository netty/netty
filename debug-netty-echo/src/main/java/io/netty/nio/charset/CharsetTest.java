package io.netty.nio.charset;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Map;
import java.util.Set;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15.09.2021
 */
public class CharsetTest {

    @Test
    public void testCharset() throws Exception {
        // 通过编码类型获取charset 对象
        Charset charset = Charset.forName("UTF-8");

        // 获取编码器对象
        CharsetEncoder encoder = charset.newEncoder();

        // 创建缓冲区
        CharBuffer charBuffer = CharBuffer.allocate(1024);
        charBuffer.put("charset test 测试");
        charBuffer.flip();

        // 编码
        ByteBuffer byteBuffer = encoder.encode(charBuffer);
        System.out.println("编码之后结果:");
        for (int i = 0; i < byteBuffer.limit(); i++) {
            System.out.println(byteBuffer.get());
        }

        // 获取解码器对象
        byteBuffer.flip();
        CharsetDecoder decoder = charset.newDecoder();

        //解码
        CharBuffer charBuffer1 = decoder.decode(byteBuffer);
        System.out.println("解码之后结果:");
        System.out.println(charBuffer1);

        // 使用 GBK 解码
        Charset gbk = Charset.forName("GBK");
        byteBuffer.flip();
        CharBuffer decode = gbk.decode(byteBuffer);
        System.out.println("使用GBK进行解码:");
        System.out.println(decode);

        // 获取虚拟机默认的编码方式
        Charset defaultCharset = Charset.defaultCharset();
        System.out.println(defaultCharset);

        // 判断是否支持该编码类型
        boolean supported = Charset.isSupported("GBK");
        System.out.println(supported);

        // 获取系统所支持所有编码方式
        Map<String, Charset> map = Charset.availableCharsets();
        Set<Map.Entry<String, Charset>> set = map.entrySet();
        for (Map.Entry<String, Charset> entry : set) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
    }

}
