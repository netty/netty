package io.netty.array;


public class ObjectArray<E> extends AbstractArray<E> {

    public ObjectArray(E[] array, int offset, int length) {
        super(array, offset, length);
    }

    @Override
    public E[] array() {
        return (E[]) super.array();
    }
}
