/* ============================================================
   作者主页：作者信息卡（可关注）、粉丝/关注列表、TA 的公开笔记
   ============================================================ */
(function () {
    const authorId = new URLSearchParams(location.search).get('id');
    const cardBox = document.getElementById('author');
    const listBox = document.getElementById('list');
    const pagerBox = document.getElementById('pager');
    const followersBox = document.getElementById('followers');
    const followingBox = document.getElementById('following');
    const state = { page: 1, size: 10, total: 0 };
    let author = null;

    if (!authorId) {
        cardBox.innerHTML = '<div class="empty">地址里缺少用户 id</div>';
        return;
    }

    async function loadAuthor() {
        try {
            author = await API.get('/api/users/' + authorId, { silent401: true });
            cardBox.innerHTML = '<div class="card">' + authorCardHtml(author) + '</div>';
        } catch (e) {
            cardBox.innerHTML = '<div class="card"><div class="empty">' + escapeHtml(e.message) + '</div></div>';
            listBox.innerHTML = '';
            return;
        }
        loadFollowers();
        loadFollowing();
    }

    async function loadNotes(page) {
        state.page = page || 1;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const data = await API.get('/api/users/' + authorId + '/notes?page=' + state.page + '&size=' + state.size,
                { silent401: true });
            state.total = data.total;
            if (!data.list.length) {
                listBox.innerHTML = '<div class="card"><div class="empty">TA 还没有公开的笔记</div></div>';
            } else {
                let html = '';
                data.list.forEach(function (note) {
                    html += noteCard(note, { showLikeCount: false, showActions: false });
                });
                listBox.innerHTML = html;
            }
            renderPager(pagerBox, state, loadNotes);
        } catch (e) {
            listBox.innerHTML = '<div class="card"><div class="empty">加载失败：' + escapeHtml(e.message) + '</div></div>';
        }
    }

    function userListHtml(list, emptyText) {
        if (!list || !list.length) {
            return '<div class="empty">' + emptyText + '</div>';
        }
        let html = '';
        list.forEach(function (user) {
            const avatar = user.avatarUrl
                ? '<img class="avatar-sm" src="' + escapeHtml(user.avatarUrl) + '" alt="头像">'
                : '<span class="avatar-sm"></span>';
            html += '<div class="comment-item">' + avatar
                + '<div class="comment-body"><div class="comment-head">'
                + '<b><a href="/author.html?id=' + user.id + '">' + escapeHtml(user.nickname || '未知用户') + '</a></b>'
                + ' · 关注于 ' + formatTime(user.createTime) + '</div>'
                + '<div class="comment-actions">' + followButtonHtml(user.id, user.followed) + '</div>'
                + '</div></div>';
        });
        return html;
    }

    async function loadFollowers() {
        try {
            const data = await API.get('/api/users/' + authorId + '/followers?page=1&size=20', { silent401: true });
            followersBox.innerHTML = '<h3 class="section-title" style="font-size:15px">粉丝（' + data.total + '）</h3>'
                + userListHtml(data.list, '还没有粉丝');
        } catch (e) {
            followersBox.innerHTML = '';
        }
    }

    async function loadFollowing() {
        try {
            const data = await API.get('/api/users/' + authorId + '/following?page=1&size=20', { silent401: true });
            followingBox.innerHTML = '<h3 class="section-title" style="font-size:15px">关注（' + data.total + '）</h3>'
                + userListHtml(data.list, '还没有关注任何人');
        } catch (e) {
            followingBox.innerHTML = '';
        }
    }

    bindFollowButtons(document.body, function () {
        loadAuthor();   // 关注状态变化后刷新作者卡与列表
    });

    loadAuthor();
    loadNotes(1);
})();
