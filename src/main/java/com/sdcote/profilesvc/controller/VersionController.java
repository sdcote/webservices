package com.sdcote.profilesvc.controller;

import org.springframework.boot.actuate.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
public class VersionController {

    private final BuildProperties buildProperties;

    @Autowired
    public VersionController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/version")
    public String getVersion() {
        return "Component Version: " + buildProperties.getVersion();
    }
}