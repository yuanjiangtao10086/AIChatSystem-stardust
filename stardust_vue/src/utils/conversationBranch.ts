import { ChatMessage } from "@/types/conversation";

export function selectActiveBranch(messages: ChatMessage[]): ChatMessage[] {
  if (!messages.length) return [];
  const ordered = [...messages].sort(
    (a, b) =>
      Date.parse(a.createdAt) - Date.parse(b.createdAt) ||
      a.sequenceNo - b.sequenceNo ||
      a.variantNo - b.variantNo
  );
  const hasBranches = ordered.some(
    (message) => message.supersedesMessageId || message.parentMessageId
  );
  if (!hasBranches) return latestVariantPerSequence(ordered);

  const byId = new Map(ordered.map((message) => [message.id, message]));
  const leaf = ordered[ordered.length - 1];
  const path: ChatMessage[] = [];
  const seen = new Set<string>();
  let current: ChatMessage | undefined = leaf;
  while (current && !seen.has(current.id)) {
    seen.add(current.id);
    path.push(current);
    current = current.parentMessageId
      ? byId.get(current.parentMessageId)
      : undefined;
  }
  path.reverse();
  return path.length > 1 ? path : latestVariantPerSequence(ordered);
}

function latestVariantPerSequence(messages: ChatMessage[]): ChatMessage[] {
  const latest = new Map<number, ChatMessage>();
  messages.forEach((message) => {
    const existing = latest.get(message.sequenceNo);
    if (!existing || message.variantNo >= existing.variantNo) {
      latest.set(message.sequenceNo, message);
    }
  });
  return [...latest.values()].sort(
    (a, b) => a.sequenceNo - b.sequenceNo || a.variantNo - b.variantNo
  );
}
