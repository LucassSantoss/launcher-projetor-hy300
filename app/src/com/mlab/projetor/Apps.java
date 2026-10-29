package com.mlab.projetor;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lista e abre os apps instalados. */
final class Apps {
    /** Pseudo-pacote do cartao "TV ao vivo" (abre a TvActivity do proprio app). */
    static final String TV_PKG = "tv.aovivo";

    static final class Entry {
        final String pkg;
        final String label;
        final Drawable icon;

        Entry(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private Apps() {}

    static Intent launchIntent(Context c, String pkg) {
        if (TV_PKG.equals(pkg)) return new Intent(c, TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    static Entry entry(Context c, String pkg) {
        if (TV_PKG.equals(pkg)) return tvEntry(c);
        PackageManager pm = c.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (launchIntent(c, pkg) == null) return null;
            return new Entry(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Arte horizontal do proprio app; null permite usar o icone como alternativa. */
    static Drawable banner(Context c, String pkg) {
        if (TV_PKG.equals(pkg)) return null;
        PackageManager pm = c.getPackageManager();
        try {
            Intent launch = launchIntent(c, pkg);
            ActivityInfo activity = launch == null ? null : launch.resolveActivityInfo(pm, 0);
            Drawable banner = activity == null ? null : activity.loadBanner(pm);
            if (banner != null) return banner;
        } catch (RuntimeException ignored) {
            // Um recurso da atividade com defeito nao impede tentar a arte da aplicacao.
        }
        try {
            return pm.getApplicationBanner(pkg);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return null;
        }
    }

    /** Todos os apps abriveis (TV e celular), sem duplicatas, em ordem alfabetica. */
    static List<Entry> all(Context c, boolean withIcons) {
        PackageManager pm = c.getPackageManager();
        Map<String, Entry> map = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (map.containsKey(pkg) || pkg.equals(c.getPackageName())) continue;
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                map.put(pkg, new Entry(pkg, pm.getApplicationLabel(ai).toString(),
                        withIcons ? pm.getApplicationIcon(ai) : null));
            }
        }
        List<Entry> list = new ArrayList<>(map.values());
        list.add(tvEntry(c));
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    static List<Entry> favorites(Context c) {
        List<Entry> out = new ArrayList<>();
        for (String pkg : Prefs.favs(c)) {
            Entry e = entry(c, pkg);
            if (e != null) out.add(e);
            if (out.size() >= Prefs.MAX_FAVS) break;
        }
        return out;
    }

    static Entry tvEntry(Context c) {
        return new Entry(TV_PKG, "TV ao vivo", c.getDrawable(R.drawable.ic_tv_app));
    }

    static String kind(String pkg) {
        if (TV_PKG.equals(pkg)) return "Canais abertos";
        if (pkg.contains("youtube")) return "Vídeos";
        if (pkg.contains("netflix") || pkg.contains("disney") || pkg.contains("amazonvideo")) return "Filmes e séries";
        if (pkg.contains("globo")) return "TV e novelas";
        if (pkg.contains("spotify")) return "Música";
        if (pkg.contains("espn")) return "Esportes";
        if (pkg.contains("browser")) return "Internet";
        return "Aplicativo";
    }

    static String plain(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    /** Acha um app pelo nome falado/digitado ("globoplay", "Prime Video"). */
    static String findByName(Context c, String name) {
        String want = plain(name).replace(" ", "");
        if (want.isEmpty()) return null;
        String partial = null;
        for (Entry e : all(c, false)) {
            String l = plain(e.label).replace(" ", "");
            if (l.equals(want)) return e.pkg;
            if (partial == null && (l.contains(want) || want.contains(l))) partial = e.pkg;
        }
        return partial;
    }
}
