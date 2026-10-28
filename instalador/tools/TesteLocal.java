package com.mlab.instalador;

import java.net.ServerSocket;

/** Testes locais que nao precisam de Android nem de um projetor. */
public class TesteLocal {
    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket(0)) {
            port = server.getLocalPort();
            check("porta aberta detectada", Instalacao.portaAberta("127.0.0.1", port));
        }
        check("porta fechada detectada", !Instalacao.portaAberta("127.0.0.1", port));
        System.out.println("TUDO OK");
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALHA ") + what);
        if (!ok) System.exit(1);
    }
}
