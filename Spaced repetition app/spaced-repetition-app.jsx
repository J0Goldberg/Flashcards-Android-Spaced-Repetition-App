import { useState, useRef, useCallback } from "react";

const CONFIDENCE_LABELS = ["", "Not at all", "Barely", "Somewhat", "Mostly", "Perfectly"];
const CONFIDENCE_COLORS = ["", "#E24B4A", "#EF9F27", "#EF9F27", "#1D9E75", "#0F6E56"];
const CONFIDENCE_BG    = ["", "#FCEBEB", "#FAEEDA", "#FAEEDA", "#E1F5EE", "#E1F5EE"];

const DEFAULT_DECKS = [
  {
    id: "d1", name: "Spanish Basics", color: "#534AB7",
    cards: [
      { id: "c1", front: "Hello", back: "Hola" },
      { id: "c2", front: "Thank you", back: "Gracias" },
      { id: "c3", front: "Good morning", back: "Buenos días" },
      { id: "c4", front: "How are you?", back: "¿Cómo estás?" },
      { id: "c5", front: "Please", back: "Por favor" },
    ]
  },
  {
    id: "d2", name: "World Capitals", color: "#0F6E56",
    cards: [
      { id: "c6", front: "Japan", back: "Tokyo" },
      { id: "c7", front: "Brazil", back: "Brasília" },
      { id: "c8", front: "Egypt", back: "Cairo" },
      { id: "c9", front: "Australia", back: "Canberra" },
    ]
  },
  {
    id: "d3", name: "JavaScript Concepts", color: "#993C1D",
    cards: [
      { id: "c10", front: "What is a closure?", back: "A function that retains access to its outer scope even after the outer function has returned." },
      { id: "c11", front: "What is hoisting?", back: "JavaScript moves variable and function declarations to the top of their scope before execution." },
      { id: "c12", front: "What is the event loop?", back: "A mechanism that continuously checks the call stack and processes tasks from the message queue." },
    ]
  }
];

// ── CSV parser ────────────────────────────────────────────────────────────────
// Handles quoted fields, escaped quotes (""), and CRLF/LF line endings.
function parseCsv(text) {
  const rows = [];
  let field = "";
  let inQuotes = false;
  let row = [];
  const push = () => { row.push(field); field = ""; };
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    const next = text[i + 1];
    if (inQuotes) {
      if (ch === '"' && next === '"') { field += '"'; i++; }
      else if (ch === '"') { inQuotes = false; }
      else { field += ch; }
    } else {
      if (ch === '"') { inQuotes = true; }
      else if (ch === ',') { push(); }
      else if (ch === '\r' && next === '\n') { push(); rows.push(row); row = []; i++; }
      else if (ch === '\n' || ch === '\r') { push(); rows.push(row); row = []; }
      else { field += ch; }
    }
  }
  push();
  if (row.length > 0 && row.some(f => f.trim())) rows.push(row);
  return rows;
}

function csvToCards(text) {
  const rows = parseCsv(text);
  const cards = [];
  let skipped = 0;
  rows.forEach((row, i) => {
    const front = (row[0] || "").trim();
    const back  = (row[1] || "").trim();
    // Skip header row if first row looks like a header
    if (i === 0 && front.toLowerCase() === "front" && back.toLowerCase() === "back") return;
    if (front && back) {
      cards.push({ id: `csv_${Date.now()}_${i}`, front, back });
    } else {
      skipped++;
    }
  });
  return { cards, skipped };
}

// ── CBR algorithm ─────────────────────────────────────────────────────────────
function initCardState(cardId) {
  return { cardId, confidence: 0, interval: 1, repetitions: 0, nextDue: Date.now(), history: [] };
}

function cbrSchedule(state, rating) {
  const now = Date.now();
  const DAY = 86400000;
  let { interval, repetitions } = state;
  const newHistory = [...(state.history || []), { rating, date: now }];
  if (rating <= 2) {
    interval = 1; repetitions = 0;
  } else {
    if (repetitions === 0) interval = 1;
    else if (repetitions === 1) interval = 3;
    else {
      const growthFactor = 1.5 + ((rating - 3) * 0.5);
      interval = Math.round(interval * growthFactor);
    }
    repetitions += 1;
    interval = Math.min(interval, 180);
  }
  const staleness = Math.max(0, (now - state.nextDue) / DAY);
  if (staleness > 2) interval = Math.max(1, Math.floor(interval * 0.85));
  return { ...state, confidence: rating, interval, repetitions, nextDue: now + interval * DAY, history: newHistory };
}

function isDue(cardState) {
  return !cardState.nextDue || Date.now() >= cardState.nextDue;
}

function useStorage(key, fallback) {
  const [val, setVal] = useState(() => {
    try { const s = localStorage.getItem(key); return s ? JSON.parse(s) : fallback; }
    catch { return fallback; }
  });
  const save = useCallback((v) => {
    setVal(v);
    try { localStorage.setItem(key, JSON.stringify(v)); } catch {}
  }, [key]);
  return [val, save];
}

const VIEWS = { home: "home", study: "study", stats: "stats", editor: "editor" };

// ── App root ──────────────────────────────────────────────────────────────────
export default function App() {
  const [decks, setDecks] = useStorage("cbr_decks", DEFAULT_DECKS);
  const [cardStates, setCardStates] = useStorage("cbr_states", {});
  const [view, setView] = useState(VIEWS.home);
  const [activeDeckId, setActiveDeckId] = useState(null);
  const [queue, setQueue] = useState([]);
  const [queueIndex, setQueueIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [sessionStats, setSessionStats] = useState({ reviewed: 0, total: 0 });
  const [editorDeckId, setEditorDeckId] = useState(null);

  function ensureCardStates(deckCards) {
    const updated = { ...cardStates };
    let changed = false;
    deckCards.forEach(c => { if (!updated[c.id]) { updated[c.id] = initCardState(c.id); changed = true; } });
    if (changed) setCardStates(updated);
    return updated;
  }

  function startStudy(deckId) {
    const deck = decks.find(d => d.id === deckId);
    if (!deck) return;
    const states = ensureCardStates(deck.cards);
    const dueCards = deck.cards.filter(c => isDue(states[c.id] || initCardState(c.id)));
    const newCards = deck.cards.filter(c => !(states[c.id]?.repetitions > 0));
    const allDue = [...new Map([...dueCards, ...newCards.slice(0, 5)].map(c => [c.id, c])).values()];
    if (allDue.length === 0) { alert("No cards due right now! Great job keeping up."); return; }
    setActiveDeckId(deckId);
    setQueue(allDue.sort(() => Math.random() - 0.5));
    setQueueIndex(0); setFlipped(false);
    setSessionStats({ reviewed: 0, total: allDue.length });
    setView(VIEWS.study);
  }

  function gradeCard(rating) {
    const card = queue[queueIndex];
    const existing = cardStates[card.id] || initCardState(card.id);
    const updated = cbrSchedule(existing, rating);
    setCardStates({ ...cardStates, [card.id]: updated });
    const reviewed = sessionStats.reviewed + 1;
    setSessionStats(s => ({ ...s, reviewed }));
    const nextIndex = queueIndex + 1;
    if (nextIndex >= queue.length) { setView("done"); }
    else { setQueueIndex(nextIndex); setFlipped(false); }
  }

  function getDeckStats(deck) {
    const states = deck.cards.map(c => cardStates[c.id] || initCardState(c.id));
    const due = states.filter(s => isDue(s)).length;
    const mastered = states.filter(s => s.confidence >= 4 && s.repetitions >= 3).length;
    const avgConf = states.length ? (states.reduce((a, s) => a + (s.confidence || 0), 0) / states.length).toFixed(1) : "0.0";
    return { due, mastered, total: deck.cards.length, avgConf };
  }

  const activeDeck = decks.find(d => d.id === activeDeckId);
  const currentCard = queue[queueIndex];

  if (view === VIEWS.study && currentCard && activeDeck) {
    return <StudyView deck={activeDeck} card={currentCard} cardState={cardStates[currentCard.id] || initCardState(currentCard.id)} flipped={flipped} setFlipped={setFlipped} onGrade={gradeCard} sessionStats={sessionStats} onExit={() => setView(VIEWS.home)} />;
  }
  if (view === "done") return <DoneView reviewed={sessionStats.reviewed} deckName={activeDeck?.name} onBack={() => setView(VIEWS.home)} />;
  if (view === VIEWS.stats) return <StatsView decks={decks} cardStates={cardStates} getDeckStats={getDeckStats} onBack={() => setView(VIEWS.home)} />;
  if (view === VIEWS.editor) return <EditorView decks={decks} setDecks={setDecks} editDeckId={editorDeckId} onBack={() => setView(VIEWS.home)} />;

  return <HomeView decks={decks} getDeckStats={getDeckStats} onStudy={startStudy} onStats={() => setView(VIEWS.stats)} onEditor={(id) => { setEditorDeckId(id || null); setView(VIEWS.editor); }} />;
}

// ── Home ──────────────────────────────────────────────────────────────────────
function HomeView({ decks, getDeckStats, onStudy, onStats, onEditor }) {
  const totalDue = decks.reduce((a, d) => a + getDeckStats(d).due, 0);
  return (
    <div style={{ padding: "1.5rem 1rem", maxWidth: 520, margin: "0 auto" }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "0.25rem" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 500 }}>Flashcards</h1>
          <p style={{ margin: "4px 0 0", fontSize: 13, color: "var(--color-text-secondary)" }}>
            {totalDue > 0 ? `${totalDue} card${totalDue !== 1 ? "s" : ""} due for review` : "All caught up!"}
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={onStats} style={{ fontSize: 13, padding: "6px 12px" }}>
            <i className="ti ti-chart-bar" style={{ fontSize: 15, verticalAlign: -2, marginRight: 4 }} aria-hidden="true" />Stats
          </button>
          <button onClick={() => onEditor(null)} style={{ fontSize: 13, padding: "6px 12px" }}>
            <i className="ti ti-plus" style={{ fontSize: 15, verticalAlign: -2, marginRight: 4 }} aria-hidden="true" />New deck
          </button>
        </div>
      </div>
      <div style={{ marginTop: "1.5rem", display: "flex", flexDirection: "column", gap: 12 }}>
        {decks.map(deck => {
          const stats = getDeckStats(deck);
          return (
            <div key={deck.id} style={{ background: "var(--color-background-primary)", border: "0.5px solid var(--color-border-tertiary)", borderRadius: "var(--border-radius-lg)", padding: "1rem 1.25rem", display: "flex", alignItems: "center", gap: 14 }}>
              <div style={{ width: 40, height: 40, borderRadius: 10, background: deck.color, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                <i className="ti ti-cards" style={{ fontSize: 20, color: "#fff" }} aria-hidden="true" />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 500, fontSize: 15, marginBottom: 4 }}>{deck.name}</div>
                <div style={{ display: "flex", gap: 12, fontSize: 12, color: "var(--color-text-secondary)" }}>
                  <span>{stats.total} cards</span>
                  <span style={{ color: stats.due > 0 ? "#E24B4A" : "var(--color-text-secondary)" }}>{stats.due} due</span>
                  <span>{stats.mastered} mastered</span>
                </div>
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <button onClick={() => onEditor(deck.id)} style={{ fontSize: 12, padding: "5px 10px" }}>
                  <i className="ti ti-edit" style={{ fontSize: 14, verticalAlign: -2 }} aria-hidden="true" />
                </button>
                <button onClick={() => onStudy(deck.id)} style={{ fontSize: 13, padding: "6px 14px", borderColor: deck.color, color: deck.color }}>Study</button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ── Study ─────────────────────────────────────────────────────────────────────
function StudyView({ deck, card, cardState, flipped, setFlipped, onGrade, sessionStats, onExit }) {
  const progress = sessionStats.reviewed / sessionStats.total;
  return (
    <div style={{ padding: "1rem", maxWidth: 520, margin: "0 auto" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: "1rem" }}>
        <button onClick={onExit} style={{ fontSize: 13, padding: "5px 10px" }}>
          <i className="ti ti-arrow-left" style={{ fontSize: 15, verticalAlign: -2 }} aria-hidden="true" />
        </button>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 5 }}>{deck.name}</div>
          <div style={{ height: 4, background: "var(--color-border-tertiary)", borderRadius: 99 }}>
            <div style={{ height: "100%", borderRadius: 99, background: deck.color, width: `${Math.round(progress * 100)}%`, transition: "width 0.3s" }} />
          </div>
        </div>
        <span style={{ fontSize: 12, color: "var(--color-text-secondary)", minWidth: 48, textAlign: "right" }}>{sessionStats.reviewed}/{sessionStats.total}</span>
      </div>
      <FlashCard card={card} flipped={flipped} setFlipped={setFlipped} deckColor={deck.color} />
      {cardState.confidence > 0 && (
        <div style={{ textAlign: "center", marginTop: "0.75rem" }}>
          <span style={{ fontSize: 11, padding: "2px 10px", background: CONFIDENCE_BG[cardState.confidence], color: CONFIDENCE_COLORS[cardState.confidence], borderRadius: 99, border: `0.5px solid ${CONFIDENCE_COLORS[cardState.confidence]}44` }}>
            Last: {CONFIDENCE_LABELS[cardState.confidence]}
          </span>
        </div>
      )}
      {flipped ? <ConfidenceRater onGrade={onGrade} /> : (
        <div style={{ textAlign: "center", marginTop: "1.5rem" }}>
          <button onClick={() => setFlipped(true)} style={{ padding: "12px 40px", fontSize: 15, fontWeight: 500, background: deck.color, color: "#fff", border: "none", borderRadius: "var(--border-radius-md)", cursor: "pointer" }}>Show answer</button>
          <p style={{ fontSize: 12, color: "var(--color-text-tertiary)", marginTop: 10 }}>Think about your answer before revealing it</p>
        </div>
      )}
    </div>
  );
}

function FlashCard({ card, flipped, setFlipped, deckColor }) {
  return (
    <div onClick={() => !flipped && setFlipped(true)} style={{ background: "var(--color-background-primary)", border: "0.5px solid var(--color-border-tertiary)", borderRadius: "var(--border-radius-lg)", minHeight: 220, display: "flex", flexDirection: "column", cursor: flipped ? "default" : "pointer", overflow: "hidden" }}>
      <div style={{ background: deckColor + "18", borderBottom: `0.5px solid ${deckColor}30`, padding: "8px 16px", fontSize: 11, color: deckColor, fontWeight: 500, letterSpacing: "0.04em" }}>
        {flipped ? "ANSWER" : "QUESTION"}
      </div>
      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "2rem 1.5rem", textAlign: "center" }}>
        <div>
          <p style={{ fontSize: 20, fontWeight: 500, margin: 0, lineHeight: 1.4 }}>{flipped ? card.back : card.front}</p>
          {!flipped && <p style={{ fontSize: 12, color: "var(--color-text-tertiary)", marginTop: 12 }}>Tap to reveal</p>}
        </div>
      </div>
    </div>
  );
}

function ConfidenceRater({ onGrade }) {
  const [hovered, setHovered] = useState(null);
  return (
    <div style={{ marginTop: "1.5rem" }}>
      <p style={{ textAlign: "center", fontSize: 13, color: "var(--color-text-secondary)", marginBottom: "0.75rem" }}>How confident did you feel?</p>
      <div style={{ display: "flex", gap: 8, justifyContent: "center" }}>
        {[1,2,3,4,5].map(n => (
          <button key={n} onMouseEnter={() => setHovered(n)} onMouseLeave={() => setHovered(null)} onClick={() => onGrade(n)}
            style={{ flex: 1, maxWidth: 80, padding: "14px 0", fontSize: 18, fontWeight: 500, borderRadius: "var(--border-radius-md)", border: `0.5px solid ${CONFIDENCE_COLORS[n]}`, background: hovered === n ? CONFIDENCE_BG[n] : "var(--color-background-primary)", color: CONFIDENCE_COLORS[n], cursor: "pointer", transition: "background 0.12s", display: "flex", flexDirection: "column", alignItems: "center", gap: 4 }}>
            <span>{n}</span>
            <span style={{ fontSize: 9, fontWeight: 400, color: CONFIDENCE_COLORS[n], opacity: 0.8, letterSpacing: "0.02em" }}>
              {["NOT AT ALL","BARELY","SOMEWHAT","MOSTLY","PERFECT"][n-1]}
            </span>
          </button>
        ))}
      </div>
      <p style={{ textAlign: "center", fontSize: 11, color: "var(--color-text-tertiary)", marginTop: 8 }}>1 = review again soon · 5 = won't see for a long time</p>
    </div>
  );
}

// ── Done ──────────────────────────────────────────────────────────────────────
function DoneView({ reviewed, deckName, onBack }) {
  return (
    <div style={{ padding: "3rem 1rem", textAlign: "center", maxWidth: 400, margin: "0 auto" }}>
      <i className="ti ti-circle-check" style={{ fontSize: 52, color: "#0F6E56" }} aria-hidden="true" />
      <h2 style={{ fontSize: 22, fontWeight: 500, margin: "1rem 0 0.5rem" }}>Session complete!</h2>
      <p style={{ color: "var(--color-text-secondary)", margin: "0 0 0.5rem" }}>You reviewed {reviewed} card{reviewed !== 1 ? "s" : ""} from {deckName}.</p>
      <p style={{ color: "var(--color-text-secondary)", fontSize: 13, marginBottom: "2rem" }}>Cards are scheduled based on your confidence — come back tomorrow for your next batch.</p>
      <button onClick={onBack} style={{ padding: "10px 28px", fontSize: 14 }}>Back to decks</button>
    </div>
  );
}

// ── Stats ─────────────────────────────────────────────────────────────────────
function StatsView({ decks, cardStates, getDeckStats, onBack }) {
  const allCards = decks.flatMap(d => d.cards);
  const allStates = allCards.map(c => cardStates[c.id] || initCardState(c.id));
  const totalReviews = allStates.reduce((a, s) => a + (s.history?.length || 0), 0);
  const mastered = allStates.filter(s => s.confidence >= 4 && s.repetitions >= 3).length;
  const due = allStates.filter(s => isDue(s)).length;
  const confBuckets = [0,0,0,0,0];
  allStates.forEach(s => { if (s.confidence >= 1) confBuckets[s.confidence - 1]++; });
  const maxBucket = Math.max(...confBuckets, 1);
  const bucketColors = CONFIDENCE_COLORS.slice(1);
  return (
    <div style={{ padding: "1.5rem 1rem", maxWidth: 520, margin: "0 auto" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: "1.5rem" }}>
        <button onClick={onBack} style={{ fontSize: 13, padding: "5px 10px" }}><i className="ti ti-arrow-left" style={{ fontSize: 15, verticalAlign: -2 }} aria-hidden="true" /></button>
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500 }}>Progress</h2>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10, marginBottom: "1.5rem" }}>
        {[["Total reviews", totalReviews], ["Mastered", mastered], ["Due now", due]].map(([label, value]) => (
          <div key={label} style={{ background: "var(--color-background-secondary)", borderRadius: "var(--border-radius-md)", padding: "0.875rem", textAlign: "center" }}>
            <p style={{ margin: "0 0 4px", fontSize: 12, color: "var(--color-text-secondary)" }}>{label}</p>
            <p style={{ margin: 0, fontSize: 24, fontWeight: 500 }}>{value}</p>
          </div>
        ))}
      </div>
      <div style={{ background: "var(--color-background-primary)", border: "0.5px solid var(--color-border-tertiary)", borderRadius: "var(--border-radius-lg)", padding: "1rem 1.25rem", marginBottom: "1.25rem" }}>
        <p style={{ margin: "0 0 1rem", fontWeight: 500, fontSize: 14 }}>Confidence distribution</p>
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end", height: 80 }}>
          {confBuckets.map((count, i) => (
            <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 4 }}>
              <span style={{ fontSize: 11, color: bucketColors[i], fontWeight: 500 }}>{count}</span>
              <div style={{ width: "100%", height: Math.round((count / maxBucket) * 56), minHeight: count > 0 ? 4 : 0, background: bucketColors[i], borderRadius: 4, opacity: 0.85 }} />
              <span style={{ fontSize: 11, color: "var(--color-text-secondary)" }}>{i+1}</span>
            </div>
          ))}
        </div>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {decks.map(deck => {
          const stats = getDeckStats(deck);
          const pct = stats.total > 0 ? Math.round((stats.mastered / stats.total) * 100) : 0;
          return (
            <div key={deck.id} style={{ background: "var(--color-background-primary)", border: "0.5px solid var(--color-border-tertiary)", borderRadius: "var(--border-radius-lg)", padding: "0.875rem 1.25rem" }}>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
                <span style={{ fontWeight: 500, fontSize: 14 }}>{deck.name}</span>
                <span style={{ fontSize: 12, color: "var(--color-text-secondary)" }}>{pct}% mastered</span>
              </div>
              <div style={{ height: 6, background: "var(--color-border-tertiary)", borderRadius: 99 }}>
                <div style={{ height: "100%", width: `${pct}%`, background: deck.color, borderRadius: 99, transition: "width 0.4s" }} />
              </div>
              <div style={{ display: "flex", gap: 16, marginTop: 8, fontSize: 12, color: "var(--color-text-secondary)" }}>
                <span>{stats.total} cards</span><span>{stats.due} due</span><span>avg confidence: {stats.avgConf}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ── Editor (with CSV import) ───────────────────────────────────────────────────
function EditorView({ decks, setDecks, editDeckId, onBack }) {
  const existing = editDeckId ? decks.find(d => d.id === editDeckId) : null;
  const [deckName, setDeckName] = useState(existing?.name || "");
  const [cards, setCards] = useState(existing?.cards || [{ id: `nc_${Date.now()}`, front: "", back: "" }]);
  const [saved, setSaved] = useState(false);
  const [importResult, setImportResult] = useState(null); // { imported, skipped } | null
  const [dragOver, setDragOver] = useState(false);
  const fileRef = useRef();

  function addCard() { setCards([...cards, { id: `nc_${Date.now()}`, front: "", back: "" }]); }
  function removeCard(id) { if (cards.length === 1) return; setCards(cards.filter(c => c.id !== id)); }
  function updateCard(id, field, val) { setCards(cards.map(c => c.id === id ? { ...c, [field]: val } : c)); }

  function handleCsvText(text, fileName) {
    const { cards: parsed, skipped } = csvToCards(text);
    if (parsed.length === 0) { setImportResult({ imported: 0, skipped, error: true }); return; }
    // Use filename (minus extension) as deck name if field is empty
    if (!deckName.trim() && fileName) {
      setDeckName(fileName.replace(/\.[^.]+$/, "").replace(/[-_]/g, " "));
    }
    // Merge: keep existing manual cards if any have content, otherwise replace
    const hasManualCards = cards.some(c => c.front.trim() || c.back.trim());
    setCards(hasManualCards ? [...cards.filter(c => c.front.trim() || c.back.trim()), ...parsed] : parsed);
    setImportResult({ imported: parsed.length, skipped });
  }

  function handleFileInput(e) {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = evt => handleCsvText(evt.target.result, file.name);
    reader.readAsText(file, "UTF-8");
    e.target.value = "";
  }

  function handleDrop(e) {
    e.preventDefault(); setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (!file || !file.name.endsWith(".csv")) return;
    const reader = new FileReader();
    reader.onload = evt => handleCsvText(evt.target.result, file.name);
    reader.readAsText(file, "UTF-8");
  }

  function save() {
    if (!deckName.trim()) return;
    const validCards = cards.filter(c => c.front.trim() && c.back.trim());
    if (validCards.length === 0) return;
    if (existing) {
      setDecks(decks.map(d => d.id === editDeckId ? { ...d, name: deckName, cards: validCards } : d));
    } else {
      const colors = ["#534AB7","#0F6E56","#993C1D","#185FA5","#854F0B","#993556"];
      setDecks([...decks, { id: `d_${Date.now()}`, name: deckName, color: colors[decks.length % colors.length], cards: validCards }]);
    }
    setSaved(true);
    setTimeout(() => { setSaved(false); onBack(); }, 600);
  }

  function deleteDeck() {
    if (!existing) return;
    if (confirm(`Delete "${existing.name}"?`)) { setDecks(decks.filter(d => d.id !== editDeckId)); onBack(); }
  }

  const validCount = cards.filter(c => c.front.trim() && c.back.trim()).length;

  return (
    <div style={{ padding: "1.5rem 1rem", maxWidth: 520, margin: "0 auto" }}>
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: "1.5rem" }}>
        <button onClick={onBack} style={{ fontSize: 13, padding: "5px 10px" }}>
          <i className="ti ti-arrow-left" style={{ fontSize: 15, verticalAlign: -2 }} aria-hidden="true" />
        </button>
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500, flex: 1 }}>{existing ? "Edit deck" : "New deck"}</h2>
        {existing && (
          <button onClick={deleteDeck} style={{ fontSize: 13, padding: "5px 10px", color: "#E24B4A", borderColor: "#E24B4A44" }}>
            <i className="ti ti-trash" style={{ fontSize: 15, verticalAlign: -2 }} aria-hidden="true" />
          </button>
        )}
      </div>

      {/* Deck name */}
      <input value={deckName} onChange={e => setDeckName(e.target.value)} placeholder="Deck name"
        style={{ width: "100%", marginBottom: "1.25rem", fontSize: 15, padding: "10px 12px", boxSizing: "border-box" }} />

      {/* CSV drop zone */}
      <div
        onDragOver={e => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        onClick={() => fileRef.current.click()}
        style={{
          border: `1.5px dashed ${dragOver ? "#534AB7" : "var(--color-border-secondary)"}`,
          borderRadius: "var(--border-radius-lg)",
          padding: "1.25rem",
          textAlign: "center",
          cursor: "pointer",
          background: dragOver ? "#534AB710" : "var(--color-background-secondary)",
          marginBottom: "1rem",
          transition: "border-color 0.15s, background 0.15s"
        }}
      >
        <input ref={fileRef} type="file" accept=".csv,text/csv" onChange={handleFileInput} style={{ display: "none" }} />
        <i className="ti ti-file-upload" style={{ fontSize: 24, color: dragOver ? "#534AB7" : "var(--color-text-secondary)", display: "block", marginBottom: 6 }} aria-hidden="true" />
        <p style={{ margin: 0, fontSize: 13, fontWeight: 500, color: dragOver ? "#534AB7" : "var(--color-text-primary)" }}>
          Drop a CSV file or click to browse
        </p>
        <p style={{ margin: "4px 0 0", fontSize: 11, color: "var(--color-text-secondary)" }}>
          Format: <code style={{ fontSize: 11 }}>front,back</code> — one card per row. Header row optional.
        </p>
      </div>

      {/* Import result banner */}
      {importResult && (
        <div style={{
          display: "flex", alignItems: "center", gap: 8,
          padding: "8px 12px", borderRadius: "var(--border-radius-md)",
          marginBottom: "1rem", fontSize: 13,
          background: importResult.error ? "#FCEBEB" : "#E1F5EE",
          color: importResult.error ? "#A32D2D" : "#085041",
          border: `0.5px solid ${importResult.error ? "#F09595" : "#5DCAA5"}`
        }}>
          <i className={`ti ${importResult.error ? "ti-alert-circle" : "ti-circle-check"}`} style={{ fontSize: 16 }} aria-hidden="true" />
          {importResult.error
            ? "No valid cards found. Check that your CSV has front and back columns."
            : `Imported ${importResult.imported} card${importResult.imported !== 1 ? "s" : ""}${importResult.skipped > 0 ? `, ${importResult.skipped} row${importResult.skipped !== 1 ? "s" : ""} skipped` : ""}.`}
          <button onClick={() => setImportResult(null)} style={{ marginLeft: "auto", padding: "2px 6px", fontSize: 11, border: "none", background: "transparent", cursor: "pointer", color: "inherit" }}>✕</button>
        </div>
      )}

      {/* Card list */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <span style={{ fontSize: 13, fontWeight: 500, color: "var(--color-text-secondary)" }}>
          {validCount} card{validCount !== 1 ? "s" : ""}
        </span>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: "1rem", maxHeight: 360, overflowY: "auto" }}>
        {cards.map((card, i) => (
          <div key={card.id} style={{ background: "var(--color-background-primary)", border: "0.5px solid var(--color-border-tertiary)", borderRadius: "var(--border-radius-lg)", padding: "0.875rem 1rem" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <span style={{ fontSize: 12, color: "var(--color-text-secondary)", fontWeight: 500 }}>Card {i + 1}</span>
              <button onClick={() => removeCard(card.id)} style={{ fontSize: 12, padding: "2px 8px", color: "var(--color-text-secondary)" }}>
                <i className="ti ti-x" style={{ fontSize: 13, verticalAlign: -1 }} aria-hidden="true" />
              </button>
            </div>
            <input value={card.front} onChange={e => updateCard(card.id, "front", e.target.value)} placeholder="Front (question)"
              style={{ width: "100%", marginBottom: 8, fontSize: 14, boxSizing: "border-box" }} />
            <input value={card.back} onChange={e => updateCard(card.id, "back", e.target.value)} placeholder="Back (answer)"
              style={{ width: "100%", fontSize: 14, boxSizing: "border-box" }} />
          </div>
        ))}
      </div>

      <button onClick={addCard} style={{ width: "100%", fontSize: 13, padding: "9px", marginBottom: "1rem" }}>
        <i className="ti ti-plus" style={{ fontSize: 15, verticalAlign: -2, marginRight: 6 }} aria-hidden="true" />Add card
      </button>

      <button onClick={save} style={{ width: "100%", fontSize: 14, padding: "11px", background: saved ? "#0F6E56" : "#534AB7", color: "#fff", border: "none", borderRadius: "var(--border-radius-md)", cursor: "pointer", fontWeight: 500 }}>
        {saved ? "Saved!" : existing ? "Save changes" : "Create deck"}
      </button>
    </div>
  );
}
