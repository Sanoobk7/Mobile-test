// Quick end-to-end check of the relay: node test-relay.mjs [baseUrl]
import WebSocket from "ws";
const base = (process.argv[2] || "ws://127.0.0.1:8787").replace(/^http/, "ws");
const wait = (ws, pred) => new Promise((res, rej) => {
  const t = setTimeout(() => rej(new Error("timeout")), 5000);
  ws.on("message", (d) => { const o = JSON.parse(d); if (pred(o)) { clearTimeout(t); res(o); } });
});
const host = new WebSocket(base + "/host");
const room = await wait(host, (o) => o.t === "room");
console.log("room code", room.code);
const guest = new WebSocket(base + "/join/" + room.code.toLowerCase());
const peer = await wait(host, (o) => o.t === "peer");
if (guest.readyState !== 1) await new Promise((r) => guest.once("open", r));
guest.send(JSON.stringify({ t: "hello", name: "Khách" }));
const from = await wait(host, (o) => o.t === "from");
console.log("host got", JSON.stringify(from));
host.send(JSON.stringify({ t: "to", id: peer.id, m: { t: "lobby", ok: true } }));
console.log("guest got", JSON.stringify(await wait(guest, (o) => o.t === "lobby")));
const bad = new WebSocket(base + "/join/ZZZZZZ");
console.log("bad code ->", JSON.stringify(await wait(bad, (o) => o.t === "err")));
const errP = wait(guest, (o) => o.t === "err");
host.close();
console.log("after host left ->", JSON.stringify(await errP));
console.log("RELAY OK");
process.exit(0);
