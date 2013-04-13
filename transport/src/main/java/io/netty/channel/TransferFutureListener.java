package io.netty.channel;

import java.util.EventListener;

/**
 * Created with IntelliJ IDEA.
 * User: kerr
 * Date: 13-4-12
 * Time: 下午11:13
 * To change this template use File | Settings | File Templates.
 */
public interface TransferFutureListener extends EventListener {
    void onTransfered(long amount,long total) throws Exception;
}
