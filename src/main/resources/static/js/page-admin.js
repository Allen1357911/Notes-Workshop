/* ============================================================
   管理后台：待审核队列、内容管理、用户管理、评论管理
   接口全部挂在 /api/admin/** 下，后端拦截器会校验管理员身份（未登录 401 / 非管理员 403）。
   ============================================================ */
(function () {
    const box = document.getElementById('admin');
    const user = API.getUser();
    const state = { tab: 'pending', page: 1, size: 10, total: 0, status: '' };

    if (!requireLogin()) {
        return;
    }
    if (!user || user.role !== 'ADMIN') {
        box.innerHTML = '<div class="card"><div class="empty">'
            + '这个页面只有管理员能打开。如果你刚被提升为管理员，请退出后重新登录再试。<br><br>'
            + '<a class="btn btn-sm" href="/index.html">回到首页</a></div></div>';
        return;
    }

    const TABS = [
        { key: 'pending', text: '待审核' },
        { key: 'notes', text: '内容管理' },
        { key: 'users', text: '用户管理' },
        { key: 'comments', text: '评论管理' },
        { key: 'logs', text: '操作日志' },
        { key: 'ai', text: 'AI 统计' }
    ];

    function renderShell() {
        let html = '<div class="tabs">';
        TABS.forEach(function (tab) {
            html += '<button class="btn btn-sm' + (tab.key === state.tab ? ' active' : '') + '" data-tab="' + tab.key + '">'
                + tab.text + '</button>';
        });
        html += '</div>';

        if (state.tab === 'users') {
            html += '<div class="row" style="margin-bottom:10px">'
                + '<input id="keyword" class="input" style="max-width:240px" placeholder="按用户名或昵称搜索">'
                + '<select id="status-filter" class="input" style="max-width:150px">'
                + '<option value="">全部状态</option><option value="1">只看正常</option><option value="0">只看已禁用</option>'
                + '</select>'
                + '<button class="btn btn-sm btn-primary" id="search-btn">搜索</button></div>';
        }
        if (state.tab === 'notes') {
            html += '<div class="row" style="margin-bottom:10px">'
                + '<select id="audit-filter" class="input" style="max-width:190px">'
                + '<option value="">全部审核状态</option>'
                + '<option value="PENDING">待审核</option><option value="APPROVED">已通过</option>'
                + '<option value="REJECTED">已驳回</option><option value="TAKEN_DOWN">已下架</option>'
                + '</select>'
                + '<select id="visibility-filter" class="input" style="max-width:150px">'
                + '<option value="">全部可见性</option><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option>'
                + '</select>'
                + '<button class="btn btn-sm btn-primary" id="filter-btn">筛选</button></div>';
        }

        if (state.tab === 'logs') {
            html += '<div class="row" style="margin-bottom:10px">'
                + '<input id="log-username" class="input" style="max-width:190px" placeholder="按操作人用户名搜索">'
                + '<select id="log-module" class="input" style="max-width:220px">'
                + '<option value="">全部模块</option>'
                + '<option value="认证">认证</option>'
                + '<option value="用户">用户</option>'
                + '<option value="读书笔记">读书笔记</option>'
                + '<option value="笔记审核">笔记审核</option>'
                + '<option value="社区互动">社区互动</option>'
                + '<option value="管理员-用户管理">管理员-用户管理</option>'
                + '<option value="管理员-内容审核">管理员-内容审核</option>'
                + '<option value="管理员-评论管理">管理员-评论管理</option>'
                + '</select>'
                + '<select id="log-success" class="input" style="max-width:160px">'
                + '<option value="">全部结果</option>'
                + '<option value="true">只看成功</option>'
                + '<option value="false">只看失败</option>'
                + '</select>'
                + '<button class="btn btn-sm btn-primary" id="log-search-btn">查询</button></div>';
        }

        html += '<div id="admin-list"></div><div class="pager" id="admin-pager"></div>';
        box.innerHTML = html;

        box.querySelectorAll('button[data-tab]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                state.tab = btn.getAttribute('data-tab');
                state.page = 1;
                renderShell();
                load();
            });
        });
        const searchBtn = document.getElementById('search-btn');
        if (searchBtn) {
            searchBtn.addEventListener('click', function () {
                state.page = 1;
                load();
            });
        }
        const filterBtn = document.getElementById('filter-btn');
        if (filterBtn) {
            filterBtn.addEventListener('click', function () {
                state.page = 1;
                load();
            });
        }
        const logSearchBtn = document.getElementById('log-search-btn');
        if (logSearchBtn) {
            logSearchBtn.addEventListener('click', function () {
                state.page = 1;
                load();
            });
        }
    }

    function buildUrl() {
        let url = '';
        if (state.tab === 'pending') {
            url = '/api/admin/notes/pending';
        } else if (state.tab === 'notes') {
            const audit = document.getElementById('audit-filter').value;
            const visibility = document.getElementById('visibility-filter').value;
            url = '/api/admin/notes';
            const params = [];
            if (audit) {
                params.push('auditStatus=' + audit);
            }
            if (visibility) {
                params.push('visibility=' + visibility);
            }
            if (params.length) {
                url += '?' + params.join('&');
            }
        } else if (state.tab === 'users') {
            const keyword = (document.getElementById('keyword').value || '').trim();
            const status = document.getElementById('status-filter').value;
            const params = [];
            if (keyword) {
                params.push('keyword=' + encodeURIComponent(keyword));
            }
            if (status !== '') {
                params.push('status=' + status);
            }
            url = '/api/admin/users' + (params.length ? '?' + params.join('&') : '');
        } else if (state.tab === 'logs') {
            const username = (document.getElementById('log-username').value || '').trim();
            const module = document.getElementById('log-module').value;
            const success = document.getElementById('log-success').value;
            const params = [];
            if (username) {
                params.push('username=' + encodeURIComponent(username));
            }
            if (module) {
                params.push('module=' + encodeURIComponent(module));
            }
            if (success !== '') {
                params.push('success=' + success);
            }
            url = '/api/admin/logs' + (params.length ? '?' + params.join('&') : '');
        } else {
            url = '/api/admin/comments';
        }
        url += (url.indexOf('?') >= 0 ? '&' : '?') + 'page=' + state.page + '&size=' + state.size;
        return url;
    }

    async function load(page) {
        if (page) {
            state.page = page;
        }
        if (state.tab === 'ai') {
            return loadAiStats();
        }
        const listBox = document.getElementById('admin-list');
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const data = await API.get(buildUrl());
            state.total = data.total;
            if (!data.list.length) {
                listBox.innerHTML = '<div class="card"><div class="empty">没有数据</div></div>';
            } else {
                listBox.innerHTML = '<div class="card"><table class="admin-table">'
                    + '<thead>' + headHtml() + '</thead><tbody>'
                    + data.list.map(rowHtml).join('')
                    + '</tbody></table></div>';
            }
            renderPager(document.getElementById('admin-pager'), state, load);
        } catch (e) {
            listBox.innerHTML = '<div class="card"><div class="empty">加载失败：' + escapeHtml(e.message) + '</div></div>';
        }
    }

    /** AI 调用统计：汇总卡片 + 按能力 + 最近 7 天（不是分页列表，单独渲染） */
    async function loadAiStats() {
        const listBox = document.getElementById('admin-list');
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const s = await API.get('/api/admin/ai-stats');
            let html = '<div class="card"><h3 class="section-title" style="margin-top:0">总览</h3>'
                + '<div class="author-stats">'
                + '<span>总调用 <b>' + (s.totalCalls || 0) + '</b></span>'
                + '<span>成功 <b>' + (s.successCalls || 0) + '</b></span>'
                + '<span>失败 <b>' + (s.failedCalls || 0) + '</b></span>'
                + '<span>成功率 <b>' + (s.successRate || 0) + '%</b></span>'
                + '<span>总 token <b>' + (s.totalTokens || 0) + '</b></span>'
                + '<span>平均耗时 <b>' + (s.avgCostMs || 0) + ' ms</b></span>'
                + '<span>最大耗时 <b>' + (s.maxCostMs || 0) + ' ms</b></span>'
                + '<span>被确认保存 <b>' + (s.confirmedCalls || 0) + '</b></span>'
                + '<span>模型 <b>' + escapeHtml(s.model || '') + (s.mock ? '（模拟）' : '') + '</b></span>'
                + '</div></div>';

            html += '<div class="card" style="margin-top:12px"><h3 class="section-title" style="margin-top:0">按能力</h3>'
                + '<table class="admin-table"><thead><tr><th>能力</th><th>调用次数</th><th>token</th><th>平均耗时</th></tr></thead><tbody>';
            const caps = s.byCapability || [];
            caps.forEach(function (item) {
                html += '<tr><td>' + escapeHtml(item.label || item.capability) + '</td>'
                    + '<td>' + (item.calls || 0) + '</td>'
                    + '<td>' + (item.tokens || 0) + '</td>'
                    + '<td>' + (item.avgCostMs || 0) + ' ms</td></tr>';
            });
            if (!caps.length) {
                html += '<tr><td colspan="4" class="muted">暂无调用记录</td></tr>';
            }
            html += '</tbody></table></div>';

            html += '<div class="card" style="margin-top:12px"><h3 class="section-title" style="margin-top:0">最近 7 天</h3>'
                + '<table class="admin-table"><thead><tr><th>日期</th><th>调用次数</th><th>token</th></tr></thead><tbody>';
            (s.recentDays || []).forEach(function (day) {
                html += '<tr><td>' + escapeHtml(day.day) + '</td><td>' + (day.calls || 0) + '</td><td>' + (day.tokens || 0) + '</td></tr>';
            });
            html += '</tbody></table></div>';

            listBox.innerHTML = html;
            const pager = document.getElementById('admin-pager');
            if (pager) {
                pager.innerHTML = '';
            }
        } catch (e) {
            listBox.innerHTML = '<div class="card"><div class="empty">加载失败：' + escapeHtml(e.message) + '</div></div>';
        }
    }

    function headHtml() {
        if (state.tab === 'pending' || state.tab === 'notes') {
            return '<tr><th>ID</th><th>笔记</th><th>作者</th><th>可见性 / 审核</th><th>时间</th><th>操作</th></tr>';
        }
        if (state.tab === 'users') {
            return '<tr><th>ID</th><th>用户名</th><th>昵称</th><th>角色</th><th>状态</th><th>操作</th></tr>';
        }
        if (state.tab === 'logs') {
            return '<tr><th>时间</th><th>操作人</th><th>模块</th><th>动作</th><th>对象</th><th>结果</th><th>耗时</th><th>参数摘要 / 请求</th></tr>';
        }
        return '<tr><th>ID</th><th>所属笔记</th><th>评论人</th><th>内容</th><th>时间</th><th>操作</th></tr>';
    }

    function rowHtml(item) {
        if (state.tab === 'logs') {
            return logRowHtml(item);
        }
        if (state.tab === 'pending' || state.tab === 'notes') {
            return '<tr><td>' + item.id + '</td>'
                + '<td><div class="cell-clip"><a href="/note-detail.html?id=' + item.id + '">'
                + escapeHtml(item.bookName || '') + '</a></div>'
                + '<div class="cell-clip muted">' + escapeHtml(item.content || '') + '</div></td>'
                + '<td>' + escapeHtml(item.authorNickname || '') + '</td>'
                + '<td>' + visibilityTag(item.visibility, item.visibilityLabel)
                + ' ' + auditTag(item.auditStatus, item.auditStatusLabel)
                + (item.auditRemark ? '<div class="muted">' + escapeHtml(item.auditRemark) + '</div>' : '')
                + '</td>'
                + '<td>' + formatTime(item.createTime) + '</td>'
                + '<td>' + noteActions(item) + '</td></tr>';
        }
        if (state.tab === 'users') {
            const statusTag = item.status === 1
                ? '<span class="tag tag-approved">正常</span>'
                : '<span class="tag tag-rejected">已禁用</span>';
            const action = item.status === 1
                ? '<button class="btn btn-sm btn-danger" data-disable="' + item.id + '">禁用</button>'
                : '<button class="btn btn-sm btn-primary" data-enable="' + item.id + '">启用</button>';
            return '<tr><td>' + item.id + '</td><td>' + escapeHtml(item.username || '') + '</td>'
                + '<td>' + escapeHtml(item.nickname || '') + '</td>'
                + '<td>' + escapeHtml(item.roleLabel || item.role || '') + '</td>'
                + '<td>' + statusTag + '</td><td>' + action + '</td></tr>';
        }
        return '<tr><td>' + item.id + '</td>'
            + '<td><div class="cell-clip"><a href="/note-detail.html?id=' + item.noteId + '">'
            + escapeHtml(item.noteBookName || '') + '</a></div></td>'
            + '<td>' + escapeHtml(item.userNickname || '') + '</td>'
            + '<td><div class="cell-clip">' + escapeHtml(item.content || '') + '</div>'
            + (item.rootId !== item.id ? '<div class="muted">回复（评论树 #' + item.rootId + '）</div>' : '')
            + '</td>'
            + '<td>' + formatTime(item.createTime) + '</td>'
            + '<td><button class="btn btn-sm btn-danger" data-del-comment="' + item.id + '">删除</button></td></tr>';
    }

    /** 操作日志行：时间 / 操作人 / 模块 / 动作 / 对象 / 结果 / 耗时 / 参数摘要（已脱敏） */
    function logRowHtml(item) {
        const okTag = item.success
            ? '<span class="tag tag-approved">成功</span>'
            : '<span class="tag tag-rejected">失败</span>';
        return '<tr><td>' + formatTime(item.createTime) + '</td>'
            + '<td>' + escapeHtml(item.username || '（未登录）') + '</td>'
            + '<td>' + escapeHtml(item.module || '') + '</td>'
            + '<td>' + escapeHtml(item.action || '') + '</td>'
            + '<td>' + (item.targetId ? '#' + item.targetId : '-') + '</td>'
            + '<td>' + okTag
            + (item.errorMsg ? '<div class="muted">' + escapeHtml(item.errorMsg) + '</div>' : '')
            + '</td>'
            + '<td>' + (item.costMs || 0) + ' ms</td>'
            + '<td><div class="cell-clip">' + escapeHtml(item.params || '-') + '</div>'
            + '<div class="muted cell-clip">' + escapeHtml((item.method || '') + ' ' + (item.uri || '')) + '</div>'
            + '</td></tr>';
    }

    function noteActions(item) {
        let html = '';
        if (item.auditStatus === 'PENDING') {            html += '<button class="btn btn-sm btn-primary" data-approve="' + item.id + '">通过</button> '
                + '<button class="btn btn-sm btn-danger" data-reject="' + item.id + '">驳回</button>';
        }
        if (item.auditStatus === 'APPROVED') {
            html += '<button class="btn btn-sm btn-danger" data-offline="' + item.id + '">下架</button>';
        }
        if (item.auditStatus === 'TAKEN_DOWN') {
            html += '<button class="btn btn-sm btn-primary" data-restore="' + item.id + '">恢复</button>';
        }
        return html;
    }

    box.addEventListener('click', async function (ev) {
        const target = ev.target;
        try {
            if (target.dataset.approve) {
                await API.put('/api/admin/notes/' + target.dataset.approve + '/audit', { approved: true });
                toast('已通过审核', 'success');
            } else if (target.dataset.reject) {
                const remark = prompt('驳回原因（会展示给作者）', '请补充自己的思考，不要只摘抄原文');
                if (remark === null) {
                    return;
                }
                await API.put('/api/admin/notes/' + target.dataset.reject + '/audit',
                    { approved: false, remark: remark });
                toast('已驳回', 'success');
            } else if (target.dataset.offline) {
                const remark = prompt('下架说明（会展示给作者）', '经管理员核查，内容不符合社区规范，已下架');
                if (remark === null) {
                    return;
                }
                await API.put('/api/admin/notes/' + target.dataset.offline + '/offline', { remark: remark });
                toast('已下架', 'success');
            } else if (target.dataset.restore) {
                await API.put('/api/admin/notes/' + target.dataset.restore + '/restore');
                toast('已恢复上架', 'success');
            } else if (target.dataset.disable) {
                if (!confirm('确定禁用这个账号吗？该用户会立刻无法访问系统，也无法重新登录。')) {
                    return;
                }
                await API.put('/api/admin/users/' + target.dataset.disable + '/status', { status: 0 });
                toast('已禁用', 'success');
            } else if (target.dataset.enable) {
                await API.put('/api/admin/users/' + target.dataset.enable + '/status', { status: 1 });
                toast('已启用', 'success');
            } else if (target.dataset.delComment) {
                if (!confirm('确定删除这条违规评论吗？它下面的回复会一起删除（数据库里保留记录，只是标记为已删除）。')) {
                    return;
                }
                await API.del('/api/admin/comments/' + target.dataset.delComment);
                toast('已删除', 'success');
            } else {
                return;
            }
            load();
        } catch (e) {
            toast(e.message, 'error');
        }
    });

    renderShell();
    load(1);
})();
