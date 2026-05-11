package com.exam.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.module.user.dto.UserQueryDTO;
import com.exam.module.user.dto.UserSaveDTO;
import com.exam.module.user.entity.User;
import com.exam.module.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public PageResult<User> page(UserQueryDTO query) {
        Page<User> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(User::getRole, query.getRole());
        }
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword();
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getRealName, kw)
                    .or().like(User::getPhone, kw));
        }
        wrapper.orderByDesc(User::getId);
        return PageResult.of(userMapper.selectPage(page, wrapper));
    }

    public User detail(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        return u;
    }

    public void save(UserSaveDTO dto) {
        User user = new User();
        user.setId(dto.getId());
        user.setUsername(dto.getUsername());
        user.setRole(dto.getRole());
        user.setRealName(dto.getRealName());
        user.setIdCard(dto.getIdCard());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setGender(dto.getGender());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());

        if (dto.getId() == null) {
            // 新增
            Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
            if (count > 0) throw new BizException("用户名已存在");
            if (!StringUtils.hasText(dto.getPassword())) throw new BizException("新增用户需指定密码");
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            userMapper.insert(user);
        } else {
            // 更新
            User exist = userMapper.selectById(dto.getId());
            if (exist == null) throw new BizException("用户不存在");
            if (StringUtils.hasText(dto.getPassword())) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            }
            userMapper.updateById(user);
        }
    }

    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    public void resetPassword(Long id, String newPwd) {
        if (!StringUtils.hasText(newPwd)) throw new BizException("新密码不能为空");
        User u = new User();
        u.setId(id);
        u.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(u);
    }
}
