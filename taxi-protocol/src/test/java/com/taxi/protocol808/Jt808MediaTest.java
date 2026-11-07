package com.taxi.protocol808;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Jt808MediaTest {

    private static void u32(ByteArrayOutputStream out, long value) {
        out.write((int) (value >> 24) & 0xFF);
        out.write((int) (value >> 16) & 0xFF);
        out.write((int) (value >> 8) & 0xFF);
        out.write((int) value & 0xFF);
    }

    private static void u16(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    @Test
    @DisplayName("0x0801 多媒体上传：位置块与媒体数据分离")
    void mediaUpload() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        u32(body, 12345);
        body.write(0); // 图片
        body.write(0); // JPEG
        body.write(1);
        body.write(2);
        u32(body, 0);
        u32(body, 0x0000000C);
        u32(body, 31230416);
        u32(body, 121473701);
        u16(body, 10);
        u16(body, 40);
        u16(body, 90);
        body.writeBytes(Bcd.encodeTime(LocalDateTime.of(2026, 11, 7, 23, 50, 0)));
        body.writeBytes("JPEGDATA".getBytes(StandardCharsets.US_ASCII));

        Jt808Bodies.MediaUpload upload = Jt808Bodies.MediaUpload.parse(body.toByteArray());
        assertEquals(12345, upload.mediaId());
        assertEquals(2, upload.channel());
        assertEquals("31.230416", upload.lat().toPlainString());
        assertEquals("121.473701", upload.lng().toPlainString());
        assertEquals(2026, upload.time().getYear());
        assertEquals("JPEGDATA", new String(upload.data(), StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("0x1210 报警附件信息")
    void attachmentInfo() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(String.format("%-32s", "ALARM-001").getBytes(StandardCharsets.US_ASCII));
        body.write(1);
        byte[] name = "front.jpg".getBytes(StandardCharsets.UTF_8);
        body.write(name.length);
        body.writeBytes(name);
        u32(body, 2048);
        Jt808Bodies.AttachmentInfo info = Jt808Bodies.AttachmentInfo.parse(body.toByteArray());
        assertEquals("ALARM-001", info.alarmKey());
        assertEquals(1, info.files().size());
        assertEquals("front.jpg", info.files().get(0).name());
        assertEquals(2048, info.files().get(0).size());
    }

    @Test
    @DisplayName("0x1211 附件分片")
    void attachmentPacket() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] name = "a.jpg".getBytes(StandardCharsets.UTF_8);
        body.write(name.length);
        body.writeBytes(name);
        u32(body, 10);
        byte[] data = "XYZ".getBytes(StandardCharsets.US_ASCII);
        u32(body, data.length);
        body.writeBytes(data);
        Jt808Bodies.AttachmentPacket packet = Jt808Bodies.AttachmentPacket.parse(body.toByteArray());
        assertEquals("a.jpg", packet.fileName());
        assertEquals(10, packet.offset());
        assertArrayEquals(data, packet.data());
    }

    @Test
    @DisplayName("0x1212 附件完成")
    void attachmentComplete() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] name = "a.jpg".getBytes(StandardCharsets.UTF_8);
        body.write(name.length);
        body.writeBytes(name);
        u32(body, 4096);
        Jt808Bodies.AttachmentComplete done = Jt808Bodies.AttachmentComplete.parse(body.toByteArray());
        assertEquals("a.jpg", done.fileName());
        assertEquals(4096, done.size());
    }
}
