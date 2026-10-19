import { ShieldPlus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

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
import type {
  IncidentCategory,
  IncidentPriority,
  IncidentSeverity,
  IncidentSource,
} from "@/types/incident";

const SEVERITIES: IncidentSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFORMATIONAL"];
const PRIORITIES: IncidentPriority[] = ["P1", "P2", "P3", "P4"];
const CATEGORIES: IncidentCategory[] = [
  "MALWARE",
  "RANSOMWARE",
  "PHISHING",
  "CREDENTIAL_THEFT",
  "DATA_EXFILTRATION",
  "INSIDER_THREAT",
  "NETWORK_INTRUSION",
  "DENIAL_OF_SERVICE",
  "POLICY_VIOLATION",
  "UNKNOWN",
];
const SOURCES: IncidentSource[] = [
  "MANUAL",
  "PACKET_ANALYSIS",
  "THREAT_DETECTION_ENGINE",
  "THREAT_INTELLIGENCE",
  "INTEGRATION",
];

const FIELD =
  "focus-ring h-9 w-full rounded-lg border border-input bg-surface px-3 text-sm text-foreground placeholder:text-muted-foreground";

/**
 * Case intake form. The shape matches `CreateCaseRequest`; submission is
 * blocked until POST /v1/incidents exists.
 */
export function CreateCaseDialog({ triggerLabel = "Create Case" }: { triggerLabel?: string }) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [severity, setSeverity] = useState<IncidentSeverity>("HIGH");
  const [priority, setPriority] = useState<IncidentPriority>("P2");
  const [category, setCategory] = useState<IncidentCategory>("UNKNOWN");
  const [source, setSource] = useState<IncidentSource>("MANUAL");
  const [tags, setTags] = useState("");

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <ShieldPlus className="size-4" aria-hidden="true" />
          {triggerLabel}
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Create case</DialogTitle>
          <DialogDescription>
            Opens a new investigation record. Submission is available after backend integration.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">Title</span>
            <input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Short, factual case title"
              className={FIELD}
            />
          </label>

          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">Description</span>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={3}
              placeholder="What was observed, where and when"
              className="focus-ring w-full resize-none rounded-lg border border-input bg-surface p-3 text-sm placeholder:text-muted-foreground"
            />
          </label>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1.5">
              <span className="text-xs font-medium text-muted-foreground">Severity</span>
              <select
                value={severity}
                onChange={(event) => setSeverity(event.target.value as IncidentSeverity)}
                className={FIELD}
              >
                {SEVERITIES.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </label>
            <label className="block space-y-1.5">
              <span className="text-xs font-medium text-muted-foreground">Priority</span>
              <select
                value={priority}
                onChange={(event) => setPriority(event.target.value as IncidentPriority)}
                className={FIELD}
              >
                {PRIORITIES.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </label>
            <label className="block space-y-1.5">
              <span className="text-xs font-medium text-muted-foreground">Category</span>
              <select
                value={category}
                onChange={(event) => setCategory(event.target.value as IncidentCategory)}
                className={FIELD}
              >
                {CATEGORIES.map((value) => (
                  <option key={value} value={value}>
                    {value.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
            </label>
            <label className="block space-y-1.5">
              <span className="text-xs font-medium text-muted-foreground">Source</span>
              <select
                value={source}
                onChange={(event) => setSource(event.target.value as IncidentSource)}
                className={FIELD}
              >
                {SOURCES.map((value) => (
                  <option key={value} value={value}>
                    {value.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">Tags</span>
            <input
              value={tags}
              onChange={(event) => setTags(event.target.value)}
              placeholder="comma,separated,tags"
              className={FIELD}
            />
          </label>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            disabled
            title="Available after backend integration."
            onClick={() =>
              toast.info("Create case", { description: "Available after backend integration." })
            }
          >
            Create case
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
