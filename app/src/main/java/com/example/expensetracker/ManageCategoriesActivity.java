package com.example.expensetracker;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * Add or delete the expense and income categories offered in the Add transaction screen.
 */
public class ManageCategoriesActivity extends AppCompatActivity {

    private static final int MAX_NAME_LENGTH = 24;

    private AppDbHelper db;
    private MaterialButtonToggleGroup toggleType;
    private LinearLayout containerCategories;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_categories);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        setTitle(R.string.manage_categories);

        db = AppDbHelper.getInstance(this);
        toggleType = findViewById(R.id.toggle_type);
        containerCategories = findViewById(R.id.container_categories);

        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) refresh();
        });
        findViewById(R.id.fab_add_category).setOnClickListener(v -> showAddDialog());
        refresh();
    }

    private String selectedType() {
        return toggleType.getCheckedButtonId() == R.id.button_income
                ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE;
    }

    private void refresh() {
        containerCategories.removeAllViews();
        List<String> categories = db.getCategories(selectedType());
        LayoutInflater inflater = getLayoutInflater();
        for (String name : categories) {
            View row = inflater.inflate(R.layout.item_category, containerCategories, false);
            ((TextView) row.findViewById(R.id.text_icon)).setText(name.substring(0, 1).toUpperCase());
            ((TextView) row.findViewById(R.id.text_name)).setText(name);
            int used = db.countTransactionsInCategory(name, selectedType());
            ((TextView) row.findViewById(R.id.text_usage)).setText(
                    getResources().getQuantityString(R.plurals.category_usage, used, used));
            row.findViewById(R.id.button_delete).setOnClickListener(v -> confirmDelete(name, used, categories.size()));
            containerCategories.addView(row);
        }
    }

    private void confirmDelete(String name, int used, int total) {
        if (total <= 1) {
            Toast.makeText(this, R.string.error_last_category, Toast.LENGTH_LONG).show();
            return;
        }
        String message = used > 0
                ? getResources().getQuantityString(R.plurals.delete_category_used, used, name, used)
                : getString(R.string.delete_category_unused, name);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_category_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    db.deleteCategory(name, selectedType());
                    Toast.makeText(this, getString(R.string.category_deleted, name), Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    private void showAddDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_category, null);
        TextInputLayout layoutName = view.findViewById(R.id.layout_name);
        TextInputEditText inputName = view.findViewById(R.id.input_name);
        boolean income = Transaction.TYPE_INCOME.equals(selectedType());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(income ? R.string.add_income_category : R.string.add_expense_category)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.add, null) // handled below so errors keep the dialog open
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String name = AddEditTransactionActivity.textOf(inputName);
            if (TextUtils.isEmpty(name)) {
                layoutName.setError(getString(R.string.error_category_name));
                return;
            }
            if (name.length() > MAX_NAME_LENGTH) {
                layoutName.setError(getString(R.string.error_category_length, MAX_NAME_LENGTH));
                return;
            }
            if (!db.addCategory(name, selectedType())) {
                layoutName.setError(getString(R.string.error_category_exists));
                return;
            }
            dialog.dismiss();
            Toast.makeText(this, getString(R.string.category_added, name), Toast.LENGTH_SHORT).show();
            refresh();
        }));
        dialog.show();
    }
}
