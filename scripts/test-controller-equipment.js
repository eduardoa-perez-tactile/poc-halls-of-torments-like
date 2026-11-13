#!/usr/bin/env node
'use strict';
// Focused protocol/UI-state check against the real controller script, without a browser dependency.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor() {
    this.hidden = false; this.disabled = false; this.textContent = ''; this.dataset = {};
    this.listeners = new Map(); this.children = new Map(); this.attributes = new Map();
    const classes = new Set();
    this.classList = {
      add: (...names) => names.forEach((name) => classes.add(name)),
      remove: (...names) => names.forEach((name) => classes.delete(name)),
      contains: (name) => classes.has(name),
      toggle: (name, force) => {
        const on = force === undefined ? !classes.has(name) : force;
        if (on) classes.add(name); else classes.delete(name);
        return on;
      }
    };
    this.style = { values: new Map(), setProperty(name, value) { this.values.set(name, value); } };
  }
  addEventListener(name, handler) { this.listeners.set(name, handler); }
  querySelector(selector) {
    if (!this.children.has(selector)) this.children.set(selector, new Element());
    return this.children.get(selector);
  }
  setAttribute(name, value) { this.attributes.set(name, value); }
  setPointerCapture() {}
  hasPointerCapture() { return false; }
  click() { this.listeners.get('click')?.({}); }
}

async function main() {
  const elements = new Map();
  const get = (id) => {
    if (!elements.has(id)) elements.set(id, new Element());
    return elements.get(id);
  };
  const document = {
    getElementById: get, querySelector: get, body: new Element(), documentElement: new Element(),
    hidden: false, visibilityState: 'visible', addEventListener() {}
  };
  let now = 1000;
  const sockets = [];
  class Socket {
    static OPEN = 1;
    constructor() { this.readyState = Socket.OPEN; this.bufferedAmount = 0; this.sent = []; sockets.push(this); }
    send(text) { this.sent.push(JSON.parse(text)); }
    close() { this.readyState = 3; }
    receive(data) { this.onmessage({ data: JSON.stringify(data) }); }
  }
  let timerId = 0;
  const intervals = new Map();
  const windowEvents = new Map();
  const context = {
    document, window: { addEventListener(name, handler) { windowEvents.set(name, handler); } }, location: { host: 'tv:8787', href: 'http://tv:8787/' },
    performance: { now: () => now }, Date, URL, AbortController, WebSocket: Socket,
    localStorage: { getItem: () => '', setItem() {} },
    setInterval: (handler, delay) => { const id = ++timerId; intervals.set(id, { handler, delay }); return id; },
    clearInterval: (id) => intervals.delete(id), setTimeout: () => ++timerId, clearTimeout() {},
    fetch: async () => ({ ok: true, json: async () => ({ room: 'ABC123' }) })
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../assets/web/controller.js'), 'utf8'), context);
  await new Promise(setImmediate);
  const socket = sockets[0];
  assert.ok(socket, 'Controller discovers the host and opens its socket');
  socket.receive({ type: 'joined', player: 1, token: 'test-token' });
  const option = (title) => ({
    id: title.toLowerCase().replaceAll(' ', '-'), title, description: '+12% damage; -5% movement speed.',
    slot: 'RING', icon: 0, rank: 0, category: 'OFFENSE',
    headline: 'RING I: REPLACES Ashen Loop', before: '+10% DMG -5% SPD', after: '+12% DMG +5% CRIT'
  });
  const equipment = { id: 1, secondsLeft: 27, options: ['Iron Loop', 'Swift Loop', 'Guard Loop'].map(option) };
  const upgrade = { id: 1, level: 2, secondsLeft: 28, options: ['Soulfire', 'Quickening', 'Vitality'].map(option) };
  const status = (changes = {}) => socket.receive({
    type: 'status', connected: 1, hostActive: true, phase: 'session', alive: true, dodgeCooldown: 0,
    progression: { level: 2, xp: 0, xpToNext: 22, upgrade: null }, equipment, ...changes
  });
  const choices = get('upgrade-choices');
  const first = get('upgrade-option-0');
  const starterBuild = { skillsGranted: 19, equipmentGranted: 8 };
  status({ equipment: null, starterBuild });
  assert.equal(get('game-controls').hidden, false, 'New late join immediately shows the controller');
  assert.equal(choices.hidden, true, 'Inherited defaults do not open any cards');
  assert.equal(get('.touch-controls').inert, false);
  assert.equal(get('dodge').disabled, false);
  assert.equal(get('starter-build').hidden, false, 'A short confirmation needs no interaction');
  const keyboard = (key) => windowEvents.get('keydown')({ key, target: {}, preventDefault() {} });
  keyboard('d');
  assert.equal(socket.sent.at(-1).x, 1, 'A defaulted late join can move immediately');
  get('dodge').listeners.get('pointerdown')({ pointerId: 7, preventDefault() {} });
  assert.equal(socket.sent.at(-1).dodge, true, 'A defaulted late join can Dodge immediately');
  get('dodge').listeners.get('pointerup')({ pointerId: 7 });
  status({ equipment: null, starterBuild, phase: 'paused' });
  assert.equal(get('starter-build').hidden, true);
  assert.equal(socket.sent.at(-1).x, 0, 'Pause still neutralizes movement');
  status({ equipment: null, starterBuild });
  assert.equal(choices.hidden, true);
  assert.equal(get('dodge').disabled, false);
  status({ progression: { upgrade }, starterBuild });
  assert.equal(get('starter-build').hidden, true, 'Normal future choices retain the full card space');
  assert.equal(get('upgrade-level').textContent, 'LEVEL 2', 'Skill choices take priority over equipment');
  assert.equal(get('equipment-keep').hidden, true);
  first.click();
  assert.equal(socket.sent.at(-1).type, 'upgrade');
  assert.equal(choices.hidden, false, 'A submitted choice remains visible until the host clears it');

  status();
  assert.equal(get('upgrade-level').textContent, 'EQUIPMENT', 'Equal numeric IDs from different choice types do not reuse a skill card');
  assert.equal(first.querySelector('.skill-title').textContent, 'Iron Loop');
  assert.equal(first.querySelector('.skill-rank').textContent, 'RING');
  assert.equal(first.querySelector('.skill-before').textContent, equipment.options[0].before);
  assert.equal(first.querySelector('.skill-after').textContent, equipment.options[0].after);
  assert.equal(first.querySelector('.skill-illustration').style.backgroundImage, "url('/art/item-icons.png')");
  assert.equal(first.querySelector('.skill-illustration').style.backgroundSize, '800% 600%');
  assert.ok(first.attributes.get('aria-label').includes(equipment.options[0].description));
  const neutral = socket.sent.at(-1);
  assert.equal(neutral.type, 'input'); assert.equal(neutral.x, 0); assert.equal(neutral.y, 0); assert.equal(neutral.dodge, false);
  first.click();
  assert.equal(socket.sent.at(-1).type, 'equipment');
  assert.equal(socket.sent.at(-1).offerId, 1); assert.equal(socket.sent.at(-1).option, 0);
  assert.equal(choices.hidden, false);
  status({ phase: 'paused' });
  assert.equal(choices.hidden, false); assert.equal(first.disabled, true);
  status({ phase: 'invite', hostActive: false });
  assert.equal(choices.hidden, false); assert.equal(first.disabled, true);
  now += 1500;
  status();
  assert.equal(first.disabled, false, 'Host restoration permits an unacknowledged choice to be retried');
  assert.equal(get('equipment-keep').hidden, false);
  get('equipment-keep').click();
  assert.equal(socket.sent.at(-1).type, 'equipment'); assert.equal(socket.sent.at(-1).option, -1);
  assert.equal(choices.hidden, false, 'Keeping gear also waits for authoritative acknowledgment');
  status({ equipment: null });
  assert.equal(choices.hidden, true, 'Only authoritative offer clear restores gameplay controls');
  assert.equal(get('.touch-controls').inert, false);

  socket.close(); socket.onclose();
  assert.equal(choices.hidden, true);
  // Simulate joined/status restoration after transport recovery. This checks repainting after local
  // teardown; socket reconnection and reservation ownership are covered by the Java protocol tests.
  socket.readyState = Socket.OPEN;
  socket.receive({ type: 'joined', player: 1, token: 'test-token' });
  status();
  assert.equal(choices.hidden, false); assert.equal(first.disabled, false);
  assert.equal(get('upgrade-level').textContent, 'EQUIPMENT');
  status({ progression: { upgrade } });
  assert.equal(first.querySelector('.skill-illustration').style.backgroundImage, '', 'Skill cards restore the existing skill atlas after gear');
  assert.equal(first.querySelector('.skill-illustration').style.backgroundSize, '');
  const futureSkill = { ...upgrade, id: 9, level: 12, durationSeconds: 30, secondsLeft: 17.25 };
  status({ equipment: null, progression: { upgrade: futureSkill }, starterBuild });
  assert.equal(get('upgrade-level').textContent, 'LEVEL 12');
  assert.equal(choices.querySelector('.upgrade-kicker').textContent, 'CHOOSE YOUR POWER');
  assert.equal(get('upgrade-seconds').textContent, '18');
  assert.ok(Math.abs(parseFloat(choices.style.values.get('--choice-progress')) - 57.5) < .001,
    'Future rewards retain the thirty-second progress rail');
  socket.close(); socket.onclose();
  now += 12000;
  socket.readyState = Socket.OPEN;
  socket.receive({ type: 'joined', player: 1, token: 'test-token' });
  status({ phase: 'paused', equipment: null, progression: { upgrade: futureSkill }, starterBuild });
  assert.equal(choices.hidden, false, 'Reconnect restores the authoritative pending choice');
  assert.equal(get('upgrade-level').textContent, 'LEVEL 12');
  assert.equal(get('upgrade-seconds').textContent, '18', 'Disconnected time does not reduce the host timer');
  assert.equal(first.disabled, true);
  status({ equipment: null, progression: { upgrade: futureSkill }, starterBuild });
  first.click();
  assert.equal(socket.sent.at(-1).offerId, 9, 'Reconnect preserves the offer identity');
  assert.equal(choices.hidden, false, 'Restored selection also waits for host acknowledgment');
  status({ equipment: null, starterBuild });
  assert.equal(choices.hidden, true);
  assert.equal(get('starter-build').hidden, false);
  keyboard('d');
  assert.equal(socket.sent.at(-1).x, 1, 'Controls return after the host clears the normal offer');
  const levelUpSkill = { ...upgrade, id: 10, secondsLeft: 26, durationSeconds: 30 };
  status({ phase: 'level_up', progression: { upgrade: levelUpSkill }, equipment });
  assert.equal(first.disabled, false, 'Combat pause leaves personal skill choices enabled');
  assert.equal(get('upgrade-timer-label').textContent, 'Seconds to choose');
  assert.equal(get('.touch-controls').inert, true);
  assert.equal(get('dodge').disabled, true);
  now += 5000;
  [...intervals.values()].filter((timer) => timer.delay === 100).forEach((timer) => timer.handler());
  assert.equal(get('upgrade-seconds').textContent, '21', 'The choice countdown runs during level-up combat pause');
  keyboard('d');
  assert.equal(socket.sent.at(-1).x, 0, 'All movement remains neutral during level-up pause');
  first.click();
  assert.equal(socket.sent.at(-1).type, 'upgrade'); assert.equal(socket.sent.at(-1).offerId, 10);
  assert.equal(choices.hidden, false, 'Submitted cards remain until host acknowledgment');
  status({ phase: 'level_up', equipment, progression: { upgrade: null } });
  assert.equal(choices.hidden, true, 'Unrelated equipment waits behind the party level-up');
  assert.equal(get('game-controls').hidden, true, 'Finishing one player does not restore a controller before the companion');
  assert.equal(get('party').textContent, 'Waiting for your companion');
  assert.equal(get('message').textContent, 'Combat is paused. Your choice is complete.');
  keyboard('d'); assert.equal(socket.sent.at(-1).x, 0);
  status({ phase: 'level_up', equipment: null, progression: { upgrade: levelUpSkill } });
  socket.close(); socket.onclose();
  socket.readyState = Socket.OPEN; socket.receive({ type: 'joined', player: 1, token: 'test-token' });
  status({ phase: 'level_up', equipment: null, progression: { upgrade: levelUpSkill } });
  assert.equal(choices.hidden, false); assert.equal(first.disabled, false);
  assert.equal(get('upgrade-seconds').textContent, '26', 'Reconnect restores the host countdown in the selection phase');
  status({ phase: 'paused', equipment: null, progression: { upgrade: levelUpSkill } });
  now += 5000;
  [...intervals.values()].filter((timer) => timer.delay === 100).forEach((timer) => timer.handler());
  assert.equal(get('upgrade-seconds').textContent, '26', 'Manual pause still freezes selection timers');
  assert.equal(first.disabled, true);
  status({ phase: 'session', equipment: null });
  assert.equal(get('game-controls').hidden, false); assert.equal(choices.hidden, true);
  keyboard('d'); assert.equal(socket.sent.at(-1).x, 1, 'The controller returns when the party finishes');
  const loot = { ...equipment, id: 33, secondsLeft: 20, durationSeconds: 30 };
  status({ phase: 'loot', equipment: loot });
  assert.equal(get('upgrade-level').textContent, 'EQUIPMENT');
  assert.equal(first.disabled, false); assert.equal(get('equipment-keep').hidden, false);
  assert.equal(get('dodge').disabled, true); assert.equal(get('.touch-controls').inert, true);
  now += 5000;
  [...intervals.values()].filter((timer) => timer.delay === 100).forEach((timer) => timer.handler());
  assert.equal(get('upgrade-seconds').textContent, '15', 'Loot countdown runs while combat is paused');
  keyboard('d'); assert.equal(socket.sent.at(-1).x, 0);
  get('equipment-keep').click();
  assert.equal(socket.sent.at(-1).type, 'equipment'); assert.equal(socket.sent.at(-1).option, -1);
  assert.equal(choices.hidden, false, 'Loot decline stays visible until acknowledgment');
  status({ phase: 'loot', equipment: null });
  assert.equal(choices.hidden, true); assert.equal(get('game-controls').hidden, true);
  assert.equal(get('title').textContent, 'Loot found');
  assert.equal(get('party').textContent, 'Waiting for your companion');
  assert.equal(get('message').textContent, 'Combat is paused. Your choice is complete.');
  status({ phase: 'loot', equipment: loot });
  socket.close(); socket.onclose();
  socket.readyState = Socket.OPEN; socket.receive({ type: 'joined', player: 1, token: 'test-token' });
  status({ phase: 'paused', equipment: loot });
  now += 5000;
  [...intervals.values()].filter((timer) => timer.delay === 100).forEach((timer) => timer.handler());
  assert.equal(get('upgrade-seconds').textContent, '20', 'Paused reconnect retains the host loot timer');
  assert.equal(first.disabled, true);
  status({ phase: 'loot', equipment: loot });
  assert.equal(first.disabled, false);
  status({ phase: 'level_up', equipment: loot, progression: { upgrade: levelUpSkill } });
  assert.equal(get('upgrade-level').textContent, 'LEVEL 2', 'Skills take priority over loot');
  assert.equal(get('equipment-keep').hidden, true);
  status({ phase: 'loot', equipment: loot });
  first.click();
  assert.equal(socket.sent.at(-1).type, 'equipment'); assert.equal(socket.sent.at(-1).offerId, 33);
  status({ phase: 'session', equipment: null });
  keyboard('d'); assert.equal(socket.sent.at(-1).x, 1, 'All-party completion restores movement');
  for (const starterBuild of [null, {}, { skillsGranted: 0, equipmentGranted: 0 }, { skillsGranted: '19' }]) {
    status({ equipment: null, starterBuild });
    assert.equal(get('starter-build').hidden, true, 'Missing, zero or malformed default grants stay silent');
    assert.equal(choices.hidden, true);
  }
  status({ equipment: null, starterBuild: { skillsGranted: 0, equipmentGranted: 1 } });
  assert.equal(get('starter-build').hidden, false, 'A gear-only default build is acknowledged');
  status({ equipment: null, starterBuild, phase: 'lobby' });
  assert.equal(get('game-controls').hidden, true); assert.equal(get('starter-build').hidden, true);
  console.log('Controller skill/loot pause, party waiting, defaults, acknowledgment and reconnect checks passed.');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
