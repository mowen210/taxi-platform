package com.taxi.application.service;

import com.taxi.application.dto.MediaView;
import com.taxi.application.support.ScopeGuard;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.alarm.AlarmMedia;
import com.taxi.domain.alarm.AlarmRecord;
import com.taxi.domain.repository.AlarmMediaRepository;
import com.taxi.domain.repository.AlarmRepository;
import com.taxi.domain.repository.DeviceBindingRepository;
import com.taxi.infra.media.LocalMediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报警媒体用例：0x0801 多媒体上传、1078 报警附件分片接收、媒体库查询与文件定位。
 *
 * <p>附件先按「终端 + 文件名」落到媒体目录，0x1212 完成后登记为媒体记录；
 * 报警关联优先用 0x1210 的报警编号，找不到则挂到该车最近 30 分钟的未结束报警。</p>
 */
@Service
public class MediaAppService {

    private static final Logger log = LoggerFactory.getLogger(MediaAppService.class);

    private final AlarmMediaRepository mediaRepository;
    private final AlarmRepository alarmRepository;
    private final DeviceBindingRepository bindingRepository;
    private final LocalMediaStorage storage;
    private final Map<String, Long> attachmentAlarm = new ConcurrentHashMap<>();

    public MediaAppService(AlarmMediaRepository mediaRepository, AlarmRepository alarmRepository,
                           DeviceBindingRepository bindingRepository, LocalMediaStorage storage) {
        this.mediaRepository = mediaRepository;
        this.alarmRepository = alarmRepository;
        this.bindingRepository = bindingRepository;
        this.storage = storage;
    }

    /** 0x0801 多媒体数据上传：落盘并关联报警。 */
    public Long save808Media(String terminalId, Long vehicleId, long mediaId, int mediaType, int format,
                             int event, int channel, byte[] data, LocalDateTime time) {
        AlarmRecord alarm = alarmRepository.findLatestOpenByVehicle(vehicleId, LocalDateTime.now().minusMinutes(30));
        if (alarm == null) {
            log.info("0x0801 无未结束报警，媒体仅落盘不登记 alarmId terminal={} mediaId={}", terminalId, mediaId);
            return null;
        }
        String ext = switch (format) {
            case 0 -> "jpg";
            case 1 -> "tif";
            case 2 -> "mp3";
            case 3 -> "wav";
            case 4 -> "wmv";
            default -> "bin";
        };
        String relative = "808/" + LocalDateTime.now().toLocalDate() + "/" + LocalMediaStorage.safeName(terminalId)
                + "_" + mediaId + "." + ext;
        storage.saveBytes(relative, data);
        AlarmMedia media = new AlarmMedia(null, alarm.id(), alarm.alarmNo(), mediaType, channel, 1,
                mediaId, relative, (long) data.length, null, time, time, time, 2, null);
        Long id = mediaRepository.insert(media);
        log.info("0x0801 媒体归档 alarmNo={} mediaId={} size={}", alarm.alarmNo(), mediaId, data.length);
        return id;
    }

    /** 0x1210 报警附件信息：记录文件名与报警的对应关系。 */
    public void beginAttachment(String terminalId, String alarmKey, List<String> fileNames) {
        AlarmRecord alarm = null;
        if (alarmKey != null && !alarmKey.isBlank()) {
            alarm = alarmRepository.findByBizAlarmId(terminalId, alarmKey.trim());
        }
        if (alarm == null) {
            Long vehicleId = vehicleIdOf(terminalId);
            if (vehicleId != null) {
                alarm = alarmRepository.findLatestOpenByVehicle(vehicleId, LocalDateTime.now().minusMinutes(30));
            }
        }
        if (alarm == null) {
            log.warn("0x1210 找不到关联报警 terminal={} alarmKey={}", terminalId, alarmKey);
            return;
        }
        for (String name : fileNames) {
            attachmentAlarm.put(key(terminalId, name), alarm.id());
        }
    }

    /** 0x1211 附件分片：按偏移写入文件。 */
    public void attachmentChunk(String terminalId, String fileName, long offset, byte[] data) {
        String relative = attachmentPath(terminalId, fileName);
        storage.writeChunk(relative, offset, data);
    }

    /** 0x1212 附件完成：登记媒体记录。 */
    public Long completeAttachment(String terminalId, String fileName, long declaredSize) {
        Long alarmId = attachmentAlarm.remove(key(terminalId, fileName));
        if (alarmId == null) {
            log.warn("0x1212 附件未找到报警关联 terminal={} file={}", terminalId, fileName);
            return null;
        }
        AlarmRecord alarm = alarmRepository.findById(alarmId);
        if (alarm == null) {
            return null;
        }
        String relative = attachmentPath(terminalId, fileName);
        long size = storage.size(relative);
        if (size <= 0) {
            log.warn("0x1212 附件文件不存在 terminal={} file={}", terminalId, fileName);
            return null;
        }
        int mediaType = mediaTypeOf(fileName);
        AlarmMedia media = new AlarmMedia(null, alarm.id(), alarm.alarmNo(), mediaType, null, null, null,
                relative, size, null, alarm.alarmTime(), null, alarm.alarmTime(), 2, null);
        Long id = mediaRepository.insert(media);
        log.info("0x1212 附件归档 alarmNo={} file={} size={} declared={}", alarm.alarmNo(), fileName, size, declaredSize);
        return id;
    }

    public List<MediaView> listByAlarm(Long alarmId) {
        AlarmRecord alarm = alarmRepository.findById(alarmId);
        if (alarm == null) {
            throw BizException.notFound("报警不存在");
        }
        ScopeGuard.requireFleetReadable(alarm.fleetId());
        return mediaRepository.findByAlarm(alarmId).stream().map(m -> toView(m, alarm.plateNo())).toList();
    }

    public PageResult<MediaView> page(Long fleetId, Integer mediaType, int page, int size) {
        Long scope = ScopeGuard.fleetFilter(fleetId);
        PageResult<AlarmMedia> result = mediaRepository.page(scope, mediaType, page, size);
        return PageResult.of(result.total(), result.page(), result.size(),
                result.list().stream().map(m -> toView(m, null)).toList());
    }

    /** 文件定位：校验报警数据权限，防止越权下载。 */
    public MediaFile locate(Long mediaId) {
        AlarmMedia media = mediaRepository.findById(mediaId);
        if (media == null) {
            throw BizException.notFound("媒体不存在");
        }
        AlarmRecord alarm = alarmRepository.findById(media.alarmId());
        if (alarm == null) {
            throw BizException.notFound("报警不存在");
        }
        ScopeGuard.requireFleetReadable(alarm.fleetId());
        return new MediaFile(media, storage.resolve(media.fileUrl()));
    }

    private Long vehicleIdOf(String terminalId) {
        var binding = bindingRepository.findActiveByTerminal(terminalId);
        return binding == null ? null : binding.getVehicleId();
    }

    private String attachmentPath(String terminalId, String fileName) {
        return "attachments/" + LocalMediaStorage.safeName(terminalId) + "/" + LocalMediaStorage.safeName(fileName);
    }

    private static String key(String terminalId, String fileName) {
        return terminalId + "|" + fileName;
    }

    private static int mediaTypeOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
            return 1;
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".wav")) {
            return 2;
        }
        return 3;
    }

    public static MediaView toView(AlarmMedia m, String plateNo) {
        String typeDesc = switch (m.mediaType()) {
            case 1 -> "图片";
            case 2 -> "音频";
            case 3 -> "视频";
            default -> "未知";
        };
        String statusDesc = switch (m.uploadStatus()) {
            case 0 -> "待上传";
            case 1 -> "上传中";
            case 2 -> "已归档";
            case 3 -> "失败";
            default -> "未知";
        };
        return new MediaView(m.id(), m.alarmId(), m.alarmNo(), plateNo, m.mediaType(), typeDesc,
                m.channelNo(), m.streamType(), m.mediaId(), m.fileUrl(), m.fileSize(),
                m.snapshotTime(), m.uploadStatus(), statusDesc);
    }

    public record MediaFile(AlarmMedia media, Path path) {
    }
}
