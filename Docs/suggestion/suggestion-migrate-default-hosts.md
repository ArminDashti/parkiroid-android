# Suggestion: migrate existing DataStore hosts

Fresh installs get `dogan-api.xaigrok.ir` / `dogan-livekit.xaigrok.ir`. Devices that already saved the old `dogan.xaigrok.ir` default keep the old host until the user edits Connectivity.

**Why:** Changing `DEFAULT_*` only affects empty prefs.

**Effort:** Small — one-time migration if `api_endpoint` equals the legacy host.
