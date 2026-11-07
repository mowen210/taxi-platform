package com.taxi.application.dto;

/**
 * 证件到期扫描结果。用于人工触发接口回显与定时任务日志。
 *
 * @param scannedCertificates 扫描到的到期/临期证件数
 * @param warnedCertificates  置为「预警」的证件数
 * @param expiredCertificates 置为「过期」的证件数
 * @param scannedVehicles     扫描到的保险/年检临期车辆数
 * @param noticesCreated      新增通知条数
 * @param smsSent             短信实际发送成功条数
 * @param smsSkipped          未发送短信条数（无手机号或通道未启用）
 */
public record WarnScanResult(
        int scannedCertificates,
        int warnedCertificates,
        int expiredCertificates,
        int scannedVehicles,
        int noticesCreated,
        int smsSent,
        int smsSkipped) {
}
