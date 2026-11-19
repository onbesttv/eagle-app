const fs = require('fs');
const path = require('path');

const p = 'C:\\Users\\guizi\\.gemini\\antigravity\\brain\\ec91dbf4-e510-4d3f-9ab8-0710dd0446be\\.system_generated\\logs\\transcript.jsonl';
const lines = fs.readFileSync(p, 'utf8').split('\n');

for (let i = 0; i < lines.length; i++) {
  const l = lines[i];
  if (l.includes('tou a  tentar entrar no painel nao da')) {
    console.log('Found line at', i);
    for (let j = i; j < i + 15; j++) {
      const obj = JSON.parse(lines[j]);
      console.log(`[step ${obj.step_index}] ${obj.type}: ${JSON.stringify(obj.content || '').substring(0, 300)}`);
    }
    break;
  }
}
