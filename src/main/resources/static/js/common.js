/* ============================================================
   公共函数：导航栏、转义、时间格式化、提示、分页、笔记本卡片渲染
   ============================================================ */

/** 把用户输入转义后再插入 HTML，避免笔记内容里的脚本被执行（XSS 防护） */
function escapeHtml(text) {
    if (text === null || text === undefined) {
        return '';
    }
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/** 后端返回的是 ISO 时间字符串，这里转成 yyyy-MM-dd HH:mm */
function formatTime(value) {
    if (!value) {
        return '';
    }
    const text = String(value).replace('T', ' ');
    return text.length >= 16 ? text.substring(0, 16) : text;
}

/** 右上角轻提示 */
function toast(message, type) {
    let box = document.getElementById('toast');
    if (!box) {
        box = document.createElement('div');
        box.id = 'toast';
        box.className = 'toast';
        document.body.appendChild(box);
    }
    box.textContent = message;
    box.className = 'toast show' + (type ? ' ' + type : '');
    clearTimeout(box._timer);
    box._timer = setTimeout(function () {
        box.className = 'toast' + (type ? ' ' + type : '');
    }, 2600);
}

/** 可见性标签 */
function visibilityTag(visibility, label) {
    const text = label || (visibility === 'PUBLIC' ? '公开' : '私有');
    const cls = visibility === 'PUBLIC' ? 'tag-public' : 'tag-private';
    return '<span class="tag ' + cls + '">' + escapeHtml(text) + '</span>';
}

/**
 * 审核状态标签。
 * 放在 common.js 而不是 social.js：noteCard() 会用到它，而「我的笔记」等页面只加载 common.js，
 * 放到 social.js 会导致这些页面渲染时报 "auditTag is not defined"。
 */
function auditTag(auditStatus, label) {
    if (!auditStatus) {
        return '';
    }
    const map = {
        PENDING: { text: '待审核', cls: 'tag-pending' },
        APPROVED: { text: '已通过', cls: 'tag-approved' },
        REJECTED: { text: '已驳回', cls: 'tag-rejected' },
        TAKEN_DOWN: { text: '已下架', cls: 'tag-taken-down' }
    };
    const item = map[auditStatus] || { text: auditStatus, cls: '' };
    return '<span class="tag ' + item.cls + '">' + escapeHtml(label || item.text) + '</span>';
}

/** 作者信息行 */
function authorHtml(note) {
    const avatar = note.authorAvatarUrl
        ? '<img class="nav-avatar" src="' + escapeHtml(note.authorAvatarUrl) + '" alt="头像">'
        : '<span class="nav-avatar"></span>';
    return avatar + '<span>' + escapeHtml(note.authorNickname || '未知用户') + '</span>';
}

/**
 * 渲染一条笔记卡片。
 * @param note 列表项数据（NoteListVO 或 NoteRankVO）
 * @param opts { rank: 序号, showActions: 是否显示编辑/删除, likeCount: 是否显示点赞数 }
 */
function noteCard(note, opts) {
    const o = opts || {};
    let html = '<div class="card">';
    if (o.rank) {
        html += '<div class="note-item">';
        html += '<div class="rank-no' + (o.rank <= 3 ? ' top' : '') + '">' + o.rank + '</div>';
        html += '<div class="note-main">';
    } else {
        html += '<div class="note-item"><div class="note-main">';
    }

    html += '<h3 class="note-title"><a href="/note-detail.html?id=' + note.id + '">'
        + escapeHtml(note.bookName || '（未命名）') + '</a></h3>';
    html += '<p class="note-content">' + escapeHtml(note.content || '') + '</p>';

    html += '<div class="note-meta">';
    html += '<span>' + authorHtml(note) + '</span>';
    if (note.visibility) {
        html += '<span>' + visibilityTag(note.visibility, note.visibilityLabel) + '</span>';
    }
    if (note.auditStatus) {
        html += '<span>' + auditTag(note.auditStatus, note.auditStatusLabel) + '</span>';
    }
    if (o.showLikeCount) {
        html += '<span>👍 ' + (note.likeCount || 0) + '</span>';
    }
    if (note.images && note.images.length) {
        html += '<span>🖼 ' + note.images.length + ' 张配图</span>';
    }
    html += '<span>' + formatTime(note.createTime) + '</span>';
    html += '</div>';

    if (note.images && note.images.length) {
        html += '<div class="note-thumbs">';
        note.images.slice(0, 6).forEach(function (url) {
            html += '<img src="' + escapeHtml(url) + '" alt="配图">';
        });
        html += '</div>';
    }

    if (o.showActions) {
        html += '<div class="row" style="margin-top:10px">'
            + '<a class="btn btn-sm" href="/note-edit.html?id=' + note.id + '">编辑</a>';
        if (note.auditStatus === 'REJECTED') {
            html += '<button class="btn btn-sm btn-primary" data-resubmit="' + note.id + '">重新提交审核</button>';
        }
        html += '<button class="btn btn-sm btn-danger" data-delete="' + note.id + '">删除</button>'
            + '</div>';
        if (note.auditStatus === 'REJECTED') {
            html += '<p class="muted" style="margin-top:8px">这篇笔记被驳回，点"重新提交审核"或修改内容后会自动重新进入待审核队列。</p>';
        } else if (note.auditStatus === 'PENDING') {
            html += '<p class="muted" style="margin-top:8px">正在等待管理员审核，通过后其他同学才能在广场看到。</p>';
        } else if (note.auditStatus === 'TAKEN_DOWN') {
            html += '<p class="muted" style="margin-top:8px">这篇笔记已被下架，其他同学看不到；修改内容后会重新进入待审核。</p>';
        }
    }

    html += '</div></div></div>';
    return html;
}

/** 渲染分页控件 */
function renderPager(container, state, onGo) {
    if (!container) {
        return;
    }
    const totalPages = Math.max(1, Math.ceil(state.total / state.size));
    if (state.total === 0) {
        container.innerHTML = '';
        return;
    }
    container.innerHTML =
        '<button class="btn btn-sm" data-go="1"' + (state.page <= 1 ? ' disabled' : '') + '>首页</button>' +
        '<button class="btn btn-sm" data-go="' + (state.page - 1) + '"' + (state.page <= 1 ? ' disabled' : '') + '>上一页</button>' +
        '<span class="info">第 ' + state.page + ' / ' + totalPages + ' 页，共 ' + state.total + ' 条</span>' +
        '<button class="btn btn-sm" data-go="' + (state.page + 1) + '"' + (state.page >= totalPages ? ' disabled' : '') + '>下一页</button>' +
        '<button class="btn btn-sm" data-go="' + totalPages + '"' + (state.page >= totalPages ? ' disabled' : '') + '>末页</button>';

    container.querySelectorAll('button[data-go]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const target = parseInt(btn.getAttribute('data-go'), 10);
            if (target >= 1 && target <= totalPages && target !== state.page) {
                onGo(target);
            }
        });
    });
}

/** 渲染顶部导航（各页面共用），并高亮当前页 */
function renderNav() {
    const box = document.getElementById('nav');
    if (!box) {
        return;
    }
    const current = document.body.dataset.page || '';
    const user = API.getUser();

    const links = [
        { key: 'index', href: '/index.html', text: '首页' },
        { key: 'plaza', href: '/plaza.html', text: '公开笔记' },
        { key: 'rank', href: '/rank.html', text: '点赞排行' },
        { key: 'hot-authors', href: '/hot-authors.html', text: '热门作者' }
    ];
    if (API.isLogin()) {
        links.push({ key: 'feed', href: '/following.html', text: '关注流' });
        links.push({ key: 'my', href: '/note-my.html', text: '我的笔记' });
        links.push({ key: 'likes', href: '/likes.html', text: '我赞过的' });
        links.push({ key: 'chat', href: '/chat.html', text: 'AI 客服' });
    }
    if (user && user.role === 'ADMIN') {
        links.push({ key: 'admin', href: '/admin.html', text: '管理后台' });
    }

    let html = '<div class="nav-inner">';
    html += '<a class="nav-logo" href="/index.html">📖 笔记工坊</a>';
    html += '<nav class="nav-links">';
    links.forEach(function (l) {
        html += '<a href="' + l.href + '"' + (l.key === current ? ' class="active"' : '') + '>' + l.text + '</a>';
    });
    html += '</nav>';

    html += '<div class="nav-user">';
    if (API.isLogin()) {
        html += '<a class="btn btn-sm btn-primary" href="/note-edit.html">写笔记</a>';
        html += '<a href="/profile.html" title="个人资料">' + authorHtml({ authorAvatarUrl: user && user.avatarUrl, authorNickname: (user && user.nickname) || '我' }) + '</a>';
        if (user && user.role === 'ADMIN') {
            html += '<span class="tag tag-admin">管理员</span>';
        }
        html += '<button class="btn btn-sm" id="nav-logout">退出</button>';
    } else {
        html += '<a class="btn btn-sm" href="/login.html">登录</a>';
        html += '<a class="btn btn-sm btn-primary" href="/register.html">注册</a>';
    }
    html += '</div></div>';
    box.innerHTML = html;

    const logout = document.getElementById('nav-logout');
    if (logout) {
        logout.addEventListener('click', function () {
            API.clearAuth();
            toast('已退出登录', 'success');
            setTimeout(function () { location.href = '/index.html'; }, 400);
        });
    }
}

/** 需要登录的页面调用它：未登录直接跳登录页 */
function requireLogin() {
    if (!API.isLogin()) {
        API.redirectToLogin();
        return false;
    }
    return true;
}

document.addEventListener('DOMContentLoaded', renderNav);
