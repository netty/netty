package io.netty.serial;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15:04 23-10-2022
 */
public class SerializerMain {

    public static void main(String[] args) {
        /*ISerializable serializable = new JavaSerializer();
        User user = new User();
        user.setName("lxcecho");
        user.setAge(18);

        byte[] bytes = serializable.serialize(user);
        System.out.println(bytes);

        for (byte aByte : bytes) {
            System.out.print(aByte+" ");
        }
        System.out.println();

        User userDeserialize = serializable.deserialize(bytes);
        System.out.println(userDeserialize);*/

        User user = new User();
        user.setName("lxcecho");
        user.setAge(18);
        ISerializer serializer = new HessianSerializer();
        byte[] bytes = serializer.serialize(user);
        System.out.println(bytes.length);
        System.out.println(new String(bytes));
        User userRever = serializer.deserialize(bytes);
        System.out.println(userRever);

    }
}
