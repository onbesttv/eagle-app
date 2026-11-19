const fs = require('fs');
const path = require('path');

const brainDir = 'C:\\Users\\guizi\\.gemini\\antigravity\\brain';
const dirs = fs.readdirSync(brainDir);

for (const d of dirs) {
  const p = path.join(brainDir, d, '.system_generated', 'logs', 'transcript.jsonl');
  if (fs.existsSync(p)) {
    const lines = fs.readFileSync(p, 'utf8').split('\n');
    for (const l of lines) {
      if (!l.trim()) continue;
      try {
        const obj = JSON.parse(l);
        if (obj.type === 'USER_INPUT') {
          const txt = (obj.content || '').toLowerCase();
          if (txt.includes('pass') || txt.includes('senha') || txt.includes('master') || txt.includes('login')) {
            console.log(`[${d}] USER: ${obj.content.substring(0, 200)}`);
          }
        }
      } catch(e) {}
    }
  }
}
