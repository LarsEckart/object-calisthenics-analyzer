#!/usr/bin/env bash
# Resolve the released plugin in a fresh build, without a local composite build.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <released-version>" >&2
  exit 2
fi

version=$1
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
project_dir=$(mktemp -d)
trap 'rm -rf "$project_dir"' EXIT

mkdir -p "$project_dir/src/main/java/example"
cat > "$project_dir/settings.gradle.kts" <<'EOF'
rootProject.name = "object-calisthenics-published-plugin-smoke"
EOF
cat > "$project_dir/build.gradle.kts" <<EOF
plugins {
    java
    id("com.larseckart.object-calisthenics") version "$version"
}

repositories {
    mavenCentral()
}
EOF
cat > "$project_dir/src/main/java/example/Value.java" <<'EOF'
package example;

public class Value {
    public int answer() {
        return 42;
    }
}
EOF

"$repo_root/gradlew" --project-dir "$project_dir" --no-daemon objectCalisthenicsReport
report="$project_dir/build/reports/calisthenics/calisthenics.json"
test -f "$report"
grep -q '"violations": 0' "$report"
