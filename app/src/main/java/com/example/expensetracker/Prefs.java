package com.example.expensetracker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * User settings kept in SharedPreferences: currency and monthly budget.
 */
public final class Prefs {

    private static final String FILE = "settings";
    private static final String KEY_CURRENCY = "currency";
    private static final String KEY_BUDGET = "monthly_budget";
    private static final String KEY_DAILY_BUDGET = "daily_budget";
    private static final String KEY_BUDGET_ALERTS = "budget_alerts";
    private static final String KEY_USD_RATE = "usd_rate";

    /** Rupees per US dollar until the user sets their own rate. */
    public static final double DEFAULT_USD_RATE = 88.0;

    private Prefs() {}

    public static String getCurrency(Context context) {
        return prefs(context).getString(KEY_CURRENCY, "INR");
    }

    public static void setCurrency(Context context, String code) {
        prefs(context).edit().putString(KEY_CURRENCY, code).apply();
        Formatters.setCurrency(code);
    }

    /** How many rupees one US dollar is worth. */
    public static double getUsdRate(Context context) {
        return Double.parseDouble(prefs(context).getString(KEY_USD_RATE, String.valueOf(DEFAULT_USD_RATE)));
    }

    public static void setUsdRate(Context context, double rate) {
        prefs(context).edit().putString(KEY_USD_RATE, String.valueOf(rate)).apply();
        Formatters.setUsdRate(rate);
    }

    /** Monthly spending budget in rupees; 0 means no budget set. */
    public static double getBudget(Context context) {
        return Double.parseDouble(prefs(context).getString(KEY_BUDGET, "0"));
    }

    public static void setBudget(Context context, double budget) {
        prefs(context).edit().putString(KEY_BUDGET, String.valueOf(budget)).apply();
    }

    /** Daily spending limit in rupees; 0 means no limit set. */
    public static double getDailyBudget(Context context) {
        return Double.parseDouble(prefs(context).getString(KEY_DAILY_BUDGET, "0"));
    }

    public static void setDailyBudget(Context context, double budget) {
        prefs(context).edit().putString(KEY_DAILY_BUDGET, String.valueOf(budget)).apply();
    }

    public static boolean areBudgetAlertsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_BUDGET_ALERTS, true);
    }

    public static void setBudgetAlertsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_BUDGET_ALERTS, enabled).apply();
    }

    /**
     * Remembers the highest alert already shown for a period (e.g. kind "daily", period
     * "20261021") so the same warning isn't repeated for every new expense that day.
     */
    public static int getAlertLevel(Context context, String kind, String period) {
        SharedPreferences p = prefs(context);
        return period.equals(p.getString("alert_period_" + kind, "")) ? p.getInt("alert_level_" + kind, 0) : 0;
    }

    public static void setAlertLevel(Context context, String kind, String period, int level) {
        prefs(context).edit()
                .putString("alert_period_" + kind, period)
                .putInt("alert_level_" + kind, level)
                .apply();
    }

    /** Forgets which alerts were shown, so new limits are judged from scratch. */
    public static void resetAlertLevels(Context context) {
        prefs(context).edit()
                .remove("alert_period_daily").remove("alert_level_daily")
                .remove("alert_period_monthly").remove("alert_level_monthly")
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
