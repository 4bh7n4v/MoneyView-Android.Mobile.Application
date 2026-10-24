package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Calendar;

/**
 * The month being viewed, shared by the Transactions and Charts tabs.
 */
public class MonthViewModel extends ViewModel {

    private final MutableLiveData<Calendar> month =
            new MutableLiveData<>(Formatters.startOfMonth(Calendar.getInstance()));

    public LiveData<Calendar> getMonth() {
        return month;
    }

    public Calendar current() {
        return month.getValue();
    }

    public void setMonth(Calendar newMonth) {
        month.setValue(Formatters.startOfMonth(newMonth));
    }

    public void shift(int months) {
        Calendar c = (Calendar) month.getValue().clone();
        c.add(Calendar.MONTH, months);
        month.setValue(c);
    }
}
