package com.engperf.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Application entry point. Component-scans the whole {@code com.engperf} tree. */
@SpringBootApplication(scanBasePackages = "com.engperf")
public class EngPerformanceApplication {

  public static void main(String[] args) {
    SpringApplication.run(EngPerformanceApplication.class, args);
  }
}
