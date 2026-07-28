package com.tripflow.backend.schedule;

import java.time.Duration;
import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures the heuristic day/time scheduler (SCRUM-244a) that assigns each stop a
 * dayNumber and plannedTime after route optimization. Bound from app.schedule.*.
 */
@ConfigurationProperties(prefix = "app.schedule")
public record RouteScheduleProperties(LocalTime dayStartTime, LocalTime dayEndTime, Duration defaultVisitDuration) {
}
