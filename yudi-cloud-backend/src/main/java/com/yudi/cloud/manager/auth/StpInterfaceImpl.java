package com.yudi.cloud.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.manager.auth.model.SpaceUserPermissionConstant;
import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.entity.SpaceUser;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.enums.SpaceRoleEnum;
import com.yudi.cloud.model.enums.SpaceTypeEnum;
import com.yudi.cloud.service.PictureService;
import com.yudi.cloud.service.SpaceService;
import com.yudi.cloud.service.SpaceUserService;
import com.yudi.cloud.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.yudi.cloud.contstant.UserConstant.USER_LOGIN_STATUS;

@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        log.debug("权限验证开始: loginId={}, loginType={}", loginId, loginType);
        
        // 判断 loginType，仅对类型为 "space" 进行权限校验
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            log.debug("非space类型权限验证，跳过");
            return new ArrayList<>();
        }
        
        // 获取登录用户信息
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATUS);
        if (loginUser == null) {
            log.debug("用户未登录: {}", loginId);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        log.debug("登录用户: {}", loginUser.getUserName());
        
        // 获取上下文对象
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        log.debug("认证上下文: {}", authContext);
        
        // 如果所有字段都为空，表示查询公共图库，管理员有所有权限，普通用户只有查看权限
        if (isAllFieldsNull(authContext)) {
            log.debug("所有字段都为空，查询公共图库");
            if (userService.isAdmin(loginUser)) {
                return spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
            } else {
                return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
            }
        }
        
        log.debug("需要验证空间权限，认证上下文: {}", authContext);
        
        Long userId = loginUser.getId();
        
        // 优先从上下文中获取 SpaceUser 对象
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        
        // 如果有 spaceUserId，通过数据库查询 SpaceUser 对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.CANNOT_FOUND_DATA_ERROR, "未找到空间用户信息");
            }
            // 查询当前登录用户在该空间的角色
            QueryWrapper<SpaceUser> queryWrapper1 = new QueryWrapper<>();
            queryWrapper1.eq("spaceId", spaceUser.getSpaceId())
                        .eq("userId", userId);
            SpaceUser loginSpaceUser = spaceUserService.getOne(queryWrapper1);
            log.debug("通过spaceUserId查询到的SpaceUser: {}", loginSpaceUser);
            if (loginSpaceUser == null) {
                log.debug("用户{}在空间{}中没有角色，返回空权限列表", userId, spaceUser.getSpaceId());
                return new ArrayList<>();
            }
            log.debug("用户{}在空间{}中的角色: {}", userId, spaceUser.getSpaceId(), loginSpaceUser.getSpaceRole());
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        
        // 通过 spaceId 或 pictureId 获取 Space 对象
        Long spaceId = authContext.getSpaceId();
        log.debug("从认证上下文获取spaceId: {}", spaceId);
        if (spaceId == null) {
            // 通过 pictureId 获取 Space 对象
            Long pictureId = authContext.getPictureId();
            if (pictureId == null) {
                // 没有具体资源ID，返回管理员权限或查看权限
                if (userService.isAdmin(loginUser)) {
                    return spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
                } else {
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
            
            QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
            pictureQueryWrapper.eq("id", pictureId)
                              .select("id", "spaceId", "userId");
            Picture picture = pictureService.getOne(pictureQueryWrapper);
            if (picture == null) {
                throw new BusinessException(ErrorCode.CANNOT_FOUND_DATA_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            
            // 公共图库（spaceId为null），仅本人或管理员可操作
            if (spaceId == null) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
                } else {
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        
        // 获取 Space 对象并判断权限
        Space space = spaceService.getById(spaceId);
        log.debug("通过spaceId {}查询到的Space: {}", spaceId, space);
        if (space == null) {
            throw new BusinessException(ErrorCode.CANNOT_FOUND_DATA_ERROR, "未找到空间信息");
        }
        
        // 根据 Space 类型判断权限
        log.debug("Space类型: {}, 用户ID: {}, 空间所有者ID: {}", space.getSpaceType(), userId, space.getUserId());
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，只有空间创建者才能删除图片
            if (space.getUserId().equals(userId)) {
                log.debug("私有空间权限验证通过，用户{}是空间所有者", userId);
                return spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
            } else {
                log.debug("私有空间权限验证失败，用户{}不是空间所有者", userId);
                return new ArrayList<>();
            }
        } else {
            // 团队空间，查询 SpaceUser 并获取角色和权限
            QueryWrapper<SpaceUser> queryWrapper2 = new QueryWrapper<>();
            queryWrapper2.eq("spaceId", spaceId)
                        .eq("userId", userId);
            spaceUser = spaceUserService.getOne(queryWrapper2);
            log.debug("团队空间查询到的SpaceUser: {}", spaceUser);
            if (spaceUser == null) {
                log.debug("用户{}在团队空间{}中没有角色，返回空权限列表", userId, spaceId);
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }


    @Override
    public List<String> getRoleList(Object o, String s) {
        return Collections.emptyList();
    }

    /**
     * 从当前HTTP请求中提取用户认证上下文信息
     * @return SpaceUserAuthContext 包含认证信息的上下文对象
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        // 从Spring的RequestContextHolder中获取当前线程绑定的HTTP请求对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        // 获取请求头中的Content-Type，用于判断请求数据的格式
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        String requestURI = request.getRequestURI();
        log.debug("权限验证请求: URI={}, ContentType={}", requestURI, contentType);
        
        SpaceUserAuthContext authRequest = new SpaceUserAuthContext();
        
        // 根据Content-Type判断请求数据格式并进行相应的解析
        if(ContentType.JSON.getValue().equals(contentType)) {
            String body = ServletUtil.getBody(request);
            // 尝试解析为JSON对象
            try {
                // 先尝试解析为通用的JSON对象
                JSONObject jsonObj = JSONUtil.parseObj(body);
                
                // 从JSON对象中提取相关字段
                Long id = jsonObj.getLong("id");
                Long spaceId = jsonObj.getLong("spaceId");
                Long pictureId = jsonObj.getLong("pictureId");
                Long spaceUserId = jsonObj.getLong("spaceUserId");
                
                // 设置到认证上下文中
                authRequest.setId(id);
                authRequest.setSpaceId(spaceId);
                authRequest.setPictureId(pictureId);
                authRequest.setSpaceUserId(spaceUserId);
                
                log.debug("解析JSON参数: id={}, spaceId={}, pictureId={}, spaceUserId={}", 
                    id, spaceId, pictureId, spaceUserId);
                
            } catch (Exception e) {
                // 如果解析失败，尝试解析为SpaceUserAuthContext
                try {
                    authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
                } catch (Exception ex) {
                    // 解析失败，返回空的认证上下文
                    return authRequest;
                }
            }
        } else {
            // 对于非JSON请求（如GET请求），从URL参数中获取ID
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            String idStr = paramMap.get("id");
            log.debug("GET请求参数: id={}", idStr);
            if (StrUtil.isNotBlank(idStr)) {
                try {
                    Long id = Long.parseLong(idStr);
                    authRequest.setId(id);
                    log.debug("解析到ID: {}", id);
                } catch (NumberFormatException e) {
                    log.debug("ID解析失败: {}", idStr);
                }
            }
        }
        
        Long id = authRequest.getId();
        // 如果ID不为空，根据请求的URI路径来确定这个ID的具体含义
        if (ObjUtil.isNotNull(id)){
            // 获取相对路径  /api/picture/list -> picture/list
            String partURI = requestURI.replace(contextPath + "/", "");
            // 提取模块名称（URI的第一个路径段） 例如：picture/list -> picture
            String moduleName = StrUtil.subBefore(partURI, "/", false);
            switch (moduleName){
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }

    /**
     * 判断对象的所有字段是否为空
     *
     * @param object
     * @return
     */
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }
}
