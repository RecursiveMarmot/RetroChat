package org.com.timess.retrochat.temp.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.com.timess.retrochat.temp.entity.FriendRequests;
import org.com.timess.retrochat.temp.mapper.FriendRequestsMapper;
import org.com.timess.retrochat.temp.service.FriendRequestsService;
import org.springframework.stereotype.Service;

/**
 * 好友申请表 服务层实现。
 *
 * @author eternal
 */
@Service
public class FriendRequestsServiceImpl extends ServiceImpl<FriendRequestsMapper, FriendRequests>  implements FriendRequestsService{

}
