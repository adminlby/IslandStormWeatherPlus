/* IslandStorm 控制台前端逻辑（无外部依赖） */
const IST = (function () {
    const token = localStorage.getItem('IST_TOKEN');
    const perms = JSON.parse(localStorage.getItem('IST_PERMS') || '[]');
    if (!token) location.href = 'login.html';

    const WEATHER_TYPES = ['CLEAR','CLOUDY','RAIN','HEAVY_RAIN','THUNDERSTORM','FOG','WINDY','TYPHOON','EXTREME_STORM'];
    const DIRS = ['N','NE','E','SE','S','SW','W','NW'];

    function hasPerm(p) { return perms.includes('*') || perms.includes(p); }

    async function api(path, method, body) {
        const opt = { method: method || 'GET', headers: { 'Authorization': 'Bearer ' + token } };
        if (body) { opt.headers['Content-Type'] = 'application/json'; opt.body = JSON.stringify(body); }
        const res = await fetch('api/' + path, opt);
        if (res.status === 401) { logout(); throw new Error('登录已过期'); }
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.message || ('HTTP ' + res.status));
        return data;
    }

    function logout() {
        try { api('logout', 'POST'); } catch (e) {}
        localStorage.removeItem('IST_TOKEN');
        location.href = 'login.html';
    }

    function toast(msg, ok) {
        const t = document.getElementById('toast');
        t.textContent = msg; t.style.borderColor = ok === false ? 'var(--danger)' : 'var(--line)';
        t.classList.add('show'); setTimeout(() => t.classList.remove('show'), 2600);
    }

    // 根据权限隐藏无权限的卡片/按钮
    function applyPerms() {
        document.querySelectorAll('[data-perm]').forEach(el => {
            if (!hasPerm(el.getAttribute('data-perm'))) el.style.display = 'none';
        });
    }

    function fillSelect(id, values) {
        const el = document.getElementById(id);
        if (!el) return;
        el.innerHTML = values.map(v => `<option value="${v}">${v}</option>`).join('');
    }

    // ---------- 渲染 ----------
    async function loadStatus() {
        try {
            const s = await api('status');
            const rows = [
                ['在线人数', s.online + ' / ' + s.maxPlayers],
                ['TPS', s.tps == null ? 'N/A' : s.tps],
                ['区域', s.regions], ['风暴', s.storms],
                ['方块破坏', s.blockDamage ? '开启' : '关闭'],
                ['现实时间', s.realTime]
            ];
            document.getElementById('statRow').innerHTML = rows.map(r =>
                `<div class="stat"><div class="k">${r[0]}</div><div class="v">${r[1]}</div></div>`).join('');
        } catch (e) { /* 无 server.status 权限则忽略 */ }
    }

    async function loadWeather() {
        if (!hasPerm('weather.view')) return;
        const w = await api('weather');
        if (!w.present) { document.getElementById('wName').textContent = '无天气'; return; }
        document.getElementById('wIcon').textContent = w.icon;
        document.getElementById('wName').textContent = w.displayName + ' (' + w.type + ')';
        document.getElementById('wMeta').innerHTML =
            `风速 <b>${w.windSpeed} km/h</b> · 风向 <b>${w.windDirection}</b> · ${w.windLevel}`;
        document.getElementById('wMeta2').innerHTML =
            `<span style="color:${w.dangerColor}">危险 ${w.dangerLevel}</span> · 能见度 ${w.visibility} · 来源 ${w.source}`;
    }

    async function loadHourly() {
        if (!hasPerm('forecast.view')) return;
        const f = await api('forecast/hourly');
        document.getElementById('hourlyStrip').innerHTML = f.entries.map(e => `
            <div class="fc-item">
                <div class="t">${e.realStart} · MC ${e.mcStart}</div>
                <div class="ic">${e.icon}</div>
                <div class="nm">${e.displayName}</div>
                <div class="wd">${e.windSpeed}km/h ${e.windDirection}</div>
                <div class="wd" style="color:${e.dangerColor}">${e.dangerLevel}</div>
            </div>`).join('');
    }

    async function loadRegions() {
        if (!hasPerm('region.view')) return;
        const r = await api('regions');
        const tb = document.querySelector('#regionTable tbody');
        tb.innerHTML = r.regions.map(rg => `
            <tr>
                <td>${rg.name}<br><span class="muted" style="font-size:11px">${rg.minX},${rg.minZ}~${rg.maxX},${rg.maxZ}</span></td>
                <td><span class="tag" style="background:${rg.dangerColor}33;color:${rg.dangerColor}">${rg.displayName}</span></td>
                <td>${hasPerm('region.delete') ? `<button class="ghost" onclick="IST.delRegion('${rg.name}')">删除</button>` : ''}</td>
            </tr>`).join('');
        if (window.ISTMap) window.ISTMap.reload();
    }

    async function loadStorms() {
        if (!hasPerm('storm.path.view')) return;
        const s = await api('storm/path');
        const canEdit = hasPerm('storm.path.edit');
        document.getElementById('stormList').innerHTML = s.storms.length ? s.storms.map(p => {
            const state = p.paused ? '<span class="muted">[暂停]</span>'
                : (p.active ? '<span class="ok-text">[运行]</span>'
                    : (p.ended ? '<span class="muted">[已结束]</span>' : '<span class="muted">[未启动]</span>'));
            const center = p.center ? ' · 中心 ' + Math.round(p.center.x) + ',' + Math.round(p.center.z) : '';
            const cw = (p.center && p.centerWindSpeed != null)
                ? `<div class="muted" style="font-size:12px">中心天气 ${p.icon || ''}${p.typeDisplay || ''} · 风 ${p.centerWindSpeed}km/h ${p.centerWindDirection || ''} ${p.centerWindLevel || ''} · 强度×${(p.intensity || 1).toFixed(1)}</div>`
                : '';
            let btns = '';
            if (canEdit) {
                const b = (act, label) => `<button class="ghost" onclick="IST.stormAction('${p.id}','${act}')">${label}</button>`;
                if (!p.active) btns += b('start', '启动');
                if (p.active && !p.paused) btns += b('pause', '暂停');
                if (p.paused) btns += b('resume', '继续');
                if (p.active) btns += b('stop', '停止');
                btns += b('delete', '删除');
            }
            const tags = `${p.type} @${p.world} R${Math.round(p.radius)}${p.curved ? ' ·曲线' : ''}${p.blockDamageEnabled ? ' ·破坏L' + p.blockDamageLevel : ''}`;
            return `<div class="stat" style="margin-bottom:8px">
                <b>${p.id}</b> <span class="muted">${tags}</span> ${state}
                <div class="muted" style="font-size:12px">点数 ${p.points.length}${center}</div>
                ${cw}
                ${btns ? '<div style="margin-top:6px;display:flex;gap:6px;flex-wrap:wrap">' + btns + '</div>' : ''}
            </div>`;
        }).join('') : '<span class="muted">暂无风暴路径</span>';
    }

    const STORM_ACTION_LABEL = { start: '启动', pause: '暂停', resume: '继续', stop: '停止', delete: '删除' };
    async function stormAction(id, action) {
        if (action === 'delete' && !confirm('删除风暴 ' + id + '？')) return;
        try {
            await api('storm/path/' + action, 'POST', { id });
            toast('已' + (STORM_ACTION_LABEL[action] || action));
            loadStorms();
            if (window.ISTMap) window.ISTMap.reload();
        } catch (e) { toast(e.message, false); }
    }

    async function loadUsers() {
        if (!hasPerm('user.manage')) return;
        const u = await api('users');
        const tb = document.querySelector('#userTable tbody');
        tb.innerHTML = u.users.map(us => `
            <tr>
                <td>${us.username}</td>
                <td class="muted" style="font-size:12px">${us.permissions.join(', ')}</td>
                <td><button class="ghost" onclick="IST.delUser('${us.username}')">删除</button></td>
            </tr>`).join('');
    }

    // ---------- 操作 ----------
    async function setWeather() {
        try {
            await api('weather/set', 'POST', {
                weather: document.getElementById('setWeatherType').value,
                duration: document.getElementById('setWeatherValue').value + document.getElementById('setWeatherUnit').value,
                mode: document.getElementById('setWeatherMode').value
            });
            toast('天气已更新'); loadWeather(); loadHourly();
        } catch (e) { toast(e.message, false); }
    }

    async function setWind() {
        try {
            await api('wind/set', 'POST', {
                speed: parseFloat(document.getElementById('windSpeed').value),
                direction: document.getElementById('windDir').value
            });
            toast('风已更新'); loadWeather();
        } catch (e) { toast(e.message, false); }
    }

    async function delRegion(name) {
        if (!confirm('删除区域 ' + name + '？')) return;
        try { await api('regions/delete', 'POST', { name }); toast('已删除'); loadRegions(); }
        catch (e) { toast(e.message, false); }
    }

    async function createUser() {
        try {
            await api('users/create', 'POST', {
                username: document.getElementById('nuName').value,
                password: document.getElementById('nuPass').value,
                permissions: ['weather.view']
            });
            toast('用户已创建'); loadUsers();
        } catch (e) { toast(e.message, false); }
    }

    async function delUser(name) {
        if (!confirm('删除用户 ' + name + '？')) return;
        try { await api('users/delete', 'POST', { username: name }); toast('已删除'); loadUsers(); }
        catch (e) { toast(e.message, false); }
    }

    async function genHtml(which) {
        const path = which === 'preview' ? 'html/generate' : (which === 'forecast' ? 'html/generate/hourly' : 'html/generate/all');
        try {
            const r = await api(path, 'POST');
            const urls = (r.previewUrl || r.hourlyUrl)
                ? `<br>实时URL：<a href="${r.previewUrl}" target="_blank">${r.previewUrl}</a> · <a href="${r.hourlyUrl}" target="_blank">${r.hourlyUrl}</a>` : '';
            document.getElementById('htmlResult').innerHTML = (r.message || '已生成') + urls;
            toast('已生成 HTML');
        } catch (e) { toast(e.message, false); }
    }

    // ---- 天气卡实时 URL（公开只读，OBS 直接用） ----
    function buildCardUrl(kind) {
        const mode = document.getElementById('cardMode') ? document.getElementById('cardMode').value : 'global';
        const player = document.getElementById('cardPlayer') ? document.getElementById('cardPlayer').value : '';
        let u = location.origin + '/card/' + kind;
        if (mode === 'player' && player) u += '?mode=player&player=' + encodeURIComponent(player);
        return u;
    }
    function refreshCardUrls() {
        const pv = document.getElementById('cardPreviewUrl'), hv = document.getElementById('cardHourlyUrl');
        if (pv) pv.value = buildCardUrl('preview');
        if (hv) hv.value = buildCardUrl('hourly');
    }
    async function loadCardPlayers() {
        if (!hasPerm('html.generate')) return;
        try {
            const d = await api('players');
            const sel = document.getElementById('cardPlayer');
            if (sel) {
                const list = d.players || [];
                sel.innerHTML = list.length
                    ? list.map(p => `<option value="${p.name}">${p.name} @${p.world}</option>`).join('')
                    : '<option value="">（无在线玩家）</option>';
            }
            refreshCardUrls();
        } catch (e) { /* 无权限忽略 */ }
    }

    // 区域弹层
    function openRegionModal(prefill) {
        const m = document.getElementById('regionModal');
        if (prefill) {
            document.getElementById('rmMinX').value = prefill.minX;
            document.getElementById('rmMinZ').value = prefill.minZ;
            document.getElementById('rmMaxX').value = prefill.maxX;
            document.getElementById('rmMaxZ').value = prefill.maxZ;
        }
        m.classList.add('show');
    }
    function closeModal() { document.getElementById('regionModal').classList.remove('show'); }

    async function saveRegion() {
        try {
            await api('regions/create', 'POST', {
                name: document.getElementById('rmName').value,
                world: window.ISTMap ? window.ISTMap.currentWorld() : null,
                minX: +document.getElementById('rmMinX').value,
                minZ: +document.getElementById('rmMinZ').value,
                maxX: +document.getElementById('rmMaxX').value,
                maxZ: +document.getElementById('rmMaxZ').value,
                weather: document.getElementById('rmWeather').value,
                windSpeed: +document.getElementById('rmWindSpeed').value,
                windDirection: document.getElementById('rmWindDir').value,
                durationMinutes: +document.getElementById('rmDuration').value,
                blockDamageEnabled: document.getElementById('rmDamage').value === 'true',
                blockDamageLevel: +document.getElementById('rmDamageLevel').value
            });
            toast('区域已创建'); closeModal(); loadRegions();
        } catch (e) { toast(e.message, false); }
    }

    // 风暴弹层（点集来自地图点击）
    let stormDraftPoints = [];
    function openStormModal(draft) {
        stormDraftPoints = (draft || []).map(p => ({ x: p.x, z: p.z }));
        if (stormDraftPoints.length < 2) { toast('请先在地图上点至少 2 个点', false); return; }
        document.getElementById('smId').value = 'typhoon-' + (Date.now() + '').slice(-4);
        document.getElementById('smRadius').value = 150;
        document.getElementById('smType').value = 'TYPHOON';
        document.getElementById('smCurved').value = 'false';
        document.getElementById('smActive').value = 'true';
        document.getElementById('smDamage').value = 'false';
        document.getElementById('smDamageLevel').value = 2;
        renderStormPoints();
        document.getElementById('stormModal').classList.add('show');
    }
    function renderStormPoints() {
        document.getElementById('smPoints').innerHTML = stormDraftPoints.map((p, i) => `
            <div class="row" style="align-items:flex-end;margin-bottom:6px">
                <div style="flex:0 0 auto" class="muted">#${i + 1} (${Math.round(p.x)}, ${Math.round(p.z)})</div>
                <div><label class="f">到达(秒)</label><input class="f sm-arrive" type="number" value="${i * 600}" min="0"></div>
                <div><label class="f">强度×</label><input class="f sm-int" type="number" value="1.0" step="0.1" min="0.1"></div>
            </div>`).join('');
    }
    function closeStormModal() { document.getElementById('stormModal').classList.remove('show'); }

    async function saveStorm() {
        const arrives = [...document.querySelectorAll('#smPoints .sm-arrive')];
        const ints = [...document.querySelectorAll('#smPoints .sm-int')];
        const points = stormDraftPoints.map((p, i) => ({
            x: p.x, z: p.z,
            arriveAfterSeconds: +(arrives[i] ? arrives[i].value : i * 600),
            intensity: +(ints[i] ? ints[i].value : 1.0)
        }));
        try {
            await api('storm/path/set', 'POST', {
                id: document.getElementById('smId').value,
                type: document.getElementById('smType').value,
                world: window.ISTMap ? window.ISTMap.currentWorld() : null,
                radius: +document.getElementById('smRadius').value,
                curved: document.getElementById('smCurved').value === 'true',
                blockDamageEnabled: document.getElementById('smDamage').value === 'true',
                blockDamageLevel: +document.getElementById('smDamageLevel').value,
                active: document.getElementById('smActive').value === 'true',
                points
            });
            toast('风暴路径已保存'); closeStormModal(); loadStorms();
            if (window.ISTMap) window.ISTMap.reload();
        } catch (e) { toast(e.message, false); }
    }

    // ---------- 初始化 ----------
    function init() {
        document.getElementById('who').textContent = '👤 ' + (localStorage.getItem('IST_USER') || '');
        document.getElementById('logout').addEventListener('click', logout);
        applyPerms();

        fillSelect('setWeatherType', WEATHER_TYPES);
        fillSelect('windDir', DIRS);
        fillSelect('rmWeather', WEATHER_TYPES);
        fillSelect('rmWindDir', DIRS);

        const bind = (id, fn) => { const el = document.getElementById(id); if (el) el.addEventListener('click', fn); };
        bind('btnSetWeather', setWeather);
        bind('btnSetWind', setWind);
        bind('btnCreateUser', createUser);
        bind('rmSave', saveRegion);
        bind('smSave', saveStorm);
        bind('htmlPreview', () => genHtml('preview'));
        bind('htmlForecast', () => genHtml('forecast'));
        bind('htmlAll', () => genHtml('all'));
        bind('cardOpenPreview', () => window.open(buildCardUrl('preview'), '_blank'));
        bind('cardOpenHourly', () => window.open(buildCardUrl('hourly'), '_blank'));
        const cm = document.getElementById('cardMode'); if (cm) cm.addEventListener('change', refreshCardUrls);
        const cp = document.getElementById('cardPlayer'); if (cp) cp.addEventListener('change', refreshCardUrls);

        setInterval(() => {
            document.getElementById('clock').textContent = new Date().toLocaleTimeString();
        }, 1000);

        loadStatus(); loadWeather(); loadHourly(); loadRegions(); loadStorms(); loadUsers(); loadCardPlayers();
        setInterval(() => { loadStatus(); loadWeather(); }, 15000);
        setInterval(loadCardPlayers, 30000);
    }

    document.addEventListener('DOMContentLoaded', init);

    return {
        api, hasPerm, toast, delRegion, delUser, closeModal, openRegionModal, loadRegions, loadStorms,
        stormAction, openStormModal, closeStormModal
    };
})();
