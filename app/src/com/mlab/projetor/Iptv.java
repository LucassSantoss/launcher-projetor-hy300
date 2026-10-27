package com.mlab.projetor;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lista de canais abertos do Brasil do projeto iptv-org (M3U), com cache local. */
final class Iptv {
    static final String LIST_URL = "https://iptv-org.github.io/iptv/countries/br.m3u";
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;
    private static final Pattern GROUP = Pattern.compile("group-title=\"([^\"]*)\"");

    static final class Channel {
        final String name, group, url;

        Channel(String name, String group, String url) {
            this.name = name;
            this.group = group;
            this.url = url;
        }
    }

    private Iptv() {}

    /** Usa o cache do dia; senao baixa. Sem internet, cai no cache antigo. Chamar fora da main thread. */
    static List<Channel> load(Context c) {
        File cache = new File(c.getFilesDir(), "iptv_br.m3u");
        String text = null;
        if (!cache.isFile() || System.currentTimeMillis() - cache.lastModified() > MAX_AGE_MS) {
            try {
                text = download(LIST_URL);
                try (FileOutputStream fo = new FileOutputStream(cache)) {
                    fo.write(text.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        }
        if (text == null && cache.isFile()) {
            try (InputStream in = new FileInputStream(cache)) {
                text = readAll(in);
            } catch (IOException ignored) {
            }
        }
        return text == null ? new ArrayList<>() : parse(text);
    }

    static List<Channel> parse(String m3u) {
        List<Channel> out = new ArrayList<>();
        String name = null, group = "";
        for (String raw : m3u.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Canal";
                // Tira sufixos tecnicos como "(1080p)" e "[Geo-blocked]".
                name = name.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "").trim();
                Matcher m = GROUP.matcher(line);
                group = m.find() ? m.group(1).split(";")[0] : "";
            } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                out.add(new Channel(name, group, line));
                name = null;
            }
        }
        return out;
    }

    private static String download(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(15000);
        try (InputStream in = con.getInputStream()) {
            if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
            return readAll(in);
        } finally {
            con.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }
}
