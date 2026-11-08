/* 实时监控页：REST + WebSocket，Canvas 兜底 / 高德地图可选。无构建依赖。 */
(() => {
  'use strict';

  const state = {
    token: sessionStorage.getItem('taxi.token') || '',
    vehicles: new Map(),
    selected: null,
    track: null,
    progress: 0,
    playing: false,
    timer: null,
    speed: 1,
    ws: null,
    retry: null,
    amap: null,
    amapKey: new URLSearchParams(location.search).get('amapKey')
      || localStorage.getItem('taxi.amapKey') || ''
  };

  const $ = (id) => document.getElementById(id);
  const el = {
    loginBox: $('loginBox'), username: $('username'), password: $('password'), btnLogin: $('btnLogin'),
    list: $('vehicleList'), summary: $('vehicleSummary'), trackBox: $('trackBox'),
    trackTitle: $('trackTitle'), wsState: $('wsState'), provider: $('provider'),
    btnLogout: $('btnLogout'), btnPlay: $('btnPlay'), progress: $('progress'), speed: $('speed'),
    sumPoints: $('sumPoints'), sumDistance: $('sumDistance'), sumSpeed: $('sumSpeed'),
    sumDuration: $('sumDuration'), replayHint: $('replayHint'), mapMode: $('mapMode'),
    canvas: $('canvas')
  };
  const ctx = el.canvas.getContext('2d');

  // ---------------- 登录 ----------------
  async function login() {
    const username = el.username.value.trim();
    const password = el.password.value;
    if (!username || !password) {
      setHint('请输入账号与口令');
      return;
    }
    el.btnLogin.disabled = true;
    try {
      const resp = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
        body: JSON.stringify({ username, password })
      });
      const body = await resp.json();
      if (body.code !== 200 || !body.data || !body.data.token) {
        throw new Error(body.msg || '登录失败');
      }
      state.token = body.data.token;
      sessionStorage.setItem('taxi.token', state.token);
      start();
    } catch (e) {
      setHint('登录失败：' + e.message);
    } finally {
      el.btnLogin.disabled = false;
    }
  }

  function logout() {
    sessionStorage.removeItem('taxi.token');
    state.token = '';
    if (state.ws) state.ws.close();
    if (state.retry) clearTimeout(state.retry);
    location.reload();
  }

  function setHint(text) {
    el.replayHint.textContent = text;
  }

  // ---------------- 启动 ----------------
  async function start() {
    el.loginBox.hidden = true;
    el.list.hidden = false;
    el.btnLogout.hidden = false;
    if (state.amapKey) loadAmap(state.amapKey);
    await loadVehicles();
    connectWs();
  }

  async function api(path) {
    const resp = await fetch(path, { headers: { Authorization: 'Bearer ' + state.token } });
    const body = await resp.json();
    if (body.code === 401) {
      sessionStorage.removeItem('taxi.token');
      state.token = '';
      location.replace('/login?redirect=/monitor.html');
      throw new Error('登录已失效');
    }
    if (body.code !== 200) throw new Error(body.msg || ('HTTP ' + resp.status));
    return body.data;
  }

  async function loadVehicles() {
    try {
      const list = await api('/api/v1/monitor/vehicles');
      state.vehicles = new Map(list.map((v) => [String(v.vehicleId), v]));
      render();
    } catch (e) {
      setHint('车辆列表加载失败：' + e.message);
    }
  }

  // ---------------- WebSocket ----------------
  function connectWs() {
    if (!state.token) return;
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(`${scheme}://${location.host}/ws/v1/monitor?token=${encodeURIComponent(state.token)}`);
    state.ws = ws;
    setWsState('连接中', false);

    ws.onopen = () => setWsState('实时在线', true);
    ws.onmessage = (event) => {
      let msg;
      try {
        msg = JSON.parse(event.data);
      } catch (e) {
        return;
      }
      if (msg.type === 'snapshot' && Array.isArray(msg.data)) {
        state.vehicles = new Map(msg.data.map((v) => [String(v.vehicleId), v]));
        render();
      } else if (msg.type === 'position' && msg.data) {
        const v = msg.data;
        state.vehicles.set(String(v.vehicleId), Object.assign({}, state.vehicles.get(String(v.vehicleId)) || {}, v));
        render();
      }
    };
    ws.onclose = () => {
      setWsState('已断开', false);
      if (state.token) {
        state.retry = setTimeout(connectWs, 5000);
      }
    };
    ws.onerror = () => setWsState('连接异常', false);
  }

  function setWsState(text, online) {
    el.wsState.textContent = text;
    el.wsState.className = 'pill ' + (online ? 'online' : 'offline');
  }

  // ---------------- 车辆列表 ----------------
  function renderList() {
    const items = [...state.vehicles.values()].sort((a, b) => String(a.plateNo).localeCompare(String(b.plateNo), 'zh'));
    el.list.innerHTML = '';
    for (const v of items) {
      const li = document.createElement('li');
      if (String(v.vehicleId) === String(state.selected)) li.className = 'active';
      li.innerHTML = `
        <span class="dot ${v.onlineStatus === 1 ? 'on' : ''}"></span>
        <span><span class="plate">${escapeHtml(v.plateNo || '-')}</span>
          <span class="meta">${escapeHtml(v.terminalId || '-')} · ${v.ts ? escapeHtml(fmtTime(v.ts)) : '暂无定位'}</span></span>
        <span class="speed">${v.speed == null ? '-' : v.speed + ' km/h'}</span>`;
      li.onclick = () => {
        state.selected = String(v.vehicleId);
        loadTrack(state.selected);
        render();
      };
      el.list.appendChild(li);
    }
    const online = items.filter((v) => v.onlineStatus === 1).length;
    el.summary.textContent = `${online}/${items.length} 在线`;
  }

  // ---------------- 轨迹 ----------------
  async function loadTrack(vehicleId) {
    setHint('加载轨迹中…');
    const end = new Date();
    const start = new Date(end.getTime() - 2 * 3600 * 1000);
    try {
      const data = await api(`/api/v1/monitor/track?vehicleId=${encodeURIComponent(vehicleId)}`
        + `&start=${encodeURIComponent(isoLocal(start))}&end=${encodeURIComponent(isoLocal(end))}&limit=2000`);
      state.track = data;
      state.progress = 0;
      stopPlay();
      el.trackBox.hidden = false;
      el.provider.textContent = '轨迹库 ' + (data.provider || '-');
      el.trackTitle.textContent = `${data.plateNo || vehicleId} · ${data.pointCount} 点`;
      el.sumPoints.textContent = data.pointCount;
      el.sumDistance.textContent = (data.distanceKm || 0).toFixed(3) + ' km';
      el.sumSpeed.textContent = (data.maxSpeed || 0) + ' km/h';
      el.sumDuration.textContent = formatDuration(data.durationSec || 0);
      el.progress.max = Math.max(0, data.pointCount - 1);
      el.progress.value = 0;
      setHint(data.pointCount === 0 ? '该时段没有定位点，可先运行模拟器上报。' : '拖动进度条或播放查看轨迹。');
      render();
    } catch (e) {
      setHint('轨迹加载失败：' + e.message);
    }
  }

  function togglePlay() {
    if (!state.track || state.track.pointCount < 2) return;
    if (state.playing) {
      stopPlay();
    } else {
      if (state.progress >= state.track.pointCount - 1) state.progress = 0;
      state.playing = true;
      el.btnPlay.textContent = '暂停';
      state.timer = setInterval(() => {
        state.progress += state.speed;
        if (state.progress >= state.track.pointCount - 1) {
          state.progress = state.track.pointCount - 1;
          stopPlay();
        }
        el.progress.value = state.progress;
        render();
      }, 250);
    }
  }

  function stopPlay() {
    state.playing = false;
    el.btnPlay.textContent = '播放';
    if (state.timer) clearInterval(state.timer);
    state.timer = null;
  }

  // ---------------- 渲染 ----------------
  function render() {
    renderList();
    if (state.amap) {
      renderAmap();
    } else {
      renderCanvas();
    }
  }

  function renderCanvas() {
    const dpr = window.devicePixelRatio || 1;
    const width = el.canvas.clientWidth || 800;
    const height = el.canvas.clientHeight || 480;
    if (el.canvas.width !== width * dpr || el.canvas.height !== height * dpr) {
      el.canvas.width = width * dpr;
      el.canvas.height = height * dpr;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, height);

    const points = (state.track && state.track.points) ? state.track.points : [];
    const all = points.concat([...state.vehicles.values()].filter((v) => v.lng != null && v.lat != null));
    const bounds = computeBounds(all);
    const project = makeProjector(bounds, width, height);

    drawGrid(width, height);
    if (points.length > 0) {
      const limit = Math.floor(state.progress) + 1;
      drawPolyline(points.slice(0, Math.max(2, limit)), project, 'rgba(53,194,255,0.95)', 2.5);
      if (limit < points.length) {
        drawPolyline(points.slice(limit - 1), project, 'rgba(143,163,192,0.35)', 1.5);
      }
    }
    for (const v of state.vehicles.values()) {
      if (v.lng == null || v.lat == null) continue;
      const p = project(Number(v.lng), Number(v.lat));
      const isSelected = String(v.vehicleId) === String(state.selected);
      ctx.beginPath();
      ctx.fillStyle = v.onlineStatus === 1 ? (isSelected ? '#ffd166' : '#3ddc97') : '#52627d';
      ctx.arc(p.x, p.y, isSelected ? 7 : 5, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#e6edf7';
      ctx.font = '12px "PingFang SC", "Microsoft YaHei", sans-serif';
      ctx.fillText(`${v.plateNo || ''} ${v.speed == null ? '' : v.speed + 'km/h'}`, p.x + 10, p.y - 8);
    }
    if (points.length > 0) {
      const current = points[Math.min(points.length - 1, Math.floor(state.progress))];
      if (current) {
        const p = project(Number(current.lng), Number(current.lat));
        ctx.beginPath();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.arc(p.x, p.y, 6, 0, Math.PI * 2);
        ctx.stroke();
      }
    }
    if (all.length === 0) {
      ctx.fillStyle = '#8fa3c0';
      ctx.font = '14px "PingFang SC", sans-serif';
      ctx.fillText('暂无定位数据：启动终端模拟器后自动出现车辆位置', 24, 40);
    }
  }

  function drawGrid(width, height) {
    ctx.save();
    ctx.strokeStyle = 'rgba(34,49,77,0.9)';
    ctx.lineWidth = 1;
    const step = 60;
    for (let x = 0; x < width; x += step) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, height);
      ctx.stroke();
    }
    for (let y = 0; y < height; y += step) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(width, y);
      ctx.stroke();
    }
    ctx.restore();
  }

  function drawPolyline(points, project, color, lineWidth) {
    if (points.length < 2) return;
    ctx.save();
    ctx.beginPath();
    ctx.strokeStyle = color;
    ctx.lineWidth = lineWidth;
    ctx.lineJoin = 'round';
    points.forEach((p, i) => {
      const q = project(Number(p.lng), Number(p.lat));
      if (i === 0) ctx.moveTo(q.x, q.y);
      else ctx.lineTo(q.x, q.y);
    });
    ctx.stroke();
    ctx.restore();
  }

  function computeBounds(points) {
    if (points.length === 0) {
      return { minLng: 121.40, maxLng: 121.56, minLat: 31.18, maxLat: 31.28 };
    }
    let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity;
    for (const p of points) {
      const lng = Number(p.lng);
      const lat = Number(p.lat);
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) continue;
      minLng = Math.min(minLng, lng);
      maxLng = Math.max(maxLng, lng);
      minLat = Math.min(minLat, lat);
      maxLat = Math.max(maxLat, lat);
    }
    if (!Number.isFinite(minLng)) {
      return { minLng: 121.40, maxLng: 121.56, minLat: 31.18, maxLat: 31.28 };
    }
    const padLng = Math.max((maxLng - minLng) * 0.2, 0.002);
    const padLat = Math.max((maxLat - minLat) * 0.2, 0.002);
    return {
      minLng: minLng - padLng, maxLng: maxLng + padLng,
      minLat: minLat - padLat, maxLat: maxLat + padLat
    };
  }

  function makeProjector(bounds, width, height) {
    const spanLng = bounds.maxLng - bounds.minLng;
    const spanLat = bounds.maxLat - bounds.minLat;
    const pad = 24;
    const scale = Math.min((width - pad * 2) / spanLng, (height - pad * 2) / spanLat);
    const offsetX = (width - spanLng * scale) / 2;
    const offsetY = (height - spanLat * scale) / 2;
    return (lng, lat) => ({
      x: offsetX + (lng - bounds.minLng) * scale,
      y: height - offsetY - (lat - bounds.minLat) * scale
    });
  }

  // ---------------- 高德地图（可选） ----------------
  function loadAmap(key) {
    const script = document.createElement('script');
    script.src = 'https://webapi.amap.com/maps?v=2.0&key=' + encodeURIComponent(key);
    script.onload = () => {
      el.canvas.style.display = 'none';
      state.amap = new AMap.Map('map', {
        zoom: 12,
        center: [121.4737, 31.2304],
        mapStyle: 'amap://styles/dark'
      });
      el.mapMode.textContent = '高德地图模式';
      state.amapMarkers = new Map();
      render();
    };
    script.onerror = () => {
      el.mapMode.textContent = '高德脚本加载失败，已回退 Canvas 模式';
    };
    document.head.appendChild(script);
    localStorage.setItem('taxi.amapKey', key);
  }

  function renderAmap() {
    const map = state.amap;
    for (const v of state.vehicles.values()) {
      if (v.lng == null || v.lat == null) continue;
      const key = String(v.vehicleId);
      const position = [Number(v.lng), Number(v.lat)];
      let marker = state.amapMarkers.get(key);
      if (!marker) {
        marker = new AMap.Marker({
          position,
          title: v.plateNo,
          label: { content: `${v.plateNo || ''}`, direction: 'top' }
        });
        marker.setMap(map);
        state.amapMarkers.set(key, marker);
      } else {
        marker.setPosition(position);
      }
    }
    if (state.trackLine) {
      state.trackLine.setMap(null);
      state.trackLine = null;
    }
    const points = (state.track && state.track.points) || [];
    if (points.length > 1) {
      state.trackLine = new AMap.Polyline({
        path: points.map((p) => [Number(p.lng), Number(p.lat)]),
        strokeColor: '#35c2ff',
        strokeWeight: 5,
        lineJoin: 'round'
      });
      state.trackLine.setMap(map);
      map.setFitView([state.trackLine]);
    }
    const current = points[Math.min(points.length - 1, Math.floor(state.progress))];
    if (current) {
      if (!state.replayMarker) {
        state.replayMarker = new AMap.Marker({ position: [Number(current.lng), Number(current.lat)] });
        state.replayMarker.setMap(map);
      } else {
        state.replayMarker.setPosition([Number(current.lng), Number(current.lat)]);
      }
    }
  }

  // ---------------- 工具 ----------------
  function isoLocal(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
      + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  function fmtTime(value) {
    return String(value).replace('T', ' ').slice(5, 19);
  }

  function formatDuration(seconds) {
    if (!seconds) return '0 秒';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`;
  }

  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  // ---------------- 事件 ----------------
  el.btnLogin.onclick = login;
  el.password.onkeydown = (e) => { if (e.key === 'Enter') login(); };
  el.btnLogout.onclick = logout;
  el.btnPlay.onclick = togglePlay;
  el.progress.oninput = () => {
    stopPlay();
    state.progress = Number(el.progress.value);
    render();
  };
  el.speed.onchange = () => { state.speed = Number(el.speed.value); };
  window.addEventListener('resize', () => { if (!state.amap) render(); });

  if (!state.token) {
    location.replace('/login?redirect=/monitor.html');
    return;
  }
  start();
})();
