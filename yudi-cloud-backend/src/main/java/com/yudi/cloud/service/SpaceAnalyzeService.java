package com.yudi.cloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yudi.cloud.model.dto.space.analyze.*;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.vo.space.analyze.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author yudi
 * date 2025-07-20
 */
public interface SpaceAnalyzeService extends IService<Space> {

    /**
     * 获取空间使用情况分析
     *
     * @param spaceUsageAnalyzeRequest
     * @param request
     * @return
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,
                                                   HttpServletRequest request);

    /**
     * 分析图片分类情况
     *
     * @param spaceCategoryAnalyzeRequest
     * @param request
     * @return
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest
            , HttpServletRequest request);

    /**
     * 图片标签分析
     *
     * @param spaceTagAnalyzeRequest
     * @param request
     * @return
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest,
                                                     HttpServletRequest request);

    /**
     * 获取空间图片大小分析
     *
     * @param spaceSizeAnalyzeRequest
     * @param request
     * @return
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest,

                                                       HttpServletRequest request);

    /**
     * 获取空间用户上传行为分析
     *
     * @param spaceUserAnalyzeRequest
     * @param request
     * @return
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest,
                                                       HttpServletRequest request);
    /**
     * 空间使用排行分析（仅管理员）
     *
     * @param spaceRankAnalyzeRequest
     * @param request
     * @return
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, HttpServletRequest request);
}
