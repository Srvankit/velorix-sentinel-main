import { Link, createFileRoute, notFound } from "@tanstack/react-router";
import { ArrowLeft, Clock } from "lucide-react";

import { EmptyState, PageHeader } from "@/components/common";
import { MarkdownRenderer } from "@/components/copilot";
import { Button } from "@/components/ui/button";
import { KNOWLEDGE_TOPICS, findTopic } from "@/data/knowledge";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/knowledge/$topic")({
  loader: ({ params }) => {
    const topic = findTopic(params.topic);
    if (!topic) throw notFound();
    return { slug: topic.slug, title: topic.title, tagline: topic.tagline };
  },
  head: ({ loaderData }) => {
    if (!loaderData) {
      return {
        meta: [
          { title: `Article not found · ${APP_NAME}` },
          { name: "robots", content: "noindex" },
        ],
      };
    }
    return {
      meta: [
        { title: `${loaderData.title} · Knowledge Center · ${APP_NAME}` },
        { name: "description", content: loaderData.tagline },
        { property: "og:title", content: `${loaderData.title} · ${APP_NAME}` },
        { property: "og:description", content: loaderData.tagline },
        { property: "og:type", content: "article" },
        { name: "twitter:card", content: "summary_large_image" },
      ],
    };
  },
  notFoundComponent: TopicNotFound,
  component: KnowledgeTopicPage,
});

function TopicNotFound() {
  return (
    <EmptyState
      title="Article not found"
      description="This knowledge article does not exist or has been renamed."
      className="mt-10"
      action={
        <Button asChild variant="outline" size="sm">
          <Link to="/knowledge">Back to Knowledge Center</Link>
        </Button>
      }
    />
  );
}

function KnowledgeTopicPage() {
  const { slug } = Route.useLoaderData();
  const topic = findTopic(slug);
  if (!topic) return <TopicNotFound />;

  const related = KNOWLEDGE_TOPICS.filter((item) => item.slug !== topic.slug).slice(0, 4);

  return (
    <>
      <PageHeader
        title={topic.title}
        description={topic.tagline}
        actions={
          <Button asChild variant="outline" size="sm">
            <Link to="/knowledge">
              <ArrowLeft className="size-4" aria-hidden="true" />
              All articles
            </Link>
          </Button>
        }
      />

      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1fr)_18rem]">
        <article className="glass-panel min-w-0 rounded-2xl p-5 sm:p-8">
          <p className="mb-6 inline-flex items-center gap-2 rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">
            <Clock className="size-3" aria-hidden="true" />
            {topic.readingMinutes} min read · {topic.category}
          </p>
          <MarkdownRenderer content={topic.body} className="text-[0.9375rem]" />
        </article>

        <aside className="xl:sticky xl:top-24 xl:self-start">
          <nav className="glass-panel rounded-2xl p-5">
            <h2 className="mb-3 text-sm font-semibold tracking-tight">Continue reading</h2>
            <ul className="space-y-1">
              {related.map((item) => (
                <li key={item.slug}>
                  <Link
                    to="/knowledge/$topic"
                    params={{ topic: item.slug }}
                    className="focus-ring flex items-center gap-2 rounded-lg px-2 py-2 text-sm transition-colors hover:bg-sidebar-accent"
                  >
                    <item.icon className="size-4 shrink-0 text-primary" aria-hidden="true" />
                    <span className="min-w-0 truncate">{item.title}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </aside>
      </div>
    </>
  );
}
