package com.taxi.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DotenvEnvironmentPostProcessorTest {

    @Test
    void loadsExplicitConfigFile() throws Exception {
        Path file = Files.createTempFile("taxi-config-", ".env");
        Files.writeString(file, "TAXI_TEST_DB_HOST=cfg-db\nTAXI_TEST_PORT=18080\n", StandardCharsets.UTF_8);
        String old = System.getProperty("TAXI_CONFIG_FILE");
        try {
            System.setProperty("TAXI_CONFIG_FILE", file.toString());
            StandardEnvironment environment = new StandardEnvironment();
            new DotenvEnvironmentPostProcessor().postProcessEnvironment(environment, null);
            assertEquals("cfg-db", environment.getProperty("TAXI_TEST_DB_HOST"));
            assertEquals("18080", environment.getProperty("TAXI_TEST_PORT"));
        } finally {
            if (old == null) {
                System.clearProperty("TAXI_CONFIG_FILE");
            } else {
                System.setProperty("TAXI_CONFIG_FILE", old);
            }
            Files.deleteIfExists(file);
        }
    }

    @Test
    void missingConfigFileIsIgnored() {
        String old = System.getProperty("TAXI_CONFIG_FILE");
        try {
            System.setProperty("TAXI_CONFIG_FILE", "target/not-exists-taxi.env");
            StandardEnvironment environment = new StandardEnvironment();
            new DotenvEnvironmentPostProcessor().postProcessEnvironment(environment, null);
            assertNull(environment.getProperty("TAXI_TEST_DB_HOST"));
        } finally {
            if (old == null) {
                System.clearProperty("TAXI_CONFIG_FILE");
            } else {
                System.setProperty("TAXI_CONFIG_FILE", old);
            }
        }
    }
}
