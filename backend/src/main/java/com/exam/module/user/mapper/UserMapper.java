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
}
