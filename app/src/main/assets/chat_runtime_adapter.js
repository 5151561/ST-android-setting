(function () {
  'use strict';
  if (window.STAndroidChatRuntime) return;

  var postEvent = function (name, payload) {
    var msg = JSON.stringify({
      id: crypto.randomUUID ? crypto.randomUUID() : ('' + Date.now() + Math.random()),
      kind: 'event',
      name: name,
      payload: payload || {},
      timestamp: Date.now()
    });
    if (window.STAndroid && typeof window.STAndroid.postChatEvent === 'function') {
      window.STAndroid.postChatEvent(msg);
    }
  };

  var postResult = function (commandId, payload) {
    var msg = JSON.stringify({
      id: commandId,
      kind: 'result',
      name: 'bridge.result',
      payload: payload || {},
      timestamp: Date.now()
    });
    if (window.STAndroid && typeof window.STAndroid.postChatEvent === 'function') {
      window.STAndroid.postChatEvent(msg);
    }
  };

  var postError = function (commandId, message) {
    var msg = JSON.stringify({
      id: commandId,
      kind: 'error',
      name: 'bridge.error',
      payload: { message: message || 'unknown error' },
      timestamp: Date.now()
    });
    if (window.STAndroid && typeof window.STAndroid.postChatEvent === 'function') {
      window.STAndroid.postChatEvent(msg);
    }
  };

  function getContext() {
    var root = globalThis.SillyTavern;
    return root && typeof root.getContext === 'function' ? root.getContext() : null;
  }

  function normalizeIdentifier(value) {
    return String(value || '')
      .split(/[\\/]/)
      .pop()
      .replace(/\.(png|jpe?g|webp|gif|json|jsonl|charx)$/i, '')
      .trim()
      .toLowerCase();
  }

  function identifiersMatch(expected, actual) {
    var left = normalizeIdentifier(expected);
    var right = normalizeIdentifier(actual);
    return !!left && !!right && left === right;
  }

  function characterMatches(character, target) {
    if (!character) return false;
    return identifiersMatch(target, character.avatar) ||
      identifiersMatch(target, character.avatar_url) ||
      identifiersMatch(target, character.filename) ||
      identifiersMatch(target, character.name);
  }

  function isRuntimeGenerating(ctx) {
    var stopButton = document.getElementById('mes_stop');
    var stopVisible = false;
    if (stopButton) {
      var stopStyle = getComputedStyle(stopButton);
      stopVisible = stopStyle.display !== 'none' && stopStyle.visibility !== 'hidden';
    }
    var streaming = !!(ctx && ctx.streamingProcessor && ctx.streamingProcessor.isFinished === false);
    return streaming || stopVisible;
  }

  function serializeMessage(msg, index) {
    if (!msg) return null;
    return {
      id: typeof index === 'number' ? index : (msg.id != null ? msg.id : -1),
      name: msg.name || '',
      mes: msg.mes || '',
      is_user: !!msg.is_user,
      is_system: !!msg.is_system,
      send_date: msg.send_date || '',
      swipe_id: msg.swipe_id || 0,
      swipes: Array.isArray(msg.swipes) ? msg.swipes : [],
      extra: msg.extra || {}
    };
  }

  function buildSnapshot() {
    var ctx = getContext();
    if (!ctx) return null;
    var chat = ctx.chat || [];
    var characters = ctx.characters || [];
    var thisChid = ctx.characterId;
    var character = (thisChid != null && characters[thisChid]) ? characters[thisChid] : null;
    var chatMetadata = ctx.chatMetadata || {};

    return {
      mode: ctx.groupId ? 'group' : 'character',
      avatarUrl: character ? (character.avatar || '') : '',
      characterName: character ? (character.name || '') : '',
      chatFile: character ? (character.chat || '') : '',
      isGenerating: isRuntimeGenerating(ctx),
      messages: chat.map(function (msg, i) { return serializeMessage(msg, i); }).filter(Boolean),
      metadata: { integrity: chatMetadata.integrity || '' }
    };
  }

  function postSnapshot() {
    var snap = buildSnapshot();
    if (snap) postEvent('chat.loaded', snap);
    return snap;
  }

  // --- Event source listeners ---
  var eventsBound = false;
  var appReady = false;

  function tryBindEvents() {
    if (eventsBound) return true;
    var ctx = getContext();
    if (!ctx || !ctx.eventSource || !ctx.eventTypes) return false;

    var es = ctx.eventSource;
    var ev = ctx.eventTypes;

    // Wait for APP_READY before signalling runtime.ready to Android.
    // APP_READY fires after ST completes firstLoadInit, characters are loaded,
    // and the initial chat is rendered.
    es.on(ev.APP_READY, function () {
      if (appReady) return;
      appReady = true;
      postEvent('runtime.ready', {});
      throttledSnapshot();
    });

    es.on(ev.GENERATION_STARTED, function () {
      postEvent('generation.started', {});
    });

    es.on(ev.GENERATION_ENDED, function () {
      postEvent('generation.ended', {});
      throttledSnapshot();
    });

    es.on(ev.GENERATION_STOPPED, function () {
      postEvent('generation.stopped', {});
      throttledSnapshot();
    });

    es.on(ev.CHAT_CHANGED, function () {
      postEvent('chat.changed', {});
      throttledSnapshot();
    });

    if (ev.CHAT_LOADED) {
      es.on(ev.CHAT_LOADED, function () {
        throttledSnapshot();
      });
    }

    es.on(ev.MESSAGE_SENT, function (index) {
      var c = getContext();
      var chat = c && c.chat ? c.chat : [];
      var msg = chat[index];
      if (msg) postEvent('message.added', serializeMessage(msg, index));
    });

    es.on(ev.MESSAGE_RECEIVED, function (index) {
      var c = getContext();
      var chat = c && c.chat ? c.chat : [];
      var msg = chat[index];
      if (msg) postEvent('message.added', serializeMessage(msg, index));
    });

    es.on(ev.MESSAGE_UPDATED, function (index) {
      var c = getContext();
      var chat = c && c.chat ? c.chat : [];
      var msg = chat[index];
      if (msg) postEvent('message.updated', serializeMessage(msg, index));
    });

    es.on(ev.MESSAGE_DELETED, function (index) {
      postEvent('message.deleted', { id: index });
    });

    if (ev.STREAM_TOKEN_RECEIVED) {
      var lastTokenTime = 0;
      es.on(ev.STREAM_TOKEN_RECEIVED, function () {
        var now = Date.now();
        if (now - lastTokenTime < 80) return;
        lastTokenTime = now;
        var c = getContext();
        var chat = c && c.chat ? c.chat : [];
        var lastIdx = chat.length - 1;
        var msg = chat[lastIdx];
        if (msg) {
          postEvent('stream.token', {
            id: lastIdx,
            token: '',
            fullText: msg.mes || ''
          });
        }
      });
    }

    eventsBound = true;
    return true;
  }

  var snapshotTimer = null;
  function throttledSnapshot() {
    if (snapshotTimer) return;
    snapshotTimer = setTimeout(function () {
      snapshotTimer = null;
      postSnapshot();
    }, 200);
  }

  // --- Command dispatch from Android ---
  window.STAndroidChatRuntime = {
    dispatch: function (jsonStr) {
      try {
        var msg = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
        var name = msg.name;
        var payload = msg.payload || {};
        var cmdId = msg.id || '';

        switch (name) {
          case 'runtime.getSnapshot':
            var snap = buildSnapshot();
            if (snap) {
              postEvent('chat.loaded', snap);
              postResult(cmdId, snap);
            } else {
              postError(cmdId, 'Runtime not ready');
            }
            break;

          case 'chat.openCharacter':
            handleOpenCharacter(payload, cmdId);
            break;

          case 'chat.send':
            handleSend(payload, cmdId);
            break;

          case 'generation.stop':
            handleStop(cmdId);
            break;

          case 'generation.regenerate':
            handleRegenerate(cmdId);
            break;

          case 'generation.continue':
            handleContinue(cmdId);
            break;

          case 'chat.new':
            handleNewChat(cmdId);
            break;

          case 'chat.reload':
            handleReload(cmdId);
            break;

          default:
            postError(cmdId, 'Unknown command: ' + name);
        }
      } catch (e) {
        console.error('[STAndroidChatRuntime] dispatch error', e);
      }
    },

    getSnapshot: function () {
      return buildSnapshot();
    }
  };

  async function handleOpenCharacter(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }

      var avatarUrl = payload.avatarUrl || '';
      var chatFile = payload.chatFile || null;
      var characters = ctx.characters || [];

      var charIndex = characters.findIndex(function (c) { return characterMatches(c, avatarUrl); });

      if (charIndex < 0) { postError(cmdId, 'Character not found: ' + avatarUrl); return; }

      if (typeof ctx.selectCharacterById === 'function') {
        await ctx.selectCharacterById(charIndex, { switchMenu: false });
      } else {
        postError(cmdId, 'selectCharacterById not available');
        return;
      }

      ctx = getContext();
      var activeCharacter = ctx && Array.isArray(ctx.characters) ? ctx.characters[ctx.characterId] : null;
      var activeMatches = characterMatches(activeCharacter, avatarUrl);
      if (!activeMatches) {
        postError(cmdId, 'Character did not open: ' + avatarUrl);
        return;
      }

      if (chatFile && typeof ctx.openCharacterChat === 'function') {
        var normalized = String(chatFile).replace(/\.jsonl$/i, '');
        await ctx.openCharacterChat(normalized);
      }
      postSnapshot();
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'openCharacter failed: ' + (err && err.message ? err.message : err));
    }
  }

  function handleSend(payload, cmdId) {
    var text = payload.text || '';
    if (!text.trim()) { postError(cmdId, 'Empty message'); return; }
    if (isRuntimeGenerating(getContext())) { postError(cmdId, 'Generation is already running'); return; }

    var textarea = document.getElementById('send_textarea');
    var sendBtn = document.getElementById('send_but');
    if (!textarea || !sendBtn) { postError(cmdId, 'Send UI not found'); return; }

    textarea.value = text;
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
    setTimeout(function () {
      sendBtn.click();
      postResult(cmdId, {});
      setTimeout(postSnapshot, 300);
    }, 50);
  }

  function handleStop(cmdId) {
    var ctx = getContext();
    if (ctx && typeof ctx.stopGeneration === 'function') {
      var stopped = ctx.stopGeneration();
      stopped ? postResult(cmdId, {}) : postError(cmdId, 'No active generation to stop');
      setTimeout(postSnapshot, 200);
      return;
    }
    var stopBtn = document.getElementById('mes_stop');
    if (stopBtn) {
      stopBtn.click();
      postResult(cmdId, {});
    } else {
      postError(cmdId, 'stopGeneration not available');
    }
  }

  function handleRegenerate(cmdId) {
    var ctx = getContext();
    if (ctx && typeof ctx.generate === 'function') {
      Promise.resolve(ctx.generate('regenerate')).then(function () {
        postSnapshot();
        postResult(cmdId, {});
      }).catch(function (err) {
        postError(cmdId, 'regenerate failed: ' + (err && err.message ? err.message : err));
      });
    } else {
      postError(cmdId, 'generate not available');
    }
  }

  function handleContinue(cmdId) {
    var ctx = getContext();
    if (ctx && typeof ctx.generate === 'function') {
      Promise.resolve(ctx.generate('continue')).then(function () {
        postSnapshot();
        postResult(cmdId, {});
      }).catch(function (err) {
        postError(cmdId, 'continue failed: ' + (err && err.message ? err.message : err));
      });
    } else {
      postError(cmdId, 'generate not available');
    }
  }

  function handleNewChat(cmdId) {
    // doNewChat is an ES module export but not on getContext().
    // Trigger via the DOM button that ST's own UI uses.
    var btn = document.getElementById('option_start_new_chat');
    if (btn) {
      btn.click();
      postResult(cmdId, {});
      setTimeout(postSnapshot, 500);
    } else {
      postError(cmdId, 'New chat button not found');
    }
  }

  function handleReload(cmdId) {
    var ctx = getContext();
    if (ctx && typeof ctx.reloadCurrentChat === 'function') {
      ctx.reloadCurrentChat().then(function () {
        postSnapshot();
        postResult(cmdId, {});
      }).catch(function (err) {
        postError(cmdId, 'reloadCurrentChat failed: ' + (err && err.message ? err.message : err));
      });
    } else {
      postError(cmdId, 'reloadCurrentChat not available');
    }
  }

  // --- Initialization ---
  // We poll until getContext().eventSource is available, then bind event
  // listeners. runtime.ready is NOT sent here — it fires only when ST
  // emits APP_READY (after full initialization including character load).
  function init() {
    if (tryBindEvents()) {
      // APP_READY is auto-fired by ST's EventEmitter when it already happened.
      if (appReady) {
        postSnapshot();
      }
    } else {
      var attempts = 0;
      var timer = setInterval(function () {
        attempts++;
        if (tryBindEvents()) {
          clearInterval(timer);
          if (appReady) {
            postSnapshot();
          }
        } else if (attempts > 150) {
          clearInterval(timer);
          postEvent('runtime.error', { message: 'ST runtime did not initialize within 30 seconds' });
        }
      }, 200);
    }
  }

  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    setTimeout(init, 100);
  } else {
    document.addEventListener('DOMContentLoaded', function () { setTimeout(init, 100); });
  }
})();
