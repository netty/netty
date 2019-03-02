package io.netty.mercury.event.example1;

import java.util.EventObject;
import java.util.Iterator;
import java.util.Set;

public class WeChatResource {

    private String name;
    String state = "kenny";
    private Set<FriendListener> friendListeners;

    public WeChatResource(String name, String state, Set<FriendListener> friendListeners) {
        this.name = name;
        this.state = state;
        this.friendListeners = friendListeners;
    }

    public void notifyListener() {
        Iterator iterator = friendListeners.iterator();
        while (iterator.hasNext()) {
            FriendListener friendListener = (FriendListener) iterator.next();
            EventObject eventObject = new WeChatMessageEvent(this, "wechat");
            friendListener.UpdateMessage(eventObject, "String");
        }
    }

    public void setState(String state) {
        this.state = state;
        notifyListener();
    }

    public void addListener(FriendListener friendListener) {
        friendListeners.add(friendListener);
    }

    public String getName() {
        return name;
    }


}
