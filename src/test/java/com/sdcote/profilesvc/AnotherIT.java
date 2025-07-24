package com.sdcote.profilesvc;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class AnotherIT {

   @Test
   @Tag("LOCAL") // this is intended to run on a developers local workstation
    void testLocalIntegration() {
        System.out.println("Running local integration test");
        // When runing a local test, it is a good practice to acquire test configurations and data from a local file that is NOT 
        // checked into the source reponsitory. These configuration files should be in the developers $HOME directory and system 
        // properties can be used to locate the files. If they are not there, the test can gracefully report the missing 
        // configuration and continue with other tests or the rest of the build.
        // This way, each developer can have a different set of configurations unique to their local set up.
    }

    @Test
    @Tag("QA") // Only this method is tagged "QA"
    void testSpecificQaFeature() {
        System.out.println("Running specific QA feature test");
    }

}
