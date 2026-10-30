package com.futspring.backend.shared.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.task.DefaultTaskSchedulerConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.context.annotation.Import;

// STOMP's messageBrokerTaskScheduler makes the general scheduler auto-configuration back off.
// Import Boot's default by name so @Scheduled uses taskScheduler while STOMP keeps its own scheduler.
@AutoConfiguration(after = TaskSchedulingAutoConfiguration.class)
@Import(DefaultTaskSchedulerConfiguration.class)
public class TaskSchedulingConfig {
}
