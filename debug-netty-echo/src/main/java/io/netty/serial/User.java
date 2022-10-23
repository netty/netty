package io.netty.serial;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author lxcecho 909231497@qq.com
 * @since 14:23 23-10-2022
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private transient int age;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // 加密
        out.writeInt(age);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        age = in.readInt();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}
