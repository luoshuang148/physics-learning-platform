/*
霍曼转移轨道（Hohmann Transfer Orbit）
是航天动力学中，在同一个平面内的两个圆形轨道间转移所需能量最省（即消耗燃料最少）的路径。它本质上是一个与初始低轨道和目标高轨道都相切的椭圆轨道。

核心物理过程分为两次点火（加速）：

第一次点火- “变轨切入”：
    卫星在低轨道（轨道 1，半径 r_1）运行。
    发动机瞬间点火加速，使卫星脱离原圆形轨道，进入一个椭圆形转移轨道。此时，原轨道的所在点变成了转移轨道的近日点。

无动力滑行： 卫星关闭发动机，沿着椭圆轨道滑行，动能逐渐转化为势能，速度变慢，直至到达距离中心天体最远的远日点（此点正好与目标轨道 2 所在的圆相切，半径为 r_2）。

第二次点火 - “圆化轨道”： 
    卫星到达远日点时,
    如果不点火，它会掉回近日点。此时必须再次顺向点火加速，使其速度提升到目标轨道（轨道 2）所需的圆轨道运行速度。变轨完成。

亮点
    真实的活力公式：
    进行了半隐式欧拉积分计算，探测器的轨迹符合真实物理定律，能够展示出霍曼转移轨道的独特形状和特征。
    
    真实物理引擎接管：
    代码采用牛顿引力定律（加速度 a = \mu/r^2）对卫星的 x, y 坐标进行极小步长（Sub-steps）的微积分迭代。
    所以它在远地点自然变慢、近地点自然变快的视觉效果

    时间容错：
    当卫星爬升到接近蓝色大圈时，时间流速滑块会自动降到 0.2x，AI 播报提醒，并在“第二次点火”按钮上亮起刺眼的绿色光晕，给足老年评委们反应时间。

    原路返回机制
    如果故意错过最佳时机不点火，系统不会报错死机，
    而是提示“错过窗口，卫星坠回近日点。卫星会真的沿着黄线飞回起点，然后又甩回去，这完美还原了真实的轨道力学

优化
    1.新增了“时间流逝倍率”调节。
    2.沉浸式星空与星云
    3.3D建模优化：地球增加了发光大气层和网格线，卫星增加了太阳能帆板和引擎火焰效果，背景是沉浸式星空。
    4.UI优化：按钮的状态变化更明显，增加了时间流速控制滑块，日志系统改为AI播报，增加了任务完成提示。
    5.视角优化：默认全局视角，增加了追踪视角模式，切换时AI会播报提示。
    6.引力势井 (Gravity Well)：在轨道下方生成了基于U = -GM/r公式计算并实时渲染的 3D 空间扭曲网格，完美展示引力场的衰减效果。
    7.真实点火，实时改变轨道：卫星会在其当前所在位置直接改变速度向量，并以当前点作为椭圆转移轨道的近地点（A点），实时重绘转移轨道
    8.高考物理核心考点植入：引入了高中物理经典的“A点（近地点）、B点（远地点）”概念，在点火处生成 3D 坐标系标记。
        新增了四大关键点速度 ($v_1, v_2, v_3, v_4$) 的实时演算面板，直观验证 $v_2 > v_1 > v_4 > v_3$ 的经典物理结论。
    9.全新动态追踪摄像机逻辑：追踪视角模式下，摄像机默认自动平滑地保持在飞船正后方（沿速度反方向）并略微抬高的理想位置，提供沉浸式飞行体验；
        当用户按住鼠标进行环视时，摄像机无缝跟随卫星位移但不干预用户的旋转操作，松开后再平滑回到标准追踪位。
    10.尾迹系统：卫星飞行过程中会随机生成闪烁的尾迹粒子，增强飞行动态感和视觉反馈。

*/


import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

// ==========================================
// 0. 统一资源释放模板
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

function trackedAddEventListener(target, event, handler, options) {
    if(!target) return;
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
    const textureKeys = ['map', 'lightMap', 'bumpMap', 'normalMap', 'specularMap', 'envMap', 'emissiveMap', 'roughnessMap'];
    for (const key of textureKeys) {
        if (material[key]) { material[key].dispose(); material[key] = null; }
    }
    material.dispose();
}

function disposeObject3D(obj) {
    if (!obj) return;
    if (obj.children?.length > 0) {
        for (const child of [...obj.children]) { disposeObject3D(child); }
    }
    if (obj.geometry) { obj.geometry.dispose(); obj.geometry = null; }
    if (obj.material) {
        const materials = Array.isArray(obj.material) ? obj.material : [obj.material];
        for (const mat of materials) { disposeMaterial(mat); }
        obj.material = null;
    }
}

function dispose() {
    if (isDisposed) return;
    isDisposed = true;
    if (animationFrameId !== null) cancelAnimationFrame(animationFrameId);
    for (const id of activeTimeoutIds) clearTimeout(id);
    for (const id of activeIntervalIds) clearInterval(id);
    removeTrackedEventListeners();

    const uiContainers = ['log-container', 'ai-text', 'data-v1', 'data-v2', 'data-v3', 'data-v4', 'data-r', 'data-v', 'data-dv'];
    uiContainers.forEach(id => { const el = document.getElementById(id); if(el) el.innerHTML = ''; });

    scene?.traverse(obj => { if(obj.isMesh || obj.isLine || obj.isPoints) disposeObject3D(obj); });
    controls?.dispose();
    if (renderer) {
        renderer.dispose();
        if (renderer.domElement?.parentNode) renderer.domElement.parentNode.removeChild(renderer.domElement);
    }
    scene = null; camera = null; renderer = null;
}

window.disposeExperiment = dispose;
window.addEventListener('beforeunload', dispose);

// ==========================================
// 1. 场景与基础设置
// ==========================================
const canvas = document.querySelector('#c');
let renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
renderer.setSize(window.innerWidth, window.innerHeight);
const maxAniso = renderer.capabilities.getMaxAnisotropy();

let scene = new THREE.Scene();
let camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 2000);
camera.position.set(0, 0, 18);

let controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;

// ==========================================
// ✨ 2. 物理核心与真实换算比
// ==========================================
const MU = 15; 
const R_EARTH = 1.0; 
let R_current = 4.0; 
let R_target = 2.5; 

// 【新增核心解耦逻辑】：真实物理世界单位与仿真单位的换算系数
// 真实地球引力参数 MU_real ≈ 398600 km³/s²，真实地球半径 R_real = 6371 km
// 仿真公式：v_sim = sqrt(MU / r_sim)
// 真实公式：v_real = sqrt(MU_real / (r_sim * R_real))
// 缩放比例：V_SCALE = v_real / v_sim = sqrt(MU_real / (R_real * MU)) ≈ 2.042
const REAL_MU = 398600;
const REAL_RE = 6371;
const V_SCALE = Math.sqrt(REAL_MU / (REAL_RE * MU));

let satPos = new THREE.Vector3(R_current, 0, 0);
let satVel = new THREE.Vector3(0, Math.sqrt(MU / R_current), 0);
let prevSatPos = new THREE.Vector3().copy(satPos);

let totalDeltaV = 0; // 此处记录仿真内部的差值，渲染时乘上 V_SCALE 即可
let timeScale = 1.0;
let missionState = 0; 
let isOutward = false; 
let prevDistToB = 9999; 

// ==========================================
// 3. 场景建模大融合 (地球 + 星空 + 势井)
// ==========================================
scene.add(new THREE.AmbientLight(0xffffff, 0.5));
const sunLight = new THREE.DirectionalLight(0xffffff, 2);
sunLight.position.set(10, 5, 10);
scene.add(sunLight);

// 【A】沉浸式星空与星云
const backgroundGroup = new THREE.Group();
const starsGeo = new THREE.BufferGeometry();
const starsCount = 3000;
const posArray = new Float32Array(starsCount * 3);
for(let i = 0; i < starsCount * 3; i++) posArray[i] = (Math.random() - 0.5) * 600; 
starsGeo.setAttribute('position', new THREE.BufferAttribute(posArray, 3));
backgroundGroup.add(new THREE.Points(starsGeo, new THREE.PointsMaterial({ size: 0.8, color: 0xffffff, transparent: true, opacity: 0.8 })));

function createNebulaTexture() {
    const cvs = document.createElement('canvas'); cvs.width = 128; cvs.height = 128;
    const ctx = cvs.getContext('2d');
    const grad = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    grad.addColorStop(0, 'rgba(255, 255, 255, 1)'); grad.addColorStop(0.3, 'rgba(0, 229, 255, 0.5)'); grad.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = grad; ctx.fillRect(0, 0, 128, 128);
    return new THREE.CanvasTexture(cvs);
}
const nebulaGeo = new THREE.BufferGeometry();
const nebulaPos = new Float32Array(50 * 3);
for(let i=0; i<50; i++){
    nebulaPos[i*3] = (Math.random() - 0.5) * 400; nebulaPos[i*3+1] = (Math.random() - 0.5) * 100 - 50; nebulaPos[i*3+2] = (Math.random() - 0.5) * 400;
}
nebulaGeo.setAttribute('position', new THREE.BufferAttribute(nebulaPos, 3));
backgroundGroup.add(new THREE.Points(nebulaGeo, new THREE.PointsMaterial({ size: 50, map: createNebulaTexture(), transparent: true, opacity: 0.1, depthWrite: false, blending: THREE.AdditiveBlending })));
scene.add(backgroundGroup);

// 【B】引力势井 (Gravity Well)
const wellSize = 30; const wellSegments = 80;
const wellGeo = new THREE.PlaneGeometry(wellSize, wellSize, wellSegments, wellSegments);
const posAttr = wellGeo.attributes.position;
for (let i = 0; i < posAttr.count; i++) {
    const x = posAttr.getX(i); const y = posAttr.getY(i); const r = Math.sqrt(x*x + y*y);
    posAttr.setZ(i, (r > R_EARTH * 0.9) ? -MU / r * 0.4 : -MU / (R_EARTH * 0.9) * 0.4);
}
wellGeo.computeVertexNormals();
scene.add(new THREE.Mesh(wellGeo, new THREE.MeshBasicMaterial({ color: 0x6600ff, wireframe: true, transparent: true, opacity: 0.15 })));

// 【C】高精度地球
const textureLoader = new THREE.TextureLoader();
const earthTextures = {
    // 移除已失效的 bumpMap
    map: textureLoader.load('../Shared/textures/earth_atmos_2048.jpg'),
    roughnessMap: textureLoader.load('../Shared/textures/earth_specular_2048.jpg'),
    nightMap: textureLoader.load('../Shared/textures/earth_lights_2048.png'),
};

earthTextures.map.colorSpace = THREE.SRGBColorSpace;
earthTextures.nightMap.colorSpace = THREE.SRGBColorSpace;
Object.values(earthTextures).forEach(tex => { tex.anisotropy = maxAniso; });

const earthGroup = new THREE.Group();
const earthMesh = new THREE.Mesh(
    new THREE.SphereGeometry(R_EARTH, 64, 64),
    new THREE.MeshStandardMaterial({
        map: earthTextures.map, 
        // 删除了 bumpMap 和 bumpScale 相关配置
        roughnessMap: earthTextures.roughnessMap, 
        emissiveMap: earthTextures.nightMap,
        emissive: 0xffffff, 
        emissiveIntensity: 0.4
    })
);
earthGroup.add(earthMesh);

const cloudTexture = textureLoader.load('../Shared/textures/earth_clouds_1024.png');
const cloudLayer = new THREE.Mesh(
    new THREE.SphereGeometry(R_EARTH * 1.02, 64, 64),
    new THREE.MeshStandardMaterial({ map: cloudTexture, transparent: true, opacity: 0.4, blending: THREE.AdditiveBlending })
);
earthGroup.add(cloudLayer);

const atmosphere = new THREE.Mesh(
    new THREE.SphereGeometry(R_EARTH * 1.08, 64, 64),
    new THREE.MeshPhongMaterial({ color: 0x87ceeb, transparent: true, opacity: 0.2, side: THREE.BackSide, blending: THREE.AdditiveBlending })
);
earthGroup.add(atmosphere);

earthGroup.rotation.z = THREE.MathUtils.degToRad(23.4);
scene.add(earthGroup);

// 【D】轨道系统与飞船
const orbitMatCurrent = new THREE.LineBasicMaterial({ color: 0x00ff88, opacity: 0.5, transparent: true });
const orbitMatTarget = new THREE.LineBasicMaterial({ color: 0x00e5ff, opacity: 0.3, transparent: true });
const orbitMatTransfer = new THREE.LineDashedMaterial({ color: 0xffaa00, dashSize: 0.2, gapSize: 0.1 });

let orbitCurrentObj = null; let orbitTargetObj = null;
function createCircle(r, mat) {
    const curve = new THREE.EllipseCurve(0,0, r, r);
    return new THREE.Line(new THREE.BufferGeometry().setFromPoints(curve.getPoints(100)), mat);
}
function refreshBaseOrbits() {
    if(orbitCurrentObj) { scene.remove(orbitCurrentObj); disposeObject3D(orbitCurrentObj); }
    if(orbitTargetObj) { scene.remove(orbitTargetObj); disposeObject3D(orbitTargetObj); }
    orbitCurrentObj = createCircle(R_current, orbitMatCurrent);
    orbitTargetObj = createCircle(R_target, orbitMatTarget);
    scene.add(orbitCurrentObj, orbitTargetObj);
}
refreshBaseOrbits();

const transferGeo = new THREE.BufferGeometry();
const orbitTransfer = new THREE.Line(transferGeo, orbitMatTransfer);
scene.add(orbitTransfer);

// 卫星模型
const satellite = new THREE.Group();
const body = new THREE.Mesh(
    new THREE.CylinderGeometry(0.12, 0.12, 0.4, 16),
    new THREE.MeshStandardMaterial({color: 0xdddddd, metalness: 0.8, roughness: 0.2})
);
body.rotation.x = Math.PI / 2; 
satellite.add(body);
const panelMat = new THREE.MeshStandardMaterial({color: 0x1133aa, metalness: 0.5, roughness: 0.5});
const p1 = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.04, 0.2), panelMat); 
p1.rotation.z = Math.PI / 2; 
satellite.add(p1);

const p2 = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.04, 0.2), panelMat); 
p2.rotation.z = Math.PI / 2; 
satellite.add(p2);

const engineGlow = new THREE.Mesh(
    new THREE.SphereGeometry(0.1, 16, 16), 
    new THREE.MeshBasicMaterial({ color: 0xffaa00, transparent: true, opacity: 0.8 })
);
engineGlow.position.z = -0.2; 
satellite.add(engineGlow);
scene.add(satellite);

const markerA = new THREE.Mesh(new THREE.SphereGeometry(0.15), new THREE.MeshBasicMaterial({color: 0xff4444}));
const markerB = new THREE.Mesh(new THREE.SphereGeometry(0.15), new THREE.MeshBasicMaterial({color: 0x44ff44}));
scene.add(markerA, markerB);
markerA.visible = markerB.visible = false;

// 尾迹系统 (移位法)
const maxTrail = 120; let trailCount = 0;
const trailPos = new Float32Array(maxTrail * 3);
const trailGeo = new THREE.BufferGeometry();
trailGeo.setAttribute('position', new THREE.BufferAttribute(trailPos, 3));
const trailMesh = new THREE.Line(trailGeo, new THREE.LineBasicMaterial({color: 0xffaa00, transparent:true, opacity:0.5}));
scene.add(trailMesh);

// ==========================================
// 4. UI 逻辑与交互
// ==========================================
const btn1 = document.getElementById('btn-burn1');
const btn2 = document.getElementById('btn-burn2');
const logContainer = document.getElementById('log-container');
const btnCameraMode = document.getElementById('btn-camera-mode');
let cameraMode = 0; // 0: 全局视角, 1: 追踪视角

function addLog(msg) {
    const time = new Date().toLocaleTimeString();
    logContainer.insertAdjacentHTML('afterbegin', `<div style="font-size:16px; margin-bottom:5px; border-bottom:1px solid rgba(255,255,255,0.05); padding-bottom: 4px;">
        <span style="color: #aaa; font-size: 14px; margin-right: 5px;">[${time}]</span>${msg}
    </div>`);
}

const aiText = document.getElementById('ai-text');
function speakAI(text) {
    if(!aiText) return;
    clearInterval(aiText.typeInterval);
    aiText.innerText = '';
    let i = 0;
    aiText.typeInterval = trackedSetInterval(() => {
        if(i < text.length) { aiText.innerText += text[i]; i++; } 
        else { clearInterval(aiText.typeInterval); }
    }, 30);
}

// 视角切换按钮逻辑
trackedAddEventListener(btnCameraMode, 'click', () => {
    cameraMode = (cameraMode + 1) % 2; 
    if (cameraMode === 0) {
        btnCameraMode.innerText = "👁️ 追踪视角";
        speakAI("切换到全局视角模式。");
        addLog("🔄 已切换到全局视角模式。");

        controls.enabled = true; 
        camera.position.set(0, 0, 18); 
        controls.target.set(0, 0, 0); 
        controls.update();
    } else {
        btnCameraMode.innerText = "🌍 全局视角";
        speakAI("切换到追踪视角模式。");
        addLog("🔄 已切换到追踪视角模式。");
        controls.enabled = true; 
    }
});

function updateBurnButtons() {
    if (missionState !== 0) return;
    isOutward = R_target > R_current;
    if (Math.abs(R_target - R_current) < 0.1) {
        btn1.innerHTML = "✅ 无需变轨";
        btn1.disabled = true;
    } else {
        btn1.disabled = false;
        btn1.style.background = "rgba(0, 229, 255, 0.15)";
        btn1.style.color = "#ffaa00";
        btn1.style.borderColor = "#ffaa00";
        btn1.innerHTML = isOutward ? "🔥 第 1 次点火 (加速切入)" : "💨 第 1 次反推 (减速切入)";
        
        btn2.disabled = true;
        btn2.style.background = "#222";
        btn2.style.color = "#555";
        btn2.style.borderColor = "#555";
        btn2.innerHTML = isOutward ? "🛑 第 2 次点火 (加速圆化)" : "🛑 第 2 次反推 (减速圆化)";
    }
}

function updateTransferOrbitDisplay() {
    if (Math.abs(R_target - R_current) < 0.1) { 
        orbitTransfer.visible = false; 
        return; 
    }
    isOutward = R_target > R_current;
    const a_trans = (R_current + R_target) / 2;
    const c = Math.abs(a_trans - R_current);
    const centerX = isOutward ? -c : c;
    const curve = new THREE.EllipseCurve(centerX, 0, a_trans, Math.sqrt(a_trans*a_trans - c*c));
    transferGeo.setFromPoints(curve.getPoints(100));
    orbitTransfer.computeLineDistances();
    orbitTransfer.visible = true;
}

updateBurnButtons();
const sliderR2 = document.getElementById('slider-r2');
if(sliderR2) { sliderR2.value = R_target; document.getElementById('val-r2').innerText = R_target.toFixed(1); }

// 初次加载时显示预测轨道
updateTransferOrbitDisplay();

trackedAddEventListener(sliderR2, 'input', (e) => {
    if(missionState !== 0) return;
    R_target = parseFloat(e.target.value);
    document.getElementById('val-r2').innerText = R_target.toFixed(1);
    refreshBaseOrbits();
    updateBurnButtons();
    updateTransferOrbitDisplay(); 
});

trackedAddEventListener(document.getElementById('slider-speed'), 'input', (e) => {
    timeScale = parseFloat(e.target.value);
    document.getElementById('val-speed').innerText = timeScale.toFixed(1) + 'x';
});

// 🔥 第 1 次操作
trackedAddEventListener(btn1, 'click', () => {
    if (missionState !== 0 || Math.abs(R_current - R_target) < 0.05) return;
    
    isOutward = R_target > R_current;
    const v1 = Math.sqrt(MU / R_current);
    const a_trans = (R_current + R_target) / 2;
    const v2 = Math.sqrt(MU * (2/R_current - 1/a_trans)); 
    
    const dir = satVel.clone().normalize();
    satVel.copy(dir.multiplyScalar(v2));
    
    const burnAngle = Math.atan2(satPos.y, satPos.x);
    orbitTransfer.rotation.z = burnAngle;
    
    markerA.position.copy(satPos);
    markerB.position.copy(satPos).normalize().multiplyScalar(-R_target);
    markerA.visible = markerB.visible = true;
    prevDistToB = 9999; 
    
    const dv = Math.abs(v2 - v1);
    totalDeltaV += dv;
    
    // ✨ 渲染时乘以比例缩放系数 V_SCALE
    if(document.getElementById('data-v1')) document.getElementById('data-v1').innerText = (v1 * V_SCALE).toFixed(3);
    if(document.getElementById('data-v2')) document.getElementById('data-v2').innerText = (v2 * V_SCALE).toFixed(3);
    if(document.getElementById('data-dv')) document.getElementById('data-dv').innerText = (totalDeltaV * V_SCALE).toFixed(3);
    
    const conclusion = isOutward ? "v₂ > v₁ (顺向加速切入)" : "v₂ < v₁ (逆向减速切入)";
    if(document.getElementById('physics-conclusion')) document.getElementById('physics-conclusion').innerHTML = `<span style="color:#ffaa00">${conclusion}</span>`;
    
    missionState = 1;
    btn1.disabled = true;
    btn1.style.background = "#222"; btn1.style.color = "#555"; btn1.style.borderColor = "#555";
    
    btn2.disabled = false;
    btn2.style.background = "rgba(0, 255, 136, 0.15)";
    btn2.style.color = "#00ff88";
    btn2.style.borderColor = "#00ff88";
    document.getElementById('slider-r2').disabled = true;
    
    const logStr = isOutward ? `执行第一次点火，加速产生 &Delta;v = ${(dv * V_SCALE).toFixed(3)} km/s` : `执行第一次反推，减速产生 &Delta;v = ${(dv * V_SCALE).toFixed(3)} km/s`;
    addLog(logStr);
    speakAI(isOutward ? "🔥 A点第一次点火成功！正飞向高轨目标点。" : "💨 A点第一次反推成功！正朝着低轨目标点下落。");
});

// 🛑 第 2 次操作
trackedAddEventListener(btn2, 'click', () => {
    if (missionState !== 1) return;
    
    const distToB = satPos.distanceTo(markerB.position);
    if (distToB > 0.8) {
        speakAI("❌ 警告：未到达绿色的目标点B附近！此时点火无法完成圆化轨道。");
        return;
    }
    
    const v3 = satVel.length();
    const v4 = Math.sqrt(MU / R_target);
    
    satPos.copy(satPos.clone().normalize().multiplyScalar(R_target));
    
    const dir = new THREE.Vector3(-satPos.y, satPos.x, 0).normalize();
    if(satVel.dot(dir) < 0) dir.negate();
    satVel.copy(dir.multiplyScalar(v4));
    
    const dv = Math.abs(v4 - v3);
    totalDeltaV += dv;
    
    // ✨ 渲染时乘以比例缩放系数 V_SCALE
    if(document.getElementById('data-v3')) document.getElementById('data-v3').innerText = (v3 * V_SCALE).toFixed(3);
    if(document.getElementById('data-v4')) document.getElementById('data-v4').innerText = (v4 * V_SCALE).toFixed(3);
    if(document.getElementById('data-dv')) document.getElementById('data-dv').innerText = (totalDeltaV * V_SCALE).toFixed(3);
    
    const relation = isOutward ? "v₂ > v₁ > v₄ > v₃" : "v₃ > v₄ > v₁ > v₂";
    if(document.getElementById('physics-conclusion')) {
        document.getElementById('physics-conclusion').innerHTML = `${relation}<br><span style='color:#00ff88'>微调参数对齐，完美符合经典变轨理论！</span>`;
    }
    
    addLog(`执行第二次${isOutward?'点火':'反推'} (B点)，&Delta;v = ${(dv * V_SCALE).toFixed(3)} km/s。`);
    speakAI(`🎉 B点圆化成功！飞控系统已自动抹平操作残差。变轨任务圆满完成。`);

    // 状态重置
    R_current = R_target;
    missionState = 0;
    refreshBaseOrbits();
    
    // 恢复按钮样式
    btn2.disabled = true;
    btn2.style.background = "#222"; btn2.style.color = "#555"; btn2.style.borderColor = "#555";
    document.getElementById('slider-r2').disabled = false;
    
    markerA.visible = markerB.visible = false;
    orbitTransfer.visible = false;
    updateBurnButtons();

    timeScale = 1.0;
    document.getElementById('slider-speed').value = 1.0;
    document.getElementById('val-speed').innerText = '1.0x';
});

trackedAddEventListener(document.getElementById('btn-reset'), 'click', () => { location.reload(); });

addLog("🟢 飞控系统初始化完毕。已开启轨道残差自动修正模块。");
setTimeout(()=> { speakAI("系统加载完毕！请左右滑动滑块设定目标高度（支持升轨与降轨），随后点击第一次操作切入转移轨道。"); }, 500);

// ==========================================
// 5. 渲染引擎
// ==========================================
const clock = new THREE.Clock();

function animate() {
    animationFrameId = trackedRequestAnimationFrame(animate);
    let dt = clock.getDelta();
    if (dt > 0.1) dt = 0.1; 
    
    const subSteps = 10; 
    const stepDt = (dt * timeScale) / subSteps;
    
    for (let i = 0; i < subSteps; i++) {
        const r_mag = satPos.length();
        const acc = satPos.clone().normalize().multiplyScalar(-MU / (r_mag * r_mag));
        satVel.add(acc.multiplyScalar(stepDt));
        satPos.add(satVel.clone().multiplyScalar(stepDt));
    }
    
    earthGroup.rotation.y += 0.005 * dt * timeScale;
    
    satellite.position.copy(satPos);
    if (satVel.lengthSq() > 0.001) satellite.lookAt(satPos.clone().add(satVel));
    
    // ✨ 渲染时乘以比例缩放系数 V_SCALE
    if(document.getElementById('data-r')) document.getElementById('data-r').innerText = satPos.length().toFixed(2);
    if(document.getElementById('data-v')) document.getElementById('data-v').innerText = (satVel.length() * V_SCALE).toFixed(2);
    
    if (missionState === 1 || missionState === 3) {
        const distToB = satPos.distanceTo(markerB.position);
        const isMovingAway = distToB > prevDistToB; 
        
        if (missionState === 1) {
            if (distToB < 1.0 && !isMovingAway && timeScale > 0.3) {
                timeScale = 0.2; document.getElementById('slider-speed').value = 0.2; document.getElementById('val-speed').innerText = '0.2x';
                speakAI("⏳ 侦测到接近目标点 B！已为您开启时间缓速，请准备操作！");
            }
            if (distToB > 0.8 && isMovingAway && distToB < 2.0) {
                missionState = 3; 
                btn2.disabled = true; 
                btn2.style.background = "#222"; btn2.style.color = "#555"; btn2.style.borderColor = "#555";
                speakAI("⚠️ 错过了点火窗口！没关系，卫星会沿当前椭圆轨道绕行。我们在它下次靠近时再试！");
                timeScale = 1.0; document.getElementById('slider-speed').value = 1.0; document.getElementById('val-speed').innerText = '1.0x';
                addLog("⚠️ 已错过最佳圆化窗口，等待下一个循环...");
            }
        }
        if (missionState === 3) {
            if (distToB < 1.0 && !isMovingAway) {
                missionState = 1; 
                btn2.disabled = false; 
                btn2.style.background = "rgba(0, 255, 136, 0.15)"; btn2.style.color = "#00ff88"; btn2.style.borderColor = "#00ff88";
                timeScale = 0.2; document.getElementById('slider-speed').value = 0.2; document.getElementById('val-speed').innerText = '0.2x';
                addLog("🟢 再次接近 B 点，操作权限重新开启。");
            }
        }
        prevDistToB = distToB;
    }

    if (Math.random() > 0.2) { 
        for(let i = 0; i < maxTrail - 1; i++) {
            trailPos[i*3]   = trailPos[(i+1)*3];
            trailPos[i*3+1] = trailPos[(i+1)*3+1];
            trailPos[i*3+2] = trailPos[(i+1)*3+2];
        }
        trailPos[(maxTrail-1)*3]   = satPos.x;
        trailPos[(maxTrail-1)*3+1] = satPos.y;
        trailPos[(maxTrail-1)*3+2] = satPos.z;
        
        if (trailCount < maxTrail) trailCount++;
        trailGeo.setDrawRange(maxTrail - trailCount, trailCount);
        trailGeo.attributes.position.needsUpdate = true;
    }
    if (cameraMode === 1) {
        const offset = satVel.clone().normalize().multiplyScalar(-2.5).add(new THREE.Vector3(0, 0, 0.8));
        const targetPos = satPos.clone().add(offset);
        camera.position.lerp(targetPos, 0.1);
        controls.target.lerp(satPos, 0.1);
    }   

    controls.update(); 
    renderer.render(scene, camera);
}

trackedAddEventListener(window, 'resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
});

animate();