const http = require('http');
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');

const framesDir = path.join(__dirname, 'frames_detail');
if (!fs.existsSync(framesDir)) fs.mkdirSync(framesDir, { recursive: true });

const videoPath = 'C:/Users/guizi/.gemini/antigravity/brain/94586190-f156-4b78-be6b-525550d30f5b/.user_uploaded/media_1795125120904_43e92530.mp4';

const server = http.createServer((req, res) => {
  if (req.url === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(`
<!DOCTYPE html>
<html>
<body>
<video id="v" src="/video" muted playsinline style="display:none;"></video>
<canvas id="c" style="display:none;"></canvas>
<script>
  const v = document.getElementById('v');
  const c = document.getElementById('c');
  const ctx = c.getContext('2d');
  const times = [42, 45, 48, 55, 58, 62, 68, 72];
  let idx = 0;

  v.onloadedmetadata = () => {
    c.width = v.videoWidth || 1280;
    c.height = v.videoHeight || 720;
    seekNext();
  };

  function seekNext() {
    if (idx >= times.length) {
      fetch('/done');
      return;
    }
    v.currentTime = times[idx];
  }

  v.onseeked = () => {
    ctx.drawImage(v, 0, 0, c.width, c.height);
    const dataUrl = c.toDataURL('image/jpeg', 0.85);
    fetch('/save_frame', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ time: times[idx], image: dataUrl })
    }).then(() => {
      idx++;
      seekNext();
    });
  };
</script>
</body>
</html>
    `);
  } else if (req.url === '/video') {
    const stat = fs.statSync(videoPath);
    const range = req.headers.range;
    if (range) {
      const parts = range.replace(/bytes=/, "").split("-");
      const start = parseInt(parts[0], 10);
      const end = parts[1] ? parseInt(parts[1], 10) : stat.size - 1;
      const chunksize = (end - start) + 1;
      const file = fs.createReadStream(videoPath, { start, end });
      res.writeHead(206, {
        'Content-Range': `bytes ${start}-${end}/${stat.size}`,
        'Accept-Ranges': 'bytes',
        'Content-Length': chunksize,
        'Content-Type': 'video/mp4',
      });
      file.pipe(res);
    } else {
      res.writeHead(200, {
        'Content-Length': stat.size,
        'Content-Type': 'video/mp4',
      });
      fs.createReadStream(videoPath).pipe(res);
    }
  } else if (req.url === '/save_frame' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      const { time, image } = JSON.parse(body);
      const base64Data = image.replace(/^data:image\/jpeg;base64,/, "");
      const outPath = path.join(framesDir, `frame_${time}s.jpg`);
      fs.writeFileSync(outPath, base64Data, 'base64');
      res.writeHead(200);
      res.end('ok');
    });
  } else if (req.url === '/done') {
    res.writeHead(200);
    res.end('done');
    setTimeout(() => {
      server.close();
      process.exit(0);
    }, 500);
  }
});

server.listen(48292, () => {
  const edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
  execFile(edgePath, [
    '--headless',
    '--disable-gpu',
    '--mute-audio',
    '--autoplay-policy=no-user-gesture-required',
    'http://localhost:48292'
  ]);
});
