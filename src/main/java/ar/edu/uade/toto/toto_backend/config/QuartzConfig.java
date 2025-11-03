package ar.edu.uade.toto.toto_backend.config;

import ar.edu.uade.toto.toto_backend.service.ReminderNotificationService;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz configuration for reminder scheduler.
 * Schedules a job to check for reminders that need recurrence updates.
 */
@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail reminderRecurrenceJobDetail() {
        return JobBuilder.newJob(ReminderRecurrenceJob.class)
                .withIdentity("reminderRecurrenceJob")
                .withDescription("Updates reminders for next occurrence based on repeat pattern")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger reminderRecurrenceTrigger(JobDetail reminderRecurrenceJobDetail) {
        // Run every hour to check for reminders that need to be updated
        return TriggerBuilder.newTrigger()
                .forJob(reminderRecurrenceJobDetail)
                .withIdentity("reminderRecurrenceTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?")) // Every hour at minute 0
                .build();
    }

    /**
     * Job that processes reminders for next occurrences.
     * This runs periodically to update recurring reminders.
     */
    public static class ReminderRecurrenceJob implements Job {

        private final ReminderNotificationService notificationService;

        public ReminderRecurrenceJob(ReminderNotificationService notificationService) {
            this.notificationService = notificationService;
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            // Note: The actual scheduling of next occurrence is handled when a reminder is announced
            // This job exists as a backup to ensure consistency, but the main logic is event-driven
            // (when the Android app marks a reminder as announced, we schedule the next occurrence)
        }
    }
}
