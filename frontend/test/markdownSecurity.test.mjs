import { test } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { renderMarkdown as render } from '../src/utils/markdown.ts';
globalThis.window = new JSDOM('').window;
for (const payload of ['<img src=x onerror="alert(1)">','<script>alert(1)</script>','[click](javascript:alert%281%29)']) {
 test('AI markup is safe: '+payload,()=>{assert.doesNotMatch(render(payload),/onerror\s*=|<script|href\s*=\s*["']javascript:/i);});
}
test('math and markdown remain readable',()=>{const html=render('## 能量\n\n$E=mc^2$\n\n- item');assert.match(html,/katex/);assert.match(html,/<h2/);assert.match(html,/<li/);});
