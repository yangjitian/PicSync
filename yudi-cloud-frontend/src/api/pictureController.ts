// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** deletePicture POST /api/picture/delete */
export async function deletePictureUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePictureDeleteResponse_>('/api/picture/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** editPicture POST /api/picture/edit */
export async function editPictureUsingPost(
  body: API.PictureEditRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/picture/edit', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** editPictureByBatch POST /api/picture/edit/batch */
export async function editPictureByBatchUsingPost(
  body: API.PictureEditByBatchRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/picture/edit/batch', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** getPictureById GET /api/picture/get */
export async function getPictureByIdUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getPictureByIdUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePicture_>('/api/picture/get', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** getPictureVOById GET /api/picture/get/vo */
export async function getPictureVoByIdUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getPictureVOByIdUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePictureVO_>('/api/picture/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** listPictureByPage POST /api/picture/list/page */
export async function listPictureByPageUsingPost(
  body: API.PictureQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePagePicture_>('/api/picture/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listPictureVOByPage POST /api/picture/list/page/vo */
export async function listPictureVoByPageUsingPost(
  body: API.PictureQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePagePictureVO_>('/api/picture/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listPictureVOByPageWithCache POST /api/picture/list/page/vo/cache */
export async function listPictureVoByPageWithCacheUsingPost(
  body: API.PictureQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePagePictureVO_>('/api/picture/list/page/vo/cache', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listPublishedPictureVOByPage POST /api/picture/list/page/vo/published */
export async function listPublishedPictureVoByPageUsingPost(
  body: API.PictureQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePagePictureVO_>('/api/picture/list/page/vo/published', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** createPictureOutPaintingTask POST /api/picture/out_painting/create_task */
export async function createPictureOutPaintingTaskUsingPost(
  body: API.CreatePictureOutPaintingTaskRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseCreateOutPaintingTaskResponse_>(
    '/api/picture/out_painting/create_task',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      data: body,
      ...(options || {}),
    }
  )
}

/** getPictureOutPaintingTask GET /api/picture/out_painting/get_task */
export async function getPictureOutPaintingTaskUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getPictureOutPaintingTaskUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseGetOutPaintingTaskResponse_>(
    '/api/picture/out_painting/get_task',
    {
      method: 'GET',
      params: {
        ...params,
      },
      ...(options || {}),
    }
  )
}

/** doPictureReview POST /api/picture/review */
export async function doPictureReviewUsingPost(
  body: API.PictureReviewRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/picture/review', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** searchPictureByColor POST /api/picture/search/color */
export async function searchPictureByColorUsingPost(
  body: API.SearchPictureByColorRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListPictureVO_>('/api/picture/search/color', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** searchLikedPicturesByColor POST /api/picture/search/liked/color */
export async function searchLikedPicturesByColorUsingPost(
  body: API.SearchPictureByColorRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListPictureVO_>('/api/picture/search/liked/color', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** searchCollectedPicturesByColor POST /api/picture/search/collected/color */
export async function searchCollectedPicturesByColorUsingPost(
  body: API.SearchPictureByColorRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListPictureVO_>('/api/picture/search/collected/color', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** searchPictureByPicture POST /api/picture/search/picture */
export async function searchPictureByPictureUsingPost(
  body: API.SearchPictureByPictureRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListImageSearchResult_>('/api/picture/search/picture', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listPictureTagCategory GET /api/picture/tag_category */
export async function listPictureTagCategoryUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponsePictureTagCategory_>('/api/picture/tag_category', {
    method: 'GET',
    ...(options || {}),
  })
}

/** updatePicture POST /api/picture/update */
export async function updatePictureUsingPost(
  body: API.PictureUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/picture/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** uploadPicture POST /api/picture/upload */
export async function uploadPictureUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.uploadPictureUsingPOSTParams,
  body: {},
  file?: File,
  options?: { [key: string]: any }
) {
  const formData = new FormData()

  if (file) {
    formData.append('file', file)
  }

  Object.keys(body).forEach((ele) => {
    const item = (body as any)[ele]

    if (item !== undefined && item !== null) {
      if (typeof item === 'object' && !(item instanceof File)) {
        if (item instanceof Array) {
          item.forEach((f) => formData.append(ele, f || ''))
        } else {
          formData.append(ele, JSON.stringify(item))
        }
      } else {
        formData.append(ele, item)
      }
    }
  })

  return request<API.BaseResponsePictureVO_>('/api/picture/upload', {
    method: 'POST',
    params: {
      ...params,
    },
    data: formData,
    requestType: 'form',
    ...(options || {}),
  })
}

/** uploadPictureByBatch POST /api/picture/upload/batch */
export async function uploadPictureByBatchUsingPost(
  body: API.PictureUploadByBatchRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseInt_>('/api/picture/upload/batch', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** uploadPictureByUrl POST /api/picture/upload/url */
export async function uploadPictureByUrlUsingPost(
  body: API.PictureUploadRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePictureVO_>('/api/picture/upload/url', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

// ==================== 图片行为相关接口 ====================

/** 增加浏览量 POST /api/picture/action/view */
export async function addPictureViewUsingPost(
  pictureId: number,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject_>('/api/picture/action/view', {
    method: 'POST',
    params: {
      pictureId,
    },
    ...(options || {}),
  })
}

/** 点赞/取消点赞 POST /api/picture/action/like */
export async function togglePictureLikeUsingPost(
  pictureId: number,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject_>('/api/picture/action/like', {
    method: 'POST',
    params: {
      pictureId,
    },
    ...(options || {}),
  })
}

/** 收藏/取消收藏 POST /api/picture/action/collect */
export async function togglePictureCollectUsingPost(
  pictureId: number,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject_>('/api/picture/action/collect', {
    method: 'POST',
    params: {
      pictureId,
    },
    ...(options || {}),
  })
}

/** 增加分享数 POST /api/picture/action/share */
export async function addPictureShareUsingPost(
  pictureId: number,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject_>('/api/picture/action/share', {
    method: 'POST',
    params: {
      pictureId,
    },
    ...(options || {}),
  })
}

/** 增加下载量 POST /api/picture/action/download */
export async function addPictureDownloadUsingPost(
  pictureId: number,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject_>('/api/picture/action/download', {
    method: 'POST',
    params: {
      pictureId,
    },
    ...(options || {}),
  })
}

/** 生成图片分享链接 GET /api/picture/action/share-link/{pictureId} */
export async function generatePictureShareLinkUsingGet(
  pictureId: number,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString_>(`/api/picture/action/share-link/${pictureId}`, {
    method: 'GET',
    ...(options || {}),
  })
}

/** 获取用户对图片的行为状态 GET /api/picture/action/user-action */
export async function getPictureUserActionUsingGet(
  pictureId: number,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseUserPictureActionStatus_>('/api/picture/action/user-action', {
    method: 'GET',
    params: {
      pictureId,
    },
    ...(options || {}),
  })
}

/** 批量获取用户对多张图片的行为状态 GET /api/picture/action/user-actions */
export async function batchGetPictureUserActionsUsingGet(
  pictureIds: string,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapLongUserPictureActionStatus_>('/api/picture/action/user-actions', {
    method: 'GET',
    params: {
      pictureIds,
    },
    ...(options || {}),
  })
}

/** 获取用户点赞的图片列表 POST /api/picture/action/liked-pictures */
export async function getUserLikedPicturesUsingPost(
  body: API.PictureQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePagePictureVO_>('/api/picture/action/liked-pictures', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 获取用户收藏的图片列表 POST /api/picture/action/collected-pictures */
export async function getUserCollectedPicturesUsingPost(
  body: API.PictureQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePagePictureVO_>('/api/picture/action/collected-pictures', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 获取用户统计数据 GET /api/picture/action/user-stats */
export async function getUserStatsUsingGet(
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject_>('/api/picture/action/user-stats', {
    method: 'GET',
    ...(options || {}),
  })
}
