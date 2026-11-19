const fs = require('fs');
const path = require('path');

const brainDir = 'C:\\Users\\guizi\\.gemini\\antigravity\\brain';

function checkConv(convId) {
  const p = path.join(brainDir, convId, '.system_generated', 'logs', 'transcript.jsonl');
  if (!fs.existsSync(p)) return;
  const lines = fs.readFileSync(p, 'utf8').split('\n');
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (!l.trim()) continue;
    try {
      const obj = JSON.parse(l);
      if (obj.type === 'USER_INPUT' && (obj.content.includes('pass') || obj.content.includes('user'))) {
        console.log(`=== [${convId}] USER ===\n` + obj.content);
        // Look ahead for MODEL response
        for (let j = i + 1; j < Math.min(i + 10, lines.length); j++) {
          if (!lines[j].trim()) continue;
          const mObj = JSON.parse(lines[j]);
          if (mObj.source === 'MODEL' && mObj.content) {
            console.log(`--- MODEL RESPONSE ---\n` + mObj.content.substring(0, 400));
            break;
          }
        }
      }
    } catch(e) {}
  }
}

checkConv('ec91dbf4-e510-4d3f-9ab8-0710dd0446be');
checkConv('adcde980-75d5-4ec5-8a98-c473f08d7766');
checkConv('9904b4e3-46dd-4d2c-895c-f54c781d9100');
checkConv('53231ca4-cc8b-481c-a509-f061556bdc05');
