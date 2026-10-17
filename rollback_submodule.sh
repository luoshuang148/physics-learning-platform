#!/bin/bash
echo "正在回滚子模块到: e7525caa2438c3c8c820bfad027e0718c46fde79"
cd frontend/public/three-physical-project
git checkout e7525caa2438c3c8c820bfad027e0718c46fde79
cd ../..
git add frontend/public/three-physical-project
git commit -m "回滚实验组件到版本 e7525caa2438c3c8c820bfad027e0718c46fde79"
git push origin front
echo "回滚完成"
