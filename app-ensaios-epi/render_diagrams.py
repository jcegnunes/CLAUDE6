import subprocess
import os

print("Checking convert...")
res = subprocess.run(["convert", "-version"], stdout=subprocess.PIPE, text=True)
print(res.stdout[:100])
