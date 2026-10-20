import * as THREE from 'three';
import { ParticleRenderer } from './ParticleRenderer.js';
import { PostProcessor } from './PostProcessor.js';
import { computeGamma, computeDopplerFactor } from './relativity.js';

// === 统一资源释放模板 - 光速视角实验 ===
let isDisposed = false;
let animationFrameId = null;

// A. 追踪函数
const activeEventListeners = [];

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

// B. 核心清理函数
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
    if (animationFrameId !== null) {
        cancelAnimationFrame(animationFrameId);
        animationFrameId = null;
    }
    removeTrackedEventListeners();
}

// C. UI/DOM 清理
function disposeUI() {
    const uiContainers = ['log-container', 'ai-box', 'ai-text', 'result-list'];
    for (const id of uiContainers) {
        const el = document.getElementById(id);
        if (el) el.innerHTML = '';
    }
    document.querySelectorAll('.label-container, .lil-gui').forEach(el => el.remove());
}

// D. 子组件递归清理
function disposeChildComponents() {
    if (starField?.dispose) { starField.dispose(); }
    if (postProcessor?.dispose) { postProcessor.dispose(); }
}

// E. 主清理函数
function dispose() {
    if (isDisposed) return;
    isDisposed = true;
    stopAnimationLoops();
    disposeUI();
    disposeChildComponents();
    disposeSceneGraph(scene);
    // composer?.dispose(); // 已移除，因为composer未定义

    // 渲染器彻底销毁
    if (renderer) {
        renderer.dispose();
        renderer.renderLists?.dispose();
        if (renderer.forceContextLoss) renderer.forceContextLoss();
        if (renderer.domElement?.parentNode) {
            renderer.domElement.parentNode.removeChild(renderer.domElement);
        }
    }

    // 注意：const 变量不能被重新赋值，所以移除以下赋值操作
    // scene = null;
    // camera = null;
    // renderer = null;
    // starField = null;
    // postProcessor = null;
}

// F. 注册页面卸载事件
window.addEventListener('beforeunload', dispose);
window.addEventListener('pagehide', dispose);

// 暴露给前端调用的统一接口
window.disposeExperiment = dispose;

// --- 场景、相机、渲染器 ---
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x010118);
scene.fog = new THREE.FogExp2(0x010118, 0.0002);

const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 2500);
camera.position.set(0, 0, 0);
camera.lookAt(0, 0, 1);

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setPixelRatio(window.devicePixelRatio);
renderer.toneMapping = THREE.ReinhardToneMapping;
// 将 canvas 插入到 body 的最前面，确保不被 UI 遮挡
const canvas = renderer.domElement;
canvas.id = 'c';
document.body.insertBefore(canvas, document.body.firstChild);

// --- 视角控制（保持不变）---
const mouseState = {
    isDown: false,
    lastX: 0,
    lastY: 0,
    yaw: -Math.PI / 2,
    pitch: 0
};
const pitchLimit = Math.PI / 2 - 0.01;
function updateCameraOrientation() {
    const quaternion = new THREE.Quaternion();
    const euler = new THREE.Euler(mouseState.pitch, mouseState.yaw, 0, 'YXZ');
    quaternion.setFromEuler(euler);
    camera.quaternion.copy(quaternion);
}
function onMousemove(e) {
    if (!mouseState.isDown) return;
    const dx = e.clientX - mouseState.lastX;
    const dy = e.clientY - mouseState.lastY;
    const sensitivity = 0.005;
    mouseState.yaw -= dx * sensitivity;
    mouseState.pitch -= dy * sensitivity;
    mouseState.pitch = Math.max(-pitchLimit, Math.min(pitchLimit, mouseState.pitch));
    updateCameraOrientation();
    mouseState.lastX = e.clientX;
    mouseState.lastY = e.clientY;
}

function onMouseup(e) {
    if (e.button === 0) {
        mouseState.isDown = false;
        renderer.domElement.style.cursor = 'default';
    }
}

function onTouchstart(e) {
    e.preventDefault();
    if (e.touches.length === 1) {
        mouseState.isDown = true;
        mouseState.lastX = e.touches[0].clientX;
        mouseState.lastY = e.touches[0].clientY;
    }
}

function onTouchmove(e) {
    if (!mouseState.isDown) return;
    e.preventDefault();
    const dx = e.touches[0].clientX - mouseState.lastX;
    const dy = e.touches[0].clientY - mouseState.lastY;
    const sensitivity = 0.005;
    mouseState.yaw -= dx * sensitivity;
    mouseState.pitch -= dy * sensitivity;
    mouseState.pitch = Math.max(-pitchLimit, Math.min(pitchLimit, mouseState.pitch));
    updateCameraOrientation();
    mouseState.lastX = e.touches[0].clientX;
    mouseState.lastY = e.touches[0].clientY;
}

function onTouchend() {
    mouseState.isDown = false;
}

function onMousedown(e) {
    if (e.button === 0) {
        mouseState.isDown = true;
        mouseState.lastX = e.clientX;
        mouseState.lastY = e.clientY;
        renderer.domElement.style.cursor = 'grabbing';
    }
}
trackedAddEventListener(renderer.domElement, 'mousedown', onMousedown);
trackedAddEventListener(window, 'mousemove', onMousemove);
trackedAddEventListener(window, 'mouseup', onMouseup);
trackedAddEventListener(renderer.domElement, 'touchstart', onTouchstart);
trackedAddEventListener(window, 'touchmove', onTouchmove);
trackedAddEventListener(window, 'touchend', onTouchend);

// --- 星空粒子系统 ---
const starField = new ParticleRenderer(scene, 20000);
starField.create();

// --- 后处理特效 ---
const postProcessor = new PostProcessor(renderer, scene, camera);

// --- UI 元素 ---
const speedSlider = document.getElementById('speedSlider');
const speedValueSpan = document.getElementById('speedValue');
const speedDisplaySpan = document.getElementById('speedDisplay');
const dopplerProgress = document.getElementById('dopplerProgress');
const dopplerValueSpan = document.getElementById('dopplerValue');
const aberrationAngleSpan = document.getElementById('aberrationAngle');
const gammaSpan = document.getElementById('gamma');
const aiTextSpan = document.getElementById('ai-text');

// AI 实验助手文本
const AI_TEXT_LOW = '欢迎来到相对论视觉模拟器！调节左侧速度滑块，观察高速运动下的光行差与多普勒效应。鼠标可自由旋转视角。';
const AI_TEXT_HIGH = '在速度不变的情况下，即使是接近光速飞行，也不会有电影中那般的空间隧道感。当速度极高时，临界角 θ<sub>c</sub> = arccos(β) 会变得很小，意味着只有静止系中非常靠近正前方的星光才能进入你视野的前半球，其余全部汇聚到身后。同时由于多普勒效应，前方的星星变蓝变亮，后方的星星变红变暗。事实上，随着光速的增加，蓝移和红移将导致光线最终脱离可见光波段，但为了更好地呈现多普勒效应，本实验保留了可见光的视觉效果。';

// --- 自动加速控制 ---
let autoAccelerateId = null;
let isAutoAccelerating = false;
let isAutoUpdating = false;
let currentBeta = 0.0;

function updateUI(beta) {
    const gamma = computeGamma(beta);
    gammaSpan.textContent = gamma.toFixed(3);
    speedValueSpan.textContent = beta.toFixed(4);
    speedDisplaySpan.textContent = `${beta.toFixed(4)}c`;
    const frontDoppler = computeDopplerFactor(beta, 1);
    let intensity = (frontDoppler - 1) / frontDoppler;
    intensity = Math.min(0.99, intensity);
    dopplerProgress.value = intensity;
    dopplerValueSpan.textContent = `${Math.round(intensity * 100)}%`;
    // 光行差半锥角：静止系中能进入运动观测者前半球的最大角度
    const halfConeRad = Math.acos(beta);
    const halfConeDeg = halfConeRad * 180 / Math.PI;
    aberrationAngleSpan.textContent = `${halfConeDeg.toFixed(1)}°`;
}

// 速度变化时的核心逻辑
function onSpeedChange(newBeta) {
    if (Math.abs(newBeta - currentBeta) < 1e-6) return; // 无变化则跳过
    currentBeta = newBeta;
    starField.updateBeta(currentBeta);
    postProcessor.updateBeta(currentBeta);
    updateUI(currentBeta);
    // 根据速度更新AI实验助手文本
    if (aiTextSpan) {
        aiTextSpan.innerHTML = currentBeta >= 0.5 ? AI_TEXT_HIGH : AI_TEXT_LOW;
    }
    // 速度变化 → 触发拖尾效果（持续 400ms）
    postProcessor.triggerTrail(400);
}

function onSpeedInput(e) {
    if (isAutoAccelerating && !isAutoUpdating) {
        if (autoAccelerateId) cancelAnimationFrame(autoAccelerateId);
        isAutoAccelerating = false;
        autoAccelerateId = null;
    }
    const newBeta = parseFloat(e.target.value);
    onSpeedChange(newBeta);
}

trackedAddEventListener(speedSlider, 'input', onSpeedInput);

updateUI(0);

// 缓变加速（8秒，匀速到0.95c）
function startAutoAccelerate() {
    startAccelerate(8500);
}

// 急变加速（2秒，匀速到0.95c）
function startRapidAccelerate() {
    startAccelerate(2000);
}

// 统一加速函数：线性加速到0.95c
function startAccelerate(duration) {
    if (autoAccelerateId) cancelAnimationFrame(autoAccelerateId);
    const startBeta = currentBeta;
    const targetBeta = 0.95;
    if (startBeta >= targetBeta) return;
    const startTime = performance.now();
    isAutoAccelerating = true;
    function step(now) {
        const elapsed = now - startTime;
        const t = Math.min(1, elapsed / duration);
        const newBeta = startBeta + (targetBeta - startBeta) * t;
        isAutoUpdating = true;
        speedSlider.value = newBeta;
        onSpeedChange(newBeta);
        isAutoUpdating = false;
        if (t < 1) {
            autoAccelerateId = requestAnimationFrame(step);
        } else {
            isAutoAccelerating = false;
            autoAccelerateId = null;
        }
    }
    autoAccelerateId = requestAnimationFrame(step);
}

const autoBtn = document.getElementById('autoAccelerateBtn');
if (autoBtn) {
    trackedAddEventListener(autoBtn, 'click', startAutoAccelerate);
}

const rapidBtn = document.getElementById('rapidAccelerateBtn');
if (rapidBtn) {
    trackedAddEventListener(rapidBtn, 'click', startRapidAccelerate);
}

// 动画循环
function animate() {
    postProcessor.render();
    animationFrameId = requestAnimationFrame(animate);
}
animationFrameId = requestAnimationFrame(animate);

// 窗口适配
function onResize() {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
    postProcessor.setSize(window.innerWidth, window.innerHeight);
}
trackedAddEventListener(window, 'resize', onResize);