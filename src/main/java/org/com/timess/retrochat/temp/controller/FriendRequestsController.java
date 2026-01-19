package org.com.timess.retrochat.temp.controller;

import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.com.timess.retrochat.temp.entity.FriendRequests;
import org.com.timess.retrochat.temp.service.FriendRequestsService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 好友申请表 控制层。
 *
 * @author eternal
 */
@RestController
@RequestMapping("/friendRequests")
public class FriendRequestsController {

    @Autowired
    private FriendRequestsService friendRequestsService;

    /**
     * 保存好友申请表。
     *
     * @param friendRequests 好友申请表
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public boolean save(@RequestBody FriendRequests friendRequests) {
        return friendRequestsService.save(friendRequests);
    }

    /**
     * 根据主键删除好友申请表。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable Long id) {
        return friendRequestsService.removeById(id);
    }

    /**
     * 根据主键更新好友申请表。
     *
     * @param friendRequests 好友申请表
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    public boolean update(@RequestBody FriendRequests friendRequests) {
        return friendRequestsService.updateById(friendRequests);
    }

    /**
     * 查询所有好友申请表。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public List<FriendRequests> list() {
        return friendRequestsService.list();
    }

    /**
     * 根据主键获取好友申请表。
     *
     * @param id 好友申请表主键
     * @return 好友申请表详情
     */
    @GetMapping("getInfo/{id}")
    public FriendRequests getInfo(@PathVariable Long id) {
        return friendRequestsService.getById(id);
    }

    /**
     * 分页查询好友申请表。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public Page<FriendRequests> page(Page<FriendRequests> page) {
        return friendRequestsService.page(page);
    }

}
