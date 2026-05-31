/* IslandStorm 简化 2D 地图：缩放 / 拖动 / 网格 / 区域框 / 风暴中心 / 画框建区域 / 点击建风暴点 */
window.ISTMap = (function () {
    const canvas = document.getElementById('mapCanvas');
    if (!canvas) return { reload() {}, currentWorld() { return null; } };
    const ctx = canvas.getContext('2d');

    let world = null;
    let bounds = { minX: -1000, minZ: -1000, maxX: 1000, maxZ: 1000 };
    let mapData = null, regions = [], storms = [];

    // 视图：world 坐标 → 屏幕，view.scale 像素/方块，view.ox/oy 平移（屏幕像素）
    let view = { scale: 0.2, ox: 0, oy: 0 };
    let mode = 'pan'; // pan | region | storm
    let dragging = false, dragStart = null, dragCur = null, panStart = null;
    let stormDraft = [];

    function resize() {
        canvas.width = canvas.clientWidth;
        canvas.height = canvas.clientHeight;
        draw();
    }

    function worldToScreen(wx, wz) {
        return { x: (wx - bounds.minX) * view.scale + view.ox, y: (wz - bounds.minZ) * view.scale + view.oy };
    }
    function screenToWorld(sx, sy) {
        return { x: (sx - view.ox) / view.scale + bounds.minX, z: (sy - view.oy) / view.scale + bounds.minZ };
    }

    function fitView() {
        const spanX = bounds.maxX - bounds.minX, spanZ = bounds.maxZ - bounds.minZ;
        const sx = canvas.width / spanX, sy = canvas.height / spanZ;
        view.scale = Math.min(sx, sy) * 0.92;
        view.ox = (canvas.width - spanX * view.scale) / 2;
        view.oy = (canvas.height - spanZ * view.scale) / 2;
    }

    function draw() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = '#04162c';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // 简化地图格子
        if (mapData && mapData.cells && mapData.cols > 0) {
            const cw = (bounds.maxX - bounds.minX) / mapData.cols * view.scale;
            const ch = (bounds.maxZ - bounds.minZ) / mapData.rows * view.scale;
            for (let r = 0; r < mapData.rows; r++) {
                for (let c = 0; c < mapData.cols; c++) {
                    const v = mapData.cells[r * mapData.cols + c];
                    ctx.fillStyle = v === 1 ? '#0d3b66' : (v === 2 ? '#1f6b3a' : '#2a3340');
                    const p = worldToScreen(bounds.minX + (c + 0.5) * (bounds.maxX - bounds.minX) / mapData.cols,
                        bounds.minZ + (r + 0.5) * (bounds.maxZ - bounds.minZ) / mapData.rows);
                    ctx.fillRect(p.x - cw / 2, p.y - ch / 2, cw + 1, ch + 1);
                }
            }
        } else if (mapData && mapData.note) {
            ctx.fillStyle = '#8fb4e0'; ctx.font = '13px sans-serif';
            ctx.fillText(mapData.note, 16, 24);
        }

        drawGrid();

        // 区域框
        regions.forEach(rg => {
            const a = worldToScreen(rg.minX, rg.minZ), b = worldToScreen(rg.maxX, rg.maxZ);
            ctx.strokeStyle = rg.dangerColor || '#58a6ff';
            ctx.lineWidth = 2;
            ctx.strokeRect(a.x, a.y, b.x - a.x, b.y - a.y);
            ctx.fillStyle = (rg.dangerColor || '#58a6ff') + '22';
            ctx.fillRect(a.x, a.y, b.x - a.x, b.y - a.y);
            ctx.fillStyle = '#dce9ff'; ctx.font = '11px sans-serif';
            ctx.fillText(rg.name, a.x + 4, a.y + 14);
        });

        // 风暴路径线 + 中心
        storms.forEach(p => {
            if (p.points && p.points.length) {
                ctx.strokeStyle = '#f0883e'; ctx.lineWidth = 2; ctx.beginPath();
                p.points.forEach((pt, i) => {
                    const s = worldToScreen(pt.x, pt.z);
                    i === 0 ? ctx.moveTo(s.x, s.y) : ctx.lineTo(s.x, s.y);
                });
                ctx.stroke();
                p.points.forEach(pt => {
                    const s = worldToScreen(pt.x, pt.z);
                    ctx.fillStyle = '#f0883e'; ctx.beginPath(); ctx.arc(s.x, s.y, 3, 0, 7); ctx.fill();
                });
            }
            if (p.center) {
                const s = worldToScreen(p.center.x, p.center.z);
                ctx.fillStyle = '#f85149'; ctx.beginPath(); ctx.arc(s.x, s.y, 6, 0, 7); ctx.fill();
                ctx.strokeStyle = '#f8514966'; ctx.lineWidth = 1.5;
                ctx.beginPath(); ctx.arc(s.x, s.y, (p.radius || 100) * view.scale, 0, 7); ctx.stroke();
            }
        });

        // 风暴草稿点
        if (stormDraft.length) {
            ctx.fillStyle = '#58e1ff';
            stormDraft.forEach(pt => {
                const s = worldToScreen(pt.x, pt.z);
                ctx.beginPath(); ctx.arc(s.x, s.y, 4, 0, 7); ctx.fill();
            });
        }

        // 画框预览
        if (dragging && mode === 'region' && dragStart && dragCur) {
            ctx.strokeStyle = '#58e1ff'; ctx.lineWidth = 2; ctx.setLineDash([6, 4]);
            ctx.strokeRect(dragStart.x, dragStart.y, dragCur.x - dragStart.x, dragCur.y - dragStart.y);
            ctx.setLineDash([]);
        }
    }

    function drawGrid() {
        ctx.strokeStyle = 'rgba(120,180,255,0.10)'; ctx.lineWidth = 1;
        ctx.fillStyle = '#5f7da0'; ctx.font = '10px sans-serif';
        const step = niceStep((bounds.maxX - bounds.minX) / 8);
        for (let x = Math.ceil(bounds.minX / step) * step; x <= bounds.maxX; x += step) {
            const p = worldToScreen(x, bounds.minZ);
            ctx.beginPath(); ctx.moveTo(p.x, 0); ctx.lineTo(p.x, canvas.height); ctx.stroke();
            ctx.fillText('x' + x, p.x + 2, 10);
        }
        for (let z = Math.ceil(bounds.minZ / step) * step; z <= bounds.maxZ; z += step) {
            const p = worldToScreen(bounds.minX, z);
            ctx.beginPath(); ctx.moveTo(0, p.y); ctx.lineTo(canvas.width, p.y); ctx.stroke();
            ctx.fillText('z' + z, 2, p.y - 2);
        }
    }

    function niceStep(raw) {
        const pow = Math.pow(10, Math.floor(Math.log10(raw)));
        const n = raw / pow;
        return (n >= 5 ? 5 : n >= 2 ? 2 : 1) * pow;
    }

    async function reload() {
        if (!IST.hasPerm('map.view')) return;
        try {
            const params = new URLSearchParams({
                world: world || '', minX: bounds.minX, minZ: bounds.minZ,
                maxX: bounds.maxX, maxZ: bounds.maxZ, cols: 48
            });
            const data = await IST.api('map?' + params.toString());
            mapData = data.map; regions = data.regions || []; storms = data.storms || [];
            if (mapData) {
                bounds = { minX: mapData.minX, minZ: mapData.minZ, maxX: mapData.maxX, maxZ: mapData.maxZ };
            }
            draw();
        } catch (e) { IST.toast('地图加载失败：' + e.message, false); }
    }

    async function loadWorlds() {
        try {
            const data = await IST.api('worlds');
            world = data.defaultWorld || (data.worlds[0] || null);
            const sel = document.getElementById('mapWorld');
            sel.innerHTML = data.worlds.map(w => `<option ${w === world ? 'selected' : ''}>${w}</option>`).join('');
            sel.addEventListener('change', () => { world = sel.value; fitView(); reload(); });
            fitView(); reload();
        } catch (e) { /* 无权限忽略 */ }
    }

    // ---- 交互 ----
    function setMode(m) {
        mode = m;
        document.getElementById('mapMode').textContent =
            '模式：' + (m === 'pan' ? '拖动' : m === 'region' ? '画框建区域' : '建风暴点');
        if (m === 'storm') stormDraft = [];
    }

    canvas.addEventListener('mousedown', e => {
        const rect = canvas.getBoundingClientRect();
        const sx = e.clientX - rect.left, sy = e.clientY - rect.top;
        dragging = true; dragStart = { x: sx, y: sy }; dragCur = { x: sx, y: sy };
        panStart = { ox: view.ox, oy: view.oy };
    });
    canvas.addEventListener('mousemove', e => {
        if (!dragging) return;
        const rect = canvas.getBoundingClientRect();
        dragCur = { x: e.clientX - rect.left, y: e.clientY - rect.top };
        if (mode === 'pan') {
            view.ox = panStart.ox + (dragCur.x - dragStart.x);
            view.oy = panStart.oy + (dragCur.y - dragStart.y);
        }
        draw();
    });
    canvas.addEventListener('mouseup', async e => {
        dragging = false;
        if (mode === 'region' && dragStart && dragCur) {
            const a = screenToWorld(dragStart.x, dragStart.y), b = screenToWorld(dragCur.x, dragCur.y);
            if (Math.abs(dragCur.x - dragStart.x) > 5) {
                IST.openRegionModal({
                    minX: Math.round(Math.min(a.x, b.x)), minZ: Math.round(Math.min(a.z, b.z)),
                    maxX: Math.round(Math.max(a.x, b.x)), maxZ: Math.round(Math.max(a.z, b.z))
                });
            }
        } else if (mode === 'storm' && dragStart) {
            const w = screenToWorld(dragStart.x, dragStart.y);
            stormDraft.push({ x: Math.round(w.x), z: Math.round(w.z) });
            draw();
            if (stormDraft.length >= 2) promptStormSave();
        }
        dragStart = dragCur = null;
    });
    canvas.addEventListener('wheel', e => {
        e.preventDefault();
        const rect = canvas.getBoundingClientRect();
        const sx = e.clientX - rect.left, sy = e.clientY - rect.top;
        const before = screenToWorld(sx, sy);
        view.scale *= (e.deltaY < 0 ? 1.1 : 0.9);
        const after = worldToScreen(before.x, before.z);
        view.ox += sx - after.x; view.oy += sy - after.y;
        draw();
    }, { passive: false });

    async function promptStormSave() {
        if (!confirm('已选 ' + stormDraft.length + ' 个点，创建风暴路径？')) { stormDraft = []; draw(); return; }
        const id = prompt('风暴 id：', 'typhoon-' + Date.now().toString().slice(-4));
        if (!id) { stormDraft = []; draw(); return; }
        const type = prompt('类型 TYPHOON / EXTREME_STORM：', 'TYPHOON') || 'TYPHOON';
        const radius = parseFloat(prompt('影响半径：', '150')) || 150;
        // 均匀分配到达时间：每段 600 秒
        const points = stormDraft.map((p, i) => ({ x: p.x, z: p.z, arriveAfterSeconds: i * 600 }));
        try {
            await IST.api('storm/path/set', 'POST', { id, type, world, radius, active: true, points });
            IST.toast('风暴路径已创建'); stormDraft = []; reload(); IST.loadStorms && IST.loadStorms();
        } catch (e) { IST.toast(e.message, false); }
    }

    // 工具栏按钮
    const bindMode = (id, m) => { const el = document.getElementById(id); if (el) el.addEventListener('click', () => setMode(m)); };
    bindMode('modePan', 'pan'); bindMode('modeRegion', 'region'); bindMode('modeStorm', 'storm');
    const mr = document.getElementById('mapReload'); if (mr) mr.addEventListener('click', reload);

    window.addEventListener('resize', resize);
    setTimeout(() => { resize(); loadWorlds(); }, 100);

    return { reload, currentWorld: () => world };
})();
