import http from 'node:http';
import { URL } from 'node:url';

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || '0.0.0.0';
const VIDEO_ID_RE = /^[A-Za-z0-9_-]{11}$/;

const INNERTUBE_CLIENTS = [
  {
    name: 'ANDROID_VR',
    version: '1.60.19',
    ua: 'com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; Quest 3 Build/SQ3A.220605.009.A1) gzip',
  },
  {
    name: 'IOS',
    version: '19.45.4',
    ua: 'com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)',
  },
  {
    name: 'ANDROID_TESTSUITE',
    version: '1.9',
    ua: 'com.google.android.youtube/1.9 (Linux; U; Android 11) gzip',
  },
];

function sendJson(res, status, data) {
  const payload = JSON.stringify(data);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
  });
  res.end(payload);
}

async function resolveViaInnertube(videoId) {
  for (const client of INNERTUBE_CLIENTS) {
    const resp = await fetch('https://www.youtube.com/youtubei/v1/player?prettyPrint=false', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': client.ua,
        'Accept-Language': 'pt-BR,pt;q=0.9,en;q=0.8',
      },
      body: JSON.stringify({
        videoId,
        context: {
          client: {
            clientName: client.name,
            clientVersion: client.version,
            hl: 'pt',
            gl: 'BR',
          },
        },
        contentCheckOk: true,
        racyCheckOk: true,
      }),
    });

    if (!resp.ok) continue;
    const data = await resp.json().catch(() => null);
    if (!data || data?.playabilityStatus?.status !== 'OK') continue;

    const formats = Array.isArray(data?.streamingData?.formats) ? data.streamingData.formats : [];
    const mp4Streams = formats
      .filter((f) => typeof f.url === 'string' && String(f.mimeType || '').toLowerCase().includes('video/mp4'))
      .map((f) => ({
        url: f.url,
        container: 'mp4',
        resolution: f.qualityLabel || `${f.height || 360}p`,
        height: Number(f.height || 360),
      }));

    if (mp4Streams.length > 0) {
      return {
        videoId,
        title: data?.videoDetails?.title || `Video ${videoId}`,
        author: data?.videoDetails?.author || 'YouTube',
        description: data?.videoDetails?.shortDescription || '',
        clientUsed: client.name,
        formatStreams: mp4Streams,
      };
    }
  }
  return null;
}

function isAllowedUpstreamMediaUrl(rawUrl) {
  try {
    const parsed = new URL(rawUrl);
    if (parsed.protocol !== 'https:') return false;
    const host = parsed.hostname.toLowerCase();
    return host === 'googlevideo.com' || host.endsWith('.googlevideo.com');
  } catch {
    return false;
  }
}

async function handleStreamRelay(req, res, videoId, baseOrigin) {
  const resolved = await resolveViaInnertube(videoId);
  if (!resolved || resolved.formatStreams.length === 0) {
    return sendJson(res, 404, { error: 'No H.264 MP4 stream available' });
  }
  const picked = resolved.formatStreams[0];
  if (!isAllowedUpstreamMediaUrl(picked.url)) {
    return sendJson(res, 403, { error: 'Blocked upstream media host' });
  }

  const upstreamHeaders = {};
  if (typeof req.headers.range === 'string' && req.headers.range.startsWith('bytes=')) {
    upstreamHeaders.Range = req.headers.range;
  }
  const mediaResp = await fetch(picked.url, { headers: upstreamHeaders });
  const headers = {
    'Content-Type': mediaResp.headers.get('content-type') || 'video/mp4',
    'Accept-Ranges': 'bytes',
  };
  const len = mediaResp.headers.get('content-length');
  const range = mediaResp.headers.get('content-range');
  if (len) headers['Content-Length'] = len;
  if (range) headers['Content-Range'] = range;

  res.writeHead(mediaResp.status, headers);
  if (!mediaResp.body) {
    res.end();
    return;
  }
  for await (const chunk of mediaResp.body) {
    if (res.destroyed) break;
    res.write(chunk);
  }
  res.end();
}

const server = http.createServer(async (req, res) => {
  try {
    const hostHeader = req.headers.host || `127.0.0.1:${PORT}`;
    const parsed = new URL(req.url || '/', `http://${hostHeader}`);
    const path = parsed.pathname;

    if (path === '/health' || path === '/api/v1/stats') {
      return sendJson(res, 200, {
        software: { name: 'oldtube-bridge', version: '1.0.0' },
        status: 'ok',
        android44Compatible: true,
      });
    }

    if (path.startsWith('/api/v1/videos/')) {
      const videoId = path.slice('/api/v1/videos/'.length).trim();
      if (!VIDEO_ID_RE.test(videoId)) {
        return sendJson(res, 400, { error: 'Invalid videoId' });
      }
      const resolved = await resolveViaInnertube(videoId);
      if (!resolved) {
        return sendJson(res, 404, { error: 'Unable to resolve progressive H.264 stream' });
      }
      const proto = req.headers['x-forwarded-proto'] === 'https' ? 'https' : 'http';
      const relayBase = `${proto}://${hostHeader}`;
      return sendJson(res, 200, {
        ...resolved,
        formatStreams: [
          ...resolved.formatStreams,
          {
            url: `${relayBase}/stream/${videoId}`,
            container: 'mp4',
            resolution: resolved.formatStreams[0]?.resolution || '360p',
          },
        ],
      });
    }

    if (path.startsWith('/stream/')) {
      const videoId = path.slice('/stream/'.length).trim();
      if (!VIDEO_ID_RE.test(videoId)) {
        return sendJson(res, 400, { error: 'Invalid videoId' });
      }
      return await handleStreamRelay(req, res, videoId, hostHeader);
    }

    return sendJson(res, 404, { error: 'Not found' });
  } catch (err) {
    return sendJson(res, 500, { error: 'Internal bridge error' });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`OLD Tube Bridge API running on http://${HOST}:${PORT}`);
});
