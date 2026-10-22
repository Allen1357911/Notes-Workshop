/* ============================================================
   热门作者：按粉丝数排行的作者列表（带关注按钮）
   ============================================================ */
(function () {
    const listBox = document.getElementById('list');
    const pagerBox = document.getElementById('pager');
    const state = { page: 1, size: 10, total: 0 };

    async function load(page) {
        state.page = page || 1;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const data = await API.get('/api/authors/hot?page=' + state.page + '&size=' + state.size,
                { silent401: true });
            state.total = data.total;
            if (!data.list.length) {
                listBox.innerHTML = '<div class="card"><div class="empty">还没有人获得关注，先去关注几位同学吧</div></div>';
            } else {
                let html = '<div class="card">';
                data.list.forEach(function (author, index) {
                    const no = (state.page - 1) * state.size + index + 1;
                    const avatar = author.avatarUrl
                        ? '<img class="avatar-lg" src="' + escapeHtml(author.avatarUrl) + '" alt="头像">'
                        : '<span class="avatar-lg"></span>';
                    html += '<div class="hot-author">'
                        + '<div class="rank-no' + (no <= 3 ? ' top' : '') + '">' + no + '</div>'
                        + avatar
                        + '<div style="flex:1;min-width:140px">'
                        + '<div class="author-name"><a href="/author.html?id=' + author.id + '">'
                        + escapeHtml(author.nickname || '未知用户') + '</a></div>'
                        + '<div class="author-stats"><span>粉丝 <b>' + (author.followerCount || 0) + '</b></span>'
                        + '<span>公开笔记 <b>' + (author.noteCount || 0) + '</b></span></div>'
                        + '</div>'
                        + followButtonHtml(author.id, author.followed)
                        + '</div>';
                });
                html += '</div>';
                listBox.innerHTML = html;
            }
            renderPager(pagerBox, state, load);
        } catch (e) {
            listBox.innerHTML = '<div class="card"><div class="empty">加载失败：' + escapeHtml(e.message) + '</div></div>';
        }
    }

    bindFollowButtons(document.body, function () {
        load(state.page);   // 关注后粉丝数会变，重新拉一次
    });

    load(1);
})();
