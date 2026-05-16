package com.exam.module.user.service;

import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.module.user.dto.UserQueryDTO;
import com.exam.module.user.dto.UserSaveDTO;
import com.exam.module.user.entity.User;
import com.exam.shard.UserShardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    @Autowired
    private UserShardRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public PageResult<User> page(UserQueryDTO query) {
        return userRepo.page(query);
    }

    public User detail(Long id) {
        User u = userRepo.findById(id);
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
            if (userRepo.existsByUsername(dto.getUsername())) throw new BizException("用户名已存在");
            if (!StringUtils.hasText(dto.getPassword())) throw new BizException("新增用户需指定密码");
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            userRepo.insert(user);
        } else {
            User exist = userRepo.findById(dto.getId());
            if (exist == null) throw new BizException("用户不存在");
            if (StringUtils.hasText(dto.getPassword())) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            }
            userRepo.update(user);
        }
    }

    public void delete(Long id) {
        userRepo.deleteById(id);
    }

    public void resetPassword(Long id, String newPwd) {
        if (!StringUtils.hasText(newPwd)) throw new BizException("新密码不能为空");
        User exist = userRepo.findById(id);
        if (exist == null) throw new BizException("用户不存在");
        exist.setPassword(passwordEncoder.encode(newPwd));
        userRepo.update(exist);
    }
}
