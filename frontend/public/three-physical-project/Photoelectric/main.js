// ==========================================
// 量子力学3D模拟 - 光电效应 (完整重构版)
// ==========================================

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

// ==========================================
// 7. 统一资源释放与内存清理 (根据 v2 模板)
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

function trackedSetInterval(fn, delay) {
    const id = window.setInterval(() => {
        if (isDisposed) return;
        fn();
    }, delay);
    activeIntervalIds.add(id);
    return id;
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
    const textureKeys = ['map', 'lightMap', 'bumpMap', 'normalMap', 'specularMap', 'envMap', 'alphaMap', 'gradientMap', 'emissiveMap', 'metalnessMap', 'roughnessMap', 'aoMap', 'displacementMap', 'matcap'];
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

// 新增模板中的场景图遍历清理函数
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
    if (emitTimer) { clearInterval(emitTimer); emitTimer = null; } // 额外清理光电效应特定的定时器
    removeTrackedEventListeners();
}

// 补齐 UI/DOM 残留清理
function disposeUI() {
    const uiContainers = ['log-container', 'ai-text', 'val-freq', 'val-intensity', 'val-voltage', 'stop-voltage', 'max-current'];
    for (const id of uiContainers) {
        const el = document.getElementById(id);
        if (el) el.innerHTML = '';
    }
    if (chartCanvas && ctx) {
        ctx.clearRect(0, 0, chartCanvas.width, chartCanvas.height);
    }
}

function dispose() {
    if (isDisposed) return;
    isDisposed = true;

    stopAnimationLoops();
    disposeUI();

    // 清理 GSAP 动画 (电子飞行等)
    if (typeof gsap !== 'undefined' && gsap.killTweensOf) { gsap.killTweensOf('*'); }

    disposeSceneGraph(scene); // 统一调用封装函数
    controls?.dispose();

    if (renderer) {
        renderer.dispose();
        renderer.renderLists?.dispose();
        if (renderer.forceContextLoss) renderer.forceContextLoss();
        if (renderer.domElement?.parentNode) {
            renderer.domElement.parentNode.removeChild(renderer.domElement);
        }
    }

    // 释放全局引用
    state = null; emitTimer = null;
}

// 补齐规划中缺失的 beforeunload 事件注册
window.addEventListener('beforeunload', dispose);
window.addEventListener('pagehide', dispose);
window.disposeExperiment = dispose;

// ==========================================
// 1. Three.js 基础环境与 3D 场景搭建
// ==========================================
const canvas = document.querySelector('#c');
const renderer = new THREE.WebGLRenderer({ antialias: true, canvas });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.localClippingEnabled = true;

const scene = new THREE.Scene();
// 优化背景：深蓝黑底色 + 全局雾效，增加空间深度和神秘感
scene.background = new THREE.Color(0x060d14);
scene.fog = new THREE.Fog(0x060d14, 60, 400);

const camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
camera.position.set(0, 5, 50);

const controls = new OrbitControls(camera, renderer.domElement);
controls.target.set(0, 0, 0);

const ambientLight = new THREE.AmbientLight(0xffffff, 1.5);
scene.add(ambientLight);
const dirLight = new THREE.DirectionalLight(0xffffff, 2.5);
dirLight.position.set(10, 30, 30);
scene.add(dirLight);

// ================== 背景修饰：科技感网格与环境悬浮粒子 ==================
const gridHelper = new THREE.GridHelper(150, 60, 0x0088ff, 0x002244);
gridHelper.position.y = -22; // 放在仪器下方
gridHelper.material.transparent = true;
gridHelper.material.opacity = 0.5;
scene.add(gridHelper);

const dustGeo = new THREE.BufferGeometry();
const dustCount = 400;
const dustPos = new Float32Array(dustCount * 3);
for (let i = 0; i < dustCount * 3; i++) {
    dustPos[i] = (Math.random() - 0.5) * 100;
}
dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3));
const dustMat = new THREE.PointsMaterial({ color: 0x00e5ff, size: 0.15, transparent: true, opacity: 0.4, blending: THREE.AdditiveBlending });
const dustParticles = new THREE.Points(dustGeo, dustMat);
scene.add(dustParticles);

// ================== 构建实验核心仪器 ==================
const plateGeo = new THREE.BoxGeometry(0.8, 12, 12);

const cathodeMat = new THREE.MeshStandardMaterial({ color: 0xeeeeee, metalness: 0.5, roughness: 0.3 });
const cathode = new THREE.Mesh(plateGeo, cathodeMat);
window.cathodeMat = cathodeMat;
cathode.position.set(-15, 0, 0);
scene.add(cathode);

const anodeMat = new THREE.MeshStandardMaterial({ color: 0xffcc77, metalness: 0.4, roughness: 0.3, transparent: true, opacity: 0.85 });
const anode = new THREE.Mesh(plateGeo, anodeMat);
anode.position.set(15, 0, 0);
scene.add(anode);

const tubeGeo = new THREE.CylinderGeometry(8, 8, 34, 32, 1, true);
const edges = new THREE.EdgesGeometry(tubeGeo);
const lineMat = new THREE.LineBasicMaterial({ color: 0x00e5ff, transparent: true, opacity: 0.25, blending: THREE.AdditiveBlending });
const glassTubeOutline = new THREE.LineSegments(edges, lineMat);
glassTubeOutline.rotation.z = Math.PI / 2;
//scene.add(glassTubeOutline);

// ================== 构建光源与锥形光束 ==================
const lampGroup = new THREE.Group();

// 【修改核心】：将光源移到金属板的前面（阴极板右侧同一侧）
// 从原来的 x = -25 移到 x = 0 的位置，这样光线就会打在释放电子的那一面！
lampGroup.position.set(0, 15, 15);
lampGroup.lookAt(cathode.position);
scene.add(lampGroup);

// 1. 发光圆球 (灯泡)
const bulbGeo = new THREE.SphereGeometry(1.5, 16, 16);
const bulbMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
const lampBulb = new THREE.Mesh(bulbGeo, bulbMat);
lampGroup.add(lampBulb);
window.lampBulb = lampBulb;

// 2. 圆台遮罩
const shadeGeo = new THREE.CylinderGeometry(1.5, 4.5, 6, 32, 1, true);
const shadeMat = new THREE.MeshStandardMaterial({ color: 0x333333, metalness: 0.2, roughness: 0.8, side: THREE.DoubleSide });
const lampShade = new THREE.Mesh(shadeGeo, shadeMat);
lampShade.rotation.x = -Math.PI / 2;
lampShade.position.z = 1.5;
lampGroup.add(lampShade);

// 3. 锥形光束效 (双层体积光 + 完美防穿模)
const distToCathode = lampGroup.position.distanceTo(cathode.position);

const beamLength = distToCathode + 15;
// 【神奇的裁剪刀完美生效】：
// 阴极板右表面在 x = -14.6。现在光从 x = 0 照向 x = -15。
// 这个裁剪刀会保留 x > -14.6 的光，将越过金属板表面、试图穿透到背面的光直接切除！
const clipPlane = new THREE.Plane(new THREE.Vector3(1, 0, 0), 14.6);

const baseMatConfig = {
    color: 0xffffff,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    side: THREE.DoubleSide,
    clippingPlanes: [clipPlane] // 应用裁剪平面      
};

// 外层泛光
const outerGeo = new THREE.CylinderGeometry(0.5, 6.0, beamLength, 32, 1, true);
const outerMat = new THREE.MeshBasicMaterial({ ...baseMatConfig, opacity: 0.08 });
const outerBeam = new THREE.Mesh(outerGeo, outerMat);

// 内层高光
const innerGeo = new THREE.CylinderGeometry(0.2, 3.5, beamLength, 32, 1, true);
const innerMat = new THREE.MeshBasicMaterial({ ...baseMatConfig, opacity: 0.15 });
const innerBeam = new THREE.Mesh(innerGeo, innerMat);

const lightBeamGroup = new THREE.Group();
lightBeamGroup.add(outerBeam);
lightBeamGroup.add(innerBeam);

lightBeamGroup.rotation.x = -Math.PI / 2;
lightBeamGroup.position.z = distToCathode / 2;
lampGroup.add(lightBeamGroup);

window.beamMaterials = [outerMat, innerMat];
// ================== 辅助函数：创建 3D 屏幕贴图 ==================
function createTextScreen(width, height, defaultText, textColor) {
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 256;
    const ctx = canvas.getContext('2d');
    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    const mat = new THREE.MeshBasicMaterial({ map: texture, transparent: true });
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), mat);

    mesh.updateData = function (text) {
        ctx.fillStyle = '#0a0a0a';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = textColor;
        ctx.font = 'bold 80px Courier New';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(text, canvas.width / 2, canvas.height / 2);
        texture.needsUpdate = true;
    };
    mesh.updateData(defaultText);
    return mesh;
}

// ================== 构建外部电路系统 ==================
const powerGroup = new THREE.Group();
const powerGeo = new THREE.BoxGeometry(10, 6, 8);
const powerMat = new THREE.MeshStandardMaterial({ color: 0x2a3b4c, metalness: 0.4, roughness: 0.5 });
const powerBox = new THREE.Mesh(powerGeo, powerMat);
powerGroup.add(powerBox);

const powerScreen = createTextScreen(8, 3.5, "U: -0.00 V", "#ff4444");
powerScreen.position.set(0, 0, 4.01);
powerGroup.add(powerScreen);
powerGroup.position.set(-6, -20, 0);
scene.add(powerGroup);
window.powerScreen = powerScreen;

const ammeterGroup = new THREE.Group();
const ammeterGeo = new THREE.BoxGeometry(9, 7, 7);
const ammeterMat = new THREE.MeshStandardMaterial({ color: 0x3b4c3a, metalness: 0.4, roughness: 0.5 });
const ammeterBox = new THREE.Mesh(ammeterGeo, ammeterMat);
ammeterGroup.add(ammeterBox);

const ammeterScreen = createTextScreen(7, 3.5, "I: 0.00 μA", "#00ff88");
ammeterScreen.position.set(0, 1, 3.51);
ammeterGroup.add(ammeterScreen);

const terminalGeo = new THREE.CylinderGeometry(0.5, 0.5, 1, 16);
const termMat1 = new THREE.MeshStandardMaterial({ color: 0xcc0000 });
const termMat2 = new THREE.MeshStandardMaterial({ color: 0x222222 });
const term1 = new THREE.Mesh(terminalGeo, termMat1);
const term2 = new THREE.Mesh(terminalGeo, termMat2);
term1.rotation.x = term2.rotation.x = Math.PI / 2;
term1.position.set(-2, -2, 3.5);
term2.position.set(2, -2, 3.5);
ammeterGroup.add(term1, term2);

ammeterGroup.position.set(8, -20, 0);
scene.add(ammeterGroup);
window.ammeterScreen = ammeterScreen;

class CustomSinCurve extends THREE.Curve {
    constructor(points) { super(); this.points = points; }
    getPoint(t, optionalTarget = new THREE.Vector3()) {
        const index = (this.points.length - 1) * t;
        const i = Math.floor(index);
        const f = index - i;
        if (i >= this.points.length - 1) return optionalTarget.copy(this.points[this.points.length - 1]);
        return optionalTarget.copy(this.points[i]).lerp(this.points[i + 1], f);
    }
}
const leftWirePath = new CustomSinCurve([
    new THREE.Vector3(-15, -6, 0), new THREE.Vector3(-15, -20, 0), new THREE.Vector3(-11, -20, 0)
]);
const rightWirePath = new CustomSinCurve([
    new THREE.Vector3(-1, -20, 0), new THREE.Vector3(3.5, -20, 0),
    new THREE.Vector3(12.5, -20, 0), new THREE.Vector3(15, -20, 0), new THREE.Vector3(15, -6, 0)
]);
const wireMat = new THREE.MeshStandardMaterial({ color: 0xaaaaaa, metalness: 0.8, roughness: 0.3 });
const leftWire = new THREE.Mesh(new THREE.TubeGeometry(leftWirePath, 20, 0.3, 8, false), wireMat);
const rightWire = new THREE.Mesh(new THREE.TubeGeometry(rightWirePath, 20, 0.3, 8, false), wireMat);
scene.add(leftWire, rightWire);

// ==========================================
// 2. 获取 HTML DOM 元素
// ==========================================
const domFreqSlider = document.getElementById('slider-freq');
const domIntensitySlider = document.getElementById('slider-intensity');
const domVoltageSlider = document.getElementById('slider-voltage'); // 新增
const domValFreq = document.getElementById('val-freq');
const domValIntensity = document.getElementById('val-intensity');
const domValVoltage = document.getElementById('val-voltage');       // 新增
const domStopVoltage = document.getElementById('stop-voltage');
const domMaxCurrent = document.getElementById('max-current');
const aiText = document.getElementById('ai-text');
const chartCanvas = document.getElementById('iv-chart');
const ctx = chartCanvas ? chartCanvas.getContext('2d') : null;

// ==========================================
// 3. 物理常量与系统实时状态
// ==========================================
const PHYSICS = {
    h_eV: 4.135667696e-15,
    W_metal: 2.28,
    nu_multiplier: 1e14
};

// 在 PHYSICS 常数定义的下方添加
const METALS = {
    'cesium': { name: '铯 (Cs)', W: 1.93, color: 0xffd700 }, // 偏金黄色
    'sodium': { name: '钠 (Na)', W: 2.28, color: 0xdddddd }, // 银白色
    'zinc': { name: '锌 (Zn)', W: 4.33, color: 0x99aab5 }, // 蓝灰色
    'platinum': { name: '铂 (Pt)', W: 6.35, color: 0x888888 }  // 暗银灰色
};

let state = {
    metalId: 'sodium',      // 新增：当前选中的金属 ID
    freqSliderVal: 5.0,
    intensity: 100,
    isEmitting: false,
    maxEk: 0,
    stopVoltage: 0,
    saturationCurrent: 0,
    appliedVoltage: 0,      // 新增：当前施加的外加电压 (V)
    realCurrent: 0          // 新增：当前回路中真实的电流表读数 (μA)
};
let emitTimer = null;


// ==========================================
// 4. 核心功能函数集合
// ==========================================
// 新增 trackedClearInterval 用于手动清理特定追踪定时器
function trackedClearInterval(id) {
    if (!id) return;
    clearInterval(id);
    activeIntervalIds.delete(id);
}

// 电子发射器：移除了光子的单独发射，只管打出电子
function startEmitter() {
    if (emitTimer) trackedClearInterval(emitTimer);
    if (state.intensity <= 0) return;

    const interval = 350 - (state.intensity / 500) * 310;
    // 使用模板的 trackedSetInterval
    emitTimer = trackedSetInterval(() => {
        if (state.isEmitting) {
            const targetY = (Math.random() - 0.5) * 10;
            const targetZ = (Math.random() - 0.5) * 10;
            const startPos = new THREE.Vector3(-15, targetY, targetZ);
            shootElectron(startPos);
        }
    }, interval);
}

function updatePhysics() {
    if (!domFreqSlider || !domIntensitySlider) return;

    state.freqSliderVal = parseFloat(domFreqSlider.value);
    state.intensity = parseFloat(domIntensitySlider.value);

    // 获取外加电压
    if (domVoltageSlider) state.appliedVoltage = parseFloat(domVoltageSlider.value);

    // 1. 计算真实频率与光子能量
    const nu = state.freqSliderVal * PHYSICS.nu_multiplier;
    const photonEnergy = PHYSICS.h_eV * nu;
    // 2. 爱因斯坦光电方程计算动能
    const currentMetal = METALS[state.metalId];
    state.maxEk = photonEnergy - currentMetal.W;

    if (state.maxEk > 0) {
        state.isEmitting = true;
        state.stopVoltage = state.maxEk;
        state.saturationCurrent = state.intensity * 0.15;
        // 👇 新增：计算真实的回路电流 (模拟原理图中的电流表读数)
        // 如果施加的反向电压比截止电压还大（更负），则电流完全被截断为 0
        if (state.appliedVoltage <= -state.stopVoltage) {
            state.realCurrent = 0;
        } else {
            // 否则，根据外加电压在 I-V 曲线上计算实际电流
            const k = 1.5;
            state.realCurrent = state.saturationCurrent * (1 - Math.exp(-k * (state.appliedVoltage + state.stopVoltage)));
        }
    } else {
        state.isEmitting = false;
        state.maxEk = 0;
        state.stopVoltage = 0;
        state.saturationCurrent = 0;
    }

    if (domValFreq) {
        // 使用 HTML 标签渲染标准的科学计数法 (10的14次方)，并在旁边用高亮颜色显示光子能量
        domValFreq.innerHTML = `
            <strong>${state.freqSliderVal.toFixed(1)} &times; 10<sup>14</sup></strong> Hz 
            <span style="color: #ffaa00; font-size: 0.9em; margin-left: 8px;">
                (能量: ${photonEnergy.toFixed(2)} eV)
            </span>`;
    }
    if (domValIntensity) domValIntensity.innerText = state.intensity;
    if (domValVoltage) {
        const sign = state.appliedVoltage > 0 ? '+' : '';
        domValVoltage.innerText = sign + state.appliedVoltage.toFixed(2) + ' V';
    }

    // 动态更新右侧的数据面板，加入贴心的解释文字
    if (domStopVoltage) {
        domStopVoltage.innerHTML = `反向截止电压: <strong style="color: var(--theme-accent);">-${state.stopVoltage.toFixed(2)} V</strong> <span style="font-size: 0.8em; color: #888;">(刚好能挡住最快的电子)</span>`;
    }
    if (domMaxCurrent) {
        domMaxCurrent.innerHTML = `回路实时电流: <strong style="color: var(--theme-accent);">${state.realCurrent.toFixed(2)} μA</strong>`;
    }

    drawIVCurve();

    if (window.powerScreen) {
        window.powerScreen.updateData('U: ' + state.appliedVoltage.toFixed(2) + ' V');
    }
    if (window.ammeterScreen) {
        // 【修复不同步】：这里把 saturationCurrent 改成 realCurrent
        window.ammeterScreen.updateData('I: ' + state.realCurrent.toFixed(2) + ' μA');
    }

    // 更新灯泡与光束颜色，以及光束的透明度(受光强影响)
    if (window.beamMaterials && typeof freqToColor === 'function') {
        // 获取当前频率对应的颜色
        const targetColor = freqToColor(state.freqSliderVal);
        // 遍历更新内外两层光束的颜色
        window.beamMaterials.forEach(mat => mat.color.set(targetColor));
    }
}

// --- 图表引擎：绘制 I-V 动态曲线及实时工作点 ---
function drawIVCurve() {
    if (!ctx || !chartCanvas) return;
    chartCanvas.width = 400; chartCanvas.height = 250;
    const width = chartCanvas.width, height = chartCanvas.height;
    ctx.clearRect(0, 0, width, height);

    // 坐标轴范围
    const xMin = -4, xMax = 4, yMin = 0, yMax = 100;
    const mapX = (v) => ((v - xMin) / (xMax - xMin)) * width;
    const mapY = (i) => height - ((i - yMin) / (yMax - yMin)) * (height - 40) - 20;

    // 1. 画背景网格
    ctx.lineWidth = 1; ctx.strokeStyle = 'rgba(0, 229, 255, 0.15)'; ctx.beginPath();
    for (let v = xMin; v <= xMax; v++) { let x = mapX(v); ctx.moveTo(x, 0); ctx.lineTo(x, height); }
    for (let i = 0; i <= yMax; i += 20) { let y = mapY(i); ctx.moveTo(0, y); ctx.lineTo(width, y); }
    ctx.stroke();

    // 2. 画主坐标轴
    const y0 = mapY(0); const x0 = mapX(0);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.8)'; ctx.lineWidth = 2; ctx.beginPath();
    ctx.moveTo(0, y0); ctx.lineTo(width, y0);
    ctx.moveTo(x0, 0); ctx.lineTo(x0, height);
    ctx.stroke();

    // 坐标轴刻度文字
    ctx.fillStyle = '#aaa'; ctx.font = '12px Courier New';
    ctx.fillText('-4V', mapX(-4) + 5, y0 - 8); ctx.fillText('+4V', mapX(4) - 30, y0 - 8);
    ctx.fillText('0', x0 + 5, y0 + 15); ctx.fillText('I (μA)', x0 + 5, 15);

    // 【新增】：哪怕没有发生光电效应，也要画出当前施加的电压点 (电流为0)
    if (!state.isEmitting) {
        const curX = mapX(state.appliedVoltage);
        ctx.beginPath(); ctx.arc(curX, y0, 5, 0, Math.PI * 2);
        ctx.fillStyle = '#ff4444'; ctx.fill();
        ctx.fillStyle = '#fff';
        ctx.fillText(`(${state.appliedVoltage.toFixed(2)}V, 0.00μA)`, curX + 10, y0 - 10);
        return; // 未发射时，画完底点就结束
    }

    // 3. 绘制光电特征曲线 (品红色)
    ctx.beginPath(); ctx.strokeStyle = '#ff00ff'; ctx.lineWidth = 3;
    for (let px = 0; px <= width; px += 2) {
        let U = xMin + (px / width) * (xMax - xMin);
        let I = 0;
        if (U >= -state.stopVoltage) { I = state.saturationCurrent * (1 - Math.exp(-1.5 * (U + state.stopVoltage))); }
        let py = mapY(I);
        if (px === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
    }
    ctx.stroke();

    // 4. 标记理论关键点 (-Uc 和 Im)
    const xUc = mapX(-state.stopVoltage);
    ctx.beginPath(); ctx.arc(xUc, y0, 5, 0, Math.PI * 2);
    ctx.fillStyle = '#00e5ff'; ctx.fill();
    ctx.fillStyle = '#fff'; ctx.fillText(`-Uc (${-state.stopVoltage.toFixed(2)}V)`, xUc - 40, y0 + 20);

    const yIm = mapY(state.saturationCurrent);
    ctx.beginPath(); ctx.setLineDash([5, 5]);
    ctx.moveTo(x0, yIm); ctx.lineTo(width, yIm);
    ctx.strokeStyle = '#ffaa00'; ctx.stroke(); ctx.setLineDash([]);
    ctx.fillStyle = '#ffaa00'; ctx.fillText(`Im (${state.saturationCurrent.toFixed(1)}μA)`, x0 + 5, yIm - 5);

    // ==========================================
    // 5. 【全新升级】绘制动态实时工作点 (高亮发光)
    // ==========================================
    const currentX = mapX(state.appliedVoltage);
    const currentY = mapY(state.realCurrent);

    // a. 画十字瞄准虚线
    ctx.beginPath();
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = 'rgba(0, 255, 136, 0.5)';
    ctx.moveTo(currentX, y0); ctx.lineTo(currentX, currentY); // 连到 X 轴
    ctx.lineTo(x0, currentY); // 连到 Y 轴
    ctx.stroke();
    ctx.setLineDash([]);

    // b. 画发光外圈
    ctx.beginPath();
    ctx.arc(currentX, currentY, 8, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(0, 255, 136, 0.3)';
    ctx.fill();

    // c. 画实心高亮内点
    ctx.beginPath();
    ctx.arc(currentX, currentY, 4, 0, Math.PI * 2);
    ctx.fillStyle = '#00ff88'; // 亮绿色
    ctx.shadowBlur = 10;       // 开启 Canvas 辉光
    ctx.shadowColor = '#00ff88';
    ctx.fill();
    ctx.shadowBlur = 0;        // 关闭辉光以免影响其他绘制

    // d. 在点旁边绘制当前数值标签
    ctx.fillStyle = '#fff';
    ctx.font = 'bold 13px Courier New';
    // 动态调整文字位置，防止跑出画布边缘
    let textX = currentX + 12;
    if (state.appliedVoltage > 2) textX = currentX - 140;
    ctx.fillText(`(${state.appliedVoltage.toFixed(2)}V, ${state.realCurrent.toFixed(2)}μA)`, textX, currentY - 12);
}

function freqToColor(freqVal) {
    const lambda = 3000 / freqVal;
    let r = 0, g = 0, b = 0;
    if (lambda >= 380 && lambda < 440) { r = -(lambda - 440) / (440 - 380); b = 1; }
    else if (lambda >= 440 && lambda < 490) { g = (lambda - 440) / (490 - 440); b = 1; }
    else if (lambda >= 490 && lambda < 510) { g = 1; b = -(lambda - 510) / (510 - 490); }
    else if (lambda >= 510 && lambda < 580) { r = (lambda - 510) / (580 - 510); g = 1; }
    else if (lambda >= 580 && lambda < 645) { r = 1; g = -(lambda - 645) / (645 - 580); }
    else if (lambda >= 645 && lambda <= 780) { r = 1; }
    if (lambda < 380) { r = 0.6; g = 0; b = 1; }
    if (lambda > 780) { r = 0.5; g = 0; b = 0; }
    return new THREE.Color(r, g, b);
}


// 电子动画：受外加电压(电场)精准控制，包含加速、减速与半路折返特效
function shootElectron(startPos) {
    const electronGroup = new THREE.Group();

    // 1. 电子头部 (高亮小球)
    const eGeo = new THREE.SphereGeometry(0.12, 8, 8);
    const eMat = new THREE.MeshBasicMaterial({ color: 0x00ff88 });
    const electronHead = new THREE.Mesh(eGeo, eMat);

    // 2. 电子拖尾 (利用 ConeGeometry，尾部默认朝后即朝左)
    const tailGeo = new THREE.ConeGeometry(0.12, 1.5, 8);
    const tailMat = new THREE.MeshBasicMaterial({ color: 0x00ff88, transparent: true, opacity: 0.5, blending: THREE.AdditiveBlending });
    const electronTail = new THREE.Mesh(tailGeo, tailMat);
    electronTail.rotation.z = Math.PI / 2; // 尖端朝左(-X)，底座朝右连接头部
    electronTail.position.x = -0.75;

    electronGroup.add(electronHead);
    electronGroup.add(electronTail);

    electronGroup.position.set(startPos.x + 0.5, startPos.y, startPos.z);
    scene.add(electronGroup);

    // 基础飞行时间 (取决于初始动能)
    let baseDuration = 2.5 - (state.maxEk * 0.4);
    if (baseDuration < 0.3) baseDuration = 0.3;

    // 清理资源的闭包函数
    const disposeElectron = () => {
        scene.remove(electronGroup);
        eGeo.dispose(); eMat.dispose();
        tailGeo.dispose(); tailMat.dispose();
    };

    // ==========================================
    // 核心物理动画逻辑：根据外加电压改变飞行轨迹
    // ==========================================
    const U = state.appliedVoltage;
    const Uc = state.stopVoltage; // 注意：state.stopVoltage 我们存的是正数绝对值

    if (U <= -Uc) {
        // 【情况 1：完全截止，半路折返】
        // 计算折返点：电压越负，折返点离阴极越近
        let turnRatio = Uc / Math.abs(U); // 0 到 1 之间的比例
        let turnX = startPos.x + (15 - startPos.x) * turnRatio * 0.9;
        if (turnX > 14) turnX = 14; // 绝对不让它碰到阳极

        const tl = gsap.timeline({ onComplete: disposeElectron });

        // 飞出（电场阻碍，逐渐减速至0）
        tl.to(electronGroup.position, {
            x: turnX,
            duration: baseDuration,
            ease: "power2.out" // out表示先快后慢
        });

        // 瞬间翻转拖尾方向（因为电子要往回飞了！）
        tl.call(() => {
            electronTail.rotation.z = -Math.PI / 2; // 尖端朝右
            electronTail.position.x = 0.75;         // 移到头部右侧
            // 变成红色，警告它被弹回了
            eMat.color.setHex(0xff4444);
            tailMat.color.setHex(0xff4444);
        });

        // 弹回（电场加速，飞回阴极）
        tl.to(electronGroup.position, {
            x: startPos.x,
            duration: baseDuration * 0.8,
            ease: "power2.in" // in表示先慢后快
        });

    } else {
        // 【情况 2：能到达阳极】
        let easeType = "none";
        let actualDuration = baseDuration;

        if (U > 0) {
            // 正向偏压：电场加速
            easeType = "power1.in";
            actualDuration *= Math.max(0.3, 1 - (U / 4)); // 电压越大，飞得越快(时间越短)
        } else if (U < 0) {
            // 反向偏压：电场减速但未截止
            easeType = "power1.out";
            actualDuration *= (1 + Math.abs(U) / Uc); // 越接近截止电压，飞得越慢(时间越长)
        }

        gsap.to(electronGroup.position, {
            x: 15,
            duration: actualDuration,
            ease: easeType,
            onComplete: disposeElectron
        });
    }
}

// --- 日志系统 ---
const MAX_LOGS = 20; // 修改为最多保存 20 条
export function addLog(msg) {
    const container = document.getElementById('log-container');
    if (!container) return;

    // 如果这是第一条真实日志，先把系统默认的“等待输入”提示清空
    if (container.children.length === 1 && container.innerText.includes('等待实验数据输入')) {
        container.innerHTML = '';
    }

    const logTitleColor = getComputedStyle(document.documentElement)
        .getPropertyValue('--theme-log-title')
        .trim() || '#00e5ff';
    const logAccentColor = getComputedStyle(document.documentElement)
        .getPropertyValue('--theme-log-accent')
        .trim() || '#ffbf00';

    const styledMsg = msg
        .replace(/<strong>/g, `<strong style="color: ${logTitleColor};">`)
        .replace(/<span(?![^>]*style=)/g, `<span style="color: ${logAccentColor};"`)
        .replace(/<span\s+style="([^"]*)"/g, (m, styleText) => {
            const cleaned = styleText
                .replace(/color\s*:\s*[^;]+;?/gi, '')
                .trim();
            const nextStyle = `color: ${logAccentColor};${cleaned ? ` ${cleaned}` : ''}`;
            return `<span style="${nextStyle}"`;
        });

    const time = new Date().toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const div = document.createElement('div');
    div.className = 'log-item';
    // 调整一下排版，让时间和内容更好看
    div.innerHTML = `<span class="time" style="color: #888; font-size: 11px; margin-right: 8px;">[${time}]</span><span class="log-content">${styledMsg}</span>`;

    // 插入到最前面
    container.prepend(div);

    // 超过20条就删除最旧的(最底下)一条
    while (container.children.length > MAX_LOGS) {
        container.removeChild(container.lastChild);
    }
}

// ==========================================
// 5. 绑定用户 UI 交互事件
// ==========================================
const domSelectMetal = document.getElementById('select-metal');
if (domFreqSlider) {
    trackedAddEventListener(domFreqSlider, 'input', () => {
        updatePhysics();

        const aiText = document.getElementById('ai-text');
        if (!aiText) return;

        // 1. 判断当前是什么光 (根据频率大概估算，单位 e14 Hz)
        let f = state.freqSliderVal;
        let lightName = "未知光";
        if (f < 4.0) lightName = "红外线";
        else if (f < 4.8) lightName = "红光";
        else if (f < 5.1) lightName = "橙/黄光";
        else if (f < 6.1) lightName = "绿光";
        else if (f < 6.7) lightName = "蓝光";
        else if (f < 7.5) lightName = "紫光";
        else lightName = "紫外线";

        // 2. 更新 AI 提示文案
        const m = METALS[state.metalId];
        if (state.isEmitting) {
            aiText.innerHTML = `✅ <strong>发生光电效应！</strong><br>
            当前照射的是 <span style="color: var(--theme-secondary)">${lightName}</span> (频率: ${f.toFixed(2)}×10¹⁴ Hz)。<br>
            光子能量已突破金属 ${m.name} 的限制！光电子的最大初动能为 <strong>${state.maxEk.toFixed(2)} eV</strong>，对应截止电压为 <strong>-${state.stopVoltage.toFixed(2)} V</strong>。`;
        } else {
            aiText.innerHTML = `❌ <strong>未发生光电效应</strong><br>
            当前照射的是 <span style="color:#ffaa00">${lightName}</span> (频率: ${f.toFixed(2)}×10¹⁴ Hz)。<br>
            它的光子能量太弱，不足以克服金属 ${m.name} 的逸出功（${m.W} eV）。请尝试向右滑动，换用更高频率的光！`;
        }
    });

    // 1. 频率滑块日志 (加入能量与动能信息)
    trackedAddEventListener(domFreqSlider, 'change', () => {
        const f = state.freqSliderVal.toFixed(1);
        const E = (state.freqSliderVal * PHYSICS.nu_multiplier * PHYSICS.h_eV).toFixed(2);
        let msg = `调节 <strong>入射光特征</strong> 至 ${f}×10¹⁴ Hz (能量: ${E} eV)。`;

        if (state.isEmitting) {
            msg += `<br><span style="color:#00ff88;">➤ 发生光电效应！光电子最大初动能: ${state.maxEk.toFixed(2)} eV</span>`;
        } else {
            msg += `<br><span style="color:#ffaa00;">➤ 能量不足以克服逸出功，未发生光电效应。</span>`;
        }
        addLog(msg);
    });
}
if (domIntensitySlider) {
    trackedAddEventListener(domIntensitySlider, 'input', () => {
        updatePhysics();
        startEmitter();
    });

    trackedAddEventListener(domIntensitySlider, 'change', () => {
        let msg = `调节 <strong>光照强度</strong> 至 ${state.intensity} W/m²。`;
        if (state.isEmitting) {
            msg += `<br><span style="color:#00e5ff;">➤ 光子密度改变，饱和电流更新为: ${state.saturationCurrent.toFixed(2)} μA</span>`;
        } else {
            msg += `<br><span style="color:#888;">➤ (当前未发生光电效应，改变光强无效)</span>`;
        }
        addLog(msg);
    });
}
if (domVoltageSlider) {
    trackedAddEventListener(domVoltageSlider, 'input', () => {
        updatePhysics();
        // 让 AI 助教解释外加电压的作用
        if (aiText && state.isEmitting) {
            if (state.appliedVoltage < 0) {
                if (state.appliedVoltage <= -state.stopVoltage) {
                    aiText.innerHTML = `🛑 <strong>电流已截止！</strong><br>当前施加了 <strong>${state.appliedVoltage.toFixed(2)} V</strong> 的反向电压。<br>电场力做负功，即使是动能最大的光电子也无法到达阳极，电路中实际电流降为 0。`;
                } else {
                    aiText.innerHTML = `⚠️ <strong>反向减速中</strong><br>施加了反向电压。部分动能较小的电子被电场推回，只有动能较大的电子能到达阳极。电流减小为 <strong>${state.realCurrent.toFixed(2)} μA</strong>。`;
                }
            } else {
                aiText.innerHTML = `⚡ <strong>正向加速中</strong><br>当前施加了 <strong>${state.appliedVoltage.toFixed(2)} V</strong> 的正向电压。<br>电场帮助电子更快飞向阳极，电流逐渐趋近于饱和电流。`;
            }
        }
    });

    // 3. 电压滑块日志 (加入电场做功与实时电流信息)
    trackedAddEventListener(domVoltageSlider, 'change', () => {
        const sign = state.appliedVoltage > 0 ? '+' : '';
        let msg = `调节 <strong>外加偏置电压</strong> 至 ${sign}${state.appliedVoltage.toFixed(2)} V。`;

        if (state.isEmitting) {
            if (state.appliedVoltage <= -state.stopVoltage) {
                msg += `<br><span style="color:#ff4444;">🛑 电流截止！电子被电场完全反弹回阴极。</span>`;
            } else if (state.appliedVoltage < 0) {
                msg += `<br><span style="color:#ffaa00;">⚠️ 反向减速中。部分电子无法到达，电流降为 ${state.realCurrent.toFixed(2)} μA。</span>`;
            } else {
                msg += `<br><span style="color:#00ff88;">⚡ 正向加速中。电场做正功，回路电流为 ${state.realCurrent.toFixed(2)} μA。</span>`;
            }
        }
        addLog(msg);
    });

}
if (domSelectMetal) {
    trackedAddEventListener(domSelectMetal, 'change', (e) => {
        state.metalId = e.target.value;
        const metalData = METALS[state.metalId];

        // 1. 先计算 limitFreq
        const limitFreq = (metalData.W / PHYSICS.h_eV / PHYSICS.nu_multiplier).toFixed(2);

        // 2. 再输出日志 (这样 limitFreq 就有值了)
        addLog(`更换 <strong>阴极金属材料</strong> 为 ${metalData.name}。<br><span style="color:#ff00ff;">➤ 逸出功变为 ${metalData.W} eV，极限频率变为 ${limitFreq}×10¹⁴ Hz。</span>`);

        if (window.cathodeMat) {
            window.cathodeMat.color.setHex(metalData.color);
        }

        updatePhysics();
        if (typeof startEmitter === 'function') startEmitter();

        const aiText = document.getElementById('ai-text');
        if (aiText) {
            aiText.innerHTML = `🔧 <strong>更换阴极材料</strong><br>已将靶材更换为 <strong>${metalData.name}</strong>，其逸出功变为 <strong>${metalData.W} eV</strong>。<br>产生光电效应的极限频率约为 <strong>${limitFreq}×10¹⁴ Hz</strong>。请重新调节光频验证！`;
        }
    });
}

// ==========================================
// 6. 系统统一启动与渲染循环
// ==========================================
updatePhysics();
startEmitter();

function render() {
    if (isDisposed) return; // 增加防游离执行判断
    controls.update();
    renderer.render(scene, camera);
    trackedRequestAnimationFrame(render); // 替换原来的 requestAnimationFrame
}
trackedRequestAnimationFrame(render); // 替换原来的 render() 调用

trackedAddEventListener(window, 'resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
    drawIVCurve();
});