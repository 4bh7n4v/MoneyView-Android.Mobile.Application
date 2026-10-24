package com.example.expensetracker;

/**
 * Someone the user lends money to or receives money from.
 */
public class Person {

    private long id;
    private String name;
    private String phone;
    private String note;

    // Computed by queries, not stored in the people table.
    private double balance;       // positive = they owe the user
    private int activeReminders;

    public Person(long id, String name, String phone, String note) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.note = note;
    }

    public Person(String name, String phone, String note) {
        this(-1, name, phone, note);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public int getActiveReminders() { return activeReminders; }
    public void setActiveReminders(int activeReminders) { this.activeReminders = activeReminders; }
}
