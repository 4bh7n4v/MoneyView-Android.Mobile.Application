package com.example.expensetracker;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Schedules {@link ReminderWorker} runs and works out repeat dates.
 *
 * Two jobs are used: a one-off run timed for the earliest upcoming reminder (for accuracy),
 * and an hourly safety-net run in case the one-off is delayed by the system.
 */
public final class ReminderScheduler {

    private static final String WORK_NEXT = "reminder_next";
    private static final String WORK_PERIODIC = "reminder_periodic_check";

    private ReminderScheduler() {}

    public static void ensurePeriodicCheck(Context context) {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(ReminderWorker.class, 1, TimeUnit.HOURS).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** Call after any reminder is added, changed, deleted or sent. */
    public static void scheduleNext(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        long next = AppDbHelper.getInstance(context).getNextReminderTime();
        if (next < 0) {
            workManager.cancelUniqueWork(WORK_NEXT);
            return;
        }
        long delay = Math.max(0, next - System.currentTimeMillis());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        workManager.enqueueUniqueWork(WORK_NEXT, ExistingWorkPolicy.REPLACE, request);
    }

    /** First occurrence of a repeating schedule that is strictly after {@code now}. */
    public static long nextOccurrence(long from, String frequency, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(from);
        while (c.getTimeInMillis() <= now) {
            switch (frequency) {
                case Reminder.FREQ_DAILY:
                    c.add(Calendar.DAY_OF_MONTH, 1);
                    break;
                case Reminder.FREQ_WEEKLY:
                    c.add(Calendar.WEEK_OF_YEAR, 1);
                    break;
                case Reminder.FREQ_MONTHLY:
                    c.add(Calendar.MONTH, 1);
                    break;
                default:
                    return from; // FREQ_ONCE never repeats
            }
        }
        return c.getTimeInMillis();
    }

    /** Human-readable label for a frequency code, e.g. "Weekly". */
    public static String frequencyLabel(Context context, String frequency) {
        String[] labels = context.getResources().getStringArray(R.array.reminder_frequencies);
        for (int i = 0; i < Reminder.FREQUENCIES.length; i++) {
            if (Reminder.FREQUENCIES[i].equals(frequency)) return labels[i];
        }
        return frequency;
    }
}
