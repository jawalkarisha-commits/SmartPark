package com.smartpark.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalController {

    @GetMapping({
        "/login", "/register", "/forgot-password", "/admin/login",
        "/app/**", "/admin/**", "/owner/**"
    })
    public String forwardToPortal() {
        return "forward:/index.html";
    }
}
