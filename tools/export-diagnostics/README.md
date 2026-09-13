# 导出卡顿采样

用于本机开发版 MC：先启动游戏并打开回放，再在仓库根目录运行：

```sh
bash tools/export-diagnostics/capture.sh
```

多个开发实例运行时，追加目标 Java PID。脚本将临时只读诊断 agent
加载到游戏进程，每 100 毫秒采样一次，15 分钟后自动停止。导出期间及
导出结束到编辑器恢复之间会记录主线程堆栈；空闲期间只记录状态变化。
无需在卡住时手动抓取。重启游戏会结束采样，需要重新启动脚本。

脚本会输出记录路径，完成导出后可运行：

```sh
python3 tools/export-diagnostics/summarize.py /实际路径/capture.log
```

日志仅保存时间、相关状态、界面类名和主线程堆栈，不保存启动参数、
账号信息或游戏画面。代码不随模组打包，也不修改游戏状态。

2026-09-14 的现场记录显示：编码结束 30 毫秒后，主线程继续执行
runTick 前半段，却在 16.4 秒内没有恢复编辑器绘制。回放服务器快进时
绕过等待，但原版每次仍向下一 tick 的调度时间累加 tick 间隔。
导出后的普通 tick 因此被推迟，而 processedSnapshot 在该 tick 清除前
阻止客户端绘制。调度回归测试可通过 `./gradlew testReplayTickSchedule` 运行；
游戏内最终验证需重启到修复后的版本，再比较日志中的
`editor drawn again ... ms` 与实际显示。
