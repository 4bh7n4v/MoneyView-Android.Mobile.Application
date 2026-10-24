package com.example.expensetracker;

/**
 * One money movement between the user and a person.
 * LENT increases what the person owes; RECEIVED decreases it.
 */
public class Loan {

    public static final String TYPE_LENT = "LENT";
    public static final String TYPE_RECEIVED = "RECEIVED";

    private long id;
    private long personId;
    private double amount;
    private String type;
    private long date;
    private String note;

    public Loan(long id, long personId, double amount, String type, long date, String note) {
        this.id = id;
        this.personId = personId;
        this.amount = amount;
        this.type = type;
        this.date = date;
        this.note = note;
    }

    public boolean isLent() { return TYPE_LENT.equals(type); }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getPersonId() { return personId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
