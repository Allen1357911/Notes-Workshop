/* ============================================================
   列表页通用脚本：公开笔记广场 / 点赞排行 / 我赞过的 / 我的笔记
   页面通过 <body data-list-mode="public|rank|likes|my"> 指定模式。
   ============================================================ */
(function () {
    const mode = document.body.dataset.listMode;
    const CONFIG = {
        public: {
            url: '/api/notes/public', needLogin: false, showLikeCount: false,
            showActions: false, rankNo: false, empty: '还没有公开笔记，去写第一篇吧'
        },
        rank: {
            url: '/api/notes/rank', needLogin: false, showLikeCount: true,
            showActions: false, rankNo: true, empty: '还没有可排行的公开笔记'
        },
        likes: {
            url: '/api/likes/my', needLogin: true, showLikeCount: true,
            showActions: false, rankNo: false, empty: '你还没有点赞过任何笔记'
        },
        my: {
            url: '/api/notes/my', needLogin: true, showLikeCount: false,
            showActions: true, rankNo: false, empty: '你还没有写过笔记，点右上角"写笔记"开始吧'
        }
    };
    const cfg = CONFIG[mode];
    const listBox = document.getElementById('list');
    const pagerBox = document.getElementById('pager');
    const state = { page: 1, size: 10, total: 0 };

    if (!cfg) {
        listBox.innerHTML = '<div class="empty">页面配置错误：未知的列表模式</div>';
        return;
    }
    if (cfg.needLogin && !requireLogin()) {
        return;   // 未登录，已跳转到登录页
    }

    async function load(page) {
        state.page = page || 1;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const data = await API.get(cfg.url + '?page=' + state.page + '&size=' + state.size);
            state.total = data.total;
            if (!data.list.length) {
                listBox.innerHTML = '<div class="card"><div class="empty">' + cfg.empty + '</div></div>';
            } else {
                let html = '';
                data.list.forEach(function (note, index) {
                    const no = cfg.rankNo ? (state.page - 1) * state.size + index + 1 : 0;
                    html += noteCard(note, {
                        rank: no,
                        showLikeCount: cfg.showLikeCount,
                        showActions: cfg.showActions
                    });
                });
                listBox.innerHTML = html;
            }
            renderPager(pagerBox, state, load);
        } catch (e) {
            if (e.code === 401) {
                return;   // 已跳登录页
            }
            listBox.innerHTML = '<div class="card"><div class="empty">加载失败：' + escapeHtml(e.message) + '</div></div>';
        }
    }

    // 我的笔记：重新提交审核（被驳回的笔记可以不改内容直接重提）
    listBox.addEventListener('click', async function (ev) {
        const resubmit = ev.target.closest('button[data-resubmit]');
        if (!resubmit) {
            return;
        }
        const id = resubmit.getAttribute('data-resubmit');
        resubmit.disabled = true;
        try {
            await API.put('/api/notes/' + id + '/resubmit');
            toast('已重新提交，等待管理员审核', 'success');
            load(state.page);
        } catch (e) {
            toast(e.message, 'error');
            resubmit.disabled = false;
        }
    });

    // 我的笔记：删除按钮
    listBox.addEventListener('click', async function (ev) {
        const btn = ev.target.closest('button[data-delete]');
        if (!btn) {
            return;
        }
        const id = btn.getAttribute('data-delete');
        if (!confirm('确定删除这篇笔记吗？配图记录、点赞记录与评论会一并清理。')) {
            return;
        }
        try {
            await API.del('/api/notes/' + id);
            toast('已删除', 'success');
            load(state.page);
        } catch (e) {
            toast(e.message, 'error');
        }
    });

    load(1);
})();
