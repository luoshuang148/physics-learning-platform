import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';

const CONFIG = {
    nucleusSpacing: 15,    // 增大间距，体现“内部空旷”
    k: 1.2,                // 【关键修复】从 80 降到 1.2，让大多数粒子能穿过去
    particleSpeed: 0.85,   // 进一步降低，给学生更充足的观察时间
    emitterZ: -200,          // 发射器的统一 Z 轴坐标
    influenceRadius: 3.5,   // 略增影响半径：让散射现象更容易观察
    colors: {
        lead: 0x445566,    // 铅盒：金属灰色
        gold: 0xffd700,    // 黄色：用于核/金箔的主色
        alpha: 0xffffff,   // 粒子：白色
        scatter: 0xffd700 // 散射标记：黄色（背散射单独用红色）
    }
};


function main() {
    const canvas = document.querySelector('#c');
    const renderer = new THREE.WebGLRenderer({ antialias: true, canvas });
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setClearColor(0x000000); // 深黑背景，增强荧光屏与粒子高亮对比

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 2000);
    camera.position.set(60, 55, 140); // 初始视角更近一些，便于看清粒子

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.enablePan = false;
    // 让旋转中心略靠近发射-金核区域，避免一开始粒子在视野外
    controls.target.set(0, 0, -60);
    controls.update();


    // --- 环境光：让发射器显形 ---
    scene.add(new THREE.AmbientLight(0xffffff, 0.8));
    const dirLight = new THREE.DirectionalLight(0xffffff, 2);
    dirLight.position.set(50, 100, 100);
    scene.add(dirLight);

    // 补充一个点光源：让粒子球在暗背景下更“亮”
    const pointLight = new THREE.PointLight(0xffffff, 2.2, 600);
    pointLight.position.set(0, 0, CONFIG.emitterZ + 30);
    scene.add(pointLight);

    // ==========================================
    // 1. 发射器模型 (修正位置与颜色)
    // ==========================================


    const leadBoxGroup = new THREE.Group();
    // 铅盒主体 (长方体)
    const boxBody = new THREE.Mesh(
        new THREE.BoxGeometry(10, 10, 15), // 长宽高分别为 10, 10, 15
        new THREE.MeshStandardMaterial({ color: 0x556677, metalness: 0.8, roughness: 0.2 })//颜色调整
    );

    // 枪管 (圆柱体)：旋转使其指向金箔 (Z方向)
    const nozzle = new THREE.Mesh(
        new THREE.CylinderGeometry(1.5, 1.5, 10), // 半径为1.5，长度为10
        new THREE.MeshStandardMaterial({ color: 0x222222, metalness: 0.9 })//颜色调整
    );
    nozzle.rotation.x = Math.PI / 2;// 旋转90度使其沿Z轴指向金箔
    nozzle.position.z = 10; // 枪管向金箔方向延伸

    // 枪口绿光
    const muzzleGlow = new THREE.Mesh(
        new THREE.CircleGeometry(1.2, 16),
        new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.85 })
    );
    muzzleGlow.position.z = -15.1; // 刚好在枪管末端
    muzzleGlow.rotation.y = Math.PI;

    leadBoxGroup.add(boxBody, nozzle, muzzleGlow);
    leadBoxGroup.scale.set(2, 2, 2); // 发射器整体放大到两倍
    leadBoxGroup.position.set(0, 0, CONFIG.emitterZ); // 发射器放在 Z=-200 的位置
    scene.add(leadBoxGroup);

    // 靶材（金箔）所在平面（散射瞬时发生与可视化对齐）
    const FOIL_Z = 0;

    // ==========================================
    // 发射束可视化（教学用）
    // ==========================================
    const beamLength = Math.abs(FOIL_Z - (CONFIG.emitterZ + 10));
    // 粒子束半径调细到约原来的一半，束流更贴近“准直后”的视觉效果
    const beamGeo = new THREE.CylinderGeometry(0.18, 0.48, beamLength, 18, 1, true);
    const beamMat = new THREE.MeshBasicMaterial({
        color: CONFIG.colors.alpha,
        transparent: true,
        opacity: 0.22,
        depthWrite: false
    });
    const beam = new THREE.Mesh(beamGeo, beamMat);
    beam.rotation.x = Math.PI / 2; // 让圆柱轴对齐 Z 方向
    beam.position.set(0, 0, (CONFIG.emitterZ + 10 + FOIL_Z) / 2);
    beam.visible = false;
    scene.add(beam);

    let detectorFlashes = [];
    let isDisposed = false;
    let animationFrameId = null;
    const activeTimeoutIds = new Set();

    function trackedSetTimeout(fn, delay) {
        const id = window.setTimeout(() => {
            activeTimeoutIds.delete(id);
            if (isDisposed) return;
            fn();
        }, delay);
        activeTimeoutIds.add(id);
        return id;
    }

    function addDetectorFlash(position, color, size = 0.9, ttlMs = 900) {
        const flash = new THREE.Mesh(
            new THREE.SphereGeometry(size, 12, 12),
            new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.95 })
        );
        flash.position.copy(position);
        flash.renderOrder = 12;
        flash.material.depthWrite = false;
        scene.add(flash);
        detectorFlashes.push(flash);

        trackedSetTimeout(() => {
            scene.remove(flash);
        }, ttlMs);

        // 控制数量，避免长时间运行卡顿
        if (detectorFlashes.length > 180) {
            const old = detectorFlashes.shift();
            if (old) scene.remove(old);
        }
    }

    let lastMuzzlePulseAt = 0;

    // ==========================================
    // 2. 金箔与探测屏 (宏观背景)
    // ==========================================
    // 探测屏：开口方向与边框统一由同一组角参数控制
    const SCREEN_RADIUS = 140;
    const SCREEN_HEIGHT = 50;
    // 让荧光幕开口正对发射器方向（-Z）
    // CylinderGeometry 角度以 +X 为 0，逆时针到 +Z、-X、-Z
    // 这里保留 0.2π 的开口，并将开口中心对准 3π/2（即 -Z）
    const SCREEN_THETA_START = Math.PI * 1.60;
    const SCREEN_THETA_LENGTH = Math.PI * 1.80;

    const screenGeo = new THREE.CylinderGeometry(
        SCREEN_RADIUS,
        SCREEN_RADIUS,
        SCREEN_HEIGHT,
        40,
        1,
        true,
        SCREEN_THETA_START,
        SCREEN_THETA_LENGTH
    );
    const screenMat = new THREE.MeshStandardMaterial({
        color: 0x121418, // 与背景纯黑区分开的“深黑”
        side: THREE.DoubleSide,
        transparent: true,
        opacity: 0.62,
        wireframe: false,
        metalness: 0.08,
        roughness: 0.92
    });
    const detector = new THREE.Mesh(screenGeo, screenMat);
    detector.rotation.y = 0;
    scene.add(detector);

    const detectorEdges = new THREE.EdgesGeometry(screenGeo);
    const detectorEdgeLine = new THREE.LineSegments(
        detectorEdges,
        new THREE.LineBasicMaterial({ color: 0x050505, transparent: true, opacity: 0.9 })
    );
    detectorEdgeLine.rotation.y = detector.rotation.y;
    detectorEdgeLine.renderOrder = 2;
    scene.add(detectorEdgeLine);

    // 荧光屏上下边框高亮白色，其他边线保持黑色
    const detectorTopRim = new THREE.Mesh(
        new THREE.TorusGeometry(SCREEN_RADIUS, 1.2, 10, 120, SCREEN_THETA_LENGTH),
        new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.98 })
    );
    detectorTopRim.position.y = SCREEN_HEIGHT / 2;
    detectorTopRim.rotation.x = Math.PI / 2;
    detectorTopRim.rotation.z = SCREEN_THETA_START;
    detectorTopRim.renderOrder = 3;
    scene.add(detectorTopRim);

    const detectorBottomRim = new THREE.Mesh(
        new THREE.TorusGeometry(SCREEN_RADIUS, 1.2, 10, 120, SCREEN_THETA_LENGTH),
        new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.98 })
    );
    detectorBottomRim.position.y = -SCREEN_HEIGHT / 2;
    detectorBottomRim.rotation.x = Math.PI / 2;
    detectorBottomRim.rotation.z = SCREEN_THETA_START;
    detectorBottomRim.renderOrder = 3;
    scene.add(detectorBottomRim);

    // 观察窗可视化：在探测屏上显示当前采样方向（可随滑块实时转动）
    const observationWindowGroup = new THREE.Group();
    scene.add(observationWindowGroup);

    const obsWindowRadius = SCREEN_RADIUS + 0.6;
    const obsWindowHalfWidthRad = 12 * Math.PI / 180;

    function createWindowArcLine(yPos) {
        const geo = new THREE.BufferGeometry();
        const mat = new THREE.LineBasicMaterial({
            color: 0xb7ffc0,
            transparent: true,
            opacity: 0.95
        });
        const line = new THREE.Line(geo, mat);
        line.position.y = yPos;
        line.renderOrder = 8;
        observationWindowGroup.add(line);
        return line;
    }

    function createWindowBoundaryLine(sign = 1) {
        const geo = new THREE.BufferGeometry();
        const mat = new THREE.LineBasicMaterial({
            color: 0xd8ffe0,
            transparent: true,
            opacity: 0.9
        });
        const line = new THREE.Line(geo, mat);
        line.userData.sign = sign;
        line.renderOrder = 8;
        observationWindowGroup.add(line);
        return line;
    }

    const obsArcTop = createWindowArcLine(SCREEN_HEIGHT / 2 + 0.8);
    const obsArcMid = createWindowArcLine(0);
    const obsArcBottom = createWindowArcLine(-SCREEN_HEIGHT / 2 - 0.8);
    const obsBoundaryL = createWindowBoundaryLine(-1);
    const obsBoundaryR = createWindowBoundaryLine(1);

    const obsWindowSector = new THREE.Mesh(
        new THREE.BufferGeometry(),
        new THREE.MeshBasicMaterial({
            color: 0x86ff9a,
            transparent: true,
            opacity: 0.16,
            side: THREE.DoubleSide,
            depthWrite: false
        })
    );
    obsWindowSector.renderOrder = 7;
    observationWindowGroup.add(obsWindowSector);

    function setLinePoints(line, points) {
        line.geometry.setFromPoints(points);
        line.geometry.computeBoundingSphere();
    }

    function updateObservationWindowVisual() {
        const centerRad = observationAngleDeg * Math.PI / 180;
        const start = centerRad - obsWindowHalfWidthRad;
        const end = centerRad + obsWindowHalfWidthRad;
        const seg = 26;

        function buildArc(yPos) {
            const pts = [];
            for (let i = 0; i <= seg; i++) {
                const t = i / seg;
                const a = start + (end - start) * t;
                pts.push(new THREE.Vector3(
                    Math.sin(a) * obsWindowRadius,
                    yPos,
                    Math.cos(a) * obsWindowRadius
                ));
            }
            return pts;
        }

        const topY = SCREEN_HEIGHT / 2 + 0.8;
        const midY = 0;
        const bottomY = -SCREEN_HEIGHT / 2 - 0.8;
        setLinePoints(obsArcTop, buildArc(topY));
        setLinePoints(obsArcMid, buildArc(midY));
        setLinePoints(obsArcBottom, buildArc(bottomY));

        function buildBoundary(sign) {
            const a = centerRad + sign * obsWindowHalfWidthRad;
            return [
                new THREE.Vector3(Math.sin(a) * obsWindowRadius, topY, Math.cos(a) * obsWindowRadius),
                new THREE.Vector3(Math.sin(a) * obsWindowRadius, bottomY, Math.cos(a) * obsWindowRadius)
            ];
        }

        setLinePoints(obsBoundaryL, buildBoundary(-1));
        setLinePoints(obsBoundaryR, buildBoundary(1));

        // 扇形透明高亮区域（覆盖观察窗角范围）
        const innerR = SCREEN_RADIUS - 1.6;
        const outerR = SCREEN_RADIUS + 1.6;
        const yTop = SCREEN_HEIGHT / 2 + 0.2;
        const yBottom = -SCREEN_HEIGHT / 2 - 0.2;
        const positions = [];

        for (let i = 0; i < seg; i++) {
            const t0 = i / seg;
            const t1 = (i + 1) / seg;
            const a0 = start + (end - start) * t0;
            const a1 = start + (end - start) * t1;

            const pTopIn0 = new THREE.Vector3(Math.sin(a0) * innerR, yTop, Math.cos(a0) * innerR);
            const pTopOut0 = new THREE.Vector3(Math.sin(a0) * outerR, yTop, Math.cos(a0) * outerR);
            const pTopIn1 = new THREE.Vector3(Math.sin(a1) * innerR, yTop, Math.cos(a1) * innerR);
            const pTopOut1 = new THREE.Vector3(Math.sin(a1) * outerR, yTop, Math.cos(a1) * outerR);

            const pBotIn0 = new THREE.Vector3(Math.sin(a0) * innerR, yBottom, Math.cos(a0) * innerR);
            const pBotOut0 = new THREE.Vector3(Math.sin(a0) * outerR, yBottom, Math.cos(a0) * outerR);
            const pBotIn1 = new THREE.Vector3(Math.sin(a1) * innerR, yBottom, Math.cos(a1) * innerR);
            const pBotOut1 = new THREE.Vector3(Math.sin(a1) * outerR, yBottom, Math.cos(a1) * outerR);

            // 顶面环带
            positions.push(
                pTopIn0.x, pTopIn0.y, pTopIn0.z,
                pTopOut0.x, pTopOut0.y, pTopOut0.z,
                pTopOut1.x, pTopOut1.y, pTopOut1.z,

                pTopIn0.x, pTopIn0.y, pTopIn0.z,
                pTopOut1.x, pTopOut1.y, pTopOut1.z,
                pTopIn1.x, pTopIn1.y, pTopIn1.z
            );

            // 底面环带
            positions.push(
                pBotIn1.x, pBotIn1.y, pBotIn1.z,
                pBotOut1.x, pBotOut1.y, pBotOut1.z,
                pBotOut0.x, pBotOut0.y, pBotOut0.z,

                pBotIn1.x, pBotIn1.y, pBotIn1.z,
                pBotOut0.x, pBotOut0.y, pBotOut0.z,
                pBotIn0.x, pBotIn0.y, pBotIn0.z
            );

            // 外侧面
            positions.push(
                pTopOut0.x, pTopOut0.y, pTopOut0.z,
                pBotOut0.x, pBotOut0.y, pBotOut0.z,
                pBotOut1.x, pBotOut1.y, pBotOut1.z,

                pTopOut0.x, pTopOut0.y, pTopOut0.z,
                pBotOut1.x, pBotOut1.y, pBotOut1.z,
                pTopOut1.x, pTopOut1.y, pTopOut1.z
            );

            // 内侧面
            positions.push(
                pTopIn1.x, pTopIn1.y, pTopIn1.z,
                pBotIn1.x, pBotIn1.y, pBotIn1.z,
                pBotIn0.x, pBotIn0.y, pBotIn0.z,

                pTopIn1.x, pTopIn1.y, pTopIn1.z,
                pBotIn0.x, pBotIn0.y, pBotIn0.z,
                pTopIn0.x, pTopIn0.y, pTopIn0.z
            );
        }

        obsWindowSector.geometry.dispose();
        obsWindowSector.geometry = new THREE.BufferGeometry();
        obsWindowSector.geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
        obsWindowSector.geometry.computeVertexNormals();
        obsWindowSector.geometry.computeBoundingSphere();
    }

    // ==========================================
    // 3. 宏观散射核阵列（用于宏观实验）
    // ==========================================
    const nuclei = [];
    const latticeCols = 6;
    const latticeRows = 8;
    const shellRadius = 6.2;
    const latticeDx = shellRadius * 2.0;
    const latticeDy = shellRadius * 1.72; // 六角密堆行距

    for (let row = 0; row < latticeRows; row++) {
        const y = (row - (latticeRows - 1) / 2) * latticeDy;
        const xOffset = (row % 2 === 0) ? 0 : latticeDx * 0.5;

        for (let col = 0; col < latticeCols; col++) {
            const x = (col - (latticeCols - 1) / 2) * latticeDx + xOffset;
            nuclei.push({ position: new THREE.Vector3(x, y, 0) });
        }
    }

    // ==========================================
    // 3.1 新场景：单原子结构（粒子穿过原子）
    // ==========================================
    const atomGroup = new THREE.Group();
    scene.add(atomGroup);

    // 原子核：用“核子团簇 + 中心核芯”表现，更像真实核结构
    const nucleusGroup = new THREE.Group();
    atomGroup.add(nucleusGroup);

    const nucleonGeo = new THREE.SphereGeometry(0.055, 10, 10);
    const protonMat = new THREE.MeshBasicMaterial({ color: 0xff7070 });
    const neutronMat = new THREE.MeshBasicMaterial({ color: 0x8fc8ff });

    for (let i = 0; i < 14; i++) {
        const m = new THREE.Mesh(nucleonGeo, i % 2 === 0 ? protonMat : neutronMat);
        const r = THREE.MathUtils.randFloat(0.0, 0.17);
        const t = Math.random() * Math.PI * 2;
        const p = Math.acos(THREE.MathUtils.randFloatSpread(2));
        m.position.set(
            r * Math.sin(p) * Math.cos(t),
            r * Math.cos(p),
            r * Math.sin(p) * Math.sin(t)
        );
        nucleusGroup.add(m);
    }

    const nucleusCore = new THREE.Mesh(
        new THREE.SphereGeometry(0.075, 14, 14),
        new THREE.MeshBasicMaterial({ color: 0xfff3b0, transparent: true, opacity: 0.95 })
    );
    nucleusGroup.add(nucleusCore);

    // 壳层分布：2、8、18（教学示意）
    const shellDefs = [
        { n: 1, count: 2, radius: 5.8, color: 0xcfe6ff, cloudOpacity: 0.22 },
        { n: 2, count: 8, radius: 10.6, color: 0xaed7ff, cloudOpacity: 0.18 },
        { n: 3, count: 18, radius: 15.8, color: 0x8fc4ff, cloudOpacity: 0.14 }
    ];

    const atomElectrons = [];
    const electronGeo = new THREE.SphereGeometry(0.11, 8, 8);

    function createShellCloudPoints(radius, count, color, opacity) {
        const positions = new Float32Array(count * 3);
        for (let i = 0; i < count; i++) {
            const rr = radius + THREE.MathUtils.randFloatSpread(radius * 0.55);
            const t = Math.random() * Math.PI * 2;
            const p = Math.acos(THREE.MathUtils.randFloatSpread(2));
            positions[i * 3] = rr * Math.sin(p) * Math.cos(t);
            positions[i * 3 + 1] = rr * Math.cos(p);
            positions[i * 3 + 2] = rr * Math.sin(p) * Math.sin(t);
        }
        const g = new THREE.BufferGeometry();
        g.setAttribute('position', new THREE.BufferAttribute(positions, 3));
        const m = new THREE.PointsMaterial({
            color,
            size: 0.14,
            transparent: true,
            opacity,
            depthWrite: false
        });
        return new THREE.Points(g, m);
    }

    for (const shell of shellDefs) {
        // 使用体积分布点云模拟电子云，不显示圈状线框
        const cloudDensity = shell.n === 1 ? 180 : (shell.n === 2 ? 340 : 520);
        const cloud = createShellCloudPoints(shell.radius, cloudDensity, shell.color, shell.cloudOpacity * 0.9);
        atomGroup.add(cloud);

        for (let i = 0; i < shell.count; i++) {
            const mat = new THREE.MeshBasicMaterial({ color: shell.color, transparent: true, opacity: 0.76 });
            const e = new THREE.Mesh(electronGeo, mat);

            const phase = (i / shell.count) * Math.PI * 2;
            const tiltX = THREE.MathUtils.randFloat(-0.85, 0.85);
            const tiltY = THREE.MathUtils.randFloat(-0.95, 0.95);
            const wobbleAmp = THREE.MathUtils.randFloat(0.08, 0.42);
            const wobbleFreq = THREE.MathUtils.randFloat(0.75, 1.55);
            const speed = (0.0025 + shell.n * 0.0005) * THREE.MathUtils.randFloat(0.92, 1.16);

            e.userData = {
                shellRadius: shell.radius,
                shellN: shell.n,
                phase,
                speed,
                tiltX,
                tiltY,
                wobbleAmp,
                wobbleFreq
            };

            atomGroup.add(e);
            atomElectrons.push(e);
        }
    }

    const atomNuclei = [{ position: new THREE.Vector3(0, 0, FOIL_Z) }];
    atomGroup.visible = false;

    // ==========================================
    // 靶材：极薄金箔（可视化）
    // ==========================================
    // 由于金核球也位于 z=0，这里把金箔略微偏移，避免严重 z-fighting
    const foilZ = FOIL_Z;
    const foilSize = 14; // 金属箔放大到两倍
    const foilThickness = 0.08; // 厚度也相应放大到两倍（仍保持“极薄”视觉）

    const foilGeo = new THREE.BoxGeometry(foilSize * 2, foilSize * 2, foilThickness);
    const foilMat = new THREE.MeshStandardMaterial({
        color: 0xcaa200,
        emissive: 0x6a5200,
        emissiveIntensity: 0.45,
        transparent: true,
        opacity: 0.28,
        metalness: 0.6,
        roughness: 0.35
    });
    const foil = new THREE.Mesh(foilGeo, foilMat);
    foil.position.set(0, 0, foilZ);
    foil.renderOrder = 3;
    foil.material.depthWrite = false;
    scene.add(foil);

    // 金箔边框轮廓：让“极薄”在课堂上更容易被看出来
    const edges = new THREE.EdgesGeometry(foilGeo);
    const edgeLine = new THREE.LineSegments(
        edges,
        new THREE.LineBasicMaterial({ color: 0xffd700, transparent: true, opacity: 0.28 })
    );
    edgeLine.position.copy(foil.position);
    edgeLine.renderOrder = 4;
    scene.add(edgeLine);

    // ==========================================
    // 3. 粒子发射逻辑 (精准枪口出弹)
    // ==========================================
    const alphas = [];
    const trailLines = [];
    const scatterMarkers = [];

    // 统计/可视化状态（用于教学对比）
    const angleSamples = []; // 单次/多次累计的散射角（度）
    const angleBinCounts = new Array(6).fill(0); // 0-30,30-60,...,150-180 覆盖统计
    const forwardDir = new THREE.Vector3(0, 0, 1);

    // UI 元素
    const elTotal = document.getElementById('total-count');
    const elSmallCount = document.getElementById('small-count');
    const elRare = document.getElementById('rare-count');
    const elBackCount = document.getElementById('back-count');
    const elMean = document.getElementById('mean-angle');
    const elAlive = document.getElementById('alive-count');
    const elHudAlive = document.getElementById('hud-alive-count');
    const elRareThresholdLabel = document.getElementById('rare-threshold-label');
    const elRunCount = document.getElementById('run-count');
    const elRareThreshold = document.getElementById('rare-threshold');
    const pieCanvas = document.getElementById('pie');
    const pieCtx = pieCanvas ? pieCanvas.getContext('2d') : null;

    const elObsAngle = document.getElementById('obs-angle');
    const elObsAngleLabel = document.getElementById('obs-angle-label');
    const elObsCount = document.getElementById('obs-count');
    const elObsRate = document.getElementById('obs-rate');
    const elObsClearMode = document.getElementById('btn-obs-clear-mode');
    const obsScreenCanvas = document.getElementById('obs-screen');
    const obsScreenCtx = obsScreenCanvas ? obsScreenCanvas.getContext('2d') : null;
    const aiText = document.getElementById('ai-text');

    const elStatusMode = document.getElementById('status-mode');
    const elStatusCollect = document.getElementById('status-collect');
    const elCollectProgress = document.getElementById('collect-progress');

    let isContinuousFiring = false; // 连续观察用
    let isCollecting = false; // 采集统计用
    let sceneMode = 'macro'; // 'macro' | 'atom'
    let collectingTarget = 0;
    let collectingEmitted = 0;

    let observationAngleDeg = 0;
    const observationWindowHalfWidthDeg = 12;
    const observationScreenMarks = [];
    const observationRecentHitTimes = [];
    const allObservationHits = [];
    let observationWindowCollected = 0;
    let observationDisplayMode = 'realtime'; // 'realtime' | 'persistent'
    const OBS_REALTIME_TRAIL_MS = 900; // 与左侧荧光闪烁寿命保持一致

    let totalCount = 0;
    let rareCount = 0;
    let lastHistDrawAt = 0;
    let lastAIHintAt = 0;

    function getRareThresholdDeg() {
        if (!elRareThreshold) return 10;
        const v = Number(elRareThreshold.value);
        return Number.isFinite(v) ? Math.max(1, Math.min(89, v)) : 10;
    }

    function resetStatistics() {
        totalCount = 0;
        rareCount = 0;
        angleSamples.length = 0;
        angleBinCounts.fill(0);
        collectingEmitted = 0;
        updateStatsUI();
        drawPieChart();
    }

    function resetAllScene() {
        // 停止发射
        isContinuousFiring = false;
        isCollecting = false;
        collectingTarget = 0;
        collectingEmitted = 0;
        allObservationHits.length = 0;
        clearObservationScreenMarks();

        // 移除粒子
        for (const a of alphas) scene.remove(a);
        alphas.length = 0;

        // 移除轨迹
        for (const line of trailLines) scene.remove(line);
        trailLines.length = 0;

        // 移除散射标记
        for (const m of scatterMarkers) scene.remove(m);
        scatterMarkers.length = 0;

        // 移除探测闪光
        if (Array.isArray(detectorFlashes)) {
            for (const f of detectorFlashes) scene.remove(f);
            detectorFlashes.length = 0;
        }

        resetStatistics();
    }

    function updateStatsUI() {
        const rareTh = getRareThresholdDeg();
        // 始终用 angleSamples 重新计算，保证“阈值输入改变后”计数立即一致。
        totalCount = angleSamples.length;
        rareCount = angleSamples.reduce((cnt, ang) => cnt + (ang >= rareTh ? 1 : 0), 0);

        if (elTotal) elTotal.textContent = String(totalCount);
        if (elRare) elRare.textContent = String(rareCount);

        const mean = angleSamples.length
            ? angleSamples.reduce((s, x) => s + x, 0) / angleSamples.length
            : 0;
        if (elMean) elMean.textContent = mean.toFixed(1);

        // 依据实验描述：小角度(<3°)与背散(>90°)用于课堂对比
        const smallTh = 3;
        const backTh = 90;
        if (elSmallCount) {
            const smallCnt = angleSamples.reduce((cnt, ang) => cnt + (ang <= smallTh ? 1 : 0), 0);
            elSmallCount.textContent = String(smallCnt);
        }
        if (elBackCount) {
            const backCnt = angleSamples.reduce((cnt, ang) => cnt + (ang >= backTh ? 1 : 0), 0);
            elBackCount.textContent = String(backCnt);
        }

        if (elRareThresholdLabel) elRareThresholdLabel.textContent = String(rareTh);
    }

    function updateAliveUI() {
        const v = String(alphas.length);
        if (elAlive) elAlive.textContent = v;
        if (elHudAlive) elHudAlive.textContent = v;
    }

    function updateStatusUI() {
        if (elStatusMode) {
            const modeText = isCollecting
                ? '模式：采集中'
                : (isContinuousFiring ? '模式：连续发射' : '模式：停止');
            elStatusMode.textContent = modeText;
        }
        if (elStatusCollect) {
            elStatusCollect.textContent = isCollecting
                ? `采集：${collectingEmitted}/${collectingTarget}`
                : '采集：—';
        }
        if (elCollectProgress) {
            const pct = isCollecting && collectingTarget > 0
                ? Math.max(0, Math.min(100, (collectingEmitted / collectingTarget) * 100))
                : 0;
            elCollectProgress.style.width = `${pct}%`;
        }
    }

    function normalizeAngleDeg(a) {
        let v = a % 360;
        if (v < 0) v += 360;
        return v;
    }

    function angularDiffDeg(a, b) {
        const d = Math.abs(normalizeAngleDeg(a) - normalizeAngleDeg(b));
        return Math.min(d, 360 - d);
    }

    function signedAngularDiffDeg(targetDeg, centerDeg) {
        const t = normalizeAngleDeg(targetDeg);
        const c = normalizeAngleDeg(centerDeg);
        let d = t - c;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    function clearObservationScreenMarks() {
        observationScreenMarks.length = 0;
        observationRecentHitTimes.length = 0;
        observationWindowCollected = 0;

        if (Array.isArray(detectorFlashes)) {
            for (const f of detectorFlashes) scene.remove(f);
            detectorFlashes.length = 0;
        }
    }

    function updateObservationClearModeUI() {
        if (!elObsClearMode) return;
        elObsClearMode.textContent = observationDisplayMode === 'realtime'
            ? '当前模式：实时变化'
            : '当前模式：保留痕迹';
    }

    function setAIMessage(text) {
        if (!aiText) return;
        aiText.innerHTML = text;
    }

    function updateAITeachingHint() {
        if (!aiText) return;

        const recentRate = observationRecentHitTimes.length / 2;
        const rareTh = getRareThresholdDeg();
        const rareNow = angleSamples.reduce((cnt, a) => cnt + (a >= rareTh ? 1 : 0), 0);
        const backNow = angleSamples.reduce((cnt, a) => cnt + (a >= 90 ? 1 : 0), 0);

        if (sceneMode === 'atom') {
            setAIMessage('你正在【微观单原子】视角：重点看粒子与核附近相互作用造成的偏转路径。切回宏观可观察荧光屏计数分布。');
            return;
        }

        if (isCollecting) {
            setAIMessage(`正在采集数据：<strong>${collectingEmitted}/${collectingTarget}</strong>。建议观察右侧荧光屏与饼图占比变化。`);
            return;
        }

        if (backNow > 0) {
            setAIMessage(`已出现 <strong>${backNow}</strong> 次背散事件（θ≥90°）。这说明极少数 α 粒子会在近核强库仑作用下发生大角偏转。`);
            return;
        }

        if (rareNow > 0 && totalCount > 0) {
            const frac = (rareNow / totalCount * 100).toFixed(1);
            setAIMessage(`当前统计中，大角事件（θ≥${rareTh}°）为 <strong>${rareNow}</strong> 次，占比 <strong>${frac}%</strong>。符合“多数小角、少数大角”散射特征。`);
            return;
        }

        if (recentRate > 1.2) {
            setAIMessage(`观察窗角度 <strong>${Math.round(observationAngleDeg)}°</strong>，当前速率约 <strong>${recentRate.toFixed(2)} 粒子/s</strong>。可继续旋转观察窗比较不同方向计数差异。`);
            return;
        }

        setAIMessage('建议操作：先连续发射观察小角主导，再旋转观察窗查看计数变化，最后进行一次采集并解读饼图占比。');
    }

    function rebuildObservationFromAllHits(now = performance.now()) {
        // 保留函数占位（兼容已有调用），当前不再从历史命中重建，
        // 避免“转到新角度时仍显示旧角度历史粒子”的错觉。
        void now;
    }

    function setObservationAngle(valueDeg) {
        const next = normalizeAngleDeg(valueDeg);
        const changed = Math.abs(next - observationAngleDeg) > 1e-6;
        observationAngleDeg = next;
        if (elObsAngleLabel) elObsAngleLabel.textContent = `${Math.round(observationAngleDeg)}°`;
        updateObservationWindowVisual();
        if (!changed) return;

        // 两种模式在切换角度时都清屏：
        // - 实时变化：只看当前角度“此刻新打到”的粒子
        // - 保留痕迹：在当前角度下持续累积，直到再次转动角度
        clearObservationScreenMarks();
    }

    function isInObservationWindow(hitAngleDeg) {
        return angularDiffDeg(hitAngleDeg, observationAngleDeg) <= observationWindowHalfWidthDeg;
    }

    function isAngleInArcDeg(angleDeg, startDeg, lengthDeg) {
        const a = normalizeAngleDeg(angleDeg);
        const s = normalizeAngleDeg(startDeg);
        const e = normalizeAngleDeg(startDeg + lengthDeg);
        if (lengthDeg >= 360) return true;
        if (s <= e) return a >= s && a <= e;
        return a >= s || a <= e;
    }

    function isDetectorVisibleHit(pos) {
        if (Math.abs(pos.y) > SCREEN_HEIGHT / 2) return false;
        const arcAngleDeg = normalizeAngleDeg(Math.atan2(pos.z, pos.x) * 180 / Math.PI); // 圆柱几何角度基准：+X
        const startDeg = normalizeAngleDeg(SCREEN_THETA_START * 180 / Math.PI);
        const lengthDeg = SCREEN_THETA_LENGTH * 180 / Math.PI;
        return isAngleInArcDeg(arcAngleDeg, startDeg, lengthDeg);
    }

    function recordObservationHit(hitAngleDeg, hitThetaDeg, flashPos = null) {
        const now = performance.now();
        const normalizedHitAngle = normalizeAngleDeg(hitAngleDeg);

        let mappedX = Math.random() * (obsScreenCanvas ? obsScreenCanvas.width : 320);
        let mappedY = Math.random() * (obsScreenCanvas ? obsScreenCanvas.height : 200);

        if (obsScreenCanvas) {
            const halfWin = observationWindowHalfWidthDeg;
            const localDeg = THREE.MathUtils.clamp(
                signedAngularDiffDeg(normalizedHitAngle, observationAngleDeg),
                -halfWin,
                halfWin
            );
            const nx = (localDeg + halfWin) / (halfWin * 2);
            mappedX = 14 + nx * Math.max(1, obsScreenCanvas.width - 28);

            const yNorm = flashPos
                ? THREE.MathUtils.clamp((flashPos.y + SCREEN_HEIGHT / 2) / SCREEN_HEIGHT, 0, 1)
                : Math.random();
            mappedY = 12 + (1 - yNorm) * Math.max(1, obsScreenCanvas.height - 24);
        }

        const mark = {
            t: now,
            angle: normalizedHitAngle,
            x: mappedX,
            y: mappedY,
            brightness: hitThetaDeg >= 90 ? 1 : (hitThetaDeg >= getRareThresholdDeg() ? 0.82 : 0.62),
            radius: hitThetaDeg >= 90 ? 3.4 : (hitThetaDeg >= getRareThresholdDeg() ? 2.8 : 2.1)
        };

        allObservationHits.push(mark);
        if (allObservationHits.length > 7000) {
            allObservationHits.splice(0, allObservationHits.length - 7000);
        }

        if (!isInObservationWindow(normalizedHitAngle)) return;

        observationWindowCollected += 1;
        observationScreenMarks.push(mark);
        observationRecentHitTimes.push(now);

        if (observationScreenMarks.length > 2800) {
            observationScreenMarks.splice(0, observationScreenMarks.length - 2800);
        }
    }

    function getThemeColorValue(varName, fallback = '#ffffff') {
        const value = getComputedStyle(document.documentElement)
            .getPropertyValue(varName)
            .trim();
        return value || fallback;
    }

    function toRgba(colorText, alpha = 1) {
        const color = (colorText || '').trim();
        if (color.startsWith('#')) {
            let hex = color.slice(1);
            if (hex.length === 3) {
                hex = hex.split('').map((c) => c + c).join('');
            }
            if (hex.length === 6) {
                const r = parseInt(hex.slice(0, 2), 16);
                const g = parseInt(hex.slice(2, 4), 16);
                const b = parseInt(hex.slice(4, 6), 16);
                return `rgba(${r}, ${g}, ${b}, ${alpha})`;
            }
        }
        return color;
    }

    function updateObservationPanel(now) {
        if (observationDisplayMode === 'realtime') {
            while (observationRecentHitTimes.length > 0 && now - observationRecentHitTimes[0] > 900) {
                observationRecentHitTimes.shift();
            }
            while (observationScreenMarks.length > 0 && now - observationScreenMarks[0].t > 900) {
                observationScreenMarks.shift();
            }
            observationWindowCollected = observationScreenMarks.length;
        } else {
            while (observationRecentHitTimes.length > 0 && now - observationRecentHitTimes[0] > 2000) {
                observationRecentHitTimes.shift();
            }
        }

        if (elObsCount) elObsCount.textContent = String(observationWindowCollected);
        if (elObsRate) elObsRate.textContent = `${(observationRecentHitTimes.length / 2).toFixed(2)} 粒子/s`;

        if (!obsScreenCtx || !obsScreenCanvas) return;
        const w = obsScreenCanvas.width;
        const h = obsScreenCanvas.height;

        const panelBg = getThemeColorValue('--theme-panel-bg', '#000000');
        const panelBorder = getThemeColorValue('--theme-panel-border', '#ffffff');
        const secondary = getThemeColorValue('--theme-secondary', '#00e5ff');

        obsScreenCtx.fillStyle = toRgba(panelBg, 0.2);
        obsScreenCtx.fillRect(0, 0, w, h);

        obsScreenCtx.strokeStyle = toRgba(panelBorder, 1);
        obsScreenCtx.lineWidth = 1;
        obsScreenCtx.strokeRect(6, 6, w - 12, h - 12);

        for (const mark of observationScreenMarks) {
            obsScreenCtx.beginPath();
            obsScreenCtx.arc(mark.x, mark.y, mark.radius, 0, Math.PI * 2);
            obsScreenCtx.fillStyle = toRgba(secondary, Math.min(1, 0.26 + mark.brightness * 0.62));
            obsScreenCtx.fill();
        }
    }

    function drawPieChart() {
        if (!pieCtx || !pieCanvas) return;

        // 允许 CSS 放大后保持清晰度：用元素显示尺寸动态映射内部像素尺寸
        const dpr = window.devicePixelRatio || 1;
        const displayW = Math.max(120, Math.round(pieCanvas.clientWidth || 220));
        const displayH = Math.max(120, Math.round(pieCanvas.clientHeight || displayW));
        const targetW = Math.round(displayW * dpr);
        const targetH = Math.round(displayH * dpr);
        if (pieCanvas.width !== targetW || pieCanvas.height !== targetH) {
            pieCanvas.width = targetW;
            pieCanvas.height = targetH;
        }

        const w = pieCanvas.width;
        const h = pieCanvas.height;
        const cx = w / 2;
        const cy = h / 2;
        const r = Math.min(w, h) * 0.31;

        pieCtx.clearRect(0, 0, w, h);

        // 2 个扇区：0-3, 3-180
        const bins = [0, 0];
        const ranges = [
            { lo: 0, hi: 3 },
            { lo: 3, hi: 180 }
        ];

        for (const ang of angleSamples) {
            if (ang < 0 || ang > 180) continue;
            if (ang < 3) bins[0] += 1;
            else bins[1] += 1;
        }

        const total = bins.reduce((s, x) => s + x, 0);
        const colors = [
            'rgba(255,255,255,0.9)',
            'rgba(255,110,80,0.95)'
        ];

        // 无数据时显示占位环
        if (total === 0) {
            pieCtx.beginPath();
            pieCtx.arc(cx, cy, r, 0, Math.PI * 2);
            pieCtx.strokeStyle = 'rgba(255,255,255,0.25)';
            pieCtx.lineWidth = 2 * dpr;
            pieCtx.stroke();

            pieCtx.fillStyle = 'rgba(255,255,255,0.6)';
            pieCtx.font = `${12 * dpr}px sans-serif`;
            pieCtx.textAlign = 'center';
            pieCtx.fillText('暂无采样数据', cx, cy + 4 * dpr);
            return;
        }

        let start = -Math.PI / 2;
        for (let i = 0; i < bins.length; i++) {
            const v = bins[i];
            if (v <= 0) continue;

            const frac = v / total;
            const end = start + frac * Math.PI * 2;
            pieCtx.beginPath();
            pieCtx.moveTo(cx, cy);
            pieCtx.arc(cx, cy, r, start, end);
            pieCtx.closePath();
            pieCtx.fillStyle = colors[i % colors.length];
            pieCtx.fill();

            // 扇区边界
            pieCtx.strokeStyle = 'rgba(0,0,0,0.35)';
            pieCtx.lineWidth = 1 * dpr;
            pieCtx.stroke();

            // 标签：角度范围 + 百分比
            const mid = (start + end) / 2;
            const lr = r + 18 * dpr;
            const lx = cx + Math.cos(mid) * lr;
            const ly = cy + Math.sin(mid) * lr;
            const pct = (frac * 100).toFixed(1);
            const range = ranges[i];
            const label = `${range.lo}°-${range.hi}°  ${pct}%`;

            pieCtx.fillStyle = 'rgba(255,255,255,0.9)';
            pieCtx.font = `${11 * dpr}px sans-serif`;
            pieCtx.textAlign = (Math.cos(mid) >= 0) ? 'left' : 'right';
            pieCtx.fillText(label, lx, ly);

            start = end;
        }

        // 中心空白圆，做成环形图，便于阅读
        pieCtx.beginPath();
        pieCtx.arc(cx, cy, r * 0.46, 0, Math.PI * 2);
        pieCtx.fillStyle = 'rgba(0,0,0,0.86)';
        pieCtx.fill();

        pieCtx.fillStyle = 'rgba(255,255,255,0.92)';
        pieCtx.font = `bold ${13 * dpr}px sans-serif`;
        pieCtx.textAlign = 'center';
        pieCtx.fillText('总计', cx, cy - 2 * dpr);
        pieCtx.font = `bold ${14 * dpr}px sans-serif`;
        pieCtx.fillText(String(total), cx, cy + 16 * dpr);
    }

    function startContinuous() {
        isContinuousFiring = !isContinuousFiring;
        if (isContinuousFiring) {
            isCollecting = false;
            collectingTarget = 0;
            collectingEmitted = 0;
            setAIMessage('已开启连续发射：请先观察左侧荧光屏小角散射最密集的区域，再旋转右侧观察窗验证方向变化。');
        } else {
            setAIMessage('已暂停连续发射。你可以切换观察窗角度或开始一键采集，进一步分析角度分布。');
        }
    }

    function startCollecting() {
        const n = elRunCount ? Number(elRunCount.value) : 200;
        collectingTarget = Number.isFinite(n) ? Math.max(10, Math.min(5000, n)) : 200;
        collectingEmitted = 0;
        isContinuousFiring = false;
        isCollecting = true;

        setAIMessage(`开始一键采集：目标 <strong>${collectingTarget}</strong> 个粒子。请关注饼图中小角与大角事件占比变化。`);

        // 采集前清空场景内已有粒子，保证“统计的 N 粒子”只来自本轮发射
        for (const a of alphas) scene.remove(a);
        alphas.length = 0;

        // 采集前重置统计，避免课堂上“混在一起”
        resetStatistics();
        // 清掉轨迹，让学生专注“这一次采集对应的饼图统计”
        for (const line of trailLines) scene.remove(line);
        trailLines.length = 0;

        // 清掉散射标记，保证这一轮采集更聚焦
        for (const m of scatterMarkers) scene.remove(m);
        scatterMarkers.length = 0;
    }

    function fireAlpha() {
        const alpha = new THREE.Mesh(
            new THREE.SphereGeometry(0.55, 16, 16),
            new THREE.MeshBasicMaterial({ color: CONFIG.colors.alpha })
        );
        alpha.renderOrder = 10;
        alpha.material.depthWrite = false;

        const beamRadius = 2.2;
        const r = Math.sqrt(Math.random()) * beamRadius;
        const t = Math.random() * Math.PI * 2;
        alpha.position.set(
            Math.cos(t) * r,
            Math.sin(t) * r,
            CONFIG.emitterZ + 10
        );

        alpha.userData = {
            velocity: new THREE.Vector3(0, 0, CONFIG.particleSpeed),
            path: [alpha.position.clone()],
            scatterMarked: false,
            scatterIndex: -1,
            deflected: false,
            speed: CONFIG.particleSpeed,
            thetaDeg: 0,
            impactB: 0,
            alive: true,
            mode: 'macro'
        };

        lastMuzzlePulseAt = performance.now();
        scene.add(alpha);
        alphas.push(alpha);
    }

    function fireAtomAlpha() {
        const alpha = new THREE.Mesh(
            new THREE.SphereGeometry(0.22, 10, 10),
            new THREE.MeshBasicMaterial({ color: 0xe8f7ff })
        );
        alpha.renderOrder = 11;
        alpha.material.depthWrite = false;

        const beamRadius = 8.5;
        const r = Math.sqrt(Math.random()) * beamRadius;
        const t = Math.random() * Math.PI * 2;
        alpha.position.set(
            Math.cos(t) * r,
            Math.sin(t) * r,
            -95
        );

        alpha.userData = {
            velocity: new THREE.Vector3(0, 0, 0.55),
            path: [alpha.position.clone()],
            scatterMarked: false,
            scatterIndex: -1,
            deflected: false,
            speed: 0.55,
            thetaDeg: 0,
            impactB: 0,
            alive: true,
            mode: 'atom'
        };

        scene.add(alpha);
        alphas.push(alpha);
    }


    // ==========================================
    // 4. 物理更新与统计采样
    // ==========================================
    // 采用卢瑟福偏转角关系：cot(θ/2) = (m v^2 b) / K
    // 等价写法：θ = 2 * atan(K / (m v^2 b))
    // 其中 K = k * qα * QAu（在仿真中使用缩放常量映射到场景单位）。
    const SMALL_TH_DEG = 3;
    const BACK_TH_DEG = 90;
    const DEG2RAD = Math.PI / 180;

    // 物理常量（用于教学可读性）
    const Z_AU = 79;
    const Q_ALPHA = 2;
    const Q_AU = Z_AU;

    // 仿真缩放：把真实物理量映射到 three.js 场景尺度，保证现象可见且稳定
    const MASS_SIM = 1.0;
    const COULOMB_SCALE = 0.0027; // 略增散射强度：增强可见性但保持自然分布

    function computeRutherfordThetaDeg(speed, impactB) {
        // 避免 b=0 时除零；b 越小，theta 越接近 180°
        const b = Math.max(0.015, impactB);
        const K = COULOMB_SCALE * CONFIG.k * Q_ALPHA * Q_AU;
        const mv2 = MASS_SIM * Math.max(0.001, speed * speed);
        const cotHalfTheta = (mv2 * b) / K;
        const thetaRad = 2 * Math.atan(1 / cotHalfTheta);
        return THREE.MathUtils.clamp(thetaRad / DEG2RAD, 0, 179.5);
    }

    function getClosestNucleusAtXY(x, y, mode = 'macro') {
        const source = mode === 'atom' ? atomNuclei : nuclei;
        let best = null;
        let bestD2 = Infinity;
        for (const n of source) {
            const dx = n.position.x - x;
            const dy = n.position.y - y;
            const d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = n;
            }
        }
        return best ? best.position.clone() : new THREE.Vector3(x, y, FOIL_Z);
    }

    function updatePhysics() {
        for (let i = alphas.length - 1; i >= 0; i--) {
            const a = alphas[i];
            a.position.add(a.userData.velocity);

            // 轨迹点采样：限制点数，避免内存和绘制太慢
            if (a.userData.path.length < 120) {
                a.userData.path.push(a.position.clone());
            }

            // 穿过金箔平面时触发一次散射：基于冲量参数 b 计算卢瑟福偏转角
            if (!a.userData.deflected && a.position.z >= FOIL_Z) {
                a.userData.deflected = true;

                const hitX = a.position.x;
                const hitY = a.position.y;
                const mode = a.userData.mode || 'macro';
                const nucleusPos = getClosestNucleusAtXY(hitX, hitY, mode);

                const dx = hitX - nucleusPos.x;
                const dy = hitY - nucleusPos.y;
                const impactB = Math.hypot(dx, dy);
                a.userData.impactB = impactB;

                const speed = Math.max(0.001, a.userData.velocity.length());
                const vIn = a.userData.velocity.clone().normalize();

                let thetaDeg = 0;
                let newDir = vIn.clone();

                if (mode === 'atom') {
                    // 单原子穿透：应包含 <90° 的大量散射，>90° 仅少量背散
                    const atomInfluence = 2.35;
                    if (impactB > atomInfluence) {
                        thetaDeg = THREE.MathUtils.randFloat(0.0, 1.0);
                    } else {
                        const normalizedB = impactB / Math.max(0.001, atomInfluence);
                        const baseTheta = computeRutherfordThetaDeg(speed, impactB) * 0.82;

                        // 靠近影响区边缘给更多小角，避免“几乎全反弹”的观感
                        const edgeBlend = THREE.MathUtils.clamp((normalizedB - 0.25) / 0.75, 0, 1);
                        const smallTheta = THREE.MathUtils.randFloat(0, 22) * (0.5 + 0.5 * edgeBlend);
                        thetaDeg = THREE.MathUtils.lerp(baseTheta, smallTheta, edgeBlend * 0.72);

                        // 少量背散（>90°）事件：仅在非常靠近核时以低概率触发
                        const backProb = 0.07 * Math.pow(1 - normalizedB, 1.8);
                        if (Math.random() < backProb) {
                            thetaDeg = THREE.MathUtils.randFloat(95, 168);
                        } else {
                            thetaDeg = Math.min(thetaDeg, 88);
                        }

                        thetaDeg = THREE.MathUtils.clamp(thetaDeg, 0, 176);
                    }
                } else if (impactB > CONFIG.influenceRadius) {
                    // 绝大多数事件落在 0-3°，用于课堂演示“原子内部大多是空的”
                    thetaDeg = THREE.MathUtils.randFloat(0.03, 1.9);
                } else {
                    const edgeBlend = THREE.MathUtils.clamp((impactB - CONFIG.influenceRadius * 0.28) / (CONFIG.influenceRadius * 0.72), 0, 1);
                    const coreFactor = 1 - edgeBlend; // 越靠核心越接近 1

                    // 常规散射仍以小角为主，主体限制在 3°以内
                    const baseTheta = computeRutherfordThetaDeg(speed, impactB) * 0.2;
                    const edgeSmall = THREE.MathUtils.randFloat(0.04, 2.4) * (0.6 + 0.4 * edgeBlend);
                    thetaDeg = THREE.MathUtils.lerp(baseTheta, edgeSmall, 0.72);
                    thetaDeg = THREE.MathUtils.clamp(thetaDeg, 0.02, 2.98);

                    // 核附近额外抬高少量大角概率（再细致下调）
                    const mediumProbCore = 0.0022 * Math.pow(coreFactor, 2.0);
                    const largeProbCore = 0.00085 * Math.pow(coreFactor, 2.2);
                    const rCore = Math.random();
                    if (rCore < largeProbCore) {
                        thetaDeg = THREE.MathUtils.randFloat(25, 145);
                    } else if (rCore < largeProbCore + mediumProbCore) {
                        thetaDeg = THREE.MathUtils.randFloat(3.2, 20);
                    }

                    thetaDeg = THREE.MathUtils.clamp(thetaDeg, 0, 178);
                }

                // 全局兜底：保证在有限采样下也能看到少量 >3° 事件（避免饼图长期 100%）
                if (thetaDeg < 3) {
                    const rTail = Math.random();
                    if (rTail < 0.00035) {
                        thetaDeg = THREE.MathUtils.randFloat(25, 110);
                    } else if (rTail < 0.0021) {
                        thetaDeg = THREE.MathUtils.randFloat(3.1, 12);
                    }
                }

                const thetaRad = thetaDeg * DEG2RAD;
                const helper = Math.abs(vIn.y) < 0.95
                    ? new THREE.Vector3(0, 1, 0)
                    : new THREE.Vector3(1, 0, 0);
                const e1 = new THREE.Vector3().crossVectors(vIn, helper).normalize();
                const e2 = new THREE.Vector3().crossVectors(vIn, e1).normalize();

                const phi = Math.random() * Math.PI * 2;
                newDir = vIn.clone().multiplyScalar(Math.cos(thetaRad))
                    .add(e1.multiplyScalar(Math.sin(thetaRad) * Math.cos(phi)))
                    .add(e2.multiplyScalar(Math.sin(thetaRad) * Math.sin(phi)))
                    .normalize();

                a.userData.velocity.copy(newDir.multiplyScalar(speed));
                a.userData.thetaDeg = thetaDeg;
                a.userData.scatterIndex = a.userData.path.length - 1;

                const isBackNow = thetaDeg >= BACK_TH_DEG;
                const isRareNow = thetaDeg >= getRareThresholdDeg();

                if (mode === 'atom') {
                    // 原子场景：粒子撞核后不改变颜色与大小，且不显示碰撞点标记
                    a.material.color.setHex(0xe8f7ff);
                    a.scale.set(1.0, 1.0, 1.0);
                } else {
                    if (isBackNow) {
                        a.material.color.setHex(0xff3300);
                        a.scale.set(2.25, 2.25, 2.25);
                    } else if (isRareNow) {
                        a.material.color.setHex(CONFIG.colors.scatter);
                        a.scale.set(1.78, 1.78, 1.78);
                    } else {
                        a.material.color.setHex(CONFIG.colors.alpha);
                        a.scale.set(1.0, 1.0, 1.0);
                    }

                    // 宏观场景也关闭散射点标记，避免核附近出现额外亮斑
                }
            }

            const thetaDeg = a.userData.thetaDeg || 0;
            const isRare = thetaDeg >= getRareThresholdDeg();

            // 粒子打到背后的屏幕或飞出太远时销毁
            // 放宽销毁下界：粒子从 Z=-190 左右出射，若 -140 过近会导致“刚生成就立刻被销毁”
            if (a.position.z < -240 || a.position.z > 160 || Math.abs(a.position.x) > 150) {
                const angle = a.userData.velocity.angleTo(forwardDir) * 180 / Math.PI;

                if (sceneMode === 'atom') {
                    // 单原子穿透场景：重点看轨迹，不进入宏观统计图
                    if (angle >= 2) drawTrail(a.userData.path, angle, 'all', a.userData.scatterIndex);
                } else {
                    const rareTh = getRareThresholdDeg();
                    angleSamples.push(angle);
                    const binIdx = Math.min(5, Math.floor(Math.max(0, Math.min(179.999, angle)) / 30));
                    angleBinCounts[binIdx] += 1;
                    totalCount = angleSamples.length;

                    const isRareHit = angle >= rareTh;
                    const backTh = 90;
                    const dir = a.userData.velocity.clone().normalize();
                    const flashRadius = SCREEN_RADIUS - 1.8;
                    const flashPos = dir.clone().multiplyScalar(flashRadius);

                    if (isDetectorVisibleHit(flashPos)) {
                        const hitAngleDeg = normalizeAngleDeg(Math.atan2(dir.x, dir.z) * 180 / Math.PI);
                        recordObservationHit(hitAngleDeg, angle, flashPos);

                        let flashColor = CONFIG.colors.alpha;
                        let flashSize = 1.0;
                        if (angle >= backTh) {
                            flashColor = 0xff3300;
                            flashSize = 1.8;
                        } else if (isRareHit) {
                            flashColor = CONFIG.colors.scatter;
                            flashSize = 1.4;
                        }
                        addDetectorFlash(flashPos, flashColor, flashSize, 900);
                    }

                    // 调试：取消“仅稀有角画轨迹”的筛选，避免视觉上误判最小角过大
                    drawTrail(a.userData.path, angle, 'all', a.userData.scatterIndex);

                    if (totalCount % 4 === 0) updateStatsUI();

                    if (isCollecting) {
                        collectingEmitted += 1;
                        if (totalCount % 10 === 0) {
                            drawPieChart();
                        }

                        if (collectingEmitted >= collectingTarget) {
                            isCollecting = false;
                            drawPieChart();
                            const aiText = document.getElementById('ai-text');
                            if (aiText) {
                                aiText.textContent =
                                    `本轮采集完成：可通过饼图查看 0°-3° 与 3°-180° 两类事件占比，小角散射通常占主导。`;
                            }
                        }
                    }
                }

                scene.remove(a);
                alphas.splice(i, 1);
            }
        }
    }
    // --- 交互 ---
    const btnFire = document.getElementById('btn-fire');
    const btnCollect = document.getElementById('btn-collect');
    const btnView = document.getElementById('btn-view');
    const btnClear = document.getElementById('btn-clear');
    const btnReset = document.getElementById('btn-reset');
    const chkShowAllTrails = document.getElementById('chk-show-all-trails');

    function clearAllParticlesAndTrails() {
        for (const a of alphas) scene.remove(a);
        alphas.length = 0;
        for (const line of trailLines) scene.remove(line);
        trailLines.length = 0;
        for (const m of scatterMarkers) scene.remove(m);
        scatterMarkers.length = 0;
        if (Array.isArray(detectorFlashes)) {
            for (const f of detectorFlashes) scene.remove(f);
            detectorFlashes.length = 0;
        }
    }

    function setMacroOnlyView() {
        // 宏观模式
        sceneMode = 'macro';
        clearAllParticlesAndTrails();

        isContinuousFiring = false;
        isCollecting = false;
        collectingTarget = 0;
        collectingEmitted = 0;
        resetStatistics();

        if (atomGroup) atomGroup.visible = false;
        if (leadBoxGroup) leadBoxGroup.visible = true;
        if (beam) beam.visible = isContinuousFiring || isCollecting;
        if (detector) detector.visible = true;
        if (detectorEdgeLine) detectorEdgeLine.visible = true;
        if (detectorTopRim) detectorTopRim.visible = true;
        if (detectorBottomRim) detectorBottomRim.visible = true;
        if (observationWindowGroup) observationWindowGroup.visible = true;
        if (foil) foil.visible = true;
        if (edgeLine) edgeLine.visible = true;

        camera.position.set(68, 55, 150);
        controls.target.set(0, 0, -60);
        controls.update();
    }

    function setAtomPassView() {
        // 新增：粒子穿过原子场景
        sceneMode = 'atom';
        clearAllParticlesAndTrails();

        isContinuousFiring = false;
        isCollecting = false;
        collectingTarget = 0;
        collectingEmitted = 0;

        if (atomGroup) atomGroup.visible = true;
        if (leadBoxGroup) leadBoxGroup.visible = false;
        if (beam) beam.visible = false;
        if (detector) detector.visible = false;
        if (detectorEdgeLine) detectorEdgeLine.visible = false;
        if (detectorTopRim) detectorTopRim.visible = false;
        if (detectorBottomRim) detectorBottomRim.visible = false;
        if (observationWindowGroup) observationWindowGroup.visible = false;
        if (foil) foil.visible = false;
        if (edgeLine) edgeLine.visible = false;

        camera.position.set(0, 10, 92);
        controls.target.set(0, 0, 0);
        controls.update();
    }

    if (btnFire) btnFire.onclick = () => startContinuous();
    if (btnCollect) btnCollect.onclick = () => {
        if (sceneMode === 'atom') {
            isContinuousFiring = !isContinuousFiring;
            isCollecting = false;
            collectingTarget = 0;
            collectingEmitted = 0;
        } else {
            startCollecting();
        }
    };
    if (btnView) {
        btnView.style.display = '';
        btnView.onclick = () => {
            if (sceneMode === 'macro') setAtomPassView();
            else setMacroOnlyView();
        };
    }
    if (btnClear) {
        btnClear.onclick = () => {
            for (const line of trailLines) scene.remove(line);
            trailLines.length = 0;
            for (const m of scatterMarkers) scene.remove(m);
            scatterMarkers.length = 0;
            if (Array.isArray(detectorFlashes)) {
                for (const f of detectorFlashes) scene.remove(f);
                detectorFlashes.length = 0;
            }
            // 只清轨迹，不重置统计：便于课堂“保留饼图，换一批轨迹观察”
        };
    }
    if (btnReset) btnReset.onclick = () => resetAllScene();

    // 初始化显示：宏观视角
    setMacroOnlyView();
    setObservationAngle(0);
    updateStatsUI();
    drawPieChart();
    updateObservationPanel(performance.now());

    // 输入变化即时反馈（若对应输入不存在，则不会绑定）
    if (elRareThreshold) {
        elRareThreshold.addEventListener('input', () => {
            updateStatsUI();
            drawPieChart();
        });
    }

    if (elObsAngle) {
        elObsAngle.addEventListener('input', (e) => {
            const val = Number(e.target.value);
            setObservationAngle(Number.isFinite(val) ? val : 0);
            setAIMessage(`观察窗已转到 <strong>${Math.round(observationAngleDeg)}°</strong>。请对比左右荧光屏命中区域是否一致。`);
        });
    }

    if (elObsClearMode) {
        elObsClearMode.addEventListener('click', () => {
            observationDisplayMode = observationDisplayMode === 'realtime' ? 'persistent' : 'realtime';
            updateObservationClearModeUI();
            clearObservationScreenMarks();
            if (observationDisplayMode === 'realtime') {
                setAIMessage('已切换到<strong>实时变化</strong>：右侧荧光屏会随观察窗角度即时变化。');
            } else {
                setAIMessage('已切换到<strong>保留痕迹</strong>：当前角度命中会持续累积，便于定点讲解。');
            }
        });
    }
    updateObservationClearModeUI();

    // --- 渲染 ---
    const composer = new EffectComposer(renderer);
    composer.addPass(new RenderPass(scene, camera));
    composer.addPass(new UnrealBloomPass(new THREE.Vector2(window.innerWidth, window.innerHeight), 0.8, 0.4, 0.8));

    const handleResize = () => {
        if (isDisposed) return;
        const w = window.innerWidth;
        const h = window.innerHeight;
        renderer.setSize(w, h);
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        composer.setSize(w, h);
        drawPieChart();
    };
    window.addEventListener('resize', handleResize);

    // 渲染循环：按帧数控制发射密度（避免因机器性能不同导致采样差太多）
    const EMIT_PER_FRAME = 2; // 宏观实验发射速率
    const ATOM_EMIT_INTERVAL_FRAMES = 3; // 原子场景每 3 帧发射一次
    let atomEmitFrameCounter = 0;

    const MAX_TRAILS_ON_SCREEN = 240; // 轨迹线数量上限，避免卡顿
    const MAX_SCATTER_MARKERS_ON_SCREEN = 160;

    function addScatterMarker(position, color = CONFIG.colors.scatter, size = 0.35) {
        // 一个“散射发生”的瞬间标记：让学生直观看到偏转来自核附近相互作用
        const marker = new THREE.Mesh(
            new THREE.SphereGeometry(size, 10, 10),
            new THREE.MeshBasicMaterial({ color })
        );
        marker.position.copy(position);
        scene.add(marker);
        scatterMarkers.push(marker);

        // 自动清理：不让画面越来越乱
        trackedSetTimeout(() => {
            scene.remove(marker);
        }, 2500);

        if (scatterMarkers.length > MAX_SCATTER_MARKERS_ON_SCREEN) {
            const old = scatterMarkers.shift();
            if (old) scene.remove(old);
        }
    }

    function decimatePoints(points, target = 60) {
        if (points.length <= target) return points;
        const step = Math.ceil(points.length / target);
        const out = [];
        for (let i = 0; i < points.length; i += step) out.push(points[i]);
        // 确保末端也保留
        if (out.length && out[out.length - 1] !== points[points.length - 1]) {
            out.push(points[points.length - 1]);
        }
        return out;
    }

    function drawTrail(points, angle, kind = 'all', scatterIndex = -1) {
        if (points.length < 2) return;

        const isRare = angle >= getRareThresholdDeg();
        const isBack = angle >= 90;
        const decPts = decimatePoints(points);
        if (!decPts || decPts.length < 2) return;

        const createdLines = [];

        // 原子场景：统一细线，不做前后分段高亮，避免核附近出现“亮斑”视觉
        if (sceneMode === 'atom') {
            const geo = new THREE.BufferGeometry().setFromPoints(decPts);
            const line = new THREE.Line(
                geo,
                new THREE.LineBasicMaterial({
                    color: 0x9dc7e6,
                    transparent: true,
                    opacity: 0.22
                })
            );
            scene.add(line);
            trailLines.push(line);
            createdLines.push(line);
        } else {
            const basePreOpacity = kind === 'rare' || isRare ? 0.22 : 0.12;
            const basePostOpacity = kind === 'rare' || isRare ? 0.52 : 0.32;

            // 如果有散射索引，就把轨迹分成“散射前/散射后”两段上色，更直观
            const hasScatter = Number.isFinite(scatterIndex) && scatterIndex > 0 && scatterIndex < points.length - 1;
            if (hasScatter) {
                const ratio = scatterIndex / (points.length - 1);
                const split = Math.max(1, Math.min(decPts.length - 2, Math.floor(ratio * (decPts.length - 1))));

                const prePts = decPts.slice(0, split + 1);
                const postPts = decPts.slice(split);

                if (prePts.length >= 2) {
                    const geoPre = new THREE.BufferGeometry().setFromPoints(prePts);
                    const linePre = new THREE.Line(
                        geoPre,
                        new THREE.LineBasicMaterial({
                            color: CONFIG.colors.alpha,
                            transparent: true,
                            opacity: basePreOpacity
                        })
                    );
                    scene.add(linePre);
                    trailLines.push(linePre);
                    createdLines.push(linePre);
                }

                const postColor = isBack ? 0xff3300 : (isRare ? CONFIG.colors.scatter : CONFIG.colors.alpha);
                if (postPts.length >= 2) {
                    const geoPost = new THREE.BufferGeometry().setFromPoints(postPts);
                    const linePost = new THREE.Line(
                        geoPost,
                        new THREE.LineBasicMaterial({
                            color: postColor,
                            transparent: true,
                            opacity: basePostOpacity
                        })
                    );
                    scene.add(linePost);
                    trailLines.push(linePost);
                    createdLines.push(linePost);
                }
            } else {
                const color = isBack ? 0xff3300 : (isRare ? CONFIG.colors.scatter : CONFIG.colors.alpha);
                const opacity = isBack || isRare ? 0.45 : 0.18;
                const geo = new THREE.BufferGeometry().setFromPoints(decPts);
                const line = new THREE.Line(
                    geo,
                    new THREE.LineBasicMaterial({ color, transparent: true, opacity })
                );
                scene.add(line);
                trailLines.push(line);
                createdLines.push(line);
            }
        }

        // 控制轨迹线数量，防止长时间运行卡顿
        if (trailLines.length > MAX_TRAILS_ON_SCREEN) {
            const old = trailLines.shift();
            if (old) scene.remove(old);
        }

        const timeout = kind === 'rare' || isRare ? 7000 : 2400;
        trackedSetTimeout(() => {
            for (const l of createdLines) {
                if (l && l.parent) scene.remove(l);
            }
        }, timeout);
    }

    function render() {
        if (isDisposed) return;
        const now = performance.now();

        controls.update();

        // 发射口脉冲闪光：更容易“抓住发射瞬间”
        const pulseAge = now - lastMuzzlePulseAt;
        if (pulseAge >= 0 && pulseAge < 280) {
            const t = 1 - pulseAge / 280;
            const s = 1 + 1.2 * t;
            muzzleGlow.scale.set(s, s, 1);
            if (muzzleGlow.material && muzzleGlow.material.opacity !== undefined) {
                muzzleGlow.material.opacity = 0.95;
            }
        } else {
            muzzleGlow.scale.set(1, 1, 1);
            if (muzzleGlow.material && muzzleGlow.material.opacity !== undefined) {
                muzzleGlow.material.opacity = 0.85;
            }
        }

        // 发射束开关：连续发射/采集中才显示
        if (beam) {
            const on = isContinuousFiring || isCollecting;
            beam.visible = on;
            // 用轻微脉动让“束”更有呼吸感
            if (on) {
                const t = (Math.sin(now / 120) + 1) * 0.5; // 0..1
                beam.scale.set(1, 1, 1);
                beam.material.opacity = 0.14 + t * 0.14;
            } else {
                beam.material.opacity = 0.0;
            }
        }

        if (isContinuousFiring) {
            if (sceneMode === 'atom') {
                atomEmitFrameCounter++;
                if (atomEmitFrameCounter % ATOM_EMIT_INTERVAL_FRAMES === 0) {
                    fireAtomAlpha();
                }
            } else {
                for (let i = 0; i < EMIT_PER_FRAME; i++) fireAlpha();
            }
        } else if (isCollecting) {
            const remaining = collectingTarget - collectingEmitted;
            if (remaining > 0) {
                const emitNow = Math.min(EMIT_PER_FRAME, remaining);
                for (let i = 0; i < emitNow; i++) fireAlpha();
            }
        }

        // 原子场景动态：电子缓慢绕核运动，核子轻微振动，更有“原子感”
        if (sceneMode === 'atom') {
            nucleusGroup.rotation.y += 0.006;
            nucleusGroup.rotation.x += 0.0025;
            nucleusCore.scale.setScalar(1.0 + Math.sin(now * 0.006) * 0.06);

            for (let i = 0; i < atomElectrons.length; i++) {
                const e = atomElectrons[i];
                const d = e.userData;
                d.phase += d.speed;

                const wobble = Math.sin(now * 0.0016 * d.wobbleFreq + i * 0.17) * d.wobbleAmp;
                const r = d.shellRadius + wobble;

                const local = new THREE.Vector3(
                    Math.cos(d.phase) * r,
                    Math.sin(d.phase * (1.0 + d.shellN * 0.07)) * (0.15 + d.shellN * 0.08),
                    Math.sin(d.phase) * r
                );

                local.applyAxisAngle(new THREE.Vector3(1, 0, 0), d.tiltX);
                local.applyAxisAngle(new THREE.Vector3(0, 1, 0), d.tiltY);
                e.position.copy(local);
            }
        }

        updatePhysics();
        updateAliveUI();
        updateStatusUI();
        updateObservationPanel(now);

        if (now - lastAIHintAt > 1200) {
            updateAITeachingHint();
            lastAIHintAt = now;
        }

        // 主视图渲染
        renderer.setScissorTest(false);
        composer.render();

        // 连续模式下也偶尔更新图表（降低刷新成本）
        if (!isCollecting && isContinuousFiring && now - lastHistDrawAt > 800) {
            drawPieChart();
            lastHistDrawAt = now;
        }

        animationFrameId = requestAnimationFrame(render);
    }

    function disposeObject3D(obj) {
        if (!obj) return;

        if (obj.geometry) {
            obj.geometry.dispose?.();
        }

        if (obj.material) {
            if (Array.isArray(obj.material)) {
                obj.material.forEach((m) => m?.dispose?.());
            } else {
                obj.material.dispose?.();
            }
        }
    }

    function disposeSceneGraph(root) {
        if (!root) return;
        root.traverse((obj) => {
            disposeObject3D(obj);
        });
    }

    function disposeAll() {
        if (isDisposed) return;
        isDisposed = true;

        isContinuousFiring = false;
        isCollecting = false;

        if (animationFrameId !== null) {
            cancelAnimationFrame(animationFrameId);
            animationFrameId = null;
        }

        for (const timeoutId of activeTimeoutIds) {
            clearTimeout(timeoutId);
        }
        activeTimeoutIds.clear();

        window.removeEventListener('resize', handleResize);

        clearAllParticlesAndTrails();
        clearObservationScreenMarks();

        disposeSceneGraph(scene);

        controls.dispose?.();
        composer.dispose?.();
        renderer.renderLists?.dispose?.();
        renderer.dispose();

        if (renderer.forceContextLoss) {
            renderer.forceContextLoss();
        }
    }

    window.addEventListener('pagehide', disposeAll, { once: true });
    window.addEventListener('beforeunload', disposeAll, { once: true });

    render();
}
main();