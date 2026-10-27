package com.mlab.projetor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Seta do touchpad, desenhada numa janela sobreposta que nao recebe toques. */
final class CursorView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();

    CursorView(Context c) {
        super(c);
        fill.setColor(0xFFFFFFFF);
        stroke.setColor(Ui.TEXT);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dp(1.5f));
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float s = w / 15f;
        arrow.reset();
        arrow.moveTo(1 * s, 1 * s);
        arrow.lineTo(1 * s, 18 * s);
        arrow.lineTo(5.5f * s, 14 * s);
        arrow.lineTo(8.5f * s, 21 * s);
        arrow.lineTo(11 * s, 20 * s);
        arrow.lineTo(8 * s, 13 * s);
        arrow.lineTo(14 * s, 13 * s);
        arrow.close();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawPath(arrow, fill);
        c.drawPath(arrow, stroke);
    }
}
