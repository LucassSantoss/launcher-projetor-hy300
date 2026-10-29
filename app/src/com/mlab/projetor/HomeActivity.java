package com.mlab.projetor;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela inicial inspirada no iPhone/macOS: relogio grande da tela de bloqueio, favoritos
 * como cartoes de cor (paleta) e um Dock translucido com os ajustes do projetor.
 */
public class HomeActivity extends Activity {
    private static final int COLS = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView selKind, selName, info;
    private UsageRing temperatureUsage, cpuUsage, ramUsage, storageUsage, internetUsage;
    private View hardwarePanel;
    private final HardwareUsage hardware = new HardwareUsage();
    private final Runnable hardwareTick = new Runnable() {
        @Override public void run() {
            updateHardware();
            handler.postDelayed(this, 3_000);
        }
    };
    /** Painel com o QR do controle: aparece so pelo botao "Celular" do Dock. */
    private View qrPanel;
    private ImageView qrImage;
    private TextView qrAddress;
    /** Texto do QR atual (refeito so quando o endereco ou o PIN mudam). */
    private String qrText;
    private static final long QR_TIMEOUT_MS = 2 * 60_000L;
    private final Runnable hideQr = () -> showQr(false);
    private LinearLayout grid, recents;
    /** Itens do Dock, para ligar a seta para baixo dos cartoes ao Dock (o FocusFinder nao acha sozinho). */
    private final List<View> dockItems = new ArrayList<>();

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

    /** PIN trocado pelo celular: atualiza o rodape na hora. */
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
        setContentView(build());
        App.inst.startKeepAlive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
        loadRecents();
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

    private View build() {
        LinearLayout root = Ui.col(this);
        root.setBackground(Ui.wallpaper(this));
        root.setPadding(Ui.dp(56), Ui.dp(26), Ui.dp(56), Ui.dp(22));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        root.addView(topBar(), new LinearLayout.LayoutParams(-1, Ui.dp(26)));

        LinearLayout main = Ui.row(this);
        main.setGravity(Gravity.TOP);
        main.setClipChildren(false);
        main.addView(clockColumn(), new LinearLayout.LayoutParams(Ui.dp(330), -1));
        grid = Ui.col(this);
        grid.setClipChildren(false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, -1, 1f);
        glp.leftMargin = Ui.dp(36);
        main.addView(grid, glp);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        mlp.topMargin = Ui.dp(24);
        root.addView(main, mlp);

        TextView favoritesHint = Ui.text(this,
                "Para remover um favorito, selecione o app e segure OK.", 13, Ui.w500, Ui.MUTED);
        favoritesHint.setGravity(Gravity.END);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = Ui.dp(12);
        root.addView(favoritesHint, hintLp);

        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, Ui.dp(84));
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        dlp.topMargin = Ui.dp(22);
        root.addView(dock(), dlp);

        FrameLayout frame = new FrameLayout(this);
        frame.setClipChildren(false);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(qrPanel(), new FrameLayout.LayoutParams(-1, -1));
        return frame;
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

    // ---- Barra superior (estilo barra de menus do macOS) ----

    private View topBar() {
        LinearLayout bar = Ui.row(this);
        // Simbolo monocromatico, sem fundo, na cor do texto do tema.
        bar.addView(Ui.icon(this, R.drawable.ic_glyph, 28, Ui.TEXT));
        TextView brand = Ui.text(this, "Projetor", 15, Ui.w600, Ui.TEXT);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.leftMargin = Ui.dp(10);
        bar.addView(brand, blp);
        String installedVersion = installedVersion();
        if (!installedVersion.isEmpty()) {
            TextView version = Ui.text(this, "v" + installedVersion, 12, Ui.w500, Ui.MUTED);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
            vlp.leftMargin = Ui.dp(8);
            bar.addView(version, vlp);
        }
        bar.addView(Ui.space(this, 1));
        info = Ui.text(this, "", 13, Ui.w500, Ui.MUTED);
        bar.addView(info);
        ImageView wifi = Ui.icon(this, R.drawable.ic_wifi, 18, Ui.TEXT);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(Ui.dp(18), Ui.dp(18));
        wlp.leftMargin = Ui.dp(16);
        bar.addView(wifi, wlp);
        return bar;
    }

    private String installedVersion() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    // ---- Coluna do relogio (estilo tela de bloqueio do iPhone) ----

    private View clockColumn() {
        LinearLayout c = Ui.col(this);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(19);
        date.setTypeface(Ui.w500);
        date.setTextColor(Ui.MUTED);
        date.setIncludeFontPadding(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(10);
        dlp.leftMargin = Ui.dp(4);
        c.addView(date, dlp);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(112);
        time.setTypeface(Ui.w300);
        time.setTextColor(Ui.TEXT);
        time.setLetterSpacing(-0.04f);
        time.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(6);
        c.addView(time, tlp);

        recents = Ui.col(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(8);
        rlp.leftMargin = Ui.dp(4);
        c.addView(recents, rlp);

        c.addView(Ui.vspace(this, 1));
        selKind = Ui.text(this, "", 14, Ui.w600, Ui.ACCENT);
        c.addView(selKind);
        selName = Ui.text(this, "", 34, Ui.w700, Ui.TEXT);
        selName.setSingleLine(true);
        selName.setEllipsize(TextUtils.TruncateAt.END);
        selName.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(6);
        c.addView(selName, nlp);
        LinearLayout meters = Ui.row(this);
        temperatureUsage = new UsageRing(this, "Temp.");
        temperatureUsage.setTemperature(-1);
        cpuUsage = new UsageRing(this, "CPU");
        ramUsage = new UsageRing(this, "RAM");
        storageUsage = new UsageRing(this, "Espaço");
        internetUsage = new UsageRing(this, "Internet");
        internetUsage.setSpeed(Prefs.downloadKbps(this), Prefs.downloadCheckedAt(this));
        for (UsageRing ring : new UsageRing[]{temperatureUsage, cpuUsage, ramUsage, storageUsage, internetUsage}) {
            meters.addView(ring, new LinearLayout.LayoutParams(0, Ui.dp(66), 1f));
        }
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = Ui.dp(30);
        c.addView(meters, mlp);
        hardwarePanel = meters;
        hardwarePanel.setVisibility(Prefs.showHardware(this) ? View.VISIBLE : View.GONE);
        return c;
    }

    // ---- Recentes: o que foi assistido por ultimo (curiosidade, sem foco do controle) ----

    private void loadRecents() {
        recents.removeAllViews();
        List<String[]> list = Prefs.recents(this);
        if (list.isEmpty()) return;
        TextView h = Ui.text(this, "Recentes", 13, Ui.w600, Ui.MUTED);
        recents.addView(h);
        int shown = 0;
        for (String[] r : list) {
            Apps.Entry e = Apps.entry(this, r[0]);
            if (e == null) continue;
            LinearLayout row = Ui.row(this);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(e.icon);
            iv.setBackground(Ui.round(0xFFFFFFFF, 7));
            iv.setPadding(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
            iv.setClipToOutline(true);
            row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(28)));
            LinearLayout txt = Ui.col(this);
            boolean hasTitle = !r[1].isEmpty();
            TextView title = Ui.text(this, hasTitle ? r[1] : e.label, 15, Ui.w600, Ui.TEXT);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            txt.addView(title);
            if (hasTitle) {
                TextView app = Ui.text(this, e.label, 12, Ui.w400, Ui.MUTED);
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
                alp.topMargin = Ui.dp(2);
                txt.addView(app, alp);
            }
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp.leftMargin = Ui.dp(12);
            row.addView(txt, tlp);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = Ui.dp(12);
            recents.addView(row, rowLp);
            if (++shown == 3) break;
        }
    }

    // ---- Favoritos: cartoes de cor, 4 colunas x 2 linhas ----

    private void loadFavorites() {
        grid.removeAllViews();
        List<Apps.Entry> favs = Apps.favorites(this);
        View first = null;
        boolean addShown = false;
        for (int r = 0; r < 2; r++) {
            LinearLayout row = Ui.row(this);
            row.setClipChildren(false);
            for (int col = 0; col < COLS; col++) {
                int i = r * COLS + col;
                View v;
                if (i < favs.size()) {
                    v = tile(favs.get(i), i);
                    if (first == null) first = v;
                } else if (!addShown) {
                    v = addTile();
                    addShown = true;
                } else {
                    v = new View(this);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (col > 0) lp.leftMargin = Ui.dp(16);
                row.addView(v, lp);
                if (r == 1 && v.isFocusable() && !dockItems.isEmpty()) {
                    // Cada coluna desce para o item do Dock mais proximo dela.
                    View target = dockItems.get(Math.min(dockItems.size() - 1, col * 2 + 1));
                    v.setNextFocusDownId(target.getId());
                }
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
            if (r > 0) rlp.topMargin = Ui.dp(16);
            grid.addView(row, rlp);
        }
        if (favs.isEmpty()) {
            selKind.setText("Bem-vindo");
            selName.setText("Escolha seus apps");
        }
        if (first != null && getCurrentFocus() == null) first.requestFocus();
    }

    private View tile(final Apps.Entry a, int index) {
        final int color = Ui.appColor(a.pkg, a.icon, index);
        // No escuro o vidro escurece a cor, entao o texto e sempre branco.
        final int fg = Ui.dark ? 0xFFFFFFFF : Ui.onColor(color);
        LinearLayout t = Ui.col(this);
        t.setContentDescription(a.label);
        t.setPadding(Ui.dp(18), Ui.dp(18), Ui.dp(18), Ui.dp(18));
        GradientDrawable bg = Ui.round(color, 28);
        t.setBackground(bg);

        FrameLayout box = new FrameLayout(this);
        box.setBackground(Ui.round(0xFFFFFFFF, 16));
        box.setElevation(Ui.dp(1));
        if (a.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            box.addView(iv, new FrameLayout.LayoutParams(Ui.dp(42), Ui.dp(42), Gravity.CENTER));
        }
        t.addView(box, new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(58)));
        t.addView(Ui.vspace(this, 1));

        TextView name = Ui.text(this, a.label, 19, Ui.w700, fg);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setLetterSpacing(-0.01f);
        t.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView kind = Ui.text(this, Apps.kind(a.pkg), 13, Ui.w500, fg);
        kind.setAlpha(0.78f);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(-1, -2);
        klp.topMargin = Ui.dp(5);
        t.addView(kind, klp);

        Ui.focusable(t, bg, 1.06f, () -> {
            selKind.setText(Apps.kind(a.pkg));
            selName.setText(a.label);
            App.inst.controller.onScreen(getPackageName(), a.label);
        }, color);
        t.setOnClickListener(v -> open(Apps.launchIntent(this, a.pkg)));
        t.setOnLongClickListener(v -> {
            Prefs.toggleFav(this, a.pkg);
            App.inst.controller.hud.message(a.label + " removido dos favoritos");
            loadFavorites();
            return true;
        });
        return t;
    }

    private View addTile() {
        LinearLayout t = Ui.col(this);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = Ui.glass(28);
        t.setBackground(bg);
        t.addView(Ui.icon(this, R.drawable.ic_plus, 30, Ui.MUTED));
        t.setContentDescription("Adicionar app");
        TextView l = Ui.text(this, "Adicionar", 14, Ui.w600, Ui.MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(10);
        t.addView(l, lp);
        Ui.focusable(t, bg, 1.06f, () -> {
            selKind.setText("Favoritos");
            selName.setText("Adicionar app");
        });
        t.setOnClickListener(v -> open(new Intent(this, AppsActivity.class)));
        return t;
    }

    // ---- Dock (estilo macOS, adaptativo por chipset) ----

    private View dock() {
        LinearLayout d = Ui.row(this);
        d.setBackground(Ui.glass(26));
        d.setElevation(Ui.dp(6));
        d.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
        d.setClipChildren(false);

        // Detecta hardware e carrega intents compatíveis
        Hardware.Chipset chipset = Hardware.detectChipset();

        // Keystone: tenta pacotes específicos do chipset
        Intent keystoneIntent = Hardware.findAvailable(this, Hardware.getKeystoneIntents(chipset));
        if (keystoneIntent != null) {
            dockItem(d, "Keystone", R.drawable.ic_keystone, keystoneIntent);
        }

        // Imagem/Display: tenta pacotes específicos do chipset
        Intent imageIntent = Hardware.findAvailable(this, Hardware.getImageAdjustmentIntents(chipset));
        if (imageIntent != null) {
            dockItem(d, "Imagem", R.drawable.ic_image, imageIntent);
        }

        // HDMI/Input: tenta pacotes específicos do chipset
        Intent hdmiIntent = Hardware.findAvailable(this, Hardware.getHdmiIntents(chipset));
        if (hdmiIntent != null) {
            dockItem(d, "HDMI", R.drawable.ic_hdmi, hdmiIntent);
        }

        // Itens universais (sempre disponíveis)
        dockItem(d, "Wi‑Fi", R.drawable.ic_wifi, new Intent(Settings.ACTION_WIFI_SETTINGS));
        dockItem(d, "Bluetooth", R.drawable.ic_bt, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        dockItem(d, "Ajustes", R.drawable.ic_settings, null).setOnClickListener(v -> settingsMenu());

        View sep = new View(this);
        sep.setBackgroundColor(Ui.dark ? 0x33FFFFFF : 0x221D1D1F);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(1), Ui.dp(52));
        slp.leftMargin = slp.rightMargin = Ui.dp(8);
        d.addView(sep, slp);

        dockItem(d, "Apps", R.drawable.ic_apps, new Intent(this, AppsActivity.class));
        dockItem(d, "Celular", R.drawable.ic_phone, null).setOnClickListener(v -> toggleQr());
        View theme = dockItem(d, Ui.dark ? "Claro" : "Escuro", Ui.dark ? R.drawable.ic_sun : R.drawable.ic_moon, null);
        theme.setOnClickListener(v -> {
            Prefs.setDark(this, !Ui.dark);
            Ui.applyTheme(!Ui.dark);
            recreate();
        });
        return d;
    }

    private boolean available(Intent i) {
        return getPackageManager().resolveActivity(i, 0) != null;
    }

    /** Item do Dock; atalhos de apps do fabricante que nao existem neste aparelho ficam de fora. */
    private View dockItem(LinearLayout dock, String label, int icon, final Intent intent) {
        LinearLayout it = Ui.col(this);
        if (intent != null && !available(intent)) return it;
        it.setContentDescription(label);
        it.setId(View.generateViewId());
        dockItems.add(it);
        it.setGravity(Gravity.CENTER);
        GradientDrawable bg = Ui.round(0x00000000, 16);
        it.setBackground(bg);
        it.addView(Ui.icon(this, icon, 26, Ui.TEXT));
        TextView t = Ui.text(this, label, 11, Ui.w500, Ui.MUTED);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(6);
        it.addView(t, tlp);
        it.setFocusable(true);
        it.setClickable(true);
        it.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Ui.FOCUS_BG : 0x00000000);
            t.setTextColor(has ? Ui.TEXT : Ui.MUTED);
            v.animate().scaleX(has ? 1.14f : 1f).scaleY(has ? 1.14f : 1f).translationY(has ? -Ui.dp(4) : 0)
                    .setDuration(160).start();
            v.setElevation(has ? Ui.dp(8) : 0);
            if (has) App.inst.controller.onScreen(getPackageName(), label);
        });
        if (intent != null) it.setOnClickListener(v -> open(intent));
        dock.addView(it, new LinearLayout.LayoutParams(Ui.dp(76), Ui.dp(68)));
        return it;
    }

    // ---- Ajustes: os do Android e os do proprio app ----

    private android.app.AlertDialog.Builder dialog() {
        return new android.app.AlertDialog.Builder(this, Ui.dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    private void settingsMenu() {
        String hardwareAction = Prefs.showHardware(this)
                ? "Ocultar temperatura, CPU, RAM, espaço e internet" : "Mostrar temperatura, CPU, RAM, espaço e internet";
        String[] items = {"Ajustes do Android", hardwareAction, "Testar velocidade da internet", "Procurar atualização", "Gerar novo PIN do controle", "Voltar à tela original e remover o app"};
        dialog().setTitle("Ajustes").setItems(items, (dlg, which) -> {
            if (which == 0) {
                Intent tv = comp("com.android.tv.settings", "com.android.tv.settings.MainSettings");
                open(available(tv) ? tv : new Intent(Settings.ACTION_SETTINGS));
            } else if (which == 1) {
                Prefs.setShowHardware(this, !Prefs.showHardware(this));
                applyHardwareVisibility();
            } else if (which == 2) {
                testInternet();
            } else if (which == 3) {
                procurarAtualizacao();
            } else if (which == 4) {
                dialog().setTitle("Gerar novo PIN?")
                        .setMessage("Os celulares conectados e os atalhos da Siri vão precisar do PIN novo.")
                        .setPositiveButton("Gerar", (x, y) -> App.inst.server.regeneratePin())
                        .setNegativeButton("Cancelar", null).show();
            } else {
                dialog().setTitle("Remover o app Projetor?")
                        .setMessage("O projetor volta para a tela inicial e o modo mouse de fábrica, "
                                + "e o app Projetor é removido. Seus apps e arquivos não são apagados.")
                        .setPositiveButton("Remover", (x, y) -> Sistema.restoreAndUninstall(this))
                        .setNegativeButton("Cancelar", null).show();
            }
        }).show();
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
        info.setText("Controle no celular  " + ip + ":" + ControlService.PORT + "  ·  PIN " + pin);
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
