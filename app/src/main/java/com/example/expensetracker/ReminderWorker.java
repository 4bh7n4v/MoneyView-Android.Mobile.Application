package com.example.expensetracker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Background job that sends every reminder whose time has come, then moves
 * repeating reminders to their next date and switches one-time reminders off.
 */
public class ReminderWorker extends Worker {

    // The one-off and hourly jobs can overlap; this stops a reminder being sent twice.
    private static final Object LOCK = new Object();

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        AppDbHelper db = AppDbHelper.getInstance(context);

        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            for (Reminder reminder : db.getDueReminders(now)) {
                Person person = db.getPerson(reminder.getPersonId());
                if (person != null) {
                    String message = SmsSender.fillPlaceholders(
                            reminder.getMessage(), person, db.getBalance(person.getId()));
                    boolean sent = SmsSender.send(context, person.getPhone(), message);
                    SmsSender.notifyResult(context, person, reminder, message, sent);
                    if (sent) reminder.setLastSentAt(now);
                }

                // Advance even on failure so a missing permission doesn't cause a notification every run.
                if (reminder.isRepeating()) {
                    reminder.setNextAt(ReminderScheduler.nextOccurrence(
                            reminder.getNextAt(), reminder.getFrequency(), now));
                } else {
                    reminder.setEnabled(false);
                }
                db.updateReminder(reminder);
            }
        }

        ReminderScheduler.scheduleNext(context);
        return Result.success();
    }
}
