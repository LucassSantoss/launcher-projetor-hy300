package com.mlab.projetor;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executa os comandos vindos do celular. Teclas e toques sao injetados com
 * INJECT_EVENTS (concedido por sermos app de sistema), sempre fora da main thread.
 */
final class Controller {
    private static final String TAG = "Projetor";

    private static final Map<String, Integer> KEYS = new HashMap<>();
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        KEYS.put("up", KeyEvent.KEYCODE_DPAD_UP);
        KEYS.put("down", KeyEvent.KEYCODE_DPAD_DOWN);
        KEYS.put("left", KeyEvent.KEYCODE_DPAD_LEFT);
        KEYS.put("right", KeyEvent.KEYCODE_DPAD_RIGHT);
        KEYS.put("ok", KeyEvent.KEYCODE_DPAD_CENTER);
        KEYS.put("enter", KeyEvent.KEYCODE_ENTER);
        KEYS.put("back", KeyEvent.KEYCODE_BACK);
        KEYS.put("home", KeyEvent.KEYCODE_HOME);
        KEYS.put("menu", KeyEvent.KEYCODE_MENU);
        KEYS.put("del", KeyEvent.KEYCODE_DEL);
        KEYS.put("search", KeyEvent.KEYCODE_SEARCH);
        KEYS.put("playpause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        KEYS.put("play", KeyEvent.KEYCODE_MEDIA_PLAY);
        KEYS.put("pause", KeyEvent.KEYCODE_MEDIA_PAUSE);
        KEYS.put("next", KeyEvent.KEYCODE_MEDIA_NEXT);
        KEYS.put("prev", KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        KEYS.put("fwd", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        KEYS.put("rew", KeyEvent.KEYCODE_MEDIA_REWIND);
        KEYS.put("power", KeyEvent.KEYCODE_POWER);
        KEYS.put("sleep", KeyEvent.KEYCODE_SLEEP);
        KEYS.put("wake", KeyEvent.KEYCODE_WAKEUP);

        ALIASES.put("youtube", "com.google.android.youtube.tv");
        ALIASES.put("netflix", "com.netflix.mediaclient");
        ALIASES.put("disney", "com.disney.disneyplus");
        ALIASES.put("prime", "com.amazon.amazonvideo.livingroom");
        ALIASES.put("globoplay", "com.globo.globotv");
        ALIASES.put("spotify", "com.spotify.tv.android");
        ALIASES.put("espn", "com.espn.score_center");
        ALIASES.put("navegador", "com.tcl.browser");
    }

    private final Context ctx;
    private final Instrumentation inst = new Instrumentation();
    private final ExecutorService queue = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final WindowManager wm;
    final Hud hud;
    private final int sw, sh;

    /**
     * Seta do touchpad: uma camada transparente da tela inteira (nao recebe toques) com a seta
     * dentro. Mover e so deslizar a seta (translation, feito pela GPU); reposicionar a janela a
     * cada quadro travava num aparelho de 1 GB.
     */
    private android.widget.FrameLayout cursorLayer;
    private CursorView cursor;
    private volatile boolean cursorVisible;
    private volatile float cx, cy;
    private final Runnable hideCursor = this::removeCursor;

    private void removeCursor() {
        stopJoystick();
        if (cursorLayer != null) {
            try {
                wm.removeView(cursorLayer);
            } catch (RuntimeException ignored) {
            }
            cursorLayer = null;
            cursor = null;
        }
        cursorVisible = false;
    }

    // Joystick do celular em modo cursor: o celular manda a velocidade (px/s) poucas vezes por
    // segundo e o projetor anima a seta a cada quadro da tela, independente da rede.
    private float joyVx, joyVy;
    private long joyLastMsg;
    private long lastFrameNanos;
    private boolean joyRunning;
    private final android.view.Choreographer.FrameCallback joyFrame = new android.view.Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            if (!joyRunning) return;
            // Sem noticias do celular por 0,4 s (rede caiu, app fechou): para a seta.
            if (SystemClock.uptimeMillis() - joyLastMsg > 400) {
                stopJoystick();
                return;
            }
            float dt = lastFrameNanos == 0 ? 0 : Math.min(0.05f, (now - lastFrameNanos) / 1e9f);
            lastFrameNanos = now;
            if (dt > 0) applyMove(joyVx * dt, joyVy * dt);
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
    };

    Controller(Context c) {
        ctx = c.getApplicationContext();
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        hud = new Hud(ctx);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        sw = dm.widthPixels;
        sh = dm.heightPixels;
        cx = sw / 2f;
        cy = sh / 2f;
    }

    /** Mensagens curtas do WebSocket: k:tecla, m:dx,dy, c, lc, s:dy, t:texto, v:volume(0-100). */
    void command(String msg) {
        try {
            if (msg.startsWith("k:")) key(msg.substring(2));
            else if (msg.startsWith("m:")) {
                String[] p = msg.substring(2).split(",");
                move(Float.parseFloat(p[0]), Float.parseFloat(p[1]));
            } else if (msg.equals("c")) click(false);
            else if (msg.equals("lc")) click(true);
            else if (msg.startsWith("j:")) {
                String[] p = msg.substring(2).split(",");
                joystick(Float.parseFloat(p[0]), Float.parseFloat(p[1]));
            } else if (msg.startsWith("s:")) scroll(Float.parseFloat(msg.substring(2)));
            else if (msg.startsWith("t:")) text(msg.substring(2));
            else if (msg.startsWith("v:")) setVolume(Integer.parseInt(msg.substring(2).trim()));
        } catch (RuntimeException e) {
            Log.w(TAG, "comando invalido: " + msg, e);
        }
    }

    boolean key(String name) {
        if (name == null) return false;
        name = name.toLowerCase(Locale.ROOT).trim();
        switch (name) {
            case "vol_up":
            case "volup":
                volume("up");
                return true;
            case "vol_down":
            case "voldown":
                volume("down");
                return true;
            case "mute":
                volume("mute");
                return true;
        }
        Integer code = KEYS.get(name);
        if (code == null) return false;
        final int k = code;
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) onSelect();
        queue.execute(() -> {
            try {
                sendRemoteKey(k);
            } catch (Throwable e) {
                Log.w(TAG, "falha ao injetar tecla " + k, e);
            }
        });
        return true;
    }

    private int remoteDeviceId = Integer.MIN_VALUE;

    /** Id do controle remoto fisico (teclado com direcional); sem ele, o teclado virtual. */
    private int remoteDevice() {
        if (remoteDeviceId != Integer.MIN_VALUE) return remoteDeviceId;
        int found = android.view.KeyCharacterMap.VIRTUAL_KEYBOARD;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && !d.isVirtual() && (d.getSources() & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) {
                found = id;
                break;
            }
        }
        remoteDeviceId = found;
        return found;
    }

    /**
     * Injeta a tecla como se viesse do controle remoto (dispositivo real).
     * Setas e OK saem com origem so "direcional": a Netflix ignora setas que chegam como
     * "teclado" ou "teclado + direcional" (testado no HY300: so SOURCE_DPAD move a selecao).
     */
    private void sendRemoteKey(int code) {
        // Prioridade 1: Accessibility Service (resolve HOME/VOLTAR e tenta setas via gestos em apps de terceiros)
        if (KeyService.instance != null && KeyService.instance.injectKey(code)) {
            return;
        }
        // Prioridade 2: Instrumentation (fallback para o próprio app ou se o serviço não estiver ativo)
        try {
            int dev = remoteDevice();
            boolean dpad = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_CENTER;
            int src = dpad ? InputDevice.SOURCE_DPAD : InputDevice.SOURCE_KEYBOARD;
            long down = SystemClock.uptimeMillis();
            inst.sendKeySync(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0, 0, dev, 0, KeyEvent.FLAG_FROM_SYSTEM, src));
            inst.sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 0, dev, 0,
                    KeyEvent.FLAG_FROM_SYSTEM, src));
        } catch (SecurityException e) {
            Log.w(TAG, "Falha ao injetar tecla via Instrumentation. Ative o Serviço de Acessibilidade nas configurações.");
        }
    }

    // Volume vindo do celular: sem a barra do sistema (FLAG_SHOW_UI); quem mostra e o nosso aviso.
    void volume(String dir) {
        int adj = "up".equals(dir) ? AudioManager.ADJUST_RAISE
                : "down".equals(dir) ? AudioManager.ADJUST_LOWER
                : AudioManager.ADJUST_TOGGLE_MUTE;
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, adj, 0);
        showVolume();
    }

    void setVolume(int percent) {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int v = Math.max(0, Math.min(max, Math.round(percent / 100f * max)));
        if (v > 0 && audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        }
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
        showVolume();
    }

    private void showVolume() {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        hud.volume(Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max),
                audio.isStreamMute(AudioManager.STREAM_MUSIC));
    }

    // ---- Touchpad: cursor desenhado por cima de tudo + toques injetados nessa posicao ----

    /** Quanto o cursor ja "empurrou" alem da borda de cima/baixo (vira rolagem da pagina). */
    private float edgePush;
    private long lastEdgeScroll;

    /** Velocidade do joystick em px/s; (0,0) para a seta. */
    void joystick(final float vx, final float vy) {
        main.post(() -> {
            joyLastMsg = SystemClock.uptimeMillis();
            joyVx = vx;
            joyVy = vy;
            if (vx == 0 && vy == 0) {
                stopJoystick();
                applyMove(0, 0);
                return;
            }
            if (!joyRunning) {
                joyRunning = true;
                lastFrameNanos = 0;
                android.view.Choreographer.getInstance().postFrameCallback(joyFrame);
            }
        });
    }

    private void stopJoystick() {
        joyRunning = false;
        joyVx = joyVy = 0;
        android.view.Choreographer.getInstance().removeFrameCallback(joyFrame);
    }

    void move(final float dx, final float dy) {
        main.post(() -> applyMove(dx, dy));
    }

    /** Move a seta (na thread principal) e trata a rolagem pela borda do navegador. */
    private void applyMove(float dx, float dy) {
        {
            if (!ensureCursor()) return;
            cx = clamp(cx + dx, 0, sw - 1);
            float ny = cy + dy;
            String prof = profile(screenPkg);
            if ((ny > sh - 1 || ny < 0) && ("web".equals(prof) || "toque".equals(prof))) {
                // Cursor encostado na borda (so em navegador): empurrar com vontade rola a pagina,
                // no maximo uma vez a cada 0,7 s, para nao "descer sem parar".
                edgePush += ny > sh - 1 ? ny - (sh - 1) : ny;
                long now = SystemClock.uptimeMillis();
                if (Math.abs(edgePush) >= Ui.dp(60) && now - lastEdgeScroll > 700) {
                    scrollFrom(cx, sh / 2f, edgePush > 0 ? -Ui.dp(200) : Ui.dp(200));
                    lastEdgeScroll = now;
                    edgePush = 0;
                }
            } else {
                edgePush = 0;
            }
            cy = clamp(ny, 0, sh - 1);
            cursor.setTranslationX(cx);
            cursor.setTranslationY(cy);
            cursorVisible = true;
            main.removeCallbacks(hideCursor);
            main.postDelayed(hideCursor, 8000);
        }
    }

    void click(final boolean longPress) {
        if (!cursorVisible) {
            // Sem cursor na tela, "clique" no touchpad vira OK do controle.
            key("ok");
            return;
        }
        final float x = cx, y = cy;
        main.removeCallbacks(hideCursor);
        main.postDelayed(hideCursor, 8000);
        queue.execute(() -> {
            long t = SystemClock.uptimeMillis();
            touch(t, t, MotionEvent.ACTION_DOWN, x, y);
            SystemClock.sleep(longPress ? 700 : 40);
            touch(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y);
        });
    }

    /** Rolagem com dois dedos: vira um arrasto curto a partir da posicao do cursor. */
    void scroll(final float dy) {
        scrollFrom(cursorVisible ? cx : sw / 2f, sh / 2f, dy);
    }

    /** Arrasto vertical de dy pixels comecando em (x, y0); dy negativo faz a pagina descer. */
    private void scrollFrom(final float x, final float y0, final float dy) {
        final float y1 = clamp(y0 + dy, 1, sh - 2);
        queue.execute(() -> {
            long t = SystemClock.uptimeMillis();
            touch(t, t, MotionEvent.ACTION_DOWN, x, y0);
            for (int i = 1; i <= 8; i++) {
                SystemClock.sleep(12);
                touch(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y0 + (y1 - y0) * i / 8f);
            }
            touch(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y1);
        });
    }

    private void touch(long down, long when, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(down, when, action, x, y, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            inst.sendPointerSync(e);
        } catch (Throwable ex) {
            Log.w(TAG, "falha ao injetar toque", ex);
        } finally {
            e.recycle();
        }
    }

    private boolean ensureCursor() {
        if (cursorLayer != null) return true;
        try {
            android.widget.FrameLayout layer = new android.widget.FrameLayout(ctx);
            CursorView c = new CursorView(ctx);
            layer.addView(c, new android.widget.FrameLayout.LayoutParams(Ui.dp(20), Ui.dp(30), Gravity.TOP | Gravity.START));
            c.setTranslationX(cx);
            c.setTranslationY(cy);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(layer, lp);
            cursorLayer = layer;
            cursor = c;
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "sem permissao para o cursor", e);
            cursorLayer = null;
            cursor = null;
            return false;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Digita texto. O teclado virtual do Android so mapeia ASCII, entao acentos
     * sao removidos ("ação" vira "acao"), o que funciona bem em buscas.
     */
    void text(String s) {
        final String plain = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\x20-\\x7E]", "");
        if (plain.isEmpty()) return;
        queue.execute(() -> {
            try {
                inst.sendStringSync(plain);
            } catch (Throwable e) {
                Log.w(TAG, "falha ao digitar", e);
            }
        });
    }

    // ---- Abrir apps, links e arquivos ----

    boolean openApp(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String n = Apps.plain(name);
        switch (n) {
            case "inicio":
            case "home":
                return key("home");
            case "configuracoes":
            case "ajustes":
                return start(new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")));
            case "keystone":
                return start(new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.telanda.keystone", "com.telanda.keystone.MainActivity")));
            case "imagem":
                return start(new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.gls.Gsettings", "com.gls.Gsettings.MainActivity")));
            case "hdmi":
                return start(new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.softwinner.awsource", "com.softwinner.awsource.MainActivity")));
            case "tv":
            case "tv ao vivo":
            case "televisao":
            case "canais":
                return start(Apps.launchIntent(ctx, Apps.TV_PKG));
            case "wifi":
                return start(new Intent(Settings.ACTION_WIFI_SETTINGS));
            case "bluetooth":
                return start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        }
        String pkg = ALIASES.get(n.replace(" ", ""));
        if (pkg == null && name.contains(".")) pkg = name.trim();
        if (pkg == null) pkg = Apps.findByName(ctx, name);
        if (pkg == null) return false;
        return start(Apps.launchIntent(ctx, pkg));
    }

    /** Abre a TV ao vivo ja no canal pedido ("globo", "cultura"). */
    boolean openChannel(String name) {
        Intent i = Apps.launchIntent(ctx, Apps.TV_PKG);
        i.putExtra(TvActivity.EXTRA_CHANNEL, name);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return start(i);
    }

    boolean openUrl(String u) {
        if (u == null) return false;
        u = u.trim();
        if (u.isEmpty()) return false;
        if (!u.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) u = "https://" + u;
        Uri uri = Uri.parse(u);
        // So links da web: o app roda como sistema, e esquemas como intent:, file: ou content:
        // abririam telas e arquivos internos do aparelho.
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String pkg = null;
        if (host.endsWith("youtube.com") || host.equals("youtu.be")) pkg = "com.google.android.youtube.tv";
        else if (host.endsWith("netflix.com")) pkg = "com.netflix.mediaclient";
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        if (pkg != null) {
            i.setPackage(pkg);
            if (start(i)) return true;
            i.setPackage(null);
        }
        return start(i);
    }

    boolean openFile(File f) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(f.getName()).toLowerCase(Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime == null) mime = "*/*";
        MediaScannerConnection.scanFile(ctx, new String[]{f.getAbsolutePath()}, new String[]{mime}, null);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.fromFile(f), mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Abre direto no app do sistema, sem a pergunta "Abrir com".
        String pkg = mime.startsWith("image/") ? "com.android.gallery3d"
                : mime.startsWith("video/") ? "com.softwinner.TvdVideo"
                : mime.startsWith("audio/") ? "com.android.music" : null;
        if (pkg != null) {
            i.setPackage(pkg);
            if (start(i)) return true;
            i.setPackage(null);
        }
        return start(i);
    }

    private boolean start(Intent i) {
        if (i == null) return false;
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "nao abriu " + i, e);
            return false;
        }
    }

    // ---- Tela atual: o controle do celular se adapta a ela ----

    /** Recebe o estado da tela (JSON) sempre que muda; o WebServer repassa aos celulares. */
    interface ScreenListener {
        void onScreenChanged(String json);
    }

    private volatile ScreenListener screenListener;
    private volatile String screenPkg = "";
    private volatile String screenFocus = "";

    void setScreenListener(ScreenListener l) {
        screenListener = l;
    }

    // ---- Recentes: so entra o que foi assistido de verdade (5 minutos no mesmo titulo/app/canal) ----

    private static final long WATCHED_MS = 5 * 60 * 1000;
    private String watchPkg = "", watchTitle = "";
    private final Runnable watchCommit = this::commitWatch;

    private void commitWatch() {
        if (!watchPkg.isEmpty()) Prefs.addRecent(ctx, watchPkg, watchTitle);
    }

    /** Comeca a contar uma sessao; se continuar igual por 5 minutos, vai para os Recentes. */
    void watch(String pkg, String title) {
        final String t = title == null ? "" : title;
        main.post(() -> {
            if (pkg.equals(watchPkg) && t.equals(watchTitle)) return;
            main.removeCallbacks(watchCommit);
            watchPkg = pkg;
            watchTitle = t;
            main.postDelayed(watchCommit, WATCHED_MS);
        });
    }

    void stopWatch() {
        main.post(() -> {
            main.removeCallbacks(watchCommit);
            watchPkg = "";
            watchTitle = "";
        });
    }

    /** OK num app de video: o titulo focado passa a ser o que esta sendo assistido. */
    void onSelect() {
        String pkg = screenPkg, focus = screenFocus;
        if (!watchable(pkg) || focus.isEmpty() || pkg.equals(ctx.getPackageName())) return;
        watch(pkg, focus);
    }

    String screenApp() {
        return currentPkg();
    }

    String screenName() {
        String pkg = currentPkg();
        if (pkg.equals(ctx.getPackageName())) return TvActivity.visible ? "TV ao vivo" : "Tela inicial";
        Apps.Entry e = pkg.isEmpty() ? null : Apps.entry(ctx, pkg);
        return e != null ? e.label : "nada";
    }

    boolean isMuted() {
        return audio.isStreamMute(AudioManager.STREAM_MUSIC);
    }

    /** Chamado pelo KeyService: app em primeiro plano e (se houver) o item focado. */
    void onScreen(String pkg, String focus) {
        boolean appChanged = !pkg.equals(screenPkg);
        if (appChanged) {
            screenPkg = pkg;
            screenFocus = "";
            // A TV ao vivo (o proprio app) conta os canais por conta propria.
            if (!pkg.equals(ctx.getPackageName())) {
                if (watchable(pkg)) watch(pkg, null);
                else stopWatch();
            } else if (!TvActivity.visible) {
                stopWatch(); // voltou para a tela inicial
            }
        }
        if (focus != null) {
            if (focus.equals(screenFocus) && !appChanged) return;
            screenFocus = focus;
        } else if (!appChanged) {
            return;
        }
        ScreenListener l = screenListener;
        if (l != null) l.onScreenChanged(screenJson());
    }

    /**
     * inicio (nossa tela inicial), video (apps de streaming), web (navegadores), toque (app de
     * celular sem versao de TV, ex.: a Netflix de celular que vem no HY300: so anda com cursor) ou geral.
     */
    static String profile(String pkg) {
        if ("com.mlab.projetor".equals(pkg) && TvActivity.visible) return "video";
        if (pkg == null || pkg.isEmpty() || pkg.equals("com.mlab.projetor")) return "inicio";
        if (pkg.contains("browser") || pkg.contains("chrome") || pkg.contains("webview")) return "web";
        if (touchOnly(pkg)) return "toque";
        if (pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("disney") || pkg.contains("amazonvideo")
                || pkg.contains("globo") || pkg.contains("spotify") || pkg.contains("espn") || pkg.contains("TvdVideo")
                || pkg.contains("gallery") || pkg.contains("hbo") || pkg.contains("paramount")
                || pkg.contains("pluto") || pkg.contains("twitch") || pkg.contains("iptv") || pkg.contains("tv.android")) {
            return "video";
        }
        return "geral";
    }

    private static final java.util.Map<String, Boolean> touchCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** App sem entrada no menu da TV (LEANBACK_LAUNCHER) = feito para celular, navega por toque. */
    private static boolean touchOnly(String pkg) {
        Boolean v = touchCache.get(pkg);
        if (v != null) return v;
        boolean touch = false;
        try {
            android.content.pm.PackageManager pm = App.inst.getPackageManager();
            Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg);
            Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
            touch = pm.queryIntentActivities(tv, 0).isEmpty() && !pm.queryIntentActivities(phone, 0).isEmpty();
        } catch (RuntimeException ignored) {
        }
        touchCache.put(pkg, touch);
        return touch;
    }

    /** Apps em que vale guardar "o que estava assistindo" (Recentes). */
    static boolean watchable(String pkg) {
        String p = profile(pkg);
        return "video".equals(p) || ("toque".equals(p) && (pkg.contains("netflix") || pkg.contains("globo")
                || pkg.contains("disney") || pkg.contains("amazon") || pkg.contains("youtube")));
    }

    /**
     * App em primeiro plano. Pergunta ao Android primeiro (o app e de sistema, entao ve os outros
     * apps): neste aparelho os eventos de acessibilidade dos outros apps nao chegam, e o screenPkg
     * ficava parado na tela inicial.
     */
    private String currentPkg() {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            @SuppressWarnings("deprecation")
            List<ActivityManager.RunningTaskInfo> t = am.getRunningTasks(1);
            if (!t.isEmpty() && t.get(0).topActivity != null) return t.get(0).topActivity.getPackageName();
        } catch (Exception ignored) {
        }
        return screenPkg;
    }

    private String screenJson() {
        String pkg = currentPkg();
        Apps.Entry e = pkg.isEmpty() ? null : Apps.entry(ctx, pkg);
        String name = pkg.equals("com.mlab.projetor") ? (TvActivity.visible ? "TV ao vivo" : "Tela inicial")
                : e != null ? e.label : pkg;
        return "{\"tipo\":\"tela\",\"app\":" + Json.str(pkg)
                + ",\"nome\":" + Json.str(name)
                + ",\"perfil\":" + Json.str(profile(pkg))
                + ",\"foco\":" + Json.str(pkg.equals(screenPkg) ? screenFocus : "") + "}";
    }

    // ---- Estado para a pagina e para os Atalhos ----

    String stateJson() {
        String screen = screenJson();
        int vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return "{\"ok\":true," + screen.substring(1, screen.length() - 1)
                + ",\"volume\":" + Math.round(vol * 100f / Math.max(1, max))
                + ",\"mudo\":" + audio.isStreamMute(AudioManager.STREAM_MUSIC)
                + ",\"escuro\":" + Ui.dark + "}";
    }

    String appsJson() {
        List<String> favs = Prefs.favs(ctx);
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (Apps.Entry e : Apps.all(ctx, true)) {
            if (sb.length() > 1) sb.append(',');
            int fav = favs.indexOf(e.pkg);
            int color = Ui.appColor(e.pkg, e.icon, fav >= 0 ? fav : i);
            sb.append("{\"pkg\":").append(Json.str(e.pkg))
                    .append(",\"nome\":").append(Json.str(e.label))
                    .append(",\"fav\":").append(fav)
                    .append(",\"cor\":").append(Json.str(String.format("#%06X", color & 0xFFFFFF)))
                    .append('}');
            i++;
        }
        return sb.append(']').toString();
    }

    private final Map<String, byte[]> iconCache = new HashMap<>();

    /** Icone do app em PNG 96x96 para a pagina do celular (cacheado). */
    synchronized byte[] iconPng(String pkg) {
        if (pkg == null) return null;
        byte[] cached = iconCache.get(pkg);
        if (cached != null) return cached;
        Apps.Entry e = Apps.entry(ctx, pkg);
        if (e == null || e.icon == null) return null;
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        e.icon.setBounds(0, 0, 96, 96);
        e.icon.draw(c);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
        b.recycle();
        byte[] png = bo.toByteArray();
        iconCache.put(pkg, png);
        return png;
    }
}
