import { createFileRoute } from "@tanstack/react-router";
import { Bookmark, Plus, Trash2 } from "lucide-react";
import { useState } from "react";

import { EmptyState, PageHeader } from "@/components/common";
import { AwaitingFeed, IntelCard } from "@/components/intel";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

export const Route = createFileRoute("/intel/watchlists")({
  head: () => ({
    meta: [
      { title: "Watchlists · Velorix Sentinel" },
      {
        name: "description",
        content: "Track high-priority indicators, actors and assets under active watch.",
      },
      { property: "og:title", content: "Watchlists · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Track high-priority indicators, actors and assets under active watch.",
      },
    ],
  }),
  component: WatchlistsPage,
});

interface LocalWatchlist {
  id: string;
  name: string;
  description: string;
  createdAt: string;
}

const SUGGESTIONS = ["Critical IPs", "Suspicious Domains", "High Risk Hashes", "APT Groups"];

/**
 * Watchlists are analyst-authored, so they are held locally until
 * `/api/v1/intel/watchlists` exists — no fabricated entries are shown.
 */
function WatchlistsPage() {
  const [watchlists, setWatchlists] = useState<LocalWatchlist[]>([]);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const create = () => {
    if (!name.trim()) return;
    setWatchlists((current) => [
      {
        id: crypto.randomUUID(),
        name: name.trim(),
        description: description.trim(),
        createdAt: new Date().toISOString(),
      },
      ...current,
    ]);
    setName("");
    setDescription("");
    setOpen(false);
  };

  return (
    <>
      <PageHeader
        title="Watchlists"
        description="Group indicators, actors and infrastructure you want continuously monitored."
        actions={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button size="sm">
                <Plus className="size-3.5" aria-hidden="true" /> New watchlist
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Create watchlist</DialogTitle>
                <DialogDescription>
                  Watchlists are stored in this browser session until the intelligence backend is
                  connected.
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="watchlist-name">Name</Label>
                  <Input
                    id="watchlist-name"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    placeholder="Critical IPs"
                  />
                  <div className="flex flex-wrap gap-1.5 pt-1">
                    {SUGGESTIONS.map((item) => (
                      <button
                        key={item}
                        type="button"
                        onClick={() => setName(item)}
                        className="focus-ring rounded-full border border-border px-2.5 py-1 text-[11px] text-muted-foreground hover:text-foreground"
                      >
                        {item}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="watchlist-description">Description</Label>
                  <Textarea
                    id="watchlist-description"
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    placeholder="What should this watchlist monitor?"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setOpen(false)}>
                  Cancel
                </Button>
                <Button onClick={create} disabled={!name.trim()}>
                  Create
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        }
      />

      <AwaitingFeed
        className="mt-6"
        label="Watchlist matching awaits live threat intelligence"
        detail="Lists persist to the backend and start matching indicators once /api/v1/intel/watchlists ships."
      />

      {watchlists.length === 0 ? (
        <EmptyState
          className="mt-6"
          icon={Bookmark}
          title="No watchlists yet"
          description="Create a watchlist to group the indicators, actors or infrastructure you care about."
        />
      ) : (
        <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {watchlists.map((watchlist) => (
            <IntelCard
              key={watchlist.id}
              title={watchlist.name}
              description={watchlist.description || "No description"}
              icon={Bookmark}
              action={
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Delete ${watchlist.name}`}
                  onClick={() =>
                    setWatchlists((current) => current.filter((item) => item.id !== watchlist.id))
                  }
                >
                  <Trash2 className="size-3.5" aria-hidden="true" />
                </Button>
              }
            >
              <div className="space-y-3 text-xs text-muted-foreground">
                <p>Created {new Date(watchlist.createdAt).toLocaleString()}</p>
                <AwaitingFeed compact label="0 matches — awaiting live threat intelligence" />
              </div>
            </IntelCard>
          ))}
        </div>
      )}
    </>
  );
}
