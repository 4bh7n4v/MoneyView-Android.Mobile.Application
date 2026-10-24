package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Transactions tab: month summary with budget, search, category filter chips and the list.
 */
public class TransactionsFragment extends Fragment implements TransactionAdapter.Listener, MenuProvider {

    private static final String STATE_QUERY = "state_query";
    private static final String STATE_FILTER = "state_filter";

    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_TYPE = "TYPE:";
    private static final String FILTER_CATEGORY = "CAT:";

    private AppDbHelper db;
    private MonthViewModel monthViewModel;
    private TransactionAdapter adapter;
    private List<Transaction> monthItems = new ArrayList<>();
    private String query = "";
    private String filter = FILTER_ALL;

    private TextView textMonth, textIncome, textExpense, textBalance, textEmpty;
    private TextView textBudgetDaily, textBudgetMonthly;
    private View layoutBudgetDaily, layoutBudgetMonthly;
    private LinearProgressIndicator progressBudgetDaily, progressBudgetMonthly;
    private ChipGroup chipGroup;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transactions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDbHelper.getInstance(requireContext());
        monthViewModel = new ViewModelProvider(requireActivity()).get(MonthViewModel.class);
        if (savedInstanceState != null) {
            query = savedInstanceState.getString(STATE_QUERY, "");
            filter = savedInstanceState.getString(STATE_FILTER, FILTER_ALL);
        }

        textMonth = view.findViewById(R.id.text_month);
        textIncome = view.findViewById(R.id.text_income);
        textExpense = view.findViewById(R.id.text_expense);
        textBalance = view.findViewById(R.id.text_balance);
        textEmpty = view.findViewById(R.id.text_empty);
        textBudgetDaily = view.findViewById(R.id.text_budget_daily);
        textBudgetMonthly = view.findViewById(R.id.text_budget_monthly);
        layoutBudgetDaily = view.findViewById(R.id.layout_budget_daily);
        layoutBudgetMonthly = view.findViewById(R.id.layout_budget_monthly);
        progressBudgetDaily = view.findViewById(R.id.progress_budget_daily);
        progressBudgetMonthly = view.findViewById(R.id.progress_budget_monthly);
        chipGroup = view.findViewById(R.id.chip_group_filter);

        view.findViewById(R.id.button_prev_month).setOnClickListener(v -> monthViewModel.shift(-1));
        view.findViewById(R.id.button_next_month).setOnClickListener(v -> monthViewModel.shift(1));

        adapter = new TransactionAdapter(this);
        RecyclerView recycler = view.findViewById(R.id.recycler_transactions);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.fab_add).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddEditTransactionActivity.class)));

        buildFilterChips();
        monthViewModel.getMonth().observe(getViewLifecycleOwner(), month -> refresh());
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.app_name);
        refresh(); // reload after returning from add/edit or settings
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, query);
        outState.putString(STATE_FILTER, filter);
    }

    // ---------------------------------------------------------------- search (toolbar)

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_transactions, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) item.getActionView();
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        if (!query.isEmpty()) {
            String saved = query;
            item.expandActionView();
            searchView.setQuery(saved, false);
            searchView.clearFocus();
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String text) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                query = text.trim();
                applyFilter();
                return true;
            }
        });
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        return false;
    }

    // ---------------------------------------------------------------- filter chips

    private void buildFilterChips() {
        addChip(getString(R.string.filter_all), FILTER_ALL);
        addChip(getString(R.string.income), FILTER_TYPE + Transaction.TYPE_INCOME);
        addChip(getString(R.string.expenses), FILTER_TYPE + Transaction.TYPE_EXPENSE);

        Set<String> categories = new LinkedHashSet<>();
        categories.addAll(db.getCategories(Transaction.TYPE_EXPENSE));
        categories.addAll(db.getCategories(Transaction.TYPE_INCOME));
        for (String category : categories) {
            addChip(category, FILTER_CATEGORY + category);
        }

        // The saved filter may point at a category that has since been deleted.
        if (chipGroup.getCheckedChipId() == View.NO_ID) {
            filter = FILTER_ALL;
            ((Chip) chipGroup.getChildAt(0)).setChecked(true);
        }

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = group.findViewById(checkedIds.get(0));
            filter = (String) chip.getTag();
            applyFilter();
        });
    }

    private void addChip(String text, String tag) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, chipGroup, false);
        chip.setId(View.generateViewId());
        chip.setSaveEnabled(false); // selection is restored from STATE_FILTER instead
        chip.setText(text);
        chip.setTag(tag);
        chipGroup.addView(chip);
        if (tag.equals(filter)) chip.setChecked(true);
    }

    // ---------------------------------------------------------------- data

    private void refresh() {
        Calendar month = monthViewModel.current();
        monthItems = db.getTransactionsBetween(month.getTimeInMillis(), Formatters.endOfMonth(month));

        double income = 0, expense = 0;
        for (Transaction t : monthItems) {
            if (t.isIncome()) income += t.getAmount();
            else expense += t.getAmount();
        }

        textMonth.setText(Formatters.month(month));
        textIncome.setText(Formatters.money(income));
        textExpense.setText(Formatters.money(expense));
        textBalance.setText(Formatters.money(income - expense));
        updateBudget(expense);
        applyFilter();
    }

    private void updateBudget(double monthSpent) {
        double dailyLimit = Prefs.getDailyBudget(requireContext());
        layoutBudgetDaily.setVisibility(dailyLimit > 0 ? View.VISIBLE : View.GONE);
        if (dailyLimit > 0) {
            Calendar today = Formatters.startOfDay(Calendar.getInstance());
            Calendar tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            double todaySpent = db.getTotals(today.getTimeInMillis(), tomorrow.getTimeInMillis())[1];
            boolean over = todaySpent > dailyLimit;
            bindBudgetBar(progressBudgetDaily, todaySpent, dailyLimit);
            textBudgetDaily.setText(over
                    ? getString(R.string.budget_daily_over, Formatters.money(todaySpent - dailyLimit), Formatters.money(dailyLimit))
                    : getString(R.string.budget_daily_used, Formatters.money(todaySpent), Formatters.money(dailyLimit)));
        }

        double monthlyLimit = Prefs.getBudget(requireContext());
        layoutBudgetMonthly.setVisibility(monthlyLimit > 0 ? View.VISIBLE : View.GONE);
        if (monthlyLimit > 0) {
            boolean over = monthSpent > monthlyLimit;
            bindBudgetBar(progressBudgetMonthly, monthSpent, monthlyLimit);
            textBudgetMonthly.setText(over
                    ? getString(R.string.budget_over, Formatters.money(monthSpent - monthlyLimit), Formatters.money(monthlyLimit))
                    : getString(R.string.budget_used, Formatters.money(monthSpent), Formatters.money(monthlyLimit)));
        }
    }

    /** Teal while under 80%, amber from 80%, red once over the limit. */
    private void bindBudgetBar(LinearProgressIndicator bar, double spent, double limit) {
        bar.setProgress((int) Math.min(100, Math.round(spent * 100 / limit)));
        int color;
        if (spent > limit) {
            color = MaterialColors.getColor(bar, androidx.appcompat.R.attr.colorError);
        } else if (spent >= limit * 0.8) {
            color = ContextCompat.getColor(requireContext(), R.color.budget_warning);
        } else {
            color = MaterialColors.getColor(bar, androidx.appcompat.R.attr.colorPrimary);
        }
        bar.setIndicatorColor(color);
    }

    private void applyFilter() {
        String q = query.toLowerCase(Locale.ROOT);
        List<Transaction> shown = new ArrayList<>();
        for (Transaction t : monthItems) {
            if (matchesFilter(t) && matchesQuery(t, q)) shown.add(t);
        }
        adapter.submit(shown);

        if (monthItems.isEmpty()) {
            textEmpty.setText(R.string.empty_month);
            textEmpty.setVisibility(View.VISIBLE);
        } else if (shown.isEmpty()) {
            textEmpty.setText(R.string.no_matches);
            textEmpty.setVisibility(View.VISIBLE);
        } else {
            textEmpty.setVisibility(View.GONE);
        }
    }

    private boolean matchesFilter(Transaction t) {
        if (filter.startsWith(FILTER_TYPE)) return filter.substring(FILTER_TYPE.length()).equals(t.getType());
        if (filter.startsWith(FILTER_CATEGORY)) return filter.substring(FILTER_CATEGORY.length()).equals(t.getCategory());
        return true;
    }

    private static boolean matchesQuery(Transaction t, String q) {
        if (q.isEmpty()) return true;
        return t.getTitle().toLowerCase(Locale.ROOT).contains(q)
                || t.getCategory().toLowerCase(Locale.ROOT).contains(q)
                || (t.getNote() != null && t.getNote().toLowerCase(Locale.ROOT).contains(q));
    }

    // ---------------------------------------------------------------- list actions

    @Override
    public void onTransactionClick(Transaction transaction) {
        Intent intent = new Intent(requireContext(), AddEditTransactionActivity.class);
        intent.putExtra(AddEditTransactionActivity.EXTRA_ID, transaction.getId());
        startActivity(intent);
    }

    @Override
    public void onTransactionLongClick(Transaction transaction) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(getString(R.string.delete_message, transaction.getTitle()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> deleteWithUndo(transaction))
                .show();
    }

    private void deleteWithUndo(Transaction transaction) {
        db.deleteTransaction(transaction.getId());
        refresh();
        Snackbar.make(requireView(), R.string.transaction_deleted, Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.fab_add)
                .setAction(R.string.undo, v -> {
                    db.insertTransaction(transaction);
                    BudgetAlerts.check(requireContext());
                    refresh();
                })
                .show();
    }
}
