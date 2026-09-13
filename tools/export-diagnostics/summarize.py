"""Summarize post-export render-thread samples: python3 summarize.py capture.log."""
import collections
import re
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text()
groups = collections.defaultdict(list)
for block in re.split(r"(?=^\d{4}-\d\d-\d\dT)", text, flags=re.MULTILINE):
    header, _, stack = block.partition("\n")
    match = re.search(r"finishedMillis=(\d+)", header)
    if match and int(match[1]) > 0:
        groups[match[1]].append((header, stack))

if not groups:
    print("No post-export delay samples yet; keep the sampler running during export.")
for finished, samples in groups.items():
    print(f"\nExport finishedMillis={finished}: {len(samples)} post-export samples")
    print(f"  First: {samples[0][0]}")
    print(f"  Last:  {samples[-1][0]}")
    stacks = collections.Counter(stack for _, stack in samples)
    for stack, count in stacks.most_common(3):
        print(f"  {count} samples:")
        print("\n".join(stack.splitlines()[:12]))
