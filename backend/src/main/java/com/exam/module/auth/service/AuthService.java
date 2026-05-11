package com.exam.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BizException;
import com.exam.common.JwtUtil;
import com.exam.common.LoginUser;
import com.exam.common.Role;
import com.exam.module.auth.dto.LoginDTO;
import com.exam.module.auth.dto.LoginVO;
import com.exam.module.auth.dto.RegisterDTO;
import com.exam.module.user.entity.User;
import com.exam.module.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectByUsernameWithPwd(dto.getUsername());
        if (user == null) throw new BizException("用户不存在");
        if (user.getStatus() != null && user.getStatus() == 0) throw new BizException("账号已禁用");
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException("密码错误");
        }

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole(), user.getRealName());
        String token = jwtUtil.generate(loginUser);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        return vo;
    }

    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (count > 0) throw new BizException("用户名已存在");

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.STUDENT.name());
        user.setRealName(dto.getRealName());
        user.setIdCard(dto.getIdCard());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setGender(dto.getGender());
        user.setStatus(1);
        userMapper.insert(user);
    }

    public User currentUser() {
        Long uid = com.exam.common.UserContext.userId();
        User user = userMapper.selectById(uid);
        if (user == null) throw new BizException("用户不存在");
        return user;
    }
}
