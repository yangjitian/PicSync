package com.yudi.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户图片行为记录表（浏览/点赞/收藏/分享/下载）
 * @TableName user_picture_action
 */
@TableName(value = "user_picture_action")
@Data
public class UserPictureAction implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 图片id
     */
    @TableField("picture_id")
    private Long pictureId;

    /**
     * 互动类型：浏览/点赞/收藏/分享/下载
     */
    @TableField("action_type")
    private String actionType;

    /**
     * 状态：0-取消，1-有效
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;


    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}