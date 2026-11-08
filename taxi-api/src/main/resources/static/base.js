/* 基础信息设置：车队、司机、车辆、终端绑定、发票，无构建依赖。 */
(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const token = sessionStorage.getItem('taxi.token') || '';
  if (!token) {
    location.replace('/login?redirect=/base');
    return;
  }

  const state = {
    me: null,
    info: null,
    stats: null,
    fleets: [],
    fleetOptions: [],
    drivers: [],
    driverOptions: [],
    vehicles: [],
    terminals: [],
    invoices: [],
    orders: [],
    invoicePage: 1,
    invoiceTotal: 0,
    selectedVehicle: null,
    tab: 'fleets'
  };

  const fleetStatus = { 1: ['正常', 'ok'], 0: ['停用', 'off'] };
  const driverStatus = { 1: ['在职', 'ok'], 2: ['离职', 'off'], 3: ['停运', 'warn'] };
  const vehicleStatus = { 1: ['空闲', 'ok'], 2: ['营运中', 'blue'], 3: ['维修', 'warn'], 4: ['停运', 'off'] };
  const invoiceStatus = { 1: ['待开', 'warn'], 2: ['开票中', 'blue'], 3: ['成功', 'ok'], 4: ['失败', 'bad'], 5: ['已红冲', 'off'] };

  const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
  const idEq = (a, b) => String(a == null ? '' : a) === String(b == null ? '' : b);
  const value = (v) => v == null || v === '' ? '—' : v;
  const money = (v) => '¥ ' + Number(v || 0).toFixed(2);
  const time = (v) => v ? String(v).replace('T', ' ').slice(0, 19) : '—';
  const date = (v) => v ? String(v).slice(0, 10) : '—';

  function badge(map, key) {
    const item = map[Number(key)] || [String(key == null ? '—' : key), 'off'];
    return '<span class="badge ' + item[1] + '">' + esc(item[0]) + '</span>';
  }

  function daysBadge(dateValue, days) {
    if (!dateValue) return '<span class="muted">—</span>';
    const n = Number(days);
    let cls = 'ok';
    let suffix = '';
    if (days != null) {
      if (n < 0) { cls = 'bad'; suffix = ' 已过期'; }
      else if (n <= 30) { cls = 'warn'; suffix = ' 剩 ' + n + ' 天'; }
    }
    return '<span class="badge ' + cls + '">' + esc(date(dateValue)) + suffix + '</span>';
  }

  let toastTimer = null;
  function toast(message, error) {
    const el = $('toast');
    el.textContent = message || '';
    el.classList.toggle('error', !!error);
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 3200);
  }

  async function api(path, options = {}) {
    const headers = Object.assign({}, options.headers || {}, { Authorization: 'Bearer ' + token });
    let body = options.body;
    if (body != null && typeof body !== 'string') {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }
    const resp = await fetch(path, Object.assign({}, options, { headers, body }));
    const result = await resp.json().catch(() => null);
    if (!result) throw new Error('服务返回异常（HTTP ' + resp.status + '）');
    if (result.code === 401) {
      sessionStorage.removeItem('taxi.token');
      sessionStorage.removeItem('taxi.driver.token');
      location.replace('/login?redirect=/base');
      throw new Error('登录已失效');
    }
    if (result.code !== 200) throw new Error(result.msg || ('请求失败（' + result.code + '）'));
    return result.data;
  }

  function formData(form) {
    const data = {};
    new FormData(form).forEach((v, k) => { data[k] = typeof v === 'string' ? v.trim() : v; });
    return data;
  }

  let modalHandler = null;
  function openModal(title, html, submitText, onSubmit) {
    $('modalTitle').textContent = title;
    $('modalBody').innerHTML = '<div class="form-error" id="formError"></div><form id="modalForm">' + html + '</form>';
    $('modalSubmit').textContent = submitText || '保存';
    $('modalSubmit').style.display = '';
    $('modalSubmit').disabled = false;
    $('modal').classList.add('show');
    modalHandler = async (event) => {
      event.preventDefault();
      const form = $('modalForm');
      const error = $('formError');
      error.classList.remove('show');
      $('modalSubmit').disabled = true;
      try {
        await onSubmit(formData(form), form);
        closeModal();
      } catch (e) {
        error.textContent = e.message || '保存失败';
        error.classList.add('show');
        $('modalSubmit').disabled = false;
      }
    };
    $('modalForm').addEventListener('submit', modalHandler);
    const first = $('modalForm').querySelector('input,select,textarea');
    if (first) setTimeout(() => first.focus(), 40);
  }

  function closeModal() {
    $('modal').classList.remove('show');
    $('modalBody').innerHTML = '';
    modalHandler = null;
  }

  function field(label, control, full, tip) {
    return '<div class="field' + (full ? ' full' : '') + '"><label>' + label + '</label>' + control +
      (tip ? '<span class="tip">' + esc(tip) + '</span>' : '') + '</div>';
  }

  function input(name, val, type, attrs) {
    return '<input name="' + name + '" type="' + (type || 'text') + '" value="' + esc(val == null ? '' : val) + '" ' +
      (attrs || '') + '/>';
  }

  function select(name, options, selected, attrs) {
    return '<select name="' + name + '" ' + (attrs || '') + '>' + options.map((o) =>
      '<option value="' + esc(o[0]) + '"' + (idEq(o[0], selected) ? ' selected' : '') + '>' + esc(o[1]) + '</option>'
    ).join('') + '</select>';
  }

  function statusOptions(map) {
    return Object.keys(map).map((k) => [k, map[k][0]]);
  }

  function fleetOptions(selected, emptyLabel) {
    return (emptyLabel == null ? '<option value="">未指定</option>' : '<option value="">' + esc(emptyLabel) + '</option>') +
      state.fleetOptions.map((f) => '<option value="' + esc(f.id) + '"' + (idEq(f.id, selected) ? ' selected' : '') +
        '>' + esc((f.fleetCode ? f.fleetCode + ' · ' : '') + f.fleetName) + '</option>').join('');
  }

  function driverOptions(selected, emptyLabel) {
    return (emptyLabel == null ? '<option value="">未指定</option>' : '<option value="">' + esc(emptyLabel) + '</option>') +
      state.driverOptions.map((d) => '<option value="' + esc(d.id) + '"' + (idEq(d.id, selected) ? ' selected' : '') +
        '>' + esc(d.driverCode + ' · ' + d.name) + '</option>').join('');
  }

  function fillFleetFilter(id, label, selected) {
    const el = $(id);
    if (!el) return;
    el.innerHTML = '<option value="">' + esc(label) + '</option>' + state.fleetOptions.map((f) =>
      '<option value="' + esc(f.id) + '"' + (idEq(f.id, selected) ? ' selected' : '') + '>' +
      esc(f.fleetName) + '</option>').join('');
  }

  function emptyRow(id, cols, message) {
    $(id).innerHTML = '<tr><td colspan="' + cols + '" class="empty">' + esc(message || '暂无数据') + '</td></tr>';
  }

  function failTable(id, cols, message) {
    emptyRow(id, cols, message || '加载失败');
  }

  async function loadMe() {
    const me = await api('/api/v1/auth/me');
    state.me = me;
    const name = me.realName || me.username || '用户';
    $('userName').textContent = name;
    $('avatar').textContent = String(name).slice(0, 1);
    const perms = Array.isArray(me.permissions) ? me.permissions : [];
    const canManage = perms.includes('fleet:list') || perms.includes('driver:list') || perms.includes('vehicle:list');
    $('userRole').textContent = canManage ? '基础信息管理员' : '司机账号';
    $('ovScope').textContent = (me.fleetId == null ? '全平台数据范围' : '车队 #' + me.fleetId) +
      ' · ' + ({ 1: '全部数据', 2: '本车队', 3: '本人数据' }[me.dataScope] || ('范围 ' + (me.dataScope ?? '-')));
    if (!canManage) location.replace('/driver.html');
  }

  async function loadInfo() {
    const info = await api('/api/v1/system/info');
    state.info = info;
    $('ovPlatform').textContent = info.application || '出租汽车运营监管平台';
    $('ovInvoiceMode').textContent = info.invoiceMock ? '发票：Mock（待接诺诺）' : '发票：正式网关';
  }

  async function loadStats() {
    const s = await api('/api/v1/stats/overview');
    state.stats = s;
    $('ovFleets').textContent = s.fleetCount ?? '—';
    $('ovDrivers').textContent = s.driverCount ?? '—';
    $('ovVehicles').textContent = s.onlineVehicles ?? '—';
  }

  async function loadFleetOptions() {
    state.fleetOptions = await api('/api/v1/fleets/options');
    fillFleetFilter('driverFleetFilter', '全部车队', $('driverFleetFilter').value);
    fillFleetFilter('vehicleFleetFilter', '全部车队', $('vehicleFleetFilter').value);
  }

  async function loadFleets() {
    const qs = new URLSearchParams({ page: '1', size: '100' });
    const keyword = $('fleetKeyword').value.trim();
    const status = $('fleetStatus').value;
    if (keyword) qs.set('keyword', keyword);
    if (status !== '') qs.set('status', status);
    try {
      const page = await api('/api/v1/fleets?' + qs.toString());
      state.fleets = page.list || [];
      $('fleetCountText').textContent = '共 ' + (page.total ?? state.fleets.length) + ' 个车队';
      renderFleets();
      if (!state.stats) $('ovFleets').textContent = page.total ?? state.fleets.length;
    } catch (e) {
      failTable('fleetRows', 7, e.message);
      $('fleetCountText').textContent = '';
    }
  }

  function renderFleets() {
    if (!state.fleets.length) { emptyRow('fleetRows', 7, '暂无车队，点击“新增车队”开始录入'); return; }
    $('fleetRows').innerHTML = state.fleets.map((f) => '<tr>' +
      '<td class="mono">' + esc(f.fleetCode) + '</td>' +
      '<td><b>' + esc(f.fleetName) + '</b></td>' +
      '<td>' + esc(value(f.contact)) + '</td>' +
      '<td>' + esc(value(f.phoneMask)) + '</td>' +
      '<td class="mono">' + esc(value(f.taxNo)) + '</td>' +
      '<td>' + badge(fleetStatus, f.status) + '</td>' +
      '<td><div class="actions">' +
      '<button class="btn small" data-act="edit-fleet" data-id="' + esc(f.id) + '">编辑</button>' +
      '<button class="btn small danger" data-act="del-fleet" data-id="' + esc(f.id) + '">删除</button>' +
      '</div></td></tr>').join('');
  }

  async function loadDriverOptions() {
    const page = await api('/api/v1/drivers?status=1&page=1&size=100');
    state.driverOptions = page.list || [];
  }

  async function loadDrivers() {
    const qs = new URLSearchParams({ page: '1', size: '100' });
    const keyword = $('driverKeyword').value.trim();
    const fleetId = $('driverFleetFilter').value;
    const status = $('driverStatus').value;
    if (keyword) qs.set('keyword', keyword);
    if (fleetId) qs.set('fleetId', fleetId);
    if (status !== '') qs.set('status', status);
    try {
      const page = await api('/api/v1/drivers?' + qs.toString());
      state.drivers = page.list || [];
      $('driverCountText').textContent = '共 ' + (page.total ?? state.drivers.length) + ' 名司机';
      renderDrivers();
      if (!state.stats) $('ovDrivers').textContent = page.total ?? state.drivers.length;
    } catch (e) {
      failTable('driverRows', 8, e.message);
      $('driverCountText').textContent = '';
    }
  }

  function renderDrivers() {
    if (!state.drivers.length) { emptyRow('driverRows', 8, '暂无司机，点击“新增司机”开始录入'); return; }
    $('driverRows').innerHTML = state.drivers.map((d) => '<tr>' +
      '<td class="mono">' + esc(d.driverCode) + '</td>' +
      '<td><b>' + esc(d.name) + '</b></td>' +
      '<td>' + esc(value(d.phoneMask)) + '</td>' +
      '<td class="mono">' + esc(value(d.idCardMask)) + '</td>' +
      '<td>' + esc(value(d.fleetName)) + '</td>' +
      '<td>' + esc(value(d.licenseClass)) + ' · ' + esc(date(d.licenseExpireDate)) + '</td>' +
      '<td>' + badge(driverStatus, d.status) + '</td>' +
      '<td><div class="actions">' +
      '<button class="btn small" data-act="edit-driver" data-id="' + esc(d.id) + '">编辑</button>' +
      '<button class="btn small" data-act="cert-driver" data-id="' + esc(d.id) + '">证件</button>' +
      '<button class="btn small danger" data-act="del-driver" data-id="' + esc(d.id) + '">删除</button>' +
      '</div></td></tr>').join('');
  }

  async function loadVehicles() {
    const qs = new URLSearchParams({ page: '1', size: '100' });
    const keyword = $('vehicleKeyword').value.trim();
    const fleetId = $('vehicleFleetFilter').value;
    const status = $('vehicleStatus').value;
    if (keyword) qs.set('keyword', keyword);
    if (fleetId) qs.set('fleetId', fleetId);
    if (status !== '') qs.set('status', status);
    try {
      const page = await api('/api/v1/vehicles?' + qs.toString());
      state.vehicles = page.list || [];
      $('vehicleCountText').textContent = '共 ' + (page.total ?? state.vehicles.length) + ' 辆车';
      renderVehicles();
      renderVehiclePicker();
      if (state.tab === 'params') renderParams();
      if (!state.stats) $('ovVehicles').textContent = page.total ?? state.vehicles.length;
    } catch (e) {
      failTable('vehicleRows', 8, e.message);
      $('vehicleCountText').textContent = '';
      $('vehiclePicker').innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
    }
  }

  function renderVehicles() {
    if (!state.vehicles.length) { emptyRow('vehicleRows', 8, '暂无车辆，点击“新增车辆”开始录入'); return; }
    $('vehicleRows').innerHTML = state.vehicles.map((v) => '<tr>' +
      '<td><b class="mono">' + esc(v.plateNo) + '</b><div class="muted">' + esc(value(v.plateColor)) + '</div></td>' +
      '<td>' + esc(value(v.vehicleType)) + '</td>' +
      '<td>' + esc(value(v.fleetName)) + '</td>' +
      '<td>' + esc(value(v.driverName)) + '</td>' +
      '<td>' + badge(vehicleStatus, v.status) + '</td>' +
      '<td>' + daysBadge(v.insuranceExpireDate, v.insuranceDaysLeft) + '</td>' +
      '<td>' + daysBadge(v.annualInspectExpireDate, v.inspectDaysLeft) + '</td>' +
      '<td><div class="actions">' +
      '<button class="btn small" data-act="params-vehicle" data-id="' + esc(v.id) + '">参数</button>' +
      '<button class="btn small" data-act="edit-vehicle" data-id="' + esc(v.id) + '">编辑</button>' +
      '<button class="btn small danger" data-act="del-vehicle" data-id="' + esc(v.id) + '">删除</button>' +
      '</div></td></tr>').join('');
  }

  async function loadTerminals() {
    try {
      const page = await api('/api/v1/terminals?page=1&size=100');
      state.terminals = page.list || [];
      renderTerminalRows();
      if (state.tab === 'params') renderParams();
    } catch (e) {
      state.terminals = [];
      failTable('terminalRows', 7, e.message);
    }
  }

  function renderTerminalRows() {
    if (!state.terminals.length) { emptyRow('terminalRows', 7, '暂无终端，点击“新增终端”录入'); return; }
    $('terminalRows').innerHTML = state.terminals.map((t) => '<tr>' +
      '<td class="mono">' + esc(t.terminalId) + '</td>' +
      '<td>' + esc(value(t.terminalCode)) + '</td>' +
      '<td>' + esc(value(t.terminalModel)) + '</td>' +
      '<td>' + esc(value(t.protocolVersion)) + '</td>' +
      '<td>' + (Number(t.onlineStatus) === 1 ? '<span class="badge ok">在线</span>' : '<span class="badge off">离线</span>') + '</td>' +
      '<td>' + esc(value(t.boundPlateNo)) + '</td>' +
      '<td class="mono">' + esc(value(t.boundTaximeterSn)) + '</td>' +
      '</tr>').join('');
  }

  function renderVehiclePicker() {
    $('vehiclePickCount').textContent = state.vehicles.length;
    if (!state.vehicles.length) { $('vehiclePicker').innerHTML = '<div class="empty">暂无车辆</div>'; return; }
    if (!state.selectedVehicle || !state.vehicles.some((v) => idEq(v.id, state.selectedVehicle))) {
      state.selectedVehicle = state.vehicles[0].id;
    }
    $('vehiclePicker').innerHTML = state.vehicles.map((v) => '<button type="button" class="vehicle-pick' +
      (idEq(v.id, state.selectedVehicle) ? ' active' : '') + '" data-act="pick-vehicle" data-id="' + esc(v.id) + '">' +
      '<b>' + esc(v.plateNo) + ' ' + badge(vehicleStatus, v.status) + '</b>' +
      '<small>' + esc(value(v.fleetName)) + ' · ' + esc(value(v.driverName)) + '</small></button>').join('');
  }

  function currentVehicle() {
    return state.vehicles.find((v) => idEq(v.id, state.selectedVehicle)) || null;
  }

  function bindingOf(vehicleId) {
    return state.terminals.find((t) => idEq(t.boundVehicleId, vehicleId)) || null;
  }

  function renderParams() {
    renderVehiclePicker();
    const v = currentVehicle();
    if (!v) {
      $('paramDetail').innerHTML = '<div class="param-card"><h3>车辆参数</h3><div class="body"><div class="empty">请选择左侧车辆</div></div></div>';
      return;
    }
    const binding = bindingOf(v.id);
    const termOptions = state.terminals.filter((t) => t.boundVehicleId == null || idEq(t.boundVehicleId, v.id));
    const bindForm = binding ? '' :
      '<form id="bindForm"><div class="form-grid">' +
      field('终端手机号 <em>*</em>', termOptions.length
        ? '<select name="terminalId" required>' + termOptions.map((t) =>
          '<option value="' + esc(t.terminalId) + '">' +
          esc(t.terminalId + (t.terminalCode ? ' · ' + t.terminalCode : '')) +
          '</option>').join('') + '</select>'
        : '<div class="notice">暂无可用终端，请先点击下方“新增终端”。</div>') +
      field('计价器 SN <em>*</em>', input('taximeterSn', '', 'text', 'required placeholder="例如 TXM-0001"')) +
      field('备注', input('remark', '', 'text', 'placeholder="选填"'), true) +
      '</div><div style="margin-top:12px"><button class="btn primary" type="submit"' + (termOptions.length ? '' : ' disabled') +
      '>保存绑定</button> <button class="btn" type="button" data-act="add-terminal-inline">新增终端</button></div></form>';

    $('paramDetail').innerHTML =
      '<div class="param-card"><h3 style="display:flex;justify-content:space-between;align-items:center">车辆档案' +
      '<button class="btn small" data-act="edit-current-vehicle">编辑</button></h3><div class="body">' +
      '<dl class="info-list"><dt>车牌号</dt><dd><b>' + esc(v.plateNo) + '</b> ' + badge(vehicleStatus, v.status) + '</dd>' +
      '<dt>车辆类型</dt><dd>' + esc(value(v.vehicleType)) + '</dd>' +
      '<dt>所属车队</dt><dd>' + esc(value(v.fleetName)) + '</dd>' +
      '<dt>默认司机</dt><dd>' + esc(value(v.driverName)) + '</dd>' +
      '<dt>车架号</dt><dd class="mono">' + esc(value(v.vin)) + '</dd>' +
      '<dt>保险到期</dt><dd>' + daysBadge(v.insuranceExpireDate, v.insuranceDaysLeft) + '</dd>' +
      '<dt>年检到期</dt><dd>' + daysBadge(v.annualInspectExpireDate, v.inspectDaysLeft) + '</dd></dl></div></div>' +
      '<div class="param-card" style="margin-top:14px"><h3>终端与计价器</h3><div class="body">' +
      (binding
        ? '<div class="notice">当前绑定终端 <b>' + esc(binding.terminalId) + '</b>，计价器 SN <b>' +
          esc(binding.boundTaximeterSn) + '</b>。更换绑定需先解除。</div>' +
          '<button class="btn danger" type="button" data-act="unbind-vehicle">解除绑定</button>'
        : '<div class="notice">该车辆尚未绑定终端与计价器。绑定后才能置为“营运中”。</div>' + bindForm) +
      '</div></div>' +
      '<div class="param-card" style="margin-top:14px"><h3>车辆状态</h3><div class="body">' +
      '<form id="statusForm"><div class="form-grid">' +
      field('状态', '<select name="status">' + statusOptions(vehicleStatus).map(([k, label]) =>
        '<option value="' + k + '"' + (Number(v.status) === Number(k) ? ' selected' : '') + '>' + esc(label) + '</option>').join('') + '</select>') +
      field('备注', input('remark', '', 'text', 'placeholder="状态变更备注，选填"')) +
      '</div><div style="margin-top:12px"><button class="btn primary" type="submit">保存状态</button> ' +
      '<button class="btn" type="button" data-act="invoice-tab">查看发票记录</button></div></form></div></div>';

    const bindFormEl = $('bindForm');
    if (bindFormEl) {
      bindFormEl.addEventListener('submit', async (e) => {
        e.preventDefault();
        const d = formData(bindFormEl);
        try {
          await api('/api/v1/terminals/bindings', { method: 'POST', body: {
            vehicleId: v.id, terminalId: d.terminalId, taximeterSn: d.taximeterSn, remark: d.remark || null
          }});
          toast('终端与计价器绑定成功');
          await loadTerminals();
          renderParams();
        } catch (err) { toast(err.message, true); }
      });
    }
    const statusFormEl = $('statusForm');
    if (statusFormEl) {
      statusFormEl.addEventListener('submit', async (e) => {
        e.preventDefault();
        const d = formData(statusFormEl);
        try {
          await api('/api/v1/vehicles/' + v.id + '/status', { method: 'PUT', body: {
            status: Number(d.status), remark: d.remark || null
          }});
          toast('车辆状态已更新');
          await loadVehicles();
          renderParams();
        } catch (err) { toast(err.message, true); }
      });
    }
  }

  async function loadInvoices() {
    const qs = new URLSearchParams({ page: String(state.invoicePage), size: '20' });
    const keyword = $('invoiceKeyword').value.trim();
    const status = $('invoiceStatus').value;
    if (keyword) qs.set('keyword', keyword);
    if (status !== '') qs.set('status', status);
    try {
      const page = await api('/api/v1/invoices?' + qs.toString());
      state.invoices = page.list || [];
      state.invoiceTotal = page.total || 0;
      renderInvoices();
    } catch (e) {
      failTable('invoiceRows', 8, e.message);
      $('invoiceCountText').textContent = '';
    }
  }

  function renderInvoices() {
    if (!state.invoices.length) { emptyRow('invoiceRows', 8, '暂无发票记录'); }
    else {
      $('invoiceRows').innerHTML = state.invoices.map((i) => '<tr>' +
        '<td class="mono">' + esc(i.invoiceNo) + '</td>' +
        '<td class="mono">' + esc(i.orderNo) + '</td>' +
        '<td>' + esc(value(i.buyerName)) + '</td>' +
        '<td>' + money(i.amount) + '</td>' +
        '<td>' + badge(invoiceStatus, i.status) + '</td>' +
        '<td class="muted">' + time(i.invoiceTime) + '</td>' +
        '<td>' + (i.pdfUrl ? '<a class="btn small" target="_blank" rel="noopener" href="' + esc(i.pdfUrl) + '">PDF</a>' : '—') + '</td>' +
        '<td>' + (Number(i.status) === 3 ? '<button class="btn small danger" data-act="red-invoice" data-id="' +
          esc(i.id) + '">红冲</button>' : '<span class="muted">—</span>') + '</td></tr>').join('');
    }
    const pages = Math.max(1, Math.ceil(state.invoiceTotal / 20));
    $('invoiceCountText').textContent = '共 ' + state.invoiceTotal + ' 条 · 第 ' + state.invoicePage + ' / ' + pages + ' 页';
    $('invoicePrev').disabled = state.invoicePage <= 1;
    $('invoiceNext').disabled = state.invoicePage >= pages;
  }

  function openFleet(row) {
    const f = row || {};
    const body = '<div class="form-grid">' +
      field('车队编码 <em>*</em>', input('fleetCode', f.fleetCode || '', 'text', 'required maxlength="64" placeholder="如 FLEET-001"'), false, '字母、数字、下划线、横线') +
      field('车队名称 <em>*</em>', input('fleetName', f.fleetName || '', 'text', 'required maxlength="128"')) +
      field('税号', input('taxNo', f.taxNo || '', 'text', 'maxlength="20" placeholder="15~20 位大写字母或数字"')) +
      field('联系人', input('contact', f.contact || '', 'text', 'maxlength="64"')) +
      field('联系电话', input('phone', '', 'text', 'maxlength="11" placeholder="' + esc(f.phoneMask || '11 位手机号') + '"'), false, '编辑时留空表示不修改') +
      field('状态', select('status', statusOptions(fleetStatus), f.status == null ? 1 : f.status)) +
      field('地址', input('address', f.address || '', 'text', 'maxlength="255"'), true) +
      '</div>';
    openModal(row ? '编辑车队' : '新增车队', body, '保存', async (d) => {
      const payload = {
        fleetCode: d.fleetCode.toUpperCase(), fleetName: d.fleetName,
        taxNo: d.taxNo ? d.taxNo.toUpperCase() : '',
        contact: d.contact || null, phone: d.phone || '', address: d.address || null,
        status: Number(d.status)
      };
      await api('/api/v1/fleets' + (row ? '/' + row.id : ''), { method: row ? 'PUT' : 'POST', body: payload });
      toast(row ? '车队已更新' : '车队已新增');
      await Promise.all([loadFleets(), loadFleetOptions(), loadStats()]);
    });
  }

  function openDriver(row) {
    const d = row || {};
    const body = '<div class="form-grid">' +
      field('司机编号 <em>*</em>', input('driverCode', d.driverCode || '', 'text', 'required maxlength="64" placeholder="如 DRV-0001"')) +
      field('姓名 <em>*</em>', input('name', d.name || '', 'text', 'required maxlength="64"')) +
      field('手机号', input('phone', '', 'text', 'maxlength="11" placeholder="' + esc(d.phoneMask || '11 位手机号') + '"'), false, '编辑时留空表示不修改') +
      field('身份证号', input('idCard', '', 'text', 'maxlength="18" placeholder="' + esc(d.idCardMask || '18 位身份证号') + '"'), false, '编辑时留空表示不修改') +
      field('性别', select('gender', [[1, '男'], [2, '女']], d.gender == null ? 1 : d.gender)) +
      field('所属车队', '<select name="fleetId">' + fleetOptions(d.fleetId) + '</select>') +
      field('准驾车型', input('licenseClass', d.licenseClass || '', 'text', 'maxlength="8" placeholder="如 C1"')) +
      field('驾驶证号', input('licenseNo', d.licenseNo || '', 'text', 'maxlength="32"')) +
      field('驾驶证到期', input('licenseExpireDate', d.licenseExpireDate || '', 'date')) +
      field('从业资格证号', input('qualificationNo', d.qualificationNo || '', 'text', 'maxlength="32"')) +
      field('资格证到期', input('qualificationExpireDate', d.qualificationExpireDate || '', 'date')) +
      field('入职日期', input('entryDate', d.entryDate || '', 'date')) +
      field('状态', select('status', statusOptions(driverStatus), d.status == null ? 1 : d.status)) +
      field('备注', input('remark', d.remark || '', 'text', 'maxlength="255"'), true) +
      '</div>';
    openModal(row ? '编辑司机' : '新增司机', body, '保存', async (d) => {
      const payload = {
        driverCode: d.driverCode.toUpperCase(), name: d.name,
        phone: d.phone || '', idCard: d.idCard || '',
        gender: d.gender ? Number(d.gender) : null,
        licenseNo: d.licenseNo || null, licenseClass: d.licenseClass || null,
        licenseExpireDate: d.licenseExpireDate || null,
        qualificationNo: d.qualificationNo || null, qualificationExpireDate: d.qualificationExpireDate || null,
        fleetId: d.fleetId || null, status: Number(d.status),
        entryDate: d.entryDate || null, remark: d.remark || null
      };
      await api('/api/v1/drivers' + (row ? '/' + row.id : ''), { method: row ? 'PUT' : 'POST', body: payload });
      toast(row ? '司机档案已更新' : '司机档案已新增');
      await Promise.all([loadDrivers(), loadDriverOptions(), loadVehicles(), loadStats()]);
    });
  }

  function openVehicle(row) {
    const v = row || {};
    const body = '<div class="form-grid">' +
      field('车牌号 <em>*</em>', input('plateNo', v.plateNo || '', 'text', 'required maxlength="16" placeholder="如 沪A00001"')) +
      field('车牌颜色', input('plateColor', v.plateColor || '蓝', 'text', 'maxlength="8"')) +
      field('车辆类型', input('vehicleType', v.vehicleType || '巡游出租汽车', 'text', 'maxlength="32"')) +
      field('所属车队', '<select name="fleetId">' + fleetOptions(v.fleetId) + '</select>') +
      field('默认司机', '<select name="driverId">' + driverOptions(v.driverId) + '</select>') +
      field('状态', select('status', statusOptions(vehicleStatus), v.status == null ? 1 : v.status)) +
      field('车架号', input('vin', v.vin || '', 'text', 'maxlength="32"')) +
      field('发动机号', input('engineNo', v.engineNo || '', 'text', 'maxlength="32"')) +
      field('车主', input('ownerName', v.ownerName || '', 'text', 'maxlength="64"')) +
      field('购置日期', input('purchaseDate', v.purchaseDate || '', 'date')) +
      field('保险到期', input('insuranceExpireDate', v.insuranceExpireDate || '', 'date')) +
      field('年检到期', input('annualInspectExpireDate', v.annualInspectExpireDate || '', 'date')) +
      field('备注', input('remark', v.remark || '', 'text', 'maxlength="255"'), true) +
      '</div>';
    openModal(row ? '编辑车辆' : '新增车辆', body, '保存', async (d) => {
      const payload = {
        plateNo: d.plateNo.toUpperCase(), plateColor: d.plateColor || null,
        vehicleType: d.vehicleType || null, vin: d.vin || null, engineNo: d.engineNo || null,
        ownerName: d.ownerName || null, fleetId: d.fleetId || null, driverId: d.driverId || null,
        status: Number(d.status), insuranceExpireDate: d.insuranceExpireDate || null,
        annualInspectExpireDate: d.annualInspectExpireDate || null,
        purchaseDate: d.purchaseDate || null, remark: d.remark || null
      };
      await api('/api/v1/vehicles' + (row ? '/' + row.id : ''), { method: row ? 'PUT' : 'POST', body: payload });
      toast(row ? '车辆档案已更新' : '车辆档案已新增');
      await Promise.all([loadVehicles(), loadTerminals(), loadStats()]);
      if (row && state.tab === 'params') renderParams();
    });
  }

  function openTerminal() {
    const body = '<div class="form-grid">' +
      field('终端手机号 <em>*</em>', input('terminalId', '', 'text', 'required pattern="\\d{7,20}" placeholder="7~20 位数字"')) +
      field('终端编号', input('terminalCode', '', 'text', 'maxlength="64" placeholder="如 TERM-001"')) +
      field('SIM 卡号', input('simNo', '', 'text', 'maxlength="32"')) +
      field('厂商 ID', input('manufacturerId', '', 'text', 'maxlength="16" placeholder="如 JT808"')) +
      field('终端型号', input('terminalModel', '', 'text', 'maxlength="64" placeholder="如 DEMO-808"')) +
      field('终端序列号', input('terminalSn', '', 'text', 'maxlength="64"')) +
      field('协议版本', select('protocolVersion', [['2019', '2019'], ['2011', '2011']], '2019')) +
      field('固件版本', input('firmwareVersion', '', 'text', 'maxlength="32"')) +
      field('鉴权码', input('authCode', '', 'text', 'maxlength="64"'), true) +
      '</div>';
    openModal('新增终端', body, '保存', async (d) => {
      await api('/api/v1/terminals', { method: 'POST', body: {
        terminalId: d.terminalId, terminalCode: d.terminalCode || null, simNo: d.simNo || null,
        manufacturerId: d.manufacturerId || null, terminalModel: d.terminalModel || null,
        terminalSn: d.terminalSn || null, protocolVersion: d.protocolVersion || '2019',
        authCode: d.authCode || null, firmwareVersion: d.firmwareVersion || null
      }});
      toast('终端已新增');
      await loadTerminals();
      if (state.tab === 'params') renderParams();
    });
  }

  async function openCertificates(driver) {
    try {
      const list = await api('/api/v1/drivers/' + driver.id + '/certificates');
      const rows = (list || []).map((c) => '<tr><td>' + esc(c.certTypeName || c.certType) + '</td><td class="mono">' +
        esc(value(c.certNo)) + '</td><td>' + esc(date(c.issueDate)) + '</td><td>' + esc(date(c.expireDate)) + '</td>' +
        '<td>' + (Number(c.warnStatus) === 2 ? '<span class="badge bad">已过期</span>' :
          Number(c.warnStatus) === 1 ? '<span class="badge warn">临期</span>' : '<span class="badge ok">正常</span>') + '</td>' +
        '<td><button class="btn small danger" data-act="del-cert" data-driver="' + esc(driver.id) +
        '" data-id="' + esc(c.id) + '">删除</button></td></tr>').join('') ||
        '<tr><td colspan="6" class="empty">暂无证件</td></tr>';
      openModal('司机证件 · ' + driver.name,
        '<div class="toolbar" style="padding:0 0 12px;border:0;background:transparent">' +
        '<button class="btn primary" type="button" data-act="add-cert" data-driver="' + esc(driver.id) + '">新增证件</button></div>' +
        '<div class="table-wrap"><table><thead><tr><th>类型</th><th>证件号</th><th>签发</th><th>到期</th><th>状态</th><th>操作</th></tr></thead><tbody>' +
        rows + '</tbody></table></div>', '关闭', async () => {});
      $('modalSubmit').style.display = 'none';
      setTimeout(() => {
        const add = $('modalBody').querySelector('[data-act="add-cert"]');
        if (add) add.onclick = () => { closeModal(); openCertForm(driver); };
      }, 0);
    } catch (e) { toast(e.message, true); }
  }

  function openCertForm(driver) {
    const body = '<div class="form-grid">' +
      field('证件类型 <em>*</em>', select('certType', [[1, '驾驶证'], [2, '从业资格证'], [3, '身份证'], [4, '健康证'], [9, '其他']], 1)) +
      field('证件号', input('certNo', '', 'text', 'maxlength="64"')) +
      field('签发日期', input('issueDate', '', 'date')) +
      field('到期日期 <em>*</em>', input('expireDate', '', 'date', 'required')) +
      field('影像地址', input('fileUrl', '', 'text', 'maxlength="512"'), true) +
      '</div>';
    openModal('新增证件 · ' + driver.name, body, '保存', async (d) => {
      await api('/api/v1/drivers/' + driver.id + '/certificates', { method: 'POST', body: {
        certType: Number(d.certType), certNo: d.certNo || null, issueDate: d.issueDate || null,
        expireDate: d.expireDate, fileUrl: d.fileUrl || null
      }});
      toast('证件已新增');
      await loadDrivers();
    });
  }

  async function loadOrders() {
    const page = await api('/api/v1/orders?page=1&size=100');
    state.orders = (page.list || []).filter((o) =>
      ['CALCULATED', 'INVOICE_FAIL', 'INVOICE_PENDING'].includes(o.status));
  }

  async function openInvoiceApply() {
    try {
      if (!state.orders.length) await loadOrders();
    } catch (e) { toast(e.message, true); return; }
    if (!state.orders.length) {
      toast('暂无待开票订单（需订单状态为已核算/开票失败/待开票）', true);
      return;
    }
    const body = '<div class="notice">选择待开票订单。当前为 ' +
      (state.info && state.info.invoiceMock ? 'Mock 发票模式' : '正式发票模式') + '，提交后按订单幂等。</div>' +
      '<div class="form-grid">' +
      field('订单 <em>*</em>', '<select name="orderId" required>' + state.orders.map((o) =>
        '<option value="' + esc(o.id) + '">' + esc(o.orderNo + ' · ' + value(o.plateNo) + ' · ' + money(o.payableAmount)) + '</option>').join('') + '</select>', true) +
      field('购方名称', input('buyerName', '', 'text', 'maxlength="128" placeholder="个人或单位名称"')) +
      field('购方税号', input('buyerTaxNo', '', 'text', 'maxlength="32"')) +
      field('接收邮箱', input('buyerEmail', '', 'email', 'maxlength="128"'), true) +
      '</div>';
    openModal('申请开票', body, '提交开票', async (d) => {
      const inv = await api('/api/v1/invoices/apply', { method: 'POST', body: {
        orderId: d.orderId, buyerName: d.buyerName || null, buyerEmail: d.buyerEmail || null,
        buyerTaxNo: d.buyerTaxNo || null
      }});
      toast('开票成功：' + inv.invoiceNo);
      state.orders = [];
      await Promise.all([loadInvoices(), loadOrders()]);
    });
  }

  async function redInvoice(id) {
    const reason = window.prompt('请输入红冲原因：', '用户申请红冲');
    if (reason == null) return;
    if (!reason.trim()) { toast('红冲原因不能为空', true); return; }
    try {
      await api('/api/v1/invoices/' + id + '/red?reason=' + encodeURIComponent(reason.trim()), { method: 'POST' });
      toast('发票已红冲');
      await loadInvoices();
    } catch (e) { toast(e.message, true); }
  }

  function setTab(tab) {
    state.tab = tab;
    document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
    ['fleets', 'drivers', 'vehicles', 'params', 'invoices'].forEach((name) => {
      const panel = $('tab-' + name);
      if (panel) panel.hidden = name !== tab;
    });
    if (tab === 'params') { renderVehiclePicker(); renderParams(); }
    if (tab === 'invoices') loadInvoices();
  }

  function handleAction(action, el) {
    const id = el.dataset.id;
    if (action === 'edit-fleet') openFleet(state.fleets.find((f) => idEq(f.id, id)));
    else if (action === 'del-fleet') deleteFleet(id);
    else if (action === 'edit-driver') openDriver(state.drivers.find((d) => idEq(d.id, id)));
    else if (action === 'del-driver') deleteDriver(id);
    else if (action === 'cert-driver') { const d = state.drivers.find((x) => idEq(x.id, id)); if (d) openCertificates(d); }
    else if (action === 'edit-vehicle') openVehicle(state.vehicles.find((v) => idEq(v.id, id)));
    else if (action === 'del-vehicle') deleteVehicle(id);
    else if (action === 'params-vehicle') {
      state.selectedVehicle = id;
      setTab('params');
    } else if (action === 'pick-vehicle') {
      state.selectedVehicle = id;
      renderParams();
    } else if (action === 'edit-current-vehicle') {
      openVehicle(currentVehicle());
    } else if (action === 'unbind-vehicle') {
      unbindVehicle();
    } else if (action === 'invoice-tab') {
      setTab('invoices');
    } else if (action === 'add-terminal-inline') {
      openTerminal();
    } else if (action === 'red-invoice') {
      redInvoice(id);
    } else if (action === 'del-cert') {
      deleteCert(el.dataset.driver, id);
    }
  }

  async function deleteFleet(id) {
    if (!window.confirm('确认删除该车队？车队下有车辆或司机时后端会拒绝。')) return;
    try {
      await api('/api/v1/fleets/' + id, { method: 'DELETE' });
      toast('车队已删除');
      await Promise.all([loadFleets(), loadFleetOptions(), loadStats()]);
    } catch (e) { toast(e.message, true); }
  }

  async function deleteDriver(id) {
    if (!window.confirm('确认删除该司机？仍绑定车辆时后端会拒绝。')) return;
    try {
      await api('/api/v1/drivers/' + id, { method: 'DELETE' });
      toast('司机已删除');
      await Promise.all([loadDrivers(), loadDriverOptions(), loadVehicles(), loadStats()]);
    } catch (e) { toast(e.message, true); }
  }

  async function deleteVehicle(id) {
    if (!window.confirm('确认删除该车辆？存在生效终端绑定时后端会拒绝。')) return;
    try {
      await api('/api/v1/vehicles/' + id, { method: 'DELETE' });
      toast('车辆已删除');
      await Promise.all([loadVehicles(), loadTerminals(), loadStats()]);
    } catch (e) { toast(e.message, true); }
  }

  async function unbindVehicle() {
    const v = currentVehicle();
    if (!v || !window.confirm('确认解除 ' + v.plateNo + ' 的终端与计价器绑定？')) return;
    try {
      await api('/api/v1/terminals/bindings/' + v.id + '?remark=' + encodeURIComponent('管理端解绑'), { method: 'DELETE' });
      toast('已解除绑定');
      await loadTerminals();
      renderParams();
    } catch (e) { toast(e.message, true); }
  }

  async function deleteCert(driverId, certId) {
    if (!window.confirm('确认删除该证件？')) return;
    try {
      await api('/api/v1/drivers/' + driverId + '/certificates/' + certId, { method: 'DELETE' });
      toast('证件已删除');
      closeModal();
    } catch (e) { toast(e.message, true); }
  }

  document.addEventListener('click', (e) => {
    const tab = e.target.closest('.tab');
    if (tab) { setTab(tab.dataset.tab); return; }
    const action = e.target.closest('[data-act]');
    if (action) handleAction(action.dataset.act, action);
  });

  $('modalClose').addEventListener('click', closeModal);
  $('modalCancel').addEventListener('click', closeModal);
  $('modal').addEventListener('click', (e) => { if (e.target === $('modal')) closeModal(); });

  $('fleetSearch').addEventListener('click', loadFleets);
  $('fleetKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadFleets(); });
  $('fleetStatus').addEventListener('change', loadFleets);
  $('fleetAdd').addEventListener('click', () => openFleet(null));
  $('driverSearch').addEventListener('click', loadDrivers);
  $('driverKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadDrivers(); });
  $('driverFleetFilter').addEventListener('change', loadDrivers);
  $('driverStatus').addEventListener('change', loadDrivers);
  $('driverAdd').addEventListener('click', () => openDriver(null));
  $('vehicleSearch').addEventListener('click', loadVehicles);
  $('vehicleKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadVehicles(); });
  $('vehicleFleetFilter').addEventListener('change', loadVehicles);
  $('vehicleStatus').addEventListener('change', loadVehicles);
  $('vehicleAdd').addEventListener('click', () => openVehicle(null));
  $('terminalAdd').addEventListener('click', openTerminal);
  $('invoiceAdd').addEventListener('click', openInvoiceApply);
  $('invoiceSearch').addEventListener('click', () => { state.invoicePage = 1; loadInvoices(); });
  $('invoiceKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') { state.invoicePage = 1; loadInvoices(); } });
  $('invoiceStatus').addEventListener('change', () => { state.invoicePage = 1; loadInvoices(); });
  $('invoicePrev').addEventListener('click', () => { if (state.invoicePage > 1) { state.invoicePage--; loadInvoices(); } });
  $('invoiceNext').addEventListener('click', () => { state.invoicePage++; loadInvoices(); });
  $('refreshBtn').addEventListener('click', () => init(false));
  $('logoutBtn').addEventListener('click', () => {
    fetch('/api/v1/auth/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + token } })
      .catch(() => {}).finally(() => {
        sessionStorage.removeItem('taxi.token');
        sessionStorage.removeItem('taxi.driver.token');
        sessionStorage.removeItem('taxi.user');
        location.replace('/login');
      });
  });

  async function init(showError) {
    try {
      await loadMe();
      await Promise.all([
        loadInfo().catch((e) => { if (showError !== false) toast(e.message, true); }),
        loadStats().catch((e) => { if (showError !== false) toast(e.message, true); }),
        loadFleetOptions(),
        loadFleets(),
        loadDrivers(),
        loadDriverOptions().catch(() => {}),
        loadVehicles(),
        loadTerminals(),
        loadInvoices()
      ]);
    } catch (e) {
      toast(e.message, true);
    }
  }

  init(true);
})();
