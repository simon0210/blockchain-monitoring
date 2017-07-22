package org.blockchain_monitoring.scheduler;

import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import javax.annotation.PostConstruct;

import org.blockchain_monitoring.api.InfluxDestroyer;
import org.blockchain_monitoring.api.InfluxDestroyerException;
import org.blockchain_monitoring.fly_client_spring.FlyNet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Autowired
    public OrganisationMetricWriter metricWriter;

    @Autowired
    public FlyNet flyNet;

    @Value("${TIME_EVENT_LIFETIME:00:01:00}")
    private String envTimeEventLifetime;

    @Autowired
    private InfluxDestroyer influxDestroyer;

    private LocalTime timeEventLifetime;

    @PostConstruct
    public void init() {
        // be careful that @Value init only after @PostConstruct not in Constructor
        timeEventLifetime = LocalTime.parse(envTimeEventLifetime);
    }

    @Scheduled(fixedDelayString="${SCHEDULED_TASKS_DELAY:1000}")
    public void reportCurrentTime() {
        log.debug("START The time is now {}", dateFormat.format(new Date()));
        flyNet.getOrganisations().forEach(metricWriter);

        clearOldData("query");
        clearOldData("invoke");
        clearOldData("commonBlockEvent");

        log.debug("END The time is now {}", dateFormat.format(new Date()));
    }

    private void clearOldData(String measurement) {
        try {
            final LocalDateTime localDateTime = LocalDateTime.now(Clock.systemUTC()).minusSeconds(timeEventLifetime.toSecondOfDay());
            influxDestroyer.deleteMeasurementOlderTime(measurement, localDateTime);
        } catch (InfluxDestroyerException e) {
            log.error(e.getMessage(), e);
        }
    }
}
