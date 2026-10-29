package com.mlab.projetor;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Servico de acessibilidade do app:
 * - intercepta as teclas de volume do controle fisico (sem a barra do fabricante, com o Hud);
 * - acompanha qual app esta aberto e qual item esta focado, para o controle do celular
 *   se adaptar a cada tela ("Selecionado: Netflix").
 */
public class KeyService extends AccessibilityService {
    private static final int MAX_LEN = 80;
    public static KeyService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /**
     * Injeta uma tecla globalmente usando o Accessibility Service.
     * HOME/VOLTAR/RECENTES: performGlobalAction (funciona 100% sem root).
     * SETAS/OK: tenta navegar via AccessibilityNodeInfo ou dispatchGesture.
     */
    public boolean injectKey(int keyCode) {
        // Teclas globais que funcionam 100% via Accessibility sem root
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_HOME:
                return performGlobalAction(GLOBAL_ACTION_HOME);
            case android.view.KeyEvent.KEYCODE_BACK:
                return performGlobalAction(GLOBAL_ACTION_BACK);
            case android.view.KeyEvent.KEYCODE_APP_SWITCH:
                return performGlobalAction(GLOBAL_ACTION_RECENTS);
            case android.view.KeyEvent.KEYCODE_POWER:
                return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }

        // Para setas e OK: tenta usar dispatchGesture (API 24+) para simular swipe/toque
        // ou navegação via AccessibilityNodeInfo
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return injectDpadViaGesture(keyCode);
        }
        return false;
    }

    /**
     * Simula D-Pad via gestos de swipe (funciona em muitos apps como Netflix/YouTube).
     * Swipe curto na direção oposta = seta naquela direção.
     */
    private boolean injectDpadViaGesture(int keyCode) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false;

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = dm.widthPixels / 2f;
        float cy = dm.heightPixels / 2f;
        float dist = Math.min(dm.widthPixels, dm.heightPixels) * 0.15f; // 15% da tela

        float startX, startY, endX, endY;
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_DPAD_UP:
                startX = cx; startY = cy + dist; endX = cx; endY = cy - dist;
                break;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                startX = cx; startY = cy - dist; endX = cx; endY = cy + dist;
                break;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                startX = cx + dist; startY = cy; endX = cx - dist; endY = cy;
                break;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                startX = cx - dist; startY = cy; endX = cx + dist; endY = cy;
                break;
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
                // Para OK/Enter: tenta clicar no nó focado
                return clickFocusedNode();
            default:
                return false;
        }

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        android.accessibilityservice.GestureDescription.Builder builder =
                new android.accessibilityservice.GestureDescription.Builder();
        builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(
                path, 0, 100)); // 100ms de duração

        final boolean[] result = {false};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        dispatchGesture(builder.build(), new android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(android.accessibilityservice.GestureDescription gestureDescription) {
                result[0] = true;
                latch.countDown();
            }
            @Override
            public void onCancelled(android.accessibilityservice.GestureDescription gestureDescription) {
                result[0] = false;
                latch.countDown();
            }
        }, null);

        try {
            latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /**
     * Clica no nó atualmente focado (para OK/Enter).
     */
    private boolean clickFocusedNode() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (focused != null) {
            boolean clicked = focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            focused.recycle();
            root.recycle();
            return clicked;
        }
        // Fallback: tenta foco de entrada
        focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) {
            boolean clicked = focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            focused.recycle();
            root.recycle();
            return clicked;
        }
        root.recycle();
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN && k != KeyEvent.KEYCODE_VOLUME_MUTE) {
            // Teclas do controle fisico: deixa passar e depois avisa o celular do novo foco.
            // OK do controle fisico: o item focado num app de video vai para os Recentes.
            if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)
                    && App.inst != null) {
                App.inst.controller.onSelect();
            }
            return false;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && App.inst != null) {
            App.inst.controller.volume(k == KeyEvent.KEYCODE_VOLUME_UP ? "up" : k == KeyEvent.KEYCODE_VOLUME_DOWN ? "down" : "mute");
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (App.inst == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        Controller ctl = App.inst.controller;
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // Teclado e janelas do sistema nao contam como "app aberto".
                // Do proprio app so contam as telas (Activity); o aviso de volume e o cursor tambem
                // geram esse evento e fariam parecer que o usuario voltou para a tela inicial.
                CharSequence cls = event.getClassName();
                boolean ownOverlay = pkg.equals(getPackageName()) && (cls == null || !cls.toString().endsWith("Activity"));
                if (!ownOverlay && !pkg.equals("android") && !pkg.equals("com.android.systemui") && !pkg.contains("inputmethod")) {
                    ctl.onScreen(pkg, null);
                }
                break;
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                String label = describe(event);
                if (label != null) ctl.onScreen(pkg, label);
                break;
            default:
        }
    }

    /** Texto do item focado vindo de um evento (quando o sistema envia eventos de foco). */
    private static String describe(AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();
        String s = null;
        if (node != null) {
            s = describe(node);
            node.recycle();
        }
        if (s == null && !event.getText().isEmpty()) s = clean(event.getText().get(0).toString());
        if (s == null && event.getContentDescription() != null) s = clean(event.getContentDescription().toString());
        return s;
    }

    /** Texto do item: o proprio texto/descricao, senao os textos dos filhos. */
    private static String describe(AccessibilityNodeInfo node) {
        String s = nodeText(node);
        if (s == null) {
            StringBuilder sb = new StringBuilder();
            collect(node, sb, 0);
            if (sb.length() > 0) s = sb.toString();
        }
        return s == null ? null : clean(s);
    }

    private static String clean(String s) {
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        return s.length() > MAX_LEN ? s.substring(0, MAX_LEN - 1) + "…" : s;
    }

    private static String nodeText(AccessibilityNodeInfo n) {
        CharSequence d = n.getContentDescription();
        if (d != null && d.toString().trim().length() > 0) return d.toString();
        CharSequence t = n.getText();
        if (t != null && t.toString().trim().length() > 0) return t.toString();
        return null;
    }

    private static void collect(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (depth > 3 || sb.length() > MAX_LEN) return;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            String t = nodeText(c);
            if (t != null) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(t.trim());
            } else {
                collect(c, sb, depth + 1);
            }
            c.recycle();
        }
    }

    @Override
    public void onInterrupt() {
    }
}
