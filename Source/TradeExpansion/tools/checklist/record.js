// Records the results of the automated in-game tests in the checklist.
//
//   node tools/checklist/record.js        (run in Source/TradeExpansion; autotest.bat runs it)
//
// Reads build/autotest/server-results.json and client-results.json (written by the game tests in src/gametest) and
//   1. marks the items of docs/release/ingame-checklist.md the tests covered:
//        all cases passed and one covers the whole item -> "- [x] ... 〔自動 ✔ 0.2.1 26-11-14〕"
//        all cases passed but only partly                -> "- [ ] ... 〔一部自動 ✔ 0.2.1 26-11-14：<what was checked>〕"
//        a case failed                                   -> "- [ ] ... 〔自動 ✘ 0.2.1 26-11-14〕"
//      (items not run this time keep their marks; a person's own [x] stays unless a case fails)
//   2. rewrites docs/release/autotest-report.md: every case, failures and the screenshots to look at.
// Exits with 1 when a case failed.

'use strict';
const fs = require('fs');
const path = require('path');

const project = path.resolve(__dirname, '..', '..');            // Source/TradeExpansion
const root = path.resolve(project, '..', '..');                  // repository root
const resultsDir = path.join(project, 'build', 'autotest');
const checklistFile = path.join(root, 'docs', 'release', 'ingame-checklist.md');
const reportFile = path.join(root, 'docs', 'release', 'autotest-report.md');

const SIDES = { server: 'サーバー', client: 'クライアント' };

function readResults() {
	const runs = [];
	for (const side of Object.keys(SIDES)) {
		const file = path.join(resultsDir, `${side}-results.json`);
		if (!fs.existsSync(file)) continue;
		const run = JSON.parse(fs.readFileSync(file, 'utf8'));
		run.cases.push(logCase(side));
		runs.push(run);
	}
	return runs;
}

// The game's log of each run (copied next to its results when the run wrote them): errors that come from this mod
// fail the run even when every case passed.
const LOGS = {
	server: path.join(resultsDir, 'server.log'),
	client: path.join(resultsDir, 'client.log'),
};
const LOG_CASES = {
	server: { name: 'log_has_no_mod_errors', checklist: ['14-4'], partial: 'テスト用サーバー（専用サーバーではない）のログに本 Mod のエラーが出ないことのみ' },
	client: { name: 'log_has_no_mod_errors', checklist: ['20-4', '25-1'], partial: null },
};
const OURS = /tradeexpansion|ak_trade_expansion|TradeExpansion/i;
const LOG_LINE = /^\[[\d:]+\] \[([^\]]*)\/(ERROR|FATAL|WARN)\] \(([^)]*)\) (.*)$/;

/** Errors (and the mod's own warnings) of a run's log that concern this mod, with their stack traces. */
function modProblems(file) {
	if (!fs.existsSync(file)) return [`ログがありません: ${file}`];
	const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
	const problems = [];
	for (let i = 0; i < lines.length; i++) {
		const m = LOG_LINE.exec(lines[i]);
		if (!m) continue;
		const [, , level, logger, message] = m;
		if (logger === 'AKTradeExpansion/autotest') continue; // the tests' own case results
		let stack = '';
		for (let j = i + 1; j < lines.length && !LOG_LINE.test(lines[j]) && !/^\[[\d:]+\]/.test(lines[j]); j++) stack += lines[j] + '\n';
		// A failed case ends the client test with an AssertionError (and Fabric reports it as a crash): that is the
		// case's own result, already recorded, not an error of the mod.
		if (/checklist (cases|results) failed/.test(message + stack)) continue;
		const ours = logger === 'TradeExpansion' || OURS.test(message) || OURS.test(stack);
		if (level === 'WARN' ? logger === 'TradeExpansion' : ours) problems.push(`${level} (${logger}) ${message}`.slice(0, 300));
	}
	return problems;
}

function logCase(side) {
	const problems = modProblems(LOGS[side]);
	const c = LOG_CASES[side];
	return {
		name: c.name, checklist: c.checklist, partial: c.partial ?? undefined, ok: problems.length === 0,
		message: problems.length ? `${problems.length} 件: ${problems.slice(0, 5).join(' | ')}` : undefined, screenshots: [],
	};
}

function shortDate(iso) {
	// "2026-11-14T17:30:43+09:00" -> "26-11-14" (the date as written in the run's own time zone)
	return iso.slice(2, 10);
}

function main() {
	const runs = readResults();
	if (runs.length === 0) {
		console.error(`No results in ${resultsDir}. Run the game tests first (autotest.bat).`);
		process.exit(2);
	}

	// checklist id -> { cases: [...], version, date }
	const byItem = new Map();
	for (const run of runs) {
		for (const c of run.cases) {
			for (const id of c.checklist) {
				if (!byItem.has(id)) byItem.set(id, { cases: [], version: run.version, date: shortDate(run.finished) });
				byItem.get(id).cases.push({ ...c, side: run.side });
			}
		}
	}

	const text = fs.readFileSync(checklistFile, 'utf8');
	const eol = text.includes('\r\n') ? '\r\n' : '\n';
	const lines = text.split(/\r?\n/);
	const seen = new Map();
	const marked = { full: [], partial: [], failed: [] };
	const out = lines.map((line, n) => {
		const m = /^- \[( |x)\] (\d+-[0-9a-z]+) /.exec(line);
		if (!m) return line;
		const id = m[2];
		if (seen.has(id)) console.warn(`warning: item ${id} appears twice in the checklist (lines ${seen.get(id)} and ${n + 1})`);
		seen.set(id, n + 1);
		const item = byItem.get(id);
		if (!item) return line;
		const body = line.slice(m[0].length).replace(/\s*〔(一部)?自動 [✔✘][^〕]*〕\s*$/, '');
		const stamp = `${item.version} ${item.date}`;
		const failed = item.cases.some(c => !c.ok);
		const full = item.cases.some(c => !c.partial);
		if (failed) {
			marked.failed.push(id);
			const names = [...new Set(item.cases.filter(c => !c.ok).map(c => c.name))].join('・');
			return `- [ ] ${id} ${body} 〔自動 ✘ ${stamp}：${names}（autotest-report.md）〕`;
		}
		if (full) {
			marked.full.push(id);
			return `- [x] ${id} ${body} 〔自動 ✔ ${stamp}〕`;
		}
		marked.partial.push(id);
		const covers = [...new Set(item.cases.map(c => c.partial))].join('／');
		const tick = /〔自動 ✔/.test(line) ? ' ' : m[1]; // an [x] that only the tests gave goes; a person's own [x] stays
		return `- [${tick}] ${id} ${body} 〔一部自動 ✔ ${stamp}：${covers}〕`;
	});
	const unknown = [...byItem.keys()].filter(id => !seen.has(id));
	if (unknown.length) console.warn(`warning: cases name checklist items that do not exist: ${unknown.join(', ')}`);
	fs.writeFileSync(checklistFile, out.join(eol));

	writeReport(runs, marked, seen.size);

	const all = runs.flatMap(r => r.cases);
	const failedCases = all.filter(c => !c.ok);
	console.log(`Checklist: ${marked.full.length} done, ${marked.partial.length} partly checked, ${marked.failed.length} failed (of ${seen.size} items).`);
	console.log(`Report: ${path.relative(root, reportFile)}`);
	if (failedCases.length) {
		for (const c of failedCases) console.log(`FAILED ${c.checklist.join(',')} ${c.name}: ${c.message}`);
		process.exit(1);
	}
}

function writeReport(runs, marked, itemCount) {
	const shotsBase = path.relative(path.dirname(reportFile), resultsDir).split(path.sep).join('/');
	const L = [];
	L.push('# ゲーム内の自動テストの結果');
	L.push('');
	L.push('`autotest.bat`（`Source/TradeExpansion`）が作る生成物です。手で直さないでください。結果はチェックリスト（`ingame-checklist.md`）の各項目にも書き込まれます。');
	L.push('');
	for (const run of runs) {
		const failed = run.cases.filter(c => !c.ok).length;
		L.push(`- ${SIDES[run.side] ?? run.side}：バージョン ${run.version}、${run.finished.slice(0, 16).replace('T', ' ')}、${run.cases.length} 件中 ${failed} 件失敗`);
	}
	L.push(`- チェックリスト ${itemCount} 項目のうち、今回 自動で完了 ${marked.full.length}・一部を確認 ${marked.partial.length}・失敗 ${marked.failed.length}`);
	L.push('');

	const failed = runs.flatMap(r => r.cases.filter(c => !c.ok).map(c => ({ ...c, side: r.side })));
	if (failed.length) {
		L.push('## 失敗した項目');
		L.push('');
		for (const c of failed) L.push(`- ${c.checklist.join('・')} \`${c.name}\`（${SIDES[c.side]}）：${c.message}`);
		L.push('');
	}

	L.push('## すべての項目');
	L.push('');
	L.push('| 項目 | テスト | 側 | 結果 | 確認した範囲 |');
	L.push('|---|---|---|---|---|');
	for (const run of runs) {
		for (const c of run.cases) {
			L.push(`| ${c.checklist.join('・') || '—'} | \`${c.name}\` | ${SIDES[run.side]} | ${c.ok ? '✔' : '✘'} | ${c.partial ?? '全部'} |`);
		}
	}
	L.push('');

	const shots = runs.flatMap(r => r.cases.flatMap(c => c.screenshots.map(s => ({ id: c.checklist.join('・'), name: c.name, file: s }))));
	if (shots.length) {
		L.push('## 見て確かめる画像');
		L.push('');
		L.push('見た目（文字の重なり・はみ出し・色）は人が判断します。画像は `Source/TradeExpansion/build/autotest/screenshots/` にあります（リポジトリには入れない）。');
		L.push('');
		for (const s of shots) L.push(`- ${s.id} \`${s.name}\`：[${path.basename(s.file)}](${shotsBase}/${s.file})`);
		L.push('');
	}
	fs.writeFileSync(reportFile, L.join('\r\n'));
}

main();
