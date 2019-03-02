package io.netty.mercury.atomic;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Arrays;

public class UnsafeExam {
    public static void main(String[] args) throws IllegalAccessException {
        Unsafe unsafe = null;
        Field f = null;
        try {
            f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe= (Unsafe) f.get(null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        if (unsafe !=null){
            try {
                Test test = (Test) unsafe.allocateInstance(Test.class);
                long x_addr = unsafe.objectFieldOffset(Test.class.getDeclaredField("x"));
                unsafe.getAndSetInt(test,x_addr,100);
                System.out.println(test.getX());
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
        // tongguo dizhi  caozuo  shuzu
        if (unsafe!=null){
            final int INT_BYTES =4;
            int[] data = new int[10];
            System.out.println(Arrays.toString(data));
            long arrayBaseOffset = unsafe.arrayBaseOffset(int[].class);
            System.out.println("Array address is :"+arrayBaseOffset);
            unsafe.putInt(data,arrayBaseOffset,47);
            unsafe.putInt(data,arrayBaseOffset+INT_BYTES*8,43);
            System.out.println(Arrays.toString(data));

            //CAS
            if (unsafe != null){
                try {
                    Test test = (Test) unsafe.allocateInstance(Test.class);
                    long x_addr = unsafe.objectFieldOffset(Test.class.getDeclaredField("x"));
                    unsafe.getAndSetInt(test, x_addr, 500);
                    unsafe.compareAndSwapInt(test,x_addr,500,78);
                    System.out.println("After CAS:"+ test.getX());

                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
