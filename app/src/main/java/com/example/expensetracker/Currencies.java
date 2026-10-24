package com.example.expensetracker;

import androidx.annotation.NonNull;

import java.util.Currency;
import java.util.Locale;

/**
 * The two display currencies. Amounts are always stored in rupees; when US Dollar is
 * selected they are converted using the rate set in Settings (see {@link Formatters}).
 */
public final class Currencies {

    public static final class Option {
        public final String code;
        public final String name;
        public final Locale locale;

        Option(String code, String name, Locale locale) {
            this.code = code;
            this.name = name;
            this.locale = locale;
        }

        public String symbol() {
            return Currency.getInstance(code).getSymbol(locale);
        }

        /** Shown in the Settings dropdown, e.g. "INR – Indian Rupee (₹)". */
        @NonNull
        @Override
        public String toString() {
            return code + " – " + name + " (" + symbol() + ")";
        }
    }

    public static final Option INR = new Option("INR", "Indian Rupee", new Locale("en", "IN"));
    public static final Option USD = new Option("USD", "US Dollar", Locale.US);
    public static final Option[] ALL = {INR, USD};

    private Currencies() {}

    /** Unknown codes (e.g. from an older version that offered more currencies) fall back to INR. */
    public static Option find(String code) {
        return USD.code.equals(code) ? USD : INR;
    }
}
