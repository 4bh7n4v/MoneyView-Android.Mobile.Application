package com.example.expensetracker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings tab: currency, monthly budget and SMS permission.
 */
public class SettingsFragment extends Fragment {

    private TextInputLayout layoutBudget, layoutDailyBudget, layoutUsdRate;
    private TextInputEditText inputBudget, inputDailyBudget, inputUsdRate;
    private TextView textSmsStatus;
    private View buttonGrantSms;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> updateSmsStatus());

    // Budget alerts only need notifications, not SMS.
    private final ActivityResultLauncher<String> notificationLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> BudgetAlerts.check(requireContext()));

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MaterialAutoCompleteTextView inputCurrency = view.findViewById(R.id.input_currency);
        layoutBudget = view.findViewById(R.id.layout_budget);
        inputBudget = view.findViewById(R.id.input_budget);
        layoutDailyBudget = view.findViewById(R.id.layout_daily_budget);
        inputDailyBudget = view.findViewById(R.id.input_daily_budget);
        textSmsStatus = view.findViewById(R.id.text_sms_status);
        buttonGrantSms = view.findViewById(R.id.button_grant_sms);

        layoutUsdRate = view.findViewById(R.id.layout_usd_rate);
        inputUsdRate = view.findViewById(R.id.input_usd_rate);

        // Currency: INR or USD, converted with the rate below
        ArrayAdapter<Currencies.Option> currencyAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Currencies.ALL);
        inputCurrency.setAdapter(currencyAdapter);
        inputCurrency.setText(Currencies.find(Prefs.getCurrency(requireContext())).toString(), false);
        inputCurrency.setOnItemClickListener((parent, v, position, id) -> {
            Currencies.Option option = Currencies.ALL[position];
            Prefs.setCurrency(requireContext(), option.code);
            showBudgetLimits(); // the same limits, now shown in the new currency
            Toast.makeText(requireContext(), getString(R.string.currency_changed, option.name, option.symbol()),
                    Toast.LENGTH_LONG).show();
        });
        inputUsdRate.setText(Formatters.plainAmount(Prefs.getUsdRate(requireContext())));
        view.findViewById(R.id.button_save_rate).setOnClickListener(v -> saveUsdRate());

        // Budget limits and alerts
        if (savedInstanceState == null) {
            showBudgetLimits();
        } else {
            updateBudgetHint();
        }
        view.findViewById(R.id.button_save_budget).setOnClickListener(v -> saveBudget());

        MaterialSwitch switchAlerts = view.findViewById(R.id.switch_budget_alerts);
        switchAlerts.setChecked(Prefs.areBudgetAlertsEnabled(requireContext()));
        switchAlerts.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setBudgetAlertsEnabled(requireContext(), checked);
            if (checked) requestNotificationsIfNeeded();
        });

        // Categories
        view.findViewById(R.id.button_manage_categories).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ManageCategoriesActivity.class)));

        // SMS permission
        buttonGrantSms.setOnClickListener(v -> permissionLauncher.launch(reminderPermissions()));
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.tab_settings);
        updateSmsStatus();
    }

    private void updateBudgetHint() {
        layoutDailyBudget.setHint(getString(R.string.hint_daily_budget, Formatters.currencySymbol()));
        layoutBudget.setHint(getString(R.string.hint_budget, Formatters.currencySymbol()));
    }

    /** Fills the limit fields from the saved (rupee) limits, in the currency being shown. */
    private void showBudgetLimits() {
        updateBudgetHint();
        double daily = Prefs.getDailyBudget(requireContext());
        double monthly = Prefs.getBudget(requireContext());
        inputDailyBudget.setText(daily > 0 ? Formatters.editAmount(daily) : "");
        inputBudget.setText(monthly > 0 ? Formatters.editAmount(monthly) : "");
    }

    private void saveUsdRate() {
        double rate = AddEditTransactionActivity.parseAmount(AddEditTransactionActivity.textOf(inputUsdRate));
        if (rate <= 0) {
            layoutUsdRate.setError(getString(R.string.error_rate));
            return;
        }
        layoutUsdRate.setError(null);
        Prefs.setUsdRate(requireContext(), rate);
        showBudgetLimits(); // dollar limits change when the rate does
        Toast.makeText(requireContext(), getString(R.string.rate_saved, Formatters.plainAmount(rate)),
                Toast.LENGTH_SHORT).show();
    }

    private void saveBudget() {
        Double daily = readLimit(layoutDailyBudget, inputDailyBudget, Prefs.getDailyBudget(requireContext()));
        Double monthly = readLimit(layoutBudget, inputBudget, Prefs.getBudget(requireContext()));
        if (daily == null || monthly == null) return;

        Prefs.setDailyBudget(requireContext(), daily);
        Prefs.setBudget(requireContext(), monthly);
        Prefs.resetAlertLevels(requireContext());
        Toast.makeText(requireContext(), R.string.budget_saved, Toast.LENGTH_SHORT).show();
        requestNotificationsIfNeeded();
        BudgetAlerts.check(requireContext()); // alert right away if already over a new limit
    }

    /**
     * Reads a limit typed in the shown currency and returns it in rupees. Empty means
     * "no limit" (0); an untouched field keeps {@code saved} exactly. Returns null on bad input.
     */
    @Nullable
    private Double readLimit(TextInputLayout layout, TextInputEditText input, double saved) {
        String text = AddEditTransactionActivity.textOf(input);
        double value = text.isEmpty() ? 0 : AddEditTransactionActivity.parseAmount(text);
        if (!text.isEmpty() && value <= 0) {
            layout.setError(getString(R.string.error_budget));
            return null;
        }
        layout.setError(null);
        if (saved > 0 && text.equals(Formatters.editAmount(saved))) return saved;
        return Formatters.fromDisplay(value);
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void updateSmsStatus() {
        boolean granted = SmsSender.hasSmsPermission(requireContext());
        textSmsStatus.setText(granted ? R.string.sms_permission_granted : R.string.sms_permission_missing);
        buttonGrantSms.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    /** SEND_SMS, plus notification permission on Android 13+ for "reminder sent" alerts. */
    static String[] reminderPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.SEND_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return permissions.toArray(new String[0]);
    }
}
