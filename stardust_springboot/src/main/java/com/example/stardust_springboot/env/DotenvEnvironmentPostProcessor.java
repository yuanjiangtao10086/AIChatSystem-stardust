package com.example.stardust_springboot.env;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads the project level {@code .env} file (from the process working directory) into the Spring
 * {@code Environment}, so that placeholders such as {@code ${DB_URL}} in application.properties can
 * be resolved without exporting environment variables manually.
 *
 * <p>Spring Boot has no built-in dotenv support, so this is intentionally small and dependency free.
 * The file is optional: when it does not exist (for example in production) nothing happens. The
 * source is appended last, so real environment variables, system properties and application.properties
 * always take precedence over {@code .env}.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String FILE_NAME = ".env";
    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = Path.of(FILE_NAME).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read env file: " + file, exception);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name.strip(), stripQuotes(properties.getProperty(name)));
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
