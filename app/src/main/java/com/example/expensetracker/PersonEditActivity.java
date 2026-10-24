package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Register a new person (name, phone, profile note) or edit an existing one.
 */
public class PersonEditActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_person_id";

    private AppDbHelper db;
    private Person existing; // null when adding

    private TextInputLayout layoutName, layoutPhone;
    private TextInputEditText inputName, inputPhone, inputNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_edit);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = AppDbHelper.getInstance(this);
        layoutName = findViewById(R.id.layout_name);
        layoutPhone = findViewById(R.id.layout_phone);
        inputName = findViewById(R.id.input_name);
        inputPhone = findViewById(R.id.input_phone);
        inputNote = findViewById(R.id.input_note);

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (id != -1) {
            existing = db.getPerson(id);
        }
        if (existing != null) {
            setTitle(R.string.edit_person);
            if (savedInstanceState == null) {
                inputName.setText(existing.getName());
                inputPhone.setText(existing.getPhone());
                inputNote.setText(existing.getNote());
            }
        } else {
            setTitle(R.string.add_person);
        }

        findViewById(R.id.button_save).setOnClickListener(v -> save());
    }

    private void save() {
        String name = AddEditTransactionActivity.textOf(inputName);
        String phone = AddEditTransactionActivity.textOf(inputPhone);
        String note = AddEditTransactionActivity.textOf(inputNote);

        layoutName.setError(null);
        layoutPhone.setError(null);
        boolean valid = true;
        if (TextUtils.isEmpty(name)) {
            layoutName.setError(getString(R.string.error_name));
            valid = false;
        }
        if (!isValidPhone(phone)) {
            layoutPhone.setError(getString(R.string.error_phone));
            valid = false;
        }
        if (!valid) return;

        if (existing == null) {
            Person person = new Person(name, phone, note);
            db.insertPerson(person);
            Toast.makeText(this, R.string.person_added, Toast.LENGTH_SHORT).show();
            // Go straight to the new profile so records and reminders can be added.
            startActivity(new Intent(this, PersonDetailActivity.class)
                    .putExtra(PersonDetailActivity.EXTRA_ID, person.getId()));
        } else {
            existing.setName(name);
            existing.setPhone(phone);
            existing.setNote(note);
            db.updatePerson(existing);
            Toast.makeText(this, R.string.person_updated, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    /** Digits with optional leading +, spaces or dashes; 7–15 digits in total. */
    private static boolean isValidPhone(String phone) {
        if (!phone.matches("\\+?[0-9 \\-]+")) return false;
        int digits = phone.replaceAll("[^0-9]", "").length();
        return digits >= 7 && digits <= 15;
    }
}
