package io.netty.serial;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15:40 23-10-2022
 */
public class ProtobufMain {

    public static void main(String[] args) throws InvalidProtocolBufferException {

        UserProto.User user = UserProto.User.newBuilder().setName("lxcecho").setAge(599).build();
        ByteString bytes = user.toByteString();
        System.out.println(bytes.size()); // 7

        for (byte bt : bytes.toByteArray()) {
            System.out.print(bt + " ");
        }
        System.out.println();
        UserProto.User userRever = UserProto.User.parseFrom(bytes);
        System.out.println(userRever);

    }

}
