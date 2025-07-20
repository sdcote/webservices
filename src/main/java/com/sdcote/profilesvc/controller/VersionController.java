package com.sdcote.profilesvc.controller;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * http://localhost:8080/version
 */
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