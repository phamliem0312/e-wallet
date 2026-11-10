// PreToolUse hook (Bash|PowerShell): chặn lệnh phá git flow hoặc khó hoàn tác (ADR-0002).
// Exit 2 = chặn, stderr được trả cho Claude. Tự kiểm: node .claude/hooks/guard.mjs --self-test
import { execFileSync } from 'node:child_process';

const SEG = String.raw`[^;&|\n]*`; // trong cùng một lệnh con
const RULES = [
  [new RegExp(String.raw`\bgit\b${SEG}\bpush\b${SEG}(\s--force(-with-lease)?\b|\s-f\b|\s\+\S)`), 'Cấm force push.'],
  [new RegExp(String.raw`\bgit\b${SEG}\bpush\b${SEG}(--delete\s+(main|master)\b|\s\S*:(refs/heads/)?(main|master)\b)`), 'Cấm ghi đè hoặc xoá main trên remote; vào main chỉ bằng merge --no-ff rồi push main.'],
  [new RegExp(String.raw`\bgit\b${SEG}\breset\b${SEG}--hard\b`), 'Cấm git reset --hard.'],
  [new RegExp(String.raw`\bgit\b${SEG}\bclean\b${SEG}\s-\w*f`), 'Cấm git clean -f.'],
  [new RegExp(String.raw`--no-verify\b`), 'Cấm bỏ qua hook (--no-verify).'],
  [new RegExp(String.raw`\bgit\b${SEG}\bbranch\b${SEG}\s-[dDmM]\w*\s+(main|master)\b`), 'Cấm xoá hoặc đổi tên main.'],
  [new RegExp(String.raw`\bgit\b${SEG}\bremote\b${SEG}\b(add|remove|rm|rename|set-url)\b`), 'Cấm đổi remote.'],
  [new RegExp(String.raw`\brm\s+-\w*r\w*\s+(/|~|\.\.|\$HOME|[A-Za-z]:[\\/]?)(\s|$)`), 'Cấm rm -r ở thư mục gốc, home hoặc thư mục cha.'],
];

export function check(command, branchOf) {
  for (const [re, msg] of RULES) if (re.test(command)) return msg;
  // ponytail: lấy nhánh ở cwd của tool; lệnh "cd khác && git commit" hoặc "git -C" không được soát.
  if (new RegExp(String.raw`\bgit\b${SEG}\bcommit\b`).test(command) && branchOf() === 'main')
    return 'Cấm commit trực tiếp trên main; mở nhánh <loại>/<ID>-<slug> (git-manager start).';
  return null;
}

function currentBranch(cwd) {
  try { return execFileSync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { cwd, encoding: 'utf8' }).trim(); }
  catch { return null; }
}

if (process.argv[2] === '--self-test') {
  const bad = ['git push --force origin x', 'git push -f', 'git push origin +feat/a', 'git push origin feat/a:main',
    'git push origin --delete main', 'git reset --hard HEAD~1', 'git clean -fd', 'git commit --no-verify -m x',
    'git branch -D main', 'git remote set-url origin x', 'rm -rf /', 'rm -rf ..', 'cd x && git push --force-with-lease'];
  const ok = ['git push origin main', 'git push -u origin feat/M2-01-ledger', 'git push origin --delete feat/x',
    'git branch -f main origin/main', 'git branch -d feat/x', 'git merge --no-ff feat/x -m "merge: x"',
    'rm -rf build', 'git reset --merge', 'git log --format=%h -f'];
  const fails = [...bad.filter(c => !check(c, () => 'feat/x')), ...ok.filter(c => check(c, () => 'feat/x'))];
  if (!check('git commit -m x', () => 'main') || check('git commit -m x', () => 'feat/x')) fails.push('commit-on-main');
  console.log(fails.length ? `FAIL: ${fails.join(' | ')}` : `OK: ${bad.length + ok.length + 2} ca`);
  process.exit(fails.length ? 1 : 0);
}

let raw = '';
process.stdin.on('data', d => (raw += d));
process.stdin.on('end', () => {
  let input;
  try { input = JSON.parse(raw); } catch { process.exit(0); }
  const command = input?.tool_input?.command ?? '';
  const msg = check(command, () => currentBranch(input.cwd || process.cwd()));
  if (msg) { process.stderr.write(`[guard] ${msg} Lệnh: ${command}\n`); process.exit(2); }
});
