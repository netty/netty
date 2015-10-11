package io.netty.buffer;

public class UnpooledDuplicatedByteBuf extends DuplicatedByteBuf {
    public UnpooledDuplicatedByteBuf(ByteBuf buffer) {
        super(buffer);
    }

    // erase refCnting

    @Override
    public int refCnt() {
        return unwrap().refCnt();
    }

    @Override
    public ByteBuf retain() {
        unwrap().retain();
        return this;
    }

    @Override
    public ByteBuf retain(int increment) {
        unwrap().retain(increment);
        return this;
    }

    @Override
    public ByteBuf touch() {
        unwrap().touch();
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        unwrap().touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        if (unwrap().release()) {
            deallocate();
            return true;
        }
        return false;
    }

    @Override
    public boolean release(int decrement) {
        if (unwrap().release(decrement)) {
            deallocate();
            return true;
        }
        return false;
    }

}
