package com.taxi.application.support;

/**
 * 短信通道抽象。阶段 2 只落接口与日志实现，接入真实通道时替换实现即可，业务代码不改。
 */
public interface SmsSender {

    /**
     * 发送短信。
     *
     * @param mobile  明文手机号（仓储层解密后的值，仅内存）
     * @param content 短信内容
     * @return true 表示已提交成功
     */
    boolean send(String mobile, String content);
}
