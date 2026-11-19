const fs = require('fs');
const p = 'C:\\Users\\guizi\\.gemini\\antigravity\\brain\\ec91dbf4-e510-4d3f-9ab8-0710dd0446be\\.system_generated\\logs\\transcript.jsonl';
const lines = fs.readFileSync(p, 'utf8').split('\n');

for (let i = 3545; i < 3570; i++) {
  if (!lines[i]) continue;
  const obj = JSON.parse(lines[i]);
  if (obj.source === 'MODEL' && obj.content && obj.content.length > 50 && !obj.content.startsWith('Created At:')) {
    console.log(`[step ${obj.step_index}] FINAL RESPONSE:\n` + obj.content);
    break;
  }
}
