package com.example.expensetracker;

import android.app.Application;

/**
 * App-wide setup that must happen before any screen or background worker runs.
 */
public class ExpenseTrackerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Formatters.setCurrency(Prefs.getCurrency(this));
        Formatters.setUsdRate(Prefs.getUsdRate(this));
        SmsSender.createNotificationChannel(this);
        BudgetAlerts.createNotificationChannel(this);
        ReminderScheduler.ensurePeriodicCheck(this);
    }
}
