export const ERROR_CODES = [
    'AUTH_REQUIRED',
    'TOKEN_EXPIRED',
    'DEVICE_REVOKED',
    'SESSION_REVOKED',
    'OBJECT_NOT_FOUND',
    'READ_FORBIDDEN',
    'WRITE_FORBIDDEN',
    'VALUE_INVALID',
    'CONFIRMATION_REQUIRED',
    'LOCAL_ONLY',
    'RATE_LIMITED',
    'COMMAND_TIMEOUT',
    'REVISION_CONFLICT',
    'SERVER_UNAVAILABLE',
    'PAIRING_INVALID',
    'PAIRING_EXPIRED',
    'CHALLENGE_INVALID',
    'SIGNATURE_INVALID',
    'NOT_FOUND',
    'VALIDATION_ERROR',
    'REPLAY_DETECTED',
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

const STATUS_BY_CODE: Record<ErrorCode, number> = {
    AUTH_REQUIRED: 401,
    TOKEN_EXPIRED: 401,
    DEVICE_REVOKED: 403,
    SESSION_REVOKED: 401,
    OBJECT_NOT_FOUND: 404,
    READ_FORBIDDEN: 403,
    WRITE_FORBIDDEN: 403,
    VALUE_INVALID: 400,
    CONFIRMATION_REQUIRED: 428,
    LOCAL_ONLY: 403,
    RATE_LIMITED: 429,
    COMMAND_TIMEOUT: 504,
    REVISION_CONFLICT: 409,
    SERVER_UNAVAILABLE: 503,
    PAIRING_INVALID: 400,
    PAIRING_EXPIRED: 410,
    CHALLENGE_INVALID: 400,
    SIGNATURE_INVALID: 401,
    NOT_FOUND: 404,
    VALIDATION_ERROR: 400,
    REPLAY_DETECTED: 409,
};

export class ApiError extends Error {
    readonly code: ErrorCode;
    readonly status: number;

    constructor(code: ErrorCode, message?: string) {
        super(message ?? code);
        this.code = code;
        this.status = STATUS_BY_CODE[code];
        this.name = 'ApiError';
    }

    toBody(): { error: ErrorCode; message: string } {
        return { error: this.code, message: this.message };
    }
}
