package com.notework.controller;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.dto.response.OperationLogVO;
import com.notework.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员后台：操作日志查询（性能与健壮性模块）。
 * <p>
 * 日志由 {@code @OperationLog} 注解 + AOP 切面自动写入，业务代码零侵入；
 * 这里只负责查询，方便核查"谁在什么时候对什么做了什么、成功还是失败、耗时多少"。
 */
@Tag(name = "管理员-操作日志", description = "查询操作日志（谁、何时、对什么、做了什么、结果如何），需要管理员权限")
@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {

    private final OperationLogService operationLogService;

    public AdminLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Operation(summary = "操作日志列表（分页）",
            description = "可按 module（模块）、username（操作人用户名，模糊匹配）、success（true 只看成功 / false 只看失败）过滤，"
                    + "按发生时间倒序返回。参数摘要在写入时已脱敏，密码一类字段一律是 ***。")
    @GetMapping
    public Result<PageResult<OperationLogVO>> list(@RequestParam(value = "module", required = false) String module,
                                                   @RequestParam(value = "username", required = false) String username,
                                                   @RequestParam(value = "success", required = false) Boolean success,
                                                   PageQuery pageQuery) {
        return Result.success(operationLogService.pageLogs(module, username, success, pageQuery));
    }
}
