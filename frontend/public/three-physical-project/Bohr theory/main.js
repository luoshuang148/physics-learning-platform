// ==========================================
// 1. 模块导入与依赖
// ==========================================
import * as THREE from 'three';
import GUI from 'https://cdn.jsdelivr.net/npm/lil-gui@0.19/+esm';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

// 后处理特效模块
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { AfterimagePass } from 'three/addons/postprocessing/AfterimagePass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

// ==========================================
// 2. 全局配置参数 (CONFIG)
// ==========================================
const CONFIG = {
    colors: {
        background: 0x020205,
        grid: 0x00e5ff,
        nucleus: 0xff2200,
        orbit: 0x00e5ff,
        electronLevel1: 0x00e5ff,
        electronLevel2: 0x8a2be2,
        electronLevel3: 0xffffff,
        photonHigh: 0x8a2be2,
        photonMid: 0x00e5ff,
        photonLow: 0xff0000
    },
    physics: {
        baseSpeed: 0.02,
        orbitsInfo: [
            { n: 1, radius: 6 },
            { n: 2, radius: 10 },
            { n: 3, radius: 15 }
        ]
    },
    postprocessing: {
        bloomStrength: 0.5,
        bloomRadius: 0.2,
        bloomThreshold: 0.2,
        afterimageDamp: 0.65
    },
    animation: {
        transitionDuration: 1.2,
        photonFlyTime: 4.0,
    },
    scene: {
        autoRotate: false,
        electronScale: 1.0,
        showPhotonLabel: true
    }
};

// ==========================================
// 3. 统一资源释放模板 (核心管控区)
// ==========================================
let isDisposed = false;
let animationFrameId = null;
const activeTimeoutIds = new Set();
const activeIntervalIds = new Set();
const activeEventListeners = [];

// 全局核心对象引用，方便销毁时调用
let scene, camera, renderer, labelRenderer, composer, controls, gui;

// --- A. 追踪函数 ---
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

function trackedClearTimeout(id) {
    if (!id) return;
    clearTimeout(id);
    activeTimeoutIds.delete(id);
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

// --- B. 核心清理函数 ---
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
        for (let i = obj.children.length - 1; i >= 0; i--) {
            disposeObject3D(obj.children[i]);
        }
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

// --- C. 第三方库与 UI 清理 ---
function disposeThirdPartyLibraries() {
    if (typeof gsap !== 'undefined' && gsap.killTweensOf) { gsap.killTweensOf('*'); }
    if (typeof gui !== 'undefined' && gui?.destroy) { gui.destroy(); gui = null; }
}

function disposeUI() {
    const uiContainers = ['log-container', 'ai-text', 'data-n', 'data-e', 'data-lambda'];
    for (const id of uiContainers) {
        const el = document.getElementById(id);
        if (el) el.innerHTML = '';
    }
    if (labelRenderer?.domElement?.parentNode) {
        labelRenderer.domElement.parentNode.removeChild(labelRenderer.domElement);
    }
    // 兜底清理动态 DOM 残留
    document.querySelectorAll('.orbit-label, .photon-label, .lil-gui').forEach(el => el.remove());

    // 清理 1D 画布残留
    const energyCanvas = document.getElementById('energy-canvas');
    if (energyCanvas) {
        const eCtx = energyCanvas.getContext('2d');
        eCtx.clearRect(0, 0, energyCanvas.width, energyCanvas.height);
    }
}

// --- F. 主清理函数 (暴露给前端) ---
function dispose() {
    if (isDisposed) return;
    isDisposed = true;

    stopAnimationLoops();
    disposeThirdPartyLibraries();
    disposeUI();
    disposeSceneGraph(scene);

    composer?.dispose();
    controls?.dispose();

    if (renderer) {
        renderer.dispose();
        renderer.renderLists?.dispose();
        if (renderer.forceContextLoss) renderer.forceContextLoss();
    }

    // 断开全局引用
    scene = null; camera = null; renderer = null;
    labelRenderer = null; composer = null; controls = null; gui = null;
}

window.disposeExperiment = dispose;
window.addEventListener('beforeunload', dispose);
window.addEventListener('pagehide', dispose);


// ==========================================
// 4. 物理辅助函数
// ==========================================
function calculateEnergy(n) {
    if (n === Infinity) return 0;
    return -13.6 / (n * n);
}

function wavelengthToColor(wl) {
    let r = 0, g = 0, b = 0;
    if (wl >= 380 && wl < 440) { r = -(wl - 440) / (440 - 380); g = 0; b = 1; }
    else if (wl >= 440 && wl < 490) { r = 0; g = (wl - 440) / (490 - 440); b = 1; }
    else if (wl >= 490 && wl < 510) { r = 0; g = 1; b = -(wl - 510) / (510 - 490); }
    else if (wl >= 510 && wl < 580) { r = (wl - 510) / (580 - 510); g = 1; b = 0; }
    else if (wl >= 580 && wl < 645) { r = 1; g = -(wl - 645) / (645 - 580); b = 0; }
    else if (wl >= 645 && wl <= 780) { r = 1; g = 0; b = 0; }

    if (wl < 380) return new THREE.Color(0.4, 0.0, 0.8);
    if (wl > 780) return new THREE.Color(0.6, 0.0, 0.0);

    return new THREE.Color(r, g, b);
}

// ==========================================
// 5. 主程序入口
// ==========================================
function main() {
    isDisposed = false;
    let currentProjectile = 'photon';

    // --- A. 基础环境搭建 ---
    const canvas = document.querySelector('#c');
    renderer = new THREE.WebGLRenderer({ antialias: true, canvas: canvas });
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.toneMapping = THREE.ReinhardToneMapping;

    scene = new THREE.Scene();
    scene.background = new THREE.Color(CONFIG.colors.background);

    camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
    camera.position.set(0, 15, 30);

    controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;

    labelRenderer = new CSS2DRenderer();
    labelRenderer.setSize(window.innerWidth, window.innerHeight);
    labelRenderer.domElement.style.position = 'absolute';
    labelRenderer.domElement.style.top = '0px';
    labelRenderer.domElement.style.pointerEvents = 'none';
    document.body.appendChild(labelRenderer.domElement);

    // --- B. 状态管理 ---
    const electronState = {
        radius: CONFIG.physics.orbitsInfo[0].radius,
        angle: 0,
        speed: CONFIG.physics.baseSpeed,
        currentN: 1
    };

    // --- C. 后处理特效管线 ---
    const renderScene = new RenderPass(scene, camera);
    const afterimagePass = new AfterimagePass();
    afterimagePass.uniforms['damp'].value = CONFIG.postprocessing.afterimageDamp;

    const bloomPass = new UnrealBloomPass(new THREE.Vector2(window.innerWidth, window.innerHeight), 1.5, 0.4, 0.85);
    bloomPass.threshold = CONFIG.postprocessing.bloomThreshold;
    bloomPass.strength = CONFIG.postprocessing.bloomStrength;
    bloomPass.radius = CONFIG.postprocessing.bloomRadius;

    composer = new EffectComposer(renderer);
    composer.addPass(renderScene);
    composer.addPass(afterimagePass);
    composer.addPass(bloomPass);
    composer.addPass(new OutputPass());

    // --- D. UI 与 GUI 控制面板 ---
    const domDataN = document.getElementById('data-n');
    const domDataE = document.getElementById('data-e');
    const domDataLambda = document.getElementById('data-lambda');
    const aiText = document.getElementById('ai-text');
    const logContainer = document.getElementById('log-container');
    let aiTypingTimer = null;

    function getThemeVar(name, fallback = '') {
        const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return v || fallback;
    }

    function applyThemeButtonStyles() {
        const buttonBg = getThemeVar('--theme-button-bg', 'rgba(0, 229, 255, 0.15)');
        const buttonBorder = getThemeVar('--theme-button-border', '#00e5ff');

        document.querySelectorAll('#left-sidebar button, #left-panel button').forEach((btn) => {
            btn.style.background = buttonBg;
            btn.style.borderColor = buttonBorder;
        });

        // 投射粒子切换按钮（通常是 radio + label）
        document.querySelectorAll('input[name="projectile"]').forEach((radio) => {
            const label = document.querySelector(`label[for="${radio.id}"]`);
            if (label) {
                label.style.background = buttonBg;
                label.style.borderColor = buttonBorder;
            }
        });
    }

    gui = new GUI({ title: '⚙️ 实验参数调节' });
    const folderPhysics = gui.addFolder('⚛️ 物理与动画控制');

    function syncElectronSpeed() {
        if (electronState.currentN === Infinity) return;
        const level = Math.max(1, electronState.currentN || 1);
        electronState.speed = CONFIG.physics.baseSpeed / level;
    }

    folderPhysics.add(CONFIG.physics, 'baseSpeed', 0.005, 0.1).step(0.01).name('电子基准速度').onChange(() => {
        syncElectronSpeed();
    });
    folderPhysics.add(CONFIG.animation, 'transitionDuration', 0.1, 5.0).step(0.01).name('电子跃迁时长');
    folderPhysics.add(CONFIG.animation, 'photonFlyTime', 0.5, 10.0).step(0.01).name('光子飞行时长');

    const folderVisuals = gui.addFolder('🌌 视觉与场景渲染');
    folderVisuals.add(CONFIG.scene, 'electronScale', 0.2, 5.0).step(0.01).name('电子体积大小').onChange(v => electron.scale.set(v, v, v));
    folderVisuals.add(bloomPass, 'strength', 0, 3).name('发光强度');
    folderVisuals.add(afterimagePass.uniforms['damp'], 'value', 0.5, 0.99).name('电子拖尾');
    folderVisuals.add(CONFIG.scene, 'showPhotonLabel').name('显示光子数据标签');

    controls.autoRotateSpeed = 0.5;
    folderVisuals.add(CONFIG.scene, 'autoRotate').name('开启星空漫游').onChange(v => controls.autoRotate = v);

    applyThemeButtonStyles();

    // --- 1D 经典能级图 ---
    const energyCanvas = document.getElementById('energy-canvas');
    const eCtx = energyCanvas.getContext('2d');
    let activeTransition = null;

    function drawEnergyDiagram() {
        if (!eCtx || isDisposed) return;
        const w = energyCanvas.width;
        const h = energyCanvas.height;
        eCtx.clearRect(0, 0, w, h);
        const getY = (energy) => 30 + (-energy / 13.6) * (h - 60);

        const levels = [
            { n: 1, e: -13.6 }, { n: 2, e: -3.4 }, { n: 3, e: -1.51 }, { n: '∞', e: 0 }
        ];

        eCtx.textBaseline = 'middle';
        eCtx.font = '12px Arial';
        levels.forEach(lvl => {
            const y = getY(lvl.e);
            eCtx.strokeStyle = lvl.n === '∞' ? 'rgba(255, 85, 85, 0.8)' : 'rgba(0, 229, 255, 0.5)';
            eCtx.lineWidth = lvl.n === '∞' ? 2 : 1;
            if (lvl.n === '∞') eCtx.setLineDash([5, 5]); else eCtx.setLineDash([]);
            eCtx.beginPath();
            eCtx.moveTo(40, y);
            eCtx.lineTo(w - 10, y);
            eCtx.stroke();
            eCtx.setLineDash([]);
            eCtx.fillStyle = '#ccc';
            eCtx.textAlign = 'left';
            eCtx.fillText(`n=${lvl.n}`, 5, y);
            eCtx.textAlign = 'right';
            eCtx.fillText(`${lvl.e.toFixed(2)} eV`, w - 10, y - 10);
        });

        if (activeTransition) {
            const { fromY, toY, progress, color, text } = activeTransition;
            const currentY = fromY + (toY - fromY) * progress;
            const x = w / 2 + 10;
            eCtx.strokeStyle = color; eCtx.fillStyle = color;
            eCtx.lineWidth = 3;
            eCtx.shadowColor = color; eCtx.shadowBlur = 10;
            eCtx.beginPath();
            eCtx.moveTo(x, fromY);
            eCtx.lineTo(x, currentY);
            eCtx.stroke();
            if (progress > 0.05) {
                const dir = toY > fromY ? 1 : -1;
                eCtx.beginPath();
                eCtx.moveTo(x, currentY);
                eCtx.lineTo(x - 5, currentY - dir * 10);
                eCtx.lineTo(x + 5, currentY - dir * 10);
                eCtx.fill();
            }
            eCtx.shadowBlur = 0;
            if (progress > 0.5) {
                eCtx.textAlign = 'center';
                eCtx.fillText(text, x - 35, (fromY + toY) / 2);
            }
        }
    }
    drawEnergyDiagram();

    function triggerEnergyArrow(fromN, toN, deltaE, colorObj) {
        const h = energyCanvas.height;
        const getY = (energy) => 30 + (-energy / 13.6) * (h - 60);
        activeTransition = {
            fromY: getY(calculateEnergy(fromN)),
            toY: getY(calculateEnergy(toN)),
            progress: 0,
            color: `rgb(${colorObj.r * 255}, ${colorObj.g * 255}, ${colorObj.b * 255})`,
            text: `${deltaE.toFixed(2)} eV`
        };
        gsap.to(activeTransition, {
            progress: 1, duration: 1.5, ease: "power2.inOut",
            onUpdate: drawEnergyDiagram,
            onComplete: () => {
                trackedSetTimeout(() => { activeTransition = null; drawEnergyDiagram(); }, 2500);
            }
        });
    }

    function streamAIText(htmlContent) {
        if (!aiText || isDisposed) return;
        trackedClearTimeout(aiTypingTimer);
        aiText.innerHTML = '';
        let i = 0; let currentHTML = '';
        function typeNext() {
            if (i < htmlContent.length) {
                let char = htmlContent[i];
                if (char === '<') {
                    let tagEnd = htmlContent.indexOf('>', i);
                    if (tagEnd !== -1) {
                        currentHTML += htmlContent.substring(i, tagEnd + 1);
                        i = tagEnd + 1;
                    } else { currentHTML += char; i++; }
                } else { currentHTML += char; i++; }
                aiText.innerHTML = currentHTML;
                let delay = (char === '<' || char === '>') ? 0 : 15 + Math.random() * 15;
                aiTypingTimer = trackedSetTimeout(typeNext, delay);
            }
        }
        typeNext();
    }

    function addLog(htmlContent, type = 'normal') {
        if (!logContainer || isDisposed) return;
        const logItem = document.createElement('div');
        logItem.className = 'log-item';

        const logTitleColor = getThemeVar('--theme-log-title', getThemeVar('--theme-secondary', '#00e5ff'));
        const logAccentColor = getThemeVar('--theme-log-accent', getThemeVar('--theme-accent', '#ffbf00'));

        if (type === 'danger') logItem.style.borderLeftColor = '#ff5555';
        else if (type === 'success') logItem.style.borderLeftColor = '#00ff66';

        const styledContent = htmlContent
            .replace(/<strong>/g, `<strong style="color: ${logTitleColor};">`)
            .replace(/<span>/g, `<span style="color: ${logAccentColor};">`);

        const timeStr = new Date().toLocaleTimeString('zh-CN', { hour12: false });
        logItem.innerHTML = `<div class="time">[${timeStr}]</div>${styledContent}`;
        logContainer.prepend(logItem);
        if (logContainer.children.length > 30) logContainer.removeChild(logContainer.lastChild);
    }

    // --- E. 构建 3D 场景内容 ---
    const starsGeometry = new THREE.BufferGeometry();
    const starsCount = 1000;
    const posArray = new Float32Array(starsCount * 3);
    for (let i = 0; i < starsCount * 3; i++) posArray[i] = (Math.random() - 0.5) * 100;
    starsGeometry.setAttribute('position', new THREE.BufferAttribute(posArray, 3));
    const starsMaterial = new THREE.PointsMaterial({ size: 0.05, color: 0xffffff, transparent: true, opacity: 0.5 });
    scene.add(new THREE.Points(starsGeometry, starsMaterial));

    const grid = new THREE.GridHelper(100, 50, CONFIG.colors.grid, 0x080810);
    grid.position.y = -10;
    grid.material.transparent = true; grid.material.opacity = 0.2;
    scene.add(grid);

    const nucleusGeo = new THREE.SphereGeometry(1.8, 64, 64);
    const nucleusMat = new THREE.MeshStandardMaterial({
        color: CONFIG.colors.nucleus, emissive: CONFIG.colors.nucleus,
        emissiveIntensity: 2, roughness: 0, metalness: 1
    });
    scene.add(new THREE.Mesh(nucleusGeo, nucleusMat));

    CONFIG.physics.orbitsInfo.forEach(o => {
        const curve = new THREE.EllipseCurve(0, 0, o.radius, o.radius);
        const points = curve.getPoints(100);
        const geometry = new THREE.BufferGeometry().setFromPoints(points);
        const material = new THREE.LineDashedMaterial({ color: CONFIG.colors.orbit, dashSize: 0.5, gapSize: 0.5, transparent: true, opacity: 0.3 });
        const line = new THREE.Line(geometry, material);
        line.computeLineDistances(); line.rotation.x = Math.PI / 2;
        scene.add(line);

        const orbitDiv = document.createElement('div');
        orbitDiv.className = 'orbit-label'; orbitDiv.textContent = `n = ${o.n}`;
        const orbitLabel = new CSS2DObject(orbitDiv);
        orbitLabel.position.set(o.radius, 0, 0);
        scene.add(orbitLabel);
    });

    const electronGeo = new THREE.SphereGeometry(0.5, 32, 32);
    const electronMat = new THREE.MeshStandardMaterial({
        color: CONFIG.colors.electronLevel1, emissive: CONFIG.colors.electronLevel1, emissiveIntensity: 5
    });
    const electron = new THREE.Mesh(electronGeo, electronMat);
    scene.add(electron);

    // --- F. 核心交互逻辑 ---
    function shootProjectile(isAbsorbing, colorObj, wavelength, deltaE, onCompleteCallback, isMiss = false, projType = 'photon', remainE = 0) {
        const isElectron = projType === 'electron';
        const geo = new THREE.SphereGeometry(isElectron ? 0.2 : 0.3, 16, 16);
        const renderColor = isElectron ? new THREE.Color(0x00ff88) : colorObj;
        const mat = new THREE.MeshStandardMaterial({ color: renderColor, emissive: renderColor, emissiveIntensity: isElectron ? 4 : 2 });
        const projectile = new THREE.Mesh(geo, mat);
        scene.add(projectile);

        let infoDiv = null; let projLabel = null;
        if (CONFIG.scene.showPhotonLabel) {
            infoDiv = document.createElement('div');
            infoDiv.className = 'photon-label';
            if (isElectron) {
                infoDiv.style.borderColor = 'rgba(0, 255, 136, 0.5)';
                infoDiv.innerHTML = `⚪ 自由电子<br>Ek: <span>${deltaE.toFixed(2)} eV</span>`;
            } else {
                infoDiv.innerHTML = `〰️ 光子<br>λ: <span>${wavelength.toFixed(1)} nm</span><br>E: <span>${deltaE.toFixed(2)} eV</span>`;
            }
            projLabel = new CSS2DObject(infoDiv);
            projectile.add(projLabel);
        }

        const distance = 35;
        const farAngle = electronState.angle + Math.PI / 2;
        const farPos = new THREE.Vector3(Math.cos(farAngle) * distance, (Math.random() - 0.5) * 5, Math.sin(farAngle) * distance);
        let startPos = isAbsorbing ? farPos.clone() : electron.position.clone();
        projectile.position.copy(startPos);

        let animObj = { p: 0 };
        gsap.to(animObj, {
            p: 1, duration: CONFIG.animation.photonFlyTime, ease: "none",
            onUpdate: () => {
                if (isAbsorbing) {
                    if (isMiss && !isElectron) {
                        let missEndPos = new THREE.Vector3().copy(farPos).multiplyScalar(-1);
                        projectile.position.copy(startPos).lerp(missEndPos, animObj.p);
                    } else if (isMiss && isElectron) {
                        if (animObj.p < 0.5) { projectile.position.copy(startPos).lerp(electron.position, animObj.p * 2); }
                        else {
                            let bouncePos = startPos.clone().applyAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI / 3);
                            projectile.position.copy(electron.position).lerp(bouncePos, (animObj.p - 0.5) * 2);
                        }
                    } else { projectile.position.copy(startPos).lerp(electron.position, animObj.p); }
                } else { projectile.position.copy(startPos).lerp(farPos, animObj.p); }
            },
            onComplete: () => {
                if (isAbsorbing && !isMiss && isElectron && remainE >= 0) {
                    if (infoDiv) infoDiv.innerHTML = `⚪ 散射电子<br>剩余 Ek: <span>${remainE.toFixed(2)} eV</span>`;
                    let scatterPos = startPos.clone().applyAxisAngle(new THREE.Vector3(0, 1, 0), -Math.PI / 2.5);
                    gsap.to(projectile.position, {
                        x: scatterPos.x, y: scatterPos.y, z: scatterPos.z,
                        duration: CONFIG.animation.photonFlyTime, ease: "power2.out",
                        onComplete: () => doCleanUp()
                    });
                    if (onCompleteCallback) onCompleteCallback();
                } else {
                    doCleanUp();
                    if (onCompleteCallback) onCompleteCallback();
                }
            }
        });

        function doCleanUp() {
            if (projLabel) { projectile.remove(projLabel); infoDiv.remove(); }
            scene.remove(projectile); geo.dispose(); mat.dispose();
        }
    }

    function jumpToOrbit(targetN, customInputE = null, projType = 'photon', remainE = 0) {
        if (electronState.currentN === targetN && targetN !== Infinity) return;
        if (electronState.currentN === Infinity) return;

        const isIonize = targetN === Infinity;
        const targetRadius = isIonize ? 500 : CONFIG.physics.orbitsInfo[targetN - 1].radius;
        const isAbsorbing = isIonize || targetN > electronState.currentN;
        const targetSpeed = isIonize ? 0.05 : CONFIG.physics.baseSpeed / targetN;

        const eInitial = calculateEnergy(electronState.currentN);
        const eFinal = calculateEnergy(targetN);
        const deltaE = customInputE || Math.abs(eFinal - eInitial);
        const wavelength = 1240 / deltaE;
        const realPhotonColor = wavelengthToColor(wavelength);

        if (domDataN) domDataN.innerText = isIonize ? '∞ (自由电子)' : targetN;
        if (domDataE) domDataE.innerText = (isIonize ? (customInputE - Math.abs(eInitial)) : eFinal).toFixed(2) + ' eV';
        if (domDataLambda) domDataLambda.innerText = wavelength.toFixed(1) + ' nm';

        const projName = projType === 'electron' ? '自由电子' : '光子';
        const projIcon = projType === 'electron' ? '⚪' : '〰️';

        if (isIonize) {
            streamAIText(`🚨 <strong>极限状态：电离！</strong><br>氢原子的电子被高能 ${projName} 轰击，获得了强大的 <strong>${deltaE.toFixed(2)} eV</strong> 能量，克服了原子核束缚，向宇宙深处飞去。`);
            addLog(`⚡ <strong>电离 (n=∞)</strong><br>${projIcon} 轰击: <span>${deltaE.toFixed(2)} eV</span>`, 'danger');
            document.getElementById('btn-inf').style.display = 'none';
            document.getElementById('btn-reset').style.display = 'block';
        } else if (isAbsorbing) {
            if (projType === 'electron') {
                streamAIText(`⚠️ <strong>非弹性碰撞！</strong><br>原子的电子吸收了入射电子 <strong>${deltaE.toFixed(2)} eV</strong> 的动能，跃迁至 n=${targetN}。<br>入射电子带着剩余的不足 <strong>${remainE.toFixed(2)} eV</strong> 动能弹开。`);
            } else {
                streamAIText(`⚠️ <strong>吸收能量！</strong><br>电子吸收了波长为 ${wavelength.toFixed(1)}nm 的光子，跃迁至 n=${targetN}。`);
            }
            addLog(`⬆️ <strong>跃迁至 n=${targetN}</strong><br>${projIcon} 注入: <span>${deltaE.toFixed(2)} eV</span><br>能量变化: ${eInitial.toFixed(2)} → <span>${eFinal.toFixed(2)} eV</span>`);
            syncElectronSpeed();
        } else {
            streamAIText(`📉 <strong>辐射能量！</strong><br>电子跌落回 n=${targetN}，辐射了一个波长为 ${wavelength.toFixed(1)}nm 的光子。`);
            addLog(`⬇️ <strong>跌落至 n=${targetN}</strong><br>辐射光子: λ=<span>${wavelength.toFixed(1)} nm</span><br>能量变化: ${eInitial.toFixed(2)} → <span>${eFinal.toFixed(2)} eV</span>`);
        }

        const newColorHex = isIonize ? 0xff0055 : CONFIG.colors[`electronLevel${targetN}`];
        const newColor = new THREE.Color(newColorHex);
        gsap.to(electronMat.color, { r: newColor.r, g: newColor.g, b: newColor.b, duration: 1.5 });
        gsap.to(electronMat.emissive, { r: newColor.r, g: newColor.g, b: newColor.b, duration: 1.5 });

        triggerEnergyArrow(electronState.currentN, targetN, deltaE, realPhotonColor);
        electronState.currentN = targetN;
        gsap.to(electronState, { speed: 0.002, duration: 0.3 });

        if (isAbsorbing) {
            shootProjectile(true, realPhotonColor, wavelength, deltaE, () => {
                gsap.to(electronState, {
                    radius: targetRadius, duration: isIonize ? 4.0 : CONFIG.animation.transitionDuration,
                    ease: isIonize ? "power2.in" : "back.out(1.2)",
                    onComplete: () => { if (!isIonize) gsap.to(electronState, { speed: targetSpeed, duration: 0.5 }); }
                });
            }, false, projType, remainE);
        } else {
            gsap.to(electronState, {
                radius: targetRadius, duration: CONFIG.animation.transitionDuration, ease: "back.inOut(1.2)",
                onComplete: () => {
                    shootProjectile(false, realPhotonColor, wavelength, deltaE, null, false, 'photon', 0);
                    gsap.to(electronState, { speed: targetSpeed, duration: 0.5 });
                }
            });
        }
    }

    // --- G. 结合模板的事件监听 ---
    trackedAddEventListener(document.getElementById('btn-n1'), 'click', () => jumpToOrbit(1));
    trackedAddEventListener(document.getElementById('btn-n2'), 'click', () => jumpToOrbit(2));
    trackedAddEventListener(document.getElementById('btn-n3'), 'click', () => jumpToOrbit(3));
    trackedAddEventListener(document.getElementById('btn-inf'), 'click', () => jumpToOrbit(Infinity));

    trackedAddEventListener(document.getElementById('btn-fire'), 'click', () => {
        const inputE = parseFloat(document.getElementById('energy-input').value);
        if (isNaN(inputE) || inputE <= 0) return;
        if (electronState.currentN === Infinity) {
            streamAIText(`❌ <strong>原子已电离</strong><br>没有处于束缚态的电子可以发生碰撞了。`);
            return;
        }

        const eCurrent = calculateEnergy(electronState.currentN);
        const wl = 1240 / inputE;
        const c = wavelengthToColor(wl);

        if (currentProjectile === 'photon') {
            if (eCurrent + inputE >= 0) { jumpToOrbit(Infinity, inputE, 'photon'); return; }
            let targetN = -1;
            for (let n = electronState.currentN + 1; n <= 3; n++) {
                if (Math.abs(Math.abs(calculateEnergy(n) - eCurrent) - inputE) < 0.1) { targetN = n; break; }
            }
            if (targetN !== -1) { jumpToOrbit(targetN, inputE, 'photon'); }
            else {
                streamAIText(`❌ <strong>能量不匹配！</strong><br>光子能量 <strong>${inputE.toFixed(2)} eV</strong> 与任何能级差都不吻合。<br>因为光子必须被完整吸收，电子拒绝了它，光子直接<strong>穿透</strong>了原子！`);
                addLog(`❌ <strong>光子穿透</strong><br>注入: <span>${inputE.toFixed(2)} eV</span><br>不满足任何跃迁条件`, 'danger');
                shootProjectile(true, c, wl, inputE, null, true, 'photon');
            }
        } else if (currentProjectile === 'electron') {
            if (eCurrent + inputE >= 0) { jumpToOrbit(Infinity, inputE, 'electron', (eCurrent + inputE)); return; }
            let targetN = -1, actualDeltaE = 0;
            for (let n = 3; n > electronState.currentN; n--) {
                let diff = Math.abs(calculateEnergy(n) - eCurrent);
                if (inputE >= (diff - 0.05)) { targetN = n; actualDeltaE = diff; break; }
            }
            if (targetN !== -1) { jumpToOrbit(targetN, actualDeltaE, 'electron', inputE - actualDeltaE); }
            else {
                streamAIText(`❌ <strong>激发失败！</strong><br>入射电子动能 <strong>${inputE.toFixed(2)} eV</strong> 小于最低的跃迁门槛。<br>发生<strong>弹性碰撞</strong>，入射电子原速弹开！`);
                addLog(`❌ <strong>弹性碰撞</strong><br>入射动能: <span>${inputE.toFixed(2)} eV</span><br>能量不足以激发电子`, 'danger');
                shootProjectile(true, new THREE.Color(0x00ff88), 0, inputE, null, true, 'electron', inputE);
            }
        }
    });

    trackedAddEventListener(document.getElementById('btn-reset'), 'click', () => {
        if (electronState.currentN !== Infinity) return;
        addLog(`🔄 <strong>捕获新电子</strong><br>系统重置，稳定于基态 n=1<br>当前能量: <span>-13.60 eV</span>`, 'success');
        document.getElementById('btn-reset').style.display = 'none';
        document.getElementById('btn-inf').style.display = 'block';

        electronState.currentN = 1;
        if (domDataN) domDataN.innerText = '1';
        if (domDataE) domDataE.innerText = '-13.60 eV';
        if (domDataLambda) domDataLambda.innerText = '-- nm';
        streamAIText(`✅ <strong>重置成功！</strong><br>强大的引力从宇宙深处捕获了一个新的游离电子，它释放能量后重新稳定在了基态（n=1）。实验继续！`);

        const newColor = new THREE.Color(CONFIG.colors.electronLevel1);
        gsap.to(electronMat.color, { r: newColor.r, g: newColor.g, b: newColor.b, duration: 1.5 });
        gsap.to(electronMat.emissive, { r: newColor.r, g: newColor.g, b: newColor.b, duration: 1.5 });

        gsap.to(electronState, {
            radius: CONFIG.physics.orbitsInfo[0].radius, duration: 2.5, ease: "power2.out",
            onComplete: () => {
                syncElectronSpeed();
            }
        });

        if (typeof drawEnergyDiagram === 'function') {
            activeTransition = null;
            drawEnergyDiagram();
        }
    });

    document.querySelectorAll('input[name="projectile"]').forEach(radio => {
        trackedAddEventListener(radio, 'change', (e) => {
            currentProjectile = e.target.value;
            if (currentProjectile === 'electron') {
                streamAIText(`⚪ <strong>已切换为：电子轰击模式</strong><br>提示：自由电子碰撞时，只要动能大于能级差即可发生激发。`);
            } else {
                streamAIText(`〰️ <strong>已切换为：光子轰击模式</strong><br>提示：光子必须被完全吸收，只有能量严格等于能级差时，才会发生跃迁。`);
            }
        });
    });

    trackedAddEventListener(window, 'resize', () => {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
        composer.setSize(window.innerWidth, window.innerHeight);
        labelRenderer.setSize(window.innerWidth, window.innerHeight);
    });

    // --- H. 渲染循环 ---
    function render() {
        if (isDisposed) return; // 劫持循环，防止僵尸帧

        controls.update();

        electronState.angle += electronState.speed;
        electron.position.x = electronState.radius * Math.cos(electronState.angle);
        electron.position.z = electronState.radius * Math.sin(electronState.angle);

        electron.position.x += (Math.random() - 0.5) * 0.05;
        electron.position.y = (Math.random() - 0.5) * 0.05;

        composer.render();
        labelRenderer.render(scene, camera);

        trackedRequestAnimationFrame(render);
    }

    trackedRequestAnimationFrame(render);
}

// 启动程序
main();