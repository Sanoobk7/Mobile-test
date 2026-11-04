// Chaos Party relay: lets phones on different networks play using a room code.
// The host's phone still runs the game; this server only forwards messages.
//
//   GET /host        (WebSocket) -> creates a room, first message: {"t":"room","code":"K7XQ2P"}
//   GET /join/CODE   (WebSocket) -> joins a room; messages go to/from the host unchanged
//   GET /            -> health check
//
// Host <-> relay messages:
//   relay -> host: {"t":"peer","id":n} | {"t":"from","id":n,"m":{...}} | {"t":"left","id":n}
//   host -> relay: {"t":"to","id":n,"m":{...}} | {"t":"kick","id":n}

const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0/I/1 to avoid confusion
const CODE_LEN = 6;
const MAX_GUESTS = 5;

function randomCode() {
  const bytes = new Uint8Array(CODE_LEN);
  crypto.getRandomValues(bytes);
  let s = "";
  for (const b of bytes) s += CODE_CHARS[b % CODE_CHARS.length];
  return s;
}

function cors(body, status = 200) {
  return new Response(body, { status, headers: { "Access-Control-Allow-Origin": "*", "Content-Type": "text/plain; charset=utf-8" } });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);
    if (parts.length === 0) return cors("chaos-party-relay ok");
    if (request.headers.get("Upgrade") !== "websocket") return cors("WebSocket expected", 426);

    if (parts[0] === "host") {
      // Pick a free code; the room object answers 409 when its code is already taken.
      for (let i = 0; i < 8; i++) {
        const code = randomCode();
        const stub = env.ROOMS.get(env.ROOMS.idFromName(code));
        const res = await stub.fetch(`https://room/host?code=${code}`, request);
        if (res.status !== 409) return res;
      }
      return cors("No free room code, try again", 503);
    }
    if (parts[0] === "join" && parts[1]) {
      const code = parts[1].toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, CODE_LEN);
      const stub = env.ROOMS.get(env.ROOMS.idFromName(code));
      return stub.fetch(`https://room/join?code=${code}`, request);
    }
    return cors("Not found", 404);
  },
};

/** One room = one host socket + up to five guest sockets. State lives in memory only. */
export class Room {
  constructor(state) {
    this.state = state;
    this.host = null;
    this.guests = new Map(); // id -> WebSocket
    this.nextId = 1;
    this.code = "";
  }

  async fetch(request) {
    const url = new URL(request.url);
    const code = url.searchParams.get("code") || "";
    if (url.pathname === "/host") {
      if (this.host) return new Response("taken", { status: 409 });
      return this.acceptHost(code);
    }
    if (url.pathname === "/join") return this.acceptGuest();
    return new Response("Not found", { status: 404 });
  }

  acceptHost(code) {
    const [client, server] = Object.values(new WebSocketPair());
    server.accept();
    this.host = server;
    this.code = code;
    this.send(server, { t: "room", code });
    server.addEventListener("message", (ev) => this.onHostMessage(ev.data));
    const close = () => this.closeRoom();
    server.addEventListener("close", close);
    server.addEventListener("error", close);
    return new Response(null, { status: 101, webSocket: client });
  }

  acceptGuest() {
    const [client, server] = Object.values(new WebSocketPair());
    server.accept();
    if (!this.host) {
      this.send(server, { t: "err", msg: "Không tìm thấy phòng với mã này" });
      server.close(4004, "no room");
      return new Response(null, { status: 101, webSocket: client });
    }
    if (this.guests.size >= MAX_GUESTS) {
      this.send(server, { t: "err", msg: "Phòng đã đủ người" });
      server.close(4003, "full");
      return new Response(null, { status: 101, webSocket: client });
    }
    const id = this.nextId++;
    this.guests.set(id, server);
    this.send(this.host, { t: "peer", id });
    server.addEventListener("message", (ev) => {
      let m;
      try { m = JSON.parse(ev.data); } catch { return; }
      if (this.host) this.send(this.host, { t: "from", id, m });
    });
    const gone = () => {
      if (!this.guests.delete(id)) return;
      if (this.host) this.send(this.host, { t: "left", id });
    };
    server.addEventListener("close", gone);
    server.addEventListener("error", gone);
    return new Response(null, { status: 101, webSocket: client });
  }

  onHostMessage(data) {
    let o;
    try { o = JSON.parse(data); } catch { return; }
    const g = this.guests.get(o.id);
    if (!g) return;
    if (o.t === "to") this.send(g, o.m);
    else if (o.t === "kick") {
      this.guests.delete(o.id);
      try { g.close(4000, "kicked"); } catch {}
    }
  }

  closeRoom() {
    this.host = null;
    for (const g of this.guests.values()) {
      this.send(g, { t: "err", msg: "Chủ phòng đã đóng phòng" });
      try { g.close(4001, "host left"); } catch {}
    }
    this.guests.clear();
  }

  send(ws, obj) {
    try { ws.send(JSON.stringify(obj)); } catch {}
  }
}
