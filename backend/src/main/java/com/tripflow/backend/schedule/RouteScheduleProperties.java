package com.tripflow.backend.schedule;

import java.time.Duration;
import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Configures the heuristic day/time scheduler (SCRUM-244a) that assigns each stop a
 * dayNumber and plannedTime after route optimization. Bound from app.schedule.*.
 */
@Validated
@ConfigurationProperties(prefix = "app.schedule")
public record RouteScheduleProperties(
        @NotNull LocalTime dayStartTime,
        @NotNull LocalTime dayEndTime,
        @NotNull Duration defaultVisitDuration) {
}
