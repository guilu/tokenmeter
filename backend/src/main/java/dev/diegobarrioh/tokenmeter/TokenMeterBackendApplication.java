package dev.diegobarrioh.tokenmeter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TokenMeterBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(TokenMeterBackendApplication.class, args);
  }
}
