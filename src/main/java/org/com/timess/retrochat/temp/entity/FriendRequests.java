package org.com.timess.retrochat.temp.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友申请表 实体类。
 *
 * @author eternal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("friend_requests")
public class FriendRequests implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 发起者ID
     */
    private Long fromUserId;

    /**
     * 接收者ID
     */
    private Long toUserId;

    /**
     * 验证消息
     */
    private String requestMessage;

    /**
     * 0-待处理 1-已同意 2-已拒绝
     */
    private Integer status;

    /**
     * 是否已读
     */
    private Boolean isRead;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 处理时间
     */
    private LocalDateTime processTime;

}
