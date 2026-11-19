const fs = require('fs');
const p = 'C:\\Users\\guizi\\.gemini\\antigravity\\brain\\ec91dbf4-e510-4d3f-9ab8-0710dd0446be\\.system_generated\\logs\\transcript.jsonl';
const lines = fs.readFileSync(p, 'utf8').split('\n');

for (let i = 3555; i < 3570; i++) {
  if (!lines[i]) continue;
  const obj = JSON.parse(lines[i]);
  if (obj.type === 'USER_INPUT') {
    console.log(`[step ${obj.step_index}] NEXT USER INPUT: ` + obj.content);
    break;
  }
}
