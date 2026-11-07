package com.taxi.protocol808;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 808 编解码契约测试：转义、校验、消息头、905 报文、分包重组。
 */
class Jt808CodecTest {

    private static final String TERMINAL_12 = "000138000001";

    private static MessageHeader header(int msgId, int seq) {
        byte[] raw = Bcd.encode(TERMINAL_12);
        return new MessageHeader(msgId, 0, 0, Bcd.normalizeTerminalId(TERMINAL_12), raw, seq, 0, false, 0, 0);
    }

    private static byte[] operationStartBody(String businessId, LocalDateTime time,
                                             BigDecimal lng, BigDecimal lat, int state) {
        byte[] body = new byte[Jt808Bodies.OPERATION_START_LEN];
        byte[] id = businessId.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, body, 0, Math.min(id.length, Jt808Bodies.BUSINESS_ID_LEN));
        System.arraycopy(Bcd.encodeTime(time), 0, body, Jt808Bodies.BUSINESS_ID_LEN, 6);
        Bcd.putU32(body, Jt808Bodies.BUSINESS_ID_LEN + 6, lat.movePointRight(6).longValue());
        Bcd.putU32(body, Jt808Bodies.BUSINESS_ID_LEN + 10, lng.movePointRight(6).longValue());
        body[Jt808Bodies.BUSINESS_ID_LEN + 14] = (byte) state;
        return body;
    }

    private static byte[] operationEndBody(String businessId, LocalDateTime time,
                                           BigDecimal lng, BigDecimal lat, BigDecimal mileageKm,
                                           int waiting, BigDecimal amount, BigDecimal discount) {
        byte[] body = new byte[Jt808Bodies.OPERATION_END_LEN];
        byte[] id = businessId.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, body, 0, Math.min(id.length, Jt808Bodies.BUSINESS_ID_LEN));
        System.arraycopy(Bcd.encodeTime(time), 0, body, Jt808Bodies.BUSINESS_ID_LEN, 6);
        Bcd.putU32(body, Jt808Bodies.BUSINESS_ID_LEN + 6, lat.movePointRight(6).longValue());
        Bcd.putU32(body, Jt808Bodies.BUSINESS_ID_LEN + 10, lng.movePointRight(6).longValue());
        Bcd.putU32(body, Jt808Bodies.BUSINESS_ID_LEN + 14, mileageKm.movePointRight(1).longValue());
        Bcd.putU16(body, Jt808Bodies.BUSINESS_ID_LEN + 18, waiting);
        Bcd.putU32(body, Jt808Bodies.BUSINESS_ID_LEN + 20, amount.movePointRight(2).longValue());
        Bcd.putU32(body, Jt808Bodies.BUSINESS_ID_LEN + 24, discount.movePointRight(2).longValue());
        return body;
    }

    @Test
    @DisplayName("整帧编解码：含 0x7E/0x7D 的消息体可正确转义还原")
    void frameRoundTripWithEscapes() {
        byte[] body = new byte[]{0x7E, 0x01, 0x7D, 0x02, 0x7E, 0x7D};
        byte[] frame = Jt808Encoder.message(0x0200, header(0x0200, 7), body);
        assertEquals(Jt808Const.FLAG, frame[0]);
        assertEquals(Jt808Const.FLAG, frame[frame.length - 1]);

        Jt808Message decoded = Jt808Decoder.decode(frame);
        assertArrayEquals(body, decoded.body());
        assertEquals("138000001", decoded.terminalId());
        assertEquals(7, decoded.msgSeq());
    }

    @Test
    @DisplayName("校验码错误必须拒绝")
    void badChecksumRejected() {
        byte[] frame = Jt808Encoder.message(0x0002, header(0x0002, 1), new byte[0]);
        frame[frame.length - 2] ^= 0x0F;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Jt808Decoder.decode(frame));
        assertTrue(error.getMessage().contains("校验码"));
    }

    @Test
    @DisplayName("BCD 时间与坐标编解码")
    void bcdTimeAndCoordinate() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 7, 22, 45, 39);
        assertArrayEquals(new byte[]{0x26, 0x11, 0x07, 0x22, 0x45, 0x39}, Bcd.encodeTime(time));
        assertEquals(time, Bcd.decodeTime(Bcd.encodeTime(time), 0));
        assertEquals("138000001", Bcd.normalizeTerminalId(TERMINAL_12));
    }

    @Test
    @DisplayName("0x0B02 营运开始消息体解析")
    void parseOperationStart() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 7, 22, 50, 1);
        byte[] body = operationStartBody("BIZ20261107001", time,
                new BigDecimal("121.473701"), new BigDecimal("31.230416"), 2);
        Jt808Message message = Jt808Decoder.decode(
                Jt808Encoder.message(Jt808Const.MSG_OPERATION_START, header(Jt808Const.MSG_OPERATION_START, 11), body));

        Jt808Bodies.OperationStart parsed = Jt808Bodies.OperationStart.parse(message.body());
        assertEquals("BIZ20261107001", parsed.businessId());
        assertEquals(time, parsed.startTime());
        assertEquals(0, new BigDecimal("121.473701").compareTo(parsed.lng()));
        assertEquals(0, new BigDecimal("31.230416").compareTo(parsed.lat()));
        assertEquals(2, parsed.taximeterState());
    }

    @Test
    @DisplayName("0x0B05 营运结束消息体解析：里程、等候、金额")
    void parseOperationEnd() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 7, 23, 10, 5);
        byte[] body = operationEndBody("BIZ20261107001", time,
                new BigDecimal("121.500000"), new BigDecimal("31.200000"),
                new BigDecimal("8.6"), 120, new BigDecimal("32.50"), new BigDecimal("2.50"));
        Jt808Bodies.OperationEnd parsed = Jt808Bodies.OperationEnd.parse(body);

        assertEquals("BIZ20261107001", parsed.businessId());
        assertEquals(time, parsed.endTime());
        assertEquals(0, new BigDecimal("8.6").compareTo(parsed.mileageKm()));
        assertEquals(120, parsed.waitingSeconds());
        assertEquals(0, new BigDecimal("32.50").compareTo(parsed.totalAmount()));
        assertEquals(0, new BigDecimal("2.50").compareTo(parsed.discountAmount()));
    }

    @Test
    @DisplayName("消息体过短必须明确报错，不能静默截断")
    void shortBodyRejected() {
        byte[] body = new byte[20];
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Jt808Bodies.OperationEnd.parse(body));
        assertTrue(error.getMessage().contains("长度不足"));
    }

    @Test
    @DisplayName("分包重组：两包收齐后还原完整消息体")
    void subPacketReassembly() {
        byte[] full = operationEndBody("BIZ-SUB-001", LocalDateTime.now(),
                new BigDecimal("121.1"), new BigDecimal("31.1"),
                new BigDecimal("3.2"), 60, new BigDecimal("15.00"), BigDecimal.ZERO);
        int split = 25;
        byte[] first = new byte[split];
        byte[] second = new byte[full.length - split];
        System.arraycopy(full, 0, first, 0, split);
        System.arraycopy(full, split, second, 0, second.length);

        SubPacketAssembler assembler = new SubPacketAssembler(Duration.ofSeconds(30));
        MessageHeader h1 = new MessageHeader(Jt808Const.MSG_OPERATION_END, 1 << 13, 0, "138000001",
                Bcd.encode(TERMINAL_12), 21, split, true, 2, 1);
        MessageHeader h2 = new MessageHeader(Jt808Const.MSG_OPERATION_END, 1 << 13, 0, "138000001",
                Bcd.encode(TERMINAL_12), 21, second.length, true, 2, 2);

        assertNull(assembler.accept(new Jt808Message(h1, first, "AA")));
        Jt808Message merged = assembler.accept(new Jt808Message(h2, second, "BB"));
        assertNotNull(merged);
        assertArrayEquals(full, merged.body());
        assertFalse(merged.header().subPackage());
        assertEquals(0, assembler.pending());
    }

    @Test
    @DisplayName("未收齐的分包超时后清理")
    void expiredSubPacketsEvicted() throws InterruptedException {
        SubPacketAssembler assembler = new SubPacketAssembler(Duration.ofMillis(10));
        MessageHeader h = new MessageHeader(Jt808Const.MSG_OPERATION_END, 1 << 13, 0, "138000001",
                Bcd.encode(TERMINAL_12), 31, 4, true, 3, 1);
        assembler.accept(new Jt808Message(h, new byte[]{1, 2, 3, 4}, "CC"));
        assertEquals(1, assembler.pending());
        Thread.sleep(50);
        assertEquals(1, assembler.evictExpired());
        assertEquals(0, assembler.pending());
    }
}
