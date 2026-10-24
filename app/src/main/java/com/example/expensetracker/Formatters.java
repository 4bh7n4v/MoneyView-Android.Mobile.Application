package com.example.expensetracker;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;

/**
 * Shared formatting and date-range helpers.
 */
public final class Formatters {

    private static Currencies.Option currency = Currencies.INR;
    private static double rupeesPerDollar = Prefs.DEFAULT_USD_RATE;

    private Formatters() {}

    /** Called at startup and whenever the user picks a new currency. */
    public static void setCurrency(String code) {
        currency = Currencies.find(code);
    }

    /** Called at startup and whenever the user changes the exchange rate. */
    public static void setUsdRate(double rate) {
        rupeesPerDollar = rate;
    }

    public static boolean isUsd() {
        return currency == Currencies.USD;
    }

    /** Amounts are stored in rupees; this converts one to the currency being shown. */
    public static double toDisplay(double rupees) {
        return isUsd() ? rupees / rupeesPerDollar : rupees;
    }

    /** Converts an amount the user typed (in the shown currency) back to rupees for storage. */
    public static double fromDisplay(double amount) {
        return isUsd() ? amount * rupeesPerDollar : amount;
    }

    /** Formats a stored (rupee) amount in the shown currency, e.g. ₹1,500.00 or $17.05. */
    public static String money(double rupees) {
        NumberFormat format = NumberFormat.getCurrencyInstance(currency.locale);
        format.setCurrency(Currency.getInstance(currency.code));
        return format.format(toDisplay(rupees));
    }

    /** Short form for chart axes, e.g. ₹1.5k, $2M. Takes an amount already in the shown currency. */
    public static String compactDisplayMoney(double amount) {
        String symbol = currency.symbol();
        if (amount >= 1_000_000) return symbol + trim(amount / 1_000_000) + "M";
        if (amount >= 1_000) return symbol + trim(amount / 1_000) + "k";
        return symbol + trim(amount);
    }

    public static String currencySymbol() {
        return currency.symbol();
    }

    /** Text for an amount field: a stored (rupee) amount in the shown currency, e.g. "17.05". */
    public static String editAmount(double rupees) {
        return plainAmount(Math.round(toDisplay(rupees) * 100) / 100.0);
    }

    /** Plain number for edit fields: 500 rather than 500.0. */
    public static String plainAmount(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    public static String date(long millis) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(millis));
    }

    public static String time(long millis) {
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    public static String dateTime(long millis) {
        return date(millis) + ", " + time(millis);
    }

    public static String month(Calendar month) {
        return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(month.getTime());
    }

    public static String shortMonth(Calendar month) {
        return new SimpleDateFormat("MMM", Locale.getDefault()).format(month.getTime());
    }

    public static String dayName(Calendar day) {
        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(day.getTime());
    }

    public static String shortDayName(Calendar day) {
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(day.getTime());
    }

    public static String dayMonth(Calendar day) {
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(day.getTime());
    }

    /** Returns a copy of {@code cal} set to 00:00 on the same day. */
    public static Calendar startOfDay(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Returns a copy of {@code cal} set to 00:00 on the 1st of its month. */
    public static Calendar startOfMonth(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Exclusive end of the month containing {@code cal}. */
    public static long endOfMonth(Calendar cal) {
        Calendar c = startOfMonth(cal);
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }

    private static String trim(double value) {
        return BigDecimal.valueOf(Math.round(value * 10) / 10.0).stripTrailingZeros().toPlainString();
    }
}
