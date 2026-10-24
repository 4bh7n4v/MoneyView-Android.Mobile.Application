package com.example.expensetracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite helper that owns every table in the app:
 * transactions (income and expenses), people, loans and reminders.
 */
public class AppDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "expenses.db";
    // v1: "expenses" table only. v2: transactions with a type column, plus people, loans, reminders.
    // v3: user-editable categories table.
    private static final int DB_VERSION = 3;

    private static AppDbHelper instance;

    private final Context appContext; // for the default category names in strings.xml

    public static synchronized AppDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AppDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private AppDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        appContext = context;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true); // needed for ON DELETE CASCADE
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transactions ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, "
                + "amount REAL NOT NULL, "
                + "category TEXT NOT NULL, "
                + "date INTEGER NOT NULL, "
                + "note TEXT, "
                + "type TEXT NOT NULL DEFAULT 'EXPENSE')");
        db.execSQL("CREATE INDEX idx_transactions_date ON transactions(date)");
        createLendingTables(db);
        createCategoriesTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Keep existing expenses: rename the table and mark every old row as an expense.
            db.execSQL("ALTER TABLE expenses RENAME TO transactions");
            db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'");
            db.execSQL("DROP INDEX IF EXISTS idx_expenses_date");
            db.execSQL("CREATE INDEX idx_transactions_date ON transactions(date)");
            createLendingTables(db);
        }
        if (oldVersion < 3) {
            createCategoriesTable(db);
        }
    }

    private void createLendingTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE people ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "phone TEXT NOT NULL, "
                + "note TEXT)");
        db.execSQL("CREATE TABLE loans ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "person_id INTEGER NOT NULL REFERENCES people(_id) ON DELETE CASCADE, "
                + "amount REAL NOT NULL, "
                + "type TEXT NOT NULL, "
                + "date INTEGER NOT NULL, "
                + "note TEXT)");
        db.execSQL("CREATE INDEX idx_loans_person ON loans(person_id)");
        db.execSQL("CREATE TABLE reminders ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "person_id INTEGER NOT NULL REFERENCES people(_id) ON DELETE CASCADE, "
                + "message TEXT NOT NULL, "
                + "frequency TEXT NOT NULL, "
                + "next_at INTEGER NOT NULL, "
                + "enabled INTEGER NOT NULL DEFAULT 1, "
                + "last_sent_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_reminders_next ON reminders(enabled, next_at)");
    }

    /** Creates the categories table and fills it with the default names from strings.xml. */
    private void createCategoriesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE categories ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "type TEXT NOT NULL)");
        seedCategories(db, Transaction.TYPE_EXPENSE, R.array.expense_categories);
        seedCategories(db, Transaction.TYPE_INCOME, R.array.income_categories);
    }

    private void seedCategories(SQLiteDatabase db, String type, int arrayRes) {
        for (String name : appContext.getResources().getStringArray(arrayRes)) {
            ContentValues v = new ContentValues();
            v.put("name", name);
            v.put("type", type);
            db.insert("categories", null, v);
        }
    }

    // ---------------------------------------------------------------- categories

    /** Category names of one type, in the order they were added. */
    public List<String> getCategories(String type) {
        List<String> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("categories", new String[]{"name"},
                "type = ?", new String[]{type}, null, null, "_id ASC")) {
            while (c.moveToNext()) {
                list.add(c.getString(0));
            }
        }
        return list;
    }

    /** Adds a category; returns false if one with the same name (ignoring case) already exists. */
    public boolean addCategory(String name, String type) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM categories WHERE type = ? AND name = ? COLLATE NOCASE",
                new String[]{type, name})) {
            if (c.moveToFirst()) return false;
        }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("type", type);
        getWritableDatabase().insert("categories", null, v);
        return true;
    }

    /** Removes a category from the pick list. Transactions that used it keep the name. */
    public void deleteCategory(String name, String type) {
        getWritableDatabase().delete("categories", "name = ? AND type = ?", new String[]{name, type});
    }

    public int countTransactionsInCategory(String name, String type) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE category = ? AND type = ?",
                new String[]{name, type})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ---------------------------------------------------------------- transactions

    public long insertTransaction(Transaction t) {
        long id = getWritableDatabase().insert("transactions", null, toValues(t));
        t.setId(id);
        return id;
    }

    public void updateTransaction(Transaction t) {
        getWritableDatabase().update("transactions", toValues(t), "_id = ?", args(t.getId()));
    }

    public void deleteTransaction(long id) {
        getWritableDatabase().delete("transactions", "_id = ?", args(id));
    }

    public Transaction getTransaction(long id) {
        try (Cursor c = getReadableDatabase().query("transactions", null, "_id = ?", args(id),
                null, null, null)) {
            return c.moveToFirst() ? transactionFrom(c) : null;
        }
    }

    /** Transactions with start <= date < end, newest first. */
    public List<Transaction> getTransactionsBetween(long start, long end) {
        List<Transaction> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("transactions", null,
                "date >= ? AND date < ?", args(start, end),
                null, null, "date DESC, _id DESC")) {
            while (c.moveToNext()) {
                list.add(transactionFrom(c));
            }
        }
        return list;
    }

    /** Returns {income, expense} totals for start <= date < end. */
    public double[] getTotals(long start, long end) {
        double[] totals = new double[2];
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT type, SUM(amount) FROM transactions"
                        + " WHERE date >= ? AND date < ? GROUP BY type", args(start, end))) {
            while (c.moveToNext()) {
                if (Transaction.TYPE_INCOME.equals(c.getString(0))) {
                    totals[0] += c.getDouble(1);
                } else {
                    totals[1] += c.getDouble(1);
                }
            }
        }
        return totals;
    }

    /** Total per category of one type for start <= date < end, largest first. */
    public Map<String, Double> getCategoryTotals(long start, long end, String type) {
        Map<String, Double> totals = new LinkedHashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT category, SUM(amount) AS total FROM transactions"
                        + " WHERE date >= ? AND date < ? AND type = ?"
                        + " GROUP BY category ORDER BY total DESC",
                new String[]{String.valueOf(start), String.valueOf(end), type})) {
            while (c.moveToNext()) {
                totals.put(c.getString(0), c.getDouble(1));
            }
        }
        return totals;
    }

    /** The largest single expenses for start <= date < end. */
    public List<Transaction> getTopExpenses(long start, long end, int limit) {
        List<Transaction> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("transactions", null,
                "date >= ? AND date < ? AND type = ?",
                new String[]{String.valueOf(start), String.valueOf(end), Transaction.TYPE_EXPENSE},
                null, null, "amount DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                list.add(transactionFrom(c));
            }
        }
        return list;
    }

    private ContentValues toValues(Transaction t) {
        ContentValues v = new ContentValues();
        v.put("title", t.getTitle());
        v.put("amount", t.getAmount());
        v.put("category", t.getCategory());
        v.put("date", t.getDate());
        v.put("note", t.getNote());
        v.put("type", t.getType());
        return v;
    }

    private Transaction transactionFrom(Cursor c) {
        return new Transaction(getLong(c, "_id"), getString(c, "title"), getDouble(c, "amount"),
                getString(c, "category"), getLong(c, "date"), getString(c, "note"), getString(c, "type"));
    }

    // ---------------------------------------------------------------- people

    public long insertPerson(Person p) {
        long id = getWritableDatabase().insert("people", null, toValues(p));
        p.setId(id);
        return id;
    }

    public void updatePerson(Person p) {
        getWritableDatabase().update("people", toValues(p), "_id = ?", args(p.getId()));
    }

    /** Also deletes the person's loans and reminders (ON DELETE CASCADE). */
    public void deletePerson(long id) {
        getWritableDatabase().delete("people", "_id = ?", args(id));
    }

    public Person getPerson(long id) {
        try (Cursor c = getReadableDatabase().query("people", null, "_id = ?", args(id),
                null, null, null)) {
            return c.moveToFirst() ? personFrom(c) : null;
        }
    }

    /** Everyone, A–Z, with their outstanding balance and number of active reminders. */
    public List<Person> getPeopleWithBalances() {
        List<Person> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT p.*, "
                        + "COALESCE((SELECT SUM(CASE WHEN l.type = 'LENT' THEN l.amount ELSE -l.amount END)"
                        + " FROM loans l WHERE l.person_id = p._id), 0) AS balance, "
                        + "(SELECT COUNT(*) FROM reminders r WHERE r.person_id = p._id AND r.enabled = 1)"
                        + " AS active_reminders "
                        + "FROM people p ORDER BY p.name COLLATE NOCASE", null)) {
            while (c.moveToNext()) {
                Person p = personFrom(c);
                p.setBalance(getDouble(c, "balance"));
                p.setActiveReminders((int) getLong(c, "active_reminders"));
                list.add(p);
            }
        }
        return list;
    }

    /** Positive = the person owes the user; negative = the user owes them. */
    public double getBalance(long personId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN type = 'LENT' THEN amount ELSE -amount END), 0)"
                        + " FROM loans WHERE person_id = ?", args(personId))) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    private ContentValues toValues(Person p) {
        ContentValues v = new ContentValues();
        v.put("name", p.getName());
        v.put("phone", p.getPhone());
        v.put("note", p.getNote());
        return v;
    }

    private Person personFrom(Cursor c) {
        return new Person(getLong(c, "_id"), getString(c, "name"), getString(c, "phone"), getString(c, "note"));
    }

    // ---------------------------------------------------------------- loans

    public long insertLoan(Loan l) {
        long id = getWritableDatabase().insert("loans", null, toValues(l));
        l.setId(id);
        return id;
    }

    public void updateLoan(Loan l) {
        getWritableDatabase().update("loans", toValues(l), "_id = ?", args(l.getId()));
    }

    public void deleteLoan(long id) {
        getWritableDatabase().delete("loans", "_id = ?", args(id));
    }

    /** A person's loan records, newest first. */
    public List<Loan> getLoans(long personId) {
        List<Loan> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("loans", null, "person_id = ?", args(personId),
                null, null, "date DESC, _id DESC")) {
            while (c.moveToNext()) {
                list.add(new Loan(getLong(c, "_id"), getLong(c, "person_id"), getDouble(c, "amount"),
                        getString(c, "type"), getLong(c, "date"), getString(c, "note")));
            }
        }
        return list;
    }

    private ContentValues toValues(Loan l) {
        ContentValues v = new ContentValues();
        v.put("person_id", l.getPersonId());
        v.put("amount", l.getAmount());
        v.put("type", l.getType());
        v.put("date", l.getDate());
        v.put("note", l.getNote());
        return v;
    }

    // ---------------------------------------------------------------- reminders

    public long insertReminder(Reminder r) {
        long id = getWritableDatabase().insert("reminders", null, toValues(r));
        r.setId(id);
        return id;
    }

    public void updateReminder(Reminder r) {
        getWritableDatabase().update("reminders", toValues(r), "_id = ?", args(r.getId()));
    }

    public void deleteReminder(long id) {
        getWritableDatabase().delete("reminders", "_id = ?", args(id));
    }

    public List<Reminder> getReminders(long personId) {
        return queryReminders("person_id = ?", args(personId));
    }

    /** Enabled reminders whose send time has arrived. */
    public List<Reminder> getDueReminders(long now) {
        return queryReminders("enabled = 1 AND next_at <= ?", args(now));
    }

    /** Earliest upcoming send time across all enabled reminders, or -1 if none. */
    public long getNextReminderTime() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT MIN(next_at) FROM reminders WHERE enabled = 1", null)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : -1;
        }
    }

    private List<Reminder> queryReminders(String where, String[] whereArgs) {
        List<Reminder> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("reminders", null, where, whereArgs,
                null, null, "next_at ASC")) {
            while (c.moveToNext()) {
                list.add(new Reminder(getLong(c, "_id"), getLong(c, "person_id"), getString(c, "message"),
                        getString(c, "frequency"), getLong(c, "next_at"), getLong(c, "enabled") == 1,
                        getLong(c, "last_sent_at")));
            }
        }
        return list;
    }

    private ContentValues toValues(Reminder r) {
        ContentValues v = new ContentValues();
        v.put("person_id", r.getPersonId());
        v.put("message", r.getMessage());
        v.put("frequency", r.getFrequency());
        v.put("next_at", r.getNextAt());
        v.put("enabled", r.isEnabled() ? 1 : 0);
        v.put("last_sent_at", r.getLastSentAt());
        return v;
    }

    // ---------------------------------------------------------------- cursor helpers

    private static String[] args(long... values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = String.valueOf(values[i]);
        return out;
    }

    private static long getLong(Cursor c, String column) {
        return c.getLong(c.getColumnIndexOrThrow(column));
    }

    private static double getDouble(Cursor c, String column) {
        return c.getDouble(c.getColumnIndexOrThrow(column));
    }

    private static String getString(Cursor c, String column) {
        return c.getString(c.getColumnIndexOrThrow(column));
    }
}
