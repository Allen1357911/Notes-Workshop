package com.notework.service.impl;

import com.notework.interceptor.UserContext;
import com.notework.service.FileService;
import com.notework.util.FileUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件业务实现：校验与落盘交给 {@link FileUtil}，这里只补业务前置条件。
 */
@Service
public class FileServiceImpl implements FileService {

    /** 笔记配图的分类目录 */
    private static final String NOTE_IMAGE_CATEGORY = "note";

    private final FileUtil fileUtil;

    public FileServiceImpl(FileUtil fileUtil) {
        this.fileUtil = fileUtil;
    }

    @Override
    public String uploadNoteImage(MultipartFile file) {
        // 上传必须登录（拦截器已挡一层，这里再明确一次业务前置条件）
        UserContext.requireUserId();
        return fileUtil.saveImage(file, NOTE_IMAGE_CATEGORY);
    }
}
