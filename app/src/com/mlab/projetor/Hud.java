package com.mlab.projetor;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Aviso flutuante do app (pilula de vidro no topo da tela), no lugar dos Toasts
 * e da barra de volume do sistema quando o volume vem do celular. Pode ser chamado de qualquer thread.
 */
final class Hud {
    private final Context ctx;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout view;
    private ImageView icon;
    private TextView label;
    private View track, fill;
    private boolean added;
    private final Runnable hide = () -> {
        if (view != null) view.animate().alpha(0f).setDuration(200).withEndAction(() -> view.setVisibility(View.GONE)).start();
    };

    Hud(Context c) {
        ctx = c.getApplicationContext();
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    void message(final String text) {
        main.post(() -> show(R.drawable.ic_glyph, text, -1));
    }

    void volume(final int percent, final boolean muted) {
        main.post(() -> show(muted || percent == 0 ? R.drawable.ic_vol_off : R.drawable.ic_vol,
                muted ? "Mudo" : percent + "%", muted ? 0 : percent));
    }

    /** level: 0..100 mostra a barra; -1 esconde. */
    private void show(int iconRes, String text, int level) {
        if (!ensure()) return;
        icon.setImageResource(iconRes);
        label.setText(text);
        track.setVisibility(level >= 0 ? View.VISIBLE : View.GONE);
        if (level >= 0) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fill.getLayoutParams();
            lp.width = Math.round(Ui.dp(160) * level / 100f);
            fill.setLayoutParams(lp);
        }
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1f);
        main.removeCallbacks(hide);
        main.postDelayed(hide, 1600);
    }

    private boolean ensure() {
        if (added) return true;
        try {
            view = Ui.row(ctx);
            view.setPadding(Ui.dp(20), 0, Ui.dp(24), 0);
            GradientDrawable bg = Ui.round(Ui.dark ? 0xFA2C2C2E : 0xFAFFFFFF, 999);
            bg.setStroke(Ui.dp(1), Ui.dark ? 0x26FFFFFF : 0x14000000);
            view.setBackground(bg);
            icon = Ui.icon(ctx, R.drawable.ic_vol, 22, Ui.TEXT);
            view.addView(icon);

            LinearLayout trackBox = Ui.row(ctx);
            track = trackBox;
            GradientDrawable tbg = Ui.round(Ui.dark ? 0x33FFFFFF : 0x1F000000, 3);
            trackBox.setBackground(tbg);
            fill = new View(ctx);
            fill.setBackground(Ui.round(Ui.TEXT, 3));
            trackBox.addView(fill, new LinearLayout.LayoutParams(0, -1));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(160), Ui.dp(6));
            tlp.leftMargin = Ui.dp(14);
            view.addView(trackBox, tlp);

            label = Ui.text(ctx, "", 15, Ui.w600, Ui.TEXT);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
            llp.leftMargin = Ui.dp(14);
            view.addView(label, llp);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, Ui.dp(52),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            // No topo e centralizado, como o aviso de volume do iPhone; nao cobre o Dock nem legendas.
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = Ui.dp(20);
            view.setVisibility(View.GONE);
            wm.addView(view, lp);
            added = true;
            return true;
        } catch (RuntimeException e) {
            Log.w("Projetor", "nao foi possivel mostrar o aviso", e);
            return false;
        }
    }
}
