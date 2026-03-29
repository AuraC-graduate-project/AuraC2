package com.server.contestControl.authServer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping({"/", "/login", "/admin/**", "/team/**"})
    public String forward() {
        return "forward:/index.html";
    }
}