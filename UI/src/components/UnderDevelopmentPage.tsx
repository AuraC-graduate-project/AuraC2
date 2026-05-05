import { ArrowLeft, Clock3, Construction, ListChecks } from "lucide-react";

type UnderDevelopmentPageProps = {
  title: string;
  subtitle?: string;
  description?: string;
  plannedItems?: string[];
  relatedCurrentFeature?: string;
  statusLabel?: string;
  onBack?: () => void;
};

export function UnderDevelopmentPage({
  title,
  subtitle = "Planned feature",
  description = "This feature is planned but not implemented in the current backend.",
  plannedItems = [],
  relatedCurrentFeature,
  statusLabel = "Under Development",
  onBack,
}: UnderDevelopmentPageProps) {
  return (
    <section className="mx-auto max-w-5xl">
      <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 bg-slate-50 px-6 py-5">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div className="flex items-start gap-4">
              <div className="flex h-12 w-12 items-center justify-center rounded-lg border border-amber-200 bg-amber-50 text-amber-700">
                <Construction className="h-6 w-6" />
              </div>
              <div>
                <div className="mb-2 inline-flex items-center gap-2 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-800">
                  <Clock3 className="h-3.5 w-3.5" />
                  {statusLabel}
                </div>
                <h1 className="text-2xl font-semibold text-slate-950">{title}</h1>
                <p className="mt-1 text-sm text-slate-600">{subtitle}</p>
              </div>
            </div>

            {onBack && (
              <button
                type="button"
                onClick={onBack}
                className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
              >
                <ArrowLeft className="h-4 w-4" />
                Back to working feature
              </button>
            )}
          </div>
        </div>

        <div className="grid gap-6 p-6 md:grid-cols-[1.2fr_0.8fr]">
          <div className="space-y-4">
            <p className="text-sm leading-6 text-slate-700">{description}</p>
            <div className="rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm text-blue-800">
              No backend endpoint is connected yet. The navigation entry is intentionally labeled as a future feature.
            </div>
            {relatedCurrentFeature && (
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Related working area
                </p>
                <p className="mt-1 text-sm font-medium text-slate-800">{relatedCurrentFeature}</p>
              </div>
            )}
          </div>

          <div className="rounded-lg border border-slate-200 bg-slate-50 p-5">
            <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-slate-800">
              <ListChecks className="h-4 w-4 text-blue-700" />
              Planned capabilities
            </div>
            {plannedItems.length === 0 ? (
              <p className="text-sm text-slate-500">Requirements are not fully defined yet.</p>
            ) : (
              <ul className="space-y-3">
                {plannedItems.map((item) => (
                  <li key={item} className="flex gap-2 text-sm text-slate-700">
                    <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-600" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
