package com.taxi.api.controller;

import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.MediaView;
import com.taxi.application.service.MediaAppService;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 媒体库：列表与文件下载/预览。文件路径由服务端定位，客户端不接触物理路径。
 */
@Tag(name = "媒体库")
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaAppService mediaAppService;

    public MediaController(MediaAppService mediaAppService) {
        this.mediaAppService = mediaAppService;
    }

    @Operation(summary = "分页查询媒体")
    @GetMapping
    @RequiresPerm("media:view")
    public R<PageResult<MediaView>> page(@RequestParam(required = false) Long fleetId,
                                         @RequestParam(required = false) Integer mediaType,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(mediaAppService.page(fleetId, mediaType, page, size));
    }

    @Operation(summary = "媒体文件", description = "download=true 时以附件下载；否则浏览器内联预览")
    @GetMapping("/{id}/file")
    @RequiresPerm("media:view")
    public ResponseEntity<Resource> file(@PathVariable Long id,
                                         @RequestParam(defaultValue = "false") boolean download) {
        MediaAppService.MediaFile file = mediaAppService.locate(id);
        Path path = file.path();
        if (!Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        String contentType;
        try {
            contentType = Files.probeContentType(path);
        } catch (Exception e) {
            contentType = null;
        }
        String name = path.getFileName().toString();
        String disposition = (download ? "attachment" : "inline") + "; filename*=UTF-8''"
                + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(path));
    }
}
