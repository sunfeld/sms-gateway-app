"""
FastAPI application for Bluetooth stress test orchestration.

Provides HTTP endpoints to start, stop, and monitor Bluetooth stress
test sessions via the AttackOrchestrator.
"""

import asyncio
import logging
import time
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from bluetooth.attack_orchestrator import AttackOrchestrator, OrchestratorState
from bluetooth.bluetooth_manager import BluetoothManager
from bluetooth.target_scanner import TargetScanner
from bluetooth.advertising_payload import AdvertisingPayload

logger = logging.getLogger(__name__)

app = FastAPI(title="Bluetooth Stress Test API")

# Intensity level → (cycle_delay_seconds, concurrent_connections)
INTENSITY_LEVELS = {
    1: (0.100, 1),
    2: (0.050, 2),
    3: (0.030, 4),
    4: (0.020, 8),
    5: (0.020, 16),
}

# Global orchestrator instance and session tracking
_orchestrator: Optional[AttackOrchestrator] = None
_session_id: Optional[str] = None
_session_duration: Optional[int] = None
_session_intensity: Optional[int] = None
_session_start: Optional[float] = None
_run_task: Optional[asyncio.Task] = None


class StartRequest(BaseModel):
    duration: int = Field(..., gt=0, description="Test duration in seconds")
    intensity: int = Field(..., ge=1, le=5, description="Intensity level (1-5)")


class StartResponse(BaseModel):
    status: str
    session_id: str
    duration: int
    intensity: int
    targets_discovered: int


class ConflictResponse(BaseModel):
    status: str
    session_id: str
    remaining_seconds: int


class StatusResponse(BaseModel):
    session_id: str
    status: str
    packets_sent: int
    targets_active: int
    remaining_seconds: int
    intensity: int


class StopResponse(BaseModel):
    status: str
    session_id: str


def _remaining_seconds() -> int:
    if _session_start is None or _session_duration is None:
        return 0
    elapsed = time.time() - _session_start
    return max(0, int(_session_duration - elapsed))


@app.post(
    "/api/bluetooth/dos/start",
    response_model=StartResponse,
    responses={409: {"model": ConflictResponse}},
)
async def start_bluetooth_dos(request: StartRequest):
    """
    Trigger the Bluetooth stress test orchestrator.

    Accepts duration (seconds) and intensity (1-5) parameters.
    Returns 409 if a session is already running.
    """
    global _orchestrator, _session_id, _session_duration, _session_intensity
    global _session_start, _run_task

    # Check if already running
    if _orchestrator is not None and _orchestrator.state == OrchestratorState.RUNNING:
        raise HTTPException(
            status_code=409,
            detail={
                "status": "already_running",
                "session_id": _session_id,
                "remaining_seconds": _remaining_seconds(),
            },
        )

    # Map intensity to cycle_delay and concurrency
    cycle_delay, concurrency = INTENSITY_LEVELS[request.intensity]

    # Initialize subsystems
    bt_manager = BluetoothManager()
    scanner = TargetScanner()
    payload = AdvertisingPayload()

    _orchestrator = AttackOrchestrator(
        bluetooth_manager=bt_manager,
        target_scanner=scanner,
        advertising_payload=payload,
        concurrency=concurrency,
        cycle_delay=cycle_delay,
    )

    _session_id = f"bt-sess-{int(time.time())}"
    _session_duration = request.duration
    _session_intensity = request.intensity
    _session_start = time.time()

    # Discover targets before starting
    try:
        devices = await scanner.scan()
        targets_discovered = len(devices)
        if devices:
            _orchestrator.set_targets([d.address for d in devices])
    except Exception as e:
        logger.warning("Target scan failed: %s", e)
        targets_discovered = 0

    # Launch orchestrator in background
    _run_task = asyncio.create_task(
        _run_orchestrator(_orchestrator, request.duration)
    )

    return StartResponse(
        status="started",
        session_id=_session_id,
        duration=request.duration,
        intensity=request.intensity,
        targets_discovered=targets_discovered,
    )


@app.get("/api/bluetooth/dos/status/{session_id}", response_model=StatusResponse)
async def get_bluetooth_dos_status(session_id: str):
    """Get the status of a running stress test session."""
    if _session_id != session_id or _orchestrator is None:
        raise HTTPException(status_code=404, detail="Session not found")

    stats = _orchestrator.get_stats()
    return StatusResponse(
        session_id=session_id,
        status=stats["state"],
        packets_sent=stats["packets_sent"],
        targets_active=stats["devices_targeted"],
        remaining_seconds=_remaining_seconds(),
        intensity=_session_intensity or 1,
    )


@app.post("/api/bluetooth/dos/stop/{session_id}", response_model=StopResponse)
async def stop_bluetooth_dos(session_id: str):
    """Stop a running stress test session."""
    if _session_id != session_id or _orchestrator is None:
        raise HTTPException(status_code=404, detail="Session not found")

    if _orchestrator.is_running:
        _orchestrator.stop()

    return StopResponse(status="stopped", session_id=session_id)


async def _run_orchestrator(orchestrator: AttackOrchestrator, duration: int):
    """Run the orchestrator as a background task."""
    try:
        await orchestrator.run(duration=duration)
    except Exception as e:
        logger.error("Orchestrator run failed: %s", e)
    finally:
        logger.info("Orchestrator session completed")
