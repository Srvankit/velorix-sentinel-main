export const PRODUCTION_BACKEND_ORIGIN: string;
export const STAGING_BACKEND_ORIGIN: string;
export function resolveBackendOrigin(apiUrl?: string): string;
export function buildContentSecurityPolicy(apiUrl?: string): string;
export function buildHeadersFileContent(apiUrl?: string): string;
export function generateHeaders(outputPath?: string, apiUrl?: string): string;
