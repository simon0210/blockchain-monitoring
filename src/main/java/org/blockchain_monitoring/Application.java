package org.blockchain_monitoring;

import org.blockchain_monitoring.config.GrafanaConfig;
import org.blockchain_monitoring.config.InfluxDBConfig;
import org.blockchain_monitoring.config.SchedulerConfig;
import org.blockchain_monitoring.fly_client_spring.config.FlyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({SchedulerConfig.class, InfluxDBConfig.class, FlyConfig.class, GrafanaConfig.class})
public class Application {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class);
    }
}
