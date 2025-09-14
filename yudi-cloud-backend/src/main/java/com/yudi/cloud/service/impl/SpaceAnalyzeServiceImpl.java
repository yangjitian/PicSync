package com.yudi.cloud.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.mapper.SpaceMapper;
import com.yudi.cloud.model.dto.space.analyze.*;
import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.vo.space.analyze.*;
import com.yudi.cloud.service.PictureService;
import com.yudi.cloud.service.SpaceAnalyzeService;
import com.yudi.cloud.service.SpaceService;
import com.yudi.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceAnalyzeService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    /**
     * 获取空间使用情况分析
     *
     * @param spaceUsageAnalyzeRequest
     * @param request
     * @return
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,
                                                          HttpServletRequest request) {
        // 校验
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, request);
            // 统计图库的使用空间
            QueryWrapper<Picture> queryWrapper = new QueryWrapper();
            queryWrapper.select("picSize");
            // 补充查询范围
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            long usedSize = pictureObjList.stream().mapToLong(obj -> (Long) obj).sum();
            long usedCount = pictureObjList.size();
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            // 公共图库（或者全部空间）无数量和容量限制、也没有比例
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        }else {
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, request);
            Space space = spaceService.getById(spaceId);
            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
            // 计算比例
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            spaceUsageAnalyzeResponse.setCountUsageRatio(countUsageRatio);
            return spaceUsageAnalyzeResponse;
        }
    }

    /**
     * 分析图片分类情况
     *
     * @param spaceCategoryAnalyzeRequest
     * @param request
     * @return
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest,
                                                                HttpServletRequest request) {
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, request);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);
        queryWrapper.select("category","COUNT(*) AS count","COALESCE(SUM(picSize),0) AS totalSize")
                .groupBy("category")
                .orderBy(true,false,"count");
        try {
            List<Map<String, Object>> resultMaps = pictureService.getBaseMapper().selectMaps(queryWrapper);
            if (CollectionUtils.isEmpty(resultMaps)){
                return Collections.emptyList();
            }
          return resultMaps.stream().map(result->{
                String category = Optional.ofNullable(result.get("category"))
                                .map(Object::toString)
                                .orElse("未分类");
                Long count = Optional.ofNullable(result.get("count"))
                                .map(obj -> obj instanceof Number ? ((Number) obj).longValue() : 0L)
                                .orElse(0L);
                Long totalSize = Optional.ofNullable(result.get("totalSize"))
                                .map(obj -> obj instanceof Number ? ((Number) obj).longValue() : 0L)
                                .orElse(0L);
                return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                    }).filter(response -> response.getCount() > 0)
                  .collect(Collectors.toList());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取分析数据失败");
        }
    }


    /**
     * 图片标签分析
     *
     * @param spaceTagAnalyzeRequest
     * @param request
     * @return
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, HttpServletRequest request) {
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, request);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);
        queryWrapper.select("tags")
                .isNotNull("tags")
                .ne("tags","[]")
                .ne("tags","");
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                .map(Object::toString)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(tagsJsonList)) {
            return Collections.emptyList();
        }
        // 解析标签并统计
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> {
                    try {
                        return JSONUtil.toList(tagsJson, String.class).stream()
                                .filter(StringUtils::isNotBlank);
                    } catch (Exception e) {
                        log.warn("解析标签失败: {}", tagsJson);
                        return Stream.empty();
                    }
                }).collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));
        // 转化为响应对象，使用次数降序排序
        return tagCountMap.entrySet().stream()
                .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                .limit(20)
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 获取空间图片大小分析
     *
     * @param spaceSizeAnalyzeRequest
     * @param request
     * @return
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, HttpServletRequest request) {
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, request);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);
        queryWrapper.select("picSize").isNotNull("picSize");
        List<Object> sizeObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
        if (CollectionUtils.isEmpty(sizeObjList)) {
            return Arrays.asList(
                    new SpaceSizeAnalyzeResponse("< 100KB", 0L),
                    new SpaceSizeAnalyzeResponse("100KB-500KB", 0L),
                    new SpaceSizeAnalyzeResponse("500KB-1MB", 0L),
                    new SpaceSizeAnalyzeResponse("1MB-5MB", 0L),
                    new SpaceSizeAnalyzeResponse("> 5MB", 0L)
            );
        }
        // 一次遍历完成统计
        long count100KB = 0, count500KB = 0, count1MB = 0, count5MB = 0, countOver5MB = 0;
        for (Object obj : sizeObjList) {
            if (obj == null) continue;
            long size = obj instanceof Number ? ((Number) obj).longValue() : 0L;
            if (size < 100 * 1024L) {
                count100KB++;
            } else if (size < 500 * 1024L) {
                count500KB++;
            } else if (size < 1024 * 1024L) {
                count1MB++;
            } else if (size < 5 * 1024 * 1024L) {
                count5MB++;
            } else {
                countOver5MB++;
            }
        }
        // 构建返回结果
        return Arrays.asList(
                new SpaceSizeAnalyzeResponse("< 100KB", count100KB),
                new SpaceSizeAnalyzeResponse("100KB-500KB", count500KB),
                new SpaceSizeAnalyzeResponse("500KB-1MB", count1MB),
                new SpaceSizeAnalyzeResponse("1MB-5MB", count5MB),
                new SpaceSizeAnalyzeResponse("> 5MB", countOver5MB)
        );
    }

    /**
     * 获取空间用户上传行为分析
     *
     * @param spaceUserAnalyzeRequest
     * @param request
     * @return
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, HttpServletRequest request) {
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        ThrowUtils.throwIf(StringUtils.isBlank(timeDimension), ErrorCode.PARAMETER_ERROR, "时间维度不能为空");
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, request);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
        Long userId = spaceUserAnalyzeRequest.getUserId();
        if (userId != null && userId > 0) {
            queryWrapper.eq("userId", userId);
        }
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        // 根据时间维度设置查询
        String periodFormat;
        switch (timeDimension.toLowerCase()) {
            case "day":
                periodFormat = "DATE_FORMAT(createTime, '%Y-%m-%d')";
                break;
            case "week":
                periodFormat = "DATE_FORMAT(createTime, '%Y年第%u周')";
                break;
            case "month":
                periodFormat = "DATE_FORMAT(createTime, '%Y-%m')";
                break;
            case "year":
                periodFormat = "DATE_FORMAT(createTime, '%Y')";
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "不支持的时间维度: " + timeDimension);
        }
        // 设置查询字段
        queryWrapper.select(
                periodFormat + " as " + "period",
                "COUNT(*) as count",
                "SUM(picSize) as totalSize"
        );
        // 分组排序
        queryWrapper.groupBy("period").orderByAsc("period");
        // 查询并封装结果
        List<Map<String, Object>> queryResult = pictureService.getBaseMapper().selectMaps(queryWrapper);
        return queryResult
                .stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    Long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());
    }

    /**
     * 空间使用排行分析（仅管理员）
     *
     * @param spaceRankAnalyzeRequest
     * @param request
     * @return
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, HttpServletRequest request) {
        // 校验
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMETER_ERROR);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "仅管理员可查看空间排行");

        Integer topN = spaceRankAnalyzeRequest.getTopN();
        // 构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize", "totalCount")
                .gt("totalSize", 0) // 过滤空的空间
                .orderByDesc("totalSize")
                .last("LIMIT " + topN);
        try {
            return spaceService.list(queryWrapper);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取空间排行榜失败");
        }
    }

    /**
     * 校验空间权限
     *
     * @param spaceAnalyzeRequest
     * @param request
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, HttpServletRequest request) {
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        User loginUser = userService.getLoginUser(request);
        // 全空间分析或者公共图库权限校验：仅管理员可访问
        if (queryAll || queryPublic) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        } else {
            spaceService.validateSpaceAccess(spaceAnalyzeRequest.getSpaceId(), request);
        }
    }

    /**
     * 校验空间分析权限
     *
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    private void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMETER_ERROR, "未指定查询范围");
    }
}
