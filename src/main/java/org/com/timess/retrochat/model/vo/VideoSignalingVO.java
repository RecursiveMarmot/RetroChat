package org.com.timess.retrochat.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 返回当前登录用户信息
 * @author eternal
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VideoSignalingVO implements Serializable {
    private static final long serialVersionUID = -6878355451312782724L;

    /**
     * 视频通话响应结果
     */
    private boolean signal;

    /**
     * 描述
     */
    private String content;

    /**
     * 聊天id
     */
//    private String roomId;

}
