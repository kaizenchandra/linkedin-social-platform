// Fetch SSE preserves Authorization headers; tokens are never URLs or persistent storage.
const api = 'http://localhost:8080';
const status = document.querySelector('#status'), output = document.querySelector('#events');
let token = '', owner = '', generation = 0, controllers = [];
const streams = new Map(), lines = [];

function log(text) {
    lines.push(text);
    if (lines.length > 100) lines.shift();
    output.textContent = lines.join('\n');
}

function clear() {
    generation++;
    controllers.forEach(c => c.abort());
    controllers = [];
    token = '';
    owner = '';
    streams.clear();
    lines.length = 0;
    output.textContent = '';
    document.querySelector('#counts').textContent = '';
}

async function request(path, signal) {
    const response = await fetch(api + path, {headers: {Authorization: `Bearer ${token}`}, signal, cache: 'no-store'});
    if (!response.ok) throw Object.assign(new Error(`HTTP ${response.status}`), {status: response.status});
    return response.json();
}

function accountKey(claims) {
    if (!claims.iss || !claims.sub || !claims.exp) throw new Error('Required identity claims missing');
    return JSON.stringify([claims.iss, claims.sub]);
}

function counts() {
    document.querySelector('#counts').textContent = [...streams].map(([name, state]) => `${name}: ${state.unread} unread`).join(' | ');
}

async function sync(name, state, signal) {
    const snapshot = await request(`/api/v1/${name}/sync`, signal);
    // This boundary predates the state reads. Replaying duplicate invalidations is safe.
    state.cursor = snapshot.cursor;
    state.unread = snapshot.unreadCount;
    state.seen.clear();
    state.versions.clear();
    if (name === 'conversations') {
        let page = snapshot.state, pages = 0;
        while (true) {
            for (const item of page.items) state.versions.set(item.id, item.readVersion);
            if (!page.nextCursor) break;
            if (++pages >= 20) throw Object.assign(new Error('Verification client supports 2,000 conversations; use REST pagination directly for larger accounts.'), {fatal: true});
            page = await request(`/api/v1/conversations?all=true&size=100&cursor=${encodeURIComponent(page.nextCursor)}`, signal);
        }
    }
    counts();
    log(`${name}: synchronized authoritative state`);
}

async function event(name, state, frame, signal) {
    const fields = {};
    for (const line of frame.split('\n')) {
        const colon = line.indexOf(':');
        if (colon <= 0) continue;
        const key = line.slice(0, colon), value = line.slice(colon + 1).trimStart();
        fields[key] = key === 'data' && fields[key] ? fields[key] + '\n' + value : value;
    }
    if (!fields.data) return;
    const value = JSON.parse(fields.data);
    if (value.schemaVersion !== 1 || fields.id !== value.cursor) throw Object.assign(new Error('Unsupported stream event'), {fatal: true});
    if (!state.seen.has(value.eventId)) {
        if (name === 'conversations') {
            const current = await request(`/api/v1/conversations/${encodeURIComponent(value.resourceId)}`, signal);
            const previous = state.versions.get(current.id) ?? -1;
            if (current.readVersion >= previous) state.versions.set(current.id, current.readVersion);
            if (state.versions.size > 2000) state.versions.delete(state.versions.keys().next().value);
        }
        // Refetch counts; never increment or decrement them from replayed invalidations.
        state.unread = (await request(`/api/v1/${name}/sync`, signal)).unreadCount;
        state.seen.add(value.eventId);
        if (state.seen.size > 1000) state.seen.delete(state.seen.values().next().value);
        log(`${name}: ${value.eventType} ${value.eventId}`);
        counts();
    }
    state.cursor = value.cursor;
}

async function run(name, gen) {
    const state = streams.get(name);
    let attempt = 0;
    while (generation === gen) {
        const controller = new AbortController();
        controllers.push(controller);
        try {
            if (!state.cursor) await sync(name, state, controller.signal);
            const response = await fetch(`${api}/api/v1/${name}/stream`, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Last-Event-ID': state.cursor
                }, signal: controller.signal, cache: 'no-store'
            });
            if (!response.ok) throw Object.assign(new Error(`HTTP ${response.status}`), {status: response.status});
            if (!response.headers.get('content-type')?.startsWith('text/event-stream')) throw new Error('Expected event stream');
            status.textContent = 'Connected; REST remains authoritative';
            const reader = response.body.getReader(), decoder = new TextDecoder();
            let buffer = '';
            while (generation === gen) {
                const chunk = await reader.read();
                if (chunk.done) break;
                buffer += decoder.decode(chunk.value, {stream: true}).replace(/\r/g, '');
                if (buffer.length > 65536) throw new Error('Frame limit exceeded');
                let boundary;
                while ((boundary = buffer.indexOf('\n\n')) >= 0) {
                    const frame = buffer.slice(0, boundary);
                    buffer = buffer.slice(boundary + 2);
                    await event(name, state, frame, controller.signal);
                    attempt = 0;
                }
            }
        } catch (error) {
            if (generation !== gen || error.name === 'AbortError') return;
            if (error.fatal) {
                status.textContent = error.message;
                return;
            }
            if (error.status === 401 || error.status === 403) {
                status.textContent = 'Authentication ended. Supply a refreshed token to resume.';
                return;
            }
            if (error.status === 410 || error.status === 409) {
                state.cursor = null;
                log(`${name}: reset required; synchronizing before reconnect`);
            } else log(`${name}: connection interrupted; retrying without changing read state`);
        } finally {
            controller.abort();
            controllers = controllers.filter(c => c !== controller);
        }
        await new Promise(resolve => setTimeout(resolve, Math.min(30000, 500 * 2 ** Math.min(attempt++, 6) * (.5 + Math.random()))));
    }
}

document.querySelector('#connect').onclick = () => {
    const supplied = document.querySelector('#token').value.trim();
    document.querySelector('#token').value = '';
    try {
        const claims = JSON.parse(atob(supplied.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        const identity = accountKey(claims);
        if (identity !== owner) clear(); else {
            generation++;
            controllers.forEach(c => c.abort());
            controllers = [];
        }
        owner = identity;
        token = supplied;
        const gen = generation;
        for (const name of ['conversations', 'notifications']) {
            if (!streams.has(name)) streams.set(name, {cursor: null, seen: new Set(), versions: new Map(), unread: 0});
            run(name, gen);
        }
    } catch {
        clear();
        status.textContent = 'Enter a valid access token. The services verify its signature and claims.';
    }
};
document.querySelector('#stop').onclick = () => {
    clear();
    status.textContent = 'Disconnected; account state cleared';
};
