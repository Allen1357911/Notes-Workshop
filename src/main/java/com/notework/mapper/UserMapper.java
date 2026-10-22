package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层。
 * <p>
 * 只做数据库 CRUD，不写任何业务判断与权限逻辑。
 * 单表增删改查由 MyBatis-Plus 的 BaseMapper 提供，需要复杂查询时再手写 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
