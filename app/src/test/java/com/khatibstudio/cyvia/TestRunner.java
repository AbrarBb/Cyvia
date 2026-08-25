package com.khatibstudio.cyvia;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class TestRunner {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  RUNNING CYVIA COMPREHENSIVE USER PERSONA TESTS ");
        System.out.println("=================================================");

        Result result = JUnitCore.runClasses(CyviaUserPersonaTest.class);

        System.out.println("\n-------------------------------------------------");
        System.out.println("Total tests executed: " + result.getRunCount());
        System.out.println("Total failures:       " + result.getFailureCount());
        System.out.println("Total ignored:        " + result.getIgnoreCount());
        System.out.println("Execution time:       " + result.getRunTime() + " ms");
        System.out.println("-------------------------------------------------");

        if (result.wasSuccessful()) {
            System.out.println("\n>>> ALL TESTS PASSED SUCCESSFULLY! (100% PASS) <<<");
        } else {
            System.out.println("\n>>> TEST FAILURES DETECTED: <<<");
            for (Failure failure : result.getFailures()) {
                System.out.println("\n[FAILED] " + failure.getTestHeader());
                System.out.println("Message: " + failure.getMessage());
                System.out.println("Trace:\n" + failure.getTrace());
            }
        }
        System.out.println("=================================================");
    }
}
