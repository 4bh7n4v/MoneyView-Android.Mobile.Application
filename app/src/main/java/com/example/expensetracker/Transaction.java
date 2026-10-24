package com.example.expensetracker;

/**
 * An income or expense entry stored in the "transactions" table.
 */
public class Transaction {

    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_INCOME = "INCOME";

    private long id;
    private String title;
    private double amount;
    private String category;
    private long date; // epoch millis
    private String note;
    private String type;

    public Transaction(long id, String title, double amount, String category, long date, String note, String type) {
        this.id = id;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.date = date;
        this.note = note;
        this.type = type;
    }

    public Transaction(String title, double amount, String category, long date, String note, String type) {
        this(-1, title, amount, category, date, note, type);
    }

    public boolean isIncome() { return TYPE_INCOME.equals(type); }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
