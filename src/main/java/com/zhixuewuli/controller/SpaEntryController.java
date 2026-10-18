package com.zhixuewuli.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** GET page entries must coexist with the POST authentication API at these URLs. */
@Controller
public class SpaEntryController {
    @GetMapping({"/auth/login", "/auth/register"})
    public String authenticationPage() {
        return "forward:/index.html";
    }
}
