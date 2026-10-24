package com.example.expensetracker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.util.List;

/**
 * Donut chart drawn in shades of teal, with a label and total in the middle.
 */
public class PieChartView extends View {

    /** Alternates dark and light teal so neighbouring slices stay distinct. */
    public static final int[] TEAL_PALETTE = {
            0xFF00695C, 0xFF80CBC4, 0xFF26A69A, 0xFF004D40, 0xFFB2DFDB,
            0xFF00897B, 0xFF4DB6AC, 0xFF00796B, 0xFF009688,
    };

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float[] values = new float[0];
    private int[] colors = new int[0];
    private String centerLabel = "";
    private String centerValue = "";
    private float progress = 1f;

    public PieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        arcPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHighest));

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(14));
        labelPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant));

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.DEFAULT_BOLD);
        valuePaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface));
    }

    public static int colorAt(int index) {
        return TEAL_PALETTE[index % TEAL_PALETTE.length];
    }

    /** {@code colors[i]} is used for {@code data.get(i)}. */
    public void setData(List<Double> data, int[] colors, String label, String value) {
        values = new float[data.size()];
        for (int i = 0; i < data.size(); i++) values[i] = data.get(i).floatValue();
        this.colors = colors;
        centerLabel = label;
        centerValue = value;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(width, height);
        float stroke = size * 0.16f;
        float radius = (size - stroke) / 2f;
        float cx = getPaddingLeft() + width / 2f;
        float cy = getPaddingTop() + height / 2f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        trackPaint.setStrokeWidth(stroke);
        canvas.drawCircle(cx, cy, radius, trackPaint);

        float total = 0;
        for (float v : values) total += v;
        if (total > 0) {
            arcPaint.setStrokeWidth(stroke);
            float gap = values.length > 1 ? 1.5f : 0f;
            float start = -90f;
            for (int i = 0; i < values.length; i++) {
                float sweep = values[i] / total * 360f * progress;
                if (sweep > gap) {
                    arcPaint.setColor(colors[i]);
                    canvas.drawArc(oval, start + gap / 2f, sweep - gap, false, arcPaint);
                }
                start += sweep;
            }
        }

        // Shrink the total if it would not fit inside the hole.
        float maxTextWidth = (radius - stroke / 2f) * 1.6f;
        valuePaint.setTextSize(sp(22));
        float textWidth = valuePaint.measureText(centerValue);
        if (textWidth > maxTextWidth) {
            valuePaint.setTextSize(sp(22) * maxTextWidth / textWidth);
        }
        canvas.drawText(centerLabel, cx, cy - dp(6), labelPaint);
        canvas.drawText(centerValue, cx, cy + valuePaint.getTextSize(), valuePaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
