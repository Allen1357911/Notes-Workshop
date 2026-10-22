/* ============================================================
   登录 / 注册页脚本（两页共用，按表单是否存在来区分）
   ============================================================ */
(function () {
    const redirect = new URLSearchParams(location.search).get('redirect') || '/index.html';

    // ---------------- 登录 ----------------
    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        if (API.isLogin()) {
            API.clearAuth();   // 进登录页视为要重新登录
        }
        loginForm.addEventListener('submit', async function (ev) {
            ev.preventDefault();
            const btn = document.getElementById('submit-btn');
            btn.disabled = true;
            try {
                const data = await API.post('/api/auth/login', {
                    username: document.getElementById('username').value.trim(),
                    password: document.getElementById('password').value
                });
                API.setAuth(data.token, data.user);
                toast('登录成功，正在跳转…', 'success');
                setTimeout(function () {
                    location.href = redirect;
                }, 500);
            } catch (e) {
                toast(e.message, 'error');
                btn.disabled = false;
            }
        });
    }

    // ---------------- 注册 ----------------
    const registerForm = document.getElementById('register-form');
    if (registerForm) {
        registerForm.addEventListener('submit', async function (ev) {
            ev.preventDefault();
            const username = document.getElementById('username').value.trim();
            const password = document.getElementById('password').value;
            const nickname = document.getElementById('nickname').value.trim();
            const confirmPassword = document.getElementById('confirmPassword').value;

            if (password !== confirmPassword) {
                toast('两次输入的密码不一致', 'error');
                return;
            }

            const btn = document.getElementById('submit-btn');
            btn.disabled = true;
            try {
                await API.post('/api/auth/register', {
                    username: username,
                    password: password,
                    nickname: nickname || undefined
                });
                // 注册接口不返回令牌，这里顺手帮用户登录一次
                try {
                    const data = await API.post('/api/auth/login', { username: username, password: password });
                    API.setAuth(data.token, data.user);
                    toast('注册成功，已自动登录', 'success');
                    setTimeout(function () { location.href = '/index.html'; }, 600);
                } catch (loginError) {
                    toast('注册成功，请登录', 'success');
                    setTimeout(function () { location.href = '/login.html'; }, 600);
                }
            } catch (e) {
                toast(e.message, 'error');
                btn.disabled = false;
            }
        });
    }
})();
