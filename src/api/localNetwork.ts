/** RFC1918 / loopback / link-local heuristic used to enforce ExposureRule.localOnly and LOCAL_ONLY confirm policy. */
export function isPrivateIp(ip: string): boolean {
    const normalized = ip.replace('::ffff:', '');

    if (normalized === '127.0.0.1' || normalized === '::1') {
        return true;
    }

    const parts = normalized.split('.').map(Number);
    if (parts.length === 4 && parts.every((n) => !Number.isNaN(n))) {
        const [a, b] = parts;
        if (a === 10) return true;
        if (a === 172 && b >= 16 && b <= 31) return true;
        if (a === 192 && b === 168) return true;
    }

    if (normalized.startsWith('fc') || normalized.startsWith('fd') || normalized.startsWith('fe80')) {
        return true;
    }

    return false;
}
