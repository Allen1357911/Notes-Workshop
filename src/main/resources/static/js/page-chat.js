/* ============================================================
   AI 客服：会话列表 + 多轮对话（长回复折叠、删除二次确认）
   - 所有用户/AI 文本都经过 escapeHtml，避免 XSS
   - 会话隔离与归属校验都在后端，前端只负责展示
   ============================================================ */
(function () {
    if (!requireLogin()) {
        return;
    }

    /** 超过这个长度就折叠，避免一条长回复把页面撑爆 */
    const COLLAPSE_LIMIT = 300;
    /** 和后端 app.ai.chat-max-chars 保持一致，仅用于前端提前提示 */
    const MAX_CHARS = 1000;

    const sessionBox = document.getElementById('session-list');
    const messageBox = document.getElementById('chat-messages');
    const headBox = document.getElementById('chat-head');
    const input = document.getElementById('chat-text');
    const sendBtn = document.getElementById('send-btn');
    const hint = document.getElementById('chat-hint');
    const newBtn = document.getElementById('new-session');

    const state = {
        sessions: [],
        current: null,     // { session, messages }
        expanded: {},      // messageId -> true 表示已展开
        sending: false
    };

    /* ---------------- 会话列表 ---------------- */

    async function loadSessions(selectId) {
        sessionBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const data = await API.get('/api/ai/chat/sessions?page=1&size=50');
            state.sessions = data.list || [];
            renderSessions();

            const target = selectId || (state.sessions.length ? state.sessions[0].id : null);
            if (target) {
                await openSession(target);
            } else {
                state.current = null;
                renderHead();
                messageBox.innerHTML = '<div class="empty">还没有会话，点左上角「新建会话」开始提问</div>';
            }
        } catch (e) {
            sessionBox.innerHTML = '<div class="empty">加载失败：' + escapeHtml(e.message) + '</div>';
        }
    }

    function renderSessions() {
        if (!state.sessions.length) {
            sessionBox.innerHTML = '<div class="empty">还没有会话</div>';
            return;
        }
        let html = '';
        state.sessions.forEach(function (session) {
            const active = state.current && state.current.session.id === session.id ? ' active' : '';
            html += '<div class="chat-session' + active + '" data-id="' + session.id + '">'
                + '<div class="session-title">' + escapeHtml(session.title || '新会话') + '</div>'
                + '<div class="session-meta">' + (session.messageCount || 0) + ' 条 · '
                + formatTime(session.lastMessageTime || session.createTime)
                + '<button class="link-btn" data-delete="' + session.id + '">删除</button></div>'
                + '</div>';
        });
        sessionBox.innerHTML = html;

        sessionBox.querySelectorAll('.chat-session').forEach(function (item) {
            item.addEventListener('click', function (event) {
                if (event.target.hasAttribute('data-delete')) {
                    return;   // 点的是"删除"，不要顺便切换会话
                }
                openSession(parseInt(item.getAttribute('data-id'), 10));
            });
        });
        sessionBox.querySelectorAll('button[data-delete]').forEach(function (btn) {
            btn.addEventListener('click', function (event) {
                event.stopPropagation();
                removeSession(parseInt(btn.getAttribute('data-delete'), 10));
            });
        });
    }

    /* ---------------- 会话详情 ---------------- */

    async function openSession(id) {
        try {
            state.current = await API.get('/api/ai/chat/sessions/' + id);
            state.expanded = {};
            renderSessions();
            renderHead();
            renderMessages();
        } catch (e) {
            if (e.code === 60007) {
                toast('这个会话不存在或无权访问', 'error');
                await loadSessions();
                return;
            }
            toast(e.message, 'error');
        }
    }

    function renderHead() {
        if (!state.current) {
            headBox.innerHTML = '请选择左侧会话，或新建一个';
            return;
        }
        const session = state.current.session;
        const count = (state.current.messages || []).length;
        headBox.innerHTML = '<strong>' + escapeHtml(session.title || '新会话') + '</strong>'
            + '<span class="muted">当前显示 ' + count + ' 条 · 创建于 ' + formatTime(session.createTime) + '</span>';
    }

    function renderMessages() {
        const messages = (state.current && state.current.messages) || [];
        if (!messages.length) {
            messageBox.innerHTML = '<div class="empty">这个会话还没有消息，问点什么吧</div>';
            return;
        }
        let html = '';
        messages.forEach(function (message) {
            const isUser = message.role === 'USER';
            const text = message.content || '';
            const tooLong = text.length > COLLAPSE_LIMIT;
            const expanded = !!state.expanded[message.id];
            const shown = tooLong && !expanded ? text.substring(0, COLLAPSE_LIMIT) + '…' : text;

            html += '<div class="chat-msg ' + (isUser ? 'user' : 'assistant') + '">'
                + '<div class="chat-bubble">' + escapeHtml(shown)
                + (tooLong
                    ? '<div><button class="link-btn" data-expand="' + message.id + '">'
                      + (expanded ? '收起' : '展开全文') + '</button></div>'
                    : '')
                + '</div>'
                + '<div class="chat-msg-meta">' + escapeHtml(message.roleLabel || (isUser ? '我' : 'AI 客服'))
                + (isUser ? '' : ' · ' + (message.totalTokens || 0) + ' token · ' + (message.costMs || 0) + ' ms')
                + ' · ' + formatTime(message.createTime)
                + '</div></div>';
        });
        messageBox.innerHTML = html;

        messageBox.querySelectorAll('button[data-expand]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const key = btn.getAttribute('data-expand');
                state.expanded[key] = !state.expanded[key];
                renderMessages();
            });
        });
        messageBox.scrollTop = messageBox.scrollHeight;
    }

    /* ---------------- 发送 ---------------- */

    async function send() {
        if (state.sending) {
            return;
        }
        if (!state.current) {
            toast('请先新建或选择一个会话', 'error');
            return;
        }
        const content = input.value.trim();
        if (!content) {
            toast('先输入你的问题', 'error');
            return;
        }
        if (content.length > MAX_CHARS) {
            toast('消息最多 ' + MAX_CHARS + ' 字，当前 ' + content.length + ' 字', 'error');
            return;
        }

        state.sending = true;
        sendBtn.disabled = true;
        hint.textContent = 'AI 正在思考…';
        try {
            const result = await API.post('/api/ai/chat/sessions/' + state.current.session.id + '/messages',
                { content: content });
            input.value = '';
            state.current.messages = (state.current.messages || []).concat([result.userMessage, result.reply]);
            state.current.session.title = result.sessionTitle;
            state.current.session.messageCount = (state.current.session.messageCount || 0) + 2;
            renderHead();
            renderMessages();
            await refreshSessionList();
            hint.textContent = '今日还剩 ' + result.remainingQuota + ' 次提问';
        } catch (e) {
            hint.textContent = '';
            if (e.code === 60007) {
                toast('会话不存在或无权访问，已刷新列表', 'error');
                await loadSessions();
            } else {
                toast(e.message, 'error');
            }
        } finally {
            state.sending = false;
            sendBtn.disabled = false;
        }
    }

    /** 只刷新左侧列表（标题、时间会变），不打断当前对话 */
    async function refreshSessionList() {
        try {
            const data = await API.get('/api/ai/chat/sessions?page=1&size=50');
            state.sessions = data.list || [];
            renderSessions();
        } catch (e) {
            /* 列表刷新失败不影响这次对话 */
        }
    }

    /* ---------------- 新建 / 删除 ---------------- */

    async function createSession() {
        try {
            const session = await API.post('/api/ai/chat/sessions');
            await loadSessions(session.id);
            toast('已新建会话，可以直接提问了', 'success');
            input.focus();
        } catch (e) {
            toast(e.message, 'error');
        }
    }

    async function removeSession(id) {
        if (!confirm('确定删除这个会话吗？聊天记录会一并删除，且无法恢复。')) {
            return;
        }
        try {
            await API.del('/api/ai/chat/sessions/' + id);
            toast('已删除会话', 'success');
            const removingCurrent = state.current && state.current.session.id === id;
            // 删的不是当前会话时，记住当前会话 id，刷新后仍然停在这个会话上（否则会跳回第一个会话）
            const keepId = removingCurrent ? null : state.current.session.id;
            if (removingCurrent) {
                state.current = null;
                messageBox.innerHTML = '<div class="empty">会话已删除</div>';
                renderHead();
            }
            await loadSessions(keepId);
        } catch (e) {
            toast(e.message, 'error');
        }
    }

    /* ---------------- 事件绑定 ---------------- */

    newBtn.addEventListener('click', createSession);
    sendBtn.addEventListener('click', send);
    input.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            send();
        }
    });

    loadSessions();
})();
