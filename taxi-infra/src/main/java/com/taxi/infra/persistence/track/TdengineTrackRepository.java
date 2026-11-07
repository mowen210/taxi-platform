package com.taxi.infra.persistence.track;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.domain.repository.TrackRepository;
import com.taxi.domain.track.TrackPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 轨迹 TDengine 实现（track.provider=tdengine），走 taosAdapter REST 6041，不依赖本地原生库。
 *
 * <p>写入一车一子表：{@code t_track_{vehicleId} USING st_track TAGS(...)}，
 * 超级表结构见 {@code deploy/tdengine/01_track.sql}。</p>
 *
 * <p>注意：本机当前没有 TDengine 实例，本类只做了 SQL 构造单元测试，端到端联调待时序库就绪。</p>
 */
@Repository
@ConditionalOnProperty(name = "track.provider", havingValue = "tdengine")
public class TdengineTrackRepository implements TrackRepository {

    private static final Logger log = LoggerFactory.getLogger(TdengineTrackRepository.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter TS_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${track.tdengine.url:http://127.0.0.1:6041}")
    private String url;

    @Value("${track.tdengine.database:taxi_track}")
    private String database;

    @Value("${track.tdengine.username:root}")
    private String username;

    @Value("${track.tdengine.password:taosdata}")
    private String password;

    public TdengineTrackRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(TrackPoint p) {
        execute(insertSql(database, p));
    }

    @Override
    public Optional<TrackPoint> latest(Long vehicleId) {
        JsonNode data = query(latestSql(database, vehicleId));
        return data.isEmpty() ? Optional.empty() : Optional.of(toPoint(data.get(0)));
    }

    @Override
    public List<TrackPoint> history(Long vehicleId, LocalDateTime start, LocalDateTime end, int limit) {
        JsonNode rows = query(historySql(database, vehicleId, start, end, limit));
        List<TrackPoint> points = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            points.add(toPoint(row));
        }
        return points;
    }

    @Override
    public String provider() {
        return "tdengine";
    }

    // ------------------------- SQL 构造（可单测） -------------------------

    public static String insertSql(String database, TrackPoint p) {
        String table = "`" + database + "`.`t_track_" + p.vehicleId() + "`";
        String stable = "`" + database + "`.`st_track`";
        String tags = p.vehicleId() + ", '" + escape(p.terminalId()) + "', '" + escape(p.plateNo()) + "'";
        String columns = "(ts, lng, lat, speed, direction, altitude, mileage, acc_state, alarm_flag, status_flag, order_no)";
        String values = "('" + TS.format(p.ts()) + "', " + p.lng().toPlainString() + ", " + p.lat().toPlainString()
                + ", " + p.speed() + ", " + p.direction() + ", " + p.altitude()
                + ", " + (p.mileage() == null ? "NULL" : p.mileage())
                + ", " + (p.accState() == null ? "NULL" : p.accState())
                + ", " + p.alarmFlag() + ", " + p.statusFlag()
                + ", " + (p.orderNo() == null ? "NULL" : "'" + escape(p.orderNo()) + "'") + ")";
        return "INSERT INTO " + table + " USING " + stable + " TAGS (" + tags + ") " + columns + " VALUES " + values;
    }

    public static String historySql(String database, Long vehicleId, LocalDateTime start,
                                    LocalDateTime end, int limit) {
        return "SELECT ts, lng, lat, speed, direction, altitude, mileage, acc_state, alarm_flag, status_flag, order_no"
                + " FROM `" + database + "`.`st_track`"
                + " WHERE vehicle_id = " + vehicleId
                + " AND ts >= '" + TS_SECONDS.format(start) + "'"
                + " AND ts <= '" + TS_SECONDS.format(end) + "'"
                + " ORDER BY ts ASC LIMIT " + Math.max(1, Math.min(limit, 5000));
    }

    public static String latestSql(String database, Long vehicleId) {
        return "SELECT ts, lng, lat, speed, direction, altitude, mileage, acc_state, alarm_flag, status_flag, order_no"
                + " FROM `" + database + "`.`st_track`"
                + " WHERE vehicle_id = " + vehicleId
                + " ORDER BY ts DESC LIMIT 1";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    // ------------------------- HTTP -------------------------

    private void execute(String sql) {
        JsonNode root = post(sql);
        if (root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException("TDengine 写入失败：" + root.path("desc").asText());
        }
    }

    private JsonNode query(String sql) {
        JsonNode root = post(sql);
        if (root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException("TDengine 查询失败：" + root.path("desc").asText());
        }
        return root.path("data");
    }

    private JsonNode post(String sql) {
        try {
            String auth = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/rest/sql"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("TDengine HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TDengine 请求被中断", e);
        } catch (Exception e) {
            log.warn("TDengine 请求失败 url={} 原因={}", url, e.getMessage());
            throw new IllegalStateException("TDengine 请求失败：" + e.getMessage(), e);
        }
    }

    private TrackPoint toPoint(JsonNode row) {
        String orderNo = row.size() > 10 && !row.get(10).isNull() ? row.get(10).asText() : null;
        return new TrackPoint(
                null,
                null,
                null,
                parseTs(row.get(0).asText()),
                BigDecimal.valueOf(row.get(1).asDouble()).setScale(6, java.math.RoundingMode.HALF_UP),
                BigDecimal.valueOf(row.get(2).asDouble()).setScale(6, java.math.RoundingMode.HALF_UP),
                row.get(3).asInt(),
                row.get(4).asInt(),
                row.get(5).asInt(),
                row.get(6).isNull() ? null : row.get(6).asDouble(),
                row.get(7).isNull() ? null : row.get(7).asInt(),
                row.get(8).asLong(),
                row.get(9).asLong(),
                orderNo);
    }

    /** taosAdapter 返回 ISO 时间（可能带 T/Z），统一成本地 LocalDateTime */
    static LocalDateTime parseTs(String raw) {
        String value = raw.trim().replace('T', ' ').replace("Z", "");
        if (value.length() > 19) {
            value = value.substring(0, 19);
        }
        return LocalDateTime.parse(value, TS_SECONDS);
    }
}
