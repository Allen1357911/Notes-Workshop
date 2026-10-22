/* ============================================================
   社区互动公共组件：关注按钮、作者卡片、评论渲染
   被 详情页 / 作者主页 / 热门作者 / 管理后台 共同使用。
   注意：auditTag（审核状态标签）在 common.js 里，因为 noteCard() 会用到它，
   而「我的笔记」等页面只加载 common.js。
   ============================================================ */

/** 关注 / 已关注按钮；未登录时点击会跳登录页 */
function followButtonHtml(userId, followed, size) {
    const cls = followed ? 'btn btn-sm' : 'btn btn-sm btn-primary';
    return '<button class="' + cls + '" data-follow="' + userId + '" data-followed="' + (followed ? '1' : '0') + '"'
        + (size ? ' style="font-size:' + size + '"' : '') + '>'
        + (followed ? '已关注' : '+ 关注') + '</button>';
}

/**
 * 给容器里的所有关注按钮绑定事件（事件委托，重新渲染后无需再次绑定）。
 * @param root     容器元素
 * @param onChanged 关注状态变化后的回调 (userId, followed)
 */
function bindFollowButtons(root, onChanged) {
    if (!root) {
        return;
    }
    root.addEventListener('click', async function (event) {
        const btn = event.target.closest('button[data-follow]');
        if (!btn) {
            return;
        }
        if (!API.isLogin()) {
            toast('请先登录后再关注', 'error');
            setTimeout(API.redirectToLogin, 700);
            return;
        }
        const userId = btn.getAttribute('data-follow');
        const followed = btn.getAttribute('data-followed') === '1';
        btn.disabled = true;
        try {
            if (followed) {
                await API.del('/api/users/' + userId + '/follow');
            } else {
                await API.post('/api/users/' + userId + '/follow');
            }
            toast(followed ? '已取消关注' : '关注成功', 'success');
            if (onChanged) {
                onChanged(userId, !followed);
            }
        } catch (e) {
            toast(e.message, 'error');
        } finally {
            btn.disabled = false;
        }
    });
}

/** 作者信息卡：头像、昵称、粉丝/关注/笔记数与关注按钮 */
function authorCardHtml(author) {
    const avatar = author.avatarUrl
        ? '<img class="avatar-lg" src="' + escapeHtml(author.avatarUrl) + '" alt="头像">'
        : '<span class="avatar-lg"></span>';
    let html = '<div class="author-card">' + avatar + '<div style="flex:1;min-width:160px">';
    html += '<div class="author-name"><a href="/author.html?id=' + author.id + '">'
        + escapeHtml(author.nickname || '未知用户') + '</a></div>';
    html += '<div class="author-stats">'
        + '<span>粉丝 <b>' + (author.followerCount || 0) + '</b></span>'
        + '<span>关注 <b>' + (author.followingCount || 0) + '</b></span>'
        + '<span>公开笔记 <b>' + (author.noteCount || 0) + '</b></span>'
        + '</div></div>';
    if (!author.self) {
        html += followButtonHtml(author.id, author.followed);
    } else {
        html += '<span class="tag">这是你自己</span>';
    }
    html += '</div>';
    return html;
}

/** 单条评论（含它下面的所有回复，逐级缩进） */
function commentHtml(comment, isReply) {
    const avatar = comment.avatarUrl
        ? '<img class="avatar-sm" src="' + escapeHtml(comment.avatarUrl) + '" alt="头像">'
        : '<span class="avatar-sm"></span>';
    const head = '<b>' + escapeHtml(comment.nickname || '匿名用户') + '</b>'
        + (isReply ? ' 回复' : '')
        + ' · ' + formatTime(comment.createTime);

    let html = '<div class="comment-item" data-comment="' + comment.id + '">' + avatar + '<div class="comment-body">';
    html += '<div class="comment-head">' + head + '</div>';
    html += '<div class="comment-text">' + escapeHtml(comment.content) + '</div>';
    html += '<div class="comment-actions">';
    if (API.isLogin()) {
        html += '<a data-reply="' + comment.id + '" data-reply-name="' + escapeHtml(comment.nickname || '') + '">回复</a>';
    }
    if (comment.deletable) {
        html += '<a class="danger" data-delete-comment="' + comment.id + '">删除</a>';
    }
    html += '</div>';

    if (comment.replies && comment.replies.length) {
        html += '<div class="comment-replies">';
        comment.replies.forEach(function (reply) {
            html += commentHtml(reply, true);
        });
        html += '</div>';
    }
    html += '</div></div>';
    return html;
}

/** 评论列表（顶层评论 + 各自的回复树） */
function commentListHtml(comments) {
    if (!comments || !comments.length) {
        return '<div class="empty">还没有评论，来说说你的想法吧</div>';
    }
    let html = '';
    comments.forEach(function (comment) {
        html += commentHtml(comment, false);
    });
    return html;
}
