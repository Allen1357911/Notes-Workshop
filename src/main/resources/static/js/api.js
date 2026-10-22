/* ============================================================
   API 封装：统一处理令牌、统一响应体、统一错误提示与登录跳转。
   后端所有接口都返回 { code, message, data }，这里把 data 直接返回给页面。
   ============================================================ */
const API = (function () {
    const TOKEN_KEY = 'nw_token';
    const USER_KEY = 'nw_user';

    function getToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function getUser() {
        try {
            return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
        } catch (e) {
            return null;
        }
    }

    function setAuth(token, user) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USER_KEY, JSON.stringify(user || {}));
    }

    function clearAuth() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    }

    function isLogin() {
        return !!getToken();
    }

    /** 未登录时跳到登录页，并记住要回到哪个页面 */
    function redirectToLogin() {
        const back = encodeURIComponent(location.pathname + location.search);
        location.href = '/login.html?redirect=' + back;
    }

    /**
     * 统一请求。
     * @param method  GET / POST / PUT / DELETE
     * @param path    以 /api 开头的路径
     * @param body    普通对象（自动转 JSON）或 FormData（文件上传）
     * @param options { silent401: true } 表示 401 时不跳转，交给调用方处理
     */
    async function request(method, path, body, options) {
        const opts = options || {};
        const headers = {};
        const token = getToken();
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        let payload;
        if (body instanceof FormData) {
            payload = body;                       // 文件上传：不要手动设置 Content-Type
        } else if (body !== undefined && body !== null) {
            headers['Content-Type'] = 'application/json;charset=utf-8';
            payload = JSON.stringify(body);
        }

        let res;
        try {
            res = await fetch(path, { method: method, headers: headers, body: payload });
        } catch (e) {
            throw new Error('无法连接服务器，请确认后端已启动');
        }

        let json = null;
        try {
            json = await res.json();
        } catch (e) {
            json = null;
        }

        if (res.status === 401) {
            clearAuth();
            if (!opts.silent401) {
                redirectToLogin();
            }
            const err = new Error('未登录或登录状态已过期');
            err.code = 401;
            throw err;
        }

        if (!json) {
            throw new Error('服务器返回异常（HTTP ' + res.status + '）');
        }
        if (json.code !== 200) {
            const err = new Error(json.message || '请求失败');
            err.code = json.code;
            throw err;
        }
        return json.data;
    }

    return {
        getToken: getToken,
        getUser: getUser,
        setAuth: setAuth,
        clearAuth: clearAuth,
        isLogin: isLogin,
        redirectToLogin: redirectToLogin,
        get: function (path, options) { return request('GET', path, undefined, options); },
        post: function (path, body, options) { return request('POST', path, body, options); },
        put: function (path, body, options) { return request('PUT', path, body, options); },
        del: function (path, options) { return request('DELETE', path, undefined, options); },
        /** 上传单个文件，返回后端给的访问 URL */
        upload: function (path, file) {
            const fd = new FormData();
            fd.append('file', file);
            return request('POST', path, fd);
        }
    };
})();
