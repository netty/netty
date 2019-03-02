package io.netty.mercury.event.example1;

import java.util.EventListener;
import java.util.EventObject;

public interface FriendListener extends EventListener {

    public void UpdateMessage(EventObject eventObject, Object... args);

}
