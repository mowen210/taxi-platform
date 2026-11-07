package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.TerminalDevice;

/**
 * 终端档案仓储。
 */
public interface TerminalDeviceRepository {

    TerminalDevice findById(Long id);

    TerminalDevice findByTerminalId(String terminalId);

    PageResult<TerminalDevice> search(String keyword, Integer onlineStatus, int page, int size);

    void save(TerminalDevice device);

    void update(TerminalDevice device);

    void delete(Long id);
}
