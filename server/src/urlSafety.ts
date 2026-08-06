import dns from 'node:dns';
import net from 'node:net';

export class UnsafeUrlError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UnsafeUrlError';
  }
}

export function allowLocalhostFetch(): boolean {
  return process.env.SSS_ALLOW_LOCALHOST_FETCH === '1';
}

function stripIpBrackets(host: string): string {
  return host.replace(/^\[|\]$/g, '').toLowerCase();
}

export function isBlockedIp(ip: string): boolean {
  const normalized = stripIpBrackets(ip);
  const version = net.isIP(normalized);
  if (version === 4) {
    if (normalized === '0.0.0.0') return true;
    if (/^127\./.test(normalized)) return !allowLocalhostFetch();
    if (/^10\./.test(normalized)) return true;
    if (/^172\.(1[6-9]|2\d|3[01])\./.test(normalized)) return true;
    if (/^192\.168\./.test(normalized)) return true;
    if (/^169\.254\./.test(normalized)) return true;
    if (/^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(normalized)) return true;
    const firstOctet = Number(normalized.split('.')[0]);
    if (firstOctet >= 224 && firstOctet <= 255) return true;
    return false;
  }
  if (version === 6) {
    const mappedV4 = normalized.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
    if (mappedV4) return isBlockedIp(mappedV4[1]);
    const mappedHex = normalized.match(/^::ffff:([0-9a-f]{1,4}):([0-9a-f]{1,4})$/);
    if (mappedHex) {
      const high = parseInt(mappedHex[1], 16);
      const low = parseInt(mappedHex[2], 16);
      return isBlockedIp(`${high >> 8}.${high & 255}.${low >> 8}.${low & 255}`);
    }
    if (normalized === '::' || normalized === '::0') return true;
    if (normalized === '::1') return !allowLocalhostFetch();
    if (/^fe[89ab][0-9a-f]:/i.test(normalized)) return true;
    if (/^f[cde][0-9a-f]:/i.test(normalized)) return true;
    if (/^ff[0-9a-f]:/i.test(normalized)) return true;
    return false;
  }
  return false;
}

export function assertSafeHttpUrl(raw: string): URL {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new UnsafeUrlError('URL 无法解析');
  }
  if (!['http:', 'https:'].includes(url.protocol)) {
    throw new UnsafeUrlError('仅允许 http/https 链接');
  }
  const host = stripIpBrackets(url.hostname);
  if (net.isIP(host) !== 0 && isBlockedIp(host)) {
    throw new UnsafeUrlError('不允许抓取内网或保留地址');
  }
  const hostname = url.hostname.toLowerCase().replace(/\.$/, '');
  if (hostname === 'localhost' && !allowLocalhostFetch()) {
    throw new UnsafeUrlError('不允许抓取内网或保留地址');
  }
  if (hostname === 'metadata.google.internal') {
    throw new UnsafeUrlError('不允许抓取内网或保留地址');
  }
  return url;
}

export async function assertSafeFetchTarget(raw: string): Promise<URL> {
  const url = assertSafeHttpUrl(raw);
  const host = stripIpBrackets(url.hostname);
  if (net.isIP(host) !== 0) return url;

  let addresses: Array<{ address: string; family: number }>;
  try {
    addresses = await dns.promises.lookup(host, { all: true, verbatim: true });
  } catch {
    throw new UnsafeUrlError('域名无法解析');
  }
  if (addresses.length === 0) throw new UnsafeUrlError('域名无法解析');
  for (const { address } of addresses) {
    if (isBlockedIp(address)) throw new UnsafeUrlError('不允许抓取内网或保留地址');
  }
  return url;
}
