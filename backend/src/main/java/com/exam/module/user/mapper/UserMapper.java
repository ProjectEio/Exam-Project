package com.exam.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 登录时查带密码的用户 */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    User selectByUsernameWithPwd(String username);

    /**
     * 用户名唯一性检查 — 使用 EXISTS + LIMIT 1，遇到第一条即停止，不全表扫描
     * 返回 1 表示存在，null 表示不存在
     */
    @Select("SELECT 1 FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    Integer existsByUsername(String username);
}
