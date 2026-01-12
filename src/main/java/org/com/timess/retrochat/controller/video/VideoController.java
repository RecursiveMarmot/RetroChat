package org.com.timess.retrochat.controller.video;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.com.timess.retrochat.common.BaseResponse;
import org.com.timess.retrochat.common.ResultUtils;
import org.com.timess.retrochat.exception.BusinessException;
import org.com.timess.retrochat.exception.ErrorCode;
import org.com.timess.retrochat.model.vo.VideoSignalingVO;
import org.com.timess.retrochat.service.VideoSignalingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/video")
public class VideoController {

    @Autowired
    private VideoSignalingService signalingService;

    @PostMapping("/call")
    public BaseResponse<VideoSignalingVO> getVideoSignaling(@RequestBody String jsonString, HttpServletRequest request) {
        try{
            // 解析为JsonObject
            JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
            // 直接获取字段值
            String toUserId = jsonObject.get("toUserId").getAsString();
            return ResultUtils.success(signalingService.handleSignaling(toUserId, request));
        }catch (Exception e){
            log.error(e.getMessage());
            throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }
}
