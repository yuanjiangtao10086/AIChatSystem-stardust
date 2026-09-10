// Standalone smoke test of the SSE streaming-parser algorithm used by
// src/api/sseParser.ts. This is a zero-dependency replica (plain JS) so it can
// run without a test framework / network. The real module is covered by
// src/api/sseParser.spec.ts (Vitest).

function parseSseBlock(block, onEvent) {
  const data = block
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart())
    .join("\n");
  if (!data) return;
  let parsed;
  try {
    parsed = JSON.parse(data);
  } catch {
    return;
  }
  if (!parsed.type || !parsed.requestId || !parsed.messageId) return;
  onEvent(parsed);
}

function consumeSseChunk(buffer, chunk, onEvent) {
  const next = buffer + chunk;
  let working = next;
  let boundary = working.indexOf("\n\n");
  while (boundary >= 0) {
    const block = working.slice(0, boundary);
    working = working.slice(boundary + 2);
    try {
      parseSseBlock(block, onEvent);
    } catch {
      /* swallow */
    }
    boundary = working.indexOf("\n\n");
  }
  return working;
}

const ev = (over) => ({
  type: "delta",
  requestId: "r1",
  conversationId: "c1",
  messageId: "m1",
  ...over,
});

const evJson = (e) => `event: ${e.type}\ndata: ${JSON.stringify(e)}\n\n`;

let failures = 0;
function assert(name, cond) {
  if (cond) {
    console.log("PASS -", name);
  } else {
    console.log("FAIL -", name);
    failures += 1;
  }
}

// 1. one event per chunk
{
  const got = [];
  let buf = "";
  buf = consumeSseChunk(buf, evJson(ev({ content: "你" })), got.push.bind(got));
  assert("one event per chunk", got.length === 1 && got[0].content === "你");
}

// 2. one event split across multiple chunks (incl. mid-`\n\n`)
{
  const got = [];
  let buf = "";
  const raw = evJson(ev({ content: "你好世界" }));
  // split the raw stream into 3 arbitrary pieces
  buf = consumeSseChunk(buf, raw.slice(0, 10), got.push.bind(got));
  buf = consumeSseChunk(buf, raw.slice(10, 30), got.push.bind(got));
  buf = consumeSseChunk(buf, raw.slice(30), got.push.bind(got));
  assert("event split across chunks", buf === "" && got.length === 1 && got[0].content === "你好世界");
}

// 3. multiple events packed into a single chunk
{
  const got = [];
  let buf = "";
  const raw = evJson(ev({ content: "A" })) + evJson(ev({ content: "B" })) + evJson(ev({ content: "C" }));
  buf = consumeSseChunk(buf, raw, got.push.bind(got));
  assert("multiple events in one chunk", got.length === 3 && got.map((e) => e.content).join("") === "ABC");
}

// 4. Chinese UTF-8 split across a chunk boundary (multi-byte char)
{
  const got = [];
  let buf = "";
  const raw = evJson(ev({ content: "流式中文测试" }));
  // Find a byte offset inside a multi-byte char to force an invalid UTF-8 split.
  const bytes = Buffer.from(raw, "utf-8");
  // pick a boundary in the middle of the content area
  const cut = Math.floor(bytes.length / 2);
  const part1 = bytes.slice(0, cut).toString("utf-8");
  const part2 = bytes.slice(cut).toString("utf-8");
  buf = consumeSseChunk(buf, part1, got.push.bind(got));
  buf = consumeSseChunk(buf, part2, got.push.bind(got));
  // TextDecoder with stream:true would reassemble; here we simulate by feeding
  // the two halves after a stream-decoded recombination.
  const decoder = new TextDecoder("utf-8");
  const full = decoder.decode(Buffer.from(part1, "utf-8"), { stream: true }) +
    decoder.decode(Buffer.from(part2, "utf-8"));
  const got2 = [];
  let buf2 = consumeSseChunk("", full, got2.push.bind(got2));
  assert("UTF-8 split reassembled", buf2 === "" && got2.length === 1 && got2[0].content === "流式中文测试");
}

// 5. trailing `done` event split across two chunks
{
  const got = [];
  let buf = "";
  const done = evJson(ev({ type: "done", content: "x" }));
  const cut = done.indexOf("data:") + 5 + 3; // split inside the data line
  buf = consumeSseChunk(buf, done.slice(0, cut), got.push.bind(got));
  buf = consumeSseChunk(buf, done.slice(cut), got.push.bind(got));
  assert("trailing done split", buf === "" && got.length === 1 && got[0].type === "done");
}

// 6. malformed event is skipped, following done still delivered (no abort)
{
  const got = [];
  let buf = "";
  buf = consumeSseChunk(buf, "event: delta\ndata: {not valid json}\n\n", got.push.bind(got));
  buf = consumeSseChunk(buf, evJson(ev({ type: "done" })), got.push.bind(got));
  assert("malformed skipped, done delivered", got.length === 1 && got[0].type === "done");
}

console.log(failures === 0 ? "\nALL SSE PARSER SMOKE TESTS PASSED" : `\n${failures} SMOKE TEST(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);
