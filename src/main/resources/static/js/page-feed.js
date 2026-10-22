/* ============================================================
   关注流：我关注的人发布的公开笔记（按发布时间倒序）
   ============================================================ */
(function () {
    const listBox = document.getElementById('list');
    const pagerBox = document.getElementById('pager');
    const state = { page: 1, size: 10, total: 0 };

    if (!requireLogin()) {
        return;
    }

    async function load(page) {
        state.page = page || 1;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const data = await API.get('/api/feed/following?page=' + state.page + '&size=' + state.size);
            state.total = data.total;
            if (!data.list.length) {
                listBox.innerHTML = '<div class="card"><div class="empty">'
                    + '关注流还是空的：去<a href="/hot-authors.html">热门作者</a>或'
                    + '<a href="/plaza.html">公开笔记广场</a>关注几位同学吧</div></div>';
            } else {
                let html = '';
                data.list.forEach(function (note) {
                    html += noteCard(note, { showLikeCount: false, showActions: false });
                });
                listBox.innerHTML = html;
            }
            renderPager(pagerBox, state, load);
        } catch (e) {
            if (e.code === 401) {
                return;
            }
            listBox.innerHTML = '<div class="card"><div class="empty">加载失败：' + escapeHtml(e.message) + '</div></div>';
        }
    }

    load(1);
})();
