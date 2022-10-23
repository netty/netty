package io.netty.serial;

import java.io.*;

/**
 * @author lxcecho 909231497@qq.com
 * @since 14:46 23-10-2022
 */
public class JavaSerializer implements ISerializer {

    /**
     * 序列化
     *
     * @param obj
     * @param <T>
     * @return
     */
    @Override
    public <T> byte[] serialize(T obj) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

//            ObjectOutputStream objectOutputStream=new ObjectOutputStream(new FileOutputStream("user"));

            objectOutputStream.writeObject(obj);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    /**
     * 反序列化
     *
     * @param data
     * @param <T>
     * @return
     */
    @Override
    public <T> T deserialize(byte[] data) {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

//            ObjectInputStream objectInputStream=new ObjectInputStream(new FileInputStream(new File("user")));

            return (T)objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
