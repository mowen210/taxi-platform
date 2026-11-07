package com.taxi.api.protocol;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线终端与 Netty Channel 的映射，供后续指令下发（0x8300 等）使用。
 * <p>本阶段只维护内存映射；集群化后需改为 Redis + 节点间转发。</p>
 */
@Component
public class TerminalSessionRegistry {

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    public void bind(String terminalId, Channel channel) {
        channels.put(terminalId, channel);
    }

    public Channel get(String terminalId) {
        return channels.get(terminalId);
    }

    public void removeByChannel(Channel channel) {
        channels.entrySet().removeIf(e -> e.getValue() == channel);
    }

    public int onlineCount() {
        return channels.size();
    }
}
