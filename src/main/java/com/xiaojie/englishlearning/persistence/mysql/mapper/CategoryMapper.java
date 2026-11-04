package com.xiaojie.englishlearning.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaojie.englishlearning.persistence.mysql.CategoryEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {

    @Select("SELECT id, name_key, name FROM categories WHERE name_key = #{nameKey} LIMIT 1")
    CategoryEntity selectByNameKey(@Param("nameKey") String nameKey);

    @Insert("INSERT IGNORE INTO categories (name_key, name) VALUES (#{nameKey}, #{name})")
    int insertIfAbsent(@Param("nameKey") String nameKey, @Param("name") String name);
}
