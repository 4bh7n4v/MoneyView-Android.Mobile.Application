package com.example.expensetracker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

/**
 * Grouped bar chart comparing income and expenses per period, in two shades of teal.
 * Groups can be tapped (see {@link #setOnBarClickListener}) and one can be highlighted.
 * Labels may contain "\n" for a second line, e.g. "Mon\n15".
 */
public class BarChartView extends View {

    public interface OnBarClickListener {
        void onBarClick(int index);
    }

    private static final int GRID_LINES = 4;
    private static final int DIMMED_ALPHA = 80;

    private final Paint incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final int labelColor;
    private final int selectedLabelColor;

    private String[] labels = new String[0];
    private double[] income = new double[0];
    private double[] expense = new double[0];
    private float progress = 1f;
    private int selectedIndex = -1;
    private float chartLeft; // x where the bars start, saved in onDraw for touch handling
    @Nullable private OnBarClickListener listener;

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        incomePaint.setColor(ContextCompat.getColor(context, R.color.chart_income));
        expensePaint.setColor(ContextCompat.getColor(context, R.color.chart_expense));

        gridPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant));
        gridPaint.setStrokeWidth(dp(1));
        // A faint tint of the primary colour so both bar shades stay readable on top of it.
        highlightPaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
        highlightPaint.setAlpha(28);

        labelColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        selectedLabelColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        axisTextPaint.setColor(labelColor);
        axisTextPaint.setTextSize(sp(11));
        axisTextPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setTextSize(sp(12));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Amounts are stored rupees; they are converted to the shown currency for drawing. */
    public void setData(String[] labels, double[] income, double[] expense) {
        this.labels = labels;
        this.income = new double[income.length];
        this.expense = new double[expense.length];
        for (int i = 0; i < income.length; i++) {
            this.income[i] = Formatters.toDisplay(income[i]);
            this.expense[i] = Formatters.toDisplay(expense[i]);
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /** Highlights one group and dims the rest; -1 clears the highlight. */
    public void setSelectedIndex(int index) {
        selectedIndex = index;
        invalidate();
    }

    public void setOnBarClickListener(@Nullable OnBarClickListener listener) {
        this.listener = listener;
        setClickable(listener != null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null || labels.length == 0) return super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float groupWidth = (getWidth() - getPaddingRight() - chartLeft) / labels.length;
            int index = (int) ((event.getX() - chartLeft) / groupWidth);
            if (event.getX() >= chartLeft && index >= 0 && index < labels.length) {
                performClick();
                listener.onBarClick(index);
            }
        }
        return true; // a parent ScrollView still takes over for drags
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.length == 0) return;

        double maxValue = 0;
        int labelLines = 1;
        for (int i = 0; i < labels.length; i++) {
            maxValue = Math.max(maxValue, Math.max(income[i], expense[i]));
            labelLines = Math.max(labelLines, labels[i].split("\n").length);
        }
        double axisMax = maxValue > 0 ? niceCeiling(maxValue) : 100;

        float axisWidth = 0;
        for (int i = 0; i <= GRID_LINES; i++) {
            axisWidth = Math.max(axisWidth,
                    axisTextPaint.measureText(Formatters.compactDisplayMoney(axisMax * i / GRID_LINES)));
        }
        axisWidth += dp(8);

        float lineHeight = labelPaint.getTextSize() * 1.2f;
        chartLeft = getPaddingLeft() + axisWidth;
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop() + dp(8);
        float bottom = getHeight() - getPaddingBottom() - dp(10) - lineHeight * labelLines;
        float chartHeight = bottom - top;
        float groupWidth = (right - chartLeft) / labels.length;

        if (selectedIndex >= 0 && selectedIndex < labels.length) {
            float l = chartLeft + groupWidth * selectedIndex + dp(2);
            bar.set(l, top - dp(4), l + groupWidth - dp(4), getHeight() - getPaddingBottom());
            canvas.drawRoundRect(bar, dp(8), dp(8), highlightPaint);
        }

        for (int i = 0; i <= GRID_LINES; i++) {
            float y = bottom - chartHeight * i / GRID_LINES;
            canvas.drawLine(chartLeft, y, right, y, gridPaint);
            canvas.drawText(Formatters.compactDisplayMoney(axisMax * i / GRID_LINES),
                    chartLeft - dp(6), y + axisTextPaint.getTextSize() / 3f, axisTextPaint);
        }

        float barWidth = Math.min(groupWidth * 0.3f, dp(24));
        float radius = dp(4);
        for (int i = 0; i < labels.length; i++) {
            float center = chartLeft + groupWidth * i + groupWidth / 2f;
            boolean dimmed = selectedIndex >= 0 && i != selectedIndex;
            incomePaint.setAlpha(dimmed ? DIMMED_ALPHA : 255);
            expensePaint.setAlpha(dimmed ? DIMMED_ALPHA : 255);

            float incomeHeight = (float) (income[i] / axisMax * chartHeight * progress);
            bar.set(center - barWidth - dp(2), bottom - incomeHeight, center - dp(2), bottom);
            canvas.drawRoundRect(bar, radius, radius, incomePaint);

            float expenseHeight = (float) (expense[i] / axisMax * chartHeight * progress);
            bar.set(center + dp(2), bottom - expenseHeight, center + barWidth + dp(2), bottom);
            canvas.drawRoundRect(bar, radius, radius, expensePaint);

            boolean selected = i == selectedIndex;
            labelPaint.setColor(selected ? selectedLabelColor : labelColor);
            labelPaint.setFakeBoldText(selected);
            String[] lines = labels[i].split("\n");
            for (int line = 0; line < lines.length; line++) {
                canvas.drawText(lines[line], center, bottom + dp(6) + lineHeight * (line + 1) - dp(2), labelPaint);
            }
        }
    }

    /** Rounds up to 1, 2, 2.5 or 5 × a power of ten so grid labels are tidy. */
    private static double niceCeiling(double value) {
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        double fraction = value / magnitude;
        double nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10;
        return nice * magnitude;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
