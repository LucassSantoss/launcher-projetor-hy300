package com.mlab.instalador;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.Inet4Address;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Instalador do app Projetor pelo celular: acha o projetor no Wi‑Fi (porta do ADB), conecta,
 * confirma o modelo, instala e, no fim, oferece desligar o ADB. Tudo numa tela que troca de conteudo.
 */
public class MainActivity extends Activity {
    private static final int INK = 0xFFFFFFFF, INK2 = 0xFFA3A3A3, INK3 = 0xFF737373, PAPER = 0xFF0A0A0A, LINE = 0xFF3D3D3D;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout body;
    private AdbClient adb;
    private Instalacao.Info info;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAPER);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(40), dp(24), dp(32));
        scroll.addView(body);
        setContentView(scroll);
        inicio();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        fechar();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (adb != null || !"inicio".equals(body.getTag())) {
            fechar();
            inicio();
        } else {
            super.onBackPressed();
        }
    }

    // ---- Telas ----

    private String modeloSelecionado = null;

    private void inicio() {
        limpar("inicio");
        titulo("Selecione seu aparelho");
        texto("Escolha o modelo do seu projetor ou TV Box para garantir a compatibilidade.", INK2, 17);
        espaco(24);

        // Lista de modelos suportados
        String[] modelosSuportados = {
            "HY300 / HY300 Pro",
            "TX6 / TX6 Mini",
            "X96 Max / X96 Max+",
            "H96 Max / H96 Pro",
            "Tanix TX3 / TX5",
            "Outro projetor Android (Allwinner/Rockchip)"
        };

        for (final String m : modelosSuportados) {
            botao(m, false, v -> {
                modeloSelecionado = m;
                telaConexao();
            });
        }

        espaco(16);
        texto("Smart TVs (Samsung, LG, Sony, Roku, etc.)", INK3, 14);
        espaco(8);
        botao("Minha TV é uma Smart TV fechada", false, v -> funilIncompativel());

        rodape("Versão do Instalador: 2.0 | MLAB Solution");
    }

    private void telaConexao() {
        limpar("conexao");
        titulo("Conectar ao " + modeloSelecionado);
        texto("Modelo selecionado: " + modeloSelecionado, INK2, 16);
        espaco(20);
        passo("1", "Celular e aparelho no mesmo Wi‑Fi.");
        passo("2", "No aparelho, ligue a Depuração USB (ADB).");
        link("Como ligar a Depuração USB", v -> comoLigarAdb());
        espaco(24);
        botao("Procurar na rede", true, v -> procurar());
        espaco(24);
        texto("Ou digite o IP do aparelho", INK3, 14);
        espaco(8);
        final EditText ip = new EditText(this);
        ip.setHint("Ex.: 192.168.0.20");
        ip.setHintTextColor(INK3);
        ip.setTextColor(INK);
        ip.setTextSize(18);
        ip.setSingleLine(true);
        ip.setInputType(InputType.TYPE_CLASS_PHONE);
        ip.setImeOptions(EditorInfo.IME_ACTION_GO);
        ip.setBackground(caixa(false));
        ip.setPadding(dp(14), dp(12), dp(14), dp(12));
        body.addView(ip, new LinearLayout.LayoutParams(-1, -2));
        espaco(10);
        botao("Conectar via IP", false, v -> {
            String s = ip.getText().toString().trim();
            if (!s.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                aviso("Digite um IP válido como 192.168.0.20.");
                return;
            }
            conectar(s);
        });
        espaco(16);
        link("Voltar e escolher outro modelo", v -> inicio());
    }

    private void funilIncompativel() {
        limpar("incompativel");
        titulo("Aparelho não suportado via rede");
        texto("Smart TVs com sistemas fechados (Tizen, WebOS, Android TV oficial de marcas como Sony/Samsung/LG) bloqueiam a instalação de apps de sistema via rede Wi-Fi.", INK2, 16);
        espaco(20);
        texto("Para esses aparelhos, a única forma segura de instalar a interface MLAB é através do nosso Pendrive Físico Otimizado, que já vem pronto para plugar e usar.", INK, 17);
        espaco(28);

        // Botão simulando ação de compra/redirect
        botao("Comprar Pendrive Físico Otimizado", true, v -> {
            aviso("Redirecionando para a loja MLAB... (Funcionalidade de checkout pendente)");
        });
        espaco(12);
        botao("Voltar ao início", false, v -> inicio());
    }

    private void procurar() {
        final String meu = meuIpWifi();
        if (meu == null) {
            aviso("Conecte o celular ao Wi‑Fi (o mesmo do projetor) e tente de novo.");
            return;
        }
        limpar("busca");
        titulo("Procurando…");
        texto("Olhando a rede " + meu.substring(0, meu.lastIndexOf('.')) + ".x", INK2, 16);
        espaco(24);
        carregando();
        worker.execute(() -> {
            List<String> l;
            try {
                l = Busca.procurar(meu);
            } catch (InterruptedException e) {
                return;
            }
            final List<String> achados = l;
            runOnUiThread(() -> resultados(achados));
        });
    }

    private void resultados(List<String> achados) {
        limpar("resultados");
        if (achados.isEmpty()) {
            titulo("Nenhum projetor encontrado");
            texto("Confira se o projetor está ligado, no mesmo Wi‑Fi, e com a Depuração USB ligada.", INK2, 16);
            espaco(24);
            link("Como ligar a Depuração USB", v -> comoLigarAdb());
            espaco(16);
            botao("Procurar de novo", true, v -> procurar());
            botao("Voltar", false, v -> inicio());
            return;
        }
        titulo(achados.size() == 1 ? "Achei um aparelho" : "Achei " + achados.size() + " aparelhos");
        texto("Toque no seu projetor.", INK2, 16);
        espaco(20);
        for (final String ip : achados) botao(ip, false, v -> conectar(ip));
        espaco(8);
        botao("Procurar de novo", false, v -> procurar());
    }

    private void conectar(final String ip) {
        limpar("conectando");
        titulo("Conectando…");
        final TextView status = texto(ip, INK2, 16);
        espaco(24);
        carregando();
        worker.execute(() -> {
            try {
                AdbKey key = AdbKey.loadOrCreate(new File(getFilesDir(), "adbkey"));
                AdbClient c = AdbClient.connect(ip, key, () -> runOnUiThread(() -> {
                    status.setText("Olhe a tela do projetor: apareceu \"Permitir depuração USB?\". "
                            + "Marque \"Sempre permitir\" e escolha OK com o controle.");
                    status.setTextColor(INK);
                }), 90_000);
                Instalacao.Info i = Instalacao.info(c);
                adb = c;
                info = i;
                runOnUiThread(this::confirmar);
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> erro("Não consegui conectar", msg + "\n\nConfira se a Depuração USB está ligada no projetor."));
            }
        });
    }

    private void confirmar() {
        limpar("confirmar");
        titulo("Projetor encontrado");
        TextView nome = texto(info.nome(), INK, 20);
        nome.setTypeface(Typeface.DEFAULT_BOLD);
        espaco(12);
        if (info.installed) texto("O Projetor já está instalado. Instalar de novo atualiza para a versão deste app e mantém seus ajustes.", INK2, 16);
        else texto("O app vira a tela inicial do projetor. A tela de fábrica continua lá e dá para voltar a ela em Ajustes.", INK2, 16);
        espaco(28);
        botao("Instalar o Projetor", true, v -> instalar());
        botao("Cancelar", false, v -> {
            fechar();
            inicio();
        });
    }

    private void instalar() {
        limpar("instalando");
        titulo("Instalando…");
        texto("Não desligue o projetor. Leva menos de um minuto.", INK2, 16);
        espaco(24);
        carregando();
        worker.execute(() -> {
            try {
                byte[] apk = asset("Projetor.apk");
                final Instalacao.Saida s = Instalacao.instalar(adb, info, apk);
                runOnUiThread(() -> resultado(s));
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> erro("A instalação parou", msg));
            }
        });
    }

    private void resultado(Instalacao.Saida s) {
        switch (s.resultado) {
            case OK:
                limpar("pronto");
                titulo("Pronto");
                texto(s.telaInicial ? "O Projetor já é a tela inicial do seu projetor."
                        : "O app foi instalado. Aperte a tecla Início do controle e escolha Projetor.", INK, 18);
                espaco(16);
                texto("Para usar o celular como controle: no projetor, aperte Celular na barra de baixo e aponte a câmera para o QR.", INK2, 16);
                espaco(28);
                texto("Recomendado: desligue o acesso ADB. Com ele ligado, qualquer aparelho no seu Wi‑Fi consegue controlar o projetor.", INK2, 15);
                espaco(12);
                botao("Desligar o acesso ADB", true, v -> desligarAdb());
                botao("Deixar ligado", false, v -> {
                    fechar();
                    finish();
                });
                return;
            case INCOMPATIVEL:
                erro("Este projetor não é compatível", "O sistema deste aparelho não aceita o app. Nada foi alterado no projetor.");
                return;
            case ANDROID_ANTIGO:
                erro("Android antigo demais", "O app precisa do Android 9 ou mais novo. Nada foi alterado no projetor.");
                return;
            case OUTRA_VERSAO:
                erro("Já existe outro Projetor instalado", "Há uma versão do app feita de outra fonte. Remova ela no projetor (Ajustes → Voltar à tela original) e tente de novo.");
                return;
            case VERSAO_MAIS_NOVA:
                erro("O projetor já tem uma versão mais nova", "Não é preciso instalar de novo.");
                return;
            case SEM_ESPACO:
                erro("Falta espaço no projetor", "Apague alguns apps no projetor e tente de novo.");
                return;
            default:
                erro("A instalação falhou", s.detalhe);
        }
    }

    private void desligarAdb() {
        final AdbClient c = adb;
        adb = null;
        worker.execute(() -> {
            if (c != null) {
                Instalacao.desligarAdb(c);
                c.close();
            }
            runOnUiThread(() -> {
                limpar("fim");
                titulo("Tudo certo");
                texto("Acesso ADB desligado. Para remover o app no futuro, use Ajustes → Voltar à tela original, no projetor.", INK2, 16);
                espaco(28);
                botao("Fechar", true, v -> finish());
            });
        });
    }

    private void erro(String t, String detalhe) {
        fechar();
        limpar("erro");
        titulo(t);
        texto(detalhe, INK2, 16);
        espaco(28);
        botao("Voltar ao início", true, v -> inicio());
    }

    private void comoLigarAdb() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Depuração USB no projetor")
                .setMessage("1. Configurações → Preferências do dispositivo → Sobre.\n"
                        + "2. Aperte OK 7 vezes em Build, até aparecer \"Você agora é um desenvolvedor\".\n"
                        + "3. Volte, abra Opções do desenvolvedor e ligue Depuração USB.\n\n"
                        + "Em muitos HY300 ela já vem ligada.")
                .setPositiveButton("Entendi", null)
                .show();
    }

    // ---- Rede e arquivos ----

    /** IPv4 do celular no Wi‑Fi (ou na rede local ativa), ou null sem Wi‑Fi. */
    private String meuIpWifi() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null || !(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp == null) continue;
            for (LinkAddress la : lp.getLinkAddresses()) {
                if (la.getAddress() instanceof Inet4Address && !la.getAddress().isLoopbackAddress()) {
                    return la.getAddress().getHostAddress();
                }
            }
        }
        return null;
    }

    private byte[] asset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    private void fechar() {
        AdbClient c = adb;
        adb = null;
        if (c != null) worker.execute(c::close);
    }

    // ---- Componentes (preto e branco, como o site) ----

    private void limpar(String tela) {
        body.removeAllViews();
        body.setTag(tela);
    }

    private void titulo(String s) {
        TextView t = texto(s, INK, 30);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setLetterSpacing(-0.02f);
        espaco(10);
    }

    private TextView texto(String s, int color, float sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, 1.25f);
        body.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private void passo(String n, String s) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView num = new TextView(this);
        num.setText(n);
        num.setTextColor(INK3);
        num.setTypeface(Typeface.MONOSPACE);
        num.setGravity(Gravity.CENTER);
        num.setBackground(caixa(false));
        row.addView(num, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(INK);
        t.setTextSize(17);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = dp(14);
        row.addView(t, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(12);
        body.addView(row, rp);
    }

    private void link(String s, View.OnClickListener l) {
        TextView t = texto(s, INK2, 15);
        t.getPaint().setUnderlineText(true);
        t.setPadding(dp(48), dp(4), 0, dp(4));
        t.setOnClickListener(l);
    }

    private void botao(String s, boolean primario, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primario ? PAPER : INK);
        b.setBackground(caixa(primario));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.bottomMargin = dp(10);
        body.addView(b, lp);
    }

    private void carregando() {
        ProgressBar p = new ProgressBar(this);
        p.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(INK));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.gravity = Gravity.START;
        body.addView(p, lp);
    }

    private void espaco(int dpH) {
        body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dpH)));
    }

    private void rodape(String s) {
        View v = new View(this);
        body.addView(v, new LinearLayout.LayoutParams(1, 0, 1f));
        espaco(24);
        texto(s, INK3, 12);
    }

    private void aviso(String s) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage(s).setPositiveButton("OK", null).show();
    }

    private GradientDrawable caixa(boolean cheia) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(2));
        g.setColor(cheia ? INK : Color.TRANSPARENT);
        g.setStroke(dp(1), cheia ? INK : LINE);
        return g;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
