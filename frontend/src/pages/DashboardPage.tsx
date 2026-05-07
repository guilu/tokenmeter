const metrics = [
  { label: 'Tokens tracked', value: '0', hint: 'Ready for ingestion' },
  { label: 'Estimated cost', value: '€0.00', hint: 'Billing model pending' },
  { label: 'Providers', value: '0', hint: 'Connectors coming next' },
]

export function DashboardPage() {
  return (
    <section className="mx-auto max-w-6xl px-6 py-16" id="overview">
      <div className="max-w-3xl">
        <p className="mb-4 inline-flex rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-sm text-cyan-200">
          Foundation sprint
        </p>
        <h1 className="text-4xl font-semibold tracking-tight text-white sm:text-6xl">
          Measure AI usage before the bill punches you in the face.
        </h1>
        <p className="mt-6 text-lg leading-8 text-slate-400">
          TokenMeter starts with a clean backend/frontend foundation, CI quality gates and a dark UI base inspired by Vercel, Linear and GitHub.
        </p>
      </div>

      <div className="mt-12 grid gap-4 md:grid-cols-3" id="metrics">
        {metrics.map((metric) => (
          <article
            className="rounded-2xl border border-white/10 bg-white/[0.03] p-6 shadow-2xl shadow-black/20"
            key={metric.label}
          >
            <p className="text-sm text-slate-400">{metric.label}</p>
            <p className="mt-3 text-3xl font-semibold text-white">{metric.value}</p>
            <p className="mt-2 text-sm text-slate-500">{metric.hint}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
