package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource //实现依赖注入
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendConde(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {//手机号不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //生成验证码(阿里云的短信api太麻烦了，模拟生成即可)
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis当中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("【短信验证码发送成功】" + phone + ":" + code);

        //返回token
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {//手机号不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //TODO 校验验证码（修改为从redis中获取）
        String cacheCode = (String)session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //根据手机号查用户
        User user = this.query().eq("phone", loginForm.getPhone()).one();
        if (user == null) { // 用户不存在,创建新用户本保存到数据库
            //使用手机号创建新用户并保存
            user = createUserWithPhone(loginForm.getPhone());
        }

        //随机生成一个token作为登录令牌
        String token = UUID.randomUUID().toString();

        //将user对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue( true)
                        .setFieldValueEditor((fieldName, fieldValue )-> fieldValue.toString())
        );

        //将数据保存到redis中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //登录成功
        return Result.ok(token);
    }

    /*
    * private方法，根据phone创建用户
    * */
    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }
}
