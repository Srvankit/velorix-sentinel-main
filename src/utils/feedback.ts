/**
 * Utility for retrieving the optional feedback email configured at build time.
 * If VITE_FEEDBACK_EMAIL is not set, empty, or whitespace-only, returns undefined.
 */
export function getFeedbackEmail(): string | undefined {
  const email = (import.meta.env.VITE_FEEDBACK_EMAIL as string | undefined)?.trim();
  return email && email.length > 0 ? email : undefined;
}
