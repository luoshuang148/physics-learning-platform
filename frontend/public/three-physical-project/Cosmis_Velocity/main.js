import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

//有关地球建模的第三方库为：
(() => {

  const canvas = document.getElementById('c');
  if (!canvas) return;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x030712);

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(window.innerWidth, window.innerHeight);

  const camera = new THREE.PerspectiveCamera(50, window.innerWidth / window.innerHeight, 0.1, 12000);
  camera.position.set(0, 24, 120);

  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.target.set(0, 0, 0);

  let isUserControllingCamera = false;
  let lastUserCameraActionAt = 0;
  const AUTO_FOLLOW_IN_SOLAR_VIEW = false;
  function onControlStart() {
    isUserControllingCamera = true;
    lastUserCameraActionAt = performance.now();
  }
  function onControlEnd() {
    isUserControllingCamera = false;
    lastUserCameraActionAt = performance.now();
  }
  controls.addEventListener('start', onControlStart);
  controls.addEventListener('end', onControlEnd);

  scene.add(new THREE.AmbientLight(0x7fa4ff, 0.42));
  const light = new THREE.DirectionalLight(0xffffff, 1.25);
  light.position.set(26, 18, 34);
  scene.add(light);

  const deepSpaceFog = new THREE.FogExp2(0x02050d, 0.00016);
  scene.fog = deepSpaceFog;

  // stars: 多层星场 + 稀疏彩色恒星
  const starLayerNearGeo = new THREE.BufferGeometry();
  const starLayerNearArr = new Float32Array(3200 * 3);
  for (let i = 0; i < 3200; i += 1) {
    const r = 1600;
    starLayerNearArr[i * 3] = (Math.random() - 0.5) * r;
    starLayerNearArr[i * 3 + 1] = (Math.random() - 0.5) * r;
    starLayerNearArr[i * 3 + 2] = (Math.random() - 0.5) * r;
  }
  starLayerNearGeo.setAttribute('position', new THREE.BufferAttribute(starLayerNearArr, 3));
  const starLayerNear = new THREE.Points(starLayerNearGeo, new THREE.PointsMaterial({ color: 0xcde0ff, size: 1.1, transparent: true, opacity: 0.82 }));
  scene.add(starLayerNear);

  const starLayerFarGeo = new THREE.BufferGeometry();
  const starLayerFarArr = new Float32Array(3600 * 3);
  for (let i = 0; i < 3600; i += 1) {
    const r = 3600;
    starLayerFarArr[i * 3] = (Math.random() - 0.5) * r;
    starLayerFarArr[i * 3 + 1] = (Math.random() - 0.5) * r;
    starLayerFarArr[i * 3 + 2] = (Math.random() - 0.5) * r;
  }
  starLayerFarGeo.setAttribute('position', new THREE.BufferAttribute(starLayerFarArr, 3));
  const starLayerFar = new THREE.Points(starLayerFarGeo, new THREE.PointsMaterial({ color: 0x8fb3ff, size: 1.45, transparent: true, opacity: 0.5 }));
  scene.add(starLayerFar);

  const brightStarGeo = new THREE.BufferGeometry();
  const brightStarArr = new Float32Array(260 * 3);
  const brightStarCol = new Float32Array(260 * 3);
  for (let i = 0; i < 260; i += 1) {
    const r = 2200 + Math.random() * 1400;
    const a = Math.random() * Math.PI * 2;
    const b = Math.acos(2 * Math.random() - 1);
    brightStarArr[i * 3] = Math.sin(b) * Math.cos(a) * r;
    brightStarArr[i * 3 + 1] = Math.cos(b) * r;
    brightStarArr[i * 3 + 2] = Math.sin(b) * Math.sin(a) * r;

    const t = Math.random();
    if (t < 0.33) {
      brightStarCol[i * 3] = 1.0; brightStarCol[i * 3 + 1] = 0.92; brightStarCol[i * 3 + 2] = 0.82;
    } else if (t < 0.66) {
      brightStarCol[i * 3] = 0.82; brightStarCol[i * 3 + 1] = 0.9; brightStarCol[i * 3 + 2] = 1.0;
    } else {
      brightStarCol[i * 3] = 1.0; brightStarCol[i * 3 + 1] = 0.86; brightStarCol[i * 3 + 2] = 0.9;
    }
  }
  brightStarGeo.setAttribute('position', new THREE.BufferAttribute(brightStarArr, 3));
  brightStarGeo.setAttribute('color', new THREE.BufferAttribute(brightStarCol, 3));
  const brightStars = new THREE.Points(brightStarGeo, new THREE.PointsMaterial({ size: 2.2, vertexColors: true, transparent: true, opacity: 0.88 }));
  scene.add(brightStars);

  // 稀薄星云背景（程序纹理精灵）
  function createNebulaSpriteTexture(size = 256) {
    const c = document.createElement('canvas');
    c.width = size;
    c.height = size;
    const ctx = c.getContext('2d');
    const g = ctx.createRadialGradient(size / 2, size / 2, size * 0.02, size / 2, size / 2, size * 0.5);
    g.addColorStop(0.0, 'rgba(180,210,255,0.32)');
    g.addColorStop(0.45, 'rgba(120,140,230,0.18)');
    g.addColorStop(1.0, 'rgba(20,30,60,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, size, size);
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  }

  const nebulaTexture = createNebulaSpriteTexture(256);
  const nebulaSprites = [];
  for (let i = 0; i < 12; i += 1) {
    const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: nebulaTexture, color: i % 2 === 0 ? 0x9db8ff : 0x8f7dff, transparent: true, opacity: 0.18, depthWrite: false, blending: THREE.AdditiveBlending }));
    const radius = 1200 + Math.random() * 2000;
    const angle = Math.random() * Math.PI * 2;
    const height = (Math.random() - 0.5) * 900;
    sprite.position.set(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    const s = 320 + Math.random() * 520;
    sprite.scale.set(s, s, 1);
    scene.add(sprite);
    nebulaSprites.push(sprite);
  }

  const KM_TO_SCENE = 1 / 500;
  const MU = 398600.4418;
  const EARTH_RADIUS = 6371;
  const LAUNCH_ALTITUDE = 300;
  const R0 = EARTH_RADIUS + LAUNCH_ALTITUDE;

  const FIRST_COSMIC_STANDARD = 7.9;
  const SECOND_COSMIC_STANDARD = 11.2;
  const THIRD_COSMIC_STANDARD = 16.7;

  // 教学分阶段区间（与物理阈值分开，避免边界显示混淆）
  const STAGE_1_MAX = 7.8;
  const STAGE_2_MAX = 11.1;
  const STAGE_3_MAX = 16.6;
  // 教学演示修正：让 v0=11.2 对应“恰好逃逸阈值”（保证 11.2 以下显示为束缚椭圆）
  const MU_EARTH_DEMO = 0.5 * SECOND_COSMIC_STANDARD * SECOND_COSMIC_STANDARD * R0;

  const EARTH_ORBIT_RADIUS_SCENE = 200;
  const EARTH_ORBIT_RADIUS_KM = EARTH_ORBIT_RADIUS_SCENE / KM_TO_SCENE;
  const EARTH_ORBIT_ECCENTRICITY = 0.017;
  // 椭圆轨道初始真近点角：0 表示近日点
  const EARTH_INITIAL_TRUE_ANOMALY = 0;

  // 太阳引力参数按地球在该轨道上的圆轨道速度反推，保证动力学更自洽
  const EARTH_SOLAR_ORBIT_SPEED = 8.2;
  const MU_SUN_SIM = EARTH_SOLAR_ORBIT_SPEED * EARTH_SOLAR_ORBIT_SPEED * EARTH_ORBIT_RADIUS_KM;
  const EARTH_ORBIT_ANGULAR_SPEED = EARTH_SOLAR_ORBIT_SPEED / EARTH_ORBIT_RADIUS_KM;
  // 太阳系演示加速：让第三阶段在 20~30s 内可见一圈绕日运动
  const SOLAR_DEMO_TIME_SCALE = 24;

  const EARTH_DECAY_START = 12 * R0;
  const EARTH_DECAY_END = 55 * R0;

  const EARTH_VISUAL_RADIUS = EARTH_RADIUS * KM_TO_SCENE;
  // 教学可视化比例：保持太阳明显更大，但不遮挡内行星轨道
  const SUN_VISUAL_RADIUS = 40;
  const EARTH_AXIAL_TILT = THREE.MathUtils.degToRad(23.4);

  const state = {
    launched: false,
    thirdMode: false,
    solarEscaped: false,
    earthOrbitAngle: EARTH_INITIAL_TRUE_ANOMALY,
    timeScale: 30,
    position: new THREE.Vector3(R0, 0, 0),
    velocity: new THREE.Vector3(0, 5.0, 0),
    solarDeploy: 0,
    stageSeparated: false,
    stageSepT: 0,
    shockFlash: 0,
    cameraShake: 0,
    solarTime: 0,
    launchV0: 5.0,
    currentExperimentId: null,
  };

  function fract(v) {
    return v - Math.floor(v);
  }

  function pseudoNoise2D(x, y) {
    return fract(Math.sin(x * 127.1 + y * 311.7) * 43758.5453123);
  }

  function smoothNoise2D(x, y) {
    const x0 = Math.floor(x);
    const y0 = Math.floor(y);
    const fx = x - x0;
    const fy = y - y0;

    const n00 = pseudoNoise2D(x0, y0);
    const n10 = pseudoNoise2D(x0 + 1, y0);
    const n01 = pseudoNoise2D(x0, y0 + 1);
    const n11 = pseudoNoise2D(x0 + 1, y0 + 1);

    const ux = fx * fx * (3 - 2 * fx);
    const uy = fy * fy * (3 - 2 * fy);

    const nx0 = n00 * (1 - ux) + n10 * ux;
    const nx1 = n01 * (1 - ux) + n11 * ux;
    return nx0 * (1 - uy) + nx1 * uy;
  }

  function fbmNoise2D(x, y, octaves = 5) {
    let amp = 0.5;
    let freq = 1;
    let sum = 0;
    let norm = 0;
    for (let i = 0; i < octaves; i += 1) {
      sum += smoothNoise2D(x * freq, y * freq) * amp;
      norm += amp;
      amp *= 0.5;
      freq *= 2;
    }
    return sum / Math.max(1e-6, norm);
  }

  function createEarthTextureSet(size = 1024) {
    const width = size;
    const height = Math.floor(size / 2);

    const createCanvas = () => {
      const c = document.createElement('canvas');
      c.width = width;
      c.height = height;
      return c;
    };

    const colorCvs = createCanvas();
    const bumpCvs = createCanvas();
    const roughCvs = createCanvas();
    const nightCvs = createCanvas();

    const colorCtx = colorCvs.getContext('2d');
    const bumpCtx = bumpCvs.getContext('2d');
    const roughCtx = roughCvs.getContext('2d');
    const nightCtx = nightCvs.getContext('2d');

    const colorImg = colorCtx.createImageData(width, height);
    const bumpImg = bumpCtx.createImageData(width, height);
    const roughImg = roughCtx.createImageData(width, height);
    const nightImg = nightCtx.createImageData(width, height);

    const colorData = colorImg.data;
    const bumpData = bumpImg.data;
    const roughData = roughImg.data;
    const nightData = nightImg.data;

    for (let y = 0; y < height; y += 1) {
      const v = y / (height - 1);
      const lat = (v - 0.5) * Math.PI;
      const latAbs = Math.abs(Math.sin(lat));

      for (let x = 0; x < width; x += 1) {
        const u = x / (width - 1);
        const i = (y * width + x) * 4;

        const c1 = fbmNoise2D(u * 4.8 + 1.7, v * 3.4 + 2.3, 6);
        const c2 = fbmNoise2D(u * 9.6 + 5.4, v * 6.1 + 1.2, 5);
        const c3 = fbmNoise2D(u * 22.0 + 9.1, v * 12.0 + 7.6, 3);
        const continent = c1 * 0.62 + c2 * 0.3 + c3 * 0.08;

        const mountainNoise = fbmNoise2D(u * 32.0 + 3.2, v * 20.0 + 8.1, 4);
        const detailNoise = fbmNoise2D(u * 70.0 + 11.1, v * 36.0 + 2.6, 2);

        const land = Math.max(0, Math.min(1, (continent - 0.5) / 0.2));
        const ocean = 1 - land;
        const altitude = Math.max(0, Math.min(1, land * (0.45 + mountainNoise * 0.55)));

        let r = 0;
        let g = 0;
        let b = 0;

        if (land > 0.03) {
          const forest = fbmNoise2D(u * 15.0 + 4.8, v * 10.0 + 3.1, 3);
          r = 48 + altitude * 70 + forest * 8;
          g = 92 + altitude * 100 + forest * 20;
          b = 42 + altitude * 45 + forest * 6;

          const dry = Math.max(0, Math.min(1, (Math.abs(lat) - 0.55) / 0.25));
          r = r * (1 - dry) + 164 * dry;
          g = g * (1 - dry) + 142 * dry;
          b = b * (1 - dry) + 102 * dry;
        } else {
          const depth = Math.max(0, Math.min(1, ocean * (0.25 + c2 * 0.75)));
          r = 6 + depth * 18;
          g = 28 + depth * 55;
          b = 86 + depth * 130;
        }

        const coast = Math.max(0, 1 - Math.abs(continent - 0.5) / 0.03);
        r += coast * 14;
        g += coast * 18;
        b += coast * 8;

        const ice = Math.max(0, Math.min(1, (latAbs - 0.72) / 0.2));
        r = r * (1 - ice) + 236 * ice;
        g = g * (1 - ice) + 243 * ice;
        b = b * (1 - ice) + 248 * ice;

        colorData[i] = Math.round(Math.max(0, Math.min(255, r)));
        colorData[i + 1] = Math.round(Math.max(0, Math.min(255, g)));
        colorData[i + 2] = Math.round(Math.max(0, Math.min(255, b)));
        colorData[i + 3] = 255;

        const bump = Math.max(0, Math.min(1, land * 0.5 + altitude * 0.35 + mountainNoise * 0.12 + detailNoise * 0.03));
        const bumpGray = Math.round(bump * 255);
        bumpData[i] = bumpGray;
        bumpData[i + 1] = bumpGray;
        bumpData[i + 2] = bumpGray;
        bumpData[i + 3] = 255;

        const rough = Math.max(0, Math.min(1, ocean * 0.12 + land * (0.62 - altitude * 0.25) + ice * 0.45));
        const roughGray = Math.round(rough * 255);
        roughData[i] = roughGray;
        roughData[i + 1] = roughGray;
        roughData[i + 2] = roughGray;
        roughData[i + 3] = 255;

        const citySeed = fbmNoise2D(u * 80 + 10.3, v * 44 + 19.7, 2);
        const cityBand = Math.max(0, 1 - Math.abs(lat) / (Math.PI * 0.5));
        const city = land > 0.2 ? Math.max(0, Math.min(1, (citySeed - 0.75) / 0.25)) * Math.pow(cityBand, 1.4) : 0;
        const cityVal = Math.round(city * 255);
        nightData[i] = cityVal;
        nightData[i + 1] = Math.round(cityVal * 0.78);
        nightData[i + 2] = Math.round(cityVal * 0.52);
        nightData[i + 3] = 255;
      }
    }

    colorCtx.putImageData(colorImg, 0, 0);
    bumpCtx.putImageData(bumpImg, 0, 0);
    roughCtx.putImageData(roughImg, 0, 0);
    nightCtx.putImageData(nightImg, 0, 0);

    const createTexture = (c, srgb = false) => {
      const t = new THREE.CanvasTexture(c);
      if (srgb) t.colorSpace = THREE.SRGBColorSpace;
      t.anisotropy = renderer.capabilities.getMaxAnisotropy();
      t.needsUpdate = true;
      return t;
    };

    return {
      map: createTexture(colorCvs, true),
      bumpMap: createTexture(bumpCvs),
      roughnessMap: createTexture(roughCvs),
      nightMap: createTexture(nightCvs, true),
    };
  }

  function createCloudAlphaTexture(size = 1024) {
    const cvs = document.createElement('canvas');
    cvs.width = size;
    cvs.height = size / 2;
    const ctx = cvs.getContext('2d');
    const img = ctx.createImageData(cvs.width, cvs.height);
    const data = img.data;

    for (let y = 0; y < cvs.height; y += 1) {
      const v = y / (cvs.height - 1);
      for (let x = 0; x < cvs.width; x += 1) {
        const u = x / (cvs.width - 1);

        const n1 = fbmNoise2D(u * 7.5 + 2.1, v * 4.4 + 9.7, 5);
        const n2 = fbmNoise2D(u * 19.0 + 4.2, v * 11.5 + 1.3, 3);
        const cloud = Math.max(0, (n1 * 0.78 + n2 * 0.22 - 0.58) / 0.42);

        const i = (y * cvs.width + x) * 4;
        data[i] = 255;
        data[i + 1] = 255;
        data[i + 2] = 255;
        data[i + 3] = Math.max(0, Math.min(255, Math.round(cloud * 255)));
      }
    }

    ctx.putImageData(img, 0, 0);
    const tex = new THREE.CanvasTexture(cvs);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.needsUpdate = true;
    return tex;
  }

  const textureLoader = new THREE.TextureLoader();
  const maxAniso = renderer.capabilities.getMaxAnisotropy();

  const earthTextures = {
    map: textureLoader.load('../Shared/textures/earth_atmos_2048.jpg'),
    bumpMap: null,
    roughnessMap: textureLoader.load('../Shared/textures/earth_specular_2048.jpg'),
    nightMap: textureLoader.load('../Shared/textures/earth_lights_2048.png'),
  };

  earthTextures.map.colorSpace = THREE.SRGBColorSpace;
  earthTextures.nightMap.colorSpace = THREE.SRGBColorSpace;
  earthTextures.map.anisotropy = maxAniso;
  earthTextures.roughnessMap.anisotropy = maxAniso;
  earthTextures.nightMap.anisotropy = maxAniso;

  const cloudTexture = textureLoader.load('https://threejs.org/examples/textures/planets/earth_clouds_1024.png');
  cloudTexture.colorSpace = THREE.SRGBColorSpace;
  cloudTexture.anisotropy = maxAniso;

  const earth = new THREE.Mesh(
    new THREE.SphereGeometry(EARTH_VISUAL_RADIUS, 128, 128),
    new THREE.MeshStandardMaterial({
      map: earthTextures.map,
      roughnessMap: earthTextures.roughnessMap,
      roughness: 0.7,
      metalness: 0.02,
      emissiveMap: earthTextures.nightMap,
      emissive: 0x7f5c3a,
      emissiveIntensity: 0.22,
    })
  );
  scene.add(earth);

  const cloudLayer = new THREE.Mesh(
    new THREE.SphereGeometry(EARTH_VISUAL_RADIUS * 1.018, 96, 96),
    new THREE.MeshStandardMaterial({
      alphaMap: cloudTexture,
      color: 0xffffff,
      transparent: true,
      opacity: 0.55,
      depthWrite: false,
      roughness: 0.95,
      metalness: 0,
    })
  );
  scene.add(cloudLayer);

  const atmosphere = new THREE.Mesh(
    new THREE.SphereGeometry(EARTH_VISUAL_RADIUS * 1.09, 96, 96),
    new THREE.MeshPhongMaterial({
      color: 0x6ec8ff,
      emissive: 0x1b4f8a,
      emissiveIntensity: 0.35,
      transparent: true,
      opacity: 0.16,
      side: THREE.BackSide,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      shininess: 8,
    })
  );
  scene.add(atmosphere);

  const atmosphereRim = new THREE.Mesh(
    new THREE.SphereGeometry(EARTH_VISUAL_RADIUS * 1.12, 96, 96),
    new THREE.MeshBasicMaterial({
      color: 0x7fd6ff,
      transparent: true,
      opacity: 0.12,
      side: THREE.BackSide,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
  );
  scene.add(atmosphereRim);

  const ship = new THREE.Group();
  const shipCore = new THREE.Group();
  ship.add(shipCore);

  const shipBody = new THREE.Mesh(
    new THREE.CylinderGeometry(0.3, 0.42, 2.2, 24),
    new THREE.MeshStandardMaterial({ color: 0xd7dde8, metalness: 0.78, roughness: 0.28 })
  );
  shipBody.rotation.z = -Math.PI / 2;
  shipCore.add(shipBody);

  const shipNose = new THREE.Mesh(
    new THREE.ConeGeometry(0.3, 0.8, 24),
    new THREE.MeshStandardMaterial({ color: 0xf3f6fb, metalness: 0.65, roughness: 0.22 })
  );
  shipNose.position.x = 1.45;
  shipNose.rotation.z = -Math.PI / 2;
  shipCore.add(shipNose);

  const shipEngine = new THREE.Mesh(
    new THREE.CylinderGeometry(0.22, 0.24, 0.25, 18),
    new THREE.MeshStandardMaterial({ color: 0x6f7782, metalness: 0.82, roughness: 0.35 })
  );
  shipEngine.position.x = -1.2;
  shipEngine.rotation.z = -Math.PI / 2;
  shipCore.add(shipEngine);

  const finMat = new THREE.MeshStandardMaterial({ color: 0x9fa8b8, metalness: 0.55, roughness: 0.4 });
  const finGeo = new THREE.BoxGeometry(0.55, 0.04, 0.24);
  for (let i = 0; i < 4; i += 1) {
    const fin = new THREE.Mesh(finGeo, finMat);
    fin.position.set(-0.55, 0, 0);
    fin.rotation.x = (Math.PI / 2) * i;
    shipCore.add(fin);
  }

  const cockpit = new THREE.Mesh(
    new THREE.SphereGeometry(0.16, 18, 18),
    new THREE.MeshStandardMaterial({ color: 0x8ed6ff, emissive: 0x3eb8ff, emissiveIntensity: 0.9, metalness: 0.1, roughness: 0.1 })
  );
  cockpit.position.set(0.86, 0.17, 0);
  shipCore.add(cockpit);

  const navLightL = new THREE.Mesh(
    new THREE.SphereGeometry(0.06, 10, 10),
    new THREE.MeshBasicMaterial({ color: 0xff4040, transparent: true, opacity: 0.95 })
  );
  navLightL.position.set(-0.1, 0, 0.46);
  shipCore.add(navLightL);

  const navLightR = new THREE.Mesh(
    new THREE.SphereGeometry(0.06, 10, 10),
    new THREE.MeshBasicMaterial({ color: 0x40ff70, transparent: true, opacity: 0.95 })
  );
  navLightR.position.set(-0.1, 0, -0.46);
  shipCore.add(navLightR);

  const panelMat = new THREE.MeshStandardMaterial({ color: 0x1d2b52, emissive: 0x1a3a78, emissiveIntensity: 0.35, metalness: 0.2, roughness: 0.55 });
  const panelGeo = new THREE.BoxGeometry(1.65, 0.05, 0.75);
  const panelArmGeo = new THREE.BoxGeometry(0.42, 0.04, 0.08);
  const solarLeftPivot = new THREE.Group();
  const solarRightPivot = new THREE.Group();
  solarLeftPivot.position.set(0.05, 0, 0.44);
  solarRightPivot.position.set(0.05, 0, -0.44);
  shipCore.add(solarLeftPivot, solarRightPivot);

  const panelArmL = new THREE.Mesh(panelArmGeo, new THREE.MeshStandardMaterial({ color: 0x6d7482, metalness: 0.75, roughness: 0.3 }));
  panelArmL.position.set(0.22, 0, 0);
  solarLeftPivot.add(panelArmL);
  const panelL = new THREE.Mesh(panelGeo, panelMat);
  panelL.position.set(1.15, 0, 0);
  solarLeftPivot.add(panelL);

  const panelArmR = panelArmL.clone();
  panelArmR.position.set(0.22, 0, 0);
  solarRightPivot.add(panelArmR);
  const panelR = new THREE.Mesh(panelGeo, panelMat);
  panelR.position.set(1.15, 0, 0);
  solarRightPivot.add(panelR);

  const stageBooster = new THREE.Group();
  const boosterBody = new THREE.Mesh(
    new THREE.CylinderGeometry(0.17, 0.2, 1.25, 18),
    new THREE.MeshStandardMaterial({ color: 0x8c939f, metalness: 0.65, roughness: 0.36 })
  );
  boosterBody.rotation.z = -Math.PI / 2;
  boosterBody.position.x = -1.78;
  stageBooster.add(boosterBody);
  const boosterFlame = new THREE.Mesh(
    new THREE.ConeGeometry(0.11, 0.5, 14),
    new THREE.MeshBasicMaterial({ color: 0xff7a2e, transparent: true, opacity: 0.88 })
  );
  boosterFlame.rotation.z = Math.PI / 2;
  boosterFlame.position.x = -2.45;
  stageBooster.add(boosterFlame);

  const sepFlash = new THREE.Mesh(
    new THREE.SphereGeometry(0.42, 20, 20),
    new THREE.MeshBasicMaterial({
      color: 0xffe2a0,
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
  );
  sepFlash.position.set(-1.45, 0, 0);
  ship.add(sepFlash);

  const impactFlash = new THREE.Mesh(
    new THREE.SphereGeometry(1.0, 24, 24),
    new THREE.MeshBasicMaterial({
      color: 0xffb366,
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
  );
  impactFlash.visible = false;
  scene.add(impactFlash);

  const impactDebris = [];
  for (let i = 0; i < 22; i += 1) {
    const d = new THREE.Mesh(
      new THREE.SphereGeometry(0.12 + Math.random() * 0.12, 8, 8),
      new THREE.MeshBasicMaterial({ color: 0xffa24a, transparent: true, opacity: 0 })
    );
    d.visible = false;
    d.userData = { vel: new THREE.Vector3(), life: 0, maxLife: 1 };
    scene.add(d);
    impactDebris.push(d);
  }

  const separationDebris = [];
  const debrisCount = 9;
  for (let i = 0; i < debrisCount; i += 1) {
    const d = new THREE.Mesh(
      new THREE.BoxGeometry(0.07, 0.05, 0.05),
      new THREE.MeshStandardMaterial({ color: 0xb9c2cf, metalness: 0.45, roughness: 0.55 })
    );
    d.visible = false;
    d.userData = {
      vel: new THREE.Vector3(),
      spin: new THREE.Vector3(),
      life: 0,
    };
    ship.add(d);
    separationDebris.push(d);
  }

  ship.add(stageBooster);

  const flame = new THREE.Mesh(
    new THREE.ConeGeometry(0.16, 0.9, 16),
    new THREE.MeshBasicMaterial({ color: 0xffa040, transparent: true, opacity: 0.82 })
  );
  flame.position.x = -1.7;
  flame.rotation.z = Math.PI / 2;
  shipCore.add(flame);

  const flameCore = new THREE.Mesh(
    new THREE.ConeGeometry(0.09, 0.58, 14),
    new THREE.MeshBasicMaterial({ color: 0xfff2b3, transparent: true, opacity: 0.9 })
  );
  flameCore.position.x = -1.72;
  flameCore.rotation.z = Math.PI / 2;
  shipCore.add(flameCore);

  const shipHalo = new THREE.Mesh(
    new THREE.SphereGeometry(1.35, 20, 20),
    new THREE.MeshBasicMaterial({
      color: 0x58c8ff,
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
  );
  shipHalo.visible = false;
  ship.add(shipHalo);

  scene.add(ship);

  function orientShipByVelocity() {
    const v = state.velocity.clone();
    if (v.lengthSq() < 1e-8) return;
    const dir = v.normalize();

    // 飞船模型机头是本地 +X 方向，直接把 +X 旋到速度方向
    const baseForward = new THREE.Vector3(1, 0, 0);
    ship.quaternion.setFromUnitVectors(baseForward, dir);
  }

  function setSolarPanelDeploy(t) {
    const clamped = Math.max(0, Math.min(1, t));
    const angle = THREE.MathUtils.lerp(0.1, Math.PI / 2, clamped);
    solarLeftPivot.rotation.x = angle;
    solarRightPivot.rotation.x = -angle;
    solarLeftPivot.scale.setScalar(THREE.MathUtils.lerp(0.3, 1, clamped));
    solarRightPivot.scale.setScalar(THREE.MathUtils.lerp(0.3, 1, clamped));
  }

  function updateShipVisibilityForScene() {
    if (state.thirdMode) {
      ship.scale.setScalar(2.6);
      shipHalo.visible = true;
      const pulse = 0.45 + 0.35 * (0.5 + 0.5 * Math.sin(performance.now() * 0.006));
      shipHalo.material.opacity = pulse;
      const hs = 1.0 + 0.22 * Math.sin(performance.now() * 0.008 + 0.9);
      shipHalo.scale.set(hs, hs, hs);
    } else {
      ship.scale.setScalar(1.0);
      shipHalo.visible = false;
      shipHalo.material.opacity = 0;
      shipHalo.scale.set(1, 1, 1);
    }
  }

  function resetStageSeparation() {
    state.stageSeparated = false;
    state.stageSepT = 0;
    state.shockFlash = 0;
    state.cameraShake = 0;
    stageBooster.visible = true;
    stageBooster.position.set(0, 0, 0);
    stageBooster.rotation.set(0, 0, 0);
    sepFlash.material.opacity = 0;
    sepFlash.scale.set(1, 1, 1);
    impactFlash.visible = false;
    impactFlash.material.opacity = 0;

    for (const d of impactDebris) {
      d.visible = false;
      d.material.opacity = 0;
      d.userData.vel.set(0, 0, 0);
      d.userData.life = 0;
      d.userData.maxLife = 1;
    }

    for (const d of separationDebris) {
      d.visible = false;
      d.position.set(-1.3, 0, 0);
      d.rotation.set(0, 0, 0);
      d.userData.vel.set(0, 0, 0);
      d.userData.spin.set(0, 0, 0);
      d.userData.life = 0;
    }
  }

  // solar reference
  const solarGroup = new THREE.Group();
  scene.add(solarGroup);
  solarGroup.visible = false;

  const sunGroup = new THREE.Group();
  sunGroup.position.set(0, 0, 0);
  solarGroup.add(sunGroup);

  function createSunNoiseBumpTexture(size = 512) {
    const c = document.createElement('canvas');
    c.width = size;
    c.height = size;
    const ctx = c.getContext('2d');
    const img = ctx.createImageData(size, size);
    const data = img.data;

    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        const u = x / (size - 1);
        const v = y / (size - 1);
        const n1 = fbmNoise2D(u * 10.0 + 1.7, v * 10.0 + 2.3, 5);
        const n2 = fbmNoise2D(u * 24.0 + 3.9, v * 24.0 + 6.1, 3);
        const n = Math.max(0, Math.min(1, n1 * 0.72 + n2 * 0.28));
        const g = Math.round(70 + n * 185);
        const i = (y * size + x) * 4;
        data[i] = g;
        data[i + 1] = g;
        data[i + 2] = g;
        data[i + 3] = 255;
      }
    }

    ctx.putImageData(img, 0, 0);
    const tex = new THREE.CanvasTexture(c);
    tex.wrapS = THREE.RepeatWrapping;
    tex.wrapT = THREE.RepeatWrapping;
    tex.needsUpdate = true;
    return tex;
  }

  function createSunDetailTexture(size = 1024) {
    const c = document.createElement('canvas');
    c.width = size;
    c.height = size;
    const ctx = c.getContext('2d');
    const img = ctx.createImageData(size, size);
    const data = img.data;

    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        const u = x / (size - 1);
        const v = y / (size - 1);
        const n1 = fbmNoise2D(u * 9.0 + 1.2, v * 9.0 + 2.6, 5);
        const n2 = fbmNoise2D(u * 24.0 + 7.8, v * 24.0 + 5.1, 4);
        const n3 = fbmNoise2D(u * 54.0 + 3.1, v * 54.0 + 9.7, 2);
        const plume = Math.max(0, Math.min(1, n1 * 0.55 + n2 * 0.33 + n3 * 0.12));

        const hotCore = Math.pow(plume, 1.2);
        const r = Math.round(210 + hotCore * 45);
        const g = Math.round(95 + hotCore * 90);
        const b = Math.round(12 + hotCore * 18);
        const a = Math.round(60 + hotCore * 110);

        const i = (y * size + x) * 4;
        data[i] = r;
        data[i + 1] = g;
        data[i + 2] = b;
        data[i + 3] = a;
      }
    }

    ctx.putImageData(img, 0, 0);
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.wrapS = THREE.RepeatWrapping;
    tex.wrapT = THREE.RepeatWrapping;
    tex.needsUpdate = true;
    return tex;
  }

  const sunNoiseBumpTexture = createSunNoiseBumpTexture(1024);
  sunNoiseBumpTexture.anisotropy = maxAniso;

  const sunDetailTexture = createSunDetailTexture(1024);
  sunDetailTexture.anisotropy = maxAniso;

  const sunMesh = new THREE.Mesh(
    new THREE.SphereGeometry(SUN_VISUAL_RADIUS, 96, 96),
    new THREE.MeshStandardMaterial({
      color: 0xffb85c,
      emissive: 0xffb85c,
      emissiveIntensity: 1.65,
      bumpMap: sunNoiseBumpTexture,
      bumpScale: 0.45,
      roughness: 0.9,
      metalness: 0,
    })
  );
  sunGroup.add(sunMesh);

  const sunDetailLayer = new THREE.Mesh(
    new THREE.SphereGeometry(SUN_VISUAL_RADIUS * 1.015, 96, 96),
    new THREE.MeshBasicMaterial({
      map: sunDetailTexture,
      transparent: true,
      opacity: 0.44,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
  );
  sunGroup.add(sunDetailLayer);

  const sunInnerGlow = new THREE.Mesh(
    new THREE.SphereGeometry(SUN_VISUAL_RADIUS * 1.2, 48, 48),
    new THREE.MeshBasicMaterial({ color: 0xffb347, transparent: true, opacity: 0.06, blending: THREE.AdditiveBlending, depthWrite: false })
  );
  sunInnerGlow.visible = false;
  sunGroup.add(sunInnerGlow);

  const sunOuterGlow = new THREE.Mesh(
    new THREE.SphereGeometry(SUN_VISUAL_RADIUS * 1.95, 48, 48),
    new THREE.MeshBasicMaterial({ color: 0xff7a1f, transparent: true, opacity: 0.04, blending: THREE.AdditiveBlending, depthWrite: false })
  );
  sunOuterGlow.visible = false;
  sunGroup.add(sunOuterGlow);

  const sunCorona = new THREE.Mesh(
    new THREE.SphereGeometry(SUN_VISUAL_RADIUS * 2.45, 40, 40),
    new THREE.MeshBasicMaterial({ color: 0xffb066, transparent: true, opacity: 0.03, blending: THREE.AdditiveBlending, depthWrite: false })
  );
  sunCorona.visible = false;
  sunGroup.add(sunCorona);

  function createCoronaSpriteTexture(size = 256) {
    const c = document.createElement('canvas');
    c.width = size;
    c.height = size;
    const ctx = c.getContext('2d');
    const img = ctx.createImageData(size, size);
    const data = img.data;

    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        const nx = (x / (size - 1)) * 2 - 1;
        const ny = (y / (size - 1)) * 2 - 1;
        const r = Math.sqrt(nx * nx + ny * ny);
        const a = Math.atan2(ny, nx);

        const spoke = 0.5 + 0.5 * Math.sin(a * 11 + fbmNoise2D(nx * 3.2 + 1.7, ny * 3.2 + 2.1, 3) * 7.0);
        const rim = Math.max(0, 1 - Math.abs(r - 0.72) / 0.3);
        const coreFalloff = Math.max(0, 1 - r / 1.0);
        const alpha = Math.max(0, Math.min(1, rim * (0.35 + 0.65 * spoke) + coreFalloff * 0.25));

        const i = (y * size + x) * 4;
        data[i] = 255;
        data[i + 1] = 176;
        data[i + 2] = 96;
        data[i + 3] = Math.round(alpha * 255);
      }
    }

    ctx.putImageData(img, 0, 0);
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.needsUpdate = true;
    return tex;
  }

  const coronaSpriteTexture = createCoronaSpriteTexture(256);
  const coronaSprites = [];
  for (let i = 0; i < 7; i += 1) {
    const sprite = new THREE.Sprite(new THREE.SpriteMaterial({
      map: coronaSpriteTexture,
      color: i % 2 ? 0xffb768 : 0xff9d55,
      transparent: true,
      opacity: 0.14,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    }));
    const s = SUN_VISUAL_RADIUS * (3.2 + Math.random() * 0.9);
    sprite.scale.set(s, s, 1);
    sprite.position.set((Math.random() - 0.5) * 2.0, (Math.random() - 0.5) * 2.0, (Math.random() - 0.5) * 2.0);
    sunGroup.add(sprite);
    coronaSprites.push(sprite);
  }

  const sunLight = new THREE.PointLight(0xffd48a, 2.6, 3200, 1.2);
  sunLight.position.set(0, 0, 0);
  solarGroup.add(sunLight);

  const earthOrbitRing = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(
      Array.from({ length: 241 }, (_, i) => {
        const a = (i / 240) * Math.PI * 2;
        const r = EARTH_ORBIT_RADIUS_SCENE * (1 - EARTH_ORBIT_ECCENTRICITY * EARTH_ORBIT_ECCENTRICITY)
          / (1 + EARTH_ORBIT_ECCENTRICITY * Math.cos(a));
        return new THREE.Vector3(r * Math.cos(a), 0, r * Math.sin(a));
      })
    ),
    new THREE.LineBasicMaterial({ color: 0x335577, transparent: true, opacity: 0.9 })
  );
  solarGroup.add(earthOrbitRing);

  const solarPlanetGroup = new THREE.Group();
  solarGroup.add(solarPlanetGroup);

  const orbitRingMaterial = new THREE.LineBasicMaterial({ color: 0x8fc2ff, transparent: true, opacity: 0.95 });

  const planetDefs = [
    { name: 'Mercury', color: 0xb8a88f, radius: 3.4, orbit: 92, period: 88, phase: 0.2 },
    { name: 'Venus', color: 0xe2c089, radius: 4.8, orbit: 138, period: 225, phase: 1.1 },
    { name: 'EarthRef', color: 0x6ea6ff, radius: 5.2, orbit: EARTH_ORBIT_RADIUS_SCENE, period: 365, phase: 0.0 },
    { name: 'Mars', color: 0xcf6d4a, radius: 4.1, orbit: 270, period: 687, phase: 1.9 },
    { name: 'Jupiter', color: 0xd8b187, radius: 12.0, orbit: 390, period: 4333, phase: 2.6 },
    { name: 'Saturn', color: 0xd9c48f, radius: 10.6, orbit: 500, period: 10759, phase: 0.7 },
    { name: 'Uranus', color: 0x88c9d7, radius: 7.5, orbit: 605, period: 30687, phase: 1.7 },
    { name: 'Neptune', color: 0x5c7fe0, radius: 7.3, orbit: 700, period: 60190, phase: 2.9 },
  ];

  const planets = planetDefs.map((def) => {
    const mesh = new THREE.Mesh(
      new THREE.SphereGeometry(def.radius, 28, 28),
      new THREE.MeshPhongMaterial({
        color: def.color,
        emissive: new THREE.Color(def.color).multiplyScalar(0.32),
        shininess: 24,
      })
    );

    const glow = new THREE.Mesh(
      new THREE.SphereGeometry(def.radius * 1.36, 18, 18),
      new THREE.MeshBasicMaterial({
        color: def.color,
        transparent: true,
        opacity: 0.24,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
      })
    );
    mesh.add(glow);

    const orbit = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints(
        Array.from({ length: 241 }, (_, i) => {
          const a = (i / 240) * Math.PI * 2;
          return new THREE.Vector3(Math.cos(a) * def.orbit, 0, Math.sin(a) * def.orbit);
        })
      ),
      orbitRingMaterial.clone()
    );

    // 第三宇宙速度视角仅保留地球公转轨道线
    orbit.visible = false;

    solarPlanetGroup.add(mesh);

    return {
      ...def,
      mesh,
      glow,
      orbit,
      angularSpeed: (Math.PI * 2) / Math.max(1, def.period),
    };
  });

  const saturn = planets.find((p) => p.name === 'Saturn');
  if (saturn) {
    const saturnRing = new THREE.Mesh(
      new THREE.RingGeometry(saturn.radius * 1.4, saturn.radius * 2.2, 48),
      new THREE.MeshBasicMaterial({ color: 0xd8c79c, transparent: true, opacity: 0.6, side: THREE.DoubleSide })
    );
    saturnRing.rotation.x = Math.PI / 2;
    saturn.mesh.add(saturnRing);
  }

  // 初始化一次，避免进入第三宇宙速度时行星都暂时堆在太阳中心
  updateSolarPlanets(0);


  const MAX_TRAJ_POINTS = 14000;

  const trailArr = new Float32Array(MAX_TRAJ_POINTS * 3);
  const trailGeo = new THREE.BufferGeometry();
  trailGeo.setAttribute('position', new THREE.BufferAttribute(trailArr, 3));
  trailGeo.setDrawRange(0, 0);
  const trailMat = new THREE.LineBasicMaterial({ color: 0x00ffaa });
  const trailLine = new THREE.Line(trailGeo, trailMat);
  scene.add(trailLine);
  let trailCount = 0;

  // 实验轨道可视化：预测轨道(虚线) + 已飞过轨道(实线)
  const predictedArr = new Float32Array(MAX_TRAJ_POINTS * 3);
  const predictedGeo = new THREE.BufferGeometry();
  predictedGeo.setAttribute('position', new THREE.BufferAttribute(predictedArr, 3));
  predictedGeo.setDrawRange(0, 0);
  const predictedLine = new THREE.Line(
    predictedGeo,
    new THREE.LineDashedMaterial({ color: 0x7ec9ff, dashSize: 1.7, gapSize: 1.05, transparent: true, opacity: 0.72 })
  );
  scene.add(predictedLine);

  const traveledArr = new Float32Array(MAX_TRAJ_POINTS * 3);
  const traveledColorArr = new Float32Array(MAX_TRAJ_POINTS * 3);
  const traveledGeo = new THREE.BufferGeometry();
  traveledGeo.setAttribute('position', new THREE.BufferAttribute(traveledArr, 3));
  traveledGeo.setAttribute('color', new THREE.BufferAttribute(traveledColorArr, 3));
  traveledGeo.setDrawRange(0, 0);
  const traveledLine = new THREE.Line(
    traveledGeo,
    new THREE.LineBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.96 })
  );
  scene.add(traveledLine);
  let trajectoryTotal = 0;
  let trajectoryProgress = 0;

  const previewGeo = new THREE.BufferGeometry();
  const previewDashedMat = new THREE.LineDashedMaterial({
    color: 0x58c8ff,
    dashSize: 1.6,
    gapSize: 1.1,
    transparent: true,
    opacity: 0.9,
  });
  const previewLine = new THREE.Line(previewGeo, previewDashedMat);
  scene.add(previewLine);

  const compareGeos = {
    v1: new THREE.BufferGeometry(),
    v2: new THREE.BufferGeometry(),
    v3: new THREE.BufferGeometry(),
  };
  const compareLines = {
    v1: new THREE.Line(compareGeos.v1, new THREE.LineBasicMaterial({ color: 0x00ffaa, transparent: true, opacity: 0.85 })),
    v2: new THREE.Line(compareGeos.v2, new THREE.LineBasicMaterial({ color: 0xffdd55, transparent: true, opacity: 0.85 })),
    v3: new THREE.Line(compareGeos.v3, new THREE.LineBasicMaterial({ color: 0xff66ff, transparent: true, opacity: 0.85 })),
  };
  scene.add(compareLines.v1);
  scene.add(compareLines.v2);
  scene.add(compareLines.v3);
  compareLines.v1.visible = false;
  compareLines.v2.visible = false;
  compareLines.v3.visible = false;

  let compareMode = false;

  function addLog(msg) {
    const box = document.getElementById('log-container');
    if (!box) return;
    const row = document.createElement('div');
    row.className = 'log-item';
    row.textContent = msg;
    box.prepend(row);
    while (box.children.length > 30) box.removeChild(box.lastChild);
  }

  const experimentResults = [];

  function classifyExperiment(v0, thirdMode, solarEscaped, reentered) {
    if (thirdMode) {
      if (v0 >= THIRD_COSMIC_STANDARD) return solarEscaped ? '第三宇宙速度（太阳系逃逸）' : '第三宇宙速度（太阳系内飞行）';
      return '第二宇宙速度以上（地球逃逸并绕日）';
    }
    if (reentered) return '亚第一宇宙速度（再入地球）';
    if (v0 < SECOND_COSMIC_STANDARD) return '第一宇宙速度附近（地球束缚）';
    if (v0 < THIRD_COSMIC_STANDARD) return '第二宇宙速度区间（地球逃逸）';
    return '第三宇宙速度阈值';
  }


  function addExperimentResult(result) {
    experimentResults.unshift(result);
    if (experimentResults.length > 200) experimentResults.pop();

    const list = document.getElementById('result-list');
    const count = document.getElementById('result-count');
    if (!list || !count) return;

    const card = document.createElement('div');
    card.style.border = '1px solid rgba(88,200,255,0.25)';
    card.style.borderRadius = '8px';
    card.style.padding = '8px';
    card.style.marginBottom = '8px';
    card.style.fontSize = '12px';
    card.style.background = 'rgba(12,20,36,0.4)';

    card.innerHTML = [
      `<div style="display:flex;justify-content:space-between;color:#8ecbff;"><strong>实验 #${result.id}</strong><span>${result.timeText}</span></div>`,
      `<div style="margin-top:4px;">初速度：<strong style="color:#ffffff;">${result.v0.toFixed(1)} km/s</strong></div>`,
      `<div>模式：${result.thirdMode ? '太阳系三体演示' : '地心二体演示'}</div>`,
      `<div>结果：<strong style="color:#ffffff;">${result.resultType}</strong></div>`,
      `<div>飞行时长：${result.duration.toFixed(1)} s，末速度：${result.finalSpeed.toFixed(2)} km/s</div>`
    ].join('');

    list.prepend(card);
    while (list.children.length > 80) list.removeChild(list.lastChild);
    count.textContent = `记录数：${experimentResults.length}`;
  }

  function exportResultsCsv() {
    if (!experimentResults.length) {
      addLog('无实验记录可导出');
      return;
    }

    const header = ['id', 'time', 'v0_km_s', 'mode', 'result_type', 'duration_s', 'final_speed_km_s', 'solar_escaped', 'reentered'];
    const lines = [header.join(',')];

    for (const r of experimentResults) {
      const row = [
        r.id,
        r.timeISO,
        r.v0.toFixed(2),
        r.thirdMode ? 'solar' : 'earth',
        `"${r.resultType}"`,
        r.duration.toFixed(3),
        r.finalSpeed.toFixed(3),
        r.solarEscaped ? 1 : 0,
        r.reentered ? 1 : 0,
      ];
      lines.push(row.join(','));
    }

    const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `cosmic_experiment_results_${Date.now()}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    addLog('实验结果 CSV 已导出');
  }

  function finalizeCurrentExperiment(reason = 'manual_end') {
    if (!state.currentExperimentId) return;

    const now = performance.now();
    const v0 = Number(sliderV?.value ?? '5.0');
    const duration = Math.max(0, (now - state.currentExperimentId.startMs) / 1000);
    const finalSpeed = state.velocity.length();
    const reentered = reason === 'reentry';

    const record = {
      id: state.currentExperimentId.id,
      timeISO: new Date().toISOString(),
      timeText: new Date().toLocaleTimeString(),
      v0,
      thirdMode: state.currentExperimentId.thirdMode,
      solarEscaped: state.solarEscaped,
      reentered,
      resultType: classifyExperiment(v0, state.currentExperimentId.thirdMode, state.solarEscaped, reentered),
      duration,
      finalSpeed,
    };

    addExperimentResult(record);
    state.currentExperimentId = null;
  }

  function getEarthCenterKmAtAngle(angle) {
    const e = EARTH_ORBIT_ECCENTRICITY;
    const a = EARTH_ORBIT_RADIUS_KM;
    const r = (a * (1 - e * e)) / (1 + e * Math.cos(angle));
    return new THREE.Vector3(
      Math.cos(angle) * r,
      0,
      Math.sin(angle) * r
    );
  }

  function getEarthCenterKm() {
    return getEarthCenterKmAtAngle(state.earthOrbitAngle);
  }

  function updateSolarPlanets(dt) {
    state.solarTime += dt * 50;
    for (const p of planets) {
      const a = p.phase + state.solarTime * p.angularSpeed;
      p.mesh.position.set(Math.cos(a) * p.orbit, 0, Math.sin(a) * p.orbit);

      if (p.name === 'EarthRef') {
        p.mesh.visible = false;
      } else {
        p.mesh.visible = true;
      }
    }
  }

  function triggerEarthImpactExplosion() {
    const impactDir = state.position.clone().normalize();
    const center = impactDir.multiplyScalar(EARTH_RADIUS * KM_TO_SCENE);

    impactFlash.visible = true;
    impactFlash.position.copy(center);
    impactFlash.scale.set(0.3, 0.3, 0.3);
    impactFlash.material.opacity = 0.95;

    for (const d of impactDebris) {
      d.visible = true;
      d.position.copy(center);

      const rand = new THREE.Vector3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize();
      const outward = rand.add(impactDir.clone().multiplyScalar(0.9)).normalize();
      const speed = 8 + Math.random() * 12;
      d.userData.vel.copy(outward.multiplyScalar(speed));
      d.userData.maxLife = 0.7 + Math.random() * 0.9;
      d.userData.life = d.userData.maxLife;
      d.material.opacity = 0.9;
    }

    state.cameraShake = Math.max(state.cameraShake, 1.35);
  }

  function resetTrail() {
    trailCount = 0;
    trailGeo.setDrawRange(0, 0);

    trajectoryProgress = 0;
    traveledGeo.setDrawRange(0, 0);
    predictedGeo.setDrawRange(0, trajectoryTotal);
    predictedLine.computeLineDistances();

    for (let i = 0; i < traveledColorArr.length; i += 1) traveledColorArr[i] = 0;
    traveledGeo.attributes.color.needsUpdate = true;
  }

  function pushTrail(pKm) {
    if (trailCount >= MAX_TRAJ_POINTS) return;
    const i = trailCount * 3;
    trailArr[i] = pKm.x * KM_TO_SCENE;
    trailArr[i + 1] = pKm.y * KM_TO_SCENE;
    trailArr[i + 2] = pKm.z * KM_TO_SCENE;
    trailCount += 1;
    trailGeo.setDrawRange(0, trailCount);
    trailGeo.attributes.position.needsUpdate = true;
  }

  function updateTrajectoryProgressByShip() {
    if (!state.launched || trajectoryTotal <= 1) return;

    const shipScenePos = ship.position;
    const posAttr = predictedGeo.getAttribute('position');
    if (!posAttr) return;

    const startIdx = Math.max(0, trajectoryProgress - 4);
    const endIdx = Math.min(trajectoryTotal - 1, trajectoryProgress + 26);
    let bestIdx = trajectoryProgress;
    let bestD2 = Infinity;

    for (let i = startIdx; i <= endIdx; i += 1) {
      const x = posAttr.getX(i);
      const y = posAttr.getY(i);
      const z = posAttr.getZ(i);
      const dx = shipScenePos.x - x;
      const dy = shipScenePos.y - y;
      const dz = shipScenePos.z - z;
      const d2 = dx * dx + dy * dy + dz * dz;
      if (d2 < bestD2) {
        bestD2 = d2;
        bestIdx = i;
      }
    }

    if (bestIdx <= trajectoryProgress) return;

    for (let i = trajectoryProgress; i <= bestIdx; i += 1) {
      const dst = i * 3;
      traveledArr[dst] = posAttr.getX(i);
      traveledArr[dst + 1] = posAttr.getY(i);
      traveledArr[dst + 2] = posAttr.getZ(i);
    }

    trajectoryProgress = bestIdx;
    traveledGeo.setDrawRange(0, trajectoryProgress + 1);
    traveledGeo.attributes.position.needsUpdate = true;

    const tailWindow = 340;
    for (let i = 0; i <= trajectoryProgress; i += 1) {
      const age = trajectoryProgress - i;
      const t = Math.max(0, 1 - age / tailWindow);
      const b = 0.22 + 0.78 * t;
      const r = 0.05 + 0.10 * t;
      const g = 0.45 + 0.55 * b;
      const bl = 0.35 + 0.50 * b;
      const idx = i * 3;
      traveledColorArr[idx] = r;
      traveledColorArr[idx + 1] = g;
      traveledColorArr[idx + 2] = bl;
    }
    traveledGeo.attributes.color.needsUpdate = true;

    const remain = Math.max(0, trajectoryTotal - (trajectoryProgress + 1));
    predictedGeo.setDrawRange(trajectoryProgress + 1, remain);
    predictedLine.computeLineDistances();
  }

  function getSolarInjectionState(v0, earthAngle = state.earthOrbitAngle) {
    const earthCenter = new THREE.Vector3(
      Math.cos(earthAngle) * EARTH_ORBIT_RADIUS_KM,
      0,
      Math.sin(earthAngle) * EARTH_ORBIT_RADIUS_KM
    );
    const radialOut = earthCenter.clone().normalize();
    const tangent = new THREE.Vector3(-Math.sin(earthAngle), 0, Math.cos(earthAngle)).normalize();

    const excessFromSecond = Math.max(0, v0 - SECOND_COSMIC_STANDARD);
    const secondToThirdSpan = Math.max(1e-6, THIRD_COSMIC_STANDARD - SECOND_COSMIC_STANDARD);
    const phase23 = THREE.MathUtils.clamp(excessFromSecond / secondToThirdSpan, 0, 1);

    // 发射点放在地球赤道附近（不再从地心发射）
    const equatorDir = tangent.clone();
    // 先从赤道表面出发，再叠加“轻微”径向偏移：形成轻微椭圆而非近圆重合
    const extraRadialOffsetKm = R0 * (0.08 + 0.22 * Math.pow(phase23, 1.15));
    const position = earthCenter.clone()
      .add(equatorDir.clone().multiplyScalar(R0))
      .add(radialOut.clone().multiplyScalar(extraRadialOffsetKm));

    // 11.2 附近保持与地球接近同周期：切向/径向增量在阈值附近从 0 平滑起步
    const nearSecondSoft = Math.max(0, excessFromSecond - 0.05);

    // 速度注入做平滑过渡，避免 16.6 -> 16.7 轨迹突变
    const baseTangent = 0.34 * nearSecondSoft;
    // 给一个更明显的正径向速度，让轨道在视觉上与地球轨道分离但周期仍接近
    const baseRadial = 0.24 + 0.12 * nearSecondSoft;

    const blendStart = THIRD_COSMIC_STANDARD - 0.25;
    const blendEnd = THIRD_COSMIC_STANDARD + 0.45;
    const blendRaw = (v0 - blendStart) / Math.max(1e-6, (blendEnd - blendStart));
    const blend = THREE.MathUtils.clamp(blendRaw, 0, 1);
    const smooth = blend * blend * (3 - 2 * blend); // smoothstep

    const thirdExcess = Math.max(0, v0 - THIRD_COSMIC_STANDARD);
    const extraTangent = 0.95 * thirdExcess + 0.28 * smooth * Math.max(0, v0 - blendStart);
    const extraRadial = 0.52 * thirdExcess + 0.14 * smooth * Math.max(0, v0 - blendStart);

    const tangentBoost = baseTangent + extraTangent;
    const radialBoost = baseRadial + extraRadial;

    let velocity = tangent.clone().multiplyScalar(EARTH_SOLAR_ORBIT_SPEED + tangentBoost)
      .add(radialOut.clone().multiplyScalar(radialBoost));

    // 16.7 及以上保证进入双曲逃逸（相对太阳总能量 > 0）
    if (v0 >= THIRD_COSMIC_STANDARD) {
      const rSun = Math.max(1, position.length());
      const vEscSun = Math.sqrt((2 * MU_SUN_SIM) / rSun);
      const minHyperbolicSpeed = vEscSun * 1.015 + 0.12 * (v0 - THIRD_COSMIC_STANDARD);
      if (velocity.length() < minHyperbolicSpeed) {
        velocity = velocity.clone().setLength(minHyperbolicSpeed);
      }
    }

    return { position, velocity };
  }

  function computeEarthTrajectoryPoints(v0) {
    const pts = [];

    const rVec0 = new THREE.Vector3(R0, 0, 0);
    const vVec0 = new THREE.Vector3(0, v0, 0);
    const r0 = rVec0.length();
    const v02 = vVec0.lengthSq();
    const eps0 = 0.5 * v02 - MU_EARTH_DEMO / r0;
    const hVec0 = new THREE.Vector3().crossVectors(rVec0, vVec0);
    const h0 = hVec0.length();
    const e0 = Math.sqrt(Math.max(0, 1 + (2 * eps0 * h0 * h0) / (MU_EARTH_DEMO * MU_EARTH_DEMO)));

    if (e0 < 1) {
      const p = (h0 * h0) / MU_EARTH_DEMO;
      const samples = Math.min(MAX_TRAJ_POINTS, 6000);
      for (let i = 0; i <= samples; i += 1) {
        const theta = (i / samples) * Math.PI * 2;
        const r = p / (1 + e0 * Math.cos(theta));
        if (!Number.isFinite(r) || r <= EARTH_RADIUS) continue;
        pts.push(new THREE.Vector3(r * Math.cos(theta) * KM_TO_SCENE, r * Math.sin(theta) * KM_TO_SCENE, 0));
      }
      return pts;
    }

    let p = new THREE.Vector3(R0, 0, 0);
    let v = new THREE.Vector3(0, v0, 0);
    const h = 0.35;
    let t = 0;
    const targetTime = 26000;

    for (let i = 0; i < MAX_TRAJ_POINTS && t < targetTime; i += 1) {
      pts.push(new THREE.Vector3(p.x * KM_TO_SCENE, p.y * KM_TO_SCENE, 0));

      const r = p.length();
      if (r <= EARTH_RADIUS) break;
      if (r > 260 * R0) break;

      const a0 = p.clone().multiplyScalar(-MU_EARTH_DEMO / Math.pow(Math.max(1, r), 3));
      const pNext = p.clone().addScaledVector(v, h).addScaledVector(a0, 0.5 * h * h);
      const rNext = pNext.length();
      const a1 = pNext.clone().multiplyScalar(-MU_EARTH_DEMO / Math.pow(Math.max(1, rNext), 3));
      const vNext = v.clone().addScaledVector(a0.clone().add(a1), 0.5 * h);

      p = pNext;
      v = vNext;
      t += h;
    }

    return pts;
  }

  function getSolarAccel(positionKm, earthAngle, includeEarthGravity = false) {
    let aEarth = new THREE.Vector3();

    if (includeEarthGravity) {
      const earthPos = new THREE.Vector3(
        Math.cos(earthAngle) * EARTH_ORBIT_RADIUS_KM,
        0,
        Math.sin(earthAngle) * EARTH_ORBIT_RADIUS_KM
      );

      const relToEarth = positionKm.clone().sub(earthPos);
      const rEarth = relToEarth.length();
      let earthFactor = 1;
      if (rEarth > EARTH_DECAY_START) {
        const tDecay = Math.min(1, (rEarth - EARTH_DECAY_START) / (EARTH_DECAY_END - EARTH_DECAY_START));
        earthFactor = 1 - tDecay;
      }

      aEarth = rEarth > 1
        ? relToEarth.multiplyScalar((-MU * earthFactor) / Math.pow(rEarth, 3))
        : new THREE.Vector3();
    }

    const rSun = positionKm.length();
    const aSun = rSun > 1
      ? positionKm.clone().multiplyScalar(-MU_SUN_SIM / Math.pow(rSun, 3))
      : new THREE.Vector3();

    return aEarth.add(aSun);
  }

  function propagateSolarState(positionKm, velocityKmS, earthAngle, dt, includeEarthGravity = false) {
    // 注意：step() 里已经把 dt 放大为 solarDt，这里不再重复乘演示倍率
    const e = EARTH_ORBIT_ECCENTRICITY;
    const angScale = (1 + e * Math.cos(earthAngle)) * (1 + e * Math.cos(earthAngle))
      / Math.pow(1 - e * e, 1.5);
    const nextEarthAngle = earthAngle + EARTH_ORBIT_ANGULAR_SPEED * angScale * dt;

    const a0 = getSolarAccel(positionKm, earthAngle, includeEarthGravity);
    const pNext = positionKm.clone()
      .addScaledVector(velocityKmS, dt)
      .addScaledVector(a0, 0.5 * dt * dt);
    const a1 = getSolarAccel(pNext, nextEarthAngle, includeEarthGravity);
    const vNext = velocityKmS.clone().addScaledVector(a0.clone().add(a1), 0.5 * dt);

    return { position: pNext, velocity: vNext, earthAngle: nextEarthAngle };
  }

  function computeSolarTrajectoryPoints(v0) {
    const pts = [];
    const init = getSolarInjectionState(v0, 0);
    let p = init.position.clone();
    let v = init.velocity.clone();
    let earthAngle = 0;

    const h = 6;
    const subStepsPerPoint = 8;
    let t = 0;
    const targetTime = 240 * 24 * 3600;

    for (let i = 0; i < MAX_TRAJ_POINTS && t < targetTime; i += 1) {
      pts.push(new THREE.Vector3(p.x * KM_TO_SCENE, p.y * KM_TO_SCENE, p.z * KM_TO_SCENE));

      const rSun = p.length();
      if (rSun < SUN_VISUAL_RADIUS / KM_TO_SCENE * 1.2) break;
      if (rSun > EARTH_ORBIT_RADIUS_KM * 4.2) break;

      for (let s = 0; s < subStepsPerPoint; s += 1) {
        const next = propagateSolarState(p, v, earthAngle, h, false);
        p = next.position;
        v = next.velocity;
        earthAngle = next.earthAngle;
        t += h;
      }
    }

    return pts;
  }

  function computeTrajectoryPoints(v0) {
    return v0 >= SECOND_COSMIC_STANDARD ? computeSolarTrajectoryPoints(v0) : computeEarthTrajectoryPoints(v0);
  }

  function buildPreview(v0) {
    const pts = computeTrajectoryPoints(v0);
    previewGeo.setFromPoints(pts);
    previewGeo.computeBoundingSphere();
    previewLine.computeLineDistances();

    trajectoryTotal = Math.min(pts.length, MAX_TRAJ_POINTS);
    for (let i = 0; i < trajectoryTotal; i += 1) {
      const j = i * 3;
      predictedArr[j] = pts[i].x;
      predictedArr[j + 1] = pts[i].y;
      predictedArr[j + 2] = pts[i].z;
    }
    predictedGeo.setDrawRange(0, trajectoryTotal);
    predictedGeo.attributes.position.needsUpdate = true;
    predictedLine.computeLineDistances();

    trajectoryProgress = 0;
    traveledGeo.setDrawRange(0, 0);
    traveledGeo.attributes.position.needsUpdate = true;
  }

  function fitCameraToPreview() {
    const bs = previewGeo.boundingSphere;
    if (!bs) return;

    const radius = Math.max(22, bs.radius);
    const center = bs.center.clone();
    const fov = (camera.fov * Math.PI) / 180;
    const fitDist = (radius * 1.28) / Math.tan(fov / 2);
    const dist = Math.min(520, Math.max(85, fitDist));

    const desiredPos = center.clone().add(new THREE.Vector3(-dist * 0.58, dist * 0.34, dist * 0.58));
    camera.position.copy(desiredPos);
    controls.target.copy(center);
    controls.update();
  }

  function setSolarViewCamera() {
    const shipPos = state.position.clone().multiplyScalar(KM_TO_SCENE);
    const earthPos = earth.position.clone();
    const sunPos = sunMesh.getWorldPosition(new THREE.Vector3());
    const focus = shipPos.clone().multiplyScalar(0.5).add(earthPos.clone().multiplyScalar(0.3)).add(sunPos.clone().multiplyScalar(0.2));
    const viewDir = new THREE.Vector3(-0.78, 0.32, 0.62).normalize();
    controls.target.copy(focus);
    camera.position.copy(focus.clone().add(viewDir.multiplyScalar(620)));
    camera.lookAt(focus);
    controls.update();
  }

  function updateCompareTrajectories() {
    if (!compareMode || state.thirdMode) {
      compareLines.v1.visible = false;
      compareLines.v2.visible = false;
      compareLines.v3.visible = false;
      return;
    }

    compareGeos.v1.setFromPoints(computeTrajectoryPoints(FIRST_COSMIC_STANDARD));
    compareGeos.v2.setFromPoints(computeTrajectoryPoints(SECOND_COSMIC_STANDARD));
    compareGeos.v3.setFromPoints(computeTrajectoryPoints(THIRD_COSMIC_STANDARD));

    compareLines.v1.visible = true;
    compareLines.v2.visible = true;
    compareLines.v3.visible = true;
  }

  function updatePhysicalDashboard() {
    const elE = document.getElementById('val-e');
    const elPeri = document.getElementById('val-peri');
    const elApo = document.getElementById('val-apo');
    const elSpeed = document.getElementById('val-speed');
    const elEnergy = document.getElementById('val-energy');
    const elAngMom = document.getElementById('val-angmom');

    const relPos = state.thirdMode ? state.position.clone().sub(getEarthCenterKm()) : state.position.clone();
    const r = relPos.length();
    const v = state.velocity.length();
    const muRef = MU_EARTH_DEMO;

    if (elSpeed) elSpeed.textContent = `${v.toFixed(2)} km/s`;

    if (r < 1) {
      if (elE) elE.textContent = '—';
      if (elPeri) elPeri.textContent = '—';
      if (elApo) elApo.textContent = '—';
      if (elEnergy) elEnergy.textContent = '—';
      if (elAngMom) elAngMom.textContent = '—';
      return;
    }

    const eps = 0.5 * v * v - muRef / r;
    const hVec = new THREE.Vector3().crossVectors(relPos, state.velocity);
    const h = hVec.length();
    const e = Math.sqrt(Math.max(0, 1 + (2 * eps * h * h) / (muRef * muRef)));

    let rp = NaN;
    let ra = NaN;
    if (Math.abs(e - 1) < 1e-3) {
      rp = (h * h) / (2 * muRef);
      ra = Infinity;
    } else {
      const p = (h * h) / muRef;
      rp = p / (1 + e);
      ra = e < 1 ? p / (1 - e) : Infinity;
    }

    if (elE) elE.textContent = Number.isFinite(e) ? e.toFixed(3) : '—';
    if (elPeri) elPeri.textContent = Number.isFinite(rp) ? `${Math.max(0, rp - EARTH_RADIUS).toFixed(0)} km` : '—';
    if (elApo) elApo.textContent = Number.isFinite(ra) ? `${Math.max(0, ra - EARTH_RADIUS).toFixed(0)} km` : '∞';
    if (elEnergy) elEnergy.innerHTML = `${eps.toFixed(3)} km<sup>2</sup>/s<sup>2</sup>`;
    if (elAngMom) elAngMom.innerHTML = `${h.toFixed(3)} km<sup>2</sup>/s`;
  }

  function updateCosmicUI() {
    const v0 = Number(sliderV?.value ?? '5.0');
    const orbitTypeEl = document.getElementById('orbit-type');
    const systemStatus = document.getElementById('system-status');
    const aiText = document.getElementById('ai-text');

    if (state.thirdMode) {
      if (state.launchV0 >= THIRD_COSMIC_STANDARD) {
        if (orbitTypeEl) orbitTypeEl.textContent = '第三宇宙速度工况（太阳系逃逸演示）';
        if (systemStatus) systemStatus.textContent = state.solarEscaped ? '已飞出太阳系展示区域' : '太阳系逃逸进行中';
        if (aiText) aiText.textContent = '当前演示为太阳-地球双引力场，飞船将逐步远离太阳系内圈。';
      } else {
        if (orbitTypeEl) orbitTypeEl.textContent = '第二宇宙速度以上（脱离地球并绕太阳运行）';
        if (systemStatus) systemStatus.textContent = '已逃逸地球，进入绕日轨道';
        if (aiText) aiText.textContent = '飞船已摆脱地球束缚，正在太阳引力主导下运行。';
      }
      if (orbitTypeEl) orbitTypeEl.style.color = '#ffdd55';
      if (systemStatus) systemStatus.style.color = '#ffdd55';
      trailMat.color.set(0xffdd55);
      updatePhysicalDashboard();
      return;
    }

    if (v0 < SECOND_COSMIC_STANDARD) {
      if (orbitTypeEl) orbitTypeEl.textContent = '地球束缚轨道';
      if (systemStatus) systemStatus.textContent = '未逃逸地球';
      trailMat.color.set(0x55ccff);
    } else if (v0 < THIRD_COSMIC_STANDARD) {
      if (orbitTypeEl) orbitTypeEl.textContent = '第二宇宙速度附近（地球逃逸）';
      if (systemStatus) systemStatus.textContent = '已逃逸地球';
      trailMat.color.set(0xffdd55);
    } else {
      if (orbitTypeEl) orbitTypeEl.textContent = '第三宇宙速度阈值（可切太阳系演示）';
      if (systemStatus) systemStatus.textContent = '第三宇宙速度阈值已达到';
      trailMat.color.set(0xffdd55);
    }

    updatePhysicalDashboard();
  }

  function setInitial(v0) {
    state.position.set(R0, 0, 0);
    state.velocity.set(0, v0, 0);
    state.launched = false;
    state.thirdMode = false;
    state.solarEscaped = false;
    state.earthOrbitAngle = 0;
    state.solarDeploy = 0;
    state.launchV0 = v0;

    resetStageSeparation();
    setSolarPanelDeploy(0);

    earth.position.set(0, 0, 0);
    cloudLayer.position.copy(earth.position);
    atmosphere.position.copy(earth.position);
    atmosphereRim.position.copy(earth.position);

    earth.rotation.set(EARTH_AXIAL_TILT, 0, 0);
    cloudLayer.rotation.set(EARTH_AXIAL_TILT, 0, 0);
    atmosphere.rotation.set(EARTH_AXIAL_TILT, 0, 0);
    atmosphereRim.rotation.set(EARTH_AXIAL_TILT, 0, 0);

    ship.position.copy(state.position).multiplyScalar(KM_TO_SCENE);
    orientShipByVelocity();
    updateShipVisibilityForScene();
    resetTrail();
    buildPreview(v0);
    previewLine.visible = true;

    solarGroup.visible = false;
    solarPlanetGroup.visible = false;

    // 回到地心模式时恢复轨道显示
    previewLine.visible = true;
    predictedLine.visible = true;
    traveledLine.visible = true;

    camera.position.set(0, 24, 120);
    controls.target.set(0, 0, 0);
    controls.update();

    updateCosmicUI();
  }

  const sliderV = document.getElementById('slider-v');
  const valV0 = document.getElementById('val-v0');
  const sliderTime = document.getElementById('slider-time');
  const valTime = document.getElementById('val-time-scale');

  const markV1 = document.getElementById('mark-v1');
  const markV2 = document.getElementById('mark-v2');
  const markV3 = document.getElementById('mark-v3');
  const speedZoneText = document.getElementById('speed-zone-text');

  function updateSpeedMarks() {
    if (!sliderV) return;

    const min = Number(sliderV.min || 5);
    const max = Number(sliderV.max || 20);
    const span = Math.max(0.001, max - min);

    function placeMark(el, v) {
      if (!el) return;
      const pct = ((v - min) / span) * 100;
      el.style.left = `${Math.max(0, Math.min(100, pct))}%`;
    }

    placeMark(markV1, STAGE_1_MAX);
    placeMark(markV2, STAGE_2_MAX);
    placeMark(markV3, STAGE_3_MAX);

    const current = Number(sliderV.value || FIRST_COSMIC_STANDARD);
    if (speedZoneText) {
      if (current < FIRST_COSMIC_STANDARD) {
        speedZoneText.textContent = '当前区间：低于第一宇宙速度（将再入）';
      } else if (current < SECOND_COSMIC_STANDARD) {
        speedZoneText.textContent = '当前区间：第一~第二宇宙速度（绕地）';
      } else if (current < THIRD_COSMIC_STANDARD) {
        speedZoneText.textContent = '当前区间：第二~第三宇宙速度（逃离地球并绕日）';
      } else {
        speedZoneText.textContent = '当前区间：第三宇宙速度及以上（可太阳系逃逸）';
      }
    }
  }

  document.getElementById('btn-launch')?.addEventListener('click', () => {
    finalizeCurrentExperiment('new_launch');

    const v0 = Number(sliderV?.value ?? '7.9');
    const keepCameraOnLaunch = v0 >= SECOND_COSMIC_STANDARD;
    const savedCamPos = camera.position.clone();
    const savedTarget = controls.target.clone();

    setInitial(v0);

    if (keepCameraOnLaunch) {
      camera.position.copy(savedCamPos);
      controls.target.copy(savedTarget);
      controls.update();
    }

    state.launched = true;
    previewLine.visible = false;

    state.currentExperimentId = {
      id: (experimentResults[0]?.id || 0) + 1,
      startMs: performance.now(),
      thirdMode: v0 >= SECOND_COSMIC_STANDARD,
    };

    if (v0 >= SECOND_COSMIC_STANDARD) {
      state.thirdMode = true;
      state.launchV0 = v0;
      state.solarTime = 0;
      solarGroup.visible = true;
      solarPlanetGroup.visible = true;

      // 切入太阳系视图时：保留预测/已飞轨迹，仅关闭对比线
      previewLine.visible = false;
      predictedLine.visible = true;
      traveledLine.visible = true;
      compareLines.v1.visible = false;
      compareLines.v2.visible = false;
      compareLines.v3.visible = false;

      const inject = getSolarInjectionState(v0, state.earthOrbitAngle);
      state.position.copy(inject.position);
      state.velocity.copy(inject.velocity);

      const ecScene = getEarthCenterKm().multiplyScalar(KM_TO_SCENE);
      earth.position.copy(ecScene);
      cloudLayer.position.copy(ecScene);
      atmosphere.position.copy(ecScene);
      atmosphereRim.position.copy(ecScene);

      // 切到太阳系动力学后，重建预测轨迹与已飞轨迹基准
      buildPreview(v0);
      resetTrail();
      predictedLine.visible = true;
      traveledLine.visible = true;

      addLog(v0 >= THIRD_COSMIC_STANDARD
        ? `发射：${v0.toFixed(1)} km/s（第三宇宙速度，太阳系逃逸演示）`
        : `发射：${v0.toFixed(1)} km/s（超过第二宇宙速度，进入绕日运动）`);
    } else {
      state.launchV0 = v0;
      addLog(`发射：${v0.toFixed(1)} km/s`);
    }

    updateCosmicUI();
  });

  document.getElementById('btn-reset')?.addEventListener('click', () => {
    finalizeCurrentExperiment('reset');
    const v0 = Number(sliderV?.value ?? '7.9');
    setInitial(v0);
    addLog('重置完成');
  });

  function applySpeedStage(stage) {
    if (!sliderV) return;

    let min = 5.0;
    let max = 20.0;
    let target = Number(sliderV.value || FIRST_COSMIC_STANDARD);

    if (stage === 1) {
      min = 5.0;
      max = STAGE_1_MAX;
      target = 7.0;
    } else if (stage === 2) {
      min = FIRST_COSMIC_STANDARD;
      max = STAGE_2_MAX;
      target = FIRST_COSMIC_STANDARD;
    } else if (stage === 3) {
      min = SECOND_COSMIC_STANDARD;
      max = STAGE_3_MAX;
      target = SECOND_COSMIC_STANDARD;
    } else if (stage === 4) {
      min = THIRD_COSMIC_STANDARD;
      max = 20.0;
      target = THIRD_COSMIC_STANDARD;
    }

    sliderV.min = String(min);
    sliderV.max = String(max);
    sliderV.value = String(target);

    if (valV0) valV0.textContent = Number(target).toFixed(1);

    // 切换阶段时，清空上一轮发射轨迹并结束当前飞行
    finalizeCurrentExperiment('stage_switch');
    state.launched = false;
    resetTrail();

    // 复用滑条 input 逻辑：自动同步地心/太阳系视角与预轨迹
    sliderV.dispatchEvent(new Event('input'));
  }

  document.getElementById('btn-stage-1')?.addEventListener('click', () => applySpeedStage(1));
  document.getElementById('btn-stage-2')?.addEventListener('click', () => applySpeedStage(2));
  document.getElementById('btn-stage-3')?.addEventListener('click', () => applySpeedStage(3));
  document.getElementById('btn-stage-4')?.addEventListener('click', () => applySpeedStage(4));

  sliderV?.addEventListener('input', (e) => {
    const v = Number(e.target.value);
    if (valV0) valV0.textContent = v.toFixed(1);

    if (!state.launched) {
      const wasSolarView = state.thirdMode;
      state.launchV0 = v;

      if (v >= SECOND_COSMIC_STANDARD) {
        state.thirdMode = true;
        state.solarEscaped = false;
        state.earthOrbitAngle = 0;

        solarGroup.visible = true;
        solarPlanetGroup.visible = true;

        const injected = getSolarInjectionState(v, state.earthOrbitAngle);
        state.position.copy(injected.position);
        state.velocity.copy(injected.velocity);

        const ecScene = getEarthCenterKm().multiplyScalar(KM_TO_SCENE);
        earth.position.copy(ecScene);
        cloudLayer.position.copy(ecScene);
        atmosphere.position.copy(ecScene);
        atmosphereRim.position.copy(ecScene);

        if (!wasSolarView) {
          setSolarViewCamera();
        }
      } else {
        state.thirdMode = false;
        solarGroup.visible = false;
        solarPlanetGroup.visible = false;

        state.position.set(R0, 0, 0);
        state.velocity.set(0, v, 0);

        earth.position.set(0, 0, 0);
        cloudLayer.position.copy(earth.position);
        atmosphere.position.copy(earth.position);
        atmosphereRim.position.copy(earth.position);

        if (wasSolarView) {
          controls.target.set(0, 0, 0);
          camera.position.set(0, 24, 120);
          controls.update();
        }
      }

      ship.position.copy(state.position).multiplyScalar(KM_TO_SCENE);
      orientShipByVelocity();
      updateCosmicUI();
    }

    buildPreview(v);
    previewLine.visible = true;
    updateCompareTrajectories();
    updateSpeedMarks();
  });

  sliderTime?.addEventListener('input', (e) => {
    state.timeScale = Number(e.target.value);
    if (valTime) valTime.textContent = `${state.timeScale}x`;
  });



  function step(dt) {
    if (state.thirdMode) {
      const solarDt = dt * SOLAR_DEMO_TIME_SCALE;
      const next = propagateSolarState(state.position, state.velocity, state.earthOrbitAngle, solarDt, false);
      state.position.copy(next.position);
      state.velocity.copy(next.velocity);
      state.earthOrbitAngle = next.earthAngle;

      const ec = getEarthCenterKm();
      const ecScene = ec.clone().multiplyScalar(KM_TO_SCENE);
      earth.position.copy(ecScene);
      cloudLayer.position.copy(ecScene);
      atmosphere.position.copy(ecScene);

      if (state.position.length() > EARTH_ORBIT_RADIUS_KM * 2.8 && !state.solarEscaped) {
        state.solarEscaped = true;
        addLog('第三宇宙速度效果：飞船已飞出太阳系展示区域');
      }
    } else {
      // 地心二体段改用 velocity-Verlet，显著降低能量漂移
      const subSteps = Math.max(1, Math.ceil(dt / 0.35));
      const h = dt / subSteps;

      for (let i = 0; i < subSteps; i += 1) {
        const r0 = state.position.length();
        const a0 = state.position.clone().multiplyScalar(-MU_EARTH_DEMO / Math.pow(Math.max(1, r0), 3));

        const pNext = state.position.clone()
          .addScaledVector(state.velocity, h)
          .addScaledVector(a0, 0.5 * h * h);

        const r1 = pNext.length();
        const a1 = pNext.clone().multiplyScalar(-MU_EARTH_DEMO / Math.pow(Math.max(1, r1), 3));

        state.velocity.addScaledVector(a0.clone().add(a1), 0.5 * h);
        state.position.copy(pNext);

        if (state.position.length() <= EARTH_RADIUS) {
          triggerEarthImpactExplosion();
          state.launched = false;
          previewLine.visible = true;
          addLog('再入地球，发生撞击爆炸');
          finalizeCurrentExperiment('reentry');
          break;
        }
      }
    }

    ship.position.copy(state.position).multiplyScalar(KM_TO_SCENE);
    orientShipByVelocity();
    updateShipVisibilityForScene();
    pushTrail(state.position);
    updateCosmicUI();
  }

  const clock = new THREE.Clock();
  let rafId = 0;
  let isDisposed = false;

  function animate() {
    if (isDisposed) return;
    const delta = clock.getDelta();
    const t = performance.now() * 0.001;

    if (state.launched) {
      state.solarDeploy = Math.min(1, state.solarDeploy + delta * 0.5);
    } else {
      state.solarDeploy = Math.max(0, state.solarDeploy - delta * 0.8);
    }
    setSolarPanelDeploy(state.solarDeploy);

    if (state.launched && !state.stageSeparated && state.position.length() > EARTH_RADIUS + 900) {
      state.stageSeparated = true;
      state.shockFlash = 1;
      state.cameraShake = 1;
      addLog('近地阶段：二级分离已触发');

      for (let i = 0; i < separationDebris.length; i += 1) {
        const d = separationDebris[i];
        d.visible = true;
        d.position.set(-1.3 + Math.random() * 0.2, (Math.random() - 0.5) * 0.15, (Math.random() - 0.5) * 0.15);
        d.userData.vel.set(-0.7 - Math.random() * 0.9, (Math.random() - 0.5) * 0.9, (Math.random() - 0.5) * 0.9);
        d.userData.spin.set((Math.random() - 0.5) * 12, (Math.random() - 0.5) * 12, (Math.random() - 0.5) * 12);
        d.userData.life = 0.9 + Math.random() * 0.7;
      }
    }
    if (state.stageSeparated) {
      state.stageSepT += delta;
      stageBooster.position.x = -state.stageSepT * 1.6;
      stageBooster.position.y = -0.2 - state.stageSepT * 0.7;
      stageBooster.rotation.z = state.stageSepT * 1.9;
      if (state.stageSepT > 4.2) stageBooster.visible = false;
    }

    if (state.shockFlash > 0) {
      state.shockFlash = Math.max(0, state.shockFlash - delta * 4.8);
    }
    sepFlash.material.opacity = state.shockFlash * 0.9;
    const flashScale = 1 + (1 - state.shockFlash) * 2.6;
    sepFlash.scale.set(flashScale, flashScale, flashScale);

    for (const d of separationDebris) {
      if (!d.visible) continue;
      d.userData.life -= delta;
      if (d.userData.life <= 0) {
        d.visible = false;
        continue;
      }
      d.position.addScaledVector(d.userData.vel, delta);
      d.userData.vel.multiplyScalar(0.985);
      d.rotation.x += d.userData.spin.x * delta;
      d.rotation.y += d.userData.spin.y * delta;
      d.rotation.z += d.userData.spin.z * delta;
    }

    if (impactFlash.visible) {
      impactFlash.scale.multiplyScalar(1 + delta * 3.4);
      impactFlash.material.opacity = Math.max(0, impactFlash.material.opacity - delta * 2.8);
      if (impactFlash.material.opacity <= 0.01) impactFlash.visible = false;
    }

    for (const d of impactDebris) {
      if (!d.visible) continue;
      d.userData.life -= delta;
      if (d.userData.life <= 0) {
        d.visible = false;
        continue;
      }
      d.position.addScaledVector(d.userData.vel, delta);
      d.userData.vel.multiplyScalar(0.975);
      d.material.opacity = Math.max(0, d.userData.life / Math.max(0.001, d.userData.maxLife));
    }

    earth.rotation.y += delta * 0.1;
    cloudLayer.rotation.y += delta * 0.135;
    atmosphere.rotation.y += delta * 0.03;
    atmosphereRim.rotation.y += delta * 0.02;

    starLayerNear.rotation.y += delta * 0.003;
    starLayerFar.rotation.y -= delta * 0.0015;
    brightStars.rotation.y += delta * 0.005;
    brightStars.material.opacity = 0.78 + 0.14 * Math.sin(t * 0.9);
    for (let i = 0; i < nebulaSprites.length; i += 1) {
      const n = nebulaSprites[i];
      n.material.opacity = 0.12 + 0.08 * Math.sin(t * 0.25 + i * 0.7);
      n.material.rotation += delta * 0.02 * (i % 2 === 0 ? 1 : -1);
    }

    sunMesh.rotation.y += delta * 0.06;
    sunDetailLayer.rotation.y -= delta * 0.21;
    sunDetailLayer.rotation.x = 0.04 * Math.sin(t * 0.28);
    sunDetailLayer.material.opacity = 0.34 + 0.14 * Math.sin(t * 1.25 + 0.7);
    sunMesh.material.emissiveIntensity = 1.75 + 0.18 * Math.sin(t * 1.6);

    for (let i = 0; i < coronaSprites.length; i += 1) {
      const sp = coronaSprites[i];
      const k = i + 1;
      sp.material.opacity = 0.08 + 0.08 * (0.5 + 0.5 * Math.sin(t * (0.55 + 0.08 * k) + k * 1.7));
      const s = SUN_VISUAL_RADIUS * (3.0 + 0.9 * (0.5 + 0.5 * Math.sin(t * (0.22 + 0.04 * k) + k)));
      sp.scale.set(s, s, 1);
      sp.material.rotation += delta * (0.08 + 0.03 * (k % 3));
      sp.position.x = Math.sin(t * (0.18 + 0.02 * k) + k) * 4.0;
      sp.position.y = Math.cos(t * (0.21 + 0.015 * k) + k * 0.7) * 3.2;
      sp.position.z = Math.sin(t * (0.16 + 0.02 * k) + k * 1.2) * 2.8;
    }

    const sunDir = earth.position.clone().normalize().multiplyScalar(-1);
    light.position.copy(sunDir.clone().multiplyScalar(420));

    const speed = state.velocity.length();
    const thrustNorm = Math.min(1.6, Math.max(0.5, speed / 8.5));
    flame.scale.x = 1 + 0.55 * thrustNorm + 0.12 * Math.sin(t * 18);
    flame.scale.y = 1 + 0.08 * Math.sin(t * 22 + 0.8);
    flame.scale.z = 1 + 0.08 * Math.cos(t * 20 + 0.3);
    flame.material.opacity = 0.62 + 0.22 * Math.sin(t * 16 + 0.5);

    flameCore.scale.x = 1 + 0.42 * thrustNorm + 0.1 * Math.cos(t * 19);
    flameCore.material.opacity = 0.72 + 0.2 * Math.sin(t * 17 + 1.1);

    cockpit.material.emissiveIntensity = 0.8 + 0.35 * Math.sin(t * 2.6);
    navLightL.material.opacity = 0.45 + 0.5 * (0.5 + 0.5 * Math.sin(t * 10));
    navLightR.material.opacity = 0.45 + 0.5 * (0.5 + 0.5 * Math.sin(t * 10 + Math.PI));

    if (state.stageSeparated) {
      boosterFlame.material.opacity = Math.max(0, 0.9 - state.stageSepT * 0.45);
      boosterFlame.scale.x = Math.max(0.35, 1 - state.stageSepT * 0.28);
    } else {
      boosterFlame.material.opacity = 0.88;
      boosterFlame.scale.x = 1 + 0.18 * Math.sin(t * 14);
    }

    if (state.thirdMode) {
      updateSolarPlanets(delta);
    }

    if (state.launched) {
      step(Math.min(18, delta * state.timeScale * 15));
      updateTrajectoryProgressByShip();
    }

    if (state.thirdMode) {
      const shipPos = state.position.clone().multiplyScalar(KM_TO_SCENE);
      const earthPos = earth.position.clone();
      const sunPos = sunMesh.getWorldPosition(new THREE.Vector3());

      const target = new THREE.Vector3().add(shipPos).add(earthPos).add(sunPos.multiplyScalar(1.25)).multiplyScalar(1 / 3.25);

      const now = performance.now();
      const autoFollowEnabled = AUTO_FOLLOW_IN_SOLAR_VIEW && !isUserControllingCamera && (now - lastUserCameraActionAt > 1800);
      if (autoFollowEnabled) {
        controls.target.lerp(target, 0.08);

        const span = Math.max(earthPos.length(), shipPos.length(), 720);
        const dist = Math.min(1050, Math.max(560, span * 1.05));
        const desiredCam = target.clone().add(new THREE.Vector3(-dist * 0.62, dist * 0.34, dist * 0.72));
        camera.position.lerp(desiredCam, 0.06);
      }
    }

    if (state.cameraShake > 0) {
      const amp = state.cameraShake * 1.15;
      camera.position.x += (Math.random() - 0.5) * amp;
      camera.position.y += (Math.random() - 0.5) * amp;
      camera.position.z += (Math.random() - 0.5) * amp;
      state.cameraShake = Math.max(0, state.cameraShake - delta * 3.5);
    }

    controls.update();
    renderer.render(scene, camera);
    rafId = requestAnimationFrame(animate);
  }

  function disposeObject3D(obj) {
    if (!obj) return;
    obj.traverse((node) => {
      if (node.geometry && typeof node.geometry.dispose === 'function') {
        node.geometry.dispose();
      }
      const mats = Array.isArray(node.material) ? node.material : [node.material];
      for (const m of mats) {
        if (!m) continue;
        for (const key of Object.keys(m)) {
          const v = m[key];
          if (v && typeof v.dispose === 'function' && v !== m) {
            try { v.dispose(); } catch {}
          }
        }
        if (typeof m.dispose === 'function') {
          try { m.dispose(); } catch {}
        }
      }
    });
  }

  function cleanup() {
    if (isDisposed) return;
    isDisposed = true;

    if (rafId) cancelAnimationFrame(rafId);

    window.removeEventListener('resize', onResize);
    controls.removeEventListener('start', onControlStart);
    controls.removeEventListener('end', onControlEnd);

    for (const t of [
      earthTextures?.map,
      earthTextures?.bumpMap,
      earthTextures?.roughnessMap,
      earthTextures?.nightMap,
      cloudTexture,
      nebulaTexture,
      sunNoiseBumpTexture,
      sunDetailTexture,
    ]) {
      if (t && typeof t.dispose === 'function') {
        try { t.dispose(); } catch {}
      }
    }

    disposeObject3D(scene);

    controls.dispose();
    renderer.dispose();
    try { renderer.renderLists.dispose(); } catch {}

  }

  function onResize() {
    if (isDisposed) return;
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  }

  function onVisibilityChange() {
    if (document.visibilityState === 'hidden') {
      cleanup();
    }
  }

  window.addEventListener('resize', onResize);
  window.addEventListener('beforeunload', cleanup);
  window.addEventListener('pagehide', cleanup);
  document.addEventListener('visibilitychange', onVisibilityChange);

  setInitial(5.0);
  updateSpeedMarks();
  animate();
})();