package com.taxi.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理端页面入口：独立登录页与管理首页。
 * <p>静态资源本身仍由 Spring Boot 提供，这里只负责无后缀路径转发。</p>
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping({"/login", "/login/"})
    public String login() {
        return "forward:/login.html";
    }

    @GetMapping({"/admin", "/admin/"})
    public String admin() {
        return "forward:/admin.html";
    }

    @GetMapping({"/base", "/base/"})
    public String baseInfo() {
        return "forward:/base.html";
    }
}
