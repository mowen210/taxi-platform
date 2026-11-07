package com.taxi.protocol808;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分包重组：按「终端号 + 消息ID + 流水号」聚合，收齐后返回完整消息。
 * <p>超过 {@code timeout} 未收齐的分包会被清理，避免内存泄漏。单机内存实现，
 * 与本阶段单节点部署一致；集群化时需改为 Redis。</p>
 */
public class SubPacketAssembler {

    private record Entry(byte[][] parts, int received, Instant createdAt) {
    }

    private final Map<String, Entry> packets = new ConcurrentHashMap<>();
    private final Duration timeout;

    public SubPacketAssembler(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * 收下一个分包。
     *
     * @return 收齐时的完整消息；未收齐返回 {@code null}
     */
    public Jt808Message accept(Jt808Message partial) {
        MessageHeader header = partial.header();
        if (!header.subPackage()) {
            return partial;
        }
        if (header.totalPackets() <= 0 || header.packetIndex() < 1 || header.packetIndex() > header.totalPackets()) {
            throw new IllegalArgumentException("分包序号非法：" + header.packetIndex() + "/" + header.totalPackets());
        }
        String key = header.terminalId() + ':' + header.msgId() + ':' + header.msgSeq();
        Entry entry = packets.computeIfAbsent(key,
                k -> new Entry(new byte[header.totalPackets()][], 0, Instant.now()));
        synchronized (entry) {
            if (entry.parts()[header.packetIndex() - 1] == null) {
                entry.parts()[header.packetIndex() - 1] = partial.body();
                entry = new Entry(entry.parts(), entry.received() + 1, entry.createdAt());
                packets.put(key, entry);
            }
            if (entry.received() < header.totalPackets()) {
                return null;
            }
            packets.remove(key);
            int total = 0;
            for (byte[] part : entry.parts()) {
                total += part.length;
            }
            byte[] body = new byte[total];
            int offset = 0;
            for (byte[] part : entry.parts()) {
                System.arraycopy(part, 0, body, offset, part.length);
                offset += part.length;
            }
            MessageHeader merged = new MessageHeader(header.msgId(), header.props() & ~(1 << 13),
                    header.protocolVersion(), header.terminalId(), header.terminalIdRaw(), header.msgSeq(),
                    body.length, false, 0, 0);
            return new Jt808Message(merged, body, partial.rawHex());
        }
    }

    /** 清理超时未收齐的分包，返回清理条数 */
    public int evictExpired() {
        Instant deadline = Instant.now().minus(timeout);
        int before = packets.size();
        packets.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(deadline));
        return before - packets.size();
    }

    public int pending() {
        return packets.size();
    }
}
