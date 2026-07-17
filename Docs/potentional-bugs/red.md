# Critical / likely production bugs

**[Copilot speed camera]** — Embedded COCO models have no `speed_camera` or `speed_limit_sign` classes, so Copilot alerts that depend on those labels never fire. Prefer a fine-tuned model or disable those alert paths in UI until available.
