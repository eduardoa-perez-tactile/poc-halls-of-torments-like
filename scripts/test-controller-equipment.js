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
  const context = {
    document, window: { addEventListener() {} }, location: { host: 'tv:8787', href: 'http://tv:8787/' },
    performance: { now: () => now }, Date, URL, AbortController, WebSocket: Socket,
    localStorage: { getItem: () => '', setItem() {} },
    setInterval: () => ++timerId, clearInterval() {}, setTimeout: () => ++timerId, clearTimeout() {},
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
  status({ progression: { upgrade } });
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
  const foundation = { skillsRemaining: 3, equipmentRemaining: 2,
    skillFoundation: 'Soulfire 3 / Soul Circle 2', equipmentFoundation: 'Gatekeeper Helm / Emberheart Pendant' };
  const fastSkill = { ...upgrade, id: 2, level: 18, catchUp: true, durationSeconds: 15, secondsLeft: 7.5 };
  status({ progression: { upgrade: fastSkill }, catchUp: foundation });
  assert.equal(get('upgrade-level').textContent, 'CATCH-UP SKILL');
  assert.equal(choices.querySelector('.upgrade-kicker').textContent, '3 SKILL / 2 GEAR LEFT');
  assert.equal(choices.style.values.get('--choice-progress'), '50%', 'Catch-up rails use the host-owned fifteen-second duration');
  assert.equal(get('catch-up-build').hidden, false);
  assert.equal(get('catch-up-skills').textContent, 'Skills: ' + foundation.skillFoundation);
  assert.equal(get('catch-up-gear').textContent, 'Gear: ' + foundation.equipmentFoundation);
  get('catch-up-build').open = true;
  now += 12000;
  status({ phase: 'paused', progression: { upgrade: fastSkill }, catchUp: foundation });
  assert.equal(get('upgrade-seconds').textContent, '8'); assert.equal(first.disabled, true);
  assert.equal(choices.style.values.get('--choice-progress'), '50%');
  status({ progression: { upgrade: fastSkill }, catchUp: { ...foundation, skillsRemaining: 2 } });
  assert.equal(choices.querySelector('.upgrade-kicker').textContent, '2 SKILL / 2 GEAR LEFT');
  assert.equal(get('catch-up-build').open, true, 'Status refresh does not close an inspected foundation');
  const fastGear = { ...equipment, id: 2, catchUp: true, durationSeconds: 15, secondsLeft: 15 };
  status({ equipment: fastGear, catchUp: { ...foundation, skillsRemaining: 0 } });
  assert.equal(get('upgrade-level').textContent, 'CATCH-UP GEAR');
  assert.equal(choices.style.values.get('--choice-progress'), '100%');
  get('equipment-keep').click();
  assert.equal(socket.sent.at(-1).option, -1);
  assert.equal(choices.hidden, false);
  status({ equipment: null, catchUp: null });
  assert.equal(choices.hidden, true); assert.equal(get('catch-up-build').hidden, true);
  status({ progression: { upgrade: { ...upgrade, id: 3 } }, catchUp: null });
  assert.equal(get('upgrade-level').textContent, 'LEVEL 2');
  assert.ok(Math.abs(parseFloat(choices.style.values.get('--choice-progress')) - 28 / 30 * 100) < .001,
    'Older normal offer payloads retain the thirty-second timer fallback');
  assert.equal(get('catch-up-build').hidden, true);
  console.log('Controller choice priority, typed identity, acknowledgment, pause/restoration and fast catch-up duration/summary checks passed.');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
