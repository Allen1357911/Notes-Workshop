package com.notework.common.exception;

import com.notework.common.api.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器的行为验证。
 * <p>
 * 这里用一个只存在于测试里的临时 Controller 触发各类异常，
 * 不依赖真实业务代码，因此 common 模块可以独立完成验证。
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("业务异常：按错误码返回，并带上对应的 HTTP 状态码")
    void businessExceptionUsesErrorCode() throws Exception {
        mockMvc.perform(post("/fixture/business").param("type", "note"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("笔记不存在"));
    }

    @Test
    @DisplayName("业务异常：自定义提示信息生效，错误码保持不变")
    void businessExceptionWithCustomMessage() throws Exception {
        mockMvc.perform(post("/fixture/business").param("type", "custom"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("自定义提示"));
    }

    @Test
    @DisplayName("参数校验失败：返回字段上的提示信息")
    void validationFailureReturnsFieldMessage() throws Exception {
        mockMvc.perform(post("/fixture/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("名称不能为空"));
    }

    @Test
    @DisplayName("JSON 格式错误：返回参数错误而不是系统错误")
    void malformedJsonReturnsParamError() throws Exception {
        mockMvc.perform(post("/fixture/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("缺少必填参数：返回参数错误而不是系统错误")
    void missingRequiredParameterReturnsParamError() throws Exception {
        mockMvc.perform(post("/fixture/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("请求方法不支持：返回 405 而不是系统错误")
    void methodNotSupportedReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/fixture/business").param("type", "note"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }

    @Test
    @DisplayName("请求内容类型不支持：返回 415 而不是系统错误")
    void mediaTypeNotSupportedReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/fixture/success")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"张三\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(415))
                .andExpect(jsonPath("$.message").value("请求内容类型不支持"));
    }

    @Test
    @DisplayName("未预期异常：兜底返回系统错误，不暴露内部信息")
    void unexpectedExceptionReturnsSystemError() throws Exception {
        mockMvc.perform(post("/fixture/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后再试"));
    }

    @Test
    @DisplayName("正常链路：统一响应体的 code 为 200 且带数据")
    void successResponseIsWrapped() throws Exception {
        mockMvc.perform(post("/fixture/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data").value("张三"));
    }

    /** 只用于测试的临时 Controller */
    @RestController
    @RequestMapping("/fixture")
    static class FixtureController {

        @PostMapping("/business")
        public Result<Void> business(@RequestParam String type) {
            switch (type) {
                case "note" -> throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
                case "custom" -> throw new BusinessException(ErrorCode.FORBIDDEN, "自定义提示");
                default -> throw new IllegalStateException("测试用例未覆盖的类型: " + type);
            }
        }

        @PostMapping("/success")
        public Result<String> success(@Valid @RequestBody FixtureRequest request) {
            return Result.success(request.getName());
        }

        @PostMapping("/boom")
        public Result<Void> boom() {
            throw new IllegalStateException("模拟未预期异常");
        }
    }

    /** 只用于测试的请求体 */
    static class FixtureRequest {

        @NotBlank(message = "名称不能为空")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
