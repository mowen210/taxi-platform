#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JT/T 808 + JT/T 905 终端模拟器（Python 3.8+，仅标准库）。

用法:
    python jt808_simulator.py --host 127.0.0.1 --port 7611 \
        --terminal 0138000001 --plate 沪A00001 --business BIZ-20261107-001

流程: 0x0100 注册 -> 0x0102 鉴权 -> 0x0002 心跳 -> 0x0200 位置
      -> 0x0B02 营运开始 -> 0x0B05 营运结束

说明: 本脚本用于联调验证，不实现真实设备的缓存补传、重连退避等策略。
"""
import argparse
import socket
import struct
import sys
import time
from datetime import datetime

FLAG = 0x7E
ESC = 0x7D
GBK = "gbk"


def escape(data: bytes) -> bytes:
    out = bytearray()
    for b in data:
        if b == FLAG:
            out += bytes([ESC, 0x02])
        elif b == ESC:
            out += bytes([ESC, 0x01])
        else:
            out.append(b)
    return bytes(out)


def unescape(data: bytes) -> bytes:
    out = bytearray()
    i = 0
    while i < len(data):
        b = data[i]
        if b == ESC and i + 1 < len(data):
            nxt = data[i + 1]
            out.append(FLAG if nxt == 0x02 else ESC if nxt == 0x01 else nxt)
            i += 2
        else:
            out.append(b)
            i += 1
    return bytes(out)


def bcd(digits: str) -> bytes:
    if len(digits) % 2:
        digits = "0" + digits
    return bytes(int(digits[i:i + 2], 16) for i in range(0, len(digits), 2))


def bcd_time(dt: datetime) -> bytes:
    return bcd(dt.strftime("%y%m%d%H%M%S"))


def fixed(text: str, length: int) -> bytes:
    raw = text.encode("ascii")[:length]
    return raw + b"\x00" * (length - len(raw))


class Simulator:
    def __init__(self, host, port, terminal, plate, business):
        self.host = host
        self.port = port
        self.terminal = terminal
        self.plate = plate
        self.business = business
        self.seq = 0
        self.sock = None

    # ---------- 连接与收发 ----------
    def connect(self):
        self.sock = socket.create_connection((self.host, self.port), timeout=5)
        self.sock.settimeout(5)

    def build_frame(self, msg_id: int, body: bytes) -> bytes:
        terminal12 = self.terminal.rjust(12, "0")[:12]
        header = struct.pack(">HH", msg_id, len(body) & 0x03FF)
        header += bcd(terminal12)
        header += struct.pack(">H", self.seq)
        content = header + body
        checksum = 0
        for b in content:
            checksum ^= b
        return b"\x7e" + escape(content + bytes([checksum])) + b"\x7e"

    def send(self, msg_id: int, body: bytes) -> bytes:
        self.seq = (self.seq + 1) & 0xFFFF
        frame = self.build_frame(msg_id, body)
        print(f">> 0x{msg_id:04X} seq={self.seq} bodyLen={len(body)} hex={frame.hex().upper()}")
        self.sock.sendall(frame)
        return self.receive()

    def receive(self) -> bytes:
        data = bytearray()
        started = False
        deadline = time.time() + 5
        while time.time() < deadline:
            chunk = self.sock.recv(1)
            if not chunk:
                return b""
            b = chunk[0]
            if b == FLAG:
                if started:
                    return unescape(bytes(data[1:]))  # 含校验码，解析按固定偏移
                started = True
            if started:
                data.append(b)
        return b""

    # ---------- 业务报文 ----------
    def register(self):
        body = struct.pack(">HH", 0x001F, 0x0001)
        body += fixed("SIMUL", 5) + fixed("TAXI-808-SIM", 20) + fixed("3800001", 7)
        body += bytes([0x01]) + self.plate.encode(GBK)
        resp = self.send(0x0100, body)
        if not resp:
            raise RuntimeError("注册无应答")
        result = resp[14]
        auth_code = resp[15:-1].decode("ascii", "ignore").strip()  # 末尾 1 字节是校验码
        print(f"0x0100 注册 -> result={result} authCode={auth_code}")
        if result != 0:
            raise RuntimeError(f"注册失败 result={result}")
        return auth_code

    def auth(self, auth_code: str):
        code = auth_code.encode("ascii")
        resp = self.send(0x0102, bytes([len(code)]) + code)
        self.expect(resp, "0x0102 鉴权")

    def heartbeat(self):
        self.expect(self.send(0x0002, b""), "0x0002 心跳")

    def location(self, lng, lat, alarm=0):
        body = struct.pack(">II", alarm, 0x0000000C)
        body += struct.pack(">II", int(lat * 1_000_000), int(lng * 1_000_000))
        body += struct.pack(">HHH", 10, 40, 90)
        body += bcd_time(datetime.now())
        self.expect(self.send(0x0200, body), "0x0200 位置汇报")

    def operation_start(self, lng, lat):
        body = fixed(self.business, 32) + bcd_time(datetime.now())
        body += struct.pack(">II", int(lat * 1_000_000), int(lng * 1_000_000)) + bytes([2])
        self.expect(self.send(0x0B02, body), "0x0B02 营运开始")

    def operation_end(self, lng, lat, mileage, waiting, amount, discount):
        body = fixed(self.business, 32) + bcd_time(datetime.now())
        body += struct.pack(">II", int(lat * 1_000_000), int(lng * 1_000_000))
        body += struct.pack(">I", int(mileage * 10))
        body += struct.pack(">H", waiting)
        body += struct.pack(">I", int(amount * 100))
        body += struct.pack(">I", int(discount * 100))
        self.expect(self.send(0x0B05, body), "0x0B05 营运结束")

    @staticmethod
    def expect(resp: bytes, action: str):
        if not resp:
            raise RuntimeError(f"{action} 无应答")
        result = resp[16]
        print(f"{action} -> 应答消息=0x{struct.unpack('>H', resp[0:2])[0]:04X} result={result}"
              f"{' 成功' if result == 0 else ' 失败'}")
        if result != 0:
            raise RuntimeError(f"{action} 失败 result={result}")


def main():
    parser = argparse.ArgumentParser(description="JT/T 808 + 905 终端模拟器")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=7611)
    parser.add_argument("--terminal", default="0138000001")
    parser.add_argument("--plate", default="沪A00001")
    parser.add_argument("--business", default="BIZ-" + datetime.now().strftime("%Y%m%d%H%M%S"))
    parser.add_argument("--mileage", type=float, default=8.6)
    parser.add_argument("--amount", type=float, default=32.5)
    parser.add_argument("--discount", type=float, default=2.5)
    parser.add_argument("--waiting", type=int, default=120)
    parser.add_argument("--locations", type=int, default=1, help="0x0200 连报点数，用于轨迹回放 demo")
    parser.add_argument("--alarm", default="0", help="0x0200 报警位，支持十进制或 0x 十六进制")
    args = parser.parse_args()

    sim = Simulator(args.host, args.port, args.terminal, args.plate, args.business)
    print(f"连接 {args.host}:{args.port} 终端={args.terminal} 车牌={args.plate} 营运ID={args.business}")
    sim.connect()
    try:
        code = sim.register()
        sim.auth(code)
        sim.heartbeat()
        for i in range(max(1, args.locations)):
            sim.location(121.473701 + i * 0.0012, 31.230416 + i * 0.0008, int(str(args.alarm), 0))
            if i < args.locations - 1:
                time.sleep(0.6)
        sim.operation_start(121.473701, 31.230416)
        sim.operation_end(121.500000, 31.200000, args.mileage, args.waiting, args.amount, args.discount)
        sim.heartbeat()
    finally:
        sim.sock.close()
    print("模拟流程结束")
    return 0


if __name__ == "__main__":
    sys.exit(main())
