import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * JT/T 808 + 905 终端模拟器（纯 JDK，单文件运行，无需第三方依赖）。
 *
 * <p>用法：</p>
 * <pre>
 * java simulator/Jt808Simulator.java --host 127.0.0.1 --port 7611 \
 *      --terminal 0138000001 --plate 沪A00001 --business BIZ-20261107-001
 * </pre>
 *
 * <p>流程：0x0100 注册 → 0x0102 鉴权 → 0x0002 心跳 → 0x0200 位置
 * → 0x0B02 营运开始 → 0x0B05 营运结束。每个应答打印结果码与说明。</p>
 */
public class Jt808Simulator {

    private static final byte FLAG = 0x7E;
    private static final byte ESC = 0x7D;
    private static final Charset GBK = Charset.forName("GBK");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String host;
    private final int port;
    private final String terminalId;
    private final String plateNo;
    private final String businessId;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int seq = 0;

    private final boolean skipAuth;
    private final boolean registerOnly;
    private final int locations;
    private final long alarmFlag;

    Jt808Simulator(String host, int port, String terminalId, String plateNo, String businessId,
                   boolean skipAuth, boolean registerOnly, int locations, long alarmFlag) {
        this.host = host;
        this.port = port;
        this.terminalId = terminalId;
        this.plateNo = plateNo;
        this.businessId = businessId;
        this.skipAuth = skipAuth;
        this.registerOnly = registerOnly;
        this.locations = locations;
        this.alarmFlag = alarmFlag;
    }

    public static void main(String[] args) throws Exception {
        String host = arg(args, "--host", "127.0.0.1");
        int port = Integer.parseInt(arg(args, "--port", "7611"));
        String terminal = arg(args, "--terminal", "0138000001");
        String plate = arg(args, "--plate", "沪A00001");
        String business = arg(args, "--business", "BIZ" + LocalDateTime.now().format(TS));

        Jt808Simulator sim = new Jt808Simulator(host, port, terminal, plate, business,
                has(args, "--skip-auth"), has(args, "--register-only"),
                Integer.parseInt(arg(args, "--locations", "1")), parseAlarm(arg(args, "--alarm", "0")));
        sim.run();
    }

    void run() throws Exception {
        System.out.printf("连接 %s:%d 终端=%s 车牌=%s 营运ID=%s%n", host, port, terminalId, plateNo, businessId);
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(5000);
        in = socket.getInputStream();
        out = socket.getOutputStream();

        String authCode = register();
        if (registerOnly) {
            socket.close();
            System.out.println("仅注册模式结束");
            return;
        }
        if (authCode == null) {
            throw new IllegalStateException("注册失败，终止流程");
        }
        if (skipAuth) {
            System.out.println("跳过鉴权，直接发送 0x0B02（预期被拒绝）");
            boolean ok = operationStart(new BigDecimal("121.473701"), new BigDecimal("31.230416"));
            System.out.println(ok ? "未鉴权却处理成功：不符合预期" : "未鉴权已拒绝：符合预期");
            socket.close();
            return;
        }
        boolean authed = auth(authCode);
        if (!authed) {
            throw new IllegalStateException("鉴权失败，终止流程");
        }
        heartbeat();
        int points = Math.max(1, locations);
        for (int i = 0; i < points; i++) {
            location(new BigDecimal("121.473701").add(new BigDecimal("0.0012").multiply(BigDecimal.valueOf(i))),
                    new BigDecimal("31.230416").add(new BigDecimal("0.0008").multiply(BigDecimal.valueOf(i))), alarmFlag);
            if (i < points - 1) {
                Thread.sleep(600);
            }
        }
        operationStart(new BigDecimal("121.473701"), new BigDecimal("31.230416"));
        operationEnd(new BigDecimal("121.500000"), new BigDecimal("31.200000"),
                new BigDecimal("8.6"), 120, new BigDecimal("32.50"), new BigDecimal("2.50"));
        heartbeat();

        socket.close();
        System.out.println("模拟流程结束");
    }

    private String register() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeU16(body, 0x001F);        // 省域 ID
        writeU16(body, 0x0001);        // 市域 ID
        body.write(fixed("SIMUL", 5)); // 制造商
        body.write(fixed("TAXI-808-SIM", 20)); // 终端型号
        body.write(fixed("3800001", 7));       // 终端 ID（7 字节 ASCII）
        body.write(0x01);              // 车牌颜色：蓝
        body.write(plateNo.getBytes(GBK));

        int msgId = 0x0100;
        send(msgId, body.toByteArray());
        byte[] resp = receive();
        if (resp == null) {
            System.out.println("0x0100 注册：无应答");
            return null;
        }
        int rMsgId = u16(resp, 0);
        int rSeq = u16(resp, 10);
        int result = resp[14] & 0xFF;
        // 末尾 1 字节是校验码，不属于鉴权码
        int codeLen = Math.max(0, resp.length - 15 - 1);
        String code = codeLen > 0 ? new String(resp, 15, codeLen, StandardCharsets.US_ASCII).trim() : "";
        System.out.printf("0x0100 注册 → 0x%04X seq=%d result=%d authCode=%s%n", rMsgId, rSeq, result, code);
        return result == 0 ? code : null;
    }

    private boolean auth(String authCode) throws IOException {
        byte[] code = authCode.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(code.length);
        body.write(code);
        send(0x0102, body.toByteArray());
        return expectSuccess("0x0102 鉴权");
    }

    private void heartbeat() throws IOException {
        send(0x0002, new byte[0]);
        expectSuccess("0x0002 心跳");
    }

    private void location(BigDecimal lng, BigDecimal lat, long alarm) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeU32(body, alarm);
        writeU32(body, 0x0000000CL); // 定位有效 + 北纬 + 东经
        writeU32(body, lat.movePointRight(6).longValue());
        writeU32(body, lng.movePointRight(6).longValue());
        writeU16(body, 10);   // 海拔
        writeU16(body, 40);   // 速度 km/h
        writeU16(body, 90);   // 方向
        body.write(bcdTime(LocalDateTime.now()));
        send(0x0200, body.toByteArray());
        expectSuccess("0x0200 位置汇报");
    }

    private boolean operationStart(BigDecimal lng, BigDecimal lat) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(fixed(businessId, 32));
        body.write(bcdTime(LocalDateTime.now()));
        writeU32(body, lat.movePointRight(6).longValue());
        writeU32(body, lng.movePointRight(6).longValue());
        body.write(2); // 重车
        send(0x0B02, body.toByteArray());
        return expectSuccess("0x0B02 营运开始");
    }

    private void operationEnd(BigDecimal lng, BigDecimal lat, BigDecimal mileage,
                              int waitingSec, BigDecimal amount, BigDecimal discount) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(fixed(businessId, 32));
        body.write(bcdTime(LocalDateTime.now()));
        writeU32(body, lat.movePointRight(6).longValue());
        writeU32(body, lng.movePointRight(6).longValue());
        writeU32(body, mileage.movePointRight(1).longValue());
        writeU16(body, waitingSec);
        writeU32(body, amount.movePointRight(2).longValue());
        writeU32(body, discount.movePointRight(2).longValue());
        send(0x0B05, body.toByteArray());
        expectSuccess("0x0B05 营运结束");
    }

    private boolean expectSuccess(String action) throws IOException {
        byte[] resp = receive();
        if (resp == null) {
            System.out.println(action + "：无应答");
            return false;
        }
        int msgId = u16(resp, 0);
        int seqNo = u16(resp, 10);
        int replyTo = u16(resp, 14);
        int result = resp[16] & 0xFF;
        System.out.printf("%s → 0x%04X seq=%d 应答消息=0x%04X result=%d%s%n",
                action, msgId, seqNo, replyTo, result, result == 0 ? " 成功" : " 失败");
        return result == 0;
    }

    private void send(int msgId, byte[] body) throws IOException {
        seq = (seq + 1) & 0xFFFF;
        byte[] frame = frame(msgId, seq, body);
        out.write(frame);
        out.flush();
        System.out.printf(">> 0x%04X seq=%d bodyLen=%d hex=%s%n", msgId, seq, body.length, hex(frame));
    }

    private byte[] receive() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + 5000;
        boolean started = false;
        while (System.currentTimeMillis() < deadline) {
            int b = in.read();
            if (b < 0) {
                return null;
            }
            if (b == (FLAG & 0xFF)) {
                if (started) {
                    byte[] frame = buffer.toByteArray();
                    return unescape(Arrays.copyOfRange(frame, 1, frame.length));
                }
                started = true;
            }
            if (started) {
                buffer.write(b);
            }
        }
        return null;
    }

    private byte[] frame(int msgId, int seqNo, byte[] body) {
        byte[] terminal = bcd(terminalId.length() >= 12 ? terminalId.substring(0, 12)
                : String.format("%12s", terminalId).replace(' ', '0'));
        byte[] content = new byte[12 + body.length];
        putU16(content, 0, msgId);
        putU16(content, 2, body.length & 0x03FF);
        System.arraycopy(terminal, 0, content, 4, 6);
        putU16(content, 10, seqNo);
        System.arraycopy(body, 0, content, 12, body.length);

        byte checksum = 0;
        for (byte value : content) {
            checksum ^= value;
        }
        byte[] withCheck = Arrays.copyOf(content, content.length + 1);
        withCheck[content.length] = checksum;
        byte[] escaped = escape(withCheck);
        byte[] outFrame = new byte[escaped.length + 2];
        outFrame[0] = FLAG;
        System.arraycopy(escaped, 0, outFrame, 1, escaped.length);
        outFrame[outFrame.length - 1] = FLAG;
        return outFrame;
    }

    private static byte[] escape(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte b : data) {
            if (b == FLAG) {
                out.write(ESC);
                out.write(0x02);
            } else if (b == ESC) {
                out.write(ESC);
                out.write(0x01);
            } else {
                out.write(b);
            }
        }
        return out.toByteArray();
    }

    private static byte[] unescape(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            if (data[i] == ESC && i + 1 < data.length) {
                byte next = data[++i];
                out.write(next == 0x02 ? FLAG : next == 0x01 ? ESC : next);
            } else {
                out.write(data[i]);
            }
        }
        byte[] result = out.toByteArray();
        return result;
    }

    private static byte[] fixed(String text, int length) {
        byte[] out = new byte[length];
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, out, 0, Math.min(bytes.length, length));
        return out;
    }

    private static byte[] bcd(String digits) {
        String value = digits.length() % 2 == 0 ? digits : "0" + digits;
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (((value.charAt(i * 2) - '0') << 4) | (value.charAt(i * 2 + 1) - '0'));
        }
        return out;
    }

    private static byte[] bcdTime(LocalDateTime time) {
        return bcd(String.format("%02d%02d%02d%02d%02d%02d", time.getYear() % 100,
                time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute(), time.getSecond()));
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        out.write((int) ((value >> 24) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) (value & 0xFF));
    }

    private static void putU16(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String arg(String[] args, String name, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return def;
    }

    private static boolean has(String[] args, String name) {
        for (String value : args) {
            if (name.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static long parseAlarm(String value) {
        String v = value == null ? "0" : value.trim();
        return v.startsWith("0x") || v.startsWith("0X")
                ? Long.parseLong(v.substring(2), 16) : Long.parseLong(v);
    }
}
