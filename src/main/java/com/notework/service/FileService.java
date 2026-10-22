package com.notework.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件业务接口。
 */
public interface FileService {

    /** 上传笔记配图，返回可访问的 URL（图片文件落盘，数据库里只存这个 URL） */
    String uploadNoteImage(MultipartFile file);
}
