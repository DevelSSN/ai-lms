#!/usr/bin/python
from sys import argv, stderr, exit
import pandas as pd

if len(argv) < 2:
    stderr.write("Usage: df.py <input file> <columns...> [--reverse=1,2]\n")
    exit(1)

filename = argv[1]

if not filename.endswith(".csv"):
    stderr.write("Error: input file must be a .csv file\n")
    exit(1)

# Separate sort columns and flags
sort_params = []
reverse_indices = set()

for arg in argv[2:]:
    if arg.startswith("--reverse="):
        try:
            reverse_indices = set(int(x)
                                  for x in arg.split("=")[1].split(",") if x)
        except ValueError:
            stderr.write("Error: --reverse must be comma-separated integers\n")
            exit(1)
    else:
        sort_params.append(arg)

print("File:", filename)
print("Sort columns:", sort_params)
print("Reverse indices:", reverse_indices)

df = pd.read_csv(filename)

# Validate columns
missing = [col for col in sort_params if col not in df.columns]
if missing:
    stderr.write(f"Error: Columns not found: {missing}\n")
    exit(1)

try:
    if sort_params:
        # Build ascending list (1-based index)
        ascending = [
            False if (i + 1) in reverse_indices else True
            for i in range(len(sort_params))
        ]

        sorted_df = df.sort_values(by=sort_params, ascending=ascending)
        print(sorted_df[sort_params])
    else:
        print("No sort keys provided. Showing full DataFrame:")
        print(df)

except Exception as e:
    stderr.write(f"Error: {e}\n")
    exit(1)
