import json
from collections import Counter


def load_probes(filepath):
    with open(filepath) as f:
        return {p['probe_id']: p for p in json.load(f)['probes']}


unit_probes = load_probes('data/textr_analysis/database_TextrUnit.json')
scenario_probes = load_probes('data/textr_analysis/database_TextrScenarioThrows.json')
overlapping_ids = set(unit_probes.keys()) & set(scenario_probes.keys())

categories = {
    "Dirty -> Clean": [],
    "Clean -> Dirty": [],
    "Both Dirty": [],
    "Both Clean": [],
    "Other": []
}

for pid in overlapping_ids:
    u, s = unit_probes[pid], scenario_probes[pid]
    uo, so = u['probe_outcome'], s['probe_outcome']

    if uo == 'Dirty Kill' and so == 'Clean Kill':
        categories["Dirty -> Clean"].append((u, s))
    elif uo == 'Clean Kill' and so == 'Dirty Kill':
        categories["Clean -> Dirty"].append((u, s))
    elif uo == 'Dirty Kill' and so == 'Dirty Kill':
        categories["Both Dirty"].append((u, s))
    elif uo == 'Clean Kill' and so == 'Clean Kill':
        categories["Both Clean"].append((u, s))
    else:
        categories["Other"].append((u, s))

print(f"\n=== OVERLAPPING PROBE SUMMARY (Total: {len(overlapping_ids)}) ===")
for label, items in categories.items():
    pct = (len(items) / len(overlapping_ids)) * 100 if overlapping_ids else 0
    print(f"{label:<16}: {len(items):>4}  ({pct:.1f}%)")

dirty_to_clean = categories["Dirty -> Clean"]

if dirty_to_clean:
    print(f"\n=== DIRTY -> CLEAN DEEP DIVE (n={len(dirty_to_clean)}) ===")

    operators = Counter(u['operator'] for u, _ in dirty_to_clean).most_common()
    locations = Counter(u['location'] for u, _ in dirty_to_clean).most_common()

    print("\n  By Operator:")
    for op, count in operators:
        print(f"    {op:<20} {count}")

    print("\n  By Location Type:")
    for loc, count in locations:
        print(f"    {loc:<20} {count}")