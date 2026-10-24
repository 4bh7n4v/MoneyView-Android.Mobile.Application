package com.example.expensetracker;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * A person's profile: contact details, balance, lend/receive records and scheduled SMS reminders.
 */
public class PersonDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_person_id";

    private AppDbHelper db;
    private long personId;
    private Person person;
    private double balance;

    private TextView textAvatar, textName, textPhone, textNote, textBalance;
    private TextView textLoansEmpty, textRemindersEmpty;
    private LinearLayout containerLoans, containerReminders;

    private Reminder pendingSend; // "Send now" waiting on the permission prompt
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = AppDbHelper.getInstance(this);
        personId = getIntent().getLongExtra(EXTRA_ID, -1);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

        textAvatar = findViewById(R.id.text_avatar);
        textName = findViewById(R.id.text_name);
        textPhone = findViewById(R.id.text_phone);
        textNote = findViewById(R.id.text_note);
        textBalance = findViewById(R.id.text_balance);
        textLoansEmpty = findViewById(R.id.text_loans_empty);
        textRemindersEmpty = findViewById(R.id.text_reminders_empty);
        containerLoans = findViewById(R.id.container_loans);
        containerReminders = findViewById(R.id.container_reminders);

        findViewById(R.id.button_call).setOnClickListener(v ->
                open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + person.getPhone()))));
        findViewById(R.id.button_message).setOnClickListener(v ->
                open(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + person.getPhone()))));
        findViewById(R.id.button_add_loan).setOnClickListener(v -> showLoanDialog(null));
        findViewById(R.id.button_add_reminder).setOnClickListener(v -> showReminderDialog(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // picks up edits made in PersonEditActivity
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_person_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_edit) {
            startActivity(new Intent(this, PersonEditActivity.class)
                    .putExtra(PersonEditActivity.EXTRA_ID, personId));
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_person_title)
                    .setMessage(getString(R.string.delete_person_message, person.getName()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        db.deletePerson(personId);
                        ReminderScheduler.scheduleNext(this);
                        Toast.makeText(this, R.string.person_deleted, Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refresh() {
        person = db.getPerson(personId);
        if (person == null) {
            finish();
            return;
        }
        balance = db.getBalance(personId);

        setTitle(person.getName());
        textAvatar.setText(person.getName().substring(0, 1).toUpperCase());
        textName.setText(person.getName());
        textPhone.setText(person.getPhone());
        textNote.setText(person.getNote());
        textNote.setVisibility(TextUtils.isEmpty(person.getNote()) ? View.GONE : View.VISIBLE);
        PersonAdapter.bindBalance(textBalance, balance);

        bindLoans(db.getLoans(personId));
        bindReminders(db.getReminders(personId));
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- loan records

    private void bindLoans(List<Loan> loans) {
        containerLoans.removeAllViews();
        textLoansEmpty.setVisibility(loans.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        for (Loan loan : loans) {
            View row = inflater.inflate(R.layout.item_loan, containerLoans, false);
            ((TextView) row.findViewById(R.id.text_type)).setText(
                    loan.isLent() ? R.string.loan_you_gave : R.string.loan_you_received);
            String details = Formatters.date(loan.getDate());
            if (!TextUtils.isEmpty(loan.getNote())) details += " • " + loan.getNote();
            ((TextView) row.findViewById(R.id.text_details)).setText(details);

            TextView amount = row.findViewById(R.id.text_amount);
            amount.setText(Formatters.money(loan.getAmount()));
            amount.setTextColor(ContextCompat.getColor(this, loan.isLent() ? R.color.expense : R.color.income));

            row.setOnClickListener(v -> showLoanDialog(loan));
            row.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete_record_title)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            db.deleteLoan(loan.getId());
                            refresh();
                        })
                        .show();
                return true;
            });
            containerLoans.addView(row);
        }
    }

    private void showLoanDialog(@Nullable Loan existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_loan, null);
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggle_loan_type);
        TextInputLayout layoutAmount = view.findViewById(R.id.layout_amount);
        TextInputEditText inputAmount = view.findViewById(R.id.input_amount);
        TextInputEditText inputDate = view.findViewById(R.id.input_date);
        TextInputEditText inputNote = view.findViewById(R.id.input_note);
        layoutAmount.setHint(getString(R.string.hint_amount, Formatters.currencySymbol()));

        Calendar date = Calendar.getInstance();
        if (existing != null) {
            toggle.check(existing.isLent() ? R.id.button_lent : R.id.button_received);
            inputAmount.setText(Formatters.editAmount(existing.getAmount()));
            inputNote.setText(existing.getNote());
            date.setTimeInMillis(existing.getDate());
        }
        inputDate.setText(Formatters.date(date.getTimeInMillis()));
        inputDate.setOnClickListener(v -> new DatePickerDialog(this, (dp, y, m, d) -> {
            date.set(y, m, d);
            inputDate.setText(Formatters.date(date.getTimeInMillis()));
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add_record : R.string.edit_record)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, null) // click handled below so errors keep it open
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String amountText = AddEditTransactionActivity.textOf(inputAmount);
            double amount = AddEditTransactionActivity.parseAmount(amountText);
            if (amount <= 0) {
                layoutAmount.setError(getString(R.string.error_amount));
                return;
            }
            // Stored in rupees; an untouched amount keeps its exact value.
            amount = existing != null && amountText.equals(Formatters.editAmount(existing.getAmount()))
                    ? existing.getAmount()
                    : Formatters.fromDisplay(amount);
            String type = toggle.getCheckedButtonId() == R.id.button_received ? Loan.TYPE_RECEIVED : Loan.TYPE_LENT;
            String note = AddEditTransactionActivity.textOf(inputNote);
            if (existing == null) {
                db.insertLoan(new Loan(-1, personId, amount, type, date.getTimeInMillis(), note));
            } else {
                existing.setAmount(amount);
                existing.setType(type);
                existing.setDate(date.getTimeInMillis());
                existing.setNote(note);
                db.updateLoan(existing);
            }
            dialog.dismiss();
            refresh();
        }));
        dialog.show();
    }

    // ---------------------------------------------------------------- reminders

    private void bindReminders(List<Reminder> reminders) {
        containerReminders.removeAllViews();
        textRemindersEmpty.setVisibility(reminders.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        for (Reminder reminder : reminders) {
            View row = inflater.inflate(R.layout.item_reminder, containerReminders, false);
            ((TextView) row.findViewById(R.id.text_message)).setText(
                    SmsSender.fillPlaceholders(reminder.getMessage(), person, balance));

            String frequency = ReminderScheduler.frequencyLabel(this, reminder.getFrequency());
            String schedule = reminder.isEnabled()
                    ? getString(R.string.reminder_schedule, frequency, Formatters.dateTime(reminder.getNextAt()))
                    : getString(R.string.reminder_paused, frequency);
            ((TextView) row.findViewById(R.id.text_schedule)).setText(schedule);

            TextView lastSent = row.findViewById(R.id.text_last_sent);
            if (reminder.getLastSentAt() > 0) {
                lastSent.setText(getString(R.string.reminder_last_sent, Formatters.dateTime(reminder.getLastSentAt())));
                lastSent.setVisibility(View.VISIBLE);
            }

            MaterialSwitch toggle = row.findViewById(R.id.switch_enabled);
            toggle.setChecked(reminder.isEnabled());
            toggle.setOnCheckedChangeListener((button, checked) -> setReminderEnabled(reminder, checked));

            row.findViewById(R.id.button_send_now).setOnClickListener(v -> sendNow(reminder));
            row.findViewById(R.id.button_edit).setOnClickListener(v -> showReminderDialog(reminder));
            row.findViewById(R.id.button_delete).setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.delete_reminder_title)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.delete, (d, w) -> {
                                db.deleteReminder(reminder.getId());
                                ReminderScheduler.scheduleNext(this);
                                refresh();
                            })
                            .show());
            containerReminders.addView(row);
        }
    }

    private void setReminderEnabled(Reminder reminder, boolean enabled) {
        long now = System.currentTimeMillis();
        if (enabled && reminder.getNextAt() <= now) {
            if (!reminder.isRepeating()) {
                // A one-time reminder in the past needs a new date before it can be switched on.
                Toast.makeText(this, R.string.error_pick_new_time, Toast.LENGTH_LONG).show();
                refresh();
                showReminderDialog(reminder);
                return;
            }
            reminder.setNextAt(ReminderScheduler.nextOccurrence(reminder.getNextAt(), reminder.getFrequency(), now));
        }
        reminder.setEnabled(enabled);
        db.updateReminder(reminder);
        ReminderScheduler.scheduleNext(this);
        if (enabled) requestPermissionsIfNeeded();
        refresh();
    }

    private void showReminderDialog(@Nullable Reminder existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_reminder, null);
        TextInputLayout layoutMessage = view.findViewById(R.id.layout_message);
        TextInputEditText inputMessage = view.findViewById(R.id.input_message);
        MaterialAutoCompleteTextView inputFrequency = view.findViewById(R.id.input_frequency);
        TextInputLayout layoutDate = view.findViewById(R.id.layout_date);
        TextInputEditText inputDate = view.findViewById(R.id.input_date);
        TextInputEditText inputTime = view.findViewById(R.id.input_time);

        String[] labels = getResources().getStringArray(R.array.reminder_frequencies);
        inputFrequency.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));

        Calendar when = Calendar.getInstance();
        if (existing != null) {
            inputMessage.setText(existing.getMessage());
            inputFrequency.setText(ReminderScheduler.frequencyLabel(this, existing.getFrequency()), false);
            when.setTimeInMillis(Math.max(existing.getNextAt(), System.currentTimeMillis() + 60 * 60 * 1000L));
        } else {
            inputMessage.setText(R.string.default_reminder_message);
            inputFrequency.setText(labels[3], false); // Monthly
            when.add(Calendar.HOUR_OF_DAY, 1);
        }
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);

        Runnable showWhen = () -> {
            inputDate.setText(Formatters.date(when.getTimeInMillis()));
            inputTime.setText(Formatters.time(when.getTimeInMillis()));
        };
        showWhen.run();
        inputDate.setOnClickListener(v -> new DatePickerDialog(this, (dp, y, m, d) -> {
            when.set(y, m, d);
            showWhen.run();
        }, when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH)).show());
        inputTime.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, min) -> {
            when.set(Calendar.HOUR_OF_DAY, h);
            when.set(Calendar.MINUTE, min);
            showWhen.run();
        }, when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE), DateFormat.is24HourFormat(this)).show());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add_reminder : R.string.edit_reminder)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String message = AddEditTransactionActivity.textOf(inputMessage);
            int index = Arrays.asList(labels).indexOf(inputFrequency.getText().toString());
            String frequency = Reminder.FREQUENCIES[Math.max(0, index)];
            long now = System.currentTimeMillis();
            long nextAt = when.getTimeInMillis();

            layoutMessage.setError(null);
            layoutDate.setError(null);
            if (message.isEmpty()) {
                layoutMessage.setError(getString(R.string.error_message));
                return;
            }
            if (nextAt <= now) {
                if (Reminder.FREQ_ONCE.equals(frequency)) {
                    layoutDate.setError(getString(R.string.error_future_time));
                    return;
                }
                nextAt = ReminderScheduler.nextOccurrence(nextAt, frequency, now);
            }

            if (existing == null) {
                db.insertReminder(new Reminder(-1, personId, message, frequency, nextAt, true, 0));
            } else {
                existing.setMessage(message);
                existing.setFrequency(frequency);
                existing.setNextAt(nextAt);
                existing.setEnabled(true);
                db.updateReminder(existing);
            }
            ReminderScheduler.scheduleNext(this);
            dialog.dismiss();
            Toast.makeText(this, getString(R.string.reminder_saved, Formatters.dateTime(nextAt)),
                    Toast.LENGTH_LONG).show();
            requestPermissionsIfNeeded();
            refresh();
        }));
        dialog.show();
    }

    private void sendNow(Reminder reminder) {
        if (!SmsSender.hasSmsPermission(this)) {
            pendingSend = reminder;
            permissionLauncher.launch(SettingsFragment.reminderPermissions());
            return;
        }
        String message = SmsSender.fillPlaceholders(reminder.getMessage(), person, balance);
        if (SmsSender.send(this, person.getPhone(), message)) {
            reminder.setLastSentAt(System.currentTimeMillis());
            db.updateReminder(reminder);
            Toast.makeText(this, getString(R.string.message_sent, person.getName()), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.message_failed, Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void requestPermissionsIfNeeded() {
        if (!SmsSender.hasSmsPermission(this)) {
            permissionLauncher.launch(SettingsFragment.reminderPermissions());
        }
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        if (SmsSender.hasSmsPermission(this)) {
            if (pendingSend != null) sendNow(pendingSend);
        } else {
            Toast.makeText(this, R.string.sms_permission_denied, Toast.LENGTH_LONG).show();
        }
        pendingSend = null;
    }
}
