package com.notework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * 上传相关配置，对应 application.yml 里的 app.upload.*
 */
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** 上传根目录，相对路径，便于换机器直接运行 */
    private String dir = "./uploads";

    /** 图片访问 URL 前缀 */
    private String urlPrefix = "/uploads";

    /** 单张图片大小上限 */
    private DataSize maxImageSize = DataSize.ofMegabytes(5);

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public DataSize getMaxImageSize() {
        return maxImageSize;
    }

    public void setMaxImageSize(DataSize maxImageSize) {
        this.maxImageSize = maxImageSize;
    }
}
