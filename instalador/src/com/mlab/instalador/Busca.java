package com.mlab.instalador;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Procura aparelhos com a porta do ADB (5555) aberta na faixa /24 do Wi‑Fi do celular. */
final class Busca {
    private Busca() {}

    /** @param meuIp IPv4 do celular, ex. 192.168.0.15 */
    static List<String> procurar(String meuIp) throws InterruptedException {
        String[] p = meuIp.split("\\.");
        String base = p[0] + "." + p[1] + "." + p[2] + ".";
        final List<String> achados = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(48);
        for (int i = 1; i <= 254; i++) {
            final String ip = base + i;
            if (ip.equals(meuIp)) continue;
            pool.execute(() -> {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(ip, AdbClient.PORT), 600);
                    achados.add(ip);
                } catch (Exception ignored) {
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        List<String> l = new ArrayList<>(achados);
        Collections.sort(l, (a, b) -> Integer.compare(last(a), last(b)));
        return l;
    }

    private static int last(String ip) {
        return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1));
    }
}
