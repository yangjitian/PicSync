package com.yudi.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yudi.cloud.model.entity.UserPictureAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author yudi
 * @description 针对表【user_picture_action(用户图片行为记录表)】的数据库操作Mapper
 * @createDate 2024-12-19 10:00:00
 * @Entity com.yudi.cloud.model.entity.UserPictureAction
 */
@Mapper
public interface UserPictureActionMapper extends BaseMapper<UserPictureAction> {

    /**
     * 根据用户ID和图片ID查询特定行为记录
     * @param userId 用户ID
     * @param pictureId 图片ID
     * @param actionType 行为类型
     * @return 行为记录
     */
    UserPictureAction selectByUserIdAndPictureIdAndActionType(@Param("userId") Long userId, 
                                                              @Param("pictureId") Long pictureId, 
                                                              @Param("actionType") String actionType);

    /**
     * 批量查询用户对多个图片的行为状态
     * @param userId 用户ID
     * @param pictureIds 图片ID列表
     * @return 行为记录列表
     */
    List<UserPictureAction> selectUserActionsByPictureIds(@Param("userId") Long userId, 
                                                          @Param("pictureIds") List<Long> pictureIds);

    /**
     * 检查用户是否在今天浏览过该图片（从凌晨0点到23:59:59）
     * @param userId 用户ID
     * @param pictureId 图片ID
     * @return 是否浏览过
     */
    Boolean checkUserViewedIn24Hours(@Param("userId") Long userId, @Param("pictureId") Long pictureId);

    /**
     * 检查用户是否曾经浏览过该图片（不限时间）
     * @param userId 用户ID
     * @param pictureId 图片ID
     * @return 是否浏览过
     */
    Boolean checkUserViewedBefore(@Param("userId") Long userId, @Param("pictureId") Long pictureId);

    /**
     * 更新浏览记录的更新时间（跨天浏览时使用）
     * @param userId 用户ID
     * @param pictureId 图片ID
     * @return 影响行数
     */
    int updateViewTime(@Param("userId") Long userId, @Param("pictureId") Long pictureId);

    /**
     * 检查用户是否曾经分享过该图片（不限时间）
     * @param userId 用户ID
     * @param pictureId 图片ID
     * @return 是否分享过
     */
    Boolean checkUserSharedBefore(@Param("userId") Long userId, @Param("pictureId") Long pictureId);

    /**
     * 更新分享记录的更新时间
     * @param userId 用户ID
     * @param pictureId 图片ID
     * @return 影响行数
     */
    int updateShareTime(@Param("userId") Long userId, @Param("pictureId") Long pictureId);

}