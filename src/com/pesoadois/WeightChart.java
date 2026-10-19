package com.pesoadois;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import com.pesoadois.core.ChartMath;
import com.pesoadois.core.DateText;
import com.pesoadois.core.Diary;
import com.pesoadois.core.Entry;
import com.pesoadois.core.PersonInfo;
import com.pesoadois.core.WeightFormat;

import java.util.ArrayList;

/**
 * Gráfico de evolução. Modo kg (valores absolutos) ou % (variação desde a base).
 * Tudo que é geometria é calculado em setData/onSizeChanged; onDraw só desenha.
 */
final class WeightChart extends View {
    private static final long DAY_MS = 86_400_000L;
    private static final long HALF_DAY_MS = 12 * 60 * 60 * 1000L;
    private static final int TICKS = 4;
    private static final long HIDE_DELAY_MS = 1500L;
    private static final long ANIMATION_MS = 350L;
    private static final float TOUCH_RADIUS_DP = 24f;

    private static final class Track {
        final String name;
        final int color;
        final ArrayList<Point> points = new ArrayList<>();
        final Path line = new Path();
        final Path goalLine = new Path();
        double goal = Double.NaN;
        boolean hasGoal;

        Track(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    private static final class Point {
        final Track track;
        final long at;
        final double value;
        float x;
        float y;

        Point(Track track, long at, double value) {
            this.track = track;
            this.at = at;
            this.value = value;
        }
    }

    private final Palette palette;
    private final float density;
    private final float touchSlop;
    private final Paint gridPaint = new Paint();
    private final Paint zeroPaint = new Paint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint();
    private final Paint tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect dash;
    private final RectF tipRect = new RectF();
    private final Runnable hideSelection = this::clearSelection;

    private final ArrayList<Track> tracks = new ArrayList<>();
    private final ArrayList<Point> points = new ArrayList<>();
    private final ArrayList<Point> selected = new ArrayList<>();
    private final ArrayList<String> tipLines = new ArrayList<>();

    private double[] ticks;
    private String[] tickLabels;
    private float[] tickY;
    private String[] xLabels;
    private float[] xLabelX;
    private String emptyMessage;
    private boolean percent;
    private long minAt;
    private long maxAt;
    private double minValue;
    private double maxValue;
    private float plotLeft;
    private float plotRight;
    private float plotTop;
    private float plotBottom;
    private float progress = 1f;
    private ValueAnimator animator;
    private boolean hasSelection;
    private float guideX;
    private float downX;
    private float downY;
    private boolean dragging;

    WeightChart(Context context, Palette palette) {
        super(context);
        this.palette = palette;
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        dash = new DashPathEffect(new float[]{dp(5), dp(4)}, 0f);

        gridPaint.setColor(palette.border);
        gridPaint.setStrokeWidth(dp(1));
        zeroPaint.setColor(palette.muted);
        zeroPaint.setStrokeWidth(dp(1.5f));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.5f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        ringPaint.setColor(palette.surface);
        dotPaint.setStyle(Paint.Style.FILL);
        goalPaint.setStyle(Paint.Style.STROKE);
        goalPaint.setStrokeWidth(dp(1.5f));
        goalPaint.setPathEffect(dash);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(dp(11));
        guidePaint.setColor(palette.muted);
        guidePaint.setStrokeWidth(dp(1));
        tipPaint.setColor(palette.surface);
        tipBorderPaint.setStyle(Paint.Style.STROKE);
        tipBorderPaint.setStrokeWidth(dp(1));
        tipBorderPaint.setColor(palette.border);
        tipTextPaint.setColor(palette.ink);
        tipTextPaint.setTextSize(dp(12));
        setContentDescription("Gráfico de evolução");
    }

    /**
     * Carrega os dados do período. rangeDays = 0 mostra tudo; percent = variação desde a base.
     */
    void setData(Diary diary, int rangeDays, boolean percent, long now) {
        this.percent = percent;
        clearSelection();
        tracks.clear();
        points.clear();
        long windowStart = rangeDays > 0 ? now - rangeDays * DAY_MS : Long.MIN_VALUE;
        for (String person : Diary.PEOPLE) {
            Diary.Base base = diary.base(person);
            boolean usable = base != null && (!percent || base.kg != 0);
            Track track = new Track(diary.person(person).name, palette.accent(person));
            if (usable) {
                for (Entry entry : diary.of(person)) {
                    if (entry.at < windowStart) continue;
                    double value = percent ? ChartMath.toPercent(entry.kg, base.kg) : entry.kg;
                    Point point = new Point(track, entry.at, value);
                    track.points.add(point);
                    points.add(point);
                }
            }
            PersonInfo info = diary.person(person);
            if (!track.points.isEmpty()) {
                if (info.hasGoal()) {
                    track.hasGoal = true;
                    track.goal = percent ? ChartMath.toPercent(info.goalKg, base.kg) : info.goalKg;
                }
                tracks.add(track);
            }
        }
        computeDomain(diary.size(), rangeDays);
        computeLayout();
        if (!points.isEmpty()) startAnimation(); else progress = 1f;
        invalidate();
    }

    private void computeDomain(int totalEntries, int rangeDays) {
        ticks = null;
        tickLabels = null;
        if (points.isEmpty()) {
            emptyMessage = totalEntries == 0
                    ? "Registre uma pesagem para ver o gráfico"
                    : "Sem marcações neste período";
            setContentDescription("Gráfico de evolução: " + (totalEntries == 0
                    ? "sem marcações." : "sem marcações neste período."));
            return;
        }
        emptyMessage = null;
        minAt = Long.MAX_VALUE;
        maxAt = Long.MIN_VALUE;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Point point : points) {
            minAt = Math.min(minAt, point.at);
            maxAt = Math.max(maxAt, point.at);
            min = Math.min(min, point.value);
            max = Math.max(max, point.value);
        }
        for (Track track : tracks) {
            if (track.hasGoal) {
                min = Math.min(min, track.goal);
                max = Math.max(max, track.goal);
            }
        }
        if (percent) {
            min = Math.min(min, 0);
            max = Math.max(max, 0);
        }
        if (minAt == maxAt) {
            minAt -= HALF_DAY_MS;
            maxAt += HALF_DAY_MS;
        }
        ticks = ChartMath.niceTicks(min, max, TICKS);
        minValue = ticks[0];
        maxValue = ticks[ticks.length - 1];
        tickLabels = new String[ticks.length];
        for (int i = 0; i < ticks.length; i++) {
            tickLabels[i] = percent ? percentTick(ticks[i]) : WeightFormat.format(ticks[i]);
        }
        boolean longRange = maxAt - minAt > 365 * DAY_MS;
        xLabels = new String[]{
                longRange ? DateText.monthYear(minAt) : DateText.dayMonth(minAt),
                longRange ? DateText.monthYear((minAt + maxAt) / 2) : DateText.dayMonth((minAt + maxAt) / 2),
                longRange ? DateText.monthYear(maxAt) : DateText.dayMonth(maxAt)};
        setContentDescription(summary(rangeDays));
    }

    private String summary(int rangeDays) {
        StringBuilder text = new StringBuilder("Gráfico de evolução, ");
        text.append(rangeDays == 30 ? "30 dias" : rangeDays == 90 ? "90 dias"
                : rangeDays == 365 ? "1 ano" : "tudo").append(percent ? " em variação percentual" : "").append(": ");
        boolean first = true;
        for (Track track : tracks) {
            if (!first) text.append("; ");
            first = false;
            Point start = track.points.get(0);
            Point end = track.points.get(track.points.size() - 1);
            text.append(track.name).append(" de ").append(valueText(start.value))
                    .append(" a ").append(valueText(end.value));
        }
        return text.toString();
    }

    private String valueText(double value) {
        return percent ? WeightFormat.formatPercent(value) : WeightFormat.format(value) + " kgf";
    }

    /** Calcula posições em pixels. Não aloca objetos de desenho em onDraw. */
    private void computeLayout() {
        if (ticks == null || getWidth() == 0 || getHeight() == 0) return;
        float maxLabel = 0;
        for (String label : tickLabels) maxLabel = Math.max(maxLabel, textPaint.measureText(label));
        plotLeft = dp(10) + maxLabel + dp(8);
        plotRight = getWidth() - dp(12);
        plotTop = dp(12);
        plotBottom = getHeight() - dp(26);

        tickY = new float[ticks.length];
        for (int i = 0; i < ticks.length; i++) tickY[i] = mapY(ticks[i]);

        xLabelX = new float[]{plotLeft, (plotLeft + plotRight) / 2f, plotRight};

        for (Track track : tracks) {
            track.line.reset();
            track.goalLine.reset();
            for (int i = 0; i < track.points.size(); i++) {
                Point point = track.points.get(i);
                point.x = mapX(point.at);
                point.y = mapY(point.value);
                if (i == 0) track.line.moveTo(point.x, point.y);
                else track.line.lineTo(point.x, point.y);
            }
            if (track.hasGoal) {
                float goalY = mapY(track.goal);
                track.goalLine.moveTo(plotLeft, goalY);
                track.goalLine.lineTo(plotRight, goalY);
            }
        }
    }

    private float mapX(long at) {
        return plotLeft + (float) ((at - minAt) / (double) (maxAt - minAt)) * (plotRight - plotLeft);
    }

    private float mapY(double value) {
        return plotBottom - (float) ((value - minValue) / (maxValue - minValue)) * (plotBottom - plotTop);
    }

    private void startAnimation() {
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(ANIMATION_MS);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (Float) a.getAnimatedValue();
                invalidate();
            });
        }
        animator.cancel();
        progress = 0f;
        animator.start();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        computeLayout();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (emptyMessage != null) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(13));
            canvas.drawText(emptyMessage, getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(dp(11));
            return;
        }
        if (ticks == null || tickY == null) return;

        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < ticks.length; i++) {
            canvas.drawLine(plotLeft, tickY[i], plotRight, tickY[i], gridPaint);
            canvas.drawText(tickLabels[i], plotLeft - dp(8), tickY[i] + dp(4), textPaint);
        }
        if (percent) canvas.drawLine(plotLeft, mapY(0), plotRight, mapY(0), zeroPaint);

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(xLabels[0], xLabelX[0], plotBottom + dp(18), textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(xLabels[1], xLabelX[1], plotBottom + dp(18), textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(xLabels[2], xLabelX[2], plotBottom + dp(18), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        float clipRight = progress >= 1f ? getWidth() : plotLeft + (plotRight - plotLeft) * progress;
        canvas.save();
        canvas.clipRect(0, 0, clipRight, getHeight());
        for (Track track : tracks) {
            if (track.hasGoal) {
                goalPaint.setColor(track.color);
                goalPaint.setAlpha(170);
                canvas.drawPath(track.goalLine, goalPaint);
            }
            linePaint.setColor(track.color);
            canvas.drawPath(track.line, linePaint);
        }
        for (Point point : points) {
            canvas.drawCircle(point.x, point.y, dp(4.3f), ringPaint);
            dotPaint.setColor(point.track.color);
            canvas.drawCircle(point.x, point.y, dp(2.8f), dotPaint);
        }
        canvas.restore();

        if (hasSelection) drawSelection(canvas);
    }

    private void drawSelection(Canvas canvas) {
        canvas.drawLine(guideX, plotTop, guideX, plotBottom, guidePaint);
        for (Point point : selected) {
            dotPaint.setColor(point.track.color);
            canvas.drawCircle(point.x, point.y, dp(5f), dotPaint);
        }
        canvas.drawRoundRect(tipRect, dp(10), dp(10), tipPaint);
        canvas.drawRoundRect(tipRect, dp(10), dp(10), tipBorderPaint);
        float baseline = tipRect.top + dp(20);
        for (int i = 0; i < tipLines.size(); i++) {
            canvas.drawText(tipLines.get(i), tipRect.left + dp(10), baseline + i * dp(17), tipTextPaint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty()) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                removeCallbacks(hideSelection);
                select(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (!dragging && dx > touchSlop && dx > dy) {
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) select(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                removeCallbacks(hideSelection);
                postDelayed(hideSelection, HIDE_DELAY_MS);
                return true;
            case MotionEvent.ACTION_CANCEL:
                clearSelection();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    /** Seleciona o ponto mais próximo em X e monta o balão com cada pessoa. */
    private void select(float x) {
        Point nearest = null;
        float best = Float.MAX_VALUE;
        for (Point point : points) {
            float distance = Math.abs(point.x - x);
            if (distance < best) {
                best = distance;
                nearest = point;
            }
        }
        if (nearest == null || best > dp(TOUCH_RADIUS_DP)) {
            clearSelection();
            return;
        }
        selected.clear();
        for (Track track : tracks) {
            Point closest = null;
            float closestDistance = Float.MAX_VALUE;
            for (Point point : track.points) {
                float distance = Math.abs(point.x - nearest.x);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = point;
                }
            }
            if (closest != null && closestDistance <= dp(TOUCH_RADIUS_DP)) selected.add(closest);
        }
        tipLines.clear();
        tipLines.add(DateText.shortDate(nearest.at) + " · " + DateText.time(nearest.at));
        float width = textPaint.measureText(tipLines.get(0));
        for (Point point : selected) {
            String line = point.track.name + "  " + valueText(point.value);
            tipLines.add(line);
            width = Math.max(width, tipTextPaint.measureText(line));
        }
        width = Math.max(width, tipTextPaint.measureText(tipLines.get(0)));
        float height = dp(20) + (tipLines.size() - 1) * dp(17) + dp(12);
        float left = nearest.x + dp(10);
        if (left + width + dp(20) > getWidth()) left = nearest.x - dp(10) - width - dp(20);
        left = Math.max(0, left);
        tipRect.set(left, plotTop, left + width + dp(20), plotTop + height);
        guideX = nearest.x;
        hasSelection = true;
        invalidate();
    }

    private void clearSelection() {
        if (!hasSelection && selected.isEmpty()) return;
        hasSelection = false;
        selected.clear();
        tipLines.clear();
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        removeCallbacks(hideSelection);
        super.onDetachedFromWindow();
    }

    private static String percentTick(double value) {
        if (Math.abs(value) < 1e-9) return "0%";
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return (value > 0 ? "+" : "−") + (long) Math.abs(value) + "%";
        }
        return WeightFormat.formatPercent(value);
    }

    private float dp(float value) {
        return value * density;
    }
}
