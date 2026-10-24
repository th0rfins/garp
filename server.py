# GARP backend — FastAPI + SQLite, serves frontend/ on the same port.
# Polls Grab share-my-ride API every 5s per active order (backoff on 429).
import asyncio, json, os, re, sqlite3, time
from contextlib import asynccontextmanager
from pathlib import Path

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

ROOT = Path(__file__).resolve().parent
DB = Path(os.environ.get("GARP_DB", ROOT / "garp.db"))
for p in DB.parents:
    if p != Path("/"):
        p.mkdir(parents=True, exist_ok=True)
FRONTEND = ROOT / "frontend"
POLL_INTERVAL = 5
UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

_orders: dict[str, dict] = {}          # id -> order snapshot (latest)
_watchers: set = set()                 # set of order ids with SSE clients
_sub_cache: dict[str, str] = {}        # short_code -> share token

def _db():
    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    return con

DB_INIT = """
CREATE TABLE IF NOT EXISTS orders (
  id TEXT PRIMARY KEY, token TEXT, short_code TEXT, link TEXT,
  merchant TEXT, booking_code TEXT, status TEXT,
  eta_seconds INTEGER, driver_name TEXT, driver_vehicle TEXT, driver_plate TEXT,
  driver_lat REAL, driver_lng REAL, dest_lat REAL, dest_lng REAL,
  dest_text TEXT, error TEXT, poll_failures INTEGER DEFAULT 0,
  created_at TEXT, updated_at TEXT
);
CREATE TABLE IF NOT EXISTS events (
  id INTEGER PRIMARY KEY AUTOINCREMENT, order_id TEXT, ts TEXT, kind TEXT, text TEXT
);
"""

def _now():
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime()) + "Z"

class GrabGone(Exception):
    pass

# ---------------- Grab resolution & polling ----------------

async def resolve_short_link(client: httpx.AsyncClient, url: str) -> tuple[str, str]:
    """Return (short_code, share_token)."""
    m = re.search(r"app\.grab\.com/s/([A-Za-z0-9]+)", url)
    if m:
        code = m.group(1)
        if code in _sub_cache:
            return code, _sub_cache[code]
        token = await _resolve_token(client, f"https://app.grab.com/s/{code}")
        _sub_cache[code] = token
        return code, token
    m = re.search(r"sharelocation\.grab\.com/o/([A-Za-z0-9]+)", url)
    if m:
        return "", m.group(1)
    raise HTTPException(400, "URL bukan share link Grab yang valid")

def _extract_token(text: str) -> str | None:
    m = re.search(r"/o/([A-Za-z0-9]{10,})", text)
    if m:
        return m.group(1)
    m = re.search(r"shareOrderLink=([A-Za-z0-9]{10,})", text)
    if m:
        return m.group(1)
    return None

async def _resolve_token(client: httpx.AsyncClient, url: str) -> str:
    # Method 1: GET no-redirect -> Location header
    try:
        r = await client.get(url, follow_redirects=False, timeout=10)
        loc = r.headers.get("location", "")
        tok = _extract_token(loc)
        if tok:
            return tok
    except httpx.HTTPError:
        pass
    # Method 2: GET with redirect, scan final URL then HTML body
    try:
        r = await client.get(url, follow_redirects=True, timeout=10)
        tok = _extract_token(str(r.url)) or _extract_token(r.text[:6000])
        if tok:
            return tok
    except httpx.HTTPError:
        pass
    # Method 3: HEAD no-redirect
    try:
        r = await client.head(url, follow_redirects=False, timeout=10)
        tok = _extract_token(r.headers.get("location", ""))
        if tok:
            return tok
    except httpx.HTTPError:
        pass
    raise HTTPException(400, "Gagal resolve token dari short link (mungkin sudah expired)")

def _headers(token: str) -> dict:
    return {
        "Accept": "application/json",
        "Origin": "https://sharelocation.grab.com",
        "Referer": f"https://sharelocation.grab.com/o/{token}",
        "User-Agent": UA,
    }

TERMINAL = ("COMPLETE", "CANCEL", "DELIVER", "EXPIRED", "TERMINAT")

def map_state(d: dict) -> dict:
    """Map Grab bookingdetails payload to our order snapshot fields."""
    booking = d.get("booking") or {}
    driver = d.get("driver") or {}
    route = d.get("route") or {}
    add = d.get("additionalOrderDetail") or {}
    state = booking.get("bookingState") or ""
    session = d.get("sessionStatus") or ""
    loc = (driver.get("location") or {})
    drop = (booking.get("dropOff") or {})
    droploc = drop.get("location") or drop.get("latlng") or {}
    etas = route.get("ETAs") or []
    if session != "ACTIVE" or add.get("completeFlag") == 1 or any(t in state.upper() for t in TERMINAL):
        if "CANCEL" in state.upper() or "EXPIRED" in state.upper() or "TERMINAT" in session.upper():
            status = "cancelled"
        else:
            status = "completed"
    elif "ON_THE_WAY" in state.upper() or "DELIVER" in state.upper() or driver.get("name"):
        status = "ontheway"
    else:
        status = "prepare"
    return {
        "merchant": (booking.get("pickup") or {}).get("keywords") or "",
        "booking_code": booking.get("bookingCode") or "",
        "status": status,
        "eta_seconds": etas[0] if etas else None,
        "driver_name": driver.get("name") or "",
        "driver_vehicle": driver.get("vehicleModel") or "",
        "driver_plate": driver.get("vehiclePlateNumber") or "",
        "driver_lat": loc.get("latitude"),
        "driver_lng": loc.get("longitude"),
        "dest_lat": droploc.get("latitude") or droploc.get("lat"),
        "dest_lng": droploc.get("longitude") or droploc.get("lng"),
        "dest_text": drop.get("keywords") or "",
        "session": session,
    }

async def poll_once(client: httpx.AsyncClient, oid: str) -> dict | None:
    """Poll one order; returns updated snapshot or None if unchanged/not ready. Raises GrabGone when terminal."""
    o = _orders[oid]
    token = o["token"]
    async def attempt(method: str, **kw):
        return await client.request(method, f"https://api.grab.com/api/v1/safety/sharemyride/{token}/bookingdetails",
                                    params={"fullData": "true"}, headers=_headers(token), timeout=12, **kw)
    r = None
    try:
        r = await attempt("GET")
    except httpx.HTTPError:
        o["poll_failures"] = (o.get("poll_failures") or 0) + 1
        if o["poll_failures"] >= 30:
            raise GrabGone()
        return None
    if r.status_code == 429:
        retry = r.headers.get("retry-after")
        o["poll_backoff"] = float(retry) if retry and retry.isdigit() else (o.get("poll_backoff") or POLL_INTERVAL) * 2
        return None
    o["poll_backoff"] = POLL_INTERVAL
    if r.status_code == 404:
        raise GrabGone()
    if r.status_code >= 500:
        o["poll_failures"] = (o.get("poll_failures") or 0) + 1
        if o["poll_failures"] >= 30:
            raise GrabGone()
        return None
    try:
        data = r.json()
    except Exception:
        o["poll_failures"] = (o.get("poll_failures") or 0) + 1
        return None
    if not data or data.get("pass") is False:
        raise GrabGone()
    snap = map_state(data)
    snap["error"] = None
    snap["poll_failures"] = 0
    changed = any(snap.get(k) != o.get(k) for k in ("status", "eta_seconds", "driver_lat", "driver_lng", "merchant", "booking_code"))
    o.update(snap)
    o["updated_at"] = _now()
    _save_order(o)
    if changed or oid in _watchers:
        await _emit(oid)
    if o["status"] in ("completed", "cancelled"):
        raise GrabGone()
    return o

# ---------------- persistence ----------------

def _save_order(o: dict):
    con = _db()
    con.execute("""INSERT INTO orders (id, token, short_code, link, merchant, booking_code, status, eta_seconds,
        driver_name, driver_vehicle, driver_plate, driver_lat, driver_lng, dest_lat, dest_lng, dest_text,
        error, poll_failures, created_at, updated_at)
        VALUES (:id,:token,:short_code,:link,:merchant,:booking_code,:status,:eta_seconds,
        :driver_name,:driver_vehicle,:driver_plate,:driver_lat,:driver_lng,:dest_lat,:dest_lng,:dest_text,
        :error,:poll_failures,:created_at,:updated_at)
        ON CONFLICT(id) DO UPDATE SET merchant=:merchant, booking_code=:booking_code, status=:status,
        eta_seconds=:eta_seconds, driver_name=:driver_name, driver_vehicle=:driver_vehicle, driver_plate=:driver_plate,
        driver_lat=:driver_lat, driver_lng=:driver_lng, dest_lat=:dest_lat, dest_lng=:dest_lng, dest_text=:dest_text,
        error=:error, poll_failures=:poll_failures, updated_at=:updated_at""", o)
    con.commit()
    con.close()

def _load_orders():
    con = _db()
    for row in con.execute("SELECT * FROM orders"):
        _orders[row["id"]] = dict(row)
    con.close()

def _add_event(oid: str, kind: str, text: str):
    con = _db()
    con.execute("INSERT INTO events (order_id, ts, kind, text) VALUES (?,?,?,?)", (oid, _now(), kind, text))
    con.commit(); con.close()

# ---------------- background poller ----------------

async def poller():
    client = httpx.AsyncClient()
    while True:
        active = [oid for oid, o in _orders.items() if o.get("status") in ("prepare", "ontheway")]
        for oid in active:
            try:
                await poll_once(client, oid)
            except GrabGone:
                o = _orders.get(oid)
                if o and o["status"] not in ("completed", "cancelled"):
                    o["status"] = "completed"
                    _save_order(o)
                await _emit(oid)
                _add_event(oid, "end", "Tracking selesai")
            except HTTPException as e:
                o = _orders.get(oid)
                if o:
                    o["error"] = str(e.detail)
                    _save_order(o)
                    await _emit(oid)
        asleep = POLL_INTERVAL
        for oid in active:
            b = _orders.get(oid, {}).get("poll_backoff")
            if b and b > asleep:
                asleep = b
        await asyncio.sleep(min(max(asleep, POLL_INTERVAL), 60))

# ---------------- SSE ----------------

_queues: dict[str, set] = {}

def _register(oid: str) -> asyncio.Queue:
    q = asyncio.Queue()
    _queues.setdefault(oid, set()).add(q)
    _watchers.add(oid)
    return q

def _unregister(oid: str, q: asyncio.Queue):
    _queues.get(oid, set()).discard(q)
    if not _queues.get(oid):
        _watchers.discard(oid)

async def _emit(oid: str):
    for q in _queues.get(oid, set()).copy():
        await q.put("update")

def _snapshot(o: dict) -> dict:
    keys = ("id", "merchant", "booking_code", "status", "eta_seconds", "driver_name",
            "driver_vehicle", "driver_plate", "driver_lat", "driver_lng", "dest_lat",
            "dest_lng", "dest_text", "link", "error", "created_at", "updated_at")
    return {k: o.get(k) for k in keys}

# ---------------- app ----------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    con = _db(); con.executescript(DB_INIT); con.close()
    _load_orders()
    task = asyncio.create_task(poller())
    yield
    task.cancel()

app = FastAPI(lifespan=lifespan)

class OrderIn(BaseModel):
    link: str

@app.post("/api/orders")
async def add_order(body: OrderIn):
    link = body.link.strip()
    # shared text may contain the URL inside a sentence
    m = re.search(r"https?://\S+", link)
    if m:
        link = m.group(0)
    async with httpx.AsyncClient() as client:
        short_code, token = await resolve_short_link(client, link)
    oid = token[-10:] or f"o{int(time.time())}"
    if oid in _orders:
        return _snapshot(_orders[oid])
    o = {"id": oid, "token": token, "short_code": short_code, "link": link,
         "status": "prepare", "poll_failures": 0, "error": None,
         "created_at": _now(), "updated_at": _now()}
    _orders[oid] = o
    # first fetch to seed data
    try:
        async with httpx.AsyncClient() as client:
            r = await client.get(f"https://api.grab.com/api/v1/safety/sharemyride/{token}/bookingdetails",
                                 params={"fullData": "true"}, headers=_headers(token), timeout=12)
            if r.status_code == 200 and r.json().get("pass"):
                o.update(map_state(r.json()))
                o["updated_at"] = _now()
    except Exception:
        pass
    if o["status"] in ("completed", "cancelled"):
        _save_order(o)
    _save_order(o)
    _add_event(oid, "add", f"Pesanan ditambahkan: {o.get('merchant') or link}")
    await _emit(oid)
    return _snapshot(o)

@app.get("/api/orders")
async def list_orders():
    return [_snapshot(o) for o in sorted(_orders.values(), key=lambda x: x.get("created_at") or "", reverse=True)]

@app.get("/api/orders/{oid}")
async def get_order(oid: str):
    if oid not in _orders:
        raise HTTPException(404, "Order tidak ditemukan")
    return _snapshot(_orders[oid])

@app.delete("/api/orders/{oid}")
async def delete_order(oid: str):
    if oid not in _orders:
        raise HTTPException(404)
    _orders.pop(oid)
    con = _db()
    con.execute("DELETE FROM orders WHERE id=?", (oid,))
    con.execute("DELETE FROM events WHERE order_id=?", (oid,))
    con.commit(); con.close()
    return {"ok": True}

@app.get("/api/stats")
async def stats():
    orders = list(_orders.values())
    total = len(orders)
    active = sum(1 for o in orders if o["status"] in ("prepare", "ontheway"))
    done = sum(1 for o in orders if o["status"] == "completed")
    canc = sum(1 for o in orders if o["status"] == "cancelled")
    # daily counts last 7 days
    daily = {}
    for o in orders:
        day = (o.get("created_at") or "")[:10]
        daily[day] = daily.get(day, 0) + 1
    return {"total": total, "active": active, "completed": done, "cancelled": canc,
            "value": total * 15000, "daily": daily}

@app.get("/api/orders/{oid}/stream")
async def stream(oid: str):
    if oid not in _orders:
        raise HTTPException(404)
    q = _register(oid)

    async def gen():
        try:
            yield f"data: {json.dumps(_snapshot(_orders[oid]))}\n\n"
            while True:
                try:
                    await asyncio.wait_for(q.get(), timeout=25)
                except asyncio.TimeoutError:
                    yield ": keepalive\n\n"
                    continue
                if oid not in _orders:
                    break
                yield f"data: {json.dumps(_snapshot(_orders[oid]))}\n\n"
        finally:
            _unregister(oid, q)

    return StreamingResponse(gen(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

# ---- static frontend (same port) ----
@app.get("/")
async def index():
    return FileResponse(FRONTEND / "index.html")

app.mount("/", StaticFiles(directory=FRONTEND), name="static")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
