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
    private static final String ENVIRONMENT_KEY = "ENVIRON";
    private static final String UNKNOWN_STRING = "UNKNOWN";

    @Autowired
    public VersionController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    /**
     * Retrieve the value of the named environment variable or system property.
     * 
     * <p>Check the environment variables first, and if not there, check the system
     * properties. If in neither, just return "UNKNOWN".
     * 
     * @return the value of the given variable name.
     */
    private static String getVariable(String key) {
        String retval = null;
        if (key != null && !key.isEmpty()) {
            retval = System.getenv(key);

            // If not found in environment variables, try system properties
            if (retval == null) {
                retval = System.getProperty(key);
            }
        }
        if (retval == null) {
            retval = UNKNOWN_STRING;
        }
        return retval;
    }

    @GetMapping("/version")
    public String getVersion() {
        StringBuilder builder = new StringBuilder("Component Version: ");
        builder.append(buildProperties.getVersion());
        builder.append("  Environment: ");
        builder.append(getVariable(ENVIRONMENT_KEY));
        return builder.toString();
    }
    
}