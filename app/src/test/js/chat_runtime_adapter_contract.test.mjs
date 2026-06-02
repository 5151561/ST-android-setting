import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const adapter = readFileSync('app/src/main/assets/chat_runtime_adapter.js', 'utf8');
const bridge = readFileSync('app/src/main/java/io/github/sanitised/st/chat/ChatRuntimeBridge.kt', 'utf8');
const migrationDoc = readFileSync('docs/chat-interface-migration.md', 'utf8');

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

  assert.match(adapter, /case 'runtime\.save':\s*\n\s*handleSave\(cmdId\);/);
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
  assert.match(adapter, /case 'cfg\.get':\s*\n\s*handleGetCfg\(cmdId\);/);
  assert.match(adapter, /case 'cfg\.set':\s*\n\s*handleSetCfg\(payload, cmdId\);/);
  assert.match(adapter, /case 'worldInfo\.get':\s*\n\s*handleGetWorldInfo\(cmdId\);/);
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
