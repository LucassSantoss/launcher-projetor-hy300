"""Executa Apps real com banners e falhas simulados na fronteira do Android."""
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = {
    'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable; public class Drawable {}',
    'android/content/Context.java': '''package android.content;
public class Context {
 public android.content.pm.PackageManager pm = new android.content.pm.PackageManager();
 public android.content.pm.PackageManager getPackageManager() { return pm; }
 public String getPackageName() { return "home"; }
 public android.graphics.drawable.Drawable getDrawable(int id) { return null; }
}''',
    'android/content/Intent.java': '''package android.content;
public class Intent {
 public static final int FLAG_ACTIVITY_NEW_TASK=1, FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=2;
 public static final String ACTION_MAIN="main", CATEGORY_LEANBACK_LAUNCHER="tv", CATEGORY_LAUNCHER="phone";
 public Intent() {} public Intent(String action) {} public Intent(Context c, Class<?> type) {}
 public Intent addFlags(int flags) { return this; } public Intent addCategory(String cat) { return this; }
 public android.content.pm.ActivityInfo resolveActivityInfo(android.content.pm.PackageManager pm, int flags) {
  if (pm.failActivity) throw new IllegalStateException();
  return pm.activity;
 }
}''',
    'android/content/pm/ApplicationInfo.java': '''package android.content.pm;
public class ApplicationInfo {}''',
    'android/content/pm/ActivityInfo.java': '''package android.content.pm;
public class ActivityInfo {
 public String packageName; public ApplicationInfo applicationInfo;
 public android.graphics.drawable.Drawable banner;
 public android.graphics.drawable.Drawable loadBanner(PackageManager pm) { return banner; }
}''',
    'android/content/pm/ResolveInfo.java': '''package android.content.pm;
public class ResolveInfo { public ActivityInfo activityInfo; }''',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager {
 public static class NameNotFoundException extends Exception {}
 public ActivityInfo activity = new ActivityInfo();
 public android.graphics.drawable.Drawable appBanner;
 public boolean failActivity, failApplication, missing, launchable=true;
 public int bannerRequests;
 public android.content.Intent getLeanbackLaunchIntentForPackage(String pkg) {
  return launchable ? new android.content.Intent() : null;
 }
 public android.content.Intent getLaunchIntentForPackage(String pkg) { return null; }
 public ApplicationInfo getApplicationInfo(String pkg, int flags) throws NameNotFoundException { return new ApplicationInfo(); }
 public CharSequence getApplicationLabel(ApplicationInfo ai) { return "App"; }
 public android.graphics.drawable.Drawable getApplicationIcon(ApplicationInfo ai) { return null; }
 public android.graphics.drawable.Drawable getApplicationBanner(String pkg) throws NameNotFoundException {
  bannerRequests++;
  if (missing) throw new NameNotFoundException();
  if (failApplication) throw new IllegalStateException();
  return appBanner;
 }
 public java.util.List<ResolveInfo> queryIntentActivities(android.content.Intent i, int flags) {
  return java.util.Collections.emptyList();
 }
}''',
    'com/mlab/projetor/Prefs.java': '''package com.mlab.projetor;
class Prefs { static final int MAX_FAVS=8;
 static java.util.List<String> favs(android.content.Context c) { return java.util.Collections.emptyList(); }
}''',
    'com/mlab/projetor/TvActivity.java': 'package com.mlab.projetor; class TvActivity {}',
    'com/mlab/projetor/R.java': 'package com.mlab.projetor; class R { static class drawable { static int ic_tv_app; } }',
    'com/mlab/projetor/BannerTest.java': '''package com.mlab.projetor;
import android.content.Context;
import android.graphics.drawable.Drawable;
public class BannerTest {
 static void check(boolean ok) { if (!ok) throw new AssertionError(); }
 public static void main(String[] args) {
  Context c = new Context(); Drawable activity = new Drawable(), app = new Drawable();
  c.pm.activity.banner=activity; c.pm.appBanner=app;
  check(Apps.banner(c,"app")==activity); check(c.pm.bannerRequests==0);
  c.pm.activity.banner=null; check(Apps.banner(c,"app")==app);
  c.pm.failActivity=true; check(Apps.banner(c,"app")==app);
  c.pm.failApplication=true; check(Apps.banner(c,"app")==null);
  c.pm.failApplication=false; c.pm.missing=true; check(Apps.banner(c,"app")==null);
  c.pm.missing=false; c.pm.failActivity=false; c.pm.activity=null;
  check(Apps.banner(c,"app")==app);
  c.pm.launchable=false; check(Apps.banner(c,"app")==app);
  c.pm.appBanner=null; check(Apps.banner(c,"app")==null);
  int requests=c.pm.bannerRequests;
  check(Apps.banner(c,Apps.TV_PKG)==null); check(c.pm.bannerRequests==requests);
  System.out.println("Banners: prioridade, ausencia, falhas e TV interna OK");
 }
}''',
}
with tempfile.TemporaryDirectory() as tmp:
    folder = pathlib.Path(tmp)
    for name, source in SOURCES.items():
        target = folder / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source)
    subprocess.run(['javac', '-d', tmp, *map(str, folder.rglob('*.java')),
                    str(ROOT / 'src/com/mlab/projetor/Apps.java')], check=True)
    subprocess.run(['java', '-cp', tmp, 'com.mlab.projetor.BannerTest'], check=True)
