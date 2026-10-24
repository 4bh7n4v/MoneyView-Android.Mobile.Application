package com.example.expensetracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter that shows one row per income or expense.
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    public interface Listener {
        void onTransactionClick(Transaction transaction);
        void onTransactionLongClick(Transaction transaction);
    }

    private final List<Transaction> items = new ArrayList<>();
    private final Listener listener;

    public TransactionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Transaction> transactions) {
        items.clear();
        items.addAll(transactions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction t = items.get(position);
        holder.icon.setText(t.getCategory().substring(0, 1).toUpperCase());
        holder.title.setText(t.getTitle());
        holder.subtitle.setText(holder.itemView.getContext().getString(
                R.string.item_subtitle, t.getCategory(), Formatters.date(t.getDate())));

        String sign = t.isIncome() ? "+" : "−";
        holder.amount.setText(sign + Formatters.money(t.getAmount()));
        holder.amount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                t.isIncome() ? R.color.income : R.color.expense));

        holder.itemView.setOnClickListener(v -> listener.onTransactionClick(t));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onTransactionLongClick(t);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView icon, title, subtitle, amount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.text_icon);
            title = itemView.findViewById(R.id.text_title);
            subtitle = itemView.findViewById(R.id.text_subtitle);
            amount = itemView.findViewById(R.id.text_amount);
        }
    }
}
