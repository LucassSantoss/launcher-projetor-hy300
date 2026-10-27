import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Chave e arquivo de atualizacao do app Projetor (fora do APK; roda no computador).
 *   java tools/Atualizacao.java gerar-chave <arquivo-chave-privada>
 *       Cria a chave ECDSA P-256. Mostra a chave publica para colar em Updater.PUBLIC_KEY.
 *   java tools/Atualizacao.java version-json <chave-privada> <apk> <versionCode> <versionName> <url>
 *       Escreve na saida o version.json assinado que vai para o site.
 * A chave privada NUNCA vai para o repositorio nem para o site.
 */
public class Atualizacao {
    public static void main(String[] a) throws Exception {
        if (a.length == 2 && a[0].equals("gerar-chave")) {
            File f = new File(a[1]);
            if (f.exists()) throw new IllegalStateException("ja existe: " + f);
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = g.generateKeyPair();
            Files.write(f.toPath(), kp.getPrivate().getEncoded());
            System.out.println(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
            return;
        }
        if (a.length == 6 && a[0].equals("version-json")) {
            PrivateKey k = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(new File(a[1]).toPath())));
            byte[] apk = Files.readAllBytes(new File(a[2]).toPath());
            String sha = hex(MessageDigest.getInstance("SHA-256").digest(apk));
            int code = Integer.parseInt(a[3]);
            String msg = "com.mlab.projetor|" + code + "|" + sha;
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(k);
            s.update(msg.getBytes(StandardCharsets.UTF_8));
            String sig = Base64.getEncoder().encodeToString(s.sign());
            System.out.println("{\"code\":" + code + ",\"name\":\"" + a[4] + "\",\"url\":\"" + a[5]
                    + "\",\"size\":" + apk.length + ",\"sha256\":\"" + sha + "\",\"sig\":\"" + sig + "\"}");
            return;
        }
        System.err.println("uso: gerar-chave <arq> | version-json <chave> <apk> <code> <name> <url>");
        System.exit(2);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
