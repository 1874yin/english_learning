package com.xiaojie.englishlearning.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaojie.englishlearning.persistence.mysql.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT id, username, username_key, password_hash, created_at "
            + "FROM users WHERE username_key = #{usernameKey} LIMIT 1")
    UserEntity selectByUsernameKey(@Param("usernameKey") String usernameKey);
}
