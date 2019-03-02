package io.netty.mercury.event.example2;

public class TrueStateListener implements StateListener {

    private String name;

    public TrueStateListener(String name) {
        this.name = name;
    }

    @Override
    public void handle(MyEvent event) {
        System.out.println("----------------------------------");
        System.out.println(name + " prepare to handle event");
        int state = event.getState();
        System.out.println(name + " handle the state: " + state);
        System.out.println("----------------------------------");
    }
}
