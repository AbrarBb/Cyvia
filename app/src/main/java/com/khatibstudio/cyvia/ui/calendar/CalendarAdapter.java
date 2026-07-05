package com.khatibstudio.cyvia.ui.calendar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.databinding.ItemCalendarDayBinding;
import com.khatibstudio.cyvia.domain.PredictionEngine;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Adapter for the 7-column calendar grid in CalendarFragment.
 *
 * Items include leading/trailing "ghost" cells from adjacent months
 * (shown with muted text so the grid always has complete rows).
 *
 * Day background states (in priority order):
 *   1. Period (confirmed logged)   → bg_calendar_period
 *   2. Ovulation day               → bg_calendar_ovulation
 *   3. Fertile window              → bg_calendar_fertile
 *   4. Predicted period            → bg_calendar_predicted
 *   5. Today (no other state)      → bg_calendar_today (ring)
 *   6. Normal                      → no background
 */
public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.DayViewHolder> {

    public interface OnDayClickListener {
        void onDayClicked(LocalDate date);
    }

    private List<LocalDate> days = new ArrayList<>();    // 42 cells (6 rows × 7)
    private YearMonth currentMonth;
    private PredictionEngine.CalendarData calData;
    private Set<LocalDate> loggedDates;
    private OnDayClickListener listener;

    public void setData(YearMonth month,
                        PredictionEngine.CalendarData data,
                        Set<LocalDate> logged,
                        OnDayClickListener clickListener) {
        this.currentMonth = month;
        this.calData = data;
        this.loggedDates = logged;
        this.listener = clickListener;
        this.days = buildDayGrid(month);
        notifyDataSetChanged();
    }

    /** Builds a 42-cell list starting from the Sunday before the 1st of the month. */
    private List<LocalDate> buildDayGrid(YearMonth month) {
        List<LocalDate> grid = new ArrayList<>();
        LocalDate firstOfMonth = month.atDay(1);
        // DayOfWeek: MONDAY=1, SUNDAY=7. We want Sunday=0 as first column.
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue() % 7; // Sun=0, Mon=1, ...
        LocalDate start = firstOfMonth.minusDays(dayOfWeek);

        for (int i = 0; i < 42; i++) {
            grid.add(start.plusDays(i));
        }
        return grid;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCalendarDayBinding binding = ItemCalendarDayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DayViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        LocalDate date = days.get(position);
        holder.bind(date, currentMonth, calData, loggedDates, listener);
    }

    @Override
    public int getItemCount() { return days.size(); }

    // ─── ViewHolder ───────────────────────────────────────────────────────

    static class DayViewHolder extends RecyclerView.ViewHolder {

        private final ItemCalendarDayBinding binding;

        DayViewHolder(ItemCalendarDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LocalDate date, YearMonth currentMonth,
                  PredictionEngine.CalendarData calData,
                  Set<LocalDate> loggedDates,
                  OnDayClickListener listener) {

            boolean isCurrentMonth = date.getMonth() == currentMonth.getMonth()
                    && date.getYear() == currentMonth.getYear();
            boolean isToday = date.equals(LocalDate.now());

            // Day number text
            binding.tvDayNumber.setText(String.valueOf(date.getDayOfMonth()));
            binding.tvDayNumber.setAlpha(isCurrentMonth ? 1.0f : 0.3f);

            // Determine background
            int bgDrawable = 0;
            if (calData != null) {
                if (calData.periodDays.contains(date)) {
                    bgDrawable = R.drawable.bg_calendar_period;
                    binding.tvDayNumber.setTextColor(
                            itemView.getContext().getColor(R.color.calendar_period_text));
                } else if ((calData.ovulationDays != null && calData.ovulationDays.contains(date)) || date.equals(calData.ovulationDay)) {
                    bgDrawable = R.drawable.bg_calendar_ovulation;
                    binding.tvDayNumber.setTextColor(
                            itemView.getContext().getColor(R.color.calendar_ovulation_text));
                } else if (calData.fertileDays.contains(date)) {
                    bgDrawable = R.drawable.bg_calendar_fertile;
                    binding.tvDayNumber.setTextColor(
                            itemView.getContext().getColor(R.color.calendar_fertile_text));
                } else if (calData.predictedDays.contains(date)) {
                    bgDrawable = R.drawable.bg_calendar_predicted;
                    binding.tvDayNumber.setTextColor(
                            itemView.getContext().getColor(R.color.calendar_predicted_text));
                } else {
                    binding.tvDayNumber.setTextColor(
                            isCurrentMonth
                                    ? itemView.getContext().getColor(R.color.cyvia_on_background)
                                    : itemView.getContext().getColor(R.color.calendar_other_month_text));
                }
            }

            if (bgDrawable != 0) {
                binding.frameDayCircle.setBackgroundResource(bgDrawable);
            } else if (isToday) {
                binding.frameDayCircle.setBackgroundResource(R.drawable.bg_calendar_today);
            } else {
                binding.frameDayCircle.setBackground(null);
            }

            // Log dot
            boolean hasLog = loggedDates != null && loggedDates.contains(date);
            binding.viewLogDot.setVisibility(hasLog ? View.VISIBLE : View.INVISIBLE);

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null && isCurrentMonth) {
                    listener.onDayClicked(date);
                }
            });
        }
    }
}
