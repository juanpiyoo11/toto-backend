package ar.edu.uade.toto.toto_backend.config;

import ar.edu.uade.toto.toto_backend.service.ReminderNotificationService;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        return TriggerBuilder.newTrigger()
                .forJob(reminderRecurrenceJobDetail)
                .withIdentity("reminderRecurrenceTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?"))
                .build();
    }

    public static class ReminderRecurrenceJob implements Job {

        private final ReminderNotificationService notificationService;

        public ReminderRecurrenceJob(ReminderNotificationService notificationService) {
            this.notificationService = notificationService;
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
        }
    }
}
