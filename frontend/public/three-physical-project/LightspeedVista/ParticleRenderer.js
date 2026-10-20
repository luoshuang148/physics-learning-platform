import * as THREE from 'three';

// 创建高光晕纹理：中心极亮区域仅占 12%，外围 88% 平滑渐变透明，模拟巨大弥散光晕
function createStarTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 256;      // 提高分辨率，使光晕边缘更平滑
    canvas.height = 256;
    const ctx = canvas.getContext('2d');
    const centerX = canvas.width / 2;
    const centerY = canvas.height / 2;
    const maxRadius = canvas.width / 2;

    // 清除画布
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // 定义核心半径占比（光晕从 12% 半径处开始向外扩散）
    const coreRadiusRatio = 0.12;
    const coreRadius = maxRadius * coreRadiusRatio;
    
    // 1. 绘制核心高亮区域（使用径向渐变，中心白热，边缘快速变暗）
    const coreGradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, coreRadius);
    coreGradient.addColorStop(0, 'rgba(255, 255, 255, 1)');
    coreGradient.addColorStop(0.5, 'rgba(255, 200, 100, 0.9)');
    coreGradient.addColorStop(1, 'rgba(180, 80, 40, 0.8)');
    ctx.beginPath();
    ctx.arc(centerX, centerY, coreRadius, 0, Math.PI * 2);
    ctx.fillStyle = coreGradient;
    ctx.fill();
    
    // 2. 绘制外部光晕区域（从核心边缘到外边缘平滑透明，最终完全透明）
    const glowGradient = ctx.createRadialGradient(centerX, centerY, coreRadius, centerX, centerY, maxRadius);
    glowGradient.addColorStop(0, 'rgba(255, 200, 100, 0.7)');
    glowGradient.addColorStop(0.3, 'rgba(255, 150, 80, 0.4)');
    glowGradient.addColorStop(0.6, 'rgba(255, 100, 50, 0.15)');
    glowGradient.addColorStop(0.9, 'rgba(255, 255, 255, 0.02)');
    glowGradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
    ctx.globalCompositeOperation = 'source-over';
    ctx.beginPath();
    ctx.arc(centerX, centerY, maxRadius, 0, Math.PI * 2);
    ctx.fillStyle = glowGradient;
    ctx.fill();
    
    // 可选：轻微星芒（提升视觉质感，不影响圆形轮廓）
    ctx.strokeStyle = 'rgba(255, 200, 100, 0.15)';
    ctx.lineWidth = 2;
    for (let i = 0; i < 4; i++) {
        const angle = (i * Math.PI * 2 / 4);
        const dx = Math.cos(angle) * maxRadius * 0.9;
        const dy = Math.sin(angle) * maxRadius * 0.9;
        ctx.beginPath();
        ctx.moveTo(centerX, centerY);
        ctx.lineTo(centerX + dx, centerY + dy);
        ctx.stroke();
    }
    
    const texture = new THREE.CanvasTexture(canvas);
    texture.needsUpdate = true;
    return texture;
}

// 顶点着色器：实现光行差 + 光晕尺寸随多普勒因子指数级放大
const vertexShader = `
uniform float uBeta;
attribute float radialDist;
varying float vDopplerFactor;
varying vec3 vColor;
varying float vRadialDist;

void main() {
    vec3 worldPos = position;
    vec3 dir = normalize(worldPos);
    float cosTheta = dir.z;

    float beta = uBeta;
    float gamma = 1.0 / sqrt(1.0 - beta * beta);
    
    float denom = 1.0 - beta * cosTheta;
    vec3 newDir;
    newDir.x = dir.x / (gamma * denom);
    newDir.y = dir.y / (gamma * denom);
    newDir.z = (dir.z - beta) / denom;
    newDir = normalize(newDir);
    
    vDopplerFactor = 1.0 / (gamma * denom);  // 多普勒因子（亮度核心）
    vColor = color;
    vRadialDist = radialDist;
    
    vec3 virtualPos = newDir * radialDist;
    vec4 mvPosition = viewMatrix * vec4(virtualPos, 1.0);
    
    // 基础大小（随距离衰减）- 提高系数让低速时星星更明显
    float baseSize = 550.0 / -mvPosition.z * 0.8;
    
    // 亮度因子：多普勒因子越大，光晕尺寸按指数级放大
    float brightnessFactor = clamp(vDopplerFactor, 0.25, 25.0);
    float sizeMultiplier;
    if (brightnessFactor > 1.0) {
        // 蓝移：亮度每增加 1，尺寸放大 (1 + (factor-1)*12) 倍，最高可放大 80 倍
        sizeMultiplier = 1.0 + (brightnessFactor - 1.0) * 12.0;
        sizeMultiplier = min(sizeMultiplier, 80.0);
    } else {
        // 红移：保持一个最小可见尺寸，避免完全消失
        sizeMultiplier = max(0.6, brightnessFactor);
    }
    
    // 速度感额外增强（可选）
    float speedBoost = 1.0 + uBeta * 0.6;
    
    gl_PointSize = baseSize * sizeMultiplier * speedBoost;
    // 限制最大像素尺寸，避免性能问题和视觉遮挡（蓝移亮星可达 400px）
    gl_PointSize = clamp(gl_PointSize, 3.0, 400.0);  // 最小 3 像素，保证红移星也可见
    
    gl_Position = projectionMatrix * mvPosition;
}
`;

// 片段着色器：增加圆形遮罩，彻底消除方形边缘
const fragmentShader = `
uniform sampler2D uPointTexture;
uniform float uBeta;
varying float vDopplerFactor;
varying vec3 vColor;
varying float vRadialDist;

void main() {
    vec4 texColor = texture2D(uPointTexture, gl_PointCoord);
    
    // 圆形遮罩：计算像素到中心点的距离
    vec2 coord = gl_PointCoord * 2.0 - 1.0;
    float r = length(coord);
    if (r > 1.0) discard;   // 超出圆形区域直接丢弃，消除方形边缘
    
    // 可选：进一步柔化边缘（与纹理 alpha 配合）
    float circleAlpha = 1.0 - smoothstep(0.9, 1.0, r);
    
    float D = vDopplerFactor;
    float shift = D - 1.0;
    
    // 颜色混合（红移/蓝移）
    vec3 baseColor = vec3(1.0, 0.95, 0.85);
    vec3 redShiftColor = vec3(0.7, 0.2, 0.1);
    vec3 blueShiftColor = vec3(0.3, 0.6, 2.0);
    
    float redMix = smoothstep(0.0, -0.5, shift);
    float blueMix = smoothstep(0.0, 1.2, shift);
    vec3 finalColor = baseColor;
    finalColor = mix(finalColor, redShiftColor, redMix);
    finalColor = mix(finalColor, blueShiftColor, blueMix);
    
    // 亮度调整(蓝移更亮,红移更暗)- 红移区保留最低亮度
    float brightness = 1.5 + shift * 1.0;
    brightness = clamp(brightness, 1.5, 4.8);   // 最低亮度 0.9,提升基础亮度
    finalColor *= brightness;
    
    // 距离衰减（保持原有逻辑）
    float distFactor = 1.0 / (1.0 + vRadialDist / 550.0);
    distFactor = clamp(distFactor, 0.45, 1.2);
    
    // 随机微调
    float rand = fract(sin(vRadialDist * 12.9898) * 43758.5453);
    vec3 randomFactor = vec3(0.8 + rand * 0.5);
    finalColor *= randomFactor * distFactor;
    
    // 最终透明度：纹理 alpha × 亮度系数 × 圆形柔化因子
    float finalAlpha = texColor.a * (0.6 + brightness * 0.5) * circleAlpha;
    finalAlpha = clamp(finalAlpha, 0.0, 1.0);
    
    gl_FragColor = vec4(finalColor, finalAlpha);
}
`;

export class ParticleRenderer {
    constructor(scene, count = 50000) {
        this.scene = scene;
        this.count = count;
        this.uniforms = {
            uBeta: { value: 0.0 },
            uPointTexture: { value: createStarTexture() }
        };
        this.points = null;
    }

    generatePositionsAndColors() {
        const positions = new Float32Array(this.count * 3);
        const colors = new Float32Array(this.count * 3);
        const radialDists = new Float32Array(this.count);
        
        const numClusters = 30;
        const clusterCenters = [];
        const clusterRadii = [];
        const clusterAngularSpread = 0.28;
        
        for (let i = 0; i < numClusters; i++) {
            const theta = Math.random() * Math.PI * 2;
            const phi = Math.acos(2 * Math.random() - 1);
            const x = Math.sin(phi) * Math.cos(theta);
            const y = Math.sin(phi) * Math.sin(theta);
            const z = Math.cos(phi);
            const dir = new THREE.Vector3(x, y, z).normalize();
            const radius = 400 + Math.random() * 1400;
            clusterCenters.push(dir);
            clusterRadii.push(radius);
        }
        
        const clusterCount = Math.floor(this.count * 0.8);
        const backgroundCount = this.count - clusterCount;
        
        let idx = 0;
        
        // 生成簇粒子
        for (let i = 0; i < clusterCount; i++) {
            const clusterIdx = Math.floor(Math.random() * numClusters);
            const centerDir = clusterCenters[clusterIdx];
            const baseRadius = clusterRadii[clusterIdx];
            
            const angleOffset = Math.sqrt(-2 * Math.log(Math.random())) * Math.cos(2 * Math.PI * Math.random()) * clusterAngularSpread;
            const axis = new THREE.Vector3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize();
            const quat = new THREE.Quaternion().setFromUnitVectors(
                new THREE.Vector3(0, 0, 1),
                centerDir
            );
            const offsetVec = new THREE.Vector3(0, 0, 1).applyQuaternion(quat);
            const perp1 = new THREE.Vector3(offsetVec.y, offsetVec.z, offsetVec.x).normalize();
            const perp2 = offsetVec.clone().cross(perp1);
            const randomOffset = perp1.clone().multiplyScalar(Math.sin(angleOffset) * Math.cos(2 * Math.PI * Math.random()))
                .add(perp2.clone().multiplyScalar(Math.sin(angleOffset) * Math.sin(2 * Math.PI * Math.random())))
                .add(offsetVec.clone().multiplyScalar(Math.cos(angleOffset)));
            const finalDir = randomOffset.normalize();
            
            let radius = baseRadius + (Math.sqrt(-2 * Math.log(Math.random())) * Math.cos(2 * Math.PI * Math.random()) * 200);
            radius = Math.max(250, Math.min(2200, radius));
            
            const x = finalDir.x * radius;
            const y = finalDir.y * radius;
            const z = finalDir.z * radius;
            positions[idx*3] = x;
            positions[idx*3+1] = y;
            positions[idx*3+2] = z;
            radialDists[idx] = radius;
            
            let brightness = 0.7 + Math.random() * 1.5;
            colors[idx*3] = brightness;
            colors[idx*3+1] = brightness;
            colors[idx*3+2] = brightness;
            idx++;
        }
        
        // 生成背景均匀粒子
        for (let i = 0; i < backgroundCount; i++) {
            const phi = Math.acos(1 - 2 * Math.random());
            const theta = 2 * Math.PI * Math.random();
            const x = Math.sin(phi) * Math.cos(theta);
            const y = Math.sin(phi) * Math.sin(theta);
            const z = Math.cos(phi);
            const radius = 400 + Math.random() * 1600;
            positions[idx*3] = x * radius;
            positions[idx*3+1] = y * radius;
            positions[idx*3+2] = z * radius;
            radialDists[idx] = radius;
            
            let brightness = 0.6 + Math.random() * 1.4;
            colors[idx*3] = brightness;
            colors[idx*3+1] = brightness;
            colors[idx*3+2] = brightness;
            idx++;
        }
        
        return { positions, colors, radialDists };
    }

    create() {
        const geometry = new THREE.BufferGeometry();
        const { positions, colors, radialDists } = this.generatePositionsAndColors();
        geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
        geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
        geometry.setAttribute('radialDist', new THREE.BufferAttribute(radialDists, 1));
        
        const material = new THREE.ShaderMaterial({
            uniforms: this.uniforms,
            vertexShader: vertexShader,
            fragmentShader: fragmentShader,
            transparent: true,
            depthWrite: false,
            blending: THREE.AdditiveBlending,
            vertexColors: true
        });
        
        this.points = new THREE.Points(geometry, material);
        this.scene.add(this.points);
    }
    
    updateBeta(beta) {
        this.uniforms.uBeta.value = beta;
    }

    dispose() {
        if (this.uniforms?.uPointTexture?.value) {
            this.uniforms.uPointTexture.value.dispose();
            this.uniforms.uPointTexture.value = null;
        }
        if (this.points?.geometry) {
            this.points.geometry.dispose();
            this.points.geometry = null;
        }
        if (this.points?.material) {
            this.points.material.dispose();
            this.points.material = null;
        }
        if (this.points && this.scene) {
            this.scene.remove(this.points);
            this.points = null;
        }
    }
}