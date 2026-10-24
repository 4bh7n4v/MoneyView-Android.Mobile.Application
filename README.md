# Expense Tracker

An Android app (Java) to track income and expenses, stay within budget, see where your money goes, and keep track of money lent to friends, with automatic SMS reminders to get it back.

Everything is stored on the phone. No account, no internet, no ads.

---

## Features

### 1. Income and expense tracking
- Add, edit and delete income and expenses (title, amount, category, date, note).
- Swipe between months; the dashboard shows **total income, total expenses and balance** for the month.
- Long-press to delete, with an **Undo** option.

### 2. Custom categories
- Comes with ready-made categories (Food, Transport, Bills, Salary, Freelance, …).
- **Add your own or delete ones you don't use** (Settings → Manage categories).
- Shows how many transactions use each category; deleting a category never deletes your transactions.

### 3. Search and filter
- Search by title, category or note.
- Filter chips: All, Income, Expenses, or any single category.

### 4. Budget limits with alerts
- Set a **daily limit** and a **monthly limit**.
- Progress bars on the dashboard turn **teal → amber (80%) → red (over limit)**.
- **Notifications** at 80% and when a limit is exceeded, shown once per day or month instead of on every new expense.

### 5. Charts with drill-down (teal theme)
Read the Charts tab from top to bottom:

```
Monthly summary  →  tap a month
   Top 3 categories (donut) + 3 biggest expenses
Weekly summary   →  tap a week
Daily summary    →  every day of that week
```

- 6-month income vs expense bar chart.
- Donut chart of the **top 3 spending categories**, with the rest grouped as "Others".
- Weekly bars (1–7, 8–14, 15–21, 22–28, 29–end) and a day-by-day breakdown.

### 6. Lending tracker (money given to people)
- Register a person: **name, phone number, profile note**.
- Record money you **gave** or **received**; the balance updates automatically ("Owes you ₹1,500").
- One-tap **Call** and **Message** buttons.
- Totals at the top: **To receive** and **To pay**.

### 7. Automatic SMS reminders
- Write a reminder message for each person and choose **Once, Daily, Weekly or Monthly**, with a date and time.
- `{name}` and `{amount}` are filled in with the person's name and what they owe:
  > Hi Rahul, just a friendly reminder about the ₹1,500.00 you owe me. Thanks!
- Sent **automatically in the background**, even when the app is closed. You get a notification each time.
- Pause or resume each reminder with a switch, or tap **Send now**.

### 8. Currency: INR ⇄ USD
- Switch between **Indian Rupee (₹)** and **US Dollar ($)**. All amounts are **converted**, not just relabelled.
- Set your own exchange rate (1 USD = ₹ …).
- Amounts are saved in rupees, so switching back and forth never loses accuracy.

### 9. Look and feel
- Material 3 design in teal, with **light and dark mode**.
- Works on Android 7.0 and newer, including Xiaomi/MIUI phones.

---

## What makes it unique

| | Typical expense apps | This app |
|---|---|---|
| Money lent to friends | Not tracked, or a simple list | Profiles, running balance, give/receive history |
| Getting money back | You have to remember to ask | **Scheduled SMS reminders sent automatically**, with the amount owed filled in |
| Charts | One summary chart | **Month → week → day drill-down** by tapping |
| Budget | Monthly only | **Daily and monthly** limits with smart, non-repeating alerts |
| Currency | Changes the symbol only | **Real INR ⇄ USD conversion** with your own rate |
| Privacy | Often needs an account or cloud | **100% offline**: data never leaves the phone |
| Dependencies | Third-party chart libraries | Charts drawn by the app itself; no extra libraries |

---

## Screens

| Tab | What it shows |
|---|---|
| **Transactions** | Dashboard (income, expenses, balance, budget bars), search, filters, list |
| **Charts** | Monthly → Top 3 → Weekly → Daily |
| **Lending** | People, balances, SMS reminders |
| **Settings** | Currency and rate, budget limits, categories, SMS permission |

---

## Tech stack

- **Language:** Java · **IDE:** Android Studio
- **Database:** SQLite with 5 tables: `transactions`, `categories`, `people`, `loans`, `reminders`
- **Background work:** WorkManager (SMS reminders)
- **UI:** Material Components 3, RecyclerView, custom chart views (`PieChartView`, `BarChartView`)
- **Min SDK:** 24 (Android 7.0) · **Target SDK:** 35

## How to run

1. Open the `Expense_Tracker` folder in Android Studio.
2. Let Gradle sync.
3. Connect a phone with USB debugging on (or start an emulator) and press **Run ▶**.
4. Allow **SMS** and **Notification** permissions when asked; these are needed for reminders and budget alerts.

## Permissions

| Permission | Why |
|---|---|
| `SEND_SMS` | Send the reminder messages you schedule |
| `POST_NOTIFICATIONS` | Budget alerts and "reminder sent" notifications |

> **Note:** SMS reminders use your phone's SIM and normal SMS charges apply. Apps that send SMS are restricted on the Google Play Store, so this app is meant to be installed directly (APK / Android Studio).
