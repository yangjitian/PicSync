package com.yudi.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.model.dto.space.SpaceAddDTO;
import com.yudi.cloud.model.dto.space.SpaceQueryDTO;
import com.yudi.cloud.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.vo.space.SpaceVO;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * @author yudi
 * date 2025-07-20
 */
public interface SpaceService extends IService<Space> {

    /**
     * 基础校验
     * @param space
     * @param add
     */
    void validSpace(Space space, boolean add);

    /**
     * 参数填充
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 获取查询对象
     *
     * @param spaceQueryDTO
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryDTO spaceQueryDTO);

    /**
     * 创建空间
     * @param spaceAddDTO
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddDTO spaceAddDTO, User loginUser);

    /**
     * 查询分装类（单条）
     * @param space
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 查询分装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 校验空间权限
     * @param spaceId
     * @param request
     * @return
     */
    Space validateSpaceAccess(Long spaceId, HttpServletRequest request);
}
