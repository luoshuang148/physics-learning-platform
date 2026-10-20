import * as THREE from 'three';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { AfterimagePass } from 'three/addons/postprocessing/AfterimagePass.js';
import { ShaderPass } from 'three/addons/postprocessing/ShaderPass.js';

export class PostProcessor {
    constructor(renderer, scene, camera) {
        this.composer = new EffectComposer(renderer);
        
        // 1. 基础渲染
        const renderPass = new RenderPass(scene, camera);
        this.composer.addPass(renderPass);

        // 2. 残影拖尾效果（初始状态：禁用，避免视角旋转产生拖尾）
        this.afterimagePass = new AfterimagePass(0.82); // 拖尾强度固定为 0.85
        this.afterimagePass.enabled = false;            // 默认禁用
        this.composer.addPass(this.afterimagePass);

        // 拖尾定时器
        this.trailTimer = null;

        // 3. Bloom 泛光
        this.bloomPass = new UnrealBloomPass(new THREE.Vector2(window.innerWidth, window.innerHeight), 1.2, 0.5, 0.3);
        this.bloomPass.threshold = 0.05;
        this.bloomPass.strength = 0.9;
        this.bloomPass.radius = 0.9;
        this.composer.addPass(this.bloomPass);

        // 4. 色调微调（输出到屏幕）
        const tintShader = {
            uniforms: {
                tDiffuse: { value: null },
                intensity: { value: 0.0 }
            },
            vertexShader: `
                varying vec2 vUv;
                void main() {
                    vUv = uv;
                    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
                }
            `,
            fragmentShader: `
                uniform sampler2D tDiffuse;
                uniform float intensity;
                varying vec2 vUv;
                void main() {
                    vec4 color = texture2D(tDiffuse, vUv);
                    vec3 tint = mix(vec3(1.0), vec3(0.7, 0.5, 1.0), intensity);
                    gl_FragColor = vec4(color.rgb * tint, color.a);
                }
            `
        };
        this.tintPass = new ShaderPass(tintShader);
        this.composer.addPass(this.tintPass);
        this.tintPass.renderToScreen = true;
    }

    /**
     * 触发一次拖尾效果（速度变化时调用）
     * @param {number} duration 拖尾持续时长（毫秒），结束后自动禁用拖尾
     */
    triggerTrail(duration = 400) {
        // 清除之前的恢复定时器
        if (this.trailTimer) {
            clearTimeout(this.trailTimer);
        }
        // 启用拖尾
        this.enableTrail(true);
        // 设定定时器，一段时间后禁用拖尾
        this.trailTimer = setTimeout(() => {
            this.enableTrail(false);
            this.trailTimer = null;
        }, duration);
    }

    /**
     * 控制拖尾效果的启用/禁用
     * @param {boolean} enable
     */
    enableTrail(enable) {
        if (this.afterimagePass) {
            this.afterimagePass.enabled = enable;
        }
    }

    // 更新速度相关的视觉参数（多普勒、Bloom 强度等）
    updateBeta(beta) {
        this.bloomPass.strength = 0.5 + beta * 1.0;
        this.bloomPass.radius = 0.7 + beta * 1.2;
        this.tintPass.uniforms.intensity.value = beta * 0.3;
    }

    render() {
        this.composer.render();
    }

    setSize(width, height) {
        this.composer.setSize(width, height);
        if (this.afterimagePass && this.afterimagePass.setSize) {
            this.afterimagePass.setSize(width, height);
        }
    }

    dispose() {
        if (this.trailTimer) {
            clearTimeout(this.trailTimer);
            this.trailTimer = null;
        }
        // EffectComposer 没有 dispose 方法，需要手动清理各通道
        if (this.composer) {
            this.composer.passes.forEach(pass => {
                if (pass.geometry) pass.geometry.dispose();
                if (pass.material) pass.material.dispose();
            });
        }
    }
}