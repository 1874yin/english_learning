package com.xiaojie.englishlearning.persistence.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("categories")
public class CategoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nameKey;
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNameKey() { return nameKey; }
    public void setNameKey(String nameKey) { this.nameKey = nameKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
