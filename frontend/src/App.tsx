import { useEffect, useMemo, useRef, useState } from "react";
import type { SagaView } from "./types";

const API = "http://localhost:8080";
const WS = "ws://localhost:8080/ws/sagas";

const STEPS = ["started", "debited", "credited", "completed"];

type StateMeta = { color: string; label: string; glow: boolean };

const STATE_META: Record<string, StateMeta> = {
    STARTED: { color: "var(--color-violet)", label: "forming", glow: true },
    DEBITED: { color: "var(--color-violet)", label: "in flight", glow: true },
    CREDITED: { color: "var(--color-violet)", label: "in flight", glow: true },
    COMPLETED: { color: "var(--color-teal)", label: "settled", glow: false },
    COMPENSATING: { color: "var(--color-amber)", label: "unwinding", glow: true },
    FAILED: { color: "var(--color-rose)", label: "unwound", glow: false },
};

function reachedIndex(state: string): number {
    const map: Record<string, number> = {
        STARTED: 1,
        DEBITED: 2,
        CREDITED: 3,
        COMPLETED: 4,
        COMPENSATING: 3,
        FAILED: 3,
    };
    return map[state] ?? 0;
}

export default function App() {
    const [sagas, setSagas] = useState<Record<string, SagaView>>({});
    const [connected, setConnected] = useState(false);
    const [amount, setAmount] = useState("100.00");
    const [failStep, setFailStep] = useState("NONE");
    const [sending, setSending] = useState(false);
    const wsRef = useRef<WebSocket | null>(null);

    useEffect(() => {
        fetch(`${API}/transfers`)
            .then((r) => r.json())
            .then((data: SagaView[]) => {
                const map: Record<string, SagaView> = {};
                for (const s of data) map[s.id] = s;
                setSagas(map);
            })
            .catch(() => {});
    }, []);

    useEffect(() => {
        let alive = true;
        function open() {
            const ws = new WebSocket(WS);
            wsRef.current = ws;
            ws.onopen = () => alive && setConnected(true);
            ws.onclose = () => {
                if (!alive) return;
                setConnected(false);
                setTimeout(open, 1500);
            };
            ws.onmessage = (e) => {
                const saga = JSON.parse(e.data) as SagaView;
                setSagas((prev) => ({ ...prev, [saga.id]: saga }));
            };
        }
        open();
        return () => {
            alive = false;
            wsRef.current?.close();
        };
    }, []);

    async function startTransfer() {
        setSending(true);
        try {
            await fetch(`${API}/transfers`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    fromAccount: "acct-A",
                    toAccount: "acct-B",
                    amount: parseFloat(amount) || 0,
                    failStep,
                }),
            });
        } catch {
            /* ignore */
        }
        setTimeout(() => setSending(false), 400);
    }

    const list = useMemo(
        () =>
            Object.values(sagas).sort((a, b) =>
                b.updatedAt.localeCompare(a.updatedAt)
            ),
        [sagas]
    );

    const settled = list.filter((s) => s.state === "COMPLETED").length;
    const unwound = list.filter((s) => s.state === "FAILED").length;
    const inFlight = list.filter((s) =>
        ["STARTED", "DEBITED", "CREDITED", "COMPENSATING"].includes(s.state)
    ).length;

    return (
        <div className="min-h-screen">
            <div className="max-w-3xl mx-auto px-6 py-14">
                <header className="flex items-start justify-between mb-4">
                    <div className="flex items-center gap-3">
                        <ThreadMark />
                        <h1 className="text-3xl font-bold tracking-tight leading-none">
                            unwind
                        </h1>
                    </div>
                    <div className="mono text-xs flex items-center gap-2 mt-1.5">
            <span
                className="w-1.5 h-1.5 rounded-full"
                style={{
                    background: connected
                        ? "var(--color-teal)"
                        : "var(--color-amber)",
                    boxShadow: connected ? "0 0 8px var(--color-teal)" : "none",
                    animation: connected ? "glowBreathe 2.5s ease infinite" : "none",
                }}
            />
                        <span className="text-[var(--color-mist)]">
              {connected ? "live" : "connecting"}
            </span>
                    </div>
                </header>

                <p className="text-[var(--color-mist)] text-[15px] leading-relaxed max-w-md mb-12">
                    A saga steps a transfer forward: debit, credit, record. When a step
                    fails, it retraces the thread and undoes every step that already
                    happened.
                </p>

                <div className="grid grid-cols-3 gap-px rounded-2xl overflow-hidden border border-[var(--color-edge)] mb-12">
                    <Stat value={inFlight} label="in flight" color="var(--color-violet)" />
                    <Stat value={settled} label="settled" color="var(--color-teal)" />
                    <Stat value={unwound} label="unwound" color="var(--color-rose)" />
                </div>

                <section className="rounded-2xl border border-[var(--color-edge)] bg-[var(--color-panel)] p-5 mb-12">
                    <div className="flex flex-wrap items-end gap-4">
                        <div>
                            <label className="block text-[10px] uppercase tracking-[0.15em] text-[var(--color-mist)] mb-2">
                                Amount
                            </label>
                            <input
                                value={amount}
                                onChange={(e) => setAmount(e.target.value)}
                                className="mono w-28 px-3 py-2.5 rounded-xl text-sm outline-none border bg-[var(--color-ink)] border-[var(--color-edge)] focus:border-[var(--color-violet)] transition-colors"
                            />
                        </div>
                        <div>
                            <label className="block text-[10px] uppercase tracking-[0.15em] text-[var(--color-mist)] mb-2">
                                Inject failure
                            </label>
                            <select
                                value={failStep}
                                onChange={(e) => setFailStep(e.target.value)}
                                className="mono px-3 py-2.5 rounded-xl text-sm outline-none border bg-[var(--color-ink)] border-[var(--color-edge)] focus:border-[var(--color-violet)] transition-colors cursor-pointer"
                            >
                                <option value="NONE">none</option>
                                <option value="DEBIT">at debit</option>
                                <option value="CREDIT">at credit</option>
                                <option value="LEDGER">at ledger</option>
                            </select>
                        </div>
                        <button
                            onClick={startTransfer}
                            disabled={sending}
                            className="px-6 py-2.5 rounded-xl text-sm font-semibold text-[#0a0c12] transition-all active:scale-95 disabled:opacity-60"
                            style={{ background: "var(--color-violet)" }}
                        >
                            {sending ? "sending…" : "Send transfer"}
                        </button>
                    </div>
                </section>

                <div className="flex items-center justify-between mb-5">
                    <h2 className="text-xs uppercase tracking-[0.15em] text-[var(--color-mist)]">
                        Sagas
                    </h2>
                    <span className="mono text-xs text-[var(--color-mist)]">
            {list.length}
          </span>
                </div>

                {list.length === 0 ? (
                    <div className="rounded-2xl border border-dashed border-[var(--color-edge)] py-16 text-center">
                        <p className="text-[var(--color-mist)] text-sm">
                            Nothing in flight. Send a transfer to watch the thread draw.
                        </p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {list.map((saga, i) => (
                            <SagaRow key={saga.id} saga={saga} index={i} />
                        ))}
                    </div>
                )}

                <footer className="mt-16 mono text-[11px] text-[var(--color-mist)] opacity-60">
                    orchestration-based saga · spring boot · rabbitmq
                </footer>
            </div>
        </div>
    );
}

function ThreadMark() {
    return (
        <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <path
                d="M4 14 C 4 8, 10 8, 14 14 S 24 20, 24 14"
                stroke="var(--color-violet)"
                strokeWidth="2"
                strokeLinecap="round"
            />
            <circle cx="4" cy="14" r="2.5" fill="var(--color-violet)" />
            <circle cx="24" cy="14" r="2.5" fill="var(--color-teal)" />
        </svg>
    );
}

function Stat({
                  value,
                  label,
                  color,
              }: {
    value: number;
    label: string;
    color: string;
}) {
    return (
        <div className="bg-[var(--color-panel)] px-5 py-5">
            <div className="mono text-3xl font-semibold" style={{ color }}>
                {value}
            </div>
            <div className="text-[11px] uppercase tracking-[0.15em] text-[var(--color-mist)] mt-1">
                {label}
            </div>
        </div>
    );
}

function SagaRow({ saga, index }: { saga: SagaView; index: number }) {
    const meta = STATE_META[saga.state] ?? {
        color: "#fff",
        label: saga.state,
        glow: false,
    };
    const reached = reachedIndex(saga.state);
    const unwinding = saga.state === "COMPENSATING" || saga.state === "FAILED";
    const completed = saga.state === "COMPLETED";

    return (
        <div
            className="rounded-2xl border p-5 bg-[var(--color-panel)] border-[var(--color-edge)]"
            style={{
                animation: `cardIn 0.4s ease ${Math.min(index * 0.04, 0.3)}s both`,
            }}
        >
            <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-3">
          <span className="mono text-xs text-[var(--color-mist)]">
            {saga.id.slice(0, 8)}
          </span>
                    <span className="mono text-xs text-[var(--color-mist)]">
            {saga.fromAccount} → {saga.toAccount}
          </span>
                    <span
                        className="mono text-xs font-semibold"
                        style={{ color: meta.color }}
                    >
            ${saga.amount}
          </span>
                </div>
                <span
                    className="mono text-[10px] uppercase tracking-[0.15em] px-2.5 py-1 rounded-full flex items-center gap-1.5"
                    style={{ color: meta.color, border: `1px solid ${meta.color}` }}
                >
          {meta.glow && (
              <span
                  className="w-1.5 h-1.5 rounded-full"
                  style={{
                      background: meta.color,
                      animation: "glowBreathe 1.5s ease infinite",
                  }}
              />
          )}
                    {meta.label}
        </span>
            </div>

            <div className="relative flex items-center px-2">
                {STEPS.map((step, i) => {
                    const nodeReached = i < reached;
                    const isRetracting = unwinding && i > 0 && i < reached;

                    let nodeColor = "var(--color-edge)";
                    if (nodeReached) nodeColor = "var(--color-violet)";
                    if (completed) nodeColor = "var(--color-teal)";
                    if (isRetracting) nodeColor = "var(--color-amber)";
                    if (saga.state === "FAILED" && i === 0) nodeColor = "var(--color-rose)";

                    const lineActive = i < reached - 1;
                    let lineColor = "var(--color-edge)";
                    if (lineActive) {
                        lineColor = completed
                            ? "var(--color-teal)"
                            : unwinding
                                ? "var(--color-amber)"
                                : "var(--color-violet)";
                    }

                    return (
                        <div
                            key={step}
                            className="flex-1 flex flex-col items-center relative"
                        >
                            {i < STEPS.length - 1 && (
                                <div
                                    className="absolute top-[9px] left-1/2 h-[3px] rounded-full transition-all duration-700"
                                    style={{ width: "100%", background: lineColor }}
                                />
                            )}
                            <div
                                className="w-[18px] h-[18px] rounded-full z-10 transition-colors duration-500"
                                style={{
                                    background: nodeColor,
                                    boxShadow: nodeReached ? `0 0 12px ${nodeColor}` : "none",
                                    animation: nodeReached
                                        ? `nodeBloom 0.5s ease ${i * 0.08}s both`
                                        : "none",
                                }}
                            />
                            <span className="mono text-[10px] text-[var(--color-mist)] mt-2.5">
                {step}
              </span>
                        </div>
                    );
                })}
            </div>

            {saga.detail && (
                <p className="mono text-[11px] text-[var(--color-mist)] mt-5 pt-4 border-t border-[var(--color-edge)] leading-relaxed">
                    {saga.detail}
                </p>
            )}
        </div>
    );
}