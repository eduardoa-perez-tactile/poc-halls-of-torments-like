'use strict';
(() => {
  const byId = (id) => document.getElementById(id);
  const form = byId('join-form'), joinButton = byId('join');
  const connected = byId('connected'), title = byId('title'), message = byId('message');
  const player = byId('player'), party = byId('party'), network = byId('network-status');
  let socket, heartbeat, watchdog, retry, attempts = 0, stopped = false, joined = false, room = '';
  let lastSeen = Date.now();
  let memoryToken = '';

  function token(value) {
    const key = `couch-crypt:${location.host}:${room}`;
    try {
      if (value !== undefined) { localStorage.setItem(key, value); memoryToken = value; }
      return localStorage.getItem(key) || memoryToken;
    } catch { if (value !== undefined) memoryToken = value; return memoryToken; }
  }
  function stopTimers() { clearInterval(heartbeat); clearInterval(watchdog); clearTimeout(retry); }
  function showForm(text, label = 'Try again') {
    connected.hidden = true; form.hidden = false; joinButton.disabled = false;
    joinButton.textContent = label; message.textContent = text;
    title.textContent = 'Join your party'; network.textContent = 'NOT CONNECTED';
  }
  async function discover() {
    joinButton.disabled = true; stopped = false;
    message.textContent = 'Finding your place in the party…';
    network.textContent = 'CONNECTING';
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), 6000);
    try {
      const response = await fetch('/session', { cache: 'no-store', signal: abort.signal });
      if (!response.ok) throw new Error('Host unavailable');
      const session = await response.json();
      if (!/^[A-Z0-9]{6}$/.test(session.room)) throw new Error('Invalid session');
      room = session.room; attempts = 0; connect();
    } catch {
      stopped = true;
      showForm('Cannot reach the TV. Check that your phone is on the same Wi-Fi.');
    } finally { clearTimeout(timeout); }
  }
  function connect() {
    stopTimers(); stopped = false; joined = false;
    if (socket) { socket.onclose = null; socket.close(); }
    joinButton.disabled = true; message.textContent = 'Finding your place in the party…';
    network.textContent = 'CONNECTING';
    const url = new URL('/socket', location.href);
    url.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    url.searchParams.set('room', room);
    if (token()) url.searchParams.set('token', token());
    const current = socket = new WebSocket(url);
    lastSeen = Date.now();
    watchdog = setInterval(() => { if (Date.now() - lastSeen > 10000) current.close(); }, 2000);
    current.onmessage = (event) => {
      if (current !== socket) return;
      lastSeen = Date.now();
      let data;
      try { data = JSON.parse(event.data); } catch { return; }
      if (data.type === 'joined') {
        joined = true; attempts = 0; token(data.token);
        form.hidden = true; connected.hidden = false;
        player.textContent = `PLAYER ${data.player}`;
        document.documentElement.style.setProperty('--player', data.player === 1 ? '#d4b47c' : '#9bc3bb');
        title.textContent = 'You have a place';
        message.textContent = 'Connected. Look at the TV while your host gathers the party.';
        network.textContent = 'CONNECTED';
        heartbeat = setInterval(() => { if (current.readyState === WebSocket.OPEN) current.send('{"type":"ping"}'); }, 2000);
      } else if (data.type === 'status') {
        party.textContent = data.connected === 2 ? 'Both souls are here' : data.phase === 'invite' ? 'Waiting for your companion' : 'Ready for a solo descent';
        const descriptions = {
          lobby: 'Connected. Your host can start solo or gather a companion.',
          session: 'Your controller is connected. Look at the TV.',
          paused: 'The host has paused. Stay connected to keep your place.',
          invite: 'The host is inviting a companion. Stay connected to keep your place.'
        };
        message.textContent = data.hostActive ? (descriptions[data.phase] || descriptions.lobby) : 'The host has stepped away. Stay here to keep your place.';
      } else if (data.type === 'removed' || data.type === 'error') {
        stopped = true; stopTimers();
        const explanations = {
          lobby_full: 'Both places are taken or reserved for reconnecting players. Ask the host to free a place.',
          already_connected: 'This phone already has a connected tab. Use that tab, or close it before reconnecting.',
          invalid_room: 'The host has restarted. Tap Try again to rejoin.',
          invite_required: 'Ask the host to open Invite Player 2 from the pause menu, then try again.',
          removed: 'The host removed your controller. You can join again if a place is free.'
        };
        if (data.type === 'removed') token('');
        showForm(explanations[data.reason || data.type] || 'Unable to join this party. Scan the TV again.');
        current.close();
      }
    };
    current.onerror = () => { /* close handles retry without duplicate status messages. */ };
    current.onclose = () => {
      if (current !== socket) return;
      stopTimers();
      if (stopped) return;
      network.textContent = 'RECONNECTING';
      message.textContent = joined ? 'Connection interrupted. Reclaiming your place…' : 'Cannot reach the TV. Check that both devices use the same Wi-Fi.';
      if (attempts++ < 12) retry = setTimeout(connect, Math.min(4000, 750 + attempts * 250));
      else { stopped = true; showForm('The TV is not reachable. Check Wi-Fi and scan the QR code again.'); }
    };
  }
  form.addEventListener('submit', (event) => {
    event.preventDefault(); discover();
  });
  window.addEventListener('online', () => { if (!stopped && socket?.readyState !== WebSocket.OPEN && room) connect(); });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && room && !stopped && socket?.readyState !== WebSocket.OPEN) connect();
  });
  window.addEventListener('pagehide', () => { stopped = true; stopTimers(); socket?.close(); });
  window.addEventListener('pageshow', (event) => { if (event.persisted) discover(); });
  discover();
})();
