package com.notework.util;

import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.config.UploadProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储工具。
 * <p>
 * 只负责"校验 + 落盘 + 返回访问 URL"，不碰业务表 —— 数据库里保存的是这里返回的 URL。
 * 存储根目录来自配置（相对路径），所以换机器不用改代码。
 */
@Component
public class FileUtil {

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final UploadProperties uploadProperties;

    /** 上传根目录的绝对路径，构造时算好，避免每次请求重复计算 */
    private final Path rootDir;

    public FileUtil(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
        this.rootDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
    }

    /**
     * 保存一张图片。
     *
     * @param file     上传的文件
     * @param category 分类目录，例如 avatar（头像）、note（笔记配图）
     * @return 可直接访问的相对 URL，例如 /uploads/avatar/2026/10/14/xxxx.png
     */
    public String saveImage(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        if (file.getSize() > uploadProperties.getMaxImageSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String datePath = LocalDate.now().format(DATE_PATH);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path targetDir = rootDir.resolve(category).resolve(datePath);
        try {
            Files.createDirectories(targetDir);
            // transferTo 要求绝对路径，rootDir 构造时已绝对化
            file.transferTo(targetDir.resolve(filename));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return uploadProperties.getUrlPrefix() + "/" + category + "/" + datePath + "/" + filename;
    }

    /** 上传根目录（绝对路径），静态资源映射与测试使用 */
    public Path getRootDir() {
        return rootDir;
    }

    /** 取小写扩展名；没有扩展名时返回空串，交给白名单拒绝 */
    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
