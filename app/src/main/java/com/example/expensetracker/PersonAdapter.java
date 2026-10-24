package com.example.expensetracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * One row per person on the Lending tab, with what they owe and a bell if reminders are on.
 */
public class PersonAdapter extends RecyclerView.Adapter<PersonAdapter.ViewHolder> {

    public interface Listener {
        void onPersonClick(Person person);
    }

    private final List<Person> items = new ArrayList<>();
    private final Listener listener;

    public PersonAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Person> people) {
        items.clear();
        items.addAll(people);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_person, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Person p = items.get(position);
        Context context = holder.itemView.getContext();
        holder.avatar.setText(p.getName().substring(0, 1).toUpperCase());
        holder.name.setText(p.getName());
        holder.phone.setText(p.getPhone());
        bindBalance(holder.balance, p.getBalance());
        holder.bell.setVisibility(p.getActiveReminders() > 0 ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onPersonClick(p));
    }

    /** "Owes you ₹X" in green, "You owe ₹X" in red, or "Settled". Shared with the detail screen. */
    public static void bindBalance(TextView view, double balance) {
        Context context = view.getContext();
        if (Math.abs(balance) < 0.005) {
            view.setText(R.string.balance_settled);
            view.setTextColor(ContextCompat.getColor(context, R.color.settled));
        } else if (balance > 0) {
            view.setText(context.getString(R.string.balance_owes_you, Formatters.money(balance)));
            view.setTextColor(ContextCompat.getColor(context, R.color.income));
        } else {
            view.setText(context.getString(R.string.balance_you_owe, Formatters.money(-balance)));
            view.setTextColor(ContextCompat.getColor(context, R.color.expense));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView avatar, name, phone, balance;
        final ImageView bell;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.text_avatar);
            name = itemView.findViewById(R.id.text_name);
            phone = itemView.findViewById(R.id.text_phone);
            balance = itemView.findViewById(R.id.text_balance);
            bell = itemView.findViewById(R.id.image_bell);
        }
    }
}
