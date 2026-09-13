#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
probe_java_home="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
probe_pid="${1:-}"
if [[ -z "$probe_pid" ]]; then
    probe_pid="$("$probe_java_home/bin/jps" -l | awk '$2 == "net.fabricmc.devlaunchinjector.Main" { print $1 }')"
fi
if [[ ! "$probe_pid" =~ ^[0-9]+$ ]]; then
    echo '请先启动开发版 MC 并打开回放；多个实例同时运行时，请传入目标 PID。' >&2
    exit 1
fi

probe_dir="$(mktemp -d "${TMPDIR:-/tmp}/flashback-export-probe.XXXXXX")"
"$probe_java_home/bin/javac" -d "$probe_dir" "$script_dir/ExportHangProbe.java" "$script_dir/Attach.java"
printf 'Manifest-Version: 1.0\nAgent-Class: ExportHangProbe\n' > "$probe_dir/MANIFEST.MF"
"$probe_java_home/bin/jar" cfm "$probe_dir/probe.jar" "$probe_dir/MANIFEST.MF" -C "$probe_dir" ExportHangProbe.class
"$probe_java_home/bin/java" -cp "$probe_dir" Attach "$probe_pid" "$probe_dir/probe.jar" "$probe_dir/capture.log"
echo "采样已启动，15 分钟后自动停止。现在可以正常导出，无须在卡住时通知。"
echo "记录路径：$probe_dir/capture.log"
