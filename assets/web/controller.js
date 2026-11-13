'use strict';
(() => {
  const byId = (id) => document.getElementById(id);
  const form = byId('join-form'), joinButton = byId('join');
  const connected = byId('connected'), title = byId('title'), message = byId('message');
  const player = byId('player'), party = byId('party'), network = byId('network-status');
  let socket, heartbeat, watchdog, retry, attempts = 0, stopped = false, joined = false, room = '';
  let lastSeen = Date.now();
  let memoryToken = '';
  const controls = byId('game-controls'), joystick = byId('joystick'), stick = byId('stick'), dodgeButton = byId('dodge');
  const dodgeStatus = byId('dodge-status');
  const upgradeChoices = byId('upgrade-choices'), upgradeLevel = byId('upgrade-level'), upgradeCountdown = byId('upgrade-countdown');
  const upgradeSecondsLabel = byId('upgrade-seconds'), upgradeTimerLabel = byId('upgrade-timer-label');
  const upgradeButtons = [0, 1, 2].map((index) => byId(`upgrade-option-${index}`));
  const keepEquipment = byId('equipment-keep');
  let upgradeOffer = null, upgradeSeconds = 0, upgradeSampleTime = 0, upgradeTicker;
  let choiceKind = 'upgrade';
  let upgradeSelected = -1, upgradePendingSince = 0;
  let phase = 'lobby', hostActive = false, alive = true, foreground = true, sender, sequence = 0;
  let axisX = 0, axisY = 0, dodgeEdge = false, stickPointer = null, dodgePointer = null, stickBounds;
  const keys = new Set();

  function canControl() { return joined && !upgradeOffer && phase === 'session' && hostActive && alive && foreground && !document.hidden && socket?.readyState === WebSocket.OPEN; }
  function sendInput() {
    if (socket?.readyState !== WebSocket.OPEN || !joined) return;
    // Never queue movement behind a stalled network connection. The host expires it in 300ms.
    if (socket.bufferedAmount > 4096) { socket.close(); return; }
    const active = canControl();
    socket.send(JSON.stringify({ type: 'input', x: active ? axisX : 0, y: active ? axisY : 0, dodge: active && dodgeEdge, seq: sequence++ }));
    dodgeEdge = false;
  }
  function paintStick(x, y, radius) { stick.style.transform = `translate(${x * radius}px, ${-y * radius}px)`; }
  function releaseControls(send = true) {
    axisX = axisY = 0; dodgeEdge = false; keys.clear();
    const oldStick = stickPointer, oldDodge = dodgePointer;
    stickPointer = dodgePointer = null;
    if (oldStick !== null && joystick.hasPointerCapture(oldStick)) joystick.releasePointerCapture(oldStick);
    if (oldDodge !== null && dodgeButton.hasPointerCapture(oldDodge)) dodgeButton.releasePointerCapture(oldDodge);
    joystick.classList.remove('engaged'); dodgeButton.classList.remove('pressed'); paintStick(0, 0, 0);
    if (send) sendInput();
  }
  function refreshSender() {
    if (!canControl()) { clearInterval(sender); sender = undefined; releaseControls(); }
    else if (!sender) { sendInput(); sender = setInterval(sendInput, 1000 / 30); }
  }
  function clearUpgrade() {
    const wasVisible = !upgradeChoices.hidden;
    clearInterval(upgradeTicker); upgradeTicker = undefined;
    upgradeOffer = null; upgradeSelected = -1; upgradePendingSince = 0;
    choiceKind = 'upgrade';
    upgradeChoices.hidden = true; document.body.classList.remove('has-upgrade', 'choice-urgent');
    keepEquipment.hidden = true;
    if (wasVisible) releaseControls();
  }
  function canChooseUpgrade() {
    return joined && phase === 'session' && hostActive && foreground && !document.hidden && socket?.readyState === WebSocket.OPEN;
  }
  function remainingUpgradeSeconds() {
    // Interpolation is display-only. Only the host resolves expiry or applies an upgrade.
    const elapsed = phase === 'session' && hostActive ? (performance.now() - upgradeSampleTime) / 1000 : 0;
    return Math.max(0, upgradeSeconds - elapsed);
  }
  function paintUpgrade() {
    if (!upgradeOffer) return;
    const remaining = remainingUpgradeSeconds();
    // A lost/rejected request can be retried after the next authoritative statuses arrive.
    if (upgradeSelected >= 0 && performance.now() - upgradePendingSince >= 1200) {
      upgradeSelected = -1; upgradePendingSince = 0;
    }
    const seconds = Math.ceil(remaining);
    upgradeSecondsLabel.textContent = String(seconds);
    upgradeTimerLabel.textContent = upgradeSelected >= 0 ? 'Applying choice…'
      : !hostActive || phase !== 'session' ? 'Paused'
      : remaining <= 0 ? 'Applying auto-pick…' : 'Seconds to choose';
    upgradeCountdown.setAttribute('aria-label', `${seconds} seconds. ${upgradeTimerLabel.textContent}`);
    upgradeChoices.style.setProperty('--choice-progress', `${remaining / 30 * 100}%`);
    document.body.classList.toggle('choice-urgent', seconds <= 10 && hostActive && phase === 'session');
    const disabled = !canChooseUpgrade() || remaining <= 0 || upgradeSelected >= 0;
    upgradeButtons.forEach((button, index) => {
      button.disabled = disabled;
      button.classList.toggle('selected', index === upgradeSelected);
    });
    keepEquipment.hidden = choiceKind !== 'equipment';
    keepEquipment.disabled = disabled;
    if (foreground && !document.hidden) {
      if (!upgradeTicker) upgradeTicker = setInterval(paintUpgrade, 100);
    } else { clearInterval(upgradeTicker); upgradeTicker = undefined; }
  }
  const fallbackSkills = {
    power: [0, 'OFFENSE'], haste: [1, 'OFFENSE'], fleet: [2, 'MOBILITY'], vitality: [3, 'DEFENSE'],
    evasion: [4, 'MOBILITY'], sweep: [5, 'WEAPON'], multishot: [6, 'WEAPON'], pierce: [7, 'WEAPON'],
    aura: [8, 'OFFENSE'], storm: [9, 'OFFENSE'], magnet: [10, 'UTILITY'], critical: [11, 'OFFENSE']
  };
  const categoryGlyphs = { WEAPON: '⚔︎', OFFENSE: '✦', DEFENSE: '✚', MOBILITY: '➶', UTILITY: '◎' };
  function paintSkillCard(button, option, equipment = false) {
    const fallback = fallbackSkills[option.id];
    const category = Object.hasOwn(categoryGlyphs, option.category) ? option.category : fallback?.[1] || 'OFFENSE';
    const icon = Number.isInteger(option.icon) && option.icon >= 0 && option.icon < (equipment ? 48 : 12)
      ? option.icon : equipment ? undefined : fallback?.[0];
    const illustrated = Number.isInteger(icon);
    button.dataset.category = category;
    button.querySelector('.skill-art').hidden = !illustrated;
    const illustration = button.querySelector('.skill-illustration');
    illustration.style.backgroundImage = equipment ? "url('/art/item-icons.png')" : '';
    illustration.style.backgroundSize = equipment ? '800% 600%' : '';
    if (illustrated) {
      button.style.setProperty('--icon-x', `${icon % (equipment ? 8 : 4) / (equipment ? 7 : 3) * 100}%`);
      button.style.setProperty('--icon-y', `${Math.floor(icon / (equipment ? 8 : 4)) / (equipment ? 5 : 2) * 100}%`);
    }
    button.querySelector('.skill-category').textContent = `${categoryGlyphs[category]} ${category}`;
    button.querySelector('.skill-rank').textContent = equipment && typeof option.slot === 'string'
      ? option.slot.replaceAll('_', ' ') : Number.isSafeInteger(option.rank) && option.rank > 0 ? `RANK ${option.rank}` : '';
    button.querySelector('.skill-title').textContent = option.title.slice(0, 64);
    const hasPreview = typeof option.headline === 'string' && typeof option.before === 'string' && typeof option.after === 'string';
    button.classList.toggle('legacy', !hasPreview);
    button.querySelector('.skill-headline').textContent = hasPreview ? option.headline.slice(0, equipment ? 128 : 40) : option.description.slice(0, 128);
    button.querySelector('.skill-preview').hidden = !hasPreview;
    button.querySelector('.skill-before').textContent = hasPreview ? option.before.slice(0, equipment ? 128 : 40) : '';
    button.querySelector('.skill-after').textContent = hasPreview ? option.after.slice(0, equipment ? 128 : 40) : '';
    button.setAttribute('aria-label', `${option.title}. ${hasPreview ? `${option.headline}. ${option.before} to ${option.after}` : option.description}${equipment ? `. ${option.description}` : Number.isSafeInteger(option.rank) && option.rank > 0 ? `. Rank ${option.rank}` : ''}`);
  }
  function setUpgradeOffer(progression, equipment) {
    const kind = progression?.upgrade ? 'upgrade' : 'equipment';
    const next = kind === 'upgrade' ? progression.upgrade : equipment;
    if (!joined || !['session', 'paused', 'invite'].includes(phase) || !next
      || !Number.isSafeInteger(next.id) || next.id <= 0 || !Number.isFinite(next.secondsLeft)
      || !Array.isArray(next.options) || next.options.length !== 3
      || !next.options.every((option) => typeof option?.title === 'string' && typeof option.description === 'string')) {
      clearUpgrade(); return;
    }
    if (upgradeOffer?.id !== next.id || choiceKind !== kind) {
      upgradeOffer = next; upgradeSelected = -1; upgradePendingSince = 0;
      choiceKind = kind;
      upgradeChoices.querySelector('.upgrade-kicker').textContent = kind === 'equipment' ? 'CHOOSE YOUR EQUIPMENT' : 'CHOOSE YOUR POWER';
      upgradeLevel.textContent = kind === 'equipment' ? 'EQUIPMENT' : `LEVEL ${Number.isSafeInteger(next.level) ? next.level : ''}`;
      upgradeButtons.forEach((button, index) => paintSkillCard(button, next.options[index], kind === 'equipment'));
      upgradeChoices.hidden = false; document.body.classList.add('has-upgrade');
      upgradeChoices.querySelector('.upgrade-options').scrollTop = 0;
      // Combat continues, but either personal choice is a neutral-input phone mode.
      releaseControls();
    }
    upgradeSeconds = Math.max(0, Math.min(30, next.secondsLeft));
    upgradeSampleTime = performance.now();
    paintUpgrade();
  }
  upgradeButtons.forEach((button, index) => button.addEventListener('click', () => {
    if (!upgradeOffer || !canChooseUpgrade() || upgradeSelected >= 0 || remainingUpgradeSeconds() <= 0) return;
    upgradeSelected = index; upgradePendingSince = performance.now();
    socket.send(JSON.stringify({ type: choiceKind, offerId: upgradeOffer.id, option: index }));
    paintUpgrade();
  }));
  keepEquipment.addEventListener('click', () => {
    if (!upgradeOffer || choiceKind !== 'equipment' || !canChooseUpgrade()
      || upgradeSelected >= 0 || remainingUpgradeSeconds() <= 0) return;
    upgradeSelected = 3; upgradePendingSince = performance.now();
    socket.send(JSON.stringify({ type: 'equipment', offerId: upgradeOffer.id, option: -1 }));
    paintUpgrade();
  });
  function setControlScreen(data) {
    const wasSession = phase === 'session' && hostActive;
    phase = data.phase; hostActive = data.hostActive; alive = data.alive !== false;
    const activeSession = phase === 'session' && hostActive;
    title.textContent = data.phase === 'paused' || data.phase === 'invite' ? 'Paused' : data.phase === 'results' ? 'Round complete' : 'You have a place';
    if (wasSession !== activeSession) releaseControls();
    setUpgradeOffer(data.progression, data.equipment);
    const show = activeSession || upgradeOffer !== null;
    controls.hidden = !show; document.body.classList.toggle('playing', show);
    document.querySelector('.touch-controls').inert = upgradeOffer !== null;
    const cooldown = Number.isFinite(data.dodgeCooldown) ? Math.max(0, data.dodgeCooldown) : 0;
    dodgeStatus.textContent = alive ? (cooldown > 0.05 ? `${cooldown.toFixed(1)}s` : 'READY') : 'FALLEN';
    dodgeButton.disabled = !!upgradeOffer || !alive || cooldown > 0.05;
    refreshSender();
  }
  function movePointer(event) {
    if (event.pointerId !== stickPointer || !canControl()) return;
    const radius = stickBounds.width * .34;
    let x = (event.clientX - stickBounds.left - stickBounds.width / 2) / radius;
    let y = (stickBounds.top + stickBounds.height / 2 - event.clientY) / radius;
    const length = Math.hypot(x, y);
    if (length > 1) { x /= length; y /= length; }
    axisX = x; axisY = y; paintStick(x, y, radius); event.preventDefault();
  }
  joystick.addEventListener('pointerdown', (event) => {
    if (!canControl() || stickPointer !== null) return;
    stickPointer = event.pointerId; stickBounds = joystick.getBoundingClientRect();
    joystick.setPointerCapture(event.pointerId); joystick.classList.add('engaged'); movePointer(event); sendInput();
  });
  joystick.addEventListener('pointermove', movePointer);
  function endStick(event) {
    if (event.pointerId !== stickPointer) return;
    stickPointer = null; axisX = axisY = 0; joystick.classList.remove('engaged'); paintStick(0, 0, 0); sendInput();
  }
  ['pointerup', 'pointercancel', 'lostpointercapture'].forEach((name) => joystick.addEventListener(name, endStick));
  dodgeButton.addEventListener('pointerdown', (event) => {
    if (!canControl() || dodgeButton.disabled || dodgePointer !== null) return;
    event.preventDefault(); dodgePointer = event.pointerId; dodgeButton.setPointerCapture(event.pointerId);
    dodgeButton.classList.add('pressed'); dodgeEdge = true; sendInput();
  });
  function endDodge(event) {
    if (event.pointerId !== dodgePointer) return;
    dodgePointer = null; dodgeButton.classList.remove('pressed'); dodgeEdge = false; sendInput();
  }
  ['pointerup', 'pointercancel', 'lostpointercapture'].forEach((name) => dodgeButton.addEventListener(name, endDodge));
  function keyboardAxes() {
    axisX = Number(keys.has('d') || keys.has('arrowright')) - Number(keys.has('a') || keys.has('arrowleft'));
    axisY = Number(keys.has('w') || keys.has('arrowup')) - Number(keys.has('s') || keys.has('arrowdown'));
    const length = Math.hypot(axisX, axisY); if (length > 1) { axisX /= length; axisY /= length; }
    paintStick(axisX, axisY, joystick.clientWidth * .34);
  }
  window.addEventListener('keydown', (event) => {
    const key = event.key.toLowerCase();
    if ((key === ' ' || key === 'enter') && event.target.closest?.('.upgrade-choice')) return;
    if (!canControl() || !['w','a','s','d','arrowup','arrowdown','arrowleft','arrowright',' '].includes(key)) return;
    event.preventDefault();
    if (key === ' ') { if (!event.repeat && !dodgeButton.disabled) { dodgeEdge = true; sendInput(); } }
    else { keys.add(key); keyboardAxes(); sendInput(); }
  });
  window.addEventListener('keyup', (event) => { if (keys.delete(event.key.toLowerCase())) { keyboardAxes(); sendInput(); } });
  window.addEventListener('blur', () => { foreground = false; refreshSender(); paintUpgrade(); });
  window.addEventListener('focus', () => { foreground = true; refreshSender(); paintUpgrade(); });
  window.addEventListener('resize', () => releaseControls());

  function token(value) {
    const key = `couch-crypt:${location.host}:${room}`;
    try {
      if (value !== undefined) { localStorage.setItem(key, value); memoryToken = value; }
      return localStorage.getItem(key) || memoryToken;
    } catch { if (value !== undefined) memoryToken = value; return memoryToken; }
  }
  function stopTimers() { clearInterval(heartbeat); clearInterval(watchdog); clearTimeout(retry); clearInterval(sender); sender = undefined; clearUpgrade(); releaseControls(); }
  function showForm(text, label = 'Try again') {
    joined = false; controls.hidden = true; document.body.classList.remove('playing');
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
    stopTimers(); stopped = false; joined = false; phase = 'lobby'; hostActive = false; sequence = 0;
    controls.hidden = true; document.body.classList.remove('playing');
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
        player.textContent = data.player === 1 ? 'PLAYER I' : 'PLAYER II';
        document.documentElement.style.setProperty('--player', data.player === 1 ? '#d4b47c' : '#9bc3bb');
        title.textContent = 'You have a place';
        message.textContent = 'Connected. Look at the TV while your host gathers the party.';
        network.textContent = 'CONNECTED';
        heartbeat = setInterval(() => { if (current.readyState === WebSocket.OPEN) current.send('{"type":"ping"}'); }, 2000);
      } else if (data.type === 'status') {
        setControlScreen(data);
        party.textContent = data.connected === 2 ? 'Both souls are here' : data.phase === 'invite' ? 'Waiting for your companion' : 'Ready for a solo descent';
        const descriptions = {
          lobby: 'Connected. Your host can start solo or gather a companion.',
          session: '',
          results: 'Look at the TV.',
          paused: 'Look at the TV.',
          invite: 'Look at the TV.'
        };
        message.textContent = data.hostActive ? (descriptions[data.phase] ?? descriptions.lobby) : 'The host has stepped away. Stay here to keep your place.';
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
      stopTimers(); controls.hidden = true; document.body.classList.remove('playing');
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
    refreshSender(); paintUpgrade();
    if (document.visibilityState === 'visible' && room && !stopped && socket?.readyState !== WebSocket.OPEN) connect();
  });
  window.addEventListener('pagehide', () => { stopped = true; stopTimers(); joined = false; socket?.close(); });
  window.addEventListener('pageshow', (event) => { if (event.persisted) discover(); });
  discover();
})();
