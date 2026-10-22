/* ============================================================
   首页脚本：最新公开笔记 + 点赞排行预览
   ============================================================ */
(function () {
    const latestBox = document.getElementById('latest');
    const rankBox = document.getElementById('rank-preview');

    async function loadLatest() {
        try {
            const data = await API.get('/api/notes/public?page=1&size=4');
            if (!data.list.length) {
                latestBox.innerHTML = '<div class="empty">还没有公开笔记，登录后写下第一篇吧</div>';
                return;
            }
            let html = '';
            data.list.forEach(function (note) {
                html += noteCard(note, {});
            });
            latestBox.innerHTML = html;
        } catch (e) {
            latestBox.innerHTML = '<div class="empty">加载失败：' + escapeHtml(e.message) + '</div>';
        }
    }

    async function loadRank() {
        try {
            const data = await API.get('/api/notes/rank?page=1&size=5');
            if (!data.list.length) {
                rankBox.innerHTML = '<div class="empty">还没有公开笔记可排行</div>';
                return;
            }
            let html = '';
            data.list.forEach(function (note, index) {
                html += noteCard(note, { rank: index + 1, showLikeCount: true });
            });
            rankBox.innerHTML = html;
        } catch (e) {
            rankBox.innerHTML = '<div class="empty">加载失败：' + escapeHtml(e.message) + '</div>';
        }
    }

    loadLatest();
    loadRank();
})();
