/* ============================================================
   个人资料页：查看资料、改昵称、改密码、上传头像
   ============================================================ */
(function () {
    if (!requireLogin()) {
        return;
    }

    const infoBox = document.getElementById('profile-info');
    const nicknameInput = document.getElementById('nickname');
    const nicknameForm = document.getElementById('nickname-form');
    const passwordForm = document.getElementById('password-form');
    const avatarInput = document.getElementById('avatar-input');
    const avatarImg = document.getElementById('avatar-img');

    async function loadProfile() {
        try {
            const user = await API.get('/api/users/me');
            avatarImg.src = user.avatarUrl || '';
            avatarImg.style.display = user.avatarUrl ? 'block' : 'none';
            nicknameInput.value = user.nickname || '';
            infoBox.innerHTML =
                '<div class="note-meta">'
                + '<span>用户名：<strong>' + escapeHtml(user.username) + '</strong></span>'
                + '<span>昵称：' + escapeHtml(user.nickname || '') + '</span>'
                + '<span>' + (user.role === 'ADMIN'
                    ? '<span class="tag tag-admin">管理员</span>'
                    : '<span class="tag">普通用户</span>') + '</span>'
                + '<span>注册于 ' + formatTime(user.createTime) + '</span>'
                + '</div>';
        } catch (e) {
            infoBox.innerHTML = '<div class="empty">' + escapeHtml(e.message) + '</div>';
        }
    }

    function rememberUser(patch) {
        const user = API.getUser() || {};
        Object.keys(patch).forEach(function (k) {
            user[k] = patch[k];
        });
        API.setAuth(API.getToken(), user);
        renderNav();   // 刷新导航栏里的头像与昵称
    }

    // 改昵称
    nicknameForm.addEventListener('submit', async function (ev) {
        ev.preventDefault();
        const btn = document.getElementById('nickname-submit');
        btn.disabled = true;
        try {
            const user = await API.put('/api/users/me', { nickname: nicknameInput.value.trim() });
            rememberUser({ nickname: user.nickname, avatarUrl: user.avatarUrl });
            toast('昵称已更新', 'success');
            loadProfile();
        } catch (e) {
            toast(e.message, 'error');
        } finally {
            btn.disabled = false;
        }
    });

    // 改密码
    passwordForm.addEventListener('submit', async function (ev) {
        ev.preventDefault();
        const oldPassword = document.getElementById('oldPassword').value;
        const newPassword = document.getElementById('newPassword').value;
        if (newPassword.length < 6 || newPassword.length > 20) {
            toast('新密码长度必须是 6~20 位', 'error');
            return;
        }
        const btn = document.getElementById('password-submit');
        btn.disabled = true;
        try {
            await API.put('/api/users/me/password', { oldPassword: oldPassword, newPassword: newPassword });
            passwordForm.reset();
            toast('密码已修改（已签发的旧令牌仍然有效，如需强制下线请重新登录）', 'success');
        } catch (e) {
            toast(e.message, 'error');
        } finally {
            btn.disabled = false;
        }
    });

    // 上传头像
    avatarInput.addEventListener('change', async function () {
        const file = avatarInput.files && avatarInput.files[0];
        if (!file) {
            return;
        }
        try {
            const user = await API.upload('/api/users/me/avatar', file);
            avatarImg.src = user.avatarUrl || '';
            avatarImg.style.display = user.avatarUrl ? 'block' : 'none';
            rememberUser({ avatarUrl: user.avatarUrl, nickname: user.nickname });
            toast('头像已更新', 'success');
            loadProfile();
        } catch (e) {
            toast('上传失败：' + e.message, 'error');
        } finally {
            avatarInput.value = '';
        }
    });

    loadProfile();
})();
