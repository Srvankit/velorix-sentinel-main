import { useEffect } from "react";

type Handler = (event: KeyboardEvent) => void;

function isTypingTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName.toLowerCase();
  return tag === "input" || tag === "textarea" || tag === "select" || target.isContentEditable;
}

/**
 * Global keyboard shortcuts for the SOC workspace.
 *
 * Every handler is optional. Shortcuts never fire while the analyst is typing
 * in a field, except `Escape`, which always dismisses the active surface.
 */
export function useSocHotkeys(handlers: {
  onCommandPalette?: Handler;
  onSearch?: Handler;
  onEscape?: Handler;
  onArrowUp?: Handler;
  onArrowDown?: Handler;
  enabled?: boolean;
}) {
  const { onCommandPalette, onSearch, onEscape, onArrowUp, onArrowDown, enabled = true } = handlers;

  useEffect(() => {
    if (!enabled) return;

    const onKeyDown = (event: KeyboardEvent) => {
      const typing = isTypingTarget(event.target);

      if (event.key === "Escape") {
        onEscape?.(event);
        return;
      }

      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        onCommandPalette?.(event);
        return;
      }

      if (typing || event.metaKey || event.ctrlKey || event.altKey) return;

      if (event.key === "/") {
        event.preventDefault();
        onSearch?.(event);
        return;
      }

      if (event.key === "ArrowUp" && onArrowUp) {
        event.preventDefault();
        onArrowUp(event);
        return;
      }

      if (event.key === "ArrowDown" && onArrowDown) {
        event.preventDefault();
        onArrowDown(event);
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [enabled, onCommandPalette, onSearch, onEscape, onArrowUp, onArrowDown]);
}
