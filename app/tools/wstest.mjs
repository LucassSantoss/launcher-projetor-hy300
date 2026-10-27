// Envia mensagens ao projetor pelo WebSocket, como a pagina do celular faz.
// Uso: node tools/wstest.mjs PIN "msg1" "msg2" ...   (cada msg pode terminar em "*N" para repetir N vezes)
const [pin = '0000', ...msgs] = process.argv.slice(2);
// IP do projetor: variavel PROJETOR_IP (ex.: PROJETOR_IP=192.168.0.20 node tools/wstest.mjs PIN ...).
const ip = process.env.PROJETOR_IP || '192.168.0.20';
const ws = new WebSocket('ws://' + ip + ':8080/ws?t=' + pin);
const wait = ms => new Promise(r => setTimeout(r, ms));
ws.onerror = e => { console.log('erro', e.message || e); process.exit(1); };
ws.onopen = async () => {
  for (const m of msgs) {
    const star = m.lastIndexOf('*');
    const [msg, times] = star > 0 && /^\d+$/.test(m.slice(star + 1)) ? [m.slice(0, star), +m.slice(star + 1)] : [m, 1];
    for (let i = 0; i < times; i++) { ws.send(msg); await wait(30); }
  }
  await wait(800); ws.close(); console.log('enviado'); process.exit(0);
};
