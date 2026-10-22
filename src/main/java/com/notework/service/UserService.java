package com.notework.service;

import com.notework.dto.request.ChangePasswordRequest;
import com.notework.dto.request.LoginRequest;
import com.notework.dto.request.RegisterRequest;
import com.notework.dto.request.UpdateProfileRequest;
import com.notework.dto.response.LoginVO;
import com.notework.dto.response.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户业务接口。
 */
public interface UserService {

    /** 注册：用户名唯一、密码 BCrypt 加密后入库 */
    UserVO register(RegisterRequest request);

    /** 登录：校验密码并签发 JWT */
    LoginVO login(LoginRequest request);

    /** 查询当前登录用户的资料 */
    UserVO getCurrentUser();

    /** 修改个人资料（基础阶段只含昵称） */
    UserVO updateProfile(UpdateProfileRequest request);

    /** 修改密码：必须校验原密码 */
    void changePassword(ChangePasswordRequest request);

    /** 上传头像：文件落盘、数据库只存访问 URL */
    UserVO updateAvatar(MultipartFile file);
}
