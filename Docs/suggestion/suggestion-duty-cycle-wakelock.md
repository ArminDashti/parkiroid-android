# Suggestion: Duty-cycle wake lock

CaptureService still holds a `PARTIAL_WAKE_LOCK` for the whole monitoring session. Releasing it between duty-cycle frames (or using short timed wake locks around each capture+infer) could let the CPU sleep more between Watchman/Spotter ticks.

**Effort:** small–medium (careful testing with screen off / Doze).
