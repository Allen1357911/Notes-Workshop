package com.notework.dto.request;

import com.notework.common.enums.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建读书笔记请求。
 */
public class CreateNoteRequest {

    @Schema(description = "图书名，必须以书名号开头，最长 100 个字符", example = "《活着》")
    @NotBlank(message = "图书名不能为空")
    @Size(max = 100, message = "图书名最长 100 个字符")
    @Pattern(regexp = "^《[^《》]+》[\\s\\S]*$", message = "图书名要用书名号括起来，例如《活着》")
    private String bookName;

    @Schema(description = "笔记正文，最长 20000 个字符", example = "读完之后最大的感受是……")
    @NotBlank(message = "笔记正文不能为空")
    @Size(max = 20000, message = "笔记正文最长 20000 个字符")
    private String content;

    @Schema(description = "可见性：PRIVATE 私有（作者与管理员可见）/ PUBLIC 公开（所有人可见）",
            example = "PUBLIC", allowableValues = {"PRIVATE", "PUBLIC"})
    @NotNull(message = "可见性不能为空：PRIVATE 私有 / PUBLIC 公开")
    private Visibility visibility;

    @Schema(description = "配图 URL 列表（先调用配图上传接口拿到 URL），可为空表示不带配图，最多 9 张",
            example = "[\"/uploads/note/2026/10/15/xxx.png\"]")
    @Size(max = 9, message = "一篇笔记最多 9 张配图")
    private List<@NotBlank(message = "配图 URL 不能为空") String> imageUrls;

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        // 先去掉首尾空格再参与校验：用户多打一个空格不应该被"书名号格式不对"拦下
        this.bookName = bookName == null ? null : bookName.trim();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }
}
