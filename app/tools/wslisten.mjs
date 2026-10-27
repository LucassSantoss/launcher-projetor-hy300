// Escuta o que o projetor manda ao celular (mudanca de tela/foco) enquanto envia comandos.
// Uso: node tools/wslisten.mjs PIN "k:right" "k:right" ...   (um comando a cada 1,2 s)
const [pin = '0000', ...msgs] = process.argv.slice(2);
// IP do projetor: variavel PROJETOR_IP (ex.: PROJETOR_IP=192.168.0.20 node tools/wslisten.mjs PIN ...).
const ip = process.env.PROJETOR_IP || '192.168.0.20';
const ws = new WebSocket('ws://' + ip + ':8080/ws?t=' + pin);
const wait = ms => new Promise(r => setTimeout(r, ms));
ws.onmessage = ev => {
  try {
    const s = JSON.parse(ev.data);
    console.log(`<- ${s.nome} [${s.perfil}] foco="${s.foco || ''}"`);
  } catch { console.log('<-', ev.data); }
};
ws.onerror = e => { console.log('erro', e.message || e); process.exit(1); };
ws.onopen = async () => {
  await wait(800);
  for (const m of msgs) { console.log('->', m); ws.send(m); await wait(1200); }
  await wait(1500); ws.close(); process.exit(0);
};
