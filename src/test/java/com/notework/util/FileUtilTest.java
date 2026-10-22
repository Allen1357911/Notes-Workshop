package com.notework.util;

import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.config.UploadProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件上传工具的单元测试：用临时目录，不碰真实上传目录。
 */
class FileUtilTest {

    private FileUtil fileUtil(Path tempDir, long maxBytes) {
        UploadProperties properties = new UploadProperties();
        properties.setDir(tempDir.toString());
        properties.setUrlPrefix("/uploads");
        properties.setMaxImageSize(DataSize.ofBytes(maxBytes));
        return new FileUtil(properties);
    }

    @Test
    @DisplayName("正常图片落盘并返回可访问 URL")
    void saveImageSuccessfully(@TempDir Path tempDir) {
        FileUtil fileUtil = fileUtil(tempDir, 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "photo.PNG", "image/png", new byte[128]);

        String url = fileUtil.saveImage(file, "avatar");

        assertTrue(url.startsWith("/uploads/avatar/"), "URL 前缀与分类目录应正确：" + url);
        assertTrue(url.endsWith(".png"), "扩展名应统一成小写：" + url);
        assertTrue(Files.exists(tempDir.resolve(url.substring("/uploads/".length()))), "文件应真实落盘");
    }

    @Test
    @DisplayName("空文件被拒绝")
    void emptyFileRejected(@TempDir Path tempDir) {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        BusinessException e = assertThrowsBusinessException(() -> fileUtil(tempDir, 1024).saveImage(empty, "avatar"));

        assertEquals(ErrorCode.FILE_EMPTY, e.getErrorCode());
    }

    @Test
    @DisplayName("非图片扩展名被拒绝")
    void wrongExtensionRejected(@TempDir Path tempDir) {
        MockMultipartFile txt = new MockMultipartFile("file", "note.txt", "text/plain", new byte[10]);

        BusinessException e = assertThrowsBusinessException(() -> fileUtil(tempDir, 1024).saveImage(txt, "avatar"));

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    @DisplayName("没有扩展名的文件被拒绝")
    void noExtensionRejected(@TempDir Path tempDir) {
        MockMultipartFile noName = new MockMultipartFile("file", null, "image/png", new byte[10]);

        BusinessException e = assertThrowsBusinessException(() -> fileUtil(tempDir, 1024).saveImage(noName, "avatar"));

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    @DisplayName("扩展名合法但内容类型不是图片时被拒绝")
    void wrongContentTypeRejected(@TempDir Path tempDir) {
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "application/octet-stream", new byte[10]);

        BusinessException e = assertThrowsBusinessException(() -> fileUtil(tempDir, 1024).saveImage(fake, "avatar"));

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    @DisplayName("超出大小限制的图片被拒绝")
    void tooLargeRejected(@TempDir Path tempDir) {
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", new byte[101]);

        BusinessException e = assertThrowsBusinessException(() -> fileUtil(tempDir, 100).saveImage(big, "avatar"));

        assertEquals(ErrorCode.FILE_TOO_LARGE, e.getErrorCode());
    }

    private BusinessException assertThrowsBusinessException(Executable executable) {
        return assertThrows(BusinessException.class, executable);
    }
}
