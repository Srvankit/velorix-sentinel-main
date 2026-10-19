import { cn } from "@/lib/utils";
import { scorePassword } from "@/utils/password";

const BARS = [0, 1, 2, 3];

const TONE = [
  "bg-destructive",
  "bg-destructive",
  "bg-warning",
  "bg-success",
  "bg-success",
] as const;

/** Live password strength feedback for register / reset flows. */
export function PasswordStrength({ value }: { value: string }) {
  const { score, label, unmet } = scorePassword(value);

  if (!value) return null;

  return (
    <div className="space-y-2 pt-1">
      <div className="flex items-center gap-2">
        <div className="flex flex-1 gap-1">
          {BARS.map((i) => (
            <span
              key={i}
              className={cn(
                "h-1 flex-1 rounded-full transition-colors",
                i <= score ? TONE[score] : "bg-border",
              )}
            />
          ))}
        </div>
        <span className="text-[11px] text-muted-foreground">{label}</span>
      </div>
      {unmet.length > 0 && (
        <p className="text-[11px] text-muted-foreground">Still needed: {unmet.join(", ")}</p>
      )}
    </div>
  );
}
