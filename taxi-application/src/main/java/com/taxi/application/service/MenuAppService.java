package com.taxi.application.service;

import com.taxi.application.dto.MenuNode;
import com.taxi.application.dto.MenuSaveRequest;
import com.taxi.application.support.MenuTreeBuilder;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import com.taxi.domain.entity.Menu;
import com.taxi.domain.repository.MenuRepository;
import com.taxi.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 菜单与权限管理用例。
 */
@Service
public class MenuAppService {

    private static final Logger log = LoggerFactory.getLogger(MenuAppService.class);

    private final MenuRepository menuRepository;
    private final UserRepository userRepository;

    public MenuAppService(MenuRepository menuRepository, UserRepository userRepository) {
        this.menuRepository = menuRepository;
        this.userRepository = userRepository;
    }

    /** 全量菜单树（菜单管理页） */
    public List<MenuNode> tree() {
        return MenuTreeBuilder.build(menuRepository.findAllOrdered());
    }

    /** 当前登录用户可见菜单树（前端动态路由） */
    public List<MenuNode> myMenus() {
        LoginUser current = UserContext.require();
        List<Long> roleIds = userRepository.findRoleIds(current.userId());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Menu> menus = menuRepository.findByRoleIds(roleIds).stream()
                .filter(menu -> menu.getVisible() == null || menu.getVisible() == 1)
                .toList();
        return MenuTreeBuilder.build(menus);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MenuSaveRequest request) {
        validate(request, null);
        Menu menu = new Menu();
        apply(menu, request);
        menuRepository.save(menu);
        log.info("新增菜单 menuId={} name={}", menu.getId(), menu.getMenuName());
        return menu.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MenuSaveRequest request) {
        Menu existing = menuRepository.findById(id).orElseThrow(() -> BizException.notFound("菜单不存在"));
        if (request.parentId() != null && request.parentId().equals(id)) {
            throw BizException.badRequest("上级菜单不能是自己");
        }
        validate(request, id);
        Menu patch = new Menu();
        patch.setId(existing.getId());
        apply(patch, request);
        menuRepository.update(patch);
        log.info("修改菜单 menuId={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        menuRepository.findById(id).orElseThrow(() -> BizException.notFound("菜单不存在"));
        long children = menuRepository.countChildren(id);
        if (children > 0) {
            throw BizException.badRequest("存在 " + children + " 个子菜单，请先删除子节点");
        }
        menuRepository.deleteById(id);
        log.warn("删除菜单 menuId={} operator={}", id, UserContext.currentUsername());
    }

    private void validate(MenuSaveRequest request, Long excludeId) {
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        if (parentId != 0L) {
            Menu parent = menuRepository.findById(parentId)
                    .orElseThrow(() -> BizException.badRequest("上级菜单不存在"));
            if (parent.getMenuType() != null && parent.getMenuType() == 3) {
                throw BizException.badRequest("按钮节点不能作为上级菜单");
            }
        }
        if (StringUtils.hasText(request.permCode())
                && menuRepository.countByPermCode(request.permCode().trim(), excludeId) > 0) {
            throw BizException.badRequest("权限码已存在");
        }
    }

    private void apply(Menu menu, MenuSaveRequest request) {
        menu.setParentId(request.parentId() == null ? 0L : request.parentId());
        menu.setMenuType(request.menuType() == null ? 2 : request.menuType());
        menu.setMenuName(request.menuName().trim());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setPermCode(StringUtils.hasText(request.permCode()) ? request.permCode().trim() : null);
        menu.setIcon(request.icon());
        menu.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        menu.setVisible(request.visible() == null ? 1 : request.visible());
    }
}
