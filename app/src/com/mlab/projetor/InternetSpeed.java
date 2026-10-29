package com.mlab.projetor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Teste manual limitado de download, sem gravar o conteudo recebido. */
final class InternetSpeed {
    static final int MAX_BYTES = 20_000_000;
    static final long SAMPLE_NANOS = 8_000_000_000L;

    private InternetSpeed() {}

    static boolean shouldTest(boolean visible, boolean running, boolean manual, long lastAttempt, long now) {
        if (!visible || running) return false;
        return manual || lastAttempt <= 0 || now < lastAttempt || now - lastAttempt >= 5 * 60_000L;
    }

    static int measureKbps() throws IOException {
        return measureKbps(new URL("https://speed.cloudflare.com/__down?bytes=" + MAX_BYTES
                + "&cache=" + System.nanoTime()));
    }

    static int measureKbps(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(3_000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Resposta inesperada");
            String type = connection.getContentType();
            if (type == null || !type.startsWith("application/octet-stream")) {
                throw new IOException("Conteudo inesperado");
            }
            try (InputStream stream = connection.getInputStream()) {
                return sample(stream);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static int sample(InputStream stream) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long bytes = 0;
        long start = System.nanoTime();
        while (bytes < MAX_BYTES && System.nanoTime() - start < SAMPLE_NANOS) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            int read = stream.read(buffer, 0, (int) Math.min(buffer.length, MAX_BYTES - bytes));
            if (read < 0) throw new IOException("Download incompleto");
            bytes += read;
        }
        if (bytes < 64 * 1024) throw new IOException("Amostra insuficiente");
        return kbps(bytes, System.nanoTime() - start);
    }

    static int kbps(long bytes, long nanos) throws IOException {
        if (bytes <= 0 || nanos <= 0) throw new IOException("Sem dados suficientes");
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.round(bytes * 8_000_000.0 / nanos)));
    }
}
