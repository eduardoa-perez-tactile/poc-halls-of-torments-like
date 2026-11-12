'use strict';
(() => {
  const byId = (id) => document.getElementById(id);
  const form = byId('join-form'), roomField = byId('room'), joinButton = byId('join');
  const connected = byId('connected'), title = byId('title'), message = byId('message');
  const player = byId('player'), party = byId('party'), network = byId('network-status');
  let socket, heartbeat, watchdog, retry, attempts = 0, stopped = false, joined = false, room = '';
  let lastSeen = Date.now();
  let memoryToken = '';
  const params = new URLSearchParams(location.search);
  const roomParam = (params.get('room') || '').toUpperCase();
  if (/^[A-Z0-9]{6}$/.test(roomParam)) roomField.value = roomParam;

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
        network.textContent = `CONNECTED · ROOM ${room}`;
        heartbeat = setInterval(() => { if (current.readyState === WebSocket.OPEN) current.send('{"type":"ping"}'); }, 2000);
      } else if (data.type === 'status') {
        party.textContent = data.connected === 2 ? 'Both souls are here' : 'Waiting for your companion';
        message.textContent = data.hostActive ? 'Connected. Your host controls the lobby with the TV remote.' : 'The host has stepped away. Stay here to keep your place.';
      } else if (data.type === 'removed' || data.type === 'error') {
        stopped = true; stopTimers();
        const explanations = {
          lobby_full: 'Both places are taken or reserved for reconnecting players. Ask the host to free a place.',
          already_connected: 'This phone already has a connected tab. Use that tab, or close it before reconnecting.',
          invalid_room: 'This room is no longer available. Scan the current QR code on the TV.',
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
    event.preventDefault(); room = roomField.value.trim().toUpperCase();
    if (!/^[A-Z0-9]{6}$/.test(room)) return;
    attempts = 0; connect();
  });
  window.addEventListener('online', () => { if (!stopped && socket?.readyState !== WebSocket.OPEN && room) connect(); });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && room && !stopped && socket?.readyState !== WebSocket.OPEN) connect();
  });
  window.addEventListener('pagehide', () => { stopped = true; stopTimers(); socket?.close(); });
  window.addEventListener('pageshow', (event) => { if (event.persisted && room) { stopped = false; connect(); } });
  if (roomField.value) { room = roomField.value; connect(); }
})();
