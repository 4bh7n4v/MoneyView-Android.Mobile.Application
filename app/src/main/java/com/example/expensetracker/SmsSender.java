package com.example.expensetracker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

/**
 * Sends reminder text messages and tells the user, via a notification, what happened.
 */
public final class SmsSender {

    private static final String CHANNEL_ID = "reminders";

    private SmsSender() {}

    public static boolean hasSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Replaces {name} and {amount} (the outstanding balance) in a reminder template. */
    public static String fillPlaceholders(String template, Person person, double balance) {
        return template
                .replace("{name}", person.getName())
                .replace("{amount}", Formatters.money(Math.abs(balance)));
    }

    /** Sends an SMS; returns false if permission is missing or sending failed. */
    @SuppressWarnings("deprecation") // SmsManager.getDefault() is still required below Android 12
    public static boolean send(Context context, String phone, String message) {
        if (!hasSmsPermission(context)) return false;
        try {
            SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? context.getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(message);
            sms.sendMultipartTextMessage(phone, null, parts, null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.channel_reminders_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** Posts "Reminder sent to X" (or a failure notice) that opens the person's profile. */
    public static void notifyResult(Context context, Person person, Reminder reminder,
                                    String message, boolean sent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, PersonDetailActivity.class)
                .putExtra(PersonDetailActivity.EXTRA_ID, person.getId());
        PendingIntent pending = PendingIntent.getActivity(context, (int) person.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = sent
                ? context.getString(R.string.notification_sent, person.getName())
                : context.getString(R.string.notification_failed, person.getName());
        String text = sent ? message : context.getString(R.string.notification_failed_detail);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify((int) reminder.getId(), builder.build());
    }
}
