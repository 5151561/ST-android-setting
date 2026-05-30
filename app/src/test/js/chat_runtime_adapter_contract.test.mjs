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
