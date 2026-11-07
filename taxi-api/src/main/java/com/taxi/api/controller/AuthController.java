package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.ratelimit.RateLimit;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.ChangePasswordRequest;
import com.taxi.application.dto.LoginRequest;
import com.taxi.application.dto.LoginResult;
import com.taxi.application.dto.UserProfile;
import com.taxi.application.service.AuthAppService;
import com.taxi.application.service.UserAppService;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@Tag(name = "认证", description = "登录、注销、当前用户、修改口令")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAppService authAppService;
    private final UserAppService userAppService;

    public AuthController(AuthAppService authAppService, UserAppService userAppService) {
        this.authAppService = authAppService;
        this.userAppService = userAppService;
    }

    @Operation(summary = "登录", description = "返回 JWT 与用户档案；连续失败超过阈值临时锁定")
    @RateLimit(name = "auth-login", limit = 10, windowSeconds = 60)
    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return R.ok(authAppService.login(request, clientIp(servletRequest)));
    }

    @Operation(summary = "注销", description = "将当前令牌 jti 加入黑名单")
    @PostMapping("/logout")
    @RequiresPerm
    public R<Void> logout() {
        authAppService.logout();
        return R.ok();
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    @RequiresPerm
    public R<UserProfile> me() {
        return R.ok(authAppService.currentProfile());
    }

    @Operation(summary = "修改本人口令", description = "修改成功后当前令牌失效，需重新登录")
    @PutMapping("/password")
    @RequiresPerm
    @OpLog(module = "系统管理", action = "修改口令", bizType = "USER")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userAppService.changePassword(request);
        return R.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
