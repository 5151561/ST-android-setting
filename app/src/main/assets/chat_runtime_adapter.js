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

  function getGenerationContext(cmdId) {
    var ctx = getContext();
    if (!ctx) {
      postError(cmdId, 'Runtime not ready');
      return null;
    }
    if (ctx.onlineStatus === 'no_connection') {
      postError(cmdId, 'SillyTavern 还没有连接模型 API');
      return null;
    }
    return ctx;
  }

  function isRuntimeGenerating() {
    return !!(document.body && document.body.dataset && document.body.dataset.generating === 'true');
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
    var groups = ctx.groups || [];
    var thisChid = ctx.characterId;
    var groupId = ctx.groupId || '';
    var character = (thisChid != null && characters[thisChid]) ? characters[thisChid] : null;
    var group = groupId ? groups.find(function (item) { return item && item.id == groupId; }) : null;
    var chatMetadata = ctx.chatMetadata || {};

    return {
      mode: group ? 'group' : 'character',
      avatarUrl: group ? (group.id || '') : (character ? (character.avatar || '') : ''),
      characterName: group ? (group.name || '') : (character ? (character.name || '') : ''),
      chatFile: group ? (group.chat_id || '') : (character ? (character.chat || '') : ''),
      isGenerating: isRuntimeGenerating(),
      messages: chat.map(function (msg, i) { return serializeMessage(msg, i); }).filter(Boolean),
      metadata: {
        integrity: chatMetadata.integrity || '',
        authorsNote: chatMetadata.authors_note || '',
        cfgScale: Number(chatMetadata.cfg_guidance_scale) || 1.0,
        cfgNegativePrompt: chatMetadata.cfg_negative_prompt || '',
        cfgPositivePrompt: chatMetadata.cfg_positive_prompt || '',
        worldInfo: chatMetadata.world_info || ''
      }
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
  var lastGenerationState = false;
  var generationWatchTimer = null;
  var pendingSendAttachments = [];

  function syncGenerationState() {
    var generating = isRuntimeGenerating();
    if (generating !== lastGenerationState) {
      lastGenerationState = generating;
      postEvent(generating ? 'generation.started' : 'generation.ended', {});
    }
    return generating;
  }

  function watchGenerationState(durationMs) {
    if (generationWatchTimer) clearInterval(generationWatchTimer);
    var startedAt = Date.now();
    var sawGenerating = syncGenerationState();
    generationWatchTimer = setInterval(function () {
      var generating = syncGenerationState();
      if (generating) sawGenerating = true;
      if (Date.now() - startedAt > (durationMs || 30000) || (sawGenerating && !generating)) {
        clearInterval(generationWatchTimer);
        generationWatchTimer = null;
      }
    }, 250);
  }

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
      watchGenerationState();
      throttledSnapshot();
    });

    es.on(ev.GENERATION_ENDED, function () {
      lastGenerationState = false;
      postEvent('generation.ended', {});
      throttledSnapshot();
    });

    es.on(ev.GENERATION_STOPPED, function () {
      lastGenerationState = false;
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
      if (msg) {
        if (attachPendingAttachmentsToMessage(msg)) {
          safeSave().then(function () { throttledSnapshot(); });
        }
        postEvent('message.added', serializeMessage(msg, index));
      }
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

    es.on(ev.MESSAGE_DELETED, function () {
      // ST emits the new chat length here, not the deleted message id.
      // A snapshot is the only reliable generic sync path.
      postSnapshot();
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

    // Reasoning/thinking finished streaming → refresh snapshot so the
    // native UI picks up extra.reasoning on the last message.
    if (ev.STREAM_REASONING_DONE) {
      es.on(ev.STREAM_REASONING_DONE, function () {
        throttledSnapshot();
      });
    }

    tryWrapToastr();
    eventsBound = true;
    return true;
  }

  // Forward ST toastr notifications (slash command errors, API warnings,
  // extension messages) to the native UI. toastr.subscribe only reports
  // visibility changes without the type, so we wrap the 4 level methods.
  var toastrWrapped = false;
  function stripHtml(value) {
    var text = String(value == null ? '' : value);
    if (text.indexOf('<') < 0) return text;
    try {
      var div = document.createElement('div');
      div.innerHTML = text;
      return (div.textContent || div.innerText || '').trim();
    } catch (_) {
      return text;
    }
  }
  function tryWrapToastr() {
    if (toastrWrapped) return;
    var toastr = window.toastr;
    if (!toastr || typeof toastr.error !== 'function') return;
    ['error', 'warning', 'info', 'success'].forEach(function (type) {
      var original = toastr[type];
      if (typeof original !== 'function') return;
      toastr[type] = function (message, title) {
        var result = original.apply(toastr, arguments);
        try {
          postEvent('runtime.toast', {
            type: type,
            title: stripHtml(title),
            message: stripHtml(message)
          });
        } catch (_) { /* best-effort */ }
        return result;
      };
    });
    toastrWrapped = true;
  }

  async function safeSave() {
    // ctx.saveChat() maps to saveChatConditional() which catches its own
    // errors internally (console.error only, no throw). The catch below only
    // detects wrapper-level failures such as a missing or replaced save API.
    var ctx = getContext();
    if (!ctx || typeof ctx.saveChat !== 'function') {
      postEvent('save.error', { message: 'saveChat not available' });
      return false;
    }
    try {
      await ctx.saveChat();
      return true;
    } catch (err) {
      postEvent('save.error', { message: err && err.message ? err.message : String(err) });
      return false;
    }
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

          case 'runtime.save':
            handleSave(cmdId);
            break;

          case 'chat.openCharacter':
            handleOpenCharacter(payload, cmdId);
            break;

          case 'chat.openGroup':
            handleOpenGroup(payload, cmdId);
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

          case 'message.swipePrevious':
            handleSwipe(payload, cmdId, 'left');
            break;

          case 'message.swipeNext':
            handleSwipe(payload, cmdId, 'right');
            break;

          case 'message.edit':
            handleEditMessage(payload, cmdId);
            break;

          case 'message.delete':
            handleDeleteMessage(payload, cmdId);
            break;

          case 'message.hide':
            handleHideMessage(payload, cmdId);
            break;

          case 'message.unhide':
            handleUnhideMessage(payload, cmdId);
            break;

          case 'authorsNote.get':
            handleGetAuthorsNote(cmdId);
            break;

          case 'authorsNote.set':
            handleSetAuthorsNote(payload, cmdId);
            break;

          case 'cfg.get':
            handleGetCfg(cmdId);
            break;

          case 'cfg.set':
            handleSetCfg(payload, cmdId);
            break;

          case 'worldInfo.get':
            handleGetWorldInfo(cmdId);
            break;

          case 'quickReply.list':
            handleListQuickReplies(cmdId);
            break;

          case 'quickReply.execute':
            handleExecuteQuickReply(payload, cmdId);
            break;

          case 'chat.createCheckpoint':
            handleCreateCheckpoint(payload, cmdId);
            break;

          case 'chat.createBranch':
            handleCreateBranch(payload, cmdId);
            break;

          case 'chat.openCheckpoint':
            handleOpenCheckpoint(payload, cmdId);
            break;

          case 'extensions.list':
            handleListExtensions(cmdId);
            break;

          case 'itemizedPrompt.get':
            handleGetItemizedPrompt(payload, cmdId);
            break;

          case 'dataBank.list':
            handleListDataBank(cmdId);
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

  async function handleSave(cmdId) {
    var saved = await safeSave();
    if (saved) {
      postSnapshot();
      postResult(cmdId, {});
    } else {
      postError(cmdId, 'saveChat not available');
    }
  }

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

  async function handleOpenGroup(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }

      var groupId = payload.groupId || '';
      var groups = ctx.groups || [];
      var group = groups.find(function (g) { return g && String(g.id) === String(groupId); });

      if (!group) { postError(cmdId, 'Group not found: ' + groupId); return; }
      if (typeof ctx.openGroupChat !== 'function') {
        postError(cmdId, 'openGroupChat not available');
        return;
      }

      var chatId = payload.chatId || group.chat_id || (Array.isArray(group.chats) ? group.chats[0] : null);
      if (!chatId) {
        postError(cmdId, 'Group has no chat file: ' + groupId);
        return;
      }

      var openedByClick = false;
      if (!payload.chatId || payload.chatId === group.chat_id) {
        var groupNodes = Array.prototype.slice.call(document.querySelectorAll('.group_select'));
        var groupNode = groupNodes.find(function (node) {
          return node.getAttribute('data-grid') === String(group.id) ||
            node.getAttribute('data-chid') === String(group.id);
        });
        if (groupNode) {
          groupNode.click();
          await new Promise(function (resolve) { setTimeout(resolve, 600); });
          openedByClick = true;
        }
      }
      if (!openedByClick) {
        await ctx.openGroupChat(group.id, chatId);
      }
      postSnapshot();
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'openGroup failed: ' + (err && err.message ? err.message : err));
    }
  }

  function handleSend(payload, cmdId) {
    var text = payload.text || '';
    if (!text.trim()) { postError(cmdId, 'Empty message'); return; }
    if (isRuntimeGenerating()) { postError(cmdId, 'Generation is already running'); return; }
    if (!getGenerationContext(cmdId)) return;

    var textarea = document.getElementById('send_textarea');
    var sendBtn = document.getElementById('send_but');
    if (!textarea || !sendBtn) { postError(cmdId, 'Send UI not found'); return; }

    pendingSendAttachments = normalizePendingAttachments(payload.attachments);
    textarea.value = text;
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
    setTimeout(function () {
      sendBtn.click();
      watchGenerationState();
      postResult(cmdId, {});
      setTimeout(postSnapshot, 300);
    }, 50);
  }

  function normalizePendingAttachments(attachments) {
    if (!Array.isArray(attachments)) return [];
    return attachments.map(function (item) {
      return {
        url: item && (item.url || item.path) ? String(item.url || item.path) : '',
        name: item && item.name ? String(item.name) : '',
        size: item && item.size != null ? Number(item.size) || 0 : 0,
        isMedia: !!(item && item.isMedia)
      };
    }).filter(function (item) { return item.url; });
  }

  function attachPendingAttachmentsToMessage(msg) {
    if (!pendingSendAttachments.length || !msg) return false;
    msg.extra = msg.extra || {};
    var media = pendingSendAttachments.filter(function (item) { return item.isMedia; });
    var files = pendingSendAttachments.filter(function (item) { return !item.isMedia; });
    if (media.length) {
      msg.extra.media = (Array.isArray(msg.extra.media) ? msg.extra.media : []).concat(media.map(function (item) {
        return { url: item.url, path: item.url, type: 'image/*', title: item.name, name: item.name, size: item.size };
      }));
    }
    if (files.length) {
      msg.extra.files = (Array.isArray(msg.extra.files) ? msg.extra.files : []).concat(files.map(function (item) {
        return { url: item.url, path: item.url, name: item.name, size: item.size };
      }));
    }
    pendingSendAttachments = [];
    return media.length > 0 || files.length > 0;
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

  async function handleRegenerate(cmdId) {
    var ctx = getGenerationContext(cmdId);
    if (!ctx) return;

    // In group mode, ST uses regenerateGroup() which deletes the current
    // round's AI responses before regenerating. ctx.generate('regenerate')
    // does NOT handle this correctly for groups.
    if (ctx.groupId) {
      try {
        var groupModule = await import('./scripts/group-chats.js');
        if (typeof groupModule.regenerateGroup !== 'function') {
          postError(cmdId, 'regenerateGroup not available');
          return;
        }
        watchGenerationState();
        await groupModule.regenerateGroup();
        postSnapshot();
        postResult(cmdId, {});
      } catch (err) {
        postError(cmdId, 'regenerateGroup failed: ' + (err && err.message ? err.message : err));
      }
      return;
    }

    if (typeof ctx.generate === 'function') {
      watchGenerationState();
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
    var ctx = getGenerationContext(cmdId);
    if (ctx && typeof ctx.generate === 'function') {
      watchGenerationState();
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

  async function handleNewChat(cmdId) {
    try {
      var scriptModule = await import('./script.js');
      if (typeof scriptModule.doNewChat !== 'function') {
        postError(cmdId, 'doNewChat not available');
        return;
      }
      await scriptModule.doNewChat({ deleteCurrentChat: false });
      postSnapshot();
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'newChat failed: ' + (err && err.message ? err.message : err));
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

  function handleSwipe(payload, cmdId, direction) {
    var ctx = getContext();
    if (!ctx || !ctx.swipe) {
      postError(cmdId, 'Swipe runtime not available');
      return;
    }
    var messageId = Number(payload.id);
    var message = Array.isArray(ctx.chat) ? ctx.chat[messageId] : null;
    var action = direction === 'left' ? ctx.swipe.left : ctx.swipe.right;
    if (!message || typeof action !== 'function') {
      postError(cmdId, 'Message cannot be swiped');
      return;
    }
    Promise.resolve(action(null, { message: message })).then(function () {
      postSnapshot();
      postResult(cmdId, {});
    }).catch(function (err) {
      postError(cmdId, 'swipe failed: ' + (err && err.message ? err.message : err));
    });
  }

  async function handleEditMessage(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      var chat = ctx.chat || [];
      var messageId = Number(payload.id);
      var newText = String(payload.text || '');
      if (messageId < 0 || messageId >= chat.length) {
        postError(cmdId, 'Invalid message index: ' + messageId);
        return;
      }
      var msg = chat[messageId];
      msg.mes = newText;
      if (Array.isArray(msg.swipes) && msg.swipes.length > 0) {
        var swipeIdx = msg.swipe_id || 0;
        if (swipeIdx < msg.swipes.length) {
          msg.swipes[swipeIdx] = newText;
        }
      }
      // Emit MESSAGE_EDITED first (extensions may transform the text)
      if (ctx.eventSource && ctx.eventTypes && ctx.eventTypes.MESSAGE_EDITED) {
        await ctx.eventSource.emit(ctx.eventTypes.MESSAGE_EDITED, messageId);
        // Re-read text in case an extension modified it
        newText = msg.mes;
      }
      // Update DOM if the message element exists
      var mesEl = document.querySelector('.mes[mesid="' + messageId + '"]');
      if (mesEl) {
        var mesText = mesEl.querySelector('.mes_text');
        try {
          var scriptModule = await import('./script.js');
          if (mesText && typeof scriptModule.messageFormatting === 'function') {
            mesText.innerHTML = scriptModule.messageFormatting(
              newText, msg.name, msg.is_system, msg.is_user, messageId, {}, false
            );
          }
          var mesBias = mesEl.querySelector('.mes_bias');
          if (mesBias && msg.extra && msg.extra.bias != null && typeof scriptModule.messageFormatting === 'function') {
            mesBias.innerHTML = scriptModule.messageFormatting(msg.extra.bias, '', false, false, -1, {}, false);
          }
          var jqMesEl = window.jQuery ? window.jQuery(mesEl) : null;
          if (jqMesEl && typeof scriptModule.appendMediaToMessage === 'function') {
            scriptModule.appendMediaToMessage(msg, jqMesEl);
          }
          if (jqMesEl && typeof scriptModule.addCopyToCodeBlocks === 'function') {
            scriptModule.addCopyToCodeBlocks(jqMesEl);
          }
        } catch (_) { /* DOM update is best-effort */ }
      }
      await safeSave();
      if (ctx.eventSource && ctx.eventTypes && ctx.eventTypes.MESSAGE_UPDATED) {
        await ctx.eventSource.emit(ctx.eventTypes.MESSAGE_UPDATED, messageId);
      }
      postEvent('message.updated', serializeMessage(msg, messageId));
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'editMessage failed: ' + (err && err.message ? err.message : err));
    }
  }

  async function handleDeleteMessage(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      var chat = ctx.chat || [];
      var messageId = Number(payload.id);
      if (messageId < 0 || messageId >= chat.length) {
        postError(cmdId, 'Invalid message index: ' + messageId);
        return;
      }
      // Prefer ST's native deleteMessage which handles DOM removal,
      // itemized prompts cleanup, mesid updates, and debounced save.
      // It requires the DOM element to exist (returns early otherwise).
      var mesEl = document.querySelector('.mes[mesid="' + messageId + '"]');
      if (mesEl) {
        var scriptModule = await import('./script.js');
        if (typeof scriptModule.deleteMessage === 'function') {
          await scriptModule.deleteMessage(messageId, undefined, false);
          postEvent('message.deleted', { id: messageId });
          postSnapshot();
          postResult(cmdId, {});
          return;
        }
      }
      // Fallback: direct splice when DOM element is not rendered
      // (e.g. message outside lazy-render window).
      chat.splice(messageId, 1);
      if (ctx.chatMetadata) ctx.chatMetadata.tainted = true;
      await safeSave();
      if (ctx.eventSource && ctx.eventTypes && ctx.eventTypes.MESSAGE_DELETED) {
        await ctx.eventSource.emit(ctx.eventTypes.MESSAGE_DELETED, chat.length);
      }
      postEvent('message.deleted', { id: messageId });
      postSnapshot();
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'deleteMessage failed: ' + (err && err.message ? err.message : err));
    }
  }

  async function handleHideMessage(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      var chat = ctx.chat || [];
      var messageId = Number(payload.id);
      if (messageId < 0 || messageId >= chat.length) {
        postError(cmdId, 'Invalid message index: ' + messageId);
        return;
      }
      // ST hides messages by toggling is_system. Use the canonical function
      // from chats.js which also refreshes swipe buttons and saves.
      var chatsModule = await import('./scripts/chats.js');
      await chatsModule.hideChatMessageRange(messageId, messageId, false);
      postEvent('message.updated', serializeMessage(chat[messageId], messageId));
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'hideMessage failed: ' + (err && err.message ? err.message : err));
    }
  }

  async function handleUnhideMessage(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      var chat = ctx.chat || [];
      var messageId = Number(payload.id);
      if (messageId < 0 || messageId >= chat.length) {
        postError(cmdId, 'Invalid message index: ' + messageId);
        return;
      }
      var chatsModule = await import('./scripts/chats.js');
      await chatsModule.hideChatMessageRange(messageId, messageId, true);
      postEvent('message.updated', serializeMessage(chat[messageId], messageId));
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'unhideMessage failed: ' + (err && err.message ? err.message : err));
    }
  }

  function handleGetAuthorsNote(cmdId) {
    var ctx = getContext();
    if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
    var metadata = ctx.chatMetadata || {};
    postResult(cmdId, {
      text: metadata.authors_note || '',
      position: metadata.authors_note_position || 'after',
      depth: Number(metadata.authors_note_depth) || 4
    });
  }

  async function handleSetAuthorsNote(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      if (!ctx.chatMetadata) ctx.chatMetadata = {};
      ctx.chatMetadata.authors_note = payload.text || '';
      await safeSave();
      postSnapshot();
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'setAuthorsNote failed: ' + (err && err.message ? err.message : err));
    }
  }

  function handleGetCfg(cmdId) {
    var ctx = getContext();
    if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
    var metadata = ctx.chatMetadata || {};
    postResult(cmdId, {
      scale: Number(metadata.cfg_guidance_scale) || 1.0,
      negativePrompt: metadata.cfg_negative_prompt || '',
      positivePrompt: metadata.cfg_positive_prompt || ''
    });
  }

  async function handleSetCfg(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      if (!ctx.chatMetadata) ctx.chatMetadata = {};
      ctx.chatMetadata.cfg_guidance_scale = Number(payload.scale) || 1.0;
      ctx.chatMetadata.cfg_negative_prompt = payload.negativePrompt || '';
      ctx.chatMetadata.cfg_positive_prompt = payload.positivePrompt || '';
      await safeSave();
      postSnapshot();
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'setCfg failed: ' + (err && err.message ? err.message : err));
    }
  }

  function handleGetWorldInfo(cmdId) {
    var ctx = getContext();
    if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
    var metadata = ctx.chatMetadata || {};
    postResult(cmdId, { name: metadata.world_info || '' });
  }

  // --- Quick Replies (B1) ---
  // window.quickReplyApi is exposed by the Quick Reply v2 extension.
  // The visible buttons come from settings.config/chatConfig/charConfig setList
  // links where isVisible is true (matches ButtonUi.renderBar).
  function handleListQuickReplies(cmdId) {
    var api = globalThis.quickReplyApi;
    if (!api || !api.settings) {
      // Extension not loaded → empty list, not an error.
      postResult(cmdId, { items: [] });
      return;
    }
    try {
      var settings = api.settings;
      var links = []
        .concat(settings.config && settings.config.setList ? settings.config.setList : [])
        .concat(settings.chatConfig && settings.chatConfig.setList ? settings.chatConfig.setList : [])
        .concat(settings.charConfig && settings.charConfig.setList ? settings.charConfig.setList : []);
      var items = [];
      links.forEach(function (link) {
        if (!link || !link.isVisible || !link.set) return;
        var set = link.set;
        if (set.isDeleted) return;
        var setName = set.name || '';
        (set.qrList || []).forEach(function (qr) {
          if (!qr || qr.isHidden) return;
          items.push({
            setName: setName,
            label: qr.label || '',
            icon: qr.icon || '',
            message: qr.message || ''
          });
        });
      });
      postResult(cmdId, { items: items });
    } catch (err) {
      postResult(cmdId, { items: [] });
    }
  }

  function handleExecuteQuickReply(payload, cmdId) {
    var api = globalThis.quickReplyApi;
    var setName = payload && payload.setName ? String(payload.setName) : '';
    var label = payload && payload.label ? String(payload.label) : '';
    if (!label) { postError(cmdId, 'Quick reply label missing'); return; }
    try {
      var promise;
      if (api && typeof api.executeQuickReply === 'function' && setName) {
        promise = api.executeQuickReply(setName, label);
      } else if (typeof globalThis.executeQuickReplyByName === 'function') {
        promise = globalThis.executeQuickReplyByName(setName ? (setName + '.' + label) : label);
      } else {
        postError(cmdId, 'Quick reply runtime not available');
        return;
      }
      watchGenerationState();
      Promise.resolve(promise).then(function () {
        postResult(cmdId, {});
        setTimeout(postSnapshot, 300);
      }).catch(function (err) {
        postError(cmdId, 'quickReply failed: ' + (err && err.message ? err.message : err));
      });
    } catch (err) {
      postError(cmdId, 'quickReply failed: ' + (err && err.message ? err.message : err));
    }
  }

  // --- Checkpoint / Branch (B2) ---
  // createNewBookmark(mesId, {forceName}) writes extra.bookmark_link and does
  // NOT navigate. branchChat(mesId) auto-names, writes extra.branches, and
  // navigates to the new chat.
  async function handleCreateCheckpoint(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      var chat = ctx.chat || [];
      var messageId = Number(payload.id);
      if (messageId < 0 || messageId >= chat.length) {
        postError(cmdId, 'Invalid message index: ' + messageId);
        return;
      }
      var bookmarks = await import('./scripts/bookmarks.js');
      if (typeof bookmarks.createNewBookmark !== 'function') {
        postError(cmdId, 'createNewBookmark not available');
        return;
      }
      var name = payload.name ? String(payload.name) : null;
      var created = await bookmarks.createNewBookmark(messageId, { forceName: name });
      if (!created) {
        postError(cmdId, 'Checkpoint not created');
        return;
      }
      postSnapshot();
      postResult(cmdId, { name: created });
    } catch (err) {
      postError(cmdId, 'createCheckpoint failed: ' + (err && err.message ? err.message : err));
    }
  }

  async function handleCreateBranch(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      var chat = ctx.chat || [];
      var messageId = Number(payload.id);
      if (messageId < 0 || messageId >= chat.length) {
        postError(cmdId, 'Invalid message index: ' + messageId);
        return;
      }
      var bookmarks = await import('./scripts/bookmarks.js');
      if (typeof bookmarks.branchChat !== 'function') {
        postError(cmdId, 'branchChat not available');
        return;
      }
      // branchChat auto-navigates to the new branch chat.
      var created = await bookmarks.branchChat(messageId);
      if (!created) {
        postError(cmdId, 'Branch not created');
        return;
      }
      postSnapshot();
      postResult(cmdId, { name: created });
    } catch (err) {
      postError(cmdId, 'createBranch failed: ' + (err && err.message ? err.message : err));
    }
  }

  async function handleOpenCheckpoint(payload, cmdId) {
    try {
      var ctx = getContext();
      if (!ctx) { postError(cmdId, 'Runtime not ready'); return; }
      var name = payload && payload.name ? String(payload.name).replace(/\.jsonl$/i, '') : '';
      if (!name) { postError(cmdId, 'Checkpoint name missing'); return; }
      if (ctx.groupId && typeof ctx.openGroupChat === 'function') {
        await ctx.openGroupChat(ctx.groupId, name);
      } else if (typeof ctx.openCharacterChat === 'function') {
        await ctx.openCharacterChat(name);
      } else {
        postError(cmdId, 'open chat runtime not available');
        return;
      }
      postSnapshot();
      postResult(cmdId, {});
    } catch (err) {
      postError(cmdId, 'openCheckpoint failed: ' + (err && err.message ? err.message : err));
    }
  }

  // --- Extensions (B3) ---
  async function handleListExtensions(cmdId) {
    try {
      var mod = await import('./scripts/extensions.js');
      var names = Array.isArray(mod.extensionNames) ? mod.extensionNames.slice() : [];
      postResult(cmdId, { names: names });
    } catch (err) {
      postResult(cmdId, { names: [] });
    }
  }

  // --- Itemized Prompts (C2) ---
  // itemizedPrompts is module-internal to script.js; the public copy lives on
  // window via getContext()? No — it is exported from itemized-prompts.js but
  // the populated array is the one in script.js. The exported itemizedParams()
  // takes the array as an argument, so we read the live array from script.js.
  async function handleGetItemizedPrompt(payload, cmdId) {
    try {
      var messageId = Number(payload.id);
      var scriptModule = await import('./script.js');
      var itemizedModule = await import('./scripts/itemized-prompts.js');
      var list = scriptModule.itemizedPrompts || itemizedModule.itemizedPrompts || [];
      if (!Array.isArray(list) || list.length === 0) {
        postResult(cmdId, { available: false });
        return;
      }
      var setIndex = itemizedModule.findItemizedPromptSet(list, messageId);
      if (setIndex === undefined || setIndex === null) {
        postResult(cmdId, { available: false });
        return;
      }
      var params = await itemizedModule.itemizedParams(list, setIndex, messageId);
      // Build a normalized component list (name + token count). Field set
      // differs between OAI and non-OAI; we surface the common components and
      // the computed total.
      var isOai = params.this_main_api === 'openai';
      var total = isOai
        ? Number(params.finalPromptTokens) || 0
        : Number(params.totalTokensInPrompt) || 0;
      var components = [];
      function add(label, value) {
        var n = Number(value) || 0;
        if (n > 0) components.push({ name: label, tokens: n });
      }
      add('角色描述', params.charDescriptionTokens);
      add('角色性格', params.charPersonalityTokens);
      add('场景', params.scenarioTextTokens);
      add('用户人设', params.userPersonaStringTokens);
      add('世界书', params.worldInfoStringTokens);
      add('作者注', params.allAnchorsTokens);
      add('聊天历史', params.ActualChatHistoryTokens);
      if (isOai) {
        add('系统提示', params.oaiSystemTokens);
        add('示例对话', params.examplesStringTokens);
        add('偏置', params.oaiBiasTokens);
      } else {
        add('故事串', params.storyStringTokens);
        add('示例对话', params.examplesStringTokens);
        add('指令', params.instructionTokens);
        add('提示偏置', params.promptBiasTokens);
      }
      postResult(cmdId, {
        available: true,
        mesId: messageId,
        total: total,
        components: components,
        presetName: params.presetName || '',
        modelUsed: params.modelUsed || '',
        apiUsed: params.apiUsed || params.mainApiFriendlyName || '',
        tokenizer: params.selectedTokenizer || ''
      });
    } catch (err) {
      postError(cmdId, 'itemizedPrompt failed: ' + (err && err.message ? err.message : err));
    }
  }

  // --- Data Bank (C3) ---
  async function handleListDataBank(cmdId) {
    try {
      var chatsModule = await import('./scripts/chats.js');
      if (typeof chatsModule.getDataBankAttachmentsForSource !== 'function') {
        postResult(cmdId, { global: [], character: [], chat: [] });
        return;
      }
      function mapList(source) {
        var list = chatsModule.getDataBankAttachmentsForSource(source, false) || [];
        return list.map(function (a) {
          return {
            url: a && a.url ? String(a.url) : '',
            name: a && a.name ? String(a.name) : '',
            size: a && a.size != null ? Number(a.size) || 0 : 0,
            created: a && a.created != null ? Number(a.created) || 0 : 0
          };
        });
      }
      postResult(cmdId, {
        global: mapList('global'),
        character: mapList('character'),
        chat: mapList('chat')
      });
    } catch (err) {
      postResult(cmdId, { global: [], character: [], chat: [] });
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
