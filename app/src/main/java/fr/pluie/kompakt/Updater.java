package fr.pluie.kompakt;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Mise à jour depuis l'appli : lecture de la dernière Release du dépôt public des APK sur GitHub,
 * comparaison avec la version installée, téléchargement de l'APK de cette variante (E Ink ou couleur)
 * puis installation par l'installateur d'Android (l'utilisateur confirme ; l'appli doit avoir le droit
 * d'installer des applis, réglage Android « Installer des applis inconnues »).
 */
final class Updater {
    private Updater() {}

    /** Dépôt public où la compilation publie les APK (le dépôt du code est privé). */
    static final String REPO = "mdeberly/paper_meteo-apk";
    static final String ACTION_STATUS = "fr.pluie.kompakt.UPDATE_STATUS";

    /** Dernière version publiée. */
    static final class Release {
        String version = "", apkUrl, notes = "";
    }

    /** Dernière Release publiée (hors du fil principal). */
    static Release latest() throws Exception {
        JSONObject o = new JSONObject(Http.get("https://api.github.com/repos/" + REPO + "/releases/latest"));
        Release r = new Release();
        r.version = o.optString("tag_name", "").replaceFirst("^[vV]", "");
        r.notes = o.optString("body", "");
        String wanted = Ui.COLOR ? "PaperMeteo-Color.apk" : "PaperMeteo-EInk.apk";
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (wanted.equals(a.optString("name"))) r.apkUrl = a.optString("browser_download_url");
            }
        }
        return r;
    }

    /** Vrai si {@code candidate} est plus récente que {@code installed} (« 2.4 » > « 2.30 », numéros décimaux). */
    static boolean newer(String candidate, String installed) {
        try {
            return new BigDecimal(number(candidate)).compareTo(new BigDecimal(number(installed))) > 0;
        } catch (Exception e) {
            return false; // étiquette non numérique (« v1.2-commune »…) : ignorée
        }
    }

    private static String number(String v) {
        return v.replaceFirst("^[vV]", "").trim();
    }

    /** Télécharge l'APK dans le cache de l'appli (hors du fil principal) ; progress reçoit 0..100. */
    static File download(Context c, String url, java.util.function.IntConsumer progress) throws Exception {
        File out = new File(c.getCacheDir(), "update.apk");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(true);
        try {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            long total = con.getContentLengthLong(), done = 0;
            try (InputStream in = con.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    o.write(buf, 0, n);
                    done += n;
                    if (total > 0) progress.accept((int) (100 * done / total));
                }
            }
        } finally {
            con.disconnect();
        }
        return out;
    }

    /**
     * Confie l'APK à l'installateur d'Android. Le résultat revient à {@code activity} (action
     * {@link #ACTION_STATUS}) : il faut alors afficher la confirmation d'Android (voir {@link #onStatus}).
     */
    static void install(Activity activity, File apk) throws Exception {
        PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream o = s.openWrite("paper_meteo.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                s.fsync(o);
            }
            Intent back = new Intent(activity, activity.getClass()).setAction(ACTION_STATUS)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending = PendingIntent.getActivity(activity, 0, back,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            s.commit(pending.getIntentSender());
        }
    }

    /**
     * Retour de l'installateur : affiche la confirmation d'Android si elle est demandée.
     * Renvoie un message d'erreur à montrer, ou null.
     */
    static String onStatus(Activity activity, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) activity.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) return null;
        String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        return msg == null ? "" : msg;
    }
}
