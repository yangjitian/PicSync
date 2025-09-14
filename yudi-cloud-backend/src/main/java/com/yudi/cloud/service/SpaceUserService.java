package com.yudi.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yudi.cloud.model.dto.spaceuser.SpaceUserAddRequest;
import com.yudi.cloud.model.dto.spaceuser.SpaceUserEditRequest;
import com.yudi.cloud.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yudi.cloud.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yudi.cloud.model.vo.spaceuser.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author yudi
 * date 2025-07-20
 */
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 校验空间成员
     *
     * @param spaceUser
     * @param add       是否为创建时检验
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 创建空间成员
     *
     * @param spaceUserAddRequest
     * @return
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 更新空间成员信息
     *
     * @param spaceUserEditRequest
     * @param request
     * @return
     */
    boolean updateSpaceUser(SpaceUserEditRequest spaceUserEditRequest, HttpServletRequest request);

    /**
     * 获取空间成员包装类（单条）
     *
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间成员包装类（列表）
     *
     * @param spaceUserList
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
