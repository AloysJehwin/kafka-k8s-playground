package com.eventflow.webbff.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public Map<String, Object> me(@AuthenticationPrincipal OAuth2User principal) {
        return Map.of(
            "sub", principal.getAttribute("sub"),
            "email", principal.getAttribute("email"),
            "name", principal.getAttribute("name"),
            "picture", principal.getAttribute("picture")
        );
    }
}
