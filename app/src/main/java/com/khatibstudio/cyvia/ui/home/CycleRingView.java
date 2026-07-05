package com.khatibstudio.cyvia.ui.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.khatibstudio.cyvia.R;

public class CycleRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint periodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint follicularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fertilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lutealPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ovulationMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ovulationGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF ringRect = new RectF();
    private float strokeWidth = 44f;

    private int cycleLength = 28;
    private int periodLength = 6;
    private int fertileStartDay = 12;
    private int fertileEndDay = 18;
    private int currentDay = 1;

    private float animatedCurrentDay = 1f;
    private ValueAnimator dayAnimator;
    private ValueAnimator pulseAnimator;
    private float pulseRadiusExtra = 0f;

    public CycleRingView(Context context) {
        super(context);
        init(context);
    }

    public CycleRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CycleRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        strokeWidth = 18f * density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        try {
            int trackColor = ContextCompat.getColor(context, R.color.cyvia_outline);
            trackPaint.setColor(trackColor);
        } catch (Exception e) {
            trackPaint.setColor(Color.parseColor("#DDD6FE"));
        }

        tickPaint.setStyle(Paint.Style.FILL);
        tickPaint.setColor(Color.parseColor("#9E9E9E"));

        periodPaint.setStyle(Paint.Style.STROKE);
        periodPaint.setStrokeWidth(strokeWidth);
        periodPaint.setStrokeCap(Paint.Cap.ROUND);
        periodPaint.setColor(Color.parseColor("#EF5350"));

        follicularPaint.setStyle(Paint.Style.STROKE);
        follicularPaint.setStrokeWidth(strokeWidth);
        follicularPaint.setStrokeCap(Paint.Cap.ROUND);
        follicularPaint.setColor(Color.parseColor("#1E88E5"));

        fertilePaint.setStyle(Paint.Style.STROKE);
        fertilePaint.setStrokeWidth(strokeWidth);
        fertilePaint.setStrokeCap(Paint.Cap.ROUND);
        fertilePaint.setColor(Color.parseColor("#26C6DA"));

        lutealPaint.setStyle(Paint.Style.STROKE);
        lutealPaint.setStrokeWidth(strokeWidth);
        lutealPaint.setStrokeCap(Paint.Cap.ROUND);
        lutealPaint.setColor(Color.parseColor("#FFA726"));

        ovulationMarkerPaint.setStyle(Paint.Style.FILL);
        ovulationMarkerPaint.setColor(Color.parseColor("#00ACC1"));

        ovulationGlowPaint.setStyle(Paint.Style.FILL);
        ovulationGlowPaint.setColor(Color.parseColor("#5500ACC1"));

        indicatorGlowPaint.setStyle(Paint.Style.FILL);
        indicatorGlowPaint.setColor(Color.parseColor("#66FFFFFF"));

        indicatorPaint.setStyle(Paint.Style.FILL);
        indicatorPaint.setColor(Color.WHITE);

        labelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        startPulseAnimation();
    }

    private void startPulseAnimation() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        pulseAnimator = ValueAnimator.ofFloat(0f, 6f, 0f);
        pulseAnimator.setDuration(1600L);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(anim -> {
            pulseRadiusExtra = (float) anim.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    public void setCycleData(int cycleLength, int periodLength, int fertileStartDay, int fertileEndDay, int currentDay) {
        this.cycleLength = Math.max(14, cycleLength);
        this.periodLength = Math.min(this.cycleLength, Math.max(1, periodLength));
        this.fertileStartDay = Math.max(1, fertileStartDay);
        this.fertileEndDay = Math.min(this.cycleLength, fertileEndDay);
        int targetDay = Math.max(1, Math.min(this.cycleLength, currentDay));

        if (this.currentDay != targetDay || dayAnimator == null) {
            this.currentDay = targetDay;
            animateToDay(targetDay);
        } else {
            invalidate();
        }
    }

    private void animateToDay(int targetDay) {
        if (dayAnimator != null && dayAnimator.isRunning()) {
            dayAnimator.cancel();
        }
        float startValue = animatedCurrentDay;
        if (startValue <= 0f) startValue = 1f;

        dayAnimator = ValueAnimator.ofFloat(startValue, targetDay);
        dayAnimator.setDuration(1000L);
        dayAnimator.setInterpolator(new DecelerateInterpolator());
        dayAnimator.addUpdateListener(anim -> {
            animatedCurrentDay = (float) anim.getAnimatedValue();
            invalidate();
        });
        dayAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        float pad = strokeWidth / 2f + 32f * density;
        float size = Math.min(w, h);
        float left = (w - size) / 2f + pad;
        float top = (h - size) / 2f + pad;
        float right = (w + size) / 2f - pad;
        float bottom = (h + size) / 2f - pad;
        ringRect.set(left, top, right, bottom);
    }

    private boolean minimalMode = false;

    public void setMinimalMode(boolean minimalMode) {
        this.minimalMode = minimalMode;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (ringRect.width() <= 0 || ringRect.height() <= 0) return;

        float anglePerDay = 360f / (float) cycleLength;
        float cx = ringRect.centerX();
        float cy = ringRect.centerY();
        float radius = ringRect.width() / 2f;
        float density = getResources().getDisplayMetrics().density;

        if (minimalMode) {
            // Minimal Mode: Draw simple background track + period arc only
            canvas.drawArc(ringRect, -90f, 360f, false, trackPaint);
            float periodSweep = anglePerDay * periodLength;
            canvas.drawArc(ringRect, -90f, periodSweep, false, periodPaint);
        } else {
            // Dynamic 4-Phase Medical Reference Ring
            float gap = 3.5f;

            // 1. Menstrual Phase Arc (Days 1 to periodLength)
            float startAngle = -90f + gap / 2f;
            float sweepAngle = (periodLength * anglePerDay) - gap;
            if (sweepAngle > 0) canvas.drawArc(ringRect, startAngle, sweepAngle, false, periodPaint);

            // 2. Follicular Phase Arc (Days periodLength+1 to fertileStartDay-1)
            int follDays = Math.max(0, fertileStartDay - 1 - periodLength);
            if (follDays > 0) {
                float follStart = -90f + (periodLength * anglePerDay) + gap / 2f;
                float follSweep = (follDays * anglePerDay) - gap;
                if (follSweep > 0) canvas.drawArc(ringRect, follStart, follSweep, false, follicularPaint);
            }

            // 3. Ovulation / Fertile Phase Arc (Days fertileStartDay to fertileEndDay)
            int fertDays = Math.max(0, fertileEndDay - fertileStartDay + 1);
            if (fertDays > 0) {
                float fertStart = -90f + ((fertileStartDay - 1) * anglePerDay) + gap / 2f;
                float fertSweep = (fertDays * anglePerDay) - gap;
                if (fertSweep > 0) canvas.drawArc(ringRect, fertStart, fertSweep, false, fertilePaint);
            }

            // 4. Luteal Phase Arc (Days fertileEndDay+1 to cycleLength)
            int lutealDays = Math.max(0, cycleLength - fertileEndDay);
            if (lutealDays > 0) {
                float lutealStart = -90f + (fertileEndDay * anglePerDay) + gap / 2f;
                float lutealSweep = (lutealDays * anglePerDay) - gap;
                if (lutealSweep > 0) canvas.drawArc(ringRect, lutealStart, lutealSweep, false, lutealPaint);
            }
        }

        // Draw clean circular dots around the track
        for (int d = 1; d <= cycleLength; d++) {
            float tickAngleRad = (float) Math.toRadians(-90f + (d - 0.5f) * anglePerDay);
            float tx = cx + (float) Math.cos(tickAngleRad) * radius;
            float ty = cy + (float) Math.sin(tickAngleRad) * radius;
            if (d % 5 == 0 || d == 1) {
                tickPaint.setAlpha(200);
                canvas.drawCircle(tx, ty, 3f * density, tickPaint);
            } else {
                tickPaint.setAlpha(110);
                canvas.drawCircle(tx, ty, 1.8f * density, tickPaint);
            }
        }

        int ovulationDay = (fertileStartDay + fertileEndDay) / 2;
        boolean isCurrentInPeriod = (currentDay <= periodLength);
        boolean isCurrentInFollicular = (!minimalMode && currentDay > periodLength && currentDay < fertileStartDay);
        boolean isCurrentInFertile = (!minimalMode && currentDay >= fertileStartDay && currentDay <= fertileEndDay);
        boolean isCurrentInLuteal = (!minimalMode && currentDay > fertileEndDay);

        // Draw explicit Ovulation Marker dot on the ring track when not in minimal mode
        if (!minimalMode && fertileEndDay >= fertileStartDay) {
            float ovAngleDeg = -90f + (ovulationDay - 0.5f) * anglePerDay;
            float ovAngleRad = (float) Math.toRadians(ovAngleDeg);
            float ovX = cx + (float) Math.cos(ovAngleRad) * radius;
            float ovY = cy + (float) Math.sin(ovAngleRad) * radius;

            // Draw glowing halo around ovulation marker
            canvas.drawCircle(ovX, ovY, (9f + pulseRadiusExtra * 0.4f) * density, ovulationGlowPaint);
            canvas.drawCircle(ovX, ovY, 5.5f * density, ovulationMarkerPaint);
            canvas.drawCircle(ovX, ovY, 2.2f * density, indicatorPaint);
        }

        // Draw separate static phase labels when current day dot is NOT inside that phase
        if (!isCurrentInPeriod) {
            float periodMidDay = (1 + periodLength) / 2f;
            float periodMidAngleDeg = -90f + (periodMidDay - 0.5f) * anglePerDay;
            drawSmartLabel(canvas, "Period", periodMidAngleDeg, Color.parseColor("#EF5350"), 11.5f * density);
        }

        if (!minimalMode) {
            // Follicular label
            if (!isCurrentInFollicular && fertileStartDay > periodLength + 1) {
                float follMidDay = periodLength + (fertileStartDay - 1 - periodLength) / 2f;
                float follMidAngleDeg = -90f + (follMidDay - 0.5f) * anglePerDay;
                drawSmartLabel(canvas, "Follicular", follMidAngleDeg, Color.parseColor("#1E88E5"), 11f * density);
            }

            // Ovulation label
            if (!isCurrentInFertile && fertileEndDay >= fertileStartDay) {
                float ovAngleDeg = -90f + (ovulationDay - 0.5f) * anglePerDay;
                drawSmartLabel(canvas, "Ovulation", ovAngleDeg, Color.parseColor("#0097A7"), 11.5f * density);
            }

            // Luteal label
            if (!isCurrentInLuteal && cycleLength > fertileEndDay) {
                float lutealMidDay = fertileEndDay + (cycleLength - fertileEndDay) / 2f;
                float lutealMidAngleDeg = -90f + (lutealMidDay - 0.5f) * anglePerDay;
                drawSmartLabel(canvas, "Luteal", lutealMidAngleDeg, Color.parseColor("#FB8C00"), 11f * density);
            }
        }

        // Draw current day animated pointer indicator on the ring
        float currentAngleDeg = -90f + (animatedCurrentDay - 0.5f) * anglePerDay;
        float currentAngleRad = (float) Math.toRadians(currentAngleDeg);
        float indX = cx + (float) Math.cos(currentAngleRad) * radius;
        float indY = cy + (float) Math.sin(currentAngleRad) * radius;

        // Glowing pulsing circles for current day
        canvas.drawCircle(indX, indY, (12f + pulseRadiusExtra) * density, indicatorGlowPaint);
        canvas.drawCircle(indX, indY, 7.5f * density, indicatorPaint);

        // Draw unified Current Day label (combined with phase)
        String dotLabel = "Day " + currentDay;
        int dotColor = Color.parseColor("#EF5350");
        if (isCurrentInPeriod) {
            dotLabel = "Day " + currentDay + " • Period";
            dotColor = Color.parseColor("#EF5350");
        } else if (isCurrentInFollicular) {
            dotLabel = "Day " + currentDay + " • Follicular";
            dotColor = Color.parseColor("#1E88E5");
        } else if (isCurrentInFertile) {
            if (currentDay == ovulationDay) {
                dotLabel = "Day " + currentDay + " • Ovulation";
            } else {
                dotLabel = "Day " + currentDay + " • Fertile";
            }
            dotColor = Color.parseColor("#0097A7");
        } else if (isCurrentInLuteal) {
            dotLabel = "Day " + currentDay + " • Luteal";
            dotColor = Color.parseColor("#FB8C00");
        }
        drawSmartLabel(canvas, dotLabel, currentAngleDeg, dotColor, 12f * density);
    }

    private void drawSmartLabel(Canvas canvas, String text, float angleDeg, int color, float textSize) {
        float density = getResources().getDisplayMetrics().density;
        labelPaint.setTextSize(textSize);
        labelPaint.setColor(color);

        while (angleDeg <= -180f) angleDeg += 360f;
        while (angleDeg > 180f) angleDeg -= 360f;

        float angleRad = (float) Math.toRadians(angleDeg);
        float cx = ringRect.centerX();
        float cy = ringRect.centerY();
        float radius = ringRect.width() / 2f;
        float outerRadius = radius + strokeWidth / 2f;

        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        float textOffset = (textHeight / 2f) - fm.descent;

        float lx, ly;

        if (angleDeg >= -135f && angleDeg <= -45f) {
            labelPaint.setTextAlign(Paint.Align.CENTER);
            lx = cx + (float) Math.cos(angleRad) * (outerRadius + 4f * density);
            ly = cy - outerRadius - 10f * density;
        } else if (angleDeg >= 45f && angleDeg <= 135f) {
            labelPaint.setTextAlign(Paint.Align.CENTER);
            lx = cx + (float) Math.cos(angleRad) * (outerRadius + 4f * density);
            ly = cy + outerRadius + 18f * density;
        } else if (angleDeg > -45f && angleDeg < 45f) {
            labelPaint.setTextAlign(Paint.Align.LEFT);
            float cos = (float) Math.cos(angleRad);
            float sin = (float) Math.sin(angleRad);
            lx = cx + cos * outerRadius + 8f * density;
            ly = cy + sin * outerRadius + textOffset;
        } else {
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            float cos = (float) Math.cos(angleRad);
            float sin = (float) Math.sin(angleRad);
            lx = cx + cos * outerRadius - 8f * density;
            ly = cy + sin * outerRadius + textOffset;
        }

        float textWidth = labelPaint.measureText(text);
        if (labelPaint.getTextAlign() == Paint.Align.LEFT) {
            if (lx + textWidth > getWidth() - 4f * density) {
                lx = getWidth() - textWidth - 4f * density;
            }
            lx = Math.max(cx + outerRadius + 4f * density, lx);
        } else if (labelPaint.getTextAlign() == Paint.Align.RIGHT) {
            if (lx - textWidth < 4f * density) {
                lx = textWidth + 4f * density;
            }
            lx = Math.min(cx - outerRadius - 4f * density, lx);
        } else {
            lx = Math.max(textWidth / 2f + 4f * density, Math.min(getWidth() - textWidth / 2f - 4f * density, lx));
        }

        canvas.drawText(text, lx, ly, labelPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (dayAnimator != null) dayAnimator.cancel();
        if (pulseAnimator != null) pulseAnimator.cancel();
    }
}
