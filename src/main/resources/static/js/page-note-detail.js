/* ============================================================
   笔记详情页：正文、图书名、全部配图、AI 阅读辅助结果、点赞、编辑/删除入口，
   扩展部分：作者卡片（可关注）、审核状态提示与重新提交、评论区（支持多级回复）。
   ============================================================ */
(function () {
    const id = new URLSearchParams(location.search).get('id');
    const box = document.getElementById('detail');
    const commentBox = document.getElementById('comments');

    if (!id) {
        box.innerHTML = '<div class="card"><div class="empty">地址里缺少笔记 id</div></div>';
        return;
    }

    let note = null;
    let author = null;
    let comments = [];
    let commentPage = 1;
    const commentSize = 10;
    let commentTotal = 0;
    let replyTo = null;          // { id, nickname } 当前正在回复的评论

    async function load() {
        box.innerHTML = '<div class="card"><div class="empty">加载中…</div></div>';
        try {
            note = await API.get('/api/notes/' + id, { silent401: true });
            render();
            loadComments();
            loadAuthor();
        } catch (e) {
            box.innerHTML = '<div class="card"><div class="empty">'
                + escapeHtml(e.message) + '（<a href="/plaza.html">回到公开笔记广场</a>）</div></div>';
            commentBox.innerHTML = '';
        }
    }

    /** 作者信息（粉丝数、是否已关注）：公开接口，未登录也能取 */
    async function loadAuthor() {
        if (!note || !note.authorId) {
            return;
        }
        try {
            author = await API.get('/api/users/' + note.authorId, { silent401: true });
            renderAuthorCard();
        } catch (e) {
            /* 作者信息拿不到不影响正文阅读，静默处理 */
        }
    }

    function renderAuthorCard() {
        const holder = document.getElementById('author-card');
        if (holder && author) {
            holder.innerHTML = authorCardHtml(author);
        }
    }

    function auditBannerHtml() {
        if (!note.auditStatus || note.auditStatus === 'APPROVED') {
            return '';
        }
        let html = '';
        if (note.auditStatus === 'PENDING') {
            html = '<div class="audit-banner pending"><strong>这篇公开笔记正在等待管理员审核。</strong><br>'
                + '审核通过后其他同学才能在广场看到它；在通过之前，只有你自己和管理员能看到这篇笔记。</div>';
        } else if (note.auditStatus === 'REJECTED') {
            html = '<div class="audit-banner rejected"><strong>这篇笔记未通过审核。</strong><br>'
                + '审核意见：' + escapeHtml(note.auditRemark || '（管理员未填写原因）') + '<br>'
                + '<span class="muted">修改内容后会自动重新进入待审核队列，也可以直接点下面的按钮重新提交。</span>'
                + '<div class="row" style="margin-top:10px">'
                + '<button class="btn btn-sm btn-primary" id="resubmit-btn">重新提交审核</button>'
                + '<a class="btn btn-sm" href="/note-edit.html?id=' + note.id + '">去修改内容</a>'
                + '</div></div>';
        } else if (note.auditStatus === 'TAKEN_DOWN') {
            html = '<div class="audit-banner taken-down"><strong>这篇笔记已被管理员下架。</strong><br>'
                + '下架说明：' + escapeHtml(note.auditRemark || '（管理员未填写说明）') + '<br>'
                + '<span class="muted">下架期间其他同学看不到它，也不能再点赞或评论。修改内容后会重新进入待审核。</span></div>';
        }
        return html;
    }

    function render() {
        let html = '<div class="card">';
        html += auditBannerHtml();
        html += '<h1 class="note-title" style="font-size:21px">' + escapeHtml(note.bookName || '（未命名）') + '</h1>';
        html += '<div class="note-meta">'
            + '<span>' + authorHtml(note) + '</span>'
            + '<span>' + visibilityTag(note.visibility, note.visibilityLabel) + '</span>'
            + (note.auditStatus ? '<span>' + auditTag(note.auditStatus, note.auditStatusLabel) + '</span>' : '')
            + '<span>创建于 ' + formatTime(note.createTime) + '</span>'
            + '</div>';

        html += '<p class="detail-content">' + escapeHtml(note.content || '') + '</p>';

        if (note.images && note.images.length) {
            html += '<div class="detail-images">';
            note.images.forEach(function (url) {
                html += '<a href="' + escapeHtml(url) + '" target="_blank" title="点击查看原图">'
                    + '<img src="' + escapeHtml(url) + '" alt="笔记配图"></a>';
            });
            html += '</div>';
        }

        html += aiPanelHtml();

        html += '<div class="row" style="margin-top:16px">';
        html += '<button class="btn btn-like' + (note.liked ? ' liked' : '') + '" id="like-btn">'
            + (note.liked ? '已点赞' : '点赞') + ' <span id="like-count">' + (note.likeCount || 0) + '</span></button>';
        html += '<a class="btn" href="#comments">评论 ' + (note.commentCount || 0) + '</a>';
        if (note.editable) {
            html += '<a class="btn" href="/note-edit.html?id=' + note.id + '">编辑</a>';
            html += '<button class="btn btn-danger" id="delete-btn">删除</button>';
        }
        html += '</div>';

        html += '<div id="author-card" style="margin-top:18px"></div>';
        html += '<p class="muted" style="margin-top:14px">公开链接：/note-detail.html?id=' + note.id + '（公开笔记未登录也能打开）</p>';
        html += '</div>';

        box.innerHTML = html;
        bind();
        renderAuthorCard();
    }

    /* ---------------- AI 阅读辅助 ---------------- */

    const AI_CAPABILITIES = [
        { key: 'SUMMARY', text: '生成总结' },
        { key: 'AUDIENCE', text: '适宜人群' },
        { key: 'SCORE', text: '给笔记评分' }
    ];

    // 最近一次"生成但还没保存"的结果；保存/放弃后清空
    let aiPreview = null;

    function aiPanelHtml() {
        let html = '<div class="ai-box">';
        html += '<strong>AI 阅读辅助</strong> <span class="tag tag-pending" style="font-size:11px">仅供参考</span>';
        html += '<p class="muted" style="margin:6px 0 0">AI 只做总结、推荐与评价，不替你写笔记；生成结果不会自动保存，必须你点“保存到笔记”才会写入，且一篇笔记最多保存一种结果。</p>';

        // 已保存的结果
        if (note.aiResult) {
            html += '<div class="ai-saved">';
            html += '<div class="note-meta">'
                + '<span>已保存：' + escapeHtml(note.aiTypeLabel || note.aiType || '') + '</span>'
                + '<span>' + (note.aiPublic ? '对其他人公开' : '仅自己与管理员可见') + '</span>'
                + '<span>' + formatTime(note.aiConfirmedAt) + '</span>'
                + '</div>';
            html += '<div class="ai-text">' + escapeHtml(note.aiResult) + '</div>';
            if (note.editable) {
                html += '<div class="row" style="margin-top:8px">'
                    + '<button class="btn btn-sm" id="ai-toggle-public">'
                    + (note.aiPublic ? '改为仅自己可见' : '公开给其他人') + '</button>'
                    + '<button class="btn btn-sm btn-danger" id="ai-cancel">撤销 AI 结果</button>'
                    + '</div>';
            }
            html += '</div>';
        }

        // 生成入口（只有作者本人或管理员能看到 editable=true）
        if (note.editable) {
            html += '<div class="row" style="margin-top:10px">';
            AI_CAPABILITIES.forEach(function (cap) {
                html += '<button class="btn btn-sm" data-ai="' + cap.key + '">' + cap.text + '</button>';
            });
            html += '</div>';
            if (note.aiResult) {
                html += '<p class="muted" style="margin-top:6px">重新生成并保存会覆盖现在的 AI 结果。</p>';
            }
        } else if (!note.aiResult) {
            html += '<p class="muted" style="margin-top:8px">AI 辅助仅作者本人可使用。</p>';
        }

        if (aiPreview) {
            html += aiPreviewHtml();
        }
        html += '</div>';
        return html;
    }

    function aiPreviewHtml() {
        let html = '<div class="ai-preview">';
        html += '<div class="note-meta">'
            + '<span>预览：' + escapeHtml(aiPreview.capabilityLabel || '') + '</span>'
            + '<span>' + (aiPreview.totalTokens || 0) + ' tokens</span>'
            + '<span>' + (aiPreview.costMs || 0) + ' ms</span>'
            + '<span>今日还剩 ' + (aiPreview.remainingQuota || 0) + ' 次</span>'
            + '</div>';
        html += '<div class="ai-text">' + escapeHtml(aiPreview.text || '') + '</div>';
        html += '<p class="muted" style="margin:6px 0">' + escapeHtml(aiPreview.disclaimer || '') + '</p>';
        html += '<label class="muted" style="font-size:13px;display:block;margin-bottom:6px">'
            + '<input type="checkbox" id="ai-public-check"> 保存时对其他人公开（默认仅自己与管理员可见）</label>';
        html += '<div class="row">'
            + '<button class="btn btn-sm btn-primary" id="ai-save">保存到笔记</button>'
            + '<button class="btn btn-sm" id="ai-discard">放弃</button>'
            + '</div>';
        html += '</div>';
        return html;
    }

    async function generateAi(capability, button) {
        if (button) {
            button.disabled = true;
        }
        try {
            aiPreview = await API.post('/api/notes/' + id + '/ai/' + capability);
            render();
            toast('AI 已生成，确认后才会保存', 'success');
        } catch (e) {
            toast(e.message, 'error');
            if (button) {
                button.disabled = false;
            }
        }
    }

    async function saveAiResult() {
        if (!aiPreview) {
            return;
        }
        const checkbox = document.getElementById('ai-public-check');
        try {
            await API.put('/api/notes/' + id + '/ai-result', {
                aiLogId: aiPreview.aiLogId,
                aiPublic: !!(checkbox && checkbox.checked)
            });
            aiPreview = null;
            await refreshNote();
            toast('已保存到笔记', 'success');
        } catch (e) {
            toast(e.message, 'error');
        }
    }

    async function refreshNote() {
        note = await API.get('/api/notes/' + id, { silent401: true });
        render();
    }

    function bindAiPanel() {
        box.querySelectorAll('button[data-ai]').forEach(function (button) {
            button.addEventListener('click', function () {
                generateAi(button.getAttribute('data-ai'), button);
            });
        });
        const saveButton = document.getElementById('ai-save');
        if (saveButton) {
            saveButton.addEventListener('click', saveAiResult);
        }
        const discardButton = document.getElementById('ai-discard');
        if (discardButton) {
            discardButton.addEventListener('click', function () {
                aiPreview = null;
                render();
            });
        }
        const toggleButton = document.getElementById('ai-toggle-public');
        if (toggleButton) {
            toggleButton.addEventListener('click', async function () {
                try {
                    await API.put('/api/notes/' + id + '/ai-result/visibility', { aiPublic: !note.aiPublic });
                    await refreshNote();
                    toast(note.aiPublic ? '已公开给其他人' : '已改为仅自己可见', 'success');
                } catch (e) {
                    toast(e.message, 'error');
                }
            });
        }
        const cancelButton = document.getElementById('ai-cancel');
        if (cancelButton) {
            cancelButton.addEventListener('click', async function () {
                if (!confirm('确定撤销这篇笔记的 AI 结果吗？笔记正文不会受影响。')) {
                    return;
                }
                try {
                    await API.del('/api/notes/' + id + '/ai-result');
                    await refreshNote();
                    toast('已撤销', 'success');
                } catch (e) {
                    toast(e.message, 'error');
                }
            });
        }
    }

    function bind() {
        document.getElementById('like-btn').addEventListener('click', async function () {
            if (!API.isLogin()) {
                toast('请先登录后再点赞', 'error');
                setTimeout(API.redirectToLogin, 700);
                return;
            }
            const btn = this;
            btn.disabled = true;
            try {
                const count = note.liked
                    ? await API.del('/api/notes/' + id + '/like')
                    : await API.post('/api/notes/' + id + '/like');
                note.liked = !note.liked;
                note.likeCount = count;
                render();
                toast(note.liked ? '点赞成功' : '已取消点赞', 'success');
            } catch (e) {
                toast(e.message, 'error');
                btn.disabled = false;
            }
        });

        const delBtn = document.getElementById('delete-btn');
        if (delBtn) {
            delBtn.addEventListener('click', async function () {
                if (!confirm('确定删除这篇笔记吗？配图记录、点赞记录与评论会一并处理。')) {
                    return;
                }
                try {
                    await API.del('/api/notes/' + id);
                    toast('已删除', 'success');
                    setTimeout(function () { location.href = '/note-my.html'; }, 600);
                } catch (e) {
                    toast(e.message, 'error');
                }
            });
        }

        const resubmit = document.getElementById('resubmit-btn');
        if (resubmit) {
            resubmit.addEventListener('click', async function () {
                resubmit.disabled = true;
                try {
                    note = await API.put('/api/notes/' + id + '/resubmit');
                    render();
                    toast('已重新提交，等待管理员审核', 'success');
                } catch (e) {
                    toast(e.message, 'error');
                    resubmit.disabled = false;
                }
            });
        }

        bindAiPanel();
    }

    /* ---------------- 评论区 ---------------- */

    async function loadComments(page) {
        if (page) {
            commentPage = page;
        }
        commentBox.innerHTML = '<div class="empty">评论加载中…</div>';
        try {
            const data = await API.get('/api/notes/' + id + '/comments?page=' + commentPage + '&size=' + commentSize,
                { silent401: true });
            comments = data.list || [];
            commentTotal = data.total || 0;
            renderComments();
        } catch (e) {
            commentBox.innerHTML = '<div class="empty">' + escapeHtml(e.message) + '</div>';
        }
    }

    function renderComments() {
        let html = '<h2 class="section-title" style="margin-top:0">评论 <span class="muted">(' + commentTotal + ')</span></h2>';

        if (API.isLogin()) {
            html += '<div class="comment-form">';
            html += '<div class="reply-target" id="reply-target" style="display:none"></div>';
            html += '<textarea id="comment-input" maxlength="1000" placeholder="说说你的想法…（最多 1000 字）"></textarea>';
            html += '<div class="row" style="margin-top:8px">'
                + '<button class="btn btn-sm btn-primary" id="comment-submit">发表评论</button>'
                + '<button class="btn btn-sm" id="reply-cancel" style="display:none">取消回复</button>'
                + '</div></div>';
        } else {
            html += '<p class="muted">登录后可以发表评论与回复（<a href="/login.html">去登录</a>）。</p>';
        }

        html += '<div style="margin-top:14px">' + commentListHtml(comments) + '</div>';
        html += '<div class="pager" id="comment-pager"></div>';
        commentBox.innerHTML = html;

        if (API.isLogin()) {
            document.getElementById('comment-submit').addEventListener('click', submitComment);
            document.getElementById('reply-cancel').addEventListener('click', function () {
                replyTo = null;
                updateReplyHint();
            });
        }
        commentBox.querySelectorAll('a[data-reply]').forEach(function (link) {
            link.addEventListener('click', function () {
                replyTo = {
                    id: link.getAttribute('data-reply'),
                    nickname: link.getAttribute('data-reply-name')
                };
                updateReplyHint();
                const input = document.getElementById('comment-input');
                input.focus();
            });
        });
        commentBox.querySelectorAll('a[data-delete-comment]').forEach(function (link) {
            link.addEventListener('click', function () {
                deleteComment(link.getAttribute('data-delete-comment'));
            });
        });

        renderPager(document.getElementById('comment-pager'), {
            page: commentPage, size: commentSize, total: commentTotal
        }, function (target) {
            loadComments(target);
            document.getElementById('comments').scrollIntoView({ behavior: 'smooth' });
        });
    }

    function updateReplyHint() {
        const hint = document.getElementById('reply-target');
        const cancel = document.getElementById('reply-cancel');
        if (!hint) {
            return;
        }
        if (replyTo) {
            hint.style.display = 'block';
            hint.textContent = '正在回复 @' + (replyTo.nickname || '') + '（回复会显示在这条评论下面）';
            if (cancel) {
                cancel.style.display = 'inline-block';
            }
        } else {
            hint.style.display = 'none';
            hint.textContent = '';
            if (cancel) {
                cancel.style.display = 'none';
            }
        }
    }

    async function submitComment() {
        const input = document.getElementById('comment-input');
        const content = (input.value || '').trim();
        if (!content) {
            toast('评论内容不能为空', 'error');
            return;
        }
        const btn = document.getElementById('comment-submit');
        btn.disabled = true;
        try {
            await API.post('/api/notes/' + id + '/comments', {
                content: content,
                parentId: replyTo ? Number(replyTo.id) : null
            });
            input.value = '';
            replyTo = null;
            updateReplyHint();
            toast('发表成功', 'success');
            await loadComments(1);
            note.commentCount = commentTotal;
        } catch (e) {
            toast(e.message, 'error');
        } finally {
            btn.disabled = false;
        }
    }

    async function deleteComment(commentId) {
        if (!confirm('确定删除这条评论吗？它下面的回复也会一起删除。')) {
            return;
        }
        try {
            await API.del('/api/comments/' + commentId);
            toast('已删除', 'success');
            await loadComments();
        } catch (e) {
            toast(e.message, 'error');
        }
    }

    // 关注按钮用事件委托绑在 #detail 上，只绑一次：
    // 如果放在 bind() 里，每次重新渲染都会再绑一个监听器，点一次会发出多次请求。
    bindFollowButtons(box, function () {
        loadAuthor();
    });

    load();
})();
