package com.example.expensetracker;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.List;

/**
 * Form for creating a new income/expense or editing an existing one (when EXTRA_ID is passed).
 */
public class AddEditTransactionActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_transaction_id";
    private static final String STATE_DATE = "state_date";

    private AppDbHelper db;
    private Transaction existing; // null when adding
    private String originalAmountText = ""; // amount as first shown, to detect real edits
    private final Calendar selectedDate = Calendar.getInstance();

    private MaterialButtonToggleGroup toggleType;
    private TextInputLayout layoutTitle, layoutAmount, layoutCategory;
    private TextInputEditText inputTitle, inputAmount, inputDate, inputNote;
    private MaterialAutoCompleteTextView inputCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_transaction);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = AppDbHelper.getInstance(this);

        toggleType = findViewById(R.id.toggle_type);
        layoutTitle = findViewById(R.id.layout_title);
        layoutAmount = findViewById(R.id.layout_amount);
        layoutCategory = findViewById(R.id.layout_category);
        inputTitle = findViewById(R.id.input_title);
        inputAmount = findViewById(R.id.input_amount);
        inputCategory = findViewById(R.id.input_category);
        inputDate = findViewById(R.id.input_date);
        inputNote = findViewById(R.id.input_note);

        layoutAmount.setHint(getString(R.string.hint_amount, Formatters.currencySymbol()));

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (id != -1) {
            existing = db.getTransaction(id);
        }

        if (existing != null) {
            setTitle(existing.isIncome() ? R.string.edit_income : R.string.edit_expense);
            if (savedInstanceState == null) {
                toggleType.check(existing.isIncome() ? R.id.button_income : R.id.button_expense);
                inputTitle.setText(existing.getTitle());
                inputAmount.setText(Formatters.editAmount(existing.getAmount()));
                inputNote.setText(existing.getNote());
            }
            selectedDate.setTimeInMillis(existing.getDate());
            originalAmountText = Formatters.editAmount(existing.getAmount());
        } else {
            setTitle(R.string.add_transaction);
        }
        if (savedInstanceState != null) {
            selectedDate.setTimeInMillis(savedInstanceState.getLong(STATE_DATE));
        }

        updateCategoryOptions(existing != null && savedInstanceState == null ? existing.getCategory() : null);
        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) updateCategoryOptions(null);
        });

        showDate();
        inputDate.setOnClickListener(v -> pickDate());
        findViewById(R.id.button_save).setOnClickListener(v -> save());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_DATE, selectedDate.getTimeInMillis());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (existing != null) {
            getMenuInflater().inflate(R.menu.menu_delete, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_delete) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_transaction_title)
                    .setMessage(getString(R.string.delete_message, existing.getTitle()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        db.deleteTransaction(existing.getId());
                        Toast.makeText(this, R.string.transaction_deleted, Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String selectedType() {
        return toggleType.getCheckedButtonId() == R.id.button_income
                ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE;
    }

    /**
     * Shows the category list for the selected type. Keeps {@code preferred} (or the current
     * choice) if it belongs to the list, otherwise falls back to the first category.
     */
    private void updateCategoryOptions(String preferred) {
        List<String> categories = db.getCategories(selectedType());
        // An entry being edited keeps its category even if it was deleted from the list since.
        if (preferred != null && !categories.contains(preferred)) {
            categories.add(preferred);
        }
        inputCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories));

        String current = preferred != null ? preferred : inputCategory.getText().toString();
        if (categories.contains(current)) {
            inputCategory.setText(current, false);
        } else {
            inputCategory.setText(categories.isEmpty() ? "" : categories.get(0), false);
        }
    }

    private void pickDate() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate.set(year, month, day);
            showDate();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showDate() {
        inputDate.setText(Formatters.date(selectedDate.getTimeInMillis()));
    }

    private void save() {
        String title = textOf(inputTitle);
        String category = inputCategory.getText().toString().trim();
        String note = textOf(inputNote);

        layoutTitle.setError(null);
        layoutAmount.setError(null);
        layoutCategory.setError(null);

        boolean valid = true;
        if (TextUtils.isEmpty(title)) {
            layoutTitle.setError(getString(R.string.error_title));
            valid = false;
        }
        String amountText = textOf(inputAmount);
        double amount = parseAmount(amountText);
        if (amount <= 0) {
            layoutAmount.setError(getString(R.string.error_amount));
            valid = false;
        }
        if (TextUtils.isEmpty(category)) {
            layoutCategory.setError(getString(R.string.error_category));
            valid = false;
        }
        if (!valid) return;

        // Stored in rupees. An untouched amount keeps its exact value so USD rounding never drifts it.
        amount = existing != null && amountText.equals(originalAmountText)
                ? existing.getAmount()
                : Formatters.fromDisplay(amount);

        if (existing == null) {
            db.insertTransaction(new Transaction(title, amount, category,
                    selectedDate.getTimeInMillis(), note, selectedType()));
            Toast.makeText(this, R.string.transaction_added, Toast.LENGTH_SHORT).show();
        } else {
            existing.setTitle(title);
            existing.setAmount(amount);
            existing.setCategory(category);
            existing.setDate(selectedDate.getTimeInMillis());
            existing.setNote(note);
            existing.setType(selectedType());
            db.updateTransaction(existing);
            Toast.makeText(this, R.string.transaction_updated, Toast.LENGTH_SHORT).show();
        }
        BudgetAlerts.check(this);
        finish();
    }

    static double parseAmount(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
