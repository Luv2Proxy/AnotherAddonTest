export class StructurePlacementQueue {
  constructor(options = {}) {
    this.maxPerTick = Math.max(1, Number(options.maxPerTick ?? 2));
    this.maxRetries = Math.max(0, Number(options.maxRetries ?? 2));
    this.retryDelay = Math.max(1, Number(options.retryDelay ?? 20));
    this.queue = [];
    this.keys = new Set();
    this.retries = new Map();
    this.nextAttempt = new Map();
  }

  enqueue(candidate, context = {}) {
    const key = context.placementKey ?? `${candidate?.structure ?? candidate?.id ?? "unknown"}:${Math.floor((context.location?.x ?? candidate?.x ?? 0) / 16)}:${Math.floor((context.location?.z ?? candidate?.z ?? 0) / 16)}`;
    if (this.keys.has(key)) return false;
    this.keys.add(key);
    this.queue.push({ candidate, context, key });
    return true;
  }

  size() { return this.queue.length; }

  async process(coordinator) {
    let processed = 0, placed = 0, failed = 0, deferred = 0;
    const remaining = [];
    while (this.queue.length && processed < this.maxPerTick) {
      const item = this.queue.shift();
      const attempts = this.retries.get(item.key) ?? 0;
      const next = this.nextAttempt.get(item.key) ?? 0;
      if (Date.now() < next) { remaining.push(item); deferred++; continue; }
      processed++;
      try {
        const result = await coordinator.place(item.candidate, { ...item.context, placementKey: item.key });
        if (result.placed || result.reason === "already_placed") {
          placed += result.placed ? 1 : 0;
          this.keys.delete(item.key); this.retries.delete(item.key); this.nextAttempt.delete(item.key);
        } else if (attempts < this.maxRetries) {
          this.retries.set(item.key, attempts + 1);
          this.nextAttempt.set(item.key, Date.now() + this.retryDelay * 50);
          remaining.push(item); deferred++;
        } else {
          this.keys.delete(item.key); this.retries.delete(item.key); this.nextAttempt.delete(item.key); failed++;
        }
      } catch (error) {
        if (attempts < this.maxRetries) {
          this.retries.set(item.key, attempts + 1);
          this.nextAttempt.set(item.key, Date.now() + this.retryDelay * 50);
          remaining.push(item); deferred++;
        } else { this.keys.delete(item.key); this.retries.delete(item.key); this.nextAttempt.delete(item.key); failed++; }
      }
    }
    this.queue.unshift(...remaining);
    return { processed, placed, failed, deferred, queued: this.queue.length };
  }
}
