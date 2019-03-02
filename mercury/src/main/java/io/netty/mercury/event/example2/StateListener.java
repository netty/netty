package io.netty.mercury.event.example2;

import java.util.EventListener;

public interface StateListener extends EventListener {

    void handle(MyEvent event);
}
