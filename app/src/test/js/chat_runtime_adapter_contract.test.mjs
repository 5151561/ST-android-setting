import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const adapter = readFileSync('app/src/main/assets/chat_runtime_adapter.js', 'utf8');
const bridge = readFileSync('app/src/main/java/io/github/sanitised/st/chat/ChatRuntimeBridge.kt', 'utf8');
const migrationDoc = readFileSync('docs/chat-interface-migration.md', 'utf8');

function adapterWithModuleStub() {
  return adapter.replace(
    /function importModule\(path\) \{\n[\s\S]*?\n  \}/,
    `function importModule(path) {
    var stubs = window.__moduleStubs || {};
    if (stubs[path]) return Promise.resolve(stubs[path]);
    var origin = (window.location && window.location.origin) || '';
    var clean = String(path).replace(/^\\.?\\/+/,'');
    return import(origin + '/' + clean);
  }`
  );
}

function extractBlock(source, startPattern) {
  const start = source.search(startPattern);
  assert.notEqual(start, -1, `missing block: ${startPattern}`);
  const rest = source.slice(start);
  const end = rest.indexOf('\n    });');
  assert.notEqual(end, -1, `unterminated block: ${startPattern}`);
  return rest.slice(0, end + '\n    });'.length);
}

test('ST MESSAGE_DELETED listener does not forward chat length as deleted id', () => {
  const listener = extractBlock(adapter, /es\.on\(ev\.MESSAGE_DELETED/);

  assert.match(listener, /postSnapshot\(\)/);
  assert.doesNotMatch(listener, /postEvent\('message\.deleted'/);
});

test('save retry dispatches a runtime save command instead of reloading chat', () => {
  const retrySave = bridge.match(/fun retrySave\(\) \{[\s\S]*?\n    \}/)?.[0] ?? '';

  assert.match(adapter, /case 'runtime\.save':\s*\n\s*(?:return\s+)?handleSave\(cmdId\);/);
  assert.match(adapter, /async function handleSave\(cmdId\)/);
  assert.match(retrySave, /name = "runtime\.save"|BridgeMessage\(kind = "command", name = "runtime\.save"/);
  assert.doesNotMatch(retrySave, /chat\.reload/);
});

test('migration doc does not claim save integrity has a guaranteed save.error path', () => {
  assert.doesNotMatch(migrationDoc, /保存失败时通过 try\/catch 发 `save\.error` 事件/);
  assert.doesNotMatch(migrationDoc, /保存 integrity 错误处理（`save\.error` JS 事件 \+ `SaveErrorBanner` 原生 UI \+ `safeSave\(\)`/);
  assert.doesNotMatch(migrationDoc, /保存 integrity 错误处理和用户提示~~ ✅（`save\.error` 事件 \+ `SaveErrorBanner`/);
});

test('chat send contract carries pending attachments into the adapter', () => {
  const sendMessage = bridge.match(/fun sendMessage\(text: String\) \{[\s\S]*?\n    \}/)?.[0] ?? '';

  assert.match(sendMessage, /pendingAttachments/);
  assert.match(sendMessage, /attachments/);
  assert.match(adapter, /payload\.attachments/);
  assert.match(adapter, /extra\.media/);
  assert.match(adapter, /extra\.files/);
  assert.match(adapter, /MESSAGE_SENT/);
});

test('adapter exposes cfg and world info bridge commands', () => {
  assert.match(adapter, /case 'cfg\.get':\s*\n\s*(?:return\s+)?handleGetCfg\(cmdId\);/);
  assert.match(adapter, /case 'cfg\.set':\s*\n\s*(?:return\s+)?handleSetCfg\(payload, cmdId\);/);
  assert.match(adapter, /case 'worldInfo\.get':\s*\n\s*(?:return\s+)?handleGetWorldInfo\(cmdId\);/);
  assert.match(adapter, /cfg_guidance_scale/);
  assert.match(adapter, /cfg_negative_prompt/);
  assert.match(adapter, /cfg_positive_prompt/);
  assert.match(adapter, /world_info/);
});

test('adapter imports ST modules via absolute origin URL, not relative specifiers', () => {
  // Injected scripts resolve relative import() against about:blank and fail with
  // "base URL is about:blank". All dynamic imports must go through importModule,
  // which builds an absolute same-origin URL. Regression for the full-device
  // report (Checkpoint/Branch/ItemizedPrompt/DataBank import failures).
  assert.doesNotMatch(adapter, /import\('\.\//, 'no relative dynamic import() should remain');
  assert.match(adapter, /function importModule\(path\)/);
  assert.match(adapter, /window\.location && window\.location\.origin/);
  assert.match(adapter, /return import\(origin \+ '\/' \+ clean\)/);
  for (const mod of ['script.js', 'scripts/bookmarks.js', 'scripts/chats.js', 'scripts/itemized-prompts.js']) {
    assert.match(adapter, new RegExp(`await importModule\\('${mod.replace('.', '\\.')}'\\)`));
  }
});

test('dispatch catch replies with an error so no command silently times out', () => {
  const dispatch = adapter.match(/dispatch: function \(jsonStr\) \{[\s\S]*?\n    \},/)?.[0] ?? '';
  // cmdId must be hoisted out of the try so the catch can reference it.
  assert.match(dispatch, /var cmdId = '';\s*\n\s*try \{/);
  assert.match(dispatch, /cmdId = msg\.id \|\| '';/);
  assert.match(dispatch, /catch \(e\) \{[\s\S]*?if \(cmdId\) \{[\s\S]*?postError\(cmdId/);
});

test('handleListQuickReplies accesses quickReplyApi entirely inside try', () => {
  const handler = adapter.match(/function handleListQuickReplies\(cmdId\) \{[\s\S]*?\n  \}/)?.[0] ?? '';
  // The api / api.settings access must be inside the try (it can throw while the
  // extension is initialising). Regression for the persistent "加载快捷回复 超时".
  const tryIdx = handler.indexOf('try {');
  const apiIdx = handler.indexOf('globalThis.quickReplyApi');
  assert.ok(tryIdx !== -1 && apiIdx !== -1 && apiIdx > tryIdx, 'quickReplyApi access must be inside try');
  assert.match(handler, /var settings = api && api\.settings;/);
});

test('background auto-load commands time out silently (best-effort)', () => {
  const loadQr = bridge.match(/fun loadQuickReplies\(\) \{[\s\S]*?\n    \}/)?.[0] ?? '';
  const loadExt = bridge.match(/fun loadExtensions\(\) \{[\s\S]*?\n    \}/)?.[0] ?? '';
  assert.match(loadQr, /silentTimeout = true/);
  assert.match(loadExt, /silentTimeout = true/);
  assert.match(bridge, /private fun registerTimeout\(commandId: String, commandName: String, silent: Boolean = false\)/);
  assert.match(bridge, /if \(silent\) \{[\s\S]*?Log\.w/);
});

test('bridge registers pending command before evaluating JS to avoid fast-result race', () => {
  const dispatch = bridge.match(/private fun dispatch\([\s\S]*?\n    \}/)?.[0] ?? '';
  const registerIdx = dispatch.indexOf('registerTimeout(message.id, message.name, silentTimeout)');
  const evalIdx = dispatch.indexOf('wv.evaluateJavascript(js)');
  assert.ok(registerIdx !== -1, 'dispatch should register timeout for tracked commands');
  assert.ok(evalIdx !== -1, 'dispatch should evaluate runtime JS');
  assert.ok(
    registerIdx < evalIdx,
    'pending command must be registered before evaluateJavascript because fast JS results can arrive before the callback'
  );
});

test('checkpoint command passes blank forceName to use ST auto naming without hidden popup', () => {
  const handler = adapter.match(/async function handleCreateCheckpoint\(payload, cmdId\) \{[\s\S]*?\n  \}/)?.[0] ?? '';
  // ST createNewBookmark treats forceName === null as "show Popup input" and
  // forceName === '' as "confirmed blank input, auto-generate name". The native
  // dialog says blank means auto naming, so the adapter must not pass null.
  assert.match(handler, /var name = payload\.name != null \? String\(payload\.name\) : '';/);
  assert.doesNotMatch(handler, /payload\.name \? String\(payload\.name\) : null/);
  assert.match(handler, /createNewBookmark\(messageId, \{ forceName: name \}\)/);
});

test('queued bridge writes wait for chat reload to finish before dispatching the next command', async () => {
  const events = [];
  let finishReload;
  const ctx = {
    onlineStatus: 'connected',
    mainApi: 'openai',
    chat: [],
    characters: [],
    groups: [],
    chatMetadata: {},
    eventSource: { on: () => {} },
    eventTypes: { APP_READY: 'APP_READY' },
    reloadCurrentChat: () => {
      events.push('reload:start');
      return new Promise((resolve) => {
        finishReload = () => {
          events.push('reload:finish');
          resolve();
        };
      });
    },
    generate: (mode) => {
      events.push(`generate:${mode}`);
      sandbox.document.body.dataset.generating = 'false';
      return Promise.resolve();
    },
  };
  const sandbox = {
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    crypto: { randomUUID: () => 'test-id' },
    document: {
      readyState: 'loading',
      body: { dataset: { generating: 'true' } },
      addEventListener: () => {},
    },
    SillyTavern: { getContext: () => ctx },
    STAndroid: { postChatEvent: () => {} },
  };
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(adapter, sandbox);

  sandbox.STAndroidChatRuntime.dispatch({ id: 'reload', name: 'chat.reload' });
  sandbox.STAndroidChatRuntime.dispatch({ id: 'regen', name: 'generation.regenerate' });
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(events, ['reload:start']);
  finishReload();
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(events, ['reload:start', 'reload:finish', 'generate:regenerate']);
});

test('generation stop is not blocked behind a long running queued generation', async () => {
  const events = [];
  const ctx = {
    onlineStatus: 'connected',
    mainApi: 'openai',
    chat: [],
    characters: [],
    groups: [],
    chatMetadata: {},
    eventSource: { on: () => {} },
    eventTypes: { APP_READY: 'APP_READY' },
    generate: (mode) => {
      events.push(`generate:${mode}`);
      return new Promise(() => {});
    },
    stopGeneration: () => {
      events.push('stop');
      return true;
    },
  };
  const sandbox = {
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    crypto: { randomUUID: () => 'test-id' },
    document: {
      readyState: 'loading',
      body: { dataset: { generating: 'true' } },
      addEventListener: () => {},
    },
    SillyTavern: { getContext: () => ctx },
    STAndroid: { postChatEvent: () => {} },
  };
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(adapter, sandbox);

  sandbox.STAndroidChatRuntime.dispatch({ id: 'regen', name: 'generation.regenerate' });
  await new Promise((resolve) => setImmediate(resolve));
  sandbox.document.body.dataset.generating = 'false';
  sandbox.STAndroidChatRuntime.dispatch({ id: 'stop', name: 'generation.stop' });
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(events, ['generate:regenerate', 'stop']);
});

test('group regenerate stop is not blocked behind a long running queued group generation', async () => {
  const events = [];
  const ctx = {
    onlineStatus: 'connected',
    mainApi: 'openai',
    groupId: 'group-1',
    chat: [],
    characters: [],
    groups: [],
    chatMetadata: {},
    eventSource: { on: () => {} },
    eventTypes: { APP_READY: 'APP_READY' },
    stopGeneration: () => {
      events.push('stop');
      return true;
    },
  };
  const sandbox = {
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    crypto: { randomUUID: () => 'test-id' },
    document: {
      readyState: 'loading',
      body: { dataset: { generating: 'true' } },
      addEventListener: () => {},
    },
    SillyTavern: { getContext: () => ctx },
    STAndroid: { postChatEvent: () => {} },
    __moduleStubs: {
      'scripts/group-chats.js': {
        regenerateGroup: () => {
          events.push('group:regenerate');
          return new Promise(() => {});
        },
      },
    },
  };
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(adapterWithModuleStub(), sandbox);

  sandbox.STAndroidChatRuntime.dispatch({ id: 'regen', name: 'generation.regenerate' });
  await new Promise((resolve) => setImmediate(resolve));
  sandbox.document.body.dataset.generating = 'false';
  sandbox.STAndroidChatRuntime.dispatch({ id: 'stop', name: 'generation.stop' });
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(events, ['group:regenerate', 'stop']);
});

test('authors note bridge commands use upstream note_prompt metadata key with legacy fallback', async () => {
  const results = [];
  const ctx = {
    onlineStatus: 'connected',
    mainApi: 'openai',
    chat: [],
    characters: [],
    groups: [],
    chatMetadata: { authors_note: 'legacy text' },
    eventSource: { on: () => {} },
    eventTypes: { APP_READY: 'APP_READY' },
    saveChat: async () => {},
  };
  const sandbox = {
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    crypto: { randomUUID: () => 'test-id' },
    document: {
      readyState: 'loading',
      body: { dataset: { generating: 'false' } },
      addEventListener: () => {},
    },
    SillyTavern: { getContext: () => ctx },
    STAndroid: { postChatEvent: (json) => results.push(JSON.parse(json)) },
  };
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(adapter, sandbox);

  // 读：仅有 legacy authors_note 时回退读取。
  sandbox.STAndroidChatRuntime.dispatch({ id: 'an-get-legacy', name: 'authorsNote.get' });
  await new Promise((resolve) => setImmediate(resolve));
  const legacyResult = results.find((r) => r.id === 'an-get-legacy' && r.kind === 'result');
  assert.equal(legacyResult.payload.text, 'legacy text');

  // 写：必须写 ST 上游字段 note_prompt（authors-note.js metadata_keys.prompt），并清掉自造的 legacy 字段。
  sandbox.STAndroidChatRuntime.dispatch({ id: 'an-set', name: 'authorsNote.set', payload: { text: 'fresh note' } });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(ctx.chatMetadata.note_prompt, 'fresh note');
  assert.equal(Object.prototype.hasOwnProperty.call(ctx.chatMetadata, 'authors_note'), false);

  // 读：note_prompt 优先。
  sandbox.STAndroidChatRuntime.dispatch({ id: 'an-get', name: 'authorsNote.get' });
  await new Promise((resolve) => setImmediate(resolve));
  const upstreamResult = results.find((r) => r.id === 'an-get' && r.kind === 'result');
  assert.equal(upstreamResult.payload.text, 'fresh note');
});
