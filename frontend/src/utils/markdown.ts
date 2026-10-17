import createDOMPurify from 'dompurify';
import { marked } from 'marked';
import katex from 'katex';

/** All model text is untrusted, including streamed fragments and saved reports. */
export function renderMarkdown(content: string): string {
  if (!content) return '';
  const tokens = new Map<string, string>();
  const protect = (expression: string, displayMode: boolean) => {
    const key = `MATHPLACEHOLDER${tokens.size}END`;
    tokens.set(key, katex.renderToString(expression.trim(), { throwOnError: false, trust: false, displayMode }));
    return key;
  };
  let text = content.replace(/(#{1,6})([0-9a-zA-Z\u4e00-\u9fa5])/g, '$1 $2');
  text = text.replace(/\\\[([\s\S]+?)\\\]/g, (_, e) => protect(e, true))
    .replace(/\\\(([\s\S]+?)\\\)/g, (_, e) => protect(e, false))
    .replace(/\$\$([\s\S]+?)\$\$/g, (_, e) => protect(e, true))
    .replace(/\$([^$\n]+?)\$/g, (_, e) => protect(e, false));
  let html = marked.parse(text, { gfm: true, breaks: true }) as string;
  for (const [key, value] of tokens) html = html.split(key).join(value);
  return createDOMPurify(window).sanitize(html, {
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'img'],
    FORBID_ATTR: ['srcdoc'],
  });
}
