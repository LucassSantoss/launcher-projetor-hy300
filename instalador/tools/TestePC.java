package com.mlab.instalador;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;

/**
 * Teste do instalador rodando no computador contra um projetor de verdade.
 * Uso: java TestePC <ip-do-pc> <ip-do-projetor> <apk-teste-grande> <apk-chave-errada>
 * So instala e remove o pacote de teste com.mlab.projetor.teste.
 */
public class TestePC {
    public static void main(String[] a) throws Exception {
        File keyFile = File.createTempFile("adbkey", ".der");
        keyFile.delete();
        AdbKey key = AdbKey.loadOrCreate(keyFile);
        AdbKey again = AdbKey.loadOrCreate(keyFile);
        byte[] token = new byte[20];
        new java.security.SecureRandom().nextBytes(token);
        byte[] sig = key.sign(token);
        // Confere a assinatura com a chave publica: precisa voltar prefixo SHA-1 + token.
        java.security.interfaces.RSAPublicKey pub = pubOf(again);
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.DECRYPT_MODE, pub);
        byte[] dec = c.doFinal(sig);
        check("assinatura do token", dec.length == 35 && Arrays.equals(Arrays.copyOfRange(dec, 15, 35), token));
        byte[] pk = key.publicKeyPayload("teste@pc");
        String s = new String(pk, "UTF-8");
        byte[] raw = Base64.getDecoder().decode(s.substring(0, s.indexOf(' ')));
        check("chave publica no formato do adb (524 bytes, termina com \\0)", raw.length == 524 && pk[pk.length - 1] == 0);

        long t = System.currentTimeMillis();
        List<String> found = Busca.procurar(a[0]);
        check("busca achou " + a[1] + " em " + (System.currentTimeMillis() - t) / 1000 + " s " + found, found.contains(a[1]));

        try (AdbClient adb = AdbClient.connect(a[1], key, () -> System.out.println("  (pediu permissao)"), 60_000)) {
            check("conectou: " + adb.banner.trim(), adb.banner.startsWith("device"));
            Instalacao.Info info = Instalacao.info(adb);
            check("info: " + info.nome() + " sdk " + info.sdk + " projetor instalado=" + info.installed, info.sdk >= 28);
            check("tela inicial atual: " + Instalacao.telaInicialAtual(adb), true);

            byte[] grande = Files.readAllBytes(new File(a[2]).toPath());
            String out = adb.exec("exec:cmd package install -r -S " + grande.length, grande).trim();
            check("instalou APK de teste de " + grande.length / 1024 + " KB em varios blocos: " + out, out.contains("Success"));
            String uid = adb.shell("dumpsys package com.mlab.projetor.teste | grep -m1 userId=");
            check("pacote de teste com uid de sistema: " + uid.trim(), uid.contains("userId=1000"));
            check("removeu o pacote de teste: " + adb.shell("pm uninstall com.mlab.projetor.teste"), true);

            byte[] errado = Files.readAllBytes(new File(a[3]).toPath());
            out = adb.exec("exec:cmd package install -r -S " + errado.length, errado).trim();
            check("aparelho 'incompativel' recusa: " + out, out.contains("SHARED_USER_INCOMPATIBLE"));
            check("nada ficou instalado", !adb.shell("pm list packages com.mlab.projetor.teste").contains("teste"));
        }
        System.out.println("TUDO OK");
    }

    private static java.security.interfaces.RSAPublicKey pubOf(AdbKey k) throws Exception {
        java.lang.reflect.Field f = AdbKey.class.getDeclaredField("pub");
        f.setAccessible(true);
        return (java.security.interfaces.RSAPublicKey) f.get(k);
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALHA ") + what);
        if (!ok) System.exit(1);
    }
}
