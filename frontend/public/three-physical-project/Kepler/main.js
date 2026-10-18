//开普勒三大定律
/*
第一定律（椭圆定律）：所有行星绕太阳运动的轨道都是椭圆，太阳处在椭圆的一个焦点上。极坐标方程r = \frac{a(1-e^2)}{1 + e \cos \theta}（其中a为半长轴 e为偏心率）。

    交互设计：左侧滑块实时拉动偏心率 e(0.00 到 0.80) 和 半长轴 a。随着 e 的改变，画面中的轨道线条实时被“拉长”或“压扁”。

    视觉呈现：场景中心放置一个带有 UnrealBloomPass 泛光特效的炽热太阳。
        轨道使用半透明的发光线条（THREE.Line）。
        椭圆的内部标记焦点（一个是实体的太阳，另一个是虚线的十字光标）。偏心率改变时，直观看到太阳始终钉在一个焦点上，而另一个空焦点不断移动。

第二定律（面积定律）：对任意一个行星来说，它与太阳的连线在相等的时间内扫过相等的面积。物理推论： 行星在近日点运动最快，在远日点运动最慢。（角动量守恒）
    
    面积扫掠 Sweep Area按钮:按下后，系统分别在行星经过近日点和远日点时，无数根连线涂出两个面积肉眼可见相等的半透明扇形。记录并高亮显示 $\Delta t$ （例如 2 秒）内扫过的扇形区域
    
    扫过的面积用半透明的多边形网格（THREE.Mesh 配合 THREE.DoubleSide 和加法混合 AdditiveBlending）来填充,AI 助教会弹窗提示：“经系统计算，这两块面积的像素级积分为：完全相等！这验证了开普勒的第二定律。”

第三定律（调和定律）：所有行星的轨道的半长轴的三次方跟它的公转周期的二次方的比值都相等。数学表达： \frac{a^3}{T^2} = k （k 是一个只与中心天体质量有关的常数）。

    交互设计：学生调节好一组 a 和 e 后，点击🚀 发射探测器。系统在场景中生成一颗新行星，并开始计时一圈的周期T。可以同时发射多颗颜色不同、轨道不同的行星。
    
    数据联动：每发射一颗，右侧表格就增加一行数据。表格动态跳动计算 a^3 和 T^2，
    最后一列显示比值。学生会发现，无论他们怎么搞轨道参数，只要中心太阳不换，表格最后一列的数值永远是一个固定的常数（比如 1.000）。

亮点：
    开普勒定律实验的核心在于非匀速运动的真实物理模拟。如果只用简单的圆周运动代码糊弄过去，第二定律（面积定律）就完全展示不出来。
    因此，底层的 JavaScript 中引入了开普勒方程 (M = E - e \sin E) 的牛顿-拉夫逊法 (Newton-Raphson method) 求解器。只有这样，行星在近日点才会真实地加速，在远日点才会真实地减速

    速度的可视化 (多普勒尾迹)：
    不要只让行星干巴巴地跑。给行星加上拖尾（Trail）。由于近日点速度快、远日点速度慢，可以让拖尾的颜色随速度动态渐变。比如速度快时尾迹呈现高能的蓝色/白色，速度慢时呈现低能的暗红色。这能让第二定律“原形毕露”。

    星空背景与景深 (Depth of Field)：
    将纯黑背景替换为带有微弱星屑的粒子系统（THREE.Points），并加入轻微的摄像机景深，使得远处的轨道虚化，增强宇宙的浩瀚感。

    AI打字机：写在TypeWriter函数里。HTML 标签的逐字解析渲染

优化：
    ①新增了“时间流逝倍率”调节。
    ②沉浸式星空与星云
    ③面积扫掠高亮闪烁
    ④深度实验操作记录（“本次绘制了多少根极径追踪线”，并给出积分面积相等的结论。）
    ⑤第二视角：添加跟随观测器视角。即拥有全局视角和各个“探测器视角”
    ⑥太阳建模优化：增加发光效果，缩小核心体积，避免覆盖轨道线条。
    ⑦性能优化：对扫掠线条 (sweepMeshes) 增加了 dispose() 处理，防止长时间运行实验导致显存溢出。
    ⑧添加微视角：即第一人称视角（坐在上面看太阳），感受经过近日点时，太阳迎面扑来的压迫感以及甩鞭子一样的加速感
    ⑨不断优化太阳和行星的建模：将太阳改为具有“光晕感”的层次结构，行星增加金属质感和发光边缘，使其更像“探测器”。
    ⑩不再硬编码 S1=S2。加入了积分模拟算法，根据当前探测器的速度和距离实时算出“伪面积”，让不同探测器、不同扫掠时间得出的数据各不相同，更具实验真实感
    11.基于采样率的误差公式：$Error \propto 1/\sqrt{N}$，让误差随着采样数量的增加而减少，反映积分近似的现实情况。
    12.优化AI助教的提示语，对于不同数量的探测器，给出不同的提示，增强互动性和针对性。
    13.优化第三定律计算逻辑。T_UI以及kValue的计算现在更贴近真实物理意义，模拟 0.5%的观测误差，让数据更有说服力和教育意义。
       虽然我们使用了天文单位和地球年，但仍然保留了微小的浮点数差异，同时在表格中突出显示 k 值
    14.优化数据表格、对应说明与AI助教文字，表格底部的注释不干扰视线，给 tfoot 增加一点样式。
    15.统一修改了字号
    16.添加重置按钮以整理较乱的盘面
    17.优化面积定律的统计算法，添加s1/s2比值以及对应统计矩阵板块
    18.添加了资源销毁函数
*/
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

// ==========================================
// 0. 统一资源释放模板 (增强版)
// ==========================================
let isDisposed = false;
let animationFrameId = null;
const activeTimeoutIds = new Set();
const activeIntervalIds = new Set();
const activeEventListeners = [];

function trackedRequestAnimationFrame(callback) {
    const id = requestAnimationFrame((...args) => {
        if (isDisposed) return;
        callback(...args);
    });
    animationFrameId = id;
    return id;
}

function trackedSetTimeout(fn, delay) {
    const id = window.setTimeout(() => {
        activeTimeoutIds.delete(id);
        if (isDisposed) return;
        fn();
    }, delay);
    activeTimeoutIds.add(id);
    return id;
}

function trackedSetInterval(fn, delay) {
    const id = window.setInterval(() => {
        if (isDisposed) return;
        fn();
    }, delay);
    activeIntervalIds.add(id);
    return id;
}

function trackedClearInterval(id) {
    clearInterval(id);
    activeIntervalIds.delete(id);
}

function trackedAddEventListener(target, event, handler, options) {
    target.addEventListener(event, handler, options);
    activeEventListeners.push({ target, event, handler, options });
}

function removeTrackedEventListeners() {
    for (const { target, event, handler, options } of activeEventListeners) {
        target.removeEventListener(event, handler, options);
    }
    activeEventListeners.length = 0;
}

function disposeMaterial(material) {
    if (!material) return;
    const textureKeys = [
        'map', 'lightMap', 'bumpMap', 'normalMap', 'specularMap',
        'envMap', 'alphaMap', 'gradientMap', 'emissiveMap',
        'metalnessMap', 'roughnessMap', 'aoMap', 'displacementMap', 'matcap'
    ];
    for (const key of textureKeys) {
        if (material[key]) { material[key].dispose(); material[key] = null; }
    }
    if (material.uniforms) {
        for (const uniform of Object.values(material.uniforms)) {
            if (uniform?.value instanceof THREE.Texture) { uniform.value.dispose(); }
        }
    }
    material.dispose();
}

function disposeObject3D(obj) {
    if (!obj) return;
    if (obj.children?.length > 0) {
        for (const child of obj.children) { disposeObject3D(child); }
    }
    if (obj.geometry) { obj.geometry.dispose(); obj.geometry = null; }
    if (obj.material) {
        const materials = Array.isArray(obj.material) ? obj.material : [obj.material];
        for (const mat of materials) { disposeMaterial(mat); }
        obj.material = null;
    }
}

function disposeSceneGraph(root) {
    if (!root) return;
    root.traverse((obj) => disposeObject3D(obj));
}

function stopAnimationLoops() {
    if (animationFrameId !== null) { cancelAnimationFrame(animationFrameId); animationFrameId = null; }
    for (const id of activeTimeoutIds) { clearTimeout(id); }
    activeTimeoutIds.clear();
    for (const id of activeIntervalIds) { clearInterval(id); }
    activeIntervalIds.clear();
    removeTrackedEventListeners();
}

function disposeThirdPartyLibraries() {
    if (typeof gsap !== 'undefined' && gsap.killTweensOf) { gsap.killTweensOf('*'); }
}

function disposeUI() {
    const uiContainers = ['log-container', 'ai-text', 'kepler-table', 'area-table'];
    for (const id of uiContainers) {
        const el = document.getElementById(id);
        if (el && el.tagName !== 'TABLE') el.innerHTML = '';
    }
}

function dispose() {
    if (isDisposed) return;
    isDisposed = true;
    stopAnimationLoops(); 
    disposeThirdPartyLibraries();
    disposeUI();
    disposeSceneGraph(scene);
    controls?.dispose();

    if (renderer) {
        renderer.dispose();
        renderer.renderLists?.dispose();
        if (renderer.forceContextLoss) renderer.forceContextLoss();
        if (renderer.domElement?.parentNode) {
            renderer.domElement.parentNode.removeChild(renderer.domElement);
        }
    }
    scene = null; camera = null; renderer = null; controls = null;
}

window.disposeExperiment = dispose;
window.addEventListener('beforeunload', dispose);
window.addEventListener('pagehide', dispose);

// ==========================================
// 1. 全局配置与环境搭建
// ==========================================
const CONFIG = {
    scale: 10,           
    timeSpeed: 1.0,      
    colors: [0x00e5ff, 0xff00ff, 0x00ff88, 0xffaa00, 0xff4444]
};

const canvas = document.querySelector('#c');
let renderer = new THREE.WebGLRenderer({ antialias: true, canvas });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setPixelRatio(window.devicePixelRatio);

let scene = new THREE.Scene();
scene.background = new THREE.Color(0x000205); 

let camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 2000);
camera.position.set(0, 40, 50);

let controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.maxPolarAngle = Math.PI / 2 + 0.1;
controls.rotateSpeed = 0.5;
controls.enablePan = false; 

scene.add(new THREE.GridHelper(150, 75, 0x112233, 0x081015));

// ==========================================
// 2. 日志与 AI 模块
// ==========================================
function addLog(msg) {
    const container = document.getElementById('log-container');
    if (!container) return;
    container.style.display = 'block';
    container.style.opacity = '1';
    const time = new Date().toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const div = document.createElement('div');
    div.className = 'log-item';
    div.innerHTML = `<div class="time">[${time}]</div><div class="log-content">${msg}</div>`;
    container.prepend(div);
    if (container.children.length > 30) container.removeChild(container.lastChild);
}

let typeTimer = null;
function streamAIText(text) {
    const dom = document.getElementById('ai-text');
    if (typeTimer) trackedClearInterval(typeTimer);
    dom.innerHTML = "";
    let i = 0;
    typeTimer = trackedSetInterval(() => {
        if (i < text.length) {
            if (text[i] === '<') {
                let tagEnd = text.indexOf('>', i);
                dom.innerHTML += text.substring(i, tagEnd + 1);
                i = tagEnd + 1;
            } else {
                dom.innerHTML += text[i];
                i++;
            }
        } else {
            trackedClearInterval(typeTimer);
        }
    }, 20);
}

// ==========================================
// 3. 沉浸式星空与星云背景
// ==========================================
const backgroundGroup = new THREE.Group();

const starsGeometry = new THREE.BufferGeometry();
const starsCount = 3000;
const posArray = new Float32Array(starsCount * 3);
for(let i = 0; i < starsCount * 3; i++) {
    posArray[i] = (Math.random() - 0.5) * 600; 
}
starsGeometry.setAttribute('position', new THREE.BufferAttribute(posArray, 3));
const starsMaterial = new THREE.PointsMaterial({
    size: 0.8, color: 0xffffff, transparent: true, opacity: 0.8, blending: THREE.AdditiveBlending
});
backgroundGroup.add(new THREE.Points(starsGeometry, starsMaterial));

function createNebulaTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 128; canvas.height = 128;
    const ctx = canvas.getContext('2d');
    const gradient = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    gradient.addColorStop(0, 'rgba(255, 255, 255, 1)');
    gradient.addColorStop(0.3, 'rgba(0, 229, 255, 0.5)');
    gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, 128, 128);
    return new THREE.CanvasTexture(canvas);
}
const nebulaGeo = new THREE.BufferGeometry();
const nebulaPos = new Float32Array(50 * 3);
for(let i=0; i<50; i++){
    nebulaPos[i*3] = (Math.random() - 0.5) * 400;
    nebulaPos[i*3+1] = (Math.random() - 0.5) * 100 - 50; 
    nebulaPos[i*3+2] = (Math.random() - 0.5) * 400;
}
nebulaGeo.setAttribute('position', new THREE.BufferAttribute(nebulaPos, 3));
const nebulaMat = new THREE.PointsMaterial({
    size: 50, map: createNebulaTexture(), transparent: true, opacity: 0.1, depthWrite: false, blending: THREE.AdditiveBlending
});
backgroundGroup.add(new THREE.Points(nebulaGeo, nebulaMat));
scene.add(backgroundGroup);

// ==========================================
// 4. 太阳模型与轨道预览
// ==========================================
const sunGroup = new THREE.Group();

// 程序化生成太阳表面斑驳/耀斑纹理
function createSunSurfaceTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 512; canvas.height = 512;
    const ctx = canvas.getContext('2d');
    
    // 基础渐变底色
    const gradient = ctx.createRadialGradient(256, 256, 0, 256, 256, 256);
    gradient.addColorStop(0, '#ffffff');
    gradient.addColorStop(0.2, '#ffcc00');
    gradient.addColorStop(0.8, '#ff4400');
    gradient.addColorStop(1, '#880000');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, 512, 512);

    // 叠加微弱噪点模拟米粒组织
    const imgData = ctx.getImageData(0, 0, 512, 512);
    for (let i = 0; i < imgData.data.length; i += 4) {
        const noise = (Math.random() - 0.5) * 40;
        imgData.data[i] = Math.min(255, imgData.data[i] + noise);
        imgData.data[i+1] = Math.min(255, imgData.data[i+1] + noise);
    }
    ctx.putImageData(imgData, 0, 0);
    return new THREE.CanvasTexture(canvas);
}

// 太阳主核心
const sunCoreMat = new THREE.MeshBasicMaterial({ 
    map: createSunSurfaceTexture(),
    color: 0xffffff
});
const sunCore = new THREE.Mesh(new THREE.SphereGeometry(1.2, 64, 64), sunCoreMat);
sunGroup.add(sunCore);

// 色球层 (Additive Glow)
const chromosphere = new THREE.Mesh(
    new THREE.SphereGeometry(1.3, 64, 64),
    new THREE.MeshBasicMaterial({ 
        color: 0xff6600, 
        transparent: true, 
        opacity: 0.5, 
        blending: THREE.AdditiveBlending 
    })
);
sunGroup.add(chromosphere);

// 外部日冕晕影
const corona = new THREE.Mesh(
    new THREE.SphereGeometry(2.0, 64, 64),
    new THREE.MeshBasicMaterial({ 
        color: 0xffaa00, 
        transparent: true, 
        opacity: 0.15, 
        blending: THREE.AdditiveBlending 
    })
);
sunGroup.add(corona);

// 太阳系光源
const sunLight = new THREE.PointLight(0xffddaa, 3000, 500);
sunGroup.add(sunLight);
scene.add(sunGroup);
// ==========================================

let previewLine;
const sliderA = document.getElementById('slider-a');
const sliderE = document.getElementById('slider-e');
const sliderSpeed = document.getElementById('slider-speed');
const cameraTargetSelect = document.getElementById('camera-target');

function updatePreviewOrbit() {
    if (previewLine) {
        scene.remove(previewLine);
        disposeObject3D(previewLine);
    }
    const aUI = parseFloat(sliderA.value);
    const e = parseFloat(sliderE.value);
    const a = aUI * CONFIG.scale;
    const b = a * Math.sqrt(1 - e * e);
    const c = a * e; 

    const curve = new THREE.EllipseCurve(0, 0, a, b, 0, 2 * Math.PI, false, 0);
    const geometry = new THREE.BufferGeometry().setFromPoints(curve.getPoints(120));
    const material = new THREE.LineDashedMaterial({ color: 0xffaa00, dashSize: 1, gapSize: 1, transparent: true, opacity: 0.5 });
    
    previewLine = new THREE.Line(geometry, material);
    previewLine.computeLineDistances();
    previewLine.rotation.x = -Math.PI / 2;
    previewLine.position.x = -c; 
    scene.add(previewLine);
    
    document.getElementById('val-a').innerText = aUI.toFixed(1);
    document.getElementById('val-e').innerText = e.toFixed(2);
}

trackedAddEventListener(sliderA, 'input', updatePreviewOrbit);
trackedAddEventListener(sliderE, 'input', updatePreviewOrbit);
trackedAddEventListener(sliderSpeed, 'input', (e) => {
    CONFIG.timeSpeed = parseFloat(e.target.value);
    document.getElementById('val-speed').innerText = CONFIG.timeSpeed.toFixed(1) + 'x';
});
updatePreviewOrbit();

// ==========================================
// 5. 核心物理实验引擎
// ==========================================
const planets = [];
let sweepModeActive = false;
let sweepMeshes = [];

function solveKepler(M, e) {
    let E = M;
    for (let i = 0; i < 5; i++) E = E - (E - e * Math.sin(E) - M) / (1 - e * Math.cos(E));
    return E;
}

// 🚀 发射探测器 
trackedAddEventListener(document.getElementById('btn-add'), 'click', () => {
    const aUI = parseFloat(sliderA.value);
    const e = parseFloat(sliderE.value);
    const a = aUI * CONFIG.scale;
    const b = a * Math.sqrt(1 - e * e);
    const observationError = 0.995 + Math.random() * 0.01; 
    const T_UI = Math.pow(aUI, 1.5) * observationError;
    const color = CONFIG.colors[planets.length % CONFIG.colors.length];

    const curve = new THREE.EllipseCurve(0, 0, a, b, 0, 2 * Math.PI, false, 0);
    const orbitLine = new THREE.Line(
        new THREE.BufferGeometry().setFromPoints(curve.getPoints(100)),
        new THREE.LineBasicMaterial({ color: color, transparent: true, opacity: 0.3 })
    );
    orbitLine.rotation.x = -Math.PI / 2;
    orbitLine.position.x = -a * e;
    scene.add(orbitLine);

    const planetMesh = new THREE.Mesh(
        new THREE.SphereGeometry(0.5, 24, 24), 
        new THREE.MeshStandardMaterial({ 
            color: color, emissive: color, emissiveIntensity: 0.4, metalness: 0.9, roughness: 0.1 
        })
    );
    scene.add(planetMesh);

    // 【新增/修改】：为每个探测器添加积分状态属性
    planets.push({ 
        mesh: planetMesh, orbitLine: orbitLine, aUI, a, b, e, T: T_UI, timePassed: 0, color,
        lastPos: new THREE.Vector3(),
        s1AccTime: 0, // 远日点数据
        s2AccTime: 0, // 近日点数据
        targetSampleTime: 0.1    // 每个窗口统计 0.5 轨道单位时间
    });
    const pIndex = planets.length - 1;

    const aiComments = [
        `探测器 #${pIndex + 1} 已进入预定轨道。观测其运行速度：在近日点（左侧）最快，远日点最慢。`,
        `新目标已部署。半长轴 ${aUI.toFixed(1)} AU 决定了它的公转周期。试试开启面积扫掠验证第二定律。`,
        `成功发射！观察数据矩阵，你会发现无论轨道如何变化，a³ / T² 始终趋近于 1.00。`
    ];
    streamAIText(aiComments[pIndex % aiComments.length]);

    addLog(`🚀 <strong>探测器 #${pIndex + 1} 部署成功</strong><br>` +
           `• 半长轴: ${aUI.toFixed(2)} AU<br>` +
           `• 离心率: ${e.toFixed(2)}<br>` +
           `• 预估周期: ${T_UI.toFixed(2)} 年`);

    const tr = document.createElement('tr');
    tr.style.color = '#' + new THREE.Color(color).getHexString();
    const kValue = (Math.pow(aUI, 3) / Math.pow(T_UI, 2)).toFixed(3);
    tr.innerHTML = `
        <td>#${pIndex + 1}</td>
        <td>${aUI.toFixed(2)}</td>
        <td>${T_UI.toFixed(2)}</td>
        <td style="font-weight:bold; color:#ffaa00;">${kValue}</td>
    `;
    document.querySelector('#kepler-table tbody').appendChild(tr);

    if (planets.length === 1) {
        streamAIText(`🌟 <strong>发现规律了吗？</strong> 注意调和定律矩阵。无论你如何调节轨道，a³ / T² 的值始终趋近于 <strong>1.00</strong>。这是因为我们使用了天文单位(AU)和地球年。这个“1”实际上代表了<strong>太阳的质量</strong>。`);
    }

    const optTrack = document.createElement('option');
    optTrack.value = pIndex + '_track';
    optTrack.innerText = `🛰️ 旁观追踪: 探测器 ${pIndex + 1}`;
    cameraTargetSelect.appendChild(optTrack);

    const optFPV = document.createElement('option');
    optFPV.value = pIndex + '_fpv';
    optFPV.innerText = `🎥 第一人称 (登船): 探测器 ${pIndex + 1}`;
    cameraTargetSelect.appendChild(optFPV);
});

// 🔄 重置系统盘面
trackedAddEventListener(document.getElementById('btn-reset'), 'click', () => {
    planets.forEach(p => {
        scene.remove(p.mesh);
        disposeObject3D(p.mesh);
        scene.remove(p.orbitLine);
        disposeObject3D(p.orbitLine);
    });
    planets.length = 0;
    
    sweepMeshes.forEach(m => { scene.remove(m); disposeObject3D(m); });
    sweepMeshes.length = 0;
    sweepModeActive = false;
    
    const btnSweep = document.getElementById('btn-sweep');
    btnSweep.disabled = false;
    btnSweep.style.background = '';
    btnSweep.innerText = '📐 演示面积定律';

    document.querySelector('#kepler-table tbody').innerHTML = '';
    document.querySelector('#area-table tbody').innerHTML = '';
    document.getElementById('log-container').innerHTML = '';
    cameraTargetSelect.innerHTML = '<option value="-1">🌞 自由全局视角 (锁定太阳)</option>';
    
    addLog("🔄 实验盘面已重置，所有资源已清理。");
    streamAIText("实验室已清理完毕。请重新调节参数，发射新的探测器！");
});

// 📐 面积定律演示交互 
trackedAddEventListener(document.getElementById('btn-sweep'), 'click', () => {
    const btn = document.getElementById('btn-sweep');
    if (planets.length === 0) {
        streamAIText("<span style='color:#ff4444;'>操作被拦截：</span>空间站目前没有运行中的探测器，请先发射一个。");
        return;
    }

    if (!sweepModeActive) {
        sweepModeActive = true;
        btn.style.background = 'rgba(255, 0, 255, 0.4)';
        btn.innerText = '🛑 停止扫掠并进行积分统计';
        addLog(`📐 <strong>启动面积扫掠观测...</strong>`);
        
        if(planets.length === 1) {
             streamAIText(`📢 <strong>正在进行几何积分：</strong>系统正在记录探测器扫过的扇形区域。请等待其掠过近日点或远日点，采样线越多，精度越高；<strong>注意：</strong>系统将以当前唯一的探测器为主进行面积积分统计。`);
        } else {
             streamAIText(`📢 <strong>正在进行几何积分：</strong>系统正在记录探测器扫过的扇形区域。请等待其掠过近日点或远日点，采样线越多，精度越高；<br><strong>注意：</strong>当前有 ${planets.length} 个探测器在运行。系统将以最新发射的探测器为主进行面积积分统计。`);
        }
        const p = planets[planets.length - 1];
        p.s1Area = 0;p.s1AccTime = 0;
        p.s2Area = 0;p.s2AccTime = 0;

    }
    //else表示当前已经在扫掠模式，用户点击了停止按钮，此时需要进行积分统计并展示结果
    else {
        sweepModeActive = false;
        btn.disabled = true;
        btn.innerText = '📊 积分核算中...';

        const p = planets[planets.length - 1];
        
        // 【核心改进】：根据采样完整度判断显示内容
        const isS1Ready = p.s1AccTime >= p.targetSampleTime;
        const isS2Ready = p.s2AccTime >= p.targetSampleTime;

        const s1Disp = isS1Ready ? p.s1Area.toFixed(2) : "未经过";
        const s2Disp = isS2Ready ? p.s2Area.toFixed(2) : "未经过";
        let ratioDisp = "N/A";

        const lineCount = sweepMeshes.length;
        //let s1Area, s2Area, ratio;
        
        // if (lineCount === 0) {
        //     s1Area = "0.0000"; s2Area = "0.0000"; ratio = "N/A";
        // } else {
        //     // 通过采样数量模拟伪面积计算，确保模拟出相等的扫掠面积
        //     const baseArea = (lineCount / 2) * 0.05;
        //     // 引入随机微小误差增加实验感
        //     s1Area = (baseArea * (1 + (Math.random() - 0.5) * 0.01)).toFixed(4);
        //     s2Area = (baseArea * (1 + (Math.random() - 0.5) * 0.01)).toFixed(4);
        //     ratio = (s1Area / s2Area).toFixed(3);
        // }
        

        if (isS1Ready && isS2Ready) {
            ratioDisp = (p.s1Area / p.s2Area).toFixed(3);
            streamAIText("✨ <strong>验证成功：</strong>无论运行到远日点还是近日点，探测器在相等时间内扫过的面积 S₁ 与 S₂ 高度一致（比值趋近于1）。这证明了角动量守恒在引力场下的表现。");
        } else {
            streamAIText("⚠️ <strong>采样不足：</strong>记录时间太短可能导致数据偶发偏差。建议在探测器高速移动时多记录一会儿。");
        }

        addLog(`✅ <strong>积分实验完成：</strong><br>` + 
               `• S₁ (远日点)：<span style="color:#00e5ff">${s1Disp} units²</span><br>` +
               `• S₂ (近日点)：<span style="color:#ffaa00">${s2Disp} units²</span><br>` +
               `• S₁/S₂ 比值：<strong style="color:#00ff88">${ratioDisp}</strong>`);

        // 更新面积矩阵表格
        //这行没用
        //const latestPlanet = planets[planets.length - 1];
        const tr = document.createElement('tr');
        tr.style.color = '#' + new THREE.Color(p.color).getHexString();
        tr.innerHTML = `
            <td>#${planets.length}</td>
            <td>${s1Disp}</td>
            <td>${s2Disp}</td>
            <td style="font-weight:bold; color:#00ff88;">${ratioDisp}</td>
        `;
        document.querySelector('#area-table tbody').appendChild(tr);
        
        if (isS1Ready && isS2Ready) {
            sweepMeshes.forEach((line, i) => {
                gsap.to(line.material, {
                    opacity: 0.8, yoyo: true, repeat: 20, duration: 0.2,
                    onComplete: () => {
                        if(i === lineCount - 1) {
                            sweepMeshes.forEach(m => { scene.remove(m); disposeObject3D(m); });
                            sweepMeshes = [];
                            btn.disabled = false;
                            btn.style.background = '';
                            btn.innerText = '📐 演示面积定律';
                        }
                    }
                });
            });
        } else {
            // 如果采样不足，直接清理扫掠线条并重置按钮状态
            sweepMeshes.forEach(m => { scene.remove(m); disposeObject3D(m); });
            sweepMeshes = [];
            btn.disabled = false;
            btn.style.background = '';
            btn.innerText = '📐 演示面积定律';
        }
    }
});

// ==========================================
// 6. 渲染循环与多维视角控制
// ==========================================
const clock = new THREE.Clock();
let lastCameraMode = '-1'; 

function animate() {
    trackedRequestAnimationFrame(animate);
    const dt = clock.getDelta();
    backgroundGroup.rotation.y += 0.0005;

    planets.forEach((p, index) => {
        p.timePassed += dt * CONFIG.timeSpeed;
        const M = ((2 * Math.PI) / p.T) * p.timePassed;
        const currentM = M % (2 * Math.PI);
        const E = solveKepler(currentM, p.e);
        const x = p.a * Math.cos(E) - p.a * p.e;
        const z = p.b * Math.sin(E);
        const currentPos = new THREE.Vector3(x, 0, -z);
        p.mesh.position.copy(currentPos);

        // 面积扫掠：只对最新发射的探测器进行物理绘制
        if (sweepModeActive && index === planets.length - 1) {
            const deltaArea = new THREE.Vector3().crossVectors(p.lastPos, currentPos).length() * 0.5;
            // 判定区域 (利用弧度制：0/2PI 是近日点，PI 是远日点)
            const isPeri = (currentM < 0.26 || currentM > 2 * Math.PI - 0.26); // 近日点 ±15度
            const isAphel = (Math.abs(currentM - Math.PI) < 0.26);            // 远日点 ±15度
            if (isPeri || isAphel) {
                //geo是从当前探测器位置到太阳的极径线段，随着探测器运动不断生成新的线段来可视化扫掠区域
                const geo = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0,0,0), new THREE.Vector3(x,0,-z)]);
                //line是极径线段的可视化对象，颜色根据当前区域（近日点或远日点）动态设置，并且初始透明度较低，随着时间累积逐渐变得明显
                const line = new THREE.Line(geo, new THREE.LineBasicMaterial({color: p.color, transparent:true, opacity:0.15}));
                scene.add(line);
                sweepMeshes.push(line);
            
                //如果当前时间窗口内，累计的扫掠时间还未达到目标采样时间，则继续累积面积和时间
                if (isPeri && p.s2AccTime < p.targetSampleTime) {
                    p.s2Area += deltaArea;
                    p.s2AccTime += dt * CONFIG.timeSpeed;
                } 
                else if (isAphel&& p.s1AccTime < p.targetSampleTime) {
                    p.s1Area += deltaArea;
                    p.s1AccTime += dt * CONFIG.timeSpeed;
                }
                //如果扫掠线条过多，逐步移除最旧的线条以控制性能和视觉效果
                if(sweepMeshes.length > 800) {
                    const old = sweepMeshes.shift();
                    scene.remove(old);
                    disposeObject3D(old);
                }
            }
            //更新上一个位置为当前的位置，为下一次的面积计算做准备
            p.lastPos.copy(currentPos);
        }
    });

    const currentMode = cameraTargetSelect.value;
    
    if (lastCameraMode !== currentMode) {
        controls.enabled = true; 
        if (currentMode === '-1') {
            addLog("🎥 切换至：全局自由视角");
        } else if (currentMode.endsWith('_fpv')) {
            const idx = parseInt(currentMode.split('_')[0]);
            const p = planets[idx];
            camera.position.set(p.mesh.position.x, p.mesh.position.y + 2, p.mesh.position.z + 5);
            addLog(`🎥 进入探测器 ${idx + 1} 的舱内视角 (可用鼠标环顾)`);
        }
    }
    lastCameraMode = currentMode;

    if (currentMode === '-1') {
        controls.target.lerp(new THREE.Vector3(0, 0, 0), 0.1);
    } 
    else if (currentMode.endsWith('_track')) {
        const targetIndex = parseInt(currentMode.split('_')[0]);
        if (planets[targetIndex]) {
            controls.target.lerp(planets[targetIndex].mesh.position, 0.1);
        }
    } 
    else if (currentMode.endsWith('_fpv')) {
        const targetIndex = parseInt(currentMode.split('_')[0]);
        const p = planets[targetIndex];
        if (p) {
            const currentPos = p.mesh.position.clone();
            const offset = currentPos.clone().sub(controls.target);
            camera.position.add(offset);
            controls.target.copy(currentPos);
        }
    }

    controls.update(); 
    renderer.render(scene, camera);
}

trackedAddEventListener(window, 'resize', () => {
    if (camera && renderer) {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
    }
});

streamAIText("你好！我是实验助教。在这里我们将探究开普勒定律。<b>1 AU</b>≈1.5亿公里。调节参数并发射探测器开始实验吧！");
animate();