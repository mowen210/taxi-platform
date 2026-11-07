package com.taxi.domain.repository;

import com.taxi.domain.track.TrackPoint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 轨迹仓储。实现可为 TDengine（生产）或 MySQL（本地联调兜底），由 track.provider 切换。
 */
public interface TrackRepository {

    /** 写入一个定位点，失败只记录日志，不影响 808 应答 */
    void append(TrackPoint point);

    /** 最新一个点 */
    Optional<TrackPoint> latest(Long vehicleId);

    /** 时间区间轨迹，按时间升序，最多 limit 条 */
    List<TrackPoint> history(Long vehicleId, LocalDateTime start, LocalDateTime end, int limit);

    /** 当前实现名：tdengine / mysql */
    String provider();
}
