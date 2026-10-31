package com.mlab.projetor;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tela inicial em listas, no estilo Google TV: barra de status, um destaque com o item focado
 * e listas horizontais (Favoritos, Continuar assistindo, Projetor, Conexoes, Sistema).
 * Setas esquerda/direita andam na lista; cima/baixo trocam de lista e voltam ao ultimo item
 * usado nela (regras em RowNav). O controle do celular chega como as mesmas teclas DPAD.
 */
public class HomeActivity extends Activity {
    private static final float DIM = 0.45f;
    private static final float FOCUS_SCALE = 1.1f;

    /** Uma lista horizontal da tela. */
    private static final class Row {
        final String key;
        final View box;
        final TextView sub;
        final HorizontalScrollView scroll;
        final LinearLayout itemsBox;
        final List<View> items = new ArrayList<>();
        int last;

        Row(String key, View box, TextView sub, HorizontalScrollView scroll, LinearLayout itemsBox) {
            this.key = key;
            this.box = box;
            this.sub = sub;
            this.scroll = scroll;
            this.itemsBox = itemsBox;
        }
    }

    /** Aparencia do item ao ganhar e perder o foco. */
    private interface Look {
        void set(boolean focused);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView heroKind, heroName, heroDesc, info, phoneSub;
    private GradientDrawable tint;
    private UsageRing temperatureUsage, cpuUsage, ramUsage, storageUsage, internetUsage;
    private View hardwarePanel;
    private final HardwareUsage hardware = new HardwareUsage();
    private final Runnable hardwareTick = new Runnable() {
        @Override public void run() {
            updateHardware();
            handler.postDelayed(this, 3_000);
        }
    };
    /** Painel com o QR do controle: aparece pelo cartao "Controle no celular". */
    private View qrPanel;
    private ImageView qrImage;
    private TextView qrAddress;
    /** Texto do QR atual (refeito so quando o endereco ou o PIN mudam). */
    private String qrText;
    private static final long QR_TIMEOUT_MS = 2 * 60_000L;
    private final Runnable hideQr = () -> showQr(false);

    private ScrollView rowsScroll;
    private LinearLayout rowsBox;
    private final List<Row> rows = new ArrayList<>();
    private Row activeRow;
    /** Atalhos do fabricante (Keystone, Imagem, HDMI), procurados uma vez por abertura. */
    private Intent keystoneIntent, imageIntent, hdmiIntent;
    /** Ultimo item focado; estatico para sobreviver ao recreate() da troca de tema. */
    private static String focusKey;
    private static int focusIndex;
    /** Ao reconstruir as listas a rolagem vai direto ao lugar, sem animacao. */
    private boolean instantScroll;
    private static final Map<String, Integer> TINTS = new HashMap<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateInfo();
            handler.postDelayed(this, 30_000);
        }
    };

    private static HomeActivity current;

    /** Versao nova encontrada em segundo plano: pergunta uma vez por versao, se a tela inicial estiver aberta. */
    static void offerUpdate(Updater.Info info) {
        HomeActivity h = current;
        if (h == null || h.isFinishing() || Prefs.updateDismissed(h) >= info.code) return;
        h.runOnUiThread(() -> h.confirmUpdate(info, true));
    }

    private void confirmUpdate(Updater.Info info, boolean automatic) {
        String size = info.size > 0 ? " (" + Math.max(1, info.size / (1024 * 1024)) + " MB)" : "";
        dialog().setTitle("Nova versão do Projetor")
                .setMessage("A versão " + info.name + size + " está disponível. Seus ajustes continuam iguais.")
                .setPositiveButton("Atualizar", (x, y) -> {
                    App.inst.controller.hud.message("Baixando a atualização…");
                    Updater.install(this, info, (i, err) -> App.inst.controller.hud.message(err));
                })
                .setNegativeButton("Depois", (x, y) -> {
                    if (automatic) Prefs.setUpdateDismissed(this, info.code);
                })
                .show();
    }

    private void procurarAtualizacao() {
        App.inst.controller.hud.message("Procurando atualização…");
        Updater.check(this, (info, err) -> {
            if (err != null) App.inst.controller.hud.message(err);
            else if (info == null) App.inst.controller.hud.message("Você já tem a versão mais nova");
            else confirmUpdate(info, false);
        });
    }

    /** PIN trocado pelo celular: atualiza a barra de status na hora. */
    static void refreshInfo() {
        HomeActivity h = current;
        if (h != null) h.runOnUiThread(h::updateInfo);
    }

    /** Troca de tema vinda de fora (voz): redesenha a tela inicial se estiver aberta. */
    static void refreshTheme() {
        HomeActivity h = current;
        if (h != null) h.runOnUiThread(h::recreate);
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle b) {
        current = this;
        super.onCreate(b);
        Hardware.Chipset chipset = Hardware.detectChipset();
        keystoneIntent = Hardware.findAvailable(this, Hardware.getKeystoneIntents(chipset));
        imageIntent = Hardware.findAvailable(this, Hardware.getImageAdjustmentIntents(chipset));
        hdmiIntent = Hardware.findAvailable(this, Hardware.getHdmiIntents(chipset));
        setContentView(build());
        App.inst.startKeepAlive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRows();
        handler.post(tick);
        applyHardwareVisibility();
        refreshInternet(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(hardwareTick);
        hardware.resetCpu();
        if (qrPanel != null) showQr(false);
    }

    @Override
    public void onBackPressed() {
        // Launcher: voltar na tela inicial so fecha o QR, se estiver aberto.
        if (qrPanel != null && qrPanel.getVisibility() == View.VISIBLE) showQr(false);
    }

    /**
     * Setas do controle (fisico ou celular): a navegacao entre listas e feita aqui, para o foco
     * nunca pular de lado nem sair da tela. As demais teclas seguem o caminho normal.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean arrow = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!arrow || (qrPanel != null && qrPanel.getVisibility() == View.VISIBLE)) {
            if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP
                    && (qrPanel == null || qrPanel.getVisibility() != View.VISIBLE)) {
                settingsMenu();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        View f = getCurrentFocus();
        int r = rowIndexOf(f);
        if (r < 0) {
            restoreFocus();
            return true;
        }
        Row row = rows.get(r);
        int i = row.items.indexOf(f);
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int n = RowNav.nextItem(row.items.size(), i, k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            if (n >= 0) row.items.get(n).requestFocus();
        } else {
            int[] sizes = new int[rows.size()];
            for (int j = 0; j < sizes.length; j++) sizes[j] = rows.get(j).items.size();
            int t = RowNav.nextRow(sizes, r, k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            if (t >= 0) {
                Row target = rows.get(t);
                target.items.get(RowNav.clamp(target.last, target.items.size())).requestFocus();
            }
        }
        return true;
    }

    private int rowIndexOf(View v) {
        if (v == null) return -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).items.contains(v)) return i;
        return -1;
    }

    // ---- Estrutura da tela ----

    private View build() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.wallpaper(this));

        // Brilho na cor do item focado, atras do destaque (barato: um degrade radial).
        View glow = new View(this);
        tint = new GradientDrawable();
        tint.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        tint.setGradientCenter(0.72f, 0f);
        tint.setGradientRadius(Ui.dp(620));
        setTint(Ui.ACCENT);
        glow.setBackground(tint);
        frame.addView(glow, new FrameLayout.LayoutParams(-1, Ui.dp(400)));

        LinearLayout root = Ui.col(this);
        root.setPadding(0, Ui.dp(24), 0, 0);
        root.addView(topBar(), margins(new LinearLayout.LayoutParams(-1, Ui.dp(40)), 0));
        root.addView(hero(), margins(new LinearLayout.LayoutParams(-1, -2), Ui.dp(20)));

        // As duas rolagens ignoram o "manter o foco visivel" do Android: quem rola e onItemFocused.
        rowsScroll = new ScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                return 0;
            }
        };
        rowsScroll.setVerticalScrollBarEnabled(false);
        rowsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rowsScroll.setFocusable(false);
        rowsBox = Ui.col(this);
        rowsBox.setPadding(0, 0, 0, Ui.dp(48));
        rowsScroll.addView(rowsBox, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        slp.topMargin = Ui.dp(14);
        root.addView(rowsScroll, slp);

        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(qrPanel(), new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams lp, int top) {
        lp.leftMargin = lp.rightMargin = Ui.dp(56);
        lp.topMargin = top;
        return lp;
    }

    private void setTint(int color) {
        int a = Ui.dark ? 0x66 : 0x40;
        tint.setColors(new int[]{(a << 24) | (color & 0x00FFFFFF), color & 0x00FFFFFF});
    }

    // ---- Barra de status (so informacao, sem foco) ----

    private View topBar() {
        LinearLayout bar = Ui.row(this);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(30);
        time.setTypeface(Ui.w300);
        time.setTextColor(Ui.TEXT);
        time.setIncludeFontPadding(false);
        bar.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(15);
        date.setTypeface(Ui.w500);
        date.setTextColor(Ui.MUTED);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = Ui.dp(14);
        bar.addView(date, dlp);
        bar.addView(Ui.space(this, 1));

        LinearLayout phone = chip();
        phone.addView(Ui.icon(this, R.drawable.ic_phone, 16, Ui.TEXT));
        info = Ui.text(this, "", 13, Ui.w500, Ui.TEXT);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
        ilp.leftMargin = Ui.dp(8);
        phone.addView(info, ilp);
        bar.addView(phone, new LinearLayout.LayoutParams(-2, Ui.dp(34)));

        LinearLayout wifi = chip();
        wifi.addView(Ui.icon(this, R.drawable.ic_wifi, 16, Ui.TEXT));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(Ui.dp(34), Ui.dp(34));
        wlp.leftMargin = Ui.dp(10);
        bar.addView(wifi, wlp);
        return bar;
    }

    private LinearLayout chip() {
        LinearLayout c = Ui.row(this);
        c.setGravity(Gravity.CENTER);
        c.setBackground(Ui.glass(17));
        c.setPadding(Ui.dp(9), 0, Ui.dp(9), 0);
        return c;
    }

    // ---- Destaque: o item focado e os medidores do projetor ----

    private View hero() {
        LinearLayout h = Ui.row(this);
        h.setGravity(Gravity.TOP);
        LinearLayout txt = Ui.col(this);
        heroKind = Ui.text(this, "", 13, Ui.w600, Ui.ACCENT);
        heroKind.setAllCaps(true);
        heroKind.setLetterSpacing(0.04f);
        txt.addView(heroKind);
        heroName = Ui.text(this, "", 40, Ui.w700, Ui.TEXT);
        heroName.setSingleLine(true);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        heroName.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(8);
        txt.addView(heroName, nlp);
        heroDesc = Ui.text(this, "", 15, Ui.w400, Ui.MUTED);
        heroDesc.setMaxLines(2);
        heroDesc.setEllipsize(TextUtils.TruncateAt.END);
        heroDesc.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = Ui.dp(8);
        txt.addView(heroDesc, dlp);
        h.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout meters = Ui.row(this);
        temperatureUsage = new UsageRing(this, "Temp.");
        temperatureUsage.setTemperature(-1);
        cpuUsage = new UsageRing(this, "CPU");
        ramUsage = new UsageRing(this, "RAM");
        storageUsage = new UsageRing(this, "Espaço");
        internetUsage = new UsageRing(this, "Internet");
        internetUsage.setSpeed(Prefs.downloadKbps(this), Prefs.downloadCheckedAt(this));
        for (UsageRing ring : new UsageRing[]{temperatureUsage, cpuUsage, ramUsage, storageUsage, internetUsage}) {
            meters.addView(ring, new LinearLayout.LayoutParams(Ui.dp(66), Ui.dp(66)));
        }
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.leftMargin = Ui.dp(24);
        h.addView(meters, mlp);
        hardwarePanel = meters;
        hardwarePanel.setVisibility(Prefs.showHardware(this) ? View.VISIBLE : View.GONE);
        return h;
    }

    private void showHero(String kind, String name, String desc, int color) {
        heroKind.setText(kind);
        heroName.setText(name);
        heroDesc.setText(desc);
        setTint(color);
    }

    // ---- Listas ----

    /** Refaz todas as listas (favoritos, recentes e rotulos mudam) e devolve o foco ao ultimo item. */
    private void loadRows() {
        rows.clear();
        rowsBox.removeAllViews();
        activeRow = null;

        Row fav = row("fav", "Favoritos", "Segure OK para mover ou remover");
        List<Apps.Entry> favs = Apps.favorites(this);
        for (Apps.Entry a : favs) banner(fav, a);
        addTile(fav);

        List<String[]> recentList = Prefs.recents(this);
        if (!recentList.isEmpty()) {
            Row rec = row("rec", "Continuar assistindo", null);
            for (String[] r : recentList) {
                Apps.Entry e = Apps.entry(this, r[0]);
                if (e == null) continue;
                recent(rec, e, r[1]);
                if (rec.items.size() == 4) break;
            }
        }

        Row proj = row("proj", "Projetor", null);
        if (keystoneIntent != null) {
            tile(proj, R.drawable.ic_keystone, "Keystone", null, "Projetor",
                    "Corrige a imagem torta quando o projetor não está de frente para a parede.",
                    () -> open(keystoneIntent));
        }
        if (imageIntent != null) {
            tile(proj, R.drawable.ic_image, "Imagem", null, "Projetor",
                    "Brilho, contraste, cores e modo de imagem.", () -> open(imageIntent));
        }
        if (hdmiIntent != null) {
            tile(proj, R.drawable.ic_hdmi, "HDMI", null, "Projetor",
                    "Mostra o que está ligado na entrada HDMI (videogame, notebook, TV box).", () -> open(hdmiIntent));
        }
        tile(proj, R.drawable.ic_tv_app, "TV ao vivo", null, "Projetor", "Canais abertos ao vivo pela internet.",
                () -> open(Apps.launchIntent(this, Apps.TV_PKG)));

        Row con = row("con", "Conexões", null);
        Intent wifi = new Intent(Settings.ACTION_WIFI_SETTINGS);
        if (available(wifi)) {
            boolean online = Net.ip().indexOf('.') >= 0;
            tile(con, R.drawable.ic_wifi, "Wi‑Fi", online ? "Conectado" : "Desconectado", "Conexões",
                    "Escolha a rede Wi‑Fi do projetor.", () -> open(wifi));
        }
        Intent bt = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        if (available(bt)) {
            tile(con, R.drawable.ic_bt, "Bluetooth", null, "Conexões",
                    "Pareie caixas de som, fones e controles.", () -> open(bt));
        }
        phoneSub = tile(con, R.drawable.ic_phone, "Controle no celular", "PIN " + Prefs.pin(this), "Conexões",
                "Mostra o QR code para usar o celular como controle.", this::toggleQr);

        Row sys = row("sys", "Sistema", null);
        tile(sys, R.drawable.ic_settings, "Ajustes do Android", null, "Sistema",
                "Rede, som, data e hora, contas e os demais ajustes do sistema.", this::openAndroidSettings);
        tile(sys, R.drawable.ic_apps, "Todos os apps", null, "Sistema",
                "Todos os apps instalados. Segure OK num app para adicionar aos favoritos.",
                () -> open(new Intent(this, AppsActivity.class)));
        tile(sys, Ui.dark ? R.drawable.ic_sun : R.drawable.ic_moon, Ui.dark ? "Tema claro" : "Tema escuro", null,
                "Sistema", "Troca as cores da tela inicial.", this::toggleTheme);
        boolean hw = Prefs.showHardware(this);
        tile(sys, R.drawable.ic_chart, hw ? "Ocultar medidores" : "Mostrar medidores", null, "Sistema",
                "Temperatura, CPU, RAM, espaço e internet no topo da tela.", this::toggleHardware);
        tile(sys, R.drawable.ic_speed, "Testar internet", null, "Sistema",
                "Mede a velocidade de download da internet.", this::testInternet);
        String version = installedVersion();
        tile(sys, R.drawable.ic_update, "Atualizar", version.isEmpty() ? null : "v" + version, "Sistema",
                "Procura uma versão nova do Projetor.", this::procurarAtualizacao);
        tile(sys, R.drawable.ic_lock, "Novo PIN", null, "Sistema",
                "Gera um PIN novo para o controle no celular.", this::confirmNewPin);
        tile(sys, R.drawable.ic_exit, "Tela original", null, "Sistema",
                "Volta para a tela inicial de fábrica e remove o app Projetor.", this::confirmRemove);

        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).items.isEmpty()) rowsBox.removeView(rows.remove(i).box);
        }
        restoreFocus();
    }

    /** Volta ao ultimo item focado (mesma lista, mesma posicao), senao ao primeiro favorito. */
    private void restoreFocus() {
        if (rows.isEmpty()) return;
        Row target = rows.get(0);
        int index = 0;
        for (Row r : rows) {
            if (r.key.equals(focusKey)) {
                target = r;
                index = focusIndex;
                break;
            }
        }
        target.last = RowNav.clamp(index, target.items.size());
        instantScroll = true;
        View v = target.items.get(target.last);
        if (v.isFocused()) v.getOnFocusChangeListener().onFocusChange(v, true);
        else v.requestFocus();
        instantScroll = false;
    }

    private Row row(String key, String title, String hint) {
        LinearLayout box = new LinearLayout(this) {
            // Cada filho recebe o alfa direto, sem camada extra por quadro (mais leve na GPU).
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        box.setOrientation(LinearLayout.VERTICAL);
        box.setAlpha(DIM);

        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.BOTTOM);
        head.addView(Ui.text(this, title, 17, Ui.w600, Ui.TEXT));
        TextView sub = null;
        if (hint != null) {
            sub = Ui.text(this, hint, 12, Ui.w500, Ui.MUTED);
            sub.setVisibility(View.INVISIBLE);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.leftMargin = Ui.dp(10);
            head.addView(sub, sl);
        }
        box.addView(head, margins(new LinearLayout.LayoutParams(-1, -2), 0));

        HorizontalScrollView scroll = new HorizontalScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                return 0;
            }
        };
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFocusable(false);
        scroll.setClipChildren(false);
        LinearLayout items = Ui.row(this);
        items.setClipChildren(false);
        items.setClipToPadding(false);
        // Folga em volta para o item focado crescer sem ser cortado.
        items.setPadding(Ui.dp(56), Ui.dp(12), Ui.dp(56), Ui.dp(14));
        scroll.addView(items, new FrameLayout.LayoutParams(-2, -2));
        box.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = rows.isEmpty() ? 0 : Ui.dp(4);
        rowsBox.addView(box, lp);
        Row r = new Row(key, box, sub, scroll, items);
        rows.add(r);
        return r;
    }

    /** Liga um item a lista: foco, destaque, celular ("Selecionado: ...") e rolagem. */
    private void addItem(Row row, View v, int w, int h, String kind, String name, String desc, int color, Look look) {
        final int index = row.items.size();
        row.items.add(v);
        v.setFocusable(true);
        v.setClickable(true);
        v.setContentDescription(name);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f).setDuration(150).start();
            view.setElevation(has ? Ui.dp(10) : 0);
            if (look != null) look.set(has);
            if (has) {
                showHero(kind, name, desc, color);
                onItemFocused(row, index, view);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        if (index > 0) lp.leftMargin = Ui.dp(18);
        row.itemsBox.addView(v, lp);
    }

    private void onItemFocused(Row row, int index, View v) {
        row.last = index;
        focusKey = row.key;
        focusIndex = index;
        App.inst.controller.onScreen(getPackageName(), String.valueOf(v.getContentDescription()));
        if (activeRow != row) {
            for (Row r : rows) {
                boolean on = r == row;
                if (instantScroll) r.box.setAlpha(on ? 1f : DIM);
                else r.box.animate().alpha(on ? 1f : DIM).setDuration(150).start();
                if (r.sub != null) r.sub.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            }
            activeRow = row;
        }
        final boolean instant = instantScroll;
        // Depois do layout: na reconstrucao as medidas ainda nao existem.
        v.post(() -> scrollTo(row, v, instant, true));
    }

    private void scrollTo(Row row, View v, boolean instant, boolean retry) {
        if (row.scroll.getWidth() == 0) {
            if (retry) v.post(() -> scrollTo(row, v, instant, false));
            return;
        }
        int x = RowNav.scrollToShow(row.scroll.getScrollX(), row.scroll.getWidth(),
                v.getLeft(), v.getRight(), Ui.dp(56));
        // A lista focada fica logo abaixo da anterior, sempre na mesma altura da tela.
        int r = rows.indexOf(row);
        int y = r <= 0 ? 0 : rows.get(r - 1).box.getTop();
        if (instant) {
            row.scroll.scrollTo(x, 0);
            rowsScroll.scrollTo(0, y);
        } else {
            row.scroll.smoothScrollTo(x, 0);
            rowsScroll.smoothScrollTo(0, y);
        }
    }

    private int color(Apps.Entry a, int index) {
        Integer c = TINTS.get(a.pkg);
        if (c == null) {
            c = Ui.appColor(a.pkg, a.icon, index);
            TINTS.put(a.pkg, c);
        }
        return c;
    }

    /** Favorito: arte horizontal do app (ou o icone), contorno no foco. */
    private void banner(Row row, final Apps.Entry a) {
        FrameLayout t = new FrameLayout(this);
        t.setBackground(Ui.round(0xFF15161C, 12));
        t.setClipToOutline(true);
        Drawable banner = Apps.banner(this, a.pkg);
        ImageView art = new ImageView(this);
        if (banner != null) {
            art.setImageDrawable(banner);
            art.setScaleType(ImageView.ScaleType.FIT_XY);
        } else {
            art.setImageDrawable(a.icon);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        }
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        t.addView(art, new FrameLayout.LayoutParams(-1, -1));
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(Ui.dp(12));
        ring.setStroke(Ui.dp(3), Ui.RING);
        t.setOnClickListener(v -> open(Apps.launchIntent(this, a.pkg)));
        t.setOnLongClickListener(v -> {
            favoriteOptions(a);
            return true;
        });
        addItem(row, t, Ui.dp(212), Ui.dp(119), Apps.kind(a.pkg), a.label,
                "Aperte OK para abrir. Segure OK para mover ou remover dos favoritos.",
                color(a, row.items.size()), has -> t.setForeground(has ? ring : null));
    }

    private void addTile(Row row) {
        LinearLayout t = Ui.col(this);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = Ui.glass(12);
        t.setBackground(bg);
        ImageView plus = Ui.icon(this, R.drawable.ic_plus, 30, Ui.MUTED);
        t.addView(plus);
        t.setOnClickListener(v -> open(new Intent(this, AppsActivity.class)));
        addItem(row, t, Ui.dp(row.items.isEmpty() ? 212 : 119), Ui.dp(119), "Favoritos", "Adicionar app",
                "Escolha os apps que aparecem aqui. Em Todos os apps, segure OK num app.", Ui.ACCENT,
                has -> {
                    bg.setStroke(Ui.dp(has ? 3 : 1), has ? Ui.RING : Ui.GLASS_EDGE);
                    plus.setImageTintList(android.content.res.ColorStateList.valueOf(has ? Ui.TEXT : Ui.MUTED));
                });
    }

    /** Continuar assistindo: icone, titulo e app. OK abre o app. */
    private void recent(Row row, final Apps.Entry e, String title) {
        LinearLayout c = Ui.row(this);
        c.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        GradientDrawable rest = Ui.glass(14);
        GradientDrawable on = Ui.round(Ui.TEXT, 14);
        c.setBackground(rest);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(e.icon);
        iv.setBackground(Ui.round(0xFFFFFFFF, 9));
        iv.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        iv.setClipToOutline(true);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        c.addView(iv, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        LinearLayout txt = Ui.col(this);
        boolean hasTitle = !title.isEmpty();
        TextView t1 = Ui.text(this, hasTitle ? title : e.label, 14, Ui.w600, Ui.TEXT);
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(t1);
        TextView t2 = Ui.text(this, hasTitle ? e.label : "Abrir", 12, Ui.w400, Ui.MUTED);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.topMargin = Ui.dp(3);
        txt.addView(t2, alp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(12);
        c.addView(txt, tlp);
        c.setOnClickListener(v -> open(Apps.launchIntent(this, e.pkg)));
        int fg = onFocusText();
        addItem(row, c, Ui.dp(260), Ui.dp(76), "Continuar assistindo", hasTitle ? title : e.label,
                hasTitle ? "Em " + e.label + "." : "Abrir " + e.label + ".", color(e, row.items.size()),
                has -> {
                    c.setBackground(has ? on : rest);
                    t1.setTextColor(has ? fg : Ui.TEXT);
                    t2.setTextColor(has ? fg : Ui.MUTED);
                });
    }

    /** Cartao de ajuste (icone + nome + linha opcional). Devolve o texto da linha de baixo. */
    private TextView tile(Row row, int icon, String label, String subtitle, String kind, String desc, Runnable action) {
        LinearLayout t = Ui.col(this);
        t.setGravity(Gravity.CENTER);
        GradientDrawable rest = Ui.glass(16);
        GradientDrawable on = Ui.round(Ui.TEXT, 16);
        t.setBackground(rest);
        ImageView iv = Ui.icon(this, icon, 28, Ui.TEXT);
        t.addView(iv);
        TextView name = Ui.text(this, label, 13, Ui.w500, Ui.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = Ui.dp(10);
        t.addView(name, nlp);
        TextView sub = Ui.text(this, subtitle == null ? "" : subtitle, 11, Ui.w500, Ui.MUTED);
        sub.setVisibility(subtitle == null ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(3);
        t.addView(sub, slp);
        t.setOnClickListener(v -> action.run());
        int fg = onFocusText();
        addItem(row, t, Ui.dp(150), Ui.dp(96), kind, label, desc, Ui.ACCENT, has -> {
            t.setBackground(has ? on : rest);
            iv.setImageTintList(android.content.res.ColorStateList.valueOf(has ? fg : Ui.TEXT));
            name.setTextColor(has ? fg : Ui.TEXT);
            sub.setTextColor(has ? fg : Ui.MUTED);
        });
        return sub;
    }

    /** Texto sobre o cartao focado (cartao na cor do texto do tema, entao a cor oposta). */
    private static int onFocusText() {
        return Ui.dark ? 0xFF1D1D1F : 0xFFF5F5F7;
    }

    // ---- Segurar OK num favorito: mover ou remover ----

    private void favoriteOptions(final Apps.Entry a) {
        String[] items = {"Mover para a esquerda", "Mover para a direita", "Remover dos favoritos"};
        dialog().setTitle(a.label).setItems(items, (d, which) -> {
            if (which == 2) {
                Prefs.toggleFav(this, a.pkg);
                App.inst.controller.hud.message(a.label + " removido dos favoritos");
            } else {
                int to = Prefs.moveFav(this, a.pkg, which == 0 ? -1 : 1);
                if (to < 0) return;
                focusKey = "fav";
                focusIndex = to;
            }
            loadRows();
        }).show();
    }

    // ---- QR do controle: aponta a camera do celular e abre o controle ja com o PIN ----

    private View qrPanel() {
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(Ui.dark ? 0xB3000000 : 0x80000000);
        scrim.setVisibility(View.GONE);

        LinearLayout card = Ui.row(this);
        card.setBackground(Ui.round(Ui.CARD, 28));
        card.setElevation(Ui.dp(12));
        card.setPadding(Ui.dp(18), Ui.dp(18), Ui.dp(36), Ui.dp(18));
        card.setGravity(Gravity.CENTER_VERTICAL);
        qrImage = new ImageView(this);
        qrImage.setBackground(Ui.round(0xFFFFFFFF, 18));
        // A margem branca faz a "zona de silencio" que o leitor de QR precisa.
        qrImage.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        qrImage.setScaleType(ImageView.ScaleType.CENTER);
        card.addView(qrImage, new LinearLayout.LayoutParams(Ui.dp(236), Ui.dp(236)));

        LinearLayout txt = Ui.col(this);
        txt.addView(Ui.text(this, "Controle no celular", 28, Ui.w700, Ui.TEXT));
        String[] steps = {"1.  Conecte o celular no mesmo Wi‑Fi", "2.  Abra a câmera e aponte para o código",
                "3.  Toque no link que aparecer"};
        for (int i = 0; i < steps.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = Ui.dp(i == 0 ? 18 : 8);
            txt.addView(Ui.text(this, steps[i], 17, Ui.w500, Ui.TEXT), lp);
        }
        TextView chromeHint = Ui.text(this,
                "Chrome: se aparecer ERR_TOO_MANY_RETRIES, permita Rede local ou Dispositivos próximos em\n"
                        + "Configurações do Android › Apps › Chrome › Permissões.",
                13, Ui.w500, Ui.MUTED);
        chromeHint.setMaxWidth(Ui.dp(680));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = Ui.dp(16);
        txt.addView(chromeHint, hlp);
        qrAddress = Ui.text(this, "", 14, Ui.w500, Ui.MUTED);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.topMargin = Ui.dp(14);
        txt.addView(qrAddress, alp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = Ui.dp(6);
        txt.addView(Ui.text(this, "Aperte Voltar para fechar", 13, Ui.w500, Ui.MUTED), clp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = Ui.dp(28);
        card.addView(txt, tlp);

        scrim.addView(card, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        qrPanel = scrim;
        return scrim;
    }

    private void toggleQr() {
        showQr(qrPanel.getVisibility() != View.VISIBLE);
    }

    private void showQr(boolean show) {
        handler.removeCallbacks(hideQr);
        if (show) {
            String ip = Net.ip();
            if (ip.indexOf('.') < 0) {
                App.inst.controller.hud.message("Conecte o projetor ao Wi‑Fi primeiro");
                return;
            }
            updateQr(ip, Prefs.pin(this));
            handler.postDelayed(hideQr, QR_TIMEOUT_MS);
        }
        qrPanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateQr(String ip, String pin) {
        if (qrImage == null || ip.indexOf('.') < 0) return;
        // O PIN vai depois do "#": fica so no celular, nao e enviado na requisicao.
        String text = "http://" + ip + ":" + ControlService.PORT + "/#p=" + pin;
        if (!text.equals(qrText)) {
            android.graphics.Bitmap bmp = Qr.bitmap(text, Ui.dp(208), 0);
            if (bmp == null) return;
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
            d.setFilterBitmap(false);
            qrImage.setImageDrawable(d);
            qrText = text;
        }
        qrAddress.setText("Sem câmera? Abra  " + ip + ":" + ControlService.PORT + "  e digite o PIN " + pin);
    }

    private android.app.AlertDialog.Builder dialog() {
        return new android.app.AlertDialog.Builder(this, Ui.dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    /** Botao MENU do controle: as mesmas acoes da lista Sistema, num menu rapido. */
    private void settingsMenu() {
        String hardwareAction = Prefs.showHardware(this)
                ? "Ocultar temperatura, CPU, RAM, espaço e internet" : "Mostrar temperatura, CPU, RAM, espaço e internet";
        String[] items = {"Ajustes do Android", hardwareAction, "Testar velocidade da internet", "Procurar atualização", "Gerar novo PIN do controle", "Voltar à tela original e remover o app"};
        dialog().setTitle("Ajustes").setItems(items, (dlg, which) -> {
            if (which == 0) openAndroidSettings();
            else if (which == 1) toggleHardware();
            else if (which == 2) testInternet();
            else if (which == 3) procurarAtualizacao();
            else if (which == 4) confirmNewPin();
            else confirmRemove();
        }).show();
    }

    private void openAndroidSettings() {
        Intent tv = comp("com.android.tv.settings", "com.android.tv.settings.MainSettings");
        open(available(tv) ? tv : new Intent(Settings.ACTION_SETTINGS));
    }

    private void toggleHardware() {
        Prefs.setShowHardware(this, !Prefs.showHardware(this));
        applyHardwareVisibility();
        loadRows();
    }

    private void toggleTheme() {
        Prefs.setDark(this, !Ui.dark);
        Ui.applyTheme(!Ui.dark);
        recreate();
    }

    private void confirmNewPin() {
        dialog().setTitle("Gerar novo PIN?")
                .setMessage("Os celulares conectados e os atalhos da Siri vão precisar do PIN novo.")
                .setPositiveButton("Gerar", (x, y) -> App.inst.server.regeneratePin())
                .setNegativeButton("Cancelar", null).show();
    }

    private void confirmRemove() {
        dialog().setTitle("Remover o app Projetor?")
                .setMessage("O projetor volta para a tela inicial e o modo mouse de fábrica, "
                        + "e o app Projetor é removido. Seus apps e arquivos não são apagados.")
                .setPositiveButton("Remover", (x, y) -> Sistema.restoreAndUninstall(this))
                .setNegativeButton("Cancelar", null).show();
    }

    private String installedVersion() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private boolean available(Intent i) {
        return getPackageManager().resolveActivity(i, 0) != null;
    }

    private static Intent comp(String pkg, String cls) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, cls));
    }

    private void open(Intent i) {
        if (i == null) return;
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            App.inst.controller.hud.message("Não foi possível abrir");
        }
    }

    private void updateInfo() {
        String ip = Net.ip();
        String pin = Prefs.pin(this);
        info.setText(ip + ":" + ControlService.PORT + "  ·  PIN " + pin);
        if (phoneSub != null) phoneSub.setText("PIN " + pin);
        updateQr(ip, pin);
    }

    private void updateHardware() {
        temperatureUsage.setTemperature(Temperature.readHighest());
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        cpuUsage.setUsage(hardware.readCpu());
        ramUsage.setUsage(HardwareUsage.percent(mi.availMem, mi.totalMem));
        int storagePercent = -1;
        try {
            StatFs storage = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            storagePercent = HardwareUsage.percent(storage.getAvailableBytes(), storage.getTotalBytes());
        } catch (IllegalArgumentException e) {
            android.util.Log.w("Projetor", "Armazenamento indisponível", e);
        }
        storageUsage.setUsage(storagePercent);
    }

    private void applyHardwareVisibility() {
        handler.removeCallbacks(hardwareTick);
        hardware.resetCpu();
        boolean show = Prefs.showHardware(this);
        hardwarePanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            cpuUsage.setUsage(-1);
            handler.post(hardwareTick);
            App.inst.testInternet(false);
        } else {
            App.inst.cancelInternetTest();
        }
    }

    private void testInternet() {
        if (!Prefs.showHardware(this)) {
            App.inst.controller.hud.message("Mostre os gráficos para testar a internet.");
            return;
        }
        if (App.inst.testingInternet) {
            App.inst.controller.hud.message("Teste de internet em andamento…");
            return;
        }
        String last = Prefs.downloadKbps(this) < 0 ? "Nenhum teste realizado."
                : "Último teste: " + UsageRing.speedText(Prefs.downloadKbps(this)) + " Mbps em "
                        + Prefs.downloadCheckedAt(this) + ".";
        dialog().setTitle("Testar velocidade da internet")
                .setMessage(last + "\n\nO teste estima o download de 20MB pela Cloudflare. "
                        + "Pode afetar vídeos em reprodução.")
                .setPositiveButton("Testar", (d, w) -> App.inst.testInternet(true))
                .setNegativeButton("Cancelar", null).show();
    }

    static void refreshInternet(String message) {
        HomeActivity h = current;
        if (h == null || h.isDestroyed() || h.isFinishing()) return;
        h.runOnUiThread(() -> {
            if (App.inst.testingInternet) h.internetUsage.setLoading();
            else h.internetUsage.setSpeed(Prefs.downloadKbps(h), Prefs.downloadCheckedAt(h));
            if (message != null && h.hasWindowFocus()) h.dialog().setTitle("Velocidade da internet")
                    .setMessage(message).setPositiveButton("OK", null).show();
        });
    }
}
