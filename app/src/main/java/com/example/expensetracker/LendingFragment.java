package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Lending tab: totals to receive / to pay and the list of people.
 */
public class LendingFragment extends Fragment implements PersonAdapter.Listener {

    private AppDbHelper db;
    private PersonAdapter adapter;
    private TextView textToReceive, textToPay, textEmpty;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lending, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDbHelper.getInstance(requireContext());
        textToReceive = view.findViewById(R.id.text_to_receive);
        textToPay = view.findViewById(R.id.text_to_pay);
        textEmpty = view.findViewById(R.id.text_empty);

        adapter = new PersonAdapter(this);
        RecyclerView recycler = view.findViewById(R.id.recycler_people);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.fab_add_person).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PersonEditActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.tab_lending);
        refresh();
    }

    private void refresh() {
        List<Person> people = db.getPeopleWithBalances();
        double toReceive = 0, toPay = 0;
        for (Person p : people) {
            if (p.getBalance() > 0) toReceive += p.getBalance();
            else toPay -= p.getBalance();
        }
        textToReceive.setText(Formatters.money(toReceive));
        textToPay.setText(Formatters.money(toPay));
        textEmpty.setVisibility(people.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submit(people);
    }

    @Override
    public void onPersonClick(Person person) {
        Intent intent = new Intent(requireContext(), PersonDetailActivity.class);
        intent.putExtra(PersonDetailActivity.EXTRA_ID, person.getId());
        startActivity(intent);
    }
}
