package io.netty.mercury.event.example1;

import java.util.HashSet;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        FriendListener friendA1 = new FriendA("FriandA1");
        FriendListener friendA2 = new FriendA("FriandA2");
        Set<FriendListener> friendListeners = new HashSet<FriendListener>();
        friendListeners.add(friendA1);
        friendListeners.add(friendA2);
        WeChatResource weChatResource = new WeChatResource("kenny", "demo", (Set<FriendListener>) friendListeners);
        weChatResource.setState("kenny");

    }
}
