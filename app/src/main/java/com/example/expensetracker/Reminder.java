package com.example.expensetracker;

/**
 * An SMS message sent to a person at a set time, once or on a repeating schedule.
 */
public class Reminder {

    public static final String FREQ_ONCE = "ONCE";
    public static final String FREQ_DAILY = "DAILY";
    public static final String FREQ_WEEKLY = "WEEKLY";
    public static final String FREQ_MONTHLY = "MONTHLY";

    /** Same order as R.array.reminder_frequencies. */
    public static final String[] FREQUENCIES = {FREQ_ONCE, FREQ_DAILY, FREQ_WEEKLY, FREQ_MONTHLY};

    private long id;
    private long personId;
    private String message;
    private String frequency;
    private long nextAt;     // epoch millis of the next send
    private boolean enabled;
    private long lastSentAt; // 0 = never

    public Reminder(long id, long personId, String message, String frequency,
                    long nextAt, boolean enabled, long lastSentAt) {
        this.id = id;
        this.personId = personId;
        this.message = message;
        this.frequency = frequency;
        this.nextAt = nextAt;
        this.enabled = enabled;
        this.lastSentAt = lastSentAt;
    }

    public boolean isRepeating() { return !FREQ_ONCE.equals(frequency); }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getPersonId() { return personId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public long getNextAt() { return nextAt; }
    public void setNextAt(long nextAt) { this.nextAt = nextAt; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(long lastSentAt) { this.lastSentAt = lastSentAt; }
}
