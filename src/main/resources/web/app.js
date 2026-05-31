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
        document.getElementById('stormList').innerHTML = s.storms.length ? s.storms.map(p => `
            <div class="stat" style="margin-bottom:8px">
                <b>${p.id}</b> <span class="muted">${p.type} @${p.world} R${Math.round(p.radius)}</span>
                ${p.active ? '<span class="ok-text">[运行]</span>' : '<span class="muted">[停止]</span>'}
                <div class="muted" style="font-size:12px">点数 ${p.points.length}${p.center ? ' · 中心 ' + Math.round(p.center.x) + ',' + Math.round(p.center.z) : ''}</div>
            </div>`).join('') : '<span class="muted">暂无风暴路径</span>';
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
        try { const r = await api(path, 'POST'); document.getElementById('htmlResult').textContent = r.message || '已生成'; toast('已生成 HTML'); }
        catch (e) { toast(e.message, false); }
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
        bind('htmlPreview', () => genHtml('preview'));
        bind('htmlForecast', () => genHtml('forecast'));
        bind('htmlAll', () => genHtml('all'));

        setInterval(() => {
            document.getElementById('clock').textContent = new Date().toLocaleTimeString();
        }, 1000);

        loadStatus(); loadWeather(); loadHourly(); loadRegions(); loadStorms(); loadUsers();
        setInterval(() => { loadStatus(); loadWeather(); }, 15000);
    }

    document.addEventListener('DOMContentLoaded', init);

    return { api, hasPerm, toast, delRegion, delUser, closeModal, openRegionModal, loadRegions, loadStorms };
})();
