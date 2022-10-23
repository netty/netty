package io.netty.serial;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.nio.charset.StandardCharsets;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15:38 23-10-2022
 */
public class XmlSerializer implements ISerializer {

    XStream stream = new XStream(new DomDriver());

    @Override
    public <T> byte[] serialize(T obj) {
        return stream.toXML(obj).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(byte[] data) {
        return (T) stream.fromXML(new String(data));
    }
}
