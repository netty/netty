package io.netty.incubator.codec.quic;

import java.util.function.BiConsumer;

abstract class BoringSSLPrivateKeyMethodTask extends BoringSSLTask {

    private final BoringSSLPrivateKeyMethod method;

    // Will be accessed via JNI.
    private byte[] resultBytes;

    BoringSSLPrivateKeyMethodTask(long ssl, BoringSSLPrivateKeyMethod method) {
        super(ssl);
        this.method = method;
    }


    @Override
    protected final void runTask(long ssl, TaskCallback callback) {
        runMethod(ssl, method, (result, error) -> {
            if (result == null || error != null) {
                callback.onResult(ssl, -1);
            } else {
                resultBytes = result;
                callback.onResult(ssl, 1);
            }
        });
    }

    protected abstract void runMethod(long ssl, BoringSSLPrivateKeyMethod method,
                                      BiConsumer<byte[], Throwable> callback);
}
