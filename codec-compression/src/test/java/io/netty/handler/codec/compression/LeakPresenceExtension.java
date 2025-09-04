package io.netty.handler.codec.compression;

import io.netty.util.LeakPresenceDetector;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

// TODO: move to shared module
public class LeakPresenceExtension implements AfterEachCallback {
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        LeakPresenceDetector.check();
    }
}
