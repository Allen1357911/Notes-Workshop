package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.dto.response.AdminUserVO;
import com.notework.entity.User;
import com.notework.interceptor.UserContext;
import com.notework.mapper.UserMapper;
import com.notework.service.AdminUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 管理员后台：用户管理实现。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserServiceImpl.class);

    private final UserMapper userMapper;

    public AdminUserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<AdminUserVO> pageUsers(String keyword, Integer status, PageQuery pageQuery) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, trimmed).or().like(User::getNickname, trimmed));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getId);

        Page<User> page = userMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery.getSize()), wrapper);
        List<AdminUserVO> list = page.getRecords().stream().map(AdminUserVO::from).toList();

        if (list.isEmpty()) {
            return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
        }
        return PageResult.of(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO updateStatus(Long userId, Integer status) {
        BizAssert.isTrue(status != null && (status == 0 || status == 1),
                ErrorCode.PARAM_ERROR, "status 只能是 1（正常）或 0（禁用）");

        User user = userMapper.selectById(userId);
        BizAssert.notNull(user, ErrorCode.USER_NOT_FOUND);

        // 不能把自己禁掉，否则管理员可能把自己锁在门外
        if (status == 0 && userId.equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_SELF);
        }

        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);

        log.info("管理员[{}]把用户[{}]（{}）的状态改为 {}", UserContext.getUserId(), userId, user.getUsername(),
                status == 1 ? "正常" : "禁用");

        return AdminUserVO.from(userMapper.selectById(userId));
    }
}
