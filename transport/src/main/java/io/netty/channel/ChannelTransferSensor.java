package io.netty.channel;

/**
 * Created with IntelliJ IDEA.
 * User: kerr
 * Date: 13-4-12
 * Time: 下午11:20
 * To change this template use File | Settings | File Templates.
 */
public interface ChannelTransferSensor {
    ChannelTransferSensor addTransferFutureListner(TransferFutureListener listener);
    ChannelTransferSensor addTransferFutureListeners(TransferFutureListener ... listeners);
    ChannelTransferSensor removeTransferFutureListener(TransferFutureListener listener);
    ChannelTransferSensor removeTransferFutureListeners(TransferFutureListener ... listeners);
}
