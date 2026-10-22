package com.notework.controller;

import com.notework.common.api.Result;
import com.notework.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口。
 */
@Tag(name = "文件上传接口", description = "笔记配图上传，返回可直接放进笔记 imageUrls 的访问 URL")
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "上传笔记配图",
            description = "multipart/form-data，字段名 file；只允许 jpg/jpeg/png/gif/webp，单张不超过 5MB。"
                    + "返回形如 /uploads/note/2026/10/15/xxx.png 的 URL。")
    @PostMapping("/note-images")
    public Result<String> uploadNoteImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadNoteImage(file));
    }
}
