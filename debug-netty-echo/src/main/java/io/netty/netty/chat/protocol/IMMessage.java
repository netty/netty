package io.netty.netty.chat.protocol;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 自定义协议消息内容，即消息实体类
 *
 * @author lxcecho 909231497@qq.com
 * @since 20:17 29-10-2022
 */
@Getter
@Setter
@ToString
public class IMMessage {

    /**
     * IP 地址+端口
     */
    private String addr;

    /**
     * 命令类型【LOGIN|LOGOUT|SYSTEM】
     */
    private String cmd;

    /**
     * 命令发送时间
     */
    private long time;

    /**
     * 当前在线人数
     */
    private int online;

    /**
     * 发送者
     */
    private String sender;

    /**
     * 接收者
     */
    private String receiver;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 终端
     */
    private String terminal;

    public IMMessage() {
    }

    public IMMessage(String cmd, long time, int online, String content) {
        this.cmd = cmd;
        this.time = time;
        this.online = online;
        this.content = content;
        this.terminal = terminal;
    }

    public IMMessage(String cmd, String terminal, long time, String sender) {
        this.cmd = cmd;
        this.time = time;
        this.sender = sender;
        this.terminal = terminal;
    }


    public IMMessage(String cmd, long time, String sender, String content) {
        this.cmd = cmd;
        this.time = time;
        this.sender = sender;
        this.content = content;
        this.terminal = terminal;
    }

}
