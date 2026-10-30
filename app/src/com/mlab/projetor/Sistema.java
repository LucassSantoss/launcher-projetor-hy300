package com.mlab.projetor;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tela inicial e volta ao original sem computador: quem instala o APK direto no projetor
 * (gerenciador de arquivos, navegador ou pendrive) nao passa pelo instalar.bat.
 */
final class Sistema {
    private static final String TAG = "Projetor";
    private static final String ROLE_HOME = "android.app.role.HOME";
    static final String MOUSE_PKG = "com.sxk.testdsndpi";
    static final ComponentName MOUSE_SVC = new ComponentName(MOUSE_PKG, "com.sxk.testdsndpi.MyService");

    private Sistema() {}

    private static Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    /** Pacote da tela inicial padrao agora, ou null se o Android ainda pergunta qual usar. */
    static String currentHome(Context c) {
        ResolveInfo ri = c.getPackageManager().resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo == null) return null;
        String p = ri.activityInfo.packageName;
        return "android".equals(p) ? null : p;
    }

    /**
     * Outra tela inicial instalada (a do fabricante), sem a do app e sem a de espera do Android.
     * Inclui as desativadas: para o app ser a unica tela inicial, a do fabricante pode ter sido
     * desligada, e e justamente ela que precisamos devolver ao remover o app.
     */
    private static String otherHome(Context c) {
        List<ResolveInfo> l = c.getPackageManager().queryIntentActivities(
                homeIntent(), PackageManager.MATCH_DISABLED_COMPONENTS);
        for (ResolveInfo ri : l) {
            String p = ri.activityInfo.packageName;
            if (!p.equals(c.getPackageName()) && !p.equals("android") && !p.equals("com.android.settings")
                    && !p.equals("com.android.tv.settings") && !p.equals("com.android.provision")) return p;
        }
        return null;
    }

    /**
     * Na primeira vez que o app abre, guarda a tela inicial do fabricante e vira a padrao.
     * So uma vez: se a pessoa trocar depois pelos ajustes do Android, o app respeita.
     */
    static void claimHomeOnce(Context c) {
        if (Prefs.homeClaimed(c)) return;
        String cur = currentHome(c);
        if (Prefs.originalHome(c) == null) {
            String orig = cur != null && !cur.equals(c.getPackageName()) ? cur : otherHome(c);
            if (orig != null) Prefs.setOriginalHome(c, orig);
        }
        if (c.getPackageName().equals(cur)) {
            Prefs.setHomeClaimed(c);
            return;
        }
        setHome(c, c.getPackageName(), ok -> {
            if (ok) Prefs.setHomeClaimed(c);
            Log.i(TAG, "tela inicial definida: " + ok);
        });
    }

    /** Define a tela inicial padrao (o mesmo que "cmd package set-home-activity"). */
    static void setHome(Context c, String pkg, Consumer<Boolean> done) {
        try {
            Object rm = c.getSystemService(Class.forName("android.app.role.RoleManager"));
            rm.getClass().getMethod("addRoleHolderAsUser", String.class, String.class, int.class,
                    UserHandle.class, Executor.class, Consumer.class)
                    .invoke(rm, ROLE_HOME, pkg, 0, Process.myUserHandle(), c.getMainExecutor(), done);
        } catch (Exception e) {
            Log.w(TAG, "nao foi possivel definir a tela inicial", e);
            done.accept(false);
        }
    }

    static boolean hasMouseMode(Context c) {
        try {
            c.getPackageManager().getPackageInfo(MOUSE_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Devolve o projetor ao original e remove o app: tela inicial do fabricante, modo mouse do
     * fabricante, lista de acessibilidade sem o app e decodificador de audio padrao.
     */
    static void restoreAndUninstall(final Context c) {
        String orig = Prefs.originalHome(c);
        if (orig == null || !installed(c, orig)) orig = otherHome(c);
        // Para o app ficar como unica tela inicial, a do fabricante pode ter sido desativada
        // (pm disable-user). Sem religar, ela nao pode virar a padrao e o aparelho ficaria
        // sem nenhuma tela inicial depois que o app sair.
        final String home = orig != null && enableHome(c, orig) ? orig : null;
        if (home == null) {
            Log.w(TAG, "nenhuma tela inicial para devolver; o app nao sera removido");
            App.inst.controller.hud.message("Sem outra tela inicial: remova pelos Ajustes do Android");
            return;
        }
        restoreSystem(c);
        Runnable rest = () -> {
            Intent i = homeIntent().setPackage(home).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(i);
            } catch (Exception e) {
                Log.w(TAG, "tela inicial original nao abriu", e);
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> uninstallSelf(c), 1500);
        };
        setHome(c, home, ok -> rest.run());
    }

    /**
     * Garante que a tela inicial do fabricante esteja ligada antes de devolve-la.
     * Devolve false se ela nao existir ou se nao der para religar: ai e melhor manter o app
     * do que deixar o aparelho sem tela inicial nenhuma.
     */
    private static boolean enableHome(Context c, String pkg) {
        if (!installed(c, pkg)) return false;
        PackageManager pm = c.getPackageManager();
        try {
            if (pm.getApplicationEnabledSetting(pkg) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    && pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).enabled) {
                return true;
            }
            pm.setApplicationEnabledSetting(pkg, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0);
            return pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).enabled;
        } catch (Exception e) {
            Log.w(TAG, "nao foi possivel religar a tela inicial " + pkg, e);
            return false;
        }
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static void restoreSystem(Context c) {
        ContentResolver cr = c.getContentResolver();
        if (Prefs.systemStateSaved(c)) {
            try {
                c.getPackageManager().setComponentEnabledSetting(MOUSE_SVC, Prefs.originalMouseState(c),
                        PackageManager.DONT_KILL_APP);
            } catch (Exception e) {
                Log.w(TAG, "nao foi possivel restaurar o componente do modo mouse", e);
            }
            try {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        Prefs.originalAccessibilityServices(c));
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED,
                        Prefs.originalAccessibilityEnabled(c));
            } catch (Exception e) {
                Log.w(TAG, "nao foi possivel restaurar a acessibilidade", e);
            }
        } else {
            restoreLegacyAccessibility(c, cr);
        }
        try {
            // Volta o decodificador de audio ao padrao (vazio = valor de fabrica).
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, "debug.stagefright.ccodec", "");
        } catch (Exception e) {
            Log.w(TAG, "nao foi possivel desfazer o ajuste da Netflix", e);
        }
    }

    /** Instalacoes anteriores nao guardavam o estado original; mantem a restauracao antiga. */
    private static void restoreLegacyAccessibility(Context c, ContentResolver cr) {
        String mine = new ComponentName(c, KeyService.class).flattenToString();
        boolean mouse = hasMouseMode(c);
        try {
            if (mouse) {
                c.getPackageManager().setComponentEnabledSetting(MOUSE_SVC,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
            }
            String cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            StringBuilder list = new StringBuilder();
            if (cur != null) {
                for (String svc : cur.split(":")) {
                    if (svc.isEmpty() || svc.equals(mine) || svc.equals(MOUSE_SVC.flattenToString())) continue;
                    if (list.length() > 0) list.append(':');
                    list.append(svc);
                }
            }
            if (mouse) {
                if (list.length() > 0) list.append(':');
                list.append(MOUSE_SVC.flattenToString());
            }
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.toString());
            if (list.length() == 0) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        } catch (Exception e) {
            Log.w(TAG, "nao foi possivel restaurar o modo mouse", e);
        }
    }

    private static void uninstallSelf(Context c) {
        try {
            Intent done = new Intent("com.mlab.projetor.REMOVIDO").setPackage(c.getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(c, 0, done, PendingIntent.FLAG_IMMUTABLE);
            c.getPackageManager().getPackageInstaller().uninstall(c.getPackageName(), pi.getIntentSender());
        } catch (Exception e) {
            Log.w(TAG, "nao foi possivel remover o app sozinho; abrindo a tela de desinstalar", e);
            Intent i = new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + c.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        }
    }
}
