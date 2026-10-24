package com.example.expensetracker;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Charts tab, read top to bottom as a drill-down:
 * monthly summary (tap a month) → top 3 categories for that month →
 * weekly summary (tap a week) → summary of each day in that week.
 *
 * Weeks are 7-day blocks of the month: 1–7, 8–14, 15–21, 22–28 and 29–end.
 */
public class ChartsFragment extends Fragment {

    private static final int MONTHS_IN_CHART = 6;
    private static final int TOP_CATEGORIES = 3;
    private static final int TOP_EXPENSES = 3;
    private static final int DAYS_PER_WEEK = 7;
    private static final int OTHERS_COLOR = 0xFF90A4AE;

    private AppDbHelper db;
    private MonthViewModel monthViewModel;

    private TextView textMonth, textPieEmpty, textWeeklyTitle, textDailyTitle;
    private MaterialButtonToggleGroup togglePieType;
    private PieChartView pieChart;
    private LinearLayout containerLegend, layoutTopExpenses, containerTopExpenses, containerDays;
    private BarChartView monthChart, weekChart, dayChart;

    private Calendar windowEnd; // last month shown in the monthly chart
    private int selectedWeek;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_charts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDbHelper.getInstance(requireContext());
        monthViewModel = new ViewModelProvider(requireActivity()).get(MonthViewModel.class);

        textMonth = view.findViewById(R.id.text_month);
        textPieEmpty = view.findViewById(R.id.text_pie_empty);
        textWeeklyTitle = view.findViewById(R.id.text_weekly_title);
        textDailyTitle = view.findViewById(R.id.text_daily_title);
        togglePieType = view.findViewById(R.id.toggle_pie_type);
        pieChart = view.findViewById(R.id.pie_chart);
        containerLegend = view.findViewById(R.id.container_legend);
        layoutTopExpenses = view.findViewById(R.id.layout_top_expenses);
        containerTopExpenses = view.findViewById(R.id.container_top_expenses);
        containerDays = view.findViewById(R.id.container_days);
        monthChart = view.findViewById(R.id.month_chart);
        weekChart = view.findViewById(R.id.week_chart);
        dayChart = view.findViewById(R.id.day_chart);

        view.findViewById(R.id.button_prev_month).setOnClickListener(v -> monthViewModel.shift(-1));
        view.findViewById(R.id.button_next_month).setOnClickListener(v -> monthViewModel.shift(1));
        togglePieType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) refreshCategories();
        });

        monthChart.setOnBarClickListener(index -> {
            Calendar month = windowStart();
            month.add(Calendar.MONTH, index);
            monthViewModel.setMonth(month);
        });
        weekChart.setOnBarClickListener(index -> {
            selectedWeek = index;
            weekChart.setSelectedIndex(index);
            refreshDays();
        });

        monthViewModel.getMonth().observe(getViewLifecycleOwner(), month -> {
            selectedWeek = defaultWeek(month);
            refreshAll();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.tab_charts);
    }

    private void refreshAll() {
        Calendar month = monthViewModel.current();
        textMonth.setText(Formatters.month(month));
        moveWindowTo(month);
        refreshMonths();
        refreshCategories();
        refreshWeeks();
        refreshDays();
    }

    // ---------------------------------------------------------------- 1. monthly summary

    /** Keeps the 6-month window still while the selected month stays inside it. */
    private void moveWindowTo(Calendar month) {
        if (windowEnd == null || month.after(windowEnd) || month.before(windowStart())) {
            windowEnd = (Calendar) month.clone();
        }
    }

    private Calendar windowStart() {
        Calendar start = (Calendar) windowEnd.clone();
        start.add(Calendar.MONTH, -(MONTHS_IN_CHART - 1));
        return start;
    }

    private void refreshMonths() {
        String[] labels = new String[MONTHS_IN_CHART];
        double[] income = new double[MONTHS_IN_CHART];
        double[] expense = new double[MONTHS_IN_CHART];
        int selected = -1;

        Calendar month = windowStart();
        for (int i = 0; i < MONTHS_IN_CHART; i++) {
            double[] totals = db.getTotals(month.getTimeInMillis(), Formatters.endOfMonth(month));
            labels[i] = Formatters.shortMonth(month);
            income[i] = totals[0];
            expense[i] = totals[1];
            if (month.equals(monthViewModel.current())) selected = i;
            month.add(Calendar.MONTH, 1);
        }
        monthChart.setData(labels, income, expense);
        monthChart.setSelectedIndex(selected);
    }

    // ---------------------------------------------------------------- 2. top 3 categories

    private void refreshCategories() {
        Calendar month = monthViewModel.current();
        long start = month.getTimeInMillis();
        long end = Formatters.endOfMonth(month);
        boolean income = togglePieType.getCheckedButtonId() == R.id.button_pie_income;
        Map<String, Double> totals = db.getCategoryTotals(start, end,
                income ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE);

        // Top 3 categories get their own slice; everything else is grouped as "Others".
        double total = 0;
        double others = 0;
        int otherCount = 0;
        List<String> names = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            total += entry.getValue();
            if (names.size() < TOP_CATEGORIES) {
                names.add(entry.getKey());
                values.add(entry.getValue());
            } else {
                others += entry.getValue();
                otherCount++;
            }
        }
        int[] colors = new int[values.size() + (otherCount > 0 ? 1 : 0)];
        for (int i = 0; i < values.size(); i++) colors[i] = PieChartView.colorAt(i);
        if (otherCount > 0) {
            values.add(others);
            names.add(getResources().getQuantityString(R.plurals.others_categories, otherCount, otherCount));
            colors[colors.length - 1] = OTHERS_COLOR;
        }

        pieChart.setData(values, colors, getString(income ? R.string.earned : R.string.spent), Formatters.money(total));
        textPieEmpty.setVisibility(totals.isEmpty() ? View.VISIBLE : View.GONE);
        textPieEmpty.setText(income ? R.string.empty_income_chart : R.string.empty_expense_chart);

        containerLegend.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < values.size(); i++) {
            View row = inflater.inflate(R.layout.item_category_summary, containerLegend, false);
            boolean isOthers = otherCount > 0 && i == values.size() - 1;
            int percent = (int) Math.round(values.get(i) * 100 / total);

            ViewCompat.setBackgroundTintList(row.findViewById(R.id.view_color), ColorStateList.valueOf(colors[i]));
            ((TextView) row.findViewById(R.id.text_category)).setText(isOthers
                    ? names.get(i) : getString(R.string.ranked_category, i + 1, names.get(i)));
            ((TextView) row.findViewById(R.id.text_amount)).setText(
                    getString(R.string.amount_with_percent, Formatters.money(values.get(i)), percent));
            LinearProgressIndicator progress = row.findViewById(R.id.progress);
            progress.setIndicatorColor(colors[i]);
            progress.setProgress(percent);
            containerLegend.addView(row);
        }

        // Biggest individual expenses of the month
        containerTopExpenses.removeAllViews();
        List<Transaction> top = income ? new ArrayList<>() : db.getTopExpenses(start, end, TOP_EXPENSES);
        layoutTopExpenses.setVisibility(top.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < top.size(); i++) {
            Transaction t = top.get(i);
            View row = inflater.inflate(R.layout.item_transaction, containerTopExpenses, false);
            ((TextView) row.findViewById(R.id.text_icon)).setText(String.valueOf(i + 1));
            ((TextView) row.findViewById(R.id.text_title)).setText(t.getTitle());
            ((TextView) row.findViewById(R.id.text_subtitle)).setText(
                    getString(R.string.item_subtitle, t.getCategory(), Formatters.date(t.getDate())));
            TextView amount = row.findViewById(R.id.text_amount);
            amount.setText(Formatters.money(t.getAmount()));
            amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.expense));
            row.setBackground(null);
            row.setPadding(0, row.getPaddingTop(), 0, row.getPaddingBottom());
            containerTopExpenses.addView(row);
        }
    }

    // ---------------------------------------------------------------- 3. weekly summary

    private static int daysInMonth(Calendar month) {
        return month.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private static int weekCount(Calendar month) {
        return (daysInMonth(month) + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK;
    }

    private static int weekFirstDay(int week) {
        return week * DAYS_PER_WEEK + 1;
    }

    private static int weekLastDay(Calendar month, int week) {
        return Math.min((week + 1) * DAYS_PER_WEEK, daysInMonth(month));
    }

    /** The week containing today when viewing the current month, otherwise the first week. */
    private static int defaultWeek(Calendar month) {
        Calendar today = Calendar.getInstance();
        if (today.get(Calendar.YEAR) == month.get(Calendar.YEAR)
                && today.get(Calendar.MONTH) == month.get(Calendar.MONTH)) {
            return (today.get(Calendar.DAY_OF_MONTH) - 1) / DAYS_PER_WEEK;
        }
        return 0;
    }

    private static Calendar day(Calendar month, int dayOfMonth) {
        Calendar c = (Calendar) month.clone(); // month is already 00:00 on the 1st
        c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        return c;
    }

    private void refreshWeeks() {
        Calendar month = monthViewModel.current();
        int weeks = weekCount(month);
        String[] labels = new String[weeks];
        double[] income = new double[weeks];
        double[] expense = new double[weeks];
        for (int w = 0; w < weeks; w++) {
            Calendar start = day(month, weekFirstDay(w));
            Calendar end = day(month, weekLastDay(month, w));
            end.add(Calendar.DAY_OF_MONTH, 1);
            double[] totals = db.getTotals(start.getTimeInMillis(), end.getTimeInMillis());
            labels[w] = getString(R.string.week_label, w + 1, weekFirstDay(w), weekLastDay(month, w));
            income[w] = totals[0];
            expense[w] = totals[1];
        }
        textWeeklyTitle.setText(getString(R.string.weekly_summary_title, Formatters.month(month)));
        weekChart.setData(labels, income, expense);
        weekChart.setSelectedIndex(selectedWeek);
    }

    // ---------------------------------------------------------------- 4. daily summary

    private void refreshDays() {
        Calendar month = monthViewModel.current();
        int first = weekFirstDay(selectedWeek);
        int last = weekLastDay(month, selectedWeek);
        int count = last - first + 1;

        String[] labels = new String[count];
        double[] income = new double[count];
        double[] expense = new double[count];
        containerDays.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();

        for (int i = 0; i < count; i++) {
            Calendar start = day(month, first + i);
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_MONTH, 1);
            List<Transaction> items = db.getTransactionsBetween(start.getTimeInMillis(), end.getTimeInMillis());
            for (Transaction t : items) {
                if (t.isIncome()) income[i] += t.getAmount();
                else expense[i] += t.getAmount();
            }
            labels[i] = Formatters.shortDayName(start) + "\n" + start.get(Calendar.DAY_OF_MONTH);
            containerDays.addView(dayRow(inflater, start, items.size(), income[i], expense[i]));
        }

        textDailyTitle.setText(getString(R.string.daily_summary_title,
                getString(R.string.week_number, selectedWeek + 1),
                Formatters.dayMonth(day(month, first)), Formatters.dayMonth(day(month, last))));
        dayChart.setData(labels, income, expense);
    }

    private View dayRow(LayoutInflater inflater, Calendar day, int transactions, double income, double expense) {
        View row = inflater.inflate(R.layout.item_day_summary, containerDays, false);
        ((TextView) row.findViewById(R.id.text_day)).setText(Formatters.dayName(day));
        ((TextView) row.findViewById(R.id.text_date)).setText(transactions == 0
                ? getString(R.string.day_no_transactions, Formatters.dayMonth(day))
                : getResources().getQuantityString(R.plurals.day_transactions, transactions,
                Formatters.dayMonth(day), transactions));

        TextView textIncome = row.findViewById(R.id.text_income);
        textIncome.setVisibility(income > 0 ? View.VISIBLE : View.GONE);
        textIncome.setText("+" + Formatters.money(income));
        TextView textExpense = row.findViewById(R.id.text_expense);
        textExpense.setText("−" + Formatters.money(expense));
        textExpense.setVisibility(expense > 0 || income == 0 ? View.VISIBLE : View.GONE);
        if (expense == 0) {
            textExpense.setTextColor(ContextCompat.getColor(requireContext(), R.color.settled));
        }
        return row;
    }
}
