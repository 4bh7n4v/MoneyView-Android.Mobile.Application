package com.example.expensetracker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Notifies the user when today's or this month's spending reaches 80% of the limit,
 * and again when it goes over. Each alert is shown at most once per day / month.
 */
public final class BudgetAlerts {

    private static final String CHANNEL_ID = "budget";
    private static final String KIND_DAILY = "daily";
    private static final String KIND_MONTHLY = "monthly";
    private static final int NOTIFICATION_DAILY = 100_001;
    private static final int NOTIFICATION_MONTHLY = 100_002;

    private static final int LEVEL_OK = 0;
    private static final int LEVEL_WARNING = 1; // 80% or more used
    private static final int LEVEL_EXCEEDED = 2;
    private static final double WARNING_SHARE = 0.8;

    private BudgetAlerts() {}

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.channel_budget), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.channel_budget_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** Call after any expense is added, changed or restored, and after limits change. */
    public static void check(Context context) {
        if (!Prefs.areBudgetAlertsEnabled(context)) return;
        AppDbHelper db = AppDbHelper.getInstance(context);
        Calendar now = Calendar.getInstance();

        double dailyLimit = Prefs.getDailyBudget(context);
        if (dailyLimit > 0) {
            Calendar dayStart = Formatters.startOfDay(now);
            Calendar dayEnd = (Calendar) dayStart.clone();
            dayEnd.add(Calendar.DAY_OF_MONTH, 1);
            double spent = db.getTotals(dayStart.getTimeInMillis(), dayEnd.getTimeInMillis())[1];
            String period = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(now.getTime());
            update(context, KIND_DAILY, period, spent, dailyLimit);
        }

        double monthlyLimit = Prefs.getBudget(context);
        if (monthlyLimit > 0) {
            Calendar monthStart = Formatters.startOfMonth(now);
            double spent = db.getTotals(monthStart.getTimeInMillis(), Formatters.endOfMonth(now))[1];
            String period = new SimpleDateFormat("yyyyMM", Locale.ROOT).format(now.getTime());
            update(context, KIND_MONTHLY, period, spent, monthlyLimit);
        }
    }

    private static void update(Context context, String kind, String period, double spent, double limit) {
        int level = spent > limit ? LEVEL_EXCEEDED
                : spent >= limit * WARNING_SHARE ? LEVEL_WARNING
                : LEVEL_OK;
        int alreadyShown = Prefs.getAlertLevel(context, kind, period);
        if (level > alreadyShown) {
            notify(context, kind, level, spent, limit);
        }
        // Saving a lower level too lets the alert fire again if spending drops (e.g. a deletion) and rises.
        Prefs.setAlertLevel(context, kind, period, level);
    }

    private static void notify(Context context, String kind, int level, double spent, double limit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        boolean daily = KIND_DAILY.equals(kind);
        String title;
        String text;
        if (level == LEVEL_EXCEEDED) {
            title = context.getString(daily ? R.string.alert_daily_exceeded : R.string.alert_monthly_exceeded);
            text = context.getString(daily ? R.string.alert_daily_exceeded_text : R.string.alert_monthly_exceeded_text,
                    Formatters.money(spent), Formatters.money(limit), Formatters.money(spent - limit));
        } else {
            int percent = (int) Math.round(spent * 100 / limit);
            title = context.getString(daily ? R.string.alert_daily_warning : R.string.alert_monthly_warning, percent);
            text = context.getString(daily ? R.string.alert_daily_warning_text : R.string.alert_monthly_warning_text,
                    Formatters.money(spent), Formatters.money(limit), Formatters.money(limit - spent));
        }

        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_budget)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(daily ? NOTIFICATION_DAILY : NOTIFICATION_MONTHLY, builder.build());
    }
}
