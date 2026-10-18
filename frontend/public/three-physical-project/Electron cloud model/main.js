import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import GUI from 'https://cdn.jsdelivr.net/npm/lil-gui@0.19/+esm';

// ==========================================
// 🛠️ 统一资源释放工具函数 (模板规范)
// ==========================================
const trackedEventListeners = [];
function trackedAddEventListener(target, type, listener, options) {
    target.addEventListener(type, listener, options);
    trackedEventListeners.push({ target, type, listener, options });
}

function removeTrackedEventListeners() {
    trackedEventListeners.forEach(({ target, type, listener, options }) => {
        target.removeEventListener(type, listener, options);
    });
    trackedEventListeners.length = 0;
}

function disposeMaterial(material) {
    if (!material) return;
    if (Array.isArray(material)) {
        material.forEach(disposeMaterial);
        return;
    }
    Object.keys(material).forEach(key => {
        const value = material[key];
        if (value && typeof value.dispose === 'function' && key !== 'program') {
            value.dispose();
        }
    });
    material.dispose();
}

function disposeObject3D(obj) {
    if (!obj) return;
    obj.traverse(child => {
        if (child.geometry) child.geometry.dispose();
        if (child.material) disposeMaterial(child.material);
    });
}

//CONFIG定义了所有可调节的参数和颜色设置，方便后续在GUI中绑定和调整，同时也让代码更清晰易维护。
const CONFIG = {
    observeMode: '全貌概率云',
    traceSpeed: 20,
    particleCount: 20000,
    jitter: 0.04,
    opacity: 0.1,
    showAxes: true,
    autoRotate: true,
    colors: {
        nucleus: 0xff3300,
        electron1s: 0x00e5ff,
        electron2p: 0xff00ff,
        electron3d: 0x00ff88,
        activeElectron: 0xffffff
    },
    bloom: { strength: 0.5, radius: 0.6, threshold: 0.1 }
};

let typeTimer = null;
let animationId = null; // 用于记录 requestAnimationFrame 的 ID，以便后续取消

// 【新增】日志记录函数
function addLog(msg) {
    const container = document.getElementById('log-container');
    if (!container) return;
    const time = new Date().toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const div = document.createElement('div');
    div.className = 'log-item';
    // 时间戳在第一行，内容在下面，使用 <br> 强制换行
    div.innerHTML = `<div class="time">[${time}]</div><div class="log-content">${msg}</div>`;
    container.prepend(div); // 最新日志在最上
}

// ==========================================
// 💡 AI 动态打字机引擎 
// ==========================================
function typeWriterHTML(htmlString, speed = 25) {
    const el = document.getElementById('ai-text');
    if (!el) return;
    clearTimeout(typeTimer);
    el.innerHTML = '';
    let i = 0;

    function type() {
        if (i < htmlString.length) {
            let char = htmlString.charAt(i);
            let delay = speed;

            if (char === '<') {
                let tagEnd = htmlString.indexOf('>', i);
                if (tagEnd !== -1) {
                    el.innerHTML += htmlString.substring(i, tagEnd + 1);
                    i = tagEnd + 1;
                    delay = 0;
                } else {
                    el.innerHTML += char; i++;
                }
            } else {
                el.innerHTML += char; i++;
            }

            if (delay === 0) {
                type();
            } else {
                typeTimer = setTimeout(type, delay);
            }
        }
    }
    type();
}

function createParticleTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 64; canvas.height = 64;
    const context = canvas.getContext('2d');
    const gradient = context.createRadialGradient(32, 32, 0, 32, 32, 32);
    gradient.addColorStop(0, 'rgba(255,255,255,1)');
    gradient.addColorStop(0.4, 'rgba(255,255,255,0.8)');
    gradient.addColorStop(1, 'rgba(255,255,255,0)');
    context.fillStyle = gradient;
    context.fillRect(0, 0, 64, 64);
    return new THREE.CanvasTexture(canvas);
}

function getOrbitalPoint(type) {
    const rand1 = (Math.random() - 0.5) * 2;
    const rand2 = (Math.random() - 0.5) * 2;
    let rBase = Math.abs(rand1 * rand2) * 25;
    let theta = Math.random() * Math.PI * 2;
    let phi = Math.acos(2 * Math.random() - 1);

    if (type === '2p') {
        rBase *= Math.abs(Math.cos(phi)) + 0.01;
    } else if (type === '3d') {
        rBase *= Math.abs(Math.sin(2 * theta) * Math.sin(phi)) + 0.05;
    }

    return new THREE.Vector3(
        rBase * Math.sin(phi) * Math.cos(theta),
        rBase * Math.cos(phi),
        rBase * Math.sin(phi) * Math.sin(theta)
    );
}

function main() {
    const canvas = document.querySelector('#c');
    const renderer = new THREE.WebGLRenderer({ antialias: true, canvas });
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.localClippingEnabled = true;

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
    camera.position.set(30, 25, 40);

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.autoRotate = CONFIG.autoRotate;
    controls.autoRotateSpeed = 0.5;

    let currentOrbitalType = '1s';
    let traceCount = 0;
    let hasFinishedSampling = false;



    // ==========================================
    // GUI 控制面板
    // ==========================================
    const gui = new GUI({ title: '⚙️ 实验参数调节' });
    const physFolder = gui.addFolder('⚛️ 物理与测不准效果');
    physFolder.add(CONFIG, 'traceSpeed', 1, 100, 1).name('单次采样速度(帧)');
    physFolder.add(CONFIG, 'jitter', 0, 0.2).step(0.01).name('量子涨落剧烈度');
    physFolder.add(CONFIG, 'particleCount', 1000, 50000, 1000).name('目标模拟总次数').onFinishChange(() => switchOrbital(currentOrbitalType));

    const viewFolder = gui.addFolder('👁️ 观察与视觉');
    viewFolder.add(CONFIG, 'showAxes').name('显示空间坐标轴').onChange(v => axesHelper.visible = v);
    viewFolder.add(CONFIG, 'autoRotate').name('开启星空漫游').onChange(v => controls.autoRotate = v);
    viewFolder.add(CONFIG, 'opacity', 0.01, 1).step(0.01).name('历史残影透明度').onChange(v => particleMat.opacity = v);
    viewFolder.add(CONFIG.bloom, 'strength', 0, 3).step(0.01).name('整体发光强度').onChange(v => composer.passes[1].strength = v);

    const axesHelper = new THREE.AxesHelper(15); scene.add(axesHelper);
    const grid = new THREE.GridHelper(100, 50, 0x112233, 0x081015); grid.position.y = -20; scene.add(grid);

    const nucleus = new THREE.Mesh(
        new THREE.SphereGeometry(0.4, 32, 32),
        new THREE.MeshStandardMaterial({ color: CONFIG.colors.nucleus, emissive: CONFIG.colors.nucleus, emissiveIntensity: 1 })
    );
    scene.add(nucleus); scene.add(new THREE.PointLight(0xffaa00, 20));

    const activeElectron = new THREE.Mesh(new THREE.SphereGeometry(0.5, 16, 16), new THREE.MeshBasicMaterial({ color: CONFIG.colors.activeElectron }));
    activeElectron.visible = false; scene.add(activeElectron);

    const clipPlane = new THREE.Plane(new THREE.Vector3(0, 0, 1), 30);
    const particleGeo = new THREE.BufferGeometry();

    // 【核心修复】：预先分配 Float32Array 内存并设置 attribute！杜绝黑屏报错！
    const initialPositions = new Float32Array(CONFIG.particleCount * 3);
    particleGeo.setAttribute('position', new THREE.BufferAttribute(initialPositions, 3));

    const particleMat = new THREE.PointsMaterial({
        color: CONFIG.colors.electron1s, size: 0.8, map: createParticleTexture(), transparent: true,
        opacity: CONFIG.opacity, depthWrite: false, blending: THREE.AdditiveBlending, clippingPlanes: [clipPlane]
    });
    const cloud = new THREE.Points(particleGeo, particleMat); scene.add(cloud);

    // 【核心修复】：分离 input 和 change 事件
    const sliceSlider = document.getElementById('slice-slider');
    // 1. input 事件负责在拖动过程中实时更新 3D 剖切面的视觉效果
    trackedAddEventListener(sliceSlider, 'input', (e) => {
        clipPlane.constant = parseFloat(e.target.value);
    });
    // 2. change 事件负责在松开鼠标（确定最终值）时，才记录一条日志，防止刷屏
    trackedAddEventListener(sliceSlider, 'change', (e) => {
        addLog(`剖切深度调整为: <strong>${e.target.value}</strong>`);
    });

    trackedAddEventListener(window, 'resize', () => {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
        composer.setSize(window.innerWidth, window.innerHeight);
    });

    const chartCanvas = document.getElementById('prob-chart'); const ctx = chartCanvas.getContext('2d');
    const chartState = { peak: 20, spread: 30, color: CONFIG.colors.electron1s };
    function drawChart() {
        if (!ctx) return;
        ctx.clearRect(0, 0, chartCanvas.width, chartCanvas.height);
        ctx.beginPath(); ctx.moveTo(0, chartCanvas.height);
        for (let x = 0; x < chartCanvas.width; x++) {
            const y = Math.pow(x / chartState.peak, 2) * Math.exp(-x / chartState.spread) * 150;
            ctx.lineTo(x, chartCanvas.height - y);
        }
        ctx.lineWidth = 3; ctx.strokeStyle = '#' + new THREE.Color(chartState.color).getHexString(); ctx.stroke();
        const gradient = ctx.createLinearGradient(0, 0, 0, chartCanvas.height);
        gradient.addColorStop(0, ctx.strokeStyle); gradient.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.fillStyle = gradient; ctx.lineTo(chartCanvas.width, chartCanvas.height); ctx.lineTo(0, chartCanvas.height); ctx.fill();
    }

    //这两个变量可以删去
    //const btnCloud = document.getElementById('btn-mode-cloud');
    //const btnTrace = document.getElementById('btn-mode-trace');

    // 修改：使用 trackedAddEventListener 替换按钮点击事件
    trackedAddEventListener(document.getElementById('btn-mode-cloud'), 'click', () => {
        document.getElementById('btn-mode-cloud').classList.add('active');
        document.getElementById('btn-mode-trace').classList.remove('active');
        CONFIG.observeMode = '全貌概率云';
        switchOrbital(currentOrbitalType);
    });

    trackedAddEventListener(document.getElementById('btn-mode-trace'), 'click', () => {
        document.getElementById('btn-mode-trace').classList.add('active');
        document.getElementById('btn-mode-cloud').classList.remove('active');
        CONFIG.observeMode = '单电子追踪记录';
        switchOrbital(currentOrbitalType);
    });

    trackedAddEventListener(document.getElementById('btn-1s'), 'click', () => switchOrbital('1s'));
    trackedAddEventListener(document.getElementById('btn-2p'), 'click', () => switchOrbital('2p'));
    trackedAddEventListener(document.getElementById('btn-3d'), 'click', () => switchOrbital('3d'));

    function switchOrbital(type) {
        currentOrbitalType = type;
        const count = CONFIG.particleCount;
        hasFinishedSampling = false;

        const newPositions = new Float32Array(count * 3);
        particleGeo.setAttribute('position', new THREE.BufferAttribute(newPositions, 3));

        if (CONFIG.observeMode === '全貌概率云') {
            activeElectron.visible = false;
            traceCount = count;
            for (let i = 0; i < count; i++) {
                const pt = getOrbitalPoint(type);
                newPositions[i * 3] = pt.x; newPositions[i * 3 + 1] = pt.y; newPositions[i * 3 + 2] = pt.z;
            }
            particleGeo.setDrawRange(0, count);
        } else {
            activeElectron.visible = true;
            traceCount = 0;
            particleGeo.setDrawRange(0, 0);
        }

        particleGeo.attributes.position.needsUpdate = true;
        particleMat.size = Math.max(0.2, 5 / Math.pow(count / 1000, 0.5));

        let targetColor, targetPeak, targetSpread, aiMsg;
        if (type === '1s') {
            targetColor = CONFIG.colors.electron1s; targetPeak = 15; targetSpread = 20;
            aiMsg = CONFIG.observeMode === '全貌概率云' ?
                "你选择了 <span class='hl-pink'>1s 轨道全貌</span>。这是基态电子云，概率分布呈完美的球形对称。越靠近中心，青色点越密集，说明在该处发现电子的概率越高。" :
                "📡 <strong>正在启动单电子测量设备（模拟）...</strong><br>请注意屏幕中心！那颗<span class='hl-yellow'>亮白色的球体</span>就是真实电子的实际位置。观察这些离散的点是如何慢慢积累成一团云的！";
        } else if (type === '2p') {
            targetColor = CONFIG.colors.electron2p; targetPeak = 40; targetSpread = 30;
            aiMsg = CONFIG.observeMode === '全貌概率云' ?
                "你选择了 <span class='hl-pink'>2p 轨道全貌</span>。球对称被打破，形成了典型的哑铃形。<br><strong>科学说明：</strong>虽然理论上原子核中心发现电子的概率为 0，但为了保证电子云轮廓在视觉上的连贯性，仿真中保留了微量像素叠加，以展示电子‘瞬间移动’跨越节面的趋势。" :
                "📡 <strong>正在启动单电子测量设备（模拟）...</strong><br>注意观察白色电子出现的位置！虽然仿真为了视觉引导在中心保留了微弱雾气，但真实的单电子位置<span class='hl-pink'>永远不会落在两瓣云团正中间的空白间隙里</span>。";
        } else if (type === '3d') {
            targetColor = CONFIG.colors.electron3d; targetPeak = 80; targetSpread = 40;
            aiMsg = CONFIG.observeMode === '全貌概率云' ?
                "你选择了 <span class='hl-pink'>3d 轨道全貌</span>。能量极高，驻波形态变得复杂，出现了四个相互垂直的波瓣。可以尝试拖动剖切滑块观察内部结构！" :
                "📡 <strong>正在启动单电子测量设备（模拟）...</strong><br>在这个高能级状态下，单电子的落点变得更加复杂。耐心等待，让时间为我们绘制出这四片花瓣的概率轮廓。";
        }

        typeWriterHTML(aiMsg);
        // 【新增】同步发送日志
        // 在 switchOrbital(type) 内部
        const orbitalNames = { '1s': '1s 基态', '2p': '2p 激发态', '3d': '3d 高能态' };

        // 仅记录核心变更，信息通过 \n 或 <br> 拼接
        addLog(`🔮 轨道能级切换：<strong>${orbitalNames[type]}</strong>`);

        gsap.to(particleMat.color, { r: new THREE.Color(targetColor).r, g: new THREE.Color(targetColor).g, b: new THREE.Color(targetColor).b, duration: 1 });
        gsap.to(chartState, { peak: targetPeak, spread: targetSpread, color: targetColor, duration: 1.5, onUpdate: drawChart });
    }

    const composer = new EffectComposer(renderer);
    const bloomPass = new UnrealBloomPass(new THREE.Vector2(window.innerWidth, window.innerHeight), CONFIG.bloom.strength, CONFIG.bloom.radius, CONFIG.bloom.threshold);
    composer.addPass(new RenderPass(scene, camera));
    composer.addPass(bloomPass);
    composer.addPass(new OutputPass());

    // 【核心修复】：直接同步调用，不使用 setTimeout
    switchOrbital('1s');

    // ==========================================
    // 渲染与逻辑循环 
    // ==========================================
    function render() {
        controls.update();
        const positions = particleGeo.attributes.position.array;

        if (CONFIG.observeMode === '单电子追踪记录') {
            if (traceCount >= CONFIG.particleCount && !hasFinishedSampling) {
                hasFinishedSampling = true;
                activeElectron.visible = false;

                // 根据轨道类型准备统计数据
                const rMax = (currentOrbitalType === '1s' ? '1.0' : currentOrbitalType === '2p' ? '4.0' : '9.0');

                // 将所有信息组合成一个字符串，用 <br> 分隔
                const report = `✅ 采样实验完成统计：<br>` +
                    `• 有效样本数：${CONFIG.particleCount} 次捕获<br>` +
                    `• 概率峰值半径 (玻尔半径)：r ≈ ${rMax} a₀<br>` +
                    `• 结论：电子分布与 ${currentOrbitalType} 波函数统计一致。`;

                addLog(report);

                // 视觉反馈
                gsap.to(bloomPass, { strength: 3.5, duration: 0.5, yoyo: true, repeat: 1 });
            } else if (traceCount < CONFIG.particleCount) {
                for (let k = 0; k < CONFIG.traceSpeed; k++) {
                    if (traceCount < CONFIG.particleCount) {
                        const pt = getOrbitalPoint(currentOrbitalType);
                        positions[traceCount * 3] = pt.x; positions[traceCount * 3 + 1] = pt.y; positions[traceCount * 3 + 2] = pt.z;
                        activeElectron.position.copy(pt);
                        traceCount++;
                    }
                }
                particleGeo.setDrawRange(0, traceCount);
            }
        }

        const currentActiveDots = CONFIG.observeMode === '全貌概率云' ? CONFIG.particleCount : traceCount;
        for (let i = 0; i < currentActiveDots; i++) {
            positions[i * 3] += (Math.random() - 0.5) * CONFIG.jitter;
            positions[i * 3 + 1] += (Math.random() - 0.5) * CONFIG.jitter;
            positions[i * 3 + 2] += (Math.random() - 0.5) * CONFIG.jitter;
        }

        particleGeo.attributes.position.needsUpdate = true;
        composer.render();
        animationId = requestAnimationFrame(render);
    }
    render();
    return function dispose() {
        console.log("正在销毁电子云实验...");

        // 1. 停止动画与计时器
        if (animationId) cancelAnimationFrame(animationId);
        //if的作用是为了避免在 dispose() 被多次调用时，重复调用 clearTimeout 导致潜在的错误或不必要的操作。虽然 clearTimeout 在传入一个无效的 ID 时不会抛出错误，但使用 if 语句可以更明确地表达意图，即只有当 typeTimer 存在时才尝试清除它。这是一种更安全和更清晰的编程实践。
        if (typeTimer) clearTimeout(typeTimer);
        // 2. 解绑事件
        removeTrackedEventListeners();

        // 4. [新增] 清理 UI 内容，防止下次进入实验时数据堆积
        const logContainer = document.getElementById('log-container');
        if (logContainer) logContainer.innerHTML = '';
        const aiText = document.getElementById('ai-text');
        if (aiText) aiText.innerHTML = '';

        // 5. 销毁 GUI
        if (gui) gui.destroy();

        // 6. [新增] 销毁控制器（非常重要，否则鼠标事件会残留）
        if (controls) controls.dispose();

        // 7. 清理 Three.js 场景图
        disposeObject3D(scene);

        // 8. 清理后处理与渲染器
        if (composer) composer.dispose();
        if (renderer) {
            renderer.dispose();
            renderer.forceContextLoss();
            if (renderer.domElement && renderer.domElement.parentNode) {
                renderer.domElement.parentNode.removeChild(renderer.domElement);
            }
        }
    };
}
main();