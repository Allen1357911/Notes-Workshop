package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.NoteImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记配图数据访问层，只做数据库 CRUD。
 */
@Mapper
public interface NoteImageMapper extends BaseMapper<NoteImage> {
}
