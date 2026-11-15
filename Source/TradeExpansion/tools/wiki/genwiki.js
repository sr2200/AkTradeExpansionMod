// Generates GitHub Wiki pages from the design documents (docs/trade-mod-design.md, docs/goods-design.md).
// The documents in the repository stay the source of truth; the wiki is a read-only copy split by chapter.
//   node tools/wiki/genwiki.js <repo root> <wiki folder>
// Every *.md in the wiki folder is deleted and written again (pages of removed chapters disappear).
'use strict';
const fs = require('fs');
const path = require('path');

const REPO_URL = 'https://github.com/sr2200/AkTradeExpansionMod';
const BRANCH = 'main';

const root = path.resolve(process.argv[2] || '.');
const out = path.resolve(process.argv[3] || 'wiki');

// Documents: file, wiki page name of the top page, short name used in chapter page names,
// and which heading levels become pages (a heading of level split[i] is split only inside the
// "## " section whose title starts with the given prefix; '*' = every "## " section).
const DOCS = [
  { file: 'docs/trade-mod-design.md', top: '設計書', prefix: '設計書', subsplit: ['2.'] },
  { file: 'docs/goods-design.md', top: '商品設計書', prefix: '商品設計書', subsplit: [] },
];

// Links between the documents point to their top pages.
const DOC_PAGE = {};
for (const d of DOCS) DOC_PAGE[path.basename(d.file)] = d.top;

/** Wiki page names come from file names: drop characters GitHub would mangle, spaces become '-'. */
function pageName(prefix, heading) {
  let t = heading
    .replace(/【[^】]*】/g, '')          // version notes such as 【v0.12 追加】
    .replace(/[（(][^）)]*[）)]/g, '')    // parenthesised remarks
    .replace(/[`*_]/g, '')
    .trim();
  t = t.replace(/[\\/:*?"<>|#%&{}$!'@+=`~^\[\]：・、。，．.]/g, ' ');
  t = t.replace(/\s+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
  return prefix + '-' + t;
}

/** Splits markdown into lines tagged with heading level (0 = not a heading, fenced code ignored). */
function scan(text) {
  const lines = text.replace(/\r\n/g, '\n').split('\n');
  let fence = false;
  return lines.map((line) => {
    if (/^\s*(```|~~~)/.test(line)) fence = !fence;
    const m = !fence && /^(#{1,6}) (.*)$/.exec(line);
    return { line, level: m ? m[1].length : 0, title: m ? m[2].trim() : null };
  });
}

/** Rewrites links: other design document -> its wiki page, other relative paths -> the file on GitHub. */
function fixLinks(text, docFile) {
  const dir = path.posix.dirname(docFile);
  return text.replace(/\]\(([^)\s]+)\)/g, (all, target) => {
    if (/^[a-z]+:/i.test(target) || target.startsWith('#')) return all;
    const [file, hash] = target.split('#');
    const base = path.posix.basename(file);
    if (DOC_PAGE[base]) return '](' + encodeURI(DOC_PAGE[base]) + ')';
    const repoPath = path.posix.normalize(path.posix.join(dir, file));
    return '](' + REPO_URL + '/blob/' + BRANCH + '/' + encodeURI(repoPath) + (hash ? '#' + hash : '') + ')';
  });
}

/** Moves headings up so the page's own heading becomes level 2 (the wiki shows the page name as the title). */
function shift(lines, by) {
  if (by <= 0) return lines.map((l) => l.line);
  return lines.map((l) => (l.level ? '#'.repeat(Math.max(1, l.level - by)) + ' ' + l.title : l.line));
}

function wikiLink(text, page) {
  return '[[' + text.replace(/[\[\]|]/g, '') + '|' + page + ']]';
}

const pages = []; // { name, body }
const toc = [];   // per document: { doc, top, title, entries: [{ title, page, depth }] }

for (const d of DOCS) {
  const src = fs.readFileSync(path.join(root, d.file), 'utf8');
  const lines = scan(src);
  const firstH2 = lines.findIndex((l) => l.level === 2);
  const title = lines.find((l) => l.level === 1).title;

  // Chapters: every "## " section, and inside the listed ones every "### " section.
  const chapters = []; // { start, end, level, title }
  let current = null;
  for (let i = firstH2; i < lines.length; i++) {
    const l = lines[i];
    if (l.level === 2) {
      current = { start: i, level: 2, title: l.title, sub: d.subsplit.some((p) => l.title.startsWith(p)) };
      chapters.push(current);
    } else if (l.level === 3 && current && (current.sub || current.parentSub)) {
      const parent = current.parentSub ? current.parent : current;
      current = { start: i, level: 3, title: l.title, parent, parentSub: true };
      chapters.push(current);
    }
  }
  chapters.forEach((c, i) => { c.end = i + 1 < chapters.length ? chapters[i + 1].start : lines.length; });
  for (const c of chapters) c.page = pageName(d.prefix, c.title);

  const entries = chapters.map((c) => ({ title: c.title, page: c.page, depth: c.level - 2 }));
  toc.push({ doc: d, title, entries });

  const note = (page) =>
    '> このページは [`' + d.file + '`](' + REPO_URL + '/blob/' + BRANCH + '/' + encodeURI(d.file) + ') から自動で作っています。' +
    'Wiki で直接編集しても、次に作り直した時に消えます。\n';

  // Top page: the document header (version, target, ...) and its table of contents.
  const header = lines.slice(1, firstH2).map((l) => l.line).join('\n').replace(/\n---\s*$/, '').trim();
  const topBody = [
    note(d.top),
    '## ' + title,
    '',
    header,
    '',
    '## 目次',
    '',
    ...entries.map((e) => '  '.repeat(e.depth) + '- ' + wikiLink(e.title, e.page)),
    '',
  ].join('\n');
  pages.push({ name: d.top, body: fixLinks(topBody, d.file) });

  chapters.forEach((c, i) => {
    let body = lines.slice(c.start, c.end);
    // A "## " section that is split further keeps only its lead text (its "### " parts are pages of their own).
    if (c.sub) {
      const next = chapters[i + 1];
      if (next && next.level === 3) body = lines.slice(c.start, next.start);
    }
    const text = shift(body, c.level - 2).join('\n').replace(/\n+---\s*\n*$/, '\n').trimEnd();
    const children = c.sub ? entries.filter((e, j) => chapters[j].parent === c) : [];
    const prev = chapters[i - 1];
    const next = chapters[i + 1];
    const nav = [
      prev ? '← ' + wikiLink(prev.title, prev.page) : null,
      wikiLink('目次', d.top),
      next ? wikiLink(next.title, next.page) + ' →' : null,
    ].filter(Boolean).join('　｜　');
    const out = [
      note(c.page),
      text,
      '',
      ...(children.length ? ['', ...children.map((e) => '- ' + wikiLink(e.title, e.page)), ''] : []),
      '---',
      '',
      nav,
      '',
    ].join('\n');
    pages.push({ name: c.page, body: fixLinks(out, d.file) });
  });
}

// Page names must be unique (two chapters with the same name would overwrite each other).
const seen = new Set();
for (const p of pages) {
  if (seen.has(p.name)) throw new Error('Duplicate wiki page name: ' + p.name);
  seen.add(p.name);
}

const home = [
  '# AK Trade Expansion Wiki',
  '',
  'Minecraft Java Edition 26.3 向けの Fabric Mod「AK Trade Expansion」の設計書です。遊び方は [README](' + REPO_URL + '#readme) を見てください。',
  '',
  ...toc.map((t) => '- ' + wikiLink(t.title, t.doc.top)),
  '',
  '> 設計書の正本はリポジトリの `docs/` にあり、この Wiki はそこから自動で作っています。',
  '',
].join('\n');

const sidebar = [
  '**' + wikiLink('Home', 'Home') + '**',
  '',
  ...toc.flatMap((t) => [
    '**' + wikiLink(t.doc.top, t.doc.top) + '**',
    '',
    ...t.entries.map((e) => '  '.repeat(e.depth) + '- ' + wikiLink(e.title.replace(/【[^】]*】/g, '').trim(), e.page)),
    '',
  ]),
].join('\n');

fs.mkdirSync(out, { recursive: true });
for (const f of fs.readdirSync(out)) {
  if (f.endsWith('.md')) fs.unlinkSync(path.join(out, f));
}
const write = (name, body) => fs.writeFileSync(path.join(out, name + '.md'), body.replace(/\n{3,}/g, '\n\n'), 'utf8');
write('Home', home);
write('_Sidebar', sidebar);
for (const p of pages) write(p.name, p.body);
console.log('Wiki pages: ' + (pages.length + 2) + ' -> ' + out);
