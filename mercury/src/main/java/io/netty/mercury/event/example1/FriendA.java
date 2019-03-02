package io.netty.mercury.event.example1;

import java.util.EventObject;

public class FriendA implements FriendListener {

    private String name;

    public String getName() {
        return name;
    }

    public FriendA(String name) {
        this.name = name;
    }

    @Override
    public void UpdateMessage(EventObject eventObject, Object[] args) {
        WeChatResource weChatResource = (WeChatResource) eventObject.getSource();
        System.out.println(this.getName() + " update " + weChatResource.getName() + "'s messages");
    }
}
