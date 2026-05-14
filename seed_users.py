import sqlite3
import time

DB = "exam.db"
TOTAL = 20_000_000
BATCH = 100_000
PASSWORD = "$2a$10$SeedDataDummyHashNotValidForLogin.PlaceholderXX"

conn = sqlite3.connect(DB)
conn.execute("PRAGMA journal_mode = OFF")
conn.execute("PRAGMA synchronous = OFF")
conn.execute("PRAGMA temp_store = MEMORY")
conn.execute("PRAGMA cache_size = -1000000")
conn.execute("PRAGMA locking_mode = EXCLUSIVE")

start_seq = conn.execute("SELECT COALESCE(MAX(id), 0) FROM sys_user").fetchone()[0] + 1
print(f"start_seq={start_seq} target={TOTAL} batch={BATCH}")

t0 = time.time()
conn.execute("BEGIN")
for batch_start in range(0, TOTAL, BATCH):
    n = min(BATCH, TOTAL - batch_start)
    rows = [
        (f"seed_{start_seq + batch_start + i}", PASSWORD, "seed_user")
        for i in range(n)
    ]
    conn.executemany(
        "INSERT INTO sys_user (username, password, real_name) VALUES (?, ?, ?)",
        rows,
    )
    done = batch_start + n
    elapsed = time.time() - t0
    rate = done / elapsed if elapsed else 0
    print(f"{done:>10}/{TOTAL}  {elapsed:7.1f}s  {rate:>10.0f} rows/s", flush=True)
conn.commit()
print(f"commit done in {time.time()-t0:.1f}s")

count = conn.execute("SELECT COUNT(*) FROM sys_user").fetchone()[0]
print(f"final row count: {count}")
conn.close()
