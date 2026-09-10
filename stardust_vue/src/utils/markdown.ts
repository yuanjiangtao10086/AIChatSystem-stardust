import DOMPurify from "dompurify";
import hljs from "highlight.js/lib/common";
import katex from "katex";
import MarkdownIt from "markdown-it";

export type MarkdownBlock =
  | { type: "markdown"; content: string; key: string }
  | { type: "code"; content: string; language: string; key: string };

const markdown: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: true,
  highlight(code, language) {
    if (language && hljs.getLanguage(language)) {
      return hljs.highlight(code, { language, ignoreIllegals: true }).value;
    }
    return markdown.utils.escapeHtml(code);
  },
});

const defaultLinkOpen: NonNullable<typeof markdown.renderer.rules.link_open> =
  markdown.renderer.rules.link_open ||
  ((tokens, index, options, _environment, renderer) =>
    renderer.renderToken(tokens, index, options));
markdown.renderer.rules.link_open = (
  tokens,
  index,
  options,
  environment,
  renderer
) => {
  tokens[index].attrSet("target", "_blank");
  tokens[index].attrSet("rel", "noopener noreferrer nofollow");
  return defaultLinkOpen(tokens, index, options, environment, renderer);
};

export function splitMarkdownBlocks(source: string): MarkdownBlock[] {
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const blocks: MarkdownBlock[] = [];
  let text: string[] = [];
  let code: string[] | undefined;
  let language = "text";
  const flushText = () => {
    if (text.length)
      blocks.push({
        type: "markdown",
        content: text.join("\n"),
        key: `md-${blocks.length}`,
      });
    text = [];
  };
  for (const line of lines) {
    const fence = line.match(/^```\s*([^\s`]*)\s*$/);
    if (fence && code === undefined) {
      flushText();
      code = [];
      language = fence[1] || "text";
    } else if (fence && code !== undefined) {
      blocks.push({
        type: "code",
        content: code.join("\n"),
        language,
        key: `code-${blocks.length}`,
      });
      code = undefined;
      language = "text";
    } else if (code !== undefined) {
      code.push(line);
    } else {
      text.push(line);
    }
  }
  if (code !== undefined)
    blocks.push({
      type: "code",
      content: code.join("\n"),
      language,
      key: `code-${blocks.length}`,
    });
  flushText();
  return blocks;
}

export function renderSafeMarkdown(source: string): string {
  const math: string[] = [];
  const protectedSource = source
    .split(/(`+[^`]*`+)/g)
    .map((part) => {
      if (part.startsWith("`")) return part;
      return part
        .replace(/\$\$([\s\S]+?)\$\$/g, (_match, expression: string) =>
          mathToken(math, expression, true)
        )
        .replace(
          /(^|[^\\])\$([^$\n]+?)\$/g,
          (_match, prefix: string, expression: string) =>
            `${prefix}${mathToken(math, expression, false)}`
        );
    })
    .join("");
  let rendered = markdown.render(protectedSource);
  math.forEach((html, index) => {
    rendered = rendered.replace(`STARDUSTMATHTOKEN${index}END`, html);
  });
  return DOMPurify.sanitize(rendered, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: [
      "style",
      "script",
      "iframe",
      "object",
      "embed",
      "form",
      "input",
      "button",
    ],
    FORBID_ATTR: ["onerror", "onload", "onclick"],
  });
}

export function highlightCode(code: string, language: string): string {
  const result =
    language !== "text" && hljs.getLanguage(language)
      ? hljs.highlight(code, { language, ignoreIllegals: true }).value
      : markdown.utils.escapeHtml(code);
  return DOMPurify.sanitize(result, {
    ALLOWED_TAGS: ["span"],
    ALLOWED_ATTR: ["class"],
  });
}

function mathToken(
  values: string[],
  expression: string,
  displayMode: boolean
): string {
  const index = values.length;
  values.push(
    katex.renderToString(expression.trim(), {
      displayMode,
      throwOnError: false,
      strict: "warn",
      trust: false,
      output: "html",
    })
  );
  return `STARDUSTMATHTOKEN${index}END`;
}
