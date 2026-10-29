# Kit do app Projetor: instala e desinstala pelo computador, via ADB na rede Wi-Fi (ou cabo USB).
# Uso: chamado por instalar.bat e desinstalar.bat. Mensagens sem acentos (o console do Windows
# nao mostra acentos direito em todos os computadores).
param([Parameter(Mandatory = $true)][ValidateSet('instalar', 'desinstalar')][string]$Acao)

$ErrorActionPreference = 'Continue'
$PKG = 'com.mlab.projetor'
$HOME_APP = 'com.mlab.projetor/.HomeActivity'
$MOUSE_PKG = 'com.sxk.testdsndpi'
$MOUSE_SVC = 'com.sxk.testdsndpi/com.sxk.testdsndpi.MyService'
$BACKUP_DEV = '/data/local/tmp/projetor_kit_backup.txt'
$KIT = Split-Path -Parent $PSScriptRoot
$APK = Join-Path $KIT 'Projetor.apk'
$DADOS = Join-Path $env:LOCALAPPDATA 'ProjetorKit'
$script:ADB = $null
$script:DEV = $null

# ---------------------------------------------------------------- utilitarios

function Titulo([string]$t) {
    Write-Host ''
    Write-Host ('== ' + $t + ' ==') -ForegroundColor Cyan
}
function Ok([string]$t) { Write-Host ('  OK  ' + $t) -ForegroundColor Green }
function Aviso([string]$t) { Write-Host ('  !   ' + $t) -ForegroundColor Yellow }
function Erro([string]$t) { Write-Host ('  X   ' + $t) -ForegroundColor Red }
function Info([string]$t) { Write-Host ('      ' + $t) }

function SimNao([string]$pergunta) {
    while ($true) {
        $r = (Read-Host ($pergunta + ' (S/N)')).Trim().ToUpper()
        if ($r -eq 'S' -or $r -eq 'SIM') { return $true }
        if ($r -eq 'N' -or $r -eq 'NAO') { return $false }
    }
}

function Sair([int]$codigo) { exit $codigo }

# Roda o adb e devolve a saida como texto (sem as mensagens de "daemon starting").
function A {
    $o = & $script:ADB @args 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ -notmatch '^\* daemon' -and $_ -notmatch '^adb: \* ' }
    return (($o -join "`n").Trim())
}
# Comando no aparelho escolhido.
function Sh {
    return (A -s $script:DEV shell @args)
}

# ---------------------------------------------------------------- ADB

function Achar-Adb {
    $locais = @(
        (Join-Path $DADOS 'platform-tools\adb.exe'),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
    )
    $winget = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    if (Test-Path $winget) {
        Get-ChildItem $winget -Directory -Filter 'Google.PlatformTools*' -ErrorAction SilentlyContinue |
            ForEach-Object { $locais += (Join-Path $_.FullName 'platform-tools\adb.exe') }
    }
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { $locais = @($cmd.Source) + $locais }
    foreach ($l in $locais) { if ($l -and (Test-Path $l)) { return $l } }
    return $null
}

function Instalar-Adb {
    Titulo 'Ferramenta ADB'
    Info 'Para falar com o projetor, o computador precisa do ADB (Android Platform Tools),'
    Info 'distribuido gratuitamente pelo Google. Ele nao esta neste computador.'
    Info ('Sera baixado de dl.google.com (cerca de 7 MB) para ' + $DADOS)
    if (-not (SimNao 'Baixar agora?')) {
        Erro 'Sem o ADB nao da para continuar.'
        Sair 1
    }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    New-Item -ItemType Directory -Force $DADOS | Out-Null
    $zip = Join-Path $DADOS 'platform-tools.zip'
    $tmp = Join-Path $DADOS 'novo'
    try {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -UseBasicParsing 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
        Expand-Archive -Path $zip -DestinationPath $tmp -Force
    } catch {
        Erro ('Falhou o download: ' + $_.Exception.Message)
        Info 'Confira a internet e rode de novo.'
        Sair 1
    }
    $novo = Join-Path $tmp 'platform-tools\adb.exe'
    # So aceita o adb assinado pelo Google.
    $sig = Get-AuthenticodeSignature $novo
    if ($sig.Status -ne 'Valid' -or $sig.SignerCertificate.Subject -notmatch 'O=Google LLC') {
        Remove-Item -Recurse -Force $tmp, $zip -ErrorAction SilentlyContinue
        Erro 'O arquivo baixado nao tem a assinatura digital do Google. Nada foi instalado.'
        Sair 1
    }
    $destino = Join-Path $DADOS 'platform-tools'
    if (Test-Path $destino) { Remove-Item -Recurse -Force $destino }
    Move-Item (Join-Path $tmp 'platform-tools') $destino
    Remove-Item -Recurse -Force $tmp, $zip -ErrorAction SilentlyContinue
    Ok 'ADB instalado (assinatura do Google conferida).'
    return (Join-Path $destino 'adb.exe')
}

# ---------------------------------------------------------------- encontrar o projetor

function Redes-Locais {
    $cfg = Get-NetIPConfiguration -ErrorAction SilentlyContinue | Where-Object { $_.IPv4DefaultGateway -and $_.IPv4Address }
    $ips = @()
    foreach ($c in $cfg) { foreach ($a in $c.IPv4Address) { $ips += $a.IPAddress } }
    if (-not $ips) {
        $ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
            ForEach-Object { $_.IPAddress }
    }
    return @($ips | Select-Object -Unique)
}

# Procura aparelhos com a porta 5555 (ADB pela rede) aberta na faixa /24 de cada rede do PC.
function Varrer-Rede {
    $meus = Redes-Locais
    if (-not $meus) { return @() }
    $tentativas = @()
    foreach ($ip in $meus) {
        $base = ($ip -split '\.')[0..2] -join '.'
        Info ('Procurando na rede ' + $base + '.x ...')
        for ($i = 1; $i -le 254; $i++) {
            $alvo = "$base.$i"
            if ($alvo -eq $ip) { continue }
            $c = New-Object System.Net.Sockets.TcpClient
            $tentativas += [pscustomobject]@{ Ip = $alvo; C = $c; T = $c.ConnectAsync($alvo, 5555) }
        }
    }
    Start-Sleep -Milliseconds 1500
    $abertos = @()
    foreach ($t in $tentativas) {
        if ($t.T.IsCompleted -and -not $t.T.IsFaulted -and $t.C.Connected) { $abertos += $t.Ip }
        try { $t.C.Close() } catch { }
    }
    return @($abertos | Select-Object -Unique)
}

function Estado([string]$serial) {
    foreach ($l in ((A devices) -split "`n")) {
        $p = $l -split '\s+'
        if ($p.Count -ge 2 -and $p[0] -eq $serial) { return $p[1] }
    }
    return 'ausente'
}

# Espera o usuario aceitar o aviso "Permitir depuracao?" na tela do projetor.
function Esperar-Autorizacao([string]$serial) {
    if ((Estado $serial) -ne 'unauthorized') { return }
    Aviso ('O aparelho ' + $serial + ' pediu permissao.')
    Info 'Olhe a tela do projetor: deve aparecer "Permitir depuracao USB?".'
    Info 'Marque "Sempre permitir deste computador" e escolha OK/Permitir com o controle.'
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        if ((Estado $serial) -eq 'device') { Ok 'Permissao concedida.'; return }
    }
    Aviso 'A permissao nao chegou a tempo.'
}

function Descrever([string]$serial) {
    $fab = A -s $serial shell getprop ro.product.manufacturer
    $mod = A -s $serial shell getprop ro.product.model
    $and = A -s $serial shell getprop ro.build.version.release
    return ("$mod ($fab, Android $and)").Trim()
}

function Escolher-Projetor {
    Titulo 'Procurando o projetor'
    Info 'O projetor precisa estar ligado e no mesmo Wi-Fi deste computador.'
    $achados = @()
    foreach ($ip in (Varrer-Rede)) {
        $s = "$ip`:5555"
        A connect $s | Out-Null
        if ((Estado $s) -ne 'ausente') { $achados += $s }
    }
    # Aparelhos ja conectados (cabo USB ou conexoes anteriores).
    foreach ($l in ((A devices) -split "`n")) {
        $p = $l -split '\s+'
        if ($p.Count -ge 2 -and ($p[1] -eq 'device' -or $p[1] -eq 'unauthorized') -and $achados -notcontains $p[0]) { $achados += $p[0] }
    }
    # Aparelhos que pedem permissao so sao identificados depois de escolhidos (a permissao
    # aparece na tela deles; nao incomoda as outras TVs da casa).
    $lista = @()
    foreach ($s in $achados) {
        $nome = if ((Estado $s) -eq 'device') { Descrever $s } else { 'Aparelho Android (pede permissao na tela)' }
        $lista += [pscustomobject]@{ Serial = $s; Nome = $nome }
    }

    while ($true) {
        if ($lista.Count -gt 0) {
            Write-Host ''
            for ($i = 0; $i -lt $lista.Count; $i++) {
                Write-Host ('  [' + ($i + 1) + '] ' + $lista[$i].Nome + '  -  ' + $lista[$i].Serial)
            }
            Write-Host '  [D] Digitar o endereco IP do projetor'
            Write-Host '  [S] Sair'
            $r = (Read-Host 'Escolha').Trim().ToUpper()
        } else {
            Aviso 'Nenhum projetor encontrado.'
            Info 'Confira: projetor ligado, no mesmo Wi-Fi, e "Depuracao USB/ADB" ligada nas opcoes'
            Info 'do desenvolvedor do projetor. O endereco IP aparece nos ajustes de Wi-Fi do projetor.'
            $r = (Read-Host 'Digite o IP do projetor (ex.: 192.168.0.20) ou S para sair').Trim().ToUpper()
            if ($r -ne 'S') { $r = 'D:' + $r }
        }
        if ($r -eq 'S') { Sair 1 }
        $n = 0
        if ([int]::TryParse($r, [ref]$n) -and $n -ge 1 -and $n -le $lista.Count) {
            $s = $lista[$n - 1].Serial
            Esperar-Autorizacao $s
            if ((Estado $s) -eq 'device') { $script:DEV = $s; break }
            Aviso 'Sem permissao para usar esse aparelho.'
            continue
        }
        if ($r -eq 'D' -or $r.StartsWith('D:')) {
            $ip = if ($r.StartsWith('D:')) { $r.Substring(2) } else { (Read-Host 'IP do projetor').Trim() }
            if ($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}(:\d+)?$') { Aviso 'Endereco invalido.'; continue }
            if ($ip -notmatch ':') { $ip = "$ip`:5555" }
            Info (A connect $ip)
            Esperar-Autorizacao $ip
            if ((Estado $ip) -eq 'device') { $script:DEV = $ip; break }
            Aviso 'Nao consegui conectar nesse endereco.'
        }
    }
    # Solta os outros aparelhos que a busca conectou.
    foreach ($s in $achados) { if ($s -ne $script:DEV -and $s -match ':') { A disconnect $s | Out-Null } }
    Ok ('Projetor: ' + (Descrever $script:DEV) + '  -  ' + $script:DEV)
}

# ---------------------------------------------------------------- informacoes do aparelho

function Instalado { return [bool]((Sh pm list packages $PKG) -split "`n" | Where-Object { $_.Trim() -eq "package:$PKG" }) }
function Tem-Mouse { return [bool]((Sh pm list packages $MOUSE_PKG) -split "`n" | Where-Object { $_.Trim() -eq "package:$MOUSE_PKG" }) }

# Telas iniciais instaladas, sem a do app e sem as telas de espera do Android.
function Launchers {
    $out = Sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
    return @(($out -split "`n") | ForEach-Object { $_.Trim() } |
        Where-Object { $_ -match '^[\w.]+/[\w.$]+$' -and $_ -notlike "$PKG/*" -and $_ -notmatch 'FallbackHome|ResolverActivity|Provision' })
}

function Launcher-Atual {
    $out = Sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
    $ult = (($out -split "`n") | Where-Object { $_ -match '^[\w.]+/[\w.$]+$' } | Select-Object -Last 1)
    if ($ult) { $ult = $ult.Trim() }
    if ($ult -and $ult -notlike "$PKG/*" -and $ult -notmatch 'ResolverActivity|FallbackHome') { return $ult }
    $l = @(Launchers)
    if ($l.Count -ge 1) { return $l[0] }
    return ''
}

function Ler-Backup {
    $b = @{}
    $txt = Sh "cat $BACKUP_DEV 2>/dev/null"
    if (-not ($txt -match 'home=')) {
        $local = Join-Path $DADOS ('backups\' + ($script:DEV -replace '[^\w.-]', '_') + '.txt')
        if (Test-Path $local) { $txt = Get-Content -Raw $local }
    }
    foreach ($l in ($txt -split "`n")) {
        $i = $l.IndexOf('=')
        if ($i -gt 0) { $b[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim() }
    }
    return $b
}

function Gravar-Backup($b) {
    $linhas = ($b.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value }) -join "`n"
    $dir = Join-Path $DADOS 'backups'
    New-Item -ItemType Directory -Force $dir | Out-Null
    $local = Join-Path $dir (($script:DEV -replace '[^\w.-]', '_') + '.txt')
    [IO.File]::WriteAllText($local, $linhas + "`n", (New-Object Text.UTF8Encoding $false))
    A -s $script:DEV push $local $BACKUP_DEV | Out-Null
}

function Desligar-Adb([string]$aviso) {
    Titulo 'Seguranca'
    Info 'O acesso ADB pela rede deixa qualquer computador do seu Wi-Fi controlar o projetor'
    Info 'por completo (em muitos projetores desse tipo, sem senha nenhuma).'
    Info ('Recomendado: desligar agora. ' + $aviso)
    if (-not (SimNao 'Desligar o acesso ADB agora?')) {
        Aviso 'ADB continua ligado. Lembre de desligar depois.'
        return
    }
    $serial = $script:DEV
    Sh settings put global adb_enabled 0 | Out-Null
    Start-Sleep -Seconds 4
    if ($serial -match ':') {
        A disconnect $serial | Out-Null
        A connect $serial | Out-Null
        Start-Sleep -Seconds 2
        if ((Estado $serial) -eq 'device') {
            Aviso 'O projetor continua aceitando ADB pela rede mesmo com a opcao desligada.'
            Info 'Esse firmware mantem o ADB sempre ligado; nao ha como desligar pelo kit.'
            Info 'Use o projetor so em redes Wi-Fi de confianca (com senha).'
            return
        }
    }
    Ok 'Acesso ADB desligado.'
}

# ---------------------------------------------------------------- instalar

function Instalar {
    if (-not (Test-Path $APK)) { Erro ('Arquivo nao encontrado: ' + $APK); Sair 1 }
    Escolher-Projetor
    $sdk = 0
    [int]::TryParse((Sh getprop ro.build.version.sdk), [ref]$sdk) | Out-Null
    if ($sdk -lt 28) {
        Erro 'Este aparelho tem Android antigo demais (o app precisa do Android 9 ou mais novo).'
        Info 'Nada foi alterado no projetor.'
        Sair 2
    }
    $backup = Ler-Backup
    $novoBackup = -not $backup.ContainsKey('home')
    if ($novoBackup) {
        # Guarda como o aparelho estava ANTES, para o desinstalar.bat voltar tudo.
        $a11y = Sh settings get secure enabled_accessibility_services
        $backup = [ordered]@{
            home       = (Launcher-Atual)
            a11y       = $(if ($a11y -eq 'null') { '' } else { $a11y })
            a11y_ativo = (Sh settings get secure accessibility_enabled)
            mouse      = $(if (Tem-Mouse) { '1' } else { '0' })
            data       = (Get-Date -Format 'yyyy-MM-dd HH:mm')
        }
    }

    Titulo 'Instalando o app'
    $saida = A -s $script:DEV install -r $APK
    if ($saida -notmatch '(?m)^Success') {
        Write-Host ''
        if ($saida -match 'INSTALL_FAILED_SHARED_USER_INCOMPATIBLE') {
            Erro 'Este projetor nao e compativel com o app.'
            Info 'O app precisa de um firmware (sistema) do fabricante assinado de um jeito'
            Info 'especifico, e o deste aparelho e diferente. Nao existe ajuste que resolva isso'
            Info 'com seguranca.'
        } elseif ($saida -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            Erro 'Ja existe outra versao do app Projetor no aparelho, feita de outra fonte.'
            Info 'Rode o desinstalar.bat primeiro e tente de novo.'
        } elseif ($saida -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
            Erro 'O projetor ja tem uma versao mais nova do app.'
        } elseif ($saida -match 'INSUFFICIENT_STORAGE') {
            Erro 'Falta espaco no projetor. Apague alguns apps e tente de novo.'
        } else {
            Erro 'A instalacao falhou. Mensagem do aparelho:'
            Info $saida
        }
        Info 'Nada foi alterado no projetor.'
        Sair 2
    }
    Ok 'App instalado.'
    if ($novoBackup) {
        Gravar-Backup $backup
        Ok ('Configuracao original guardada (tela inicial: ' + $backup.home + ').')
    }

    $r = Sh cmd package set-home-activity $HOME_APP
    if ($r -match 'Success') { Ok 'Definido como tela inicial.' }
    else { Aviso ('Nao consegui definir como tela inicial: ' + $r); Info 'Aperte a tecla Inicio do controle e escolha "Projetor".' }

    Sh am start -a android.intent.action.MAIN -c android.intent.category.HOME | Out-Null
    $ip = ($script:DEV -split ':')[0]
    Titulo 'Pronto'
    Info 'A nova tela inicial ja deve estar no projetor.'
    if ($script:DEV -match ':') { Info ('No celular, no mesmo Wi-Fi, abra:  http://' + $ip + ':8080') }
    Info 'O PIN de 6 digitos aparece no rodape da tela inicial do projetor.'
    Desligar-Adb 'Para desinstalar no futuro, religue em "Ajustes > Acesso pelo PC (ADB)" na pagina do controle no celular.'
}

# ---------------------------------------------------------------- desinstalar

function Desinstalar {
    Info 'Se o projetor nao for encontrado, ligue o acesso ADB antes:'
    Info '  no celular: pagina do controle > Ajustes > "Acesso pelo PC (ADB)".'
    Escolher-Projetor
    if (-not (Instalado)) {
        Aviso 'O app Projetor nao esta instalado neste aparelho.'
        if (-not (SimNao 'Mesmo assim, restaurar tela inicial e modo mouse do fabricante?')) { Sair 0 }
    }
    $b = Ler-Backup
    # ($home e variavel reservada do PowerShell; por isso $inicial)
    $inicial = $b['home']
    if (-not $inicial) {
        $l = @(Launchers)
        if ($l.Count -eq 1) { $inicial = $l[0] }
        elseif ($l.Count -gt 1) {
            Write-Host ''
            Info 'Qual era a tela inicial original?'
            for ($i = 0; $i -lt $l.Count; $i++) { Write-Host ('  [' + ($i + 1) + '] ' + $l[$i]) }
            $n = 0
            while (-not ([int]::TryParse((Read-Host 'Escolha'), [ref]$n) -and $n -ge 1 -and $n -le $l.Count)) { }
            $inicial = $l[$n - 1]
        }
    }
    Titulo 'Desinstalando'
    if ($inicial) {
        $r = Sh cmd package set-home-activity $inicial
        if ($r -match 'Success') { Ok ('Tela inicial original de volta: ' + $inicial) }
        else { Aviso ('Nao consegui definir a tela inicial original: ' + $r) }
    } else {
        Aviso 'Nao achei outra tela inicial; o Android vai perguntar qual usar.'
    }

    if (Instalado) {
        $r = A -s $script:DEV uninstall $PKG
        if ($r -match 'Success') { Ok 'App Projetor removido.' } else { Erro ('Falhou ao remover: ' + $r); Sair 1 }
    }

    if (Tem-Mouse) { Sh pm default-state $MOUSE_SVC | Out-Null }
    if ($b.ContainsKey('a11y')) {
        if ($b['a11y']) { Sh settings put secure enabled_accessibility_services ("'" + $b['a11y'] + "'") | Out-Null }
        else { Sh settings delete secure enabled_accessibility_services | Out-Null }
        $a11yAtivo = if ($b.ContainsKey('a11y_ativo')) { $b['a11y_ativo'] } else { '1' }
        Sh settings put secure accessibility_enabled $a11yAtivo | Out-Null
        Ok 'Acessibilidade original restaurada.'
    } elseif (Tem-Mouse) {
        Sh settings put secure enabled_accessibility_services ("'" + $MOUSE_SVC + "'") | Out-Null
        Sh settings put secure accessibility_enabled 1 | Out-Null
        Ok 'Modo mouse do fabricante religado.'
    }

    Sh rm -f $BACKUP_DEV | Out-Null
    $local = Join-Path $DADOS ('backups\' + ($script:DEV -replace '[^\w.-]', '_') + '.txt')
    Remove-Item -Force $local -ErrorAction SilentlyContinue
    Sh am start -a android.intent.action.MAIN -c android.intent.category.HOME | Out-Null

    Titulo 'Pronto'
    Info 'O projetor voltou ao original. Reiniciar desfaz tambem o ajuste de audio da Netflix.'
    $reiniciar = SimNao 'Reiniciar o projetor agora?'
    Desligar-Adb 'Para ligar de novo: Ajustes do projetor > Opcoes do desenvolvedor > Depuracao USB.'
    if ($reiniciar) {
        if ((Estado $script:DEV) -eq 'device') { A -s $script:DEV reboot | Out-Null; Ok 'Reiniciando.' }
        else { Info 'Desligue e ligue o projetor para reiniciar.' }
    }
}

# ---------------------------------------------------------------- inicio

Write-Host 'Kit do app Projetor' -ForegroundColor Cyan
$script:ADB = Achar-Adb
if (-not $script:ADB) { $script:ADB = Instalar-Adb }
A start-server | Out-Null
if ($Acao -eq 'instalar') { Instalar } else { Desinstalar }
Sair 0
