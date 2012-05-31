package io.netty.channel;

public class AbstractChannelHandler implements ChannelHandler {

    // Not using volatile because it's used only for a sanity check.
    boolean added;

    final boolean isSharable() {
        return getClass().isAnnotationPresent(Sharable.class);
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }
}
