package io.netty.mercury.event.example2;

public class Main {

    public static void main(String[] args) {
        EventSource eventSource = new EventSource();
        eventSource.setName("state source");
        eventSource.setState(1);
        System.out.println("-----------------------------------------------");
        TrueStateListener listener = new TrueStateListener("True listener-->>");
        eventSource.addListener(listener);
        eventSource.changeState(200);


    }
}
