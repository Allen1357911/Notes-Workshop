/* ============================================================
   写笔记 / 编辑笔记：图书名、正文、可见性、多张配图上传
   ============================================================ */
(function () {
    if (!requireLogin()) {
        return;
    }

    const id = new URLSearchParams(location.search).get('id');
    const isEdit = !!id;

    const titleEl = document.getElementById('form-title');
    const tipEl = document.getElementById('form-tip');
    const form = document.getElementById('note-form');
    const bookInput = document.getElementById('bookName');
    const contentInput = document.getElementById('content');
    const visibilitySelect = document.getElementById('visibility');
    const thumbs = document.getElementById('thumbs');
    const fileInput = document.getElementById('image-input');
    const submitBtn = document.getElementById('submit-btn');

    const MAX_IMAGES = 9;
    let imageUrls = [];
    let imagesTouched = false;   // 编辑时：只有动过配图才提交 imageUrls，否则保持原配图不变

    titleEl.textContent = isEdit ? '编辑笔记' : '写笔记';

    function renderThumbs() {
        if (!imageUrls.length) {
            thumbs.innerHTML = '<span class="muted">还没有配图，可上传书页截图或手写笔记照片</span>';
            return;
        }
        let html = '';
        imageUrls.forEach(function (url, index) {
            html += '<div class="upload-item">'
                + '<img src="' + escapeHtml(url) + '" alt="配图">'
                + '<button type="button" data-remove="' + index + '" title="移除这张">×</button>'
                + '</div>';
        });
        thumbs.innerHTML = html;
    }

    if (isEdit) {
        (async function () {
            try {
                const note = await API.get('/api/notes/' + id);
                bookInput.value = note.bookName || '';
                contentInput.value = note.content || '';
                visibilitySelect.value = note.visibility || 'PRIVATE';
                imageUrls = (note.images || []).slice();
                renderThumbs();
                if (!note.editable) {
                    tipEl.innerHTML = '<span style="color:#dc2626">这篇笔记不属于你（也不是管理员），只能查看不能编辑。</span>';
                    submitBtn.disabled = true;
                    fileInput.disabled = true;
                }
            } catch (e) {
                tipEl.innerHTML = '<span style="color:#dc2626">' + escapeHtml(e.message) + '</span>';
                submitBtn.disabled = true;
            }
        })();
    } else {
        renderThumbs();
    }

    // 移除已有配图
    thumbs.addEventListener('click', function (ev) {
        const btn = ev.target.closest('button[data-remove]');
        if (!btn) {
            return;
        }
        imageUrls.splice(parseInt(btn.getAttribute('data-remove'), 10), 1);
        imagesTouched = true;
        renderThumbs();
    });

    // 选择图片 -> 逐个上传拿 URL
    fileInput.addEventListener('change', async function () {
        const files = Array.prototype.slice.call(fileInput.files || []);
        if (!files.length) {
            return;
        }
        if (imageUrls.length + files.length > MAX_IMAGES) {
            toast('一篇笔记最多 ' + MAX_IMAGES + ' 张配图', 'error');
            fileInput.value = '';
            return;
        }
        for (let i = 0; i < files.length; i++) {
            const file = files[i];
            try {
                const url = await API.upload('/api/files/note-images', file);
                imageUrls.push(url);
                imagesTouched = true;
                renderThumbs();
                toast('已上传：' + file.name, 'success');
            } catch (e) {
                toast('上传失败（' + file.name + '）：' + e.message, 'error');
            }
        }
        fileInput.value = '';   // 清空以便再次选择同一个文件
    });

    // 提交
    form.addEventListener('submit', async function (ev) {
        ev.preventDefault();
        const bookName = bookInput.value.trim();
        // 前端先拦一次：书名必须以书名号开头（后端也会校验，这里只是少一次往返）
        if (!/^《[^《》]+》([\s\S]*)$/.test(bookName)) {
            toast('图书名要用书名号括起来，例如《活着》', 'error');
            bookInput.focus();
            return;
        }
        const payload = {
            bookName: bookName,
            content: contentInput.value,
            visibility: visibilitySelect.value
        };
        if (!isEdit) {
            payload.imageUrls = imageUrls;
        } else if (imagesTouched) {
            payload.imageUrls = imageUrls;   // 传空数组表示清空配图
        }
        // 编辑且没动过配图时不传 imageUrls，后端语义是"不修改配图"

        submitBtn.disabled = true;
        try {
            const saved = isEdit
                ? await API.put('/api/notes/' + id, payload)
                : await API.post('/api/notes', payload);
            toast(isEdit ? '保存成功' : '发布成功', 'success');
            setTimeout(function () {
                location.href = '/note-detail.html?id=' + saved.id;
            }, 600);
        } catch (e) {
            toast(e.message, 'error');
            submitBtn.disabled = false;
        }
    });
})();
