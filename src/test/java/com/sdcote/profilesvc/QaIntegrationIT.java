package com.sdcote.profilesvc;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * All the methods in thes integration test are tagged to run as part of the QA group (i.e., these are designed to run in the QA environment)
 */
@Tag("QA")
public class QaIntegrationIT {

    @Test
    void testSomethingCriticalForQa() {
        System.out.println("Running QA integration test: testSomethingCriticalForQa");
        // Your test logic here
    }

    /**
     * This test has an additional tag of "SMOKE" so this will run with a group specification of `qa&smoke`:
     * `mvn verify -Dgroups="qa&smoke"`
     */
    @Test
    @Tag("SMOKE") 
    void testSmokeQaScenario() {
        System.out.println("Running QA and Smoke integration test: testSmokeQaScenario");
        // Your test logic here
    }
}
