package com.mlab.projetor;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Indicador de hardware sem foco, para nao alterar a navegacao pelo controle. */
final class UsageRing extends View {
    private static final String[] LOADING_DOTS = {".", "..", "..."};
    private static final ArgbEvaluator COLORS = new ArgbEvaluator();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final String label;
    private boolean reverseColors;
    private int percent = -1;
    private String valueText = "—", unit;
    private boolean loading;
    private long loadingStartedAt;

    UsageRing(Context context, String label) {
        super(context);
        this.label = label;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setUsage(-1);
    }

    void setUsage(int value) {
        reverseColors = false;
        loading = false;
        percent = value;
        valueText = value < 0 ? "—" : value + "%";
        unit = null;
        setContentDescription(label + ": " + (value < 0 ? "indisponível" : value + "% em uso"));
        invalidate();
    }

    void setSpeed(int kbps, String checkedAt) {
        reverseColors = true;
        loading = false;
        percent = kbps < 0 ? -1 : (int) Math.min(100, Math.round(kbps / 150.0));
        valueText = kbps < 0 ? "—" : speedText(kbps);
        unit = "Mbps";
        setContentDescription(kbps < 0 ? "Internet: sem teste"
                : "Internet: " + valueText + " Mbps, último teste em " + checkedAt);
        invalidate();
    }

    void setTemperature(int milliCelsius) {
        reverseColors = false;
        loading = false;
        percent = milliCelsius < 0 ? -1 : Math.min(100,
                Math.round(milliCelsius * 100f / Temperature.GRAPH_SCALE));
        valueText = milliCelsius < 0 ? "—" : Integer.toString(Math.round(milliCelsius / 1000f));
        unit = "°C";
        setContentDescription(milliCelsius < 0 ? "Temperatura indisponível"
                : "Maior temperatura entre CPU e GPU: " + valueText + " graus Celsius");
        invalidate();
    }

    static String speedText(int kbps) {
        return kbps < 10_000 ? String.format(java.util.Locale.ROOT, "%.1f", kbps / 1000.0)
                : Long.toString(Math.round(kbps / 1000.0));
    }

    void setLoading() {
        if (!loading) loadingStartedAt = SystemClock.uptimeMillis();
        loading = true;
        percent = -1;
        valueText = ".";
        unit = null;
        setContentDescription("Internet: teste em andamento");
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (loading) {
            int frame = (int) ((SystemClock.uptimeMillis() - loadingStartedAt) / 400 % LOADING_DOTS.length);
            valueText = LOADING_DOTS[frame];
            postInvalidateDelayed(400);
        }
        float centerX = getWidth() / 2f;
        float diameter = Math.min(Ui.dp(38), getWidth() - Ui.dp(12));
        float top = Ui.dp(4);
        arc.set(centerX - diameter / 2, top, centerX + diameter / 2, top + diameter);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Ui.SOFT);
        canvas.drawOval(arc, paint);
        if (percent > 0) {
            paint.setColor(usageColor());
            canvas.drawArc(arc, -90, percent * 3.6f, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Ui.w600);
        paint.setTextSize(Ui.dp(13));
        paint.setColor(Ui.TEXT);
        float baseline = arc.centerY() - (paint.ascent() + paint.descent()) / 2;
        if (unit != null) {
            float width = paint.measureText(valueText);
            if (width > diameter - Ui.dp(6)) paint.setTextSize(paint.getTextSize() * (diameter - Ui.dp(6)) / width);
            baseline = arc.centerY() - Ui.dp(1);
        }
        canvas.drawText(valueText, centerX, baseline, paint);
        if (unit != null) {
            paint.setTextSize(Ui.dp(9));
            canvas.drawText(unit, centerX, arc.centerY() + Ui.dp(10), paint);
        }
        paint.setTypeface(Ui.w500);
        paint.setTextSize(Ui.dp(13));
        paint.setColor(Ui.MUTED);
        canvas.drawText(label, centerX, top + diameter + Ui.dp(21), paint);
    }

    private int usageColor() {
        int level = reverseColors ? 100 - percent : percent;
        int green = 0xFF008A32;
        int yellow = Ui.dark ? 0xFFFFD65C : 0xFFA87900;
        int red = Ui.dark ? 0xFFFF6B6B : 0xFFC13F4C;
        if (level <= 50) return (int) COLORS.evaluate(level / 50f, green, yellow);
        return (int) COLORS.evaluate((level - 50) / 50f, yellow, red);
    }
}
